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
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.logging.messages.ManagementConsoleMessages;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.model.adapter.AbstractPluginAdapter;

// Simple Logging Facade 4 Java
// Java Broker Management Imports

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
public class QmfManagementPluginImpl extends AbstractPluginAdapter<QmfManagementPluginImpl> implements QmfManagementPlugin<QmfManagementPluginImpl>
{
    private static final Logger _log = LoggerFactory.getLogger(QmfManagementPluginImpl.class);

    private static final String OPERATIONAL_LOGGING_NAME = "QMF2";

    /************* Static initialiser used to implement org.apache.qpid.server.model.Plugin *************/

    public static final String PLUGIN_TYPE = "MANAGEMENT-QMF2";
    public static final String QMF_DEFAULT_DIRECT = "qmf.default.direct";
    public static final String QMF_DEFAULT_TOPIC = "qmf.default.topic";


    /************************************ End of Static initialiser *************************************/

    private final Broker<?> _broker;          // Passed in by Plugin bootstrapping.
    private String _defaultVirtualHost; // Pulled from the broker attributes.

    @ManagedAttributeField
    private String _connectionURL;      // Pulled from the Plugin config.
    private QmfManagementAgent _agent;

    /**
     * Constructor, called at broker startup by QmfManagementFactory.createInstance().
     * @param attributes a Map containing configuration information for the Plugin.
     * @param broker the root Broker Management Object from which the other Management Objects may be obtained.
     */
    @ManagedObjectFactoryConstructor
    public QmfManagementPluginImpl(Map<String, Object> attributes, Broker broker)
    {
        super(attributes, broker);
        _broker = broker;
    }

    @Override
    protected void onOpen()
    {
        super.onOpen();
        _defaultVirtualHost = _broker.getDefaultVirtualHost();
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
    @StateTransition( currentState = State.UNINITIALIZED, desiredState = State.ACTIVE )
    private void doStart()
    {
        // Log "QMF2 Management Startup" message.
        getBroker().getEventLogger().message(ManagementConsoleMessages.STARTUP(OPERATIONAL_LOGGING_NAME));

        // Wrap main startup logic in a try/catch block catching Exception. The idea is that if anything goes
        // wrong with QmfManagementPlugin startup it shouldn't fatally prevent the Broker from starting, though
        // clearly QMF2 management will not be available.
        try
        {
            // Iterate through the Virtual Hosts looking for the default Virtual Host. When we find the default
            // we create the QMF exchanges then construct the QmfManagementAgent passing it the ConnectionURL.
            boolean foundDefaultVirtualHost = false;

            for(VirtualHostNode<?> node : _broker.getVirtualHostNodes())
            {
                VirtualHost<?, ?, ?> vhost = node.getVirtualHost();


                if (vhost != null && vhost.getName().equals(_defaultVirtualHost))
                {
                    foundDefaultVirtualHost = true;

                    // Create the QMF2 exchanges if necessary.
                    if (vhost.getChildByName(Exchange.class, QMF_DEFAULT_DIRECT) == null)
                    {
                        Map<String, Object> attributes = new HashMap<>();
                        attributes.put(Exchange.NAME, QMF_DEFAULT_DIRECT);
                        attributes.put(Exchange.TYPE, ExchangeDefaults.DIRECT_EXCHANGE_CLASS);
                        attributes.put(Exchange.STATE, State.ACTIVE);
                        attributes.put(Exchange.LIFETIME_POLICY, LifetimePolicy.PERMANENT);
                        attributes.put(Exchange.DURABLE, true);
                        vhost.createExchange(attributes);
                    }

                    if (vhost.getChildByName(Exchange.class, QMF_DEFAULT_TOPIC) == null)
                    {
                        Map<String, Object> attributes = new HashMap<>();
                        attributes.put(Exchange.NAME, QMF_DEFAULT_TOPIC);
                        attributes.put(Exchange.TYPE, ExchangeDefaults.TOPIC_EXCHANGE_CLASS);
                        attributes.put(Exchange.STATE, State.ACTIVE);
                        attributes.put(Exchange.LIFETIME_POLICY, LifetimePolicy.PERMANENT);
                        attributes.put(Exchange.DURABLE, true);
                        vhost.createExchange(attributes);
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
                getBroker().getEventLogger().message(ManagementConsoleMessages.READY(OPERATIONAL_LOGGING_NAME));
            }
        }
        catch (Exception e) // Catch and log any Exception so we avoid Plugin failures stopping Broker startup.
        {
            _log.error("Exception caught in QmfManagementPlugin.start()", e);
        }
    }

    /**
     * Stop the Plugin, closing the QMF Connection and logging "QMF2 Management Stopped".
     */
    @StateTransition( currentState = State.ACTIVE, desiredState = State.STOPPED )
    private void doStop()
    {
        // When the Plugin state gets set to STOPPED we close the QMF Connection.
        if (_agent != null)
        {
            _agent.close();
        }

        // Log "QMF2 Management Stopped" message (may not get displayed).
        getBroker().getEventLogger().message(ManagementConsoleMessages.STOPPED(OPERATIONAL_LOGGING_NAME));
    }

    @Override
    protected void onClose()
    {
        super.onClose();
        if (_agent != null)
        {
            _agent.close();
        }
    }

    /**
     * Accessor to retrieve the connectionURL attribute.
     * @return the JMS connectionURL of the Plugin.
     */
    public String getConnectionURL()
    {
        return _connectionURL;
    }
}
