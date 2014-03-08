/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.apache.qpid.server.qmf2;

// Misc Imports
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

// Simple Logging Facade 4 Java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Java Broker Management Imports
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ManagementConsoleMessages;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.ManagedAttribute;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.AbstractPluginAdapter;

import org.apache.qpid.server.plugin.PluginFactory;
import org.apache.qpid.server.util.MapValueConverter;

/**
 * This class is a Qpid Java Broker Plugin which follows the Plugin API added in Qpid 0.22 it implements 
 * org.apache.qpid.server.model.Plugin and extends org.apache.qpid.server.model.adapter.AbstractPluginAdapter.
 * <p>
 * This Plugin provides access to the Java Broker Management Objects via QMF2 thus allowing the Java Broker to
 * be managed and monitored in the same way as the C++ Broker.
 * <p>
 * The intention is for the Java Broker QmfManagementPlugin to conform to the same Management Schema as the C++
 * Broker (e.g. as specified in the management-schema.xml) in order to provide maximum cohesion between the
 * two Broker implementations, however that's not entirely possible given differences between the underlying
 * Management Models. The ultimate aim is to align the Management Models of the two Qpid Brokers and migrate
 * to the AMQP 1.0 Management architecture when it becomes available.
 * <p>
 * This Plugin attempts to map properties from the Java org.apache.qpid.server.model.* classes to equivalent
 * properties and statistics in the C++ Broker's Management Schema rather than expose them "natively", this is
 * in order to try and maximise alignment between the two implementations and to try to allow the Java Broker
 * to be managed by the Command Line tools used with the C++ Broker such as qpid-config etc. it's also to
 * enable the Java Broker to be accessed via the QMF2 REST API and GUI.
 * <p>
 * This class only bootstraps the ManagementPlugin, the actual business logic is run from QmfManagementAgent.
 * It's worth also mentioning that this Plugin actually establishes an AMQP Connection to the Broker via JMS.
 * As it's a Broker Plugin it could conceivably use the low level Broker internal transport, this would probably
 * be a little more efficient, but OTOH by using the JMS based approach I can use the QMF2 Agent code
 * directly and implementing a complete QMF2 Agent for the Java Broker becomes "fairly simple" only requiring
 * mappings between the org.apache.qpid.server.model.* classes and their QmfAgentData equivalents.
 * <p>
 * This Plugin requires config to be set, if this is not done the Plugin will not bootstrap. Config may be
 * set in $QPID_WORK/config.json as part of the "plugins" config e.g.
 * <pre>
 * "plugins" : [ {
 *    "id" : "26887211-842c-3c4a-ab09-b1a1f64de369",
 *    "name" : "qmf2Management",
 *    "pluginType" : "MANAGEMENT-QMF2",
 *    "connectionURL" : "amqp://guest:guest@/?brokerlist='tcp://0.0.0.0:5672'"
 * }]
 * </pre>
 * @author Fraser Adams
 */
public class QmfManagementPlugin extends AbstractPluginAdapter<QmfManagementPlugin>
{
    private static final Logger _log = LoggerFactory.getLogger(QmfManagementPlugin.class);

    private static final String OPERATIONAL_LOGGING_NAME = "QMF2";

    /************* Static initialiser used to implement org.apache.qpid.server.model.Plugin *************/

    public static final String PLUGIN_TYPE = "MANAGEMENT-QMF2";

    // attributes
    public static final String NAME = "name";
    public static final String CONNECTION_URL = "connectionURL";

    // default values
    public static final String DEFAULT_NAME = "qmf2Management";
    public static final String DEFAULT_CONNECTION_URL = "amqp://guest:guest@/?brokerlist='tcp://0.0.0.0:5672'";

    @SuppressWarnings("serial")
    private static final Map<String, Object> DEFAULTS = Collections.unmodifiableMap(new HashMap<String, Object>()
    {{
        put(NAME, DEFAULT_NAME);
        put(CONNECTION_URL, DEFAULT_CONNECTION_URL);
        put(PluginFactory.PLUGIN_TYPE, PLUGIN_TYPE);
    }});

    @SuppressWarnings("serial")
    private static final Map<String, Type> ATTRIBUTE_TYPES = Collections.unmodifiableMap(new HashMap<String, Type>()
    {{
        put(NAME, String.class);
        put(CONNECTION_URL, String.class);
        put(PluginFactory.PLUGIN_TYPE, String.class);
    }});

    /************************************ End of Static initialiser *************************************/

    private final Broker<?> _broker;          // Passed in by Plugin bootstrapping.
    private final String _defaultVirtualHost; // Pulled from the broker attributes.
    private final String _connectionURL;      // Pulled from the Plugin config.
    private QmfManagementAgent _agent;

    /**
     * Constructor, called at broker startup by QmfManagementFactory.createInstance().
     * @param id the UUID of the Plugin.
     * @param attributes a Map containing configuration information for the Plugin.
     * @param broker the root Broker Management Object from which the other Management Objects may be obtained.
     */
    public QmfManagementPlugin(UUID id, Broker broker, Map<String, Object> attributes)
    {
        super(id, DEFAULTS, MapValueConverter.convert(attributes, ATTRIBUTE_TYPES), broker);
        addParent(Broker.class, broker);
        _broker = broker;
        _defaultVirtualHost = (String)broker.getAttribute("defaultVirtualHost");
        _connectionURL = (String)getAttribute(CONNECTION_URL);
    }

    /**
     * Set the state of the Plugin, I believe that this is called from the BrokerAdapter object when it
     * has its own state set to State.ACTIVE or State.STOPPED.
     * When State.ACTIVE is set this calls the start() method to startup the Plugin, when State.STOPPED
     * is set this calls the stop() method to shutdown the Plugin.
     * @param currentState the current state of the Plugin (ignored).
     * @param desiredState the desired state of the Plugin (either State.ACTIVE or State.STOPPED).
     * @return true if a valid state has been set, otherwise false.
     */
    @Override // From org.apache.qpid.server.model.adapter.AbstractAdapter
    protected boolean setState(State currentState, State desiredState)
    {
        if (desiredState == State.ACTIVE)
        {
            start();
            return true;
        }
        else if (desiredState == State.STOPPED)
        {
            stop();
            return true;
        }
        else
        {
            _log.info("QmfManagementPlugin.setState() received invalid desiredState {}", desiredState);
            return false;
        }
    }

    /**
     * Start the Plugin. Note that we bind the QMF Connection the the default Virtual Host, this is important
     * in order to allow C++ or Python QMF Consoles to control the Java Broker, as they know nothing of Virtual
     * Hosts and their Connection URL formats don't have a mechanism to specify Virtual Hosts.
     * <p>
     * Note too that it may be necessary to create the "qmf.default.direct" and "qmf.default.topic" exchanges
     * as these don't exist by default on the Java Broker, however we have to check if they already exist
     * as attempting to add an Exchange that already exists will cause IllegalArgumentException.
     */
    private void start()
    {
        // Log "QMF2 Management Startup" message.
        CurrentActor.get().message(ManagementConsoleMessages.STARTUP(OPERATIONAL_LOGGING_NAME));

        // Wrap main startup logic in a try/catch block catching Exception. The idea is that if anything goes
        // wrong with QmfManagementPlugin startup it shouldn't fatally prevent the Broker from starting, though
        // clearly QMF2 management will not be available.
        try
        {
            // Iterate through the Virtual Hosts looking for the default Virtual Host. When we find the default
            // we create the QMF exchanges then construct the QmfManagementAgent passing it the ConnectionURL.
            boolean foundDefaultVirtualHost = false;
            for (VirtualHost<?> vhost : _broker.getVirtualHosts())
            {
                if (vhost.getName().equals(_defaultVirtualHost))
                {
                    foundDefaultVirtualHost = true;

                    // Check if "qmf.default.direct" or "qmf.default.topic" already exist. It is important to
                    // check as attempting to add an Exchange that already exists will cause IllegalArgumentException.
                    boolean needDefaultDirect = true;
                    boolean needDefaultTopic  = true;
                    for (Exchange exchange : vhost.getExchanges())
                    {
                        if (exchange.getName().equals("qmf.default.direct"))
                        {
                            needDefaultDirect = false;
                        }
                        else if (exchange.getName().equals("qmf.default.topic"))
                        {
                            needDefaultTopic = false;
                        }
                    }

                    // Create the QMF2 exchanges if necessary.
                    Map<String, Object> attributes = Collections.emptyMap();
                    if (needDefaultDirect)
                    {
                        vhost.createExchange("qmf.default.direct", State.ACTIVE, true,
                                             LifetimePolicy.PERMANENT, "direct", attributes);
                    }

                    if (needDefaultTopic)
                    {
                        vhost.createExchange("qmf.default.topic", State.ACTIVE, true,
                                             LifetimePolicy.PERMANENT, "topic", attributes);
                    }

                    // Now create the *real* Agent which maps Broker Management Objects to QmdAgentData Objects.
                    _agent = new QmfManagementAgent(_connectionURL, _broker);
                }
            }

            // If we can't find a defaultVirtualHost we log that fact, the Plugin can't start in this case.
            // Question. If defaultVirtualHost isn't configured or it doesn't match the name of one of the actual
            // Virtual Hosts should we make the first one we find the de facto default for this Plugin??
            if (!foundDefaultVirtualHost)
            {
                _log.info("QmfManagementPlugin.start() could not find defaultVirtualHost");
            }
            else if (_agent.isConnected())
            {
                // Log QMF2 Management Ready message.
                CurrentActor.get().message(ManagementConsoleMessages.READY(OPERATIONAL_LOGGING_NAME));
            }
        }
        catch (Exception e) // Catch and log any Exception so we avoid Plugin failures stopping Broker startup.
        {
            _log.info("Exception {} caught in QmfManagementPlugin.start()", e.getMessage());
        }
    }

    /**
     * Stop the Plugin, closing the QMF Connection and logging "QMF2 Management Stopped".
     */
    private void stop()
    {
        // When the Plugin state gets set to STOPPED we close the QMF Connection.
        if (_agent != null)
        {
            _agent.close();
        }

        // Log "QMF2 Management Stopped" message (may not get displayed).
        CurrentActor.get().message(ManagementConsoleMessages.STOPPED(OPERATIONAL_LOGGING_NAME));
    }

    /**
     * Accessor to retrieve the names of the available attributes. It is important to provide this overridden
     * method because the Constructor uses this information when populating the underlying AbstractPlugin
     * information. If we don't provide this override method getAttribute(name) will return the default values.
     * @return the names of the available Plugin config attributes as a Collection.
     */
    @Override // From org.apache.qpid.server.model.adapter.AbstractPluginAdapter
    public Collection<String> getAttributeNames()
    {
        return getAttributeNames(QmfManagementPlugin.class);
    }

    /**
     * Accessor to retrieve the Plugin Type.
     * @return the Plugin Type e.g. the String "MANAGEMENT-QMF2".
     */
    @Override // From org.apache.qpid.server.model.Plugin
    public String getPluginType()
    {
        return PLUGIN_TYPE;
    }

    /**
     * Accessor to retrieve the connectionURL attribute.
     * @return the JMS connectionURL of the Plugin.
     */
    @ManagedAttribute
    public String getConnectionURL()
    {
        return (String)getAttribute(CONNECTION_URL);
    }
}
