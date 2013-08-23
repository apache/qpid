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

The Qpid Java Broker by default uses either JMX or an HTTP Management GUI & REST API, however by building
a QMF2 Agent as a Java Broker plugin it's possible to provide better synergy between the C++ and Java Brokers
and allow the Java Broker to be controlled by the Qpid Command Line tools and also via the QMF2 REST API
and GUI thus providing a unified view across a mixture of C++ and Java Brokers.


To build and install the Java Broker QMF2 plugin do:

ant all

***********************************************************************************************************
*         Note that the QmfManagementPlugin requires a version of the Java Broker >= 0.22                 *      
*                                                                                                         *
* The initial version of QmfManagementPlugin released to https://issues.apache.org/jira/browse/QPID-3675  *
* uses the ManagementPlugin and ManagementFactory interfaces which were introduced in Qpid 0.20 but then  *
* dropped in Qpid 0.22 so the version linked to the Jira is the only version that will work with the 0.20 *
* Java Broker.                                                                                            *
*                                                                                                         *
* As of Qpid 0.22 the Java Broker Plugin and Configuration APIs have changed. The Plugin API implements   *
* org.apache.qpid.server.model.Plugin extending org.apache.qpid.server.model.adapter.AbstractPluginAdapter*
* and uses org.apache.qpid.server.plugin.PluginFactory to create the Plugin instance.                     *
*                                                                                                         *
* The Plugin uses the org.apache.qpid.server.model.* classes and maps them to QmfData.                    *
*                                                                                                         *
* The intention is to track changes on Qpid Trunk, but the Plugin API is still under a bit of flux        *
***********************************************************************************************************

N.B. this requires that the Qpid jars (preferably qpid-all.jar) are on your CLASSPATH and that the
QPID_HOME environment variable is set to point to <qpid>/java/build (QPID_HOME is needed by the Java 
broker anyway).

The ant all target compiles the Java Broker QMF2 plugin and copies the qpid-broker-plugins-management-qmf2.jar
and qmf2.jar to $QPID_HOME/lib/plugins creating the plugins directory if it doesn't already exist. That
directory is one read by the qpid-server broker startup script and placed on the broker's CLASSPATH.



************************************************* Config **************************************************

As of Qpid 0.22 the way of configuring the Java Broker has moved to an initial config.json file in 
$QPID_WORK and updates via the Management Plugins. It is IMPORTANT to ensure that the following:

{
    "name" : "qmf2Management",
    "pluginType" : "MANAGEMENT-QMF2",
    "connectionURL" : "amqp://guest:guest@/?brokerlist='tcp://0.0.0.0:5672'"
  }

is added to the "plugins" list of $QPID_WORK/config.json or the Plugin will not start. There is also an "id"
property but if this is omitted it will get automatically created the first time the Plugin starts, which
is probably more useful that trying to make a UUID up.

The "connectionURL" property is particularly important. The Plugin connects to AMQP via the JMS Client so
"connectionURL" represents a valid Java ConnectionURL to the Broker so the username/password and any other
ConnectionURL configuration needs to be valid as for any other AMQP Connection to the Broker.


If the QMF GUI is to be used then either the -p option of QpidRestAPI.sh should be used to set the REST Server's
HTTP port to something other than 8080 or the "ports" list of $QPID_WORK/config.json should be modified from e.g.
{
    "id" : "1f2c4c7a-f33a-316b-b0e9-c02fab74469d",
    "name" : "8080-HTTP",
    "port" : 8080,
    "protocols" : [ "HTTP" ]
  }

to

{
    "id" : "1f2c4c7a-f33a-316b-b0e9-c02fab74469d",
    "name" : "9090-HTTP",
    "port" : 9090,
    "protocols" : [ "HTTP" ]
  }

So that the HTTP ports don't conflict.


In the top-level Broker config the "defaultVirtualHost" *MUST* be set to the name of the default Virtual Host
if this property is not set or is set to a name that doesn't match the name of one of the Virtual Hosts
then the Plugin will not start correctly.


******************************************** Troubleshooting **********************************************

If all has gone well you should see

[Broker] MNG-1001 : QMF2 Management Startup
[Broker] MNG-1004 : QMF2 Management Ready

in the Broker log messages when you run qpid-server, if you don't see these then there is likely to be a problem.

1. Check the directory $QPID_HOME/lib/plugins
That should contain qmf2.jar and qpid-broker-plugins-management-qmf2.jar if it doesn't then the Plugin hasn't been
built or deployed try doing

ant all

again.

2. If the jars mentioned above are present and correct in the plugins directory the most likely cause of failure
is incorrect configuration - see the Config section above, in particular the "plugins" list and "defaultVirtualHost".



******************************* Java Broker Management Plugin - mini HOWTO ********************************

The procedure for writing Plugins for the Java Broker isn't documented yet, so the following mini HOWTO
describes what I did. It may not be the *right* way but it seems to result in a Plugin that starts with the broker.

1. Create a PluginFactory

Management plugins are instantiated by Factory classes that implement the interface 
org.apache.qpid.server.plugin.PluginFactory e.g.


package org.apache.qpid.server.qmf2;

// Misc Imports
import java.util.Map;
import java.util.UUID;

// Java Broker Management Imports
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.plugin.PluginFactory;

public class QmfManagementFactory implements PluginFactory
{
    /**
     * This factory method creates an instance of QmfManagementPlugin called via the QpidServiceLoader.
     * @param id the UUID of the Plugin.
     * @param attributes a Map containing configuration information for the Plugin.
     * @param broker the root Broker Management Object from which the other Management Objects may be obtained.
     * @return the QmfManagementPlugin instance which creates a QMF2 Agent able to interrogate the broker Management
     * Objects and return their properties as QmfData.
     */
    @Override
    public Plugin createInstance(UUID id, Map<String, Object> attributes, Broker broker)
    {
        if (QmfManagementPlugin.PLUGIN_TYPE.equals(attributes.get(PLUGIN_TYPE)))
        {
            return new QmfManagementPlugin(id, broker, attributes);
        }
        else
        {
            return null;
        }
    }
}


2. Create a Plugin

Plugins implement the interface org.apache.qpid.server.model.Plugin which seems to be done by extending
org.apache.qpid.server.model.adapter.AbstractPluginAdapter. The main APIs are to override the setState()
and getName() methods and to implement a static initialiser to populate ConfiguredObject ATTRIBUTES.


package org.apache.qpid.server.qmf2;

// Misc Imports
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

// Java Broker Management Imports
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ManagementConsoleMessages;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.AbstractPluginAdapter;

import org.apache.qpid.server.plugin.PluginFactory;
import org.apache.qpid.server.util.MapValueConverter;

public class QmfManagementPlugin extends AbstractPluginAdapter
{
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
    private static final Collection<String> AVAILABLE_ATTRIBUTES = Collections.unmodifiableCollection(
        new HashSet<String>(Plugin.AVAILABLE_ATTRIBUTES){{
            add(NAME);
            add(CONNECTION_URL);
            add(PluginFactory.PLUGIN_TYPE);
    }});

    @SuppressWarnings("serial")
    private static final Map<String, Object> DEFAULTS = new HashMap<String, Object>(){{
        put(NAME, DEFAULT_NAME);
        put(CONNECTION_URL, DEFAULT_CONNECTION_URL);
        put(PluginFactory.PLUGIN_TYPE, PLUGIN_TYPE);
    }};

    @SuppressWarnings("serial")
    private static final Map<String, Type> ATTRIBUTE_TYPES = new HashMap<String, Type>(){{
        put(NAME, String.class);
        put(CONNECTION_URL, String.class);
        put(PluginFactory.PLUGIN_TYPE, String.class);
    }};

    /************************************ End of Static initialiser *************************************/

    /**
     * Constructor, called at broker startup by QmfManagementFactory.createInstance().
     * @param id the UUID of the Plugin.
     * @param attributes a Map containing configuration information for the Plugin.
     * @param broker the root Broker Management Object from which the other Management Objects may be obtained.
     */
    public QmfManagementPlugin(UUID id, Broker broker, Map<String, Object> attributes)
    {
        super(id, DEFAULTS, MapValueConverter.convert(attributes, ATTRIBUTE_TYPES), broker.getTaskExecutor());
        addParent(Broker.class, broker);
System.out.println("************ Constructing QmfManagementPlugin");
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
            return false;
        }
    }

    private void start()
    {
        // Log "QMF2 Management Startup" message.
        CurrentActor.get().message(ManagementConsoleMessages.STARTUP(OPERATIONAL_LOGGING_NAME));

        // Log QMF2 Management Ready message.
        CurrentActor.get().message(ManagementConsoleMessages.READY(OPERATIONAL_LOGGING_NAME));
    }

    private void stop()
    {
        // Log "QMF2 Management Stopped" message (may not get displayed).
        CurrentActor.get().message(ManagementConsoleMessages.STOPPED(OPERATIONAL_LOGGING_NAME));
    }

    /**
     * Get the name of this Plugin.
     * @return the Plugin name (default is "qmf2Management").
     */
    @Override // From org.apache.qpid.server.model.ConfiguredObject
    public String getName()
    {
        return (String)getAttribute(NAME);
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
        return AVAILABLE_ATTRIBUTES;
    }
}

3. Populate the META-INF

Qpid Java broker Plugins seem to use a facade over java.util.ServiceLoader called 
org.apache.qpid.server.plugin.QpidServiceLoader. In order to use a ServiceLoader the jar containing the Plugin
needs to contain a file:
META-INF/services/org.apache.qpid.server.plugin.PluginFactory

which contains the fully qualified class name of the class implementing PluginFactory e.g.

org.apache.qpid.server.qmf2.QmfManagementFactory


The most convenient way to achieve this is to include a ServiceProvider block in the jar ant task e.g.

	<jar destfile="build/lib/qpid-broker-plugins-management-qmf2.jar"
         basedir="build/scratch/qpid-broker-plugins-management-qmf2">

        <service type="org.apache.qpid.server.plugin.PluginFactory" 
                 provider="org.apache.qpid.server.qmf2.QmfManagementFactory"/>
	</jar>


4. Build the jar using your favourite method.

5. Deploy the jar to $QPID_HOME/lib/plugins

6. Ensure the config.json file in $QPID_WORK contains:
{
    "name" : "qmf2Management",
    "pluginType" : "MANAGEMENT-QMF2",
    "connectionURL" : "amqp://guest:guest@/?brokerlist='tcp://0.0.0.0:5672'"
  }

(or whatever the name/pluginType/etc. of the actual Plugin is)
in the "plugins" list (the id property will be added automatically when the Broker starts)

7. Start up the Java Broker via qpid-server


If all has gone well the Plugin should start up. Clearly you'll probably want to add something to the Plugin so
that it actually does something vaguely useful :-)




