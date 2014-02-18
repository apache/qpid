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

package org.apache.qpid.server.qmf2.agentdata;

// Misc Imports
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// Simple Logging Facade 4 Java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// QMF2 Imports
import org.apache.qpid.qmf2.agent.Agent;
import org.apache.qpid.qmf2.agent.QmfAgentData;
import org.apache.qpid.qmf2.common.Handle;
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfData;
/*import org.apache.qpid.qmf2.common.QmfEvent;
import org.apache.qpid.qmf2.common.QmfEventListener;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.QmfType;*/
import org.apache.qpid.qmf2.common.SchemaEventClass;
import org.apache.qpid.qmf2.common.SchemaMethod;
import org.apache.qpid.qmf2.common.SchemaObjectClass;
import org.apache.qpid.qmf2.common.SchemaProperty;

// Java Broker model Imports
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;

/**
 * This class provides a concrete implementation of QmfAgentData for the Broker Management Object.
 * In general it's possible to use QmfAgentData without sub-classing as it's really a "bean" style class
 * that retains its properties in a Map, but in the case of the Java Broker Management Agent it's useful
 * to sub-class as we need to map between the properties/statistics as specified in the Java Broker
 * management model and those specified in qpid/spec/management-schema.xml which is what the C++ broker 
 * uses. This class retains a reference to its peer org.apache.qpid.server.model.Broker and does the
 * necessary mapping when its mapEncode() method is called (which is used to serialise the QmfAgentData).
 *
 * @author Fraser Adams
 */
public class Broker extends QmfAgentData
{
    private static final Logger _log = LoggerFactory.getLogger(Broker.class);

    /**
     * This static initialiser block initialises the QMF2 Schema information needed by the Agent to find
     * QmfAgentData Objects of a given type.
     */
    private static final SchemaObjectClass _schema;
    public static SchemaObjectClass getSchema()
    {
        return _schema;
    }

    static
    {
        // Declare the schema for the QMF2 broker class.
        _schema = new SchemaObjectClass("org.apache.qpid.broker", "broker");

        // TODO
        //_schema.addProperty(new SchemaProperty("whatHappened", QmfType.TYPE_STRING));
    }

    private final org.apache.qpid.server.model.Broker _broker; // Passed in by Plugin bootstrapping.
    private final String _defaultVirtualHost; // Pulled from the broker attributes.

    /**
     * This inner class parses the name String that was passed in as a QMF method argument.
     * There are a few quirks with this name. In the first instance it may be prefixed with Virtual Host information
     * e.g. [vhost:<vhost-name>/]<queue-name> this is because in order to allow Java Broker QMF to work with things like
     * qpid-config which are not particularly Virtual Host aware prefixing the names seemed to be the most natural,
     * if slightly ugly approach. In addition the way bindings are named is of the form:
     * <exchange-name>/<queue-name>[/<binding-key>] so we need a mechanism to parse the relevant information.
     * <p>
     * N.B. the parsing that takes place in this class makes an assumption that there are no valid exchange or queue
     * names that contain a "/". This is probably a reasonable assumption given the way that a binding name is
     * constructed, but it's worth recording the restriction here in case such a beast crops up.
     * <p>
     * This class also provides accessors that allow the Exchange, Queue and Binding ConfiguredObjects for the
     * name parsed in the Constructor to be retrieved. This is generally useful because although in QMF the create
     * and delete methods are invoked on the Broker object in the Java Broker ConfiguredObject model the underlying
     * methods are distributed across a number of different classes.
     */
    private class NameParser
    {
        private String _vhostName = _defaultVirtualHost;
        private VirtualHost _vhost = null;
        private String _exchangeName = "";
        private Exchange _exchange = null;
        private String _queueName = "";
        private Queue _queue = null;
        private String _bindingKey = "";
        private Binding _binding = null;

        /**
         * NameParser Constructor.
         * The Constructor actually does the majority of the parsing, the remaining method are largely just accessors.
         *
         * @param name the name argument that was retrieved from the QMF method inArgs. This will be the exchange name,
         * the queue name or the binding name (which is of the form <exchange-name>/<queue-name>[/<binding-key>])
         * exchange and queue names may be prefixed by a Virtual Host name e.g. [vhost:<vhost-name>/]<queue-name>
         * @param type the type argument that was retrieved from the QMF method inArgs. Valid types are "exchange, 
         * "queue" or "binding".
         */
        public NameParser(final String name, final String type)
        {
            boolean malformedVHostName = false;
            String[] splitName = name.split("/"); // A slash is used as a separator in a couple of scenarios.
            if (name.startsWith("vhost:"))
            {
                if (splitName.length == 1) // If it starts with vhost: the name should also contain at least one "/".
                {
                    malformedVHostName = true;
                    _vhostName = name;
                }
                else
                {
                    _vhostName = splitName[0];
                    _vhostName = _vhostName.substring(6, _vhostName.length());
                }
            }

            // If the vhostName isn't malformed then try to find the actual Virtual Host that it relates to.
            // If it is malformed the vhost stays set to null, which will cause an exception to be returned later.
            if (!malformedVHostName)
            {
                for (VirtualHost vhost : _broker.getVirtualHosts())
                {
                    if (vhost.getName().equals(_vhostName))
                    {
                        _vhost = vhost;
                        break;
                    }
                }
            }

            // Populate the exchange, queue and binding names. We only populate the names in the constructor
            // when we actually want to find the Object associated with the name we do it "on demand" in the
            // relevant accessor and cache the result.
            if (type.equals("exchange"))
            {
                _exchangeName = splitName[splitName.length - 1];
            }
            else if (type.equals("queue"))
            {
                _queueName = splitName[splitName.length - 1];
            }
            else if (type.equals("binding"))
            { // TODO is there a way to make this parse less nasty and a bit more elegant....
                int i = 0;
                String vhost1Name = _defaultVirtualHost; // The exchange and queue vhostName need to be the same.
                if (splitName[i].startsWith("vhost:")) // Does the exchange name specify a vhost?
                {
                    vhost1Name = splitName[i];
                    i++;
                }

                if (i < splitName.length) // Extract the exchange name sans vhost part.
                {
                    _exchangeName = splitName[i];
                    i++;
                }

                String vhost2Name = _defaultVirtualHost;
                if (i < splitName.length && splitName[i].startsWith("vhost:")) // Does the queue name specify a vhost?
                {
                    vhost2Name = splitName[i];
                    i++;
                }

                // If the exchange and queue vhost names differ set _vhost and _vhostName to null which causes
                // an exception that says "VirtualHost names for exchange and queue must match.".
                if (!vhost2Name.equals(vhost1Name))
                {
                    _vhost = null;
                    _vhostName = null;
                }

                if (i < splitName.length) // Extract the queue name sans vhost part.
                {
                    _queueName = splitName[i];
                    i++;
                }

                if (i < splitName.length) // Extract the binding key if present (it's optional).
                {
                    _bindingKey = splitName[i];
                    i++;
                }
            } // End of binding name parse.
        }

        // NameParser accessors.

        /**
         * Retrieves the name of the Virtual Host that was parsed from the name supplied in the Constructor.
         * @return the parsed Virtual Host name (may be an empty String).
         */
        public String getVirtualHostName()
        {
            return _vhostName;
        }

        /**
         * Retrieves the Virtual Host with the name that was parsed from the name supplied in the Constructor.
         * @return the Virtual Host with the name that was parsed from the name supplied in the Constructor (may be null).
         */
        public VirtualHost getVirtualHost()
        {
            return _vhost;
        }

        /**
         * Retrieves the name of the Exchange that was parsed from the name supplied in the Constructor.
         * @return the parsed Exchange name (may be an empty String).
         */
        public String getExchangeName()
        {
            return _exchangeName;
        }

        /**
         * Retrieves the Exchange with the name that was parsed from the name supplied in the Constructor.
         * @return the Exchange with the name that was parsed from the name supplied in the Constructor (may be null).
         */
        public Exchange getExchange()
        {
            // If we've not previously cached the _exchange and the previously parsed Virtual Host isn't null we do a 
            // look up for the actual Exchange with the name _exchangeName and cache it.
            if (_exchange == null && _vhost != null)
            {
                for (Exchange exchange : _vhost.getExchanges())
                {
                    if (exchange.getName().equals(_exchangeName))
                    {
                        _exchange = exchange;
                        break;
                    }
                }
            }

            return _exchange;
        }

        /**
         * Retrieves the name of the Queue that was parsed from the name supplied in the Constructor.
         * @return the parsed Queue name (may be an empty String).
         */
        public String getQueueName()
        {
            return _queueName;
        }

        /**
         * Retrieves the Queue with the name that was parsed from the name supplied in the Constructor.
         * @return the Queue with the name that was parsed from the name supplied in the Constructor (may be null).
         */
        public Queue getQueue()
        {
            // If we've not previously cached the _queue and the previously parsed Virtual Host isn't null we do a 
            // look up for the actual Queue with the name _queueName and cache it.
            if (_queue == null && _vhost != null)
            {
                for (Queue queue : _vhost.getQueues())
                {
                    if (queue.getName().equals(_queueName))
                    {
                        _queue = queue;
                        break;
                    }
                }
            }

            return _queue;
        }

        /**
         * Retrieves the name of the Binding that was parsed from the name supplied in the Constructor.
         * @return the parsed Binding name (may be an empty String).
         */
        public String getBindingKey()
        {
            return _bindingKey;
        }

        /**
         * Retrieves the Binding with the name that was parsed from the name supplied in the Constructor.
         * @return the Binding with the name that was parsed from the name supplied in the Constructor (may be null).
         */
        public Binding getBinding()
        {
            // In order to retrieve a Binding it's first necessary to get the Exchange (or Queue) ConfiguredObject.
            _exchange = getExchange(); // Need to get it via the accessor as it's initialised by lazy evaluation.

            // If we've not previously cached the _binding and the previously retrieved Exchange isn't null we do a 
            // look up for the actual Binding with the name _bindingKey and cache it.
            if (_binding == null && _exchange != null)
            {
                for (Binding binding : _exchange.getBindings())
                {
                    if (binding.getName().equals(_bindingKey))
                    {
                        _binding = binding;
                        break;
                    }
                }
            }

            return _binding;
        }
    } // End of class NameParser

    /**
     * Broker Constructor.
     * @param broker the root Broker Management Object from which the other Management Objects may be obtained.
     */
    public Broker(final org.apache.qpid.server.model.Broker broker)
    {
        super(getSchema());
        _broker = broker;
        _defaultVirtualHost = (String)broker.getAttribute("defaultVirtualHost");
        int amqpPort = 5672; // Default AMQP Port.

        // Search through the available Ports on this Broker looking for the AMQP Port. When we find the
        // AMQP Port we record that in amqpPort;
        for (Port port : _broker.getPorts())
        {
            boolean isAMQP = false;
            for (Protocol protocol : port.getProtocols())
            {
                isAMQP = protocol.isAMQP();
                if (isAMQP)
                {
                    break;
                }
            }

            if (isAMQP)
            {
                amqpPort = port.getPort();
                break;
            }
        }

        String port = "" + amqpPort;

        // systemRef is ignored in this implementation.
        // stagingThreshold is ignored in this implementation (it's deprecated anyway I believe).

        // Use this name to be fairly consistent with C++ broker which uses "amqp-broker".
        // N.B. although it's useful to be able to distinguish between C++ and Java brokers note that the
        // _object_name in the ObjectId that we set below uses actually uses "amqp-broker" vice "amqp-java-broker",
        // this is because qpid-config uses a "hardcoded" ObjectId to invoke methods so we need to use the same name.
        setValue("name", "amqp-java-broker");
        setValue("port", port);

        // workerThreads doesn't *appear* to be configurable in the Java Broker, looks like there's no pool and the
        // Threads just grow with the number of Connections?
        setValue("workerThreads", 0);

        // maxConns doesn't *appear* to be configurable in the Java Broker.
        setValue("maxConns", 0);

        // The Java Broker ServerSocket seems to be created in org.apache.qpid.transport.network.io.IoNetworkTransport
        // In AcceptingThread. The does't appear to use any configuration for ServerSocket, which suggests that the
        // backlog is the default value which is assumed to be 10.
        setValue("connBacklog", 10);

        // "Technically" this isn't quite the same as for the C++ broker, which pushes management data to a particular 
        // topic - the subscription "qmf.default.topic/agent.ind.#" grabs that plus heartbeats for the C++ broker.
        // This Agent allows the use of the QMF2 Query Subscriptions (which the C++ broker does not!! - the Console
        // class fakes this client side for the C++ broker. TODO make sure that the Console does not fake for Java broker.
        setValue("mgmtPublish", true);
        setValue("mgmtPubInterval", 10);

        setValue("version", org.apache.qpid.common.QpidProperties.getReleaseVersion());
        setValue("dataDir", System.getProperty("QPID_WORK"));

        // ObjectId needs to be set here in Broker because the QMF2 version of qpid-config uses a hardcoded
        // _object_name of "org.apache.qpid.broker:broker:amqp-broker" in the _object_id that it sets.
        // It *shouldn't* do this and should really use the _object_id of the broker object returned by
        // getObjects("broker"), but it does. The following line causes the Agent to use the explicit
        // ObjectId below rather than constructing its own, which fixes the qpid-config issue.
        // Note we use "amqp-broker" in the ObjectId to be compatible with qpid-config but we set the actual
        // name to "amqp-java-broker" as it's useful to be able to distinguish between C++ and Java Brokers.
        setObjectId(new ObjectId("", "org.apache.qpid.broker:broker:amqp-broker", 0));
    }

    /**
     * This helper method checks the supplied properties Map for the "alternate-exchange" property, if it is present
     * the property is removed from the map and the alternate exchange is parsed to recover the Virtual Host name
     * and the actual alternate exchange name. If the alternate exchange Virtual Host name is not the same as the
     * supplied vhostName this method returns "invalid" otherwise it returns the alternate exchange name or null.
     *
     * @param vhostName the Virtual Host name that we want to compare the alternate exchange's Virtual Host name with.
     * @param properties a Map of properties that might contain "alternate-exchange".
     * @return the alternate exchange name if present, null if not present or "invalid" if the Virtual Host name that
     * was parsed from the alternate exchange doesn't match the name supplied in the vhostName parameter.
     */
    private String parseAlternateExchange(String vhostName, Map<String, Object> properties)
    {
        String alternateExchange = null;
        Object property = properties.get("alternate-exchange");
        if (property != null && property instanceof String) // Alternate exchange has been specified.
        {
            alternateExchange = property.toString();
            properties.remove("alternate-exchange");

            String altExVhostName = _defaultVirtualHost;
            String[] splitName = alternateExchange.split("/"); 
            if (alternateExchange.startsWith("vhost:"))
            {
                altExVhostName = splitName[0];
                altExVhostName = altExVhostName.substring(6, altExVhostName.length());
            }

            // If the Virtual Hosts differ raise an exception and return.
            if (!altExVhostName.equals(vhostName))
            {
                return "invalid";
            }
        }

        return alternateExchange;
    }

    /**
     * This method acts as a single entry point for QMF methods invoked on the Broker Object.
     *
     * @param agent the org.apache.qpid.qmf2.agent.Agent instance that we call methodResponse() and raiseException() on.
     * @param handle the reply handle used by methodResponse() and raiseException().
     * @param methodName the name of the QMF method being invoked.
     * @param inArgs a Map of input arguments wrapped in a QmfData Object.
     */
    public void invokeMethod(Agent agent, Handle handle, String methodName, QmfData inArgs)
    {
        if (methodName.equals("create") || methodName.equals("delete"))
        {
            QmfData outArgs = new QmfData();

            String name = inArgs.getStringValue("name");
            String type = inArgs.getStringValue("type");

            NameParser nameParser = new NameParser(name, type);
            String vhostName = nameParser.getVirtualHostName();
            VirtualHost vhost = nameParser.getVirtualHost();

            if (vhost == null)
            {
                if (vhostName == null)
                {
                    agent.raiseException(handle, "VirtualHost names for exchange and queue must match.");
                }
                else
                {
                    agent.raiseException(handle, "VirtualHost " + vhostName + " not found.");
                }
            }
            else
            {
                if (methodName.equals("create")) // method = create
                {
                    try
                    {
                        //boolean strict = inArgs.getBooleanValue("strict");
                        Map<String, Object> properties = inArgs.getValue("properties");

                        boolean durable = false;
                        Object property = properties.get("durable");
                        if (property != null && property instanceof Boolean)
                        {
                            Boolean durableProperty = (Boolean)property;
                            durable = durableProperty.booleanValue();
                            properties.remove("durable");
                        }

                        if (type.equals("exchange")) // create exchange.
                        {
/*
System.out.println("Create Exchange");
System.out.println("vhostName = " + vhostName);
System.out.println("exchange name = " + nameParser.getExchangeName());
System.out.println("properties = " + properties);
*/
                            String exchangeType = "";
                            property = properties.get("exchange-type");
                            if (property != null && property instanceof String)
                            {
                                exchangeType = property.toString();
                                properties.remove("exchange-type");
                            }

                            String alternateExchange = parseAlternateExchange(vhostName, properties);
                            if (alternateExchange != null && alternateExchange.equals("invalid"))
                            {
                                agent.raiseException(handle, "Alternate Exchange must belong to the same Virtual Host as the Exchange being added.");
                                return;
                            }

                            // TODO delete this block when adding an AlternateExchange is implemented.
                            if (alternateExchange != null)
                            {
                                agent.raiseException(handle,
                                    "Setting an Alternate Exchange on an Exchange is not yet implemented.");
                                return;
                            }

                            // Note that for Qpid 0.20 the "qpid.msg_sequence=1" and "qpid.ive=1" properties are
                            // not suppored, indeed no exchange properties seem to be supported yet.
                            vhost.createExchange(nameParser.getExchangeName(), State.ACTIVE, durable,
                                                 LifetimePolicy.PERMANENT, 0l, exchangeType, properties);
                            if (alternateExchange != null)
                            {
                                // TODO set Alternate Exchange. There doesn't seem to be a way to do this yet!!!
                            }
                        } // End of create exchange.
                        else if (type.equals("queue")) // create queue.
                        {
/*
System.out.println("Create Queue");
System.out.println("vhostName = " + vhostName);
System.out.println("queue name = " + nameParser.getQueueName());
System.out.println("properties = " + properties);
*/

                            // TODO Try to map from the QMF create queue properties to the closest equivalents on
                            // the Java Broker. Unfortunately there are a *lot* of frustrating little differences.


                            String alternateExchange = parseAlternateExchange(vhostName, properties);
                            if (alternateExchange != null && alternateExchange.equals("invalid"))
                            {
                                agent.raiseException(handle, "Alternate Exchange must belong to the same Virtual Host as the Queue being added.");
                                return;
                            }

                            // I don't *think* that it make sense to allow setting exclusive or autoDelete to
                            // a queue created from config.
                            Map<String,Object> attributes = new HashMap<String,Object>(properties);
                            attributes.put(Queue.NAME, nameParser.getQueueName());
                            attributes.put(Queue.DURABLE, durable);
                            attributes.put(Queue.LIFETIME_POLICY, LifetimePolicy.PERMANENT);

                            Queue queue = vhost.createQueue(attributes);

                            // Set the queue's alternateExchange, which is just a little bit involved......
                            // The queue.setAttribute() method needs an org.apache.qpid.server.model.Exchange instance
                            // not just a name, so we look up org.apache.qpid.server.qmf2.agentdata.Exchange by ID
                            // and get its associated org.apache.qpid.server.model.Exchange. We can do a look up by ID
                            // because we needed to use ObjectIds that were based on names in order to allow qpid-config
                            // to work, so we may as well make use of this convenience here too.
                            if (alternateExchange != null)
                            {
                                ObjectId objectId =
                                        new ObjectId("", "org.apache.qpid.broker:exchange:" + alternateExchange, 0);

                                // Look up Exchange QmfAgentData by ObjectId from the Agent's internal Object store.
                                QmfAgentData object = agent.getObject(objectId);
                                if (object != null)
                                {
                                    org.apache.qpid.server.qmf2.agentdata.Exchange ex = 
                                        (org.apache.qpid.server.qmf2.agentdata.Exchange)object;

                                    Exchange altEx = ex.getExchange();
                                    queue.setAttribute("alternateExchange", null, altEx);
                                }
                            }
                        }
                        else if (type.equals("binding")) // create binding.
                        {
                            Exchange exchange = nameParser.getExchange();
                            if (exchange == null)
                            {
                                agent.raiseException(handle, "Cannot create binding on Exchange " +
                                                     nameParser.getExchangeName());
                                return;
                            }
                            else
                            {
                                Map<String, Object> attributes = Collections.emptyMap();
                                exchange.createBinding(nameParser.getBindingKey(), nameParser.getQueue(),
                                                       properties, attributes);
                            }
                        }

                        agent.methodResponse(methodName, handle, outArgs, null);
                    }
                    catch (Exception e)
                    {
                        agent.raiseException(handle, e.getMessage());
                    }
                }
                else // method = delete
                {
                    try
                    {
                        if (type.equals("exchange")) // delete exchange.
                        {
                            Exchange exchange = nameParser.getExchange();
                            if (exchange != null)
                            {
                                exchange.delete();
                            }
                        }
                        else if (type.equals("queue")) // delete queue.
                        {
                            Queue queue = nameParser.getQueue();
                            if (queue != null)
                            {
                                queue.delete();
                            }
                        }
                        else if (type.equals("binding")) // delete binding.
                        {
                            Binding binding = nameParser.getBinding();
                            if (binding != null)
                            {
                                binding.delete();
                            }
                        }

                        agent.methodResponse(methodName, handle, outArgs, null);
                    }
                    catch (Exception e)
                    {
                        agent.raiseException(handle, e.getMessage());
                    }
                }
            }
        }
        else // If methodName is not create or delete.
        {
            agent.raiseException(handle, methodName + " not yet implemented on Broker.");
        }
    } // End of invokeMethod.

    /**
     * This method maps the org.apache.qpid.server.model.Broker to QMF2 broker properties where possible then
     * serialises into the underlying Map for transmission via AMQP. This method is called by handleQueryRequest()
     * in the org.apache.qpid.qmf2.agent.Agent class implementing the main QMF2 Agent behaviour.
     * 
     * @return the underlying map. 
     */
    @Override
    public Map<String, Object> mapEncode()
    {
        update(); // Need to do update before setting uptime in order to get the latest getUpdateTime() value.

        // Not sure if there's an "official" broker uptime anywhere, but as the QmfManagementAgent is created when
        // the broker is and the Broker object is created then too the following approach should be good enough.
        setValue("uptime", getUpdateTime() - getCreateTime());

        return super.mapEncode();
    }
}
