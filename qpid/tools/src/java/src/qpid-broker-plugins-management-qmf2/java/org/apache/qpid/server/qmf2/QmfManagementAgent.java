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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Simple Logging Facade 4 Java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// QMF2 Imports
import org.apache.qpid.qmf2.agent.Agent;
import org.apache.qpid.qmf2.agent.MethodCallParams;
import org.apache.qpid.qmf2.agent.MethodCallWorkItem;
import org.apache.qpid.qmf2.agent.QmfAgentData;
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfData;
import org.apache.qpid.qmf2.common.QmfEvent;
import org.apache.qpid.qmf2.common.QmfEventListener;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.QmfType;
import org.apache.qpid.qmf2.common.WorkItem;
import org.apache.qpid.qmf2.util.ConnectionHelper;
import static org.apache.qpid.qmf2.common.WorkItem.WorkItemType.*;

// Java Broker model Imports
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.Consumer;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Publisher;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.Session;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;

/**
 * This class implements a QMF2 Agent providing access to the Java broker Management Objects via QMF2 thus
 * allowing the Java broker to be managed in the same way to the C++ broker.
 * <p>
 * The intention is for the QmfManagementAgent to conform to the same Management Schema as the C++ broker
 * (e.g. as specified in <qpid>/specs/management-schema.xml) in order to provide maximum cohesion between
 * the to broker implementations, however that's not entirely possible given differences between the underlying
 * Management models.
 * <p>
 * This Plugin attempts to map properties from the Java org.apache.qpid.server.model.* classes to equivalent
 * properties and statistics in the C++ broker's Management Schema rather than expose them "natively", this is
 * in order to try and maximise alignment between the two implementations and to try to allow the Java broker
 * to be managed by the Command Line tools used with the C++ broker such as qpid-config etc. it's also to
 * enable the Java broker to be accessed via the QMF2 REST API and GUI.
 *
 * @author Fraser Adams
 */
public class QmfManagementAgent implements ConfigurationChangeListener, QmfEventListener
{
    private static final Logger _log = LoggerFactory.getLogger(QmfManagementAgent.class);

    // Set heartbeat interval to 10 seconds. TODO Should probably be config driven, but I *think* that this is
    // different than "heartbeat.delay" and "heartbeat.timeoutFactor" currently present in the config?
    private static final int HEARTBEAT_INTERVAL = 10;
    private Agent _agent = null;

    // The first Connection Object relates to the QmfManagementAgent, we use this flag to avoid mapping that Connection
    // to a QMF Object thus hiding it from Consoles. This is done to provide consistency with the C++ broker which
    // also "hides" its own private AMQP Connections, Queues & Bindings.
    private boolean agentConnection = true;

    private final Broker _broker; // Passed in by Plugin bootstrapping.
    private final String _defaultVirtualHost; // Pulled from the broker attributes.

    /**
     * A Map of QmfAgentData keyed by ConfiguredObject. This is mainly used for Management Object "lifecycle management".
     * In an ideal world the Agent class could retain all information, but I want to track ConfiguredObject state and
     * use that to create and delete QmfAgentData data, which means I need to be able to *find* the QmfAgentData and
     * the *official* Agent API doesn't have a public method to query Objects (though the evaluateQuery() method used
     * by Query Subscriptions could be used, but it's not really a "public API method" so probably best not to use it)
     * Arguably this is what the AgentExternal subclass of Agent is for whereby queries are handled in the Agent
     * implementation by handling "(wi.getType() == QUERY)" but the AgentExternal API forces some constructs that are 
     * actually likely to be less efficient, as an example sending a separate queryResponse() for each object forces a 
     * look up of a List of QmfAgentData objects keyed by the consoleHandle for each call. There is also the need to 
     * separately iterate through the List of QmfAgentData objects thus created to create the mapEncoded list needed
     * for sending via the QMF2 protocol.
     * <p>
     * So rather than go through all that faff we simply retain an additional Map as below which allows navigation
     * between the ConfiguredObject and QmfAgentData. The subclasses of QmfAgentData will contain references to
     * allow navigation back to the concrete subclasses of ConfiguredObject if necessary.
     * The capacity of 100 is pretty arbitrary but the default of 16 seems too low for a ManagementAgent.
     */
    private Map<ConfiguredObject, QmfAgentData> _objects = new ConcurrentHashMap<ConfiguredObject, QmfAgentData>(100);

    /**
     * Constructor. Creates the AMQP Connection to the broker and starts the QMF2 Agent.
     * @param url the Connection URL to be used to construct the AMQP Connection.
     * @param broker the root Broker Management Object from which the other Management Objects may be obtained.
     * to work without explicitly setting a Virtual Host, which I think is necessary because the C++ broker and
     * the python command line tools aren't currently Virtual Host aware (are they?). The intention is to mark
     * queues and exchanges with [vhost:<vhost-name>/]<object-name> in other words if we want to add things to
     * the non-default Virtual Host prefix their names with [vhost:<vhost-name>/]. This approach *ought* to allow
     * non-Virtual Host aware command line tools the ability to add queues/exchanges to a particular vhost.
     */
    public QmfManagementAgent(final String url, final Broker broker)
    {
        _broker = broker;
        _defaultVirtualHost = (String)broker.getAttribute("defaultVirtualHost");

        try
        {
            // Create the actual JMS Connection. ConnectionHelper allows us to work with a variety of URL
            // formats so we can abstract away from the somewhat complex Java AMQP URL format.
            javax.jms.Connection connection = ConnectionHelper.createConnection(url);
            if (connection == null)
            {
                _log.info("QmfManagementAgent Constructor failed due to null AMQP Connection");
            }
            else
            {
                _agent = new Agent(this, HEARTBEAT_INTERVAL);
                // Vendor and Product are deliberately set to be the same as for the C++ broker.
                _agent.setVendor("apache.org");
                _agent.setProduct("qpidd");
                _agent.setConnection(connection);

                // Register the schema for the Management Objects. These don't have to be completely populated
                // the minimum is to register package name and class name for the QmfAgentData.
                _agent.registerObjectClass(org.apache.qpid.server.qmf2.agentdata.Broker.getSchema());
                _agent.registerObjectClass(org.apache.qpid.server.qmf2.agentdata.Connection.getSchema());
                _agent.registerEventClass(org.apache.qpid.server.qmf2.agentdata.Connection.getClientConnectSchema());
                _agent.registerEventClass(org.apache.qpid.server.qmf2.agentdata.Connection.getClientDisconnectSchema());

                _agent.registerObjectClass(org.apache.qpid.server.qmf2.agentdata.Exchange.getSchema());
                _agent.registerEventClass(org.apache.qpid.server.qmf2.agentdata.Exchange.getExchangeDeclareSchema());
                _agent.registerEventClass(org.apache.qpid.server.qmf2.agentdata.Exchange.getExchangeDeleteSchema());

                _agent.registerObjectClass(org.apache.qpid.server.qmf2.agentdata.Queue.getSchema());
                _agent.registerEventClass(org.apache.qpid.server.qmf2.agentdata.Queue.getQueueDeclareSchema());
                _agent.registerEventClass(org.apache.qpid.server.qmf2.agentdata.Queue.getQueueDeleteSchema());

                _agent.registerObjectClass(org.apache.qpid.server.qmf2.agentdata.Binding.getSchema());
                _agent.registerEventClass(org.apache.qpid.server.qmf2.agentdata.Binding.getBindSchema());
                _agent.registerEventClass(org.apache.qpid.server.qmf2.agentdata.Binding.getUnbindSchema());

                _agent.registerObjectClass(org.apache.qpid.server.qmf2.agentdata.Subscription.getSchema());
                _agent.registerEventClass(org.apache.qpid.server.qmf2.agentdata.Subscription.getSubscribeSchema());
                _agent.registerEventClass(org.apache.qpid.server.qmf2.agentdata.Subscription.getUnsubscribeSchema());

                _agent.registerObjectClass(org.apache.qpid.server.qmf2.agentdata.Session.getSchema());

                // Initialise QmfAgentData Objects and track changes to the broker Management Objects.
                registerConfigurationChangeListeners();
            }
        }
        catch (QmfException qmfe)
        {
            _log.info("QmfException {} caught in QmfManagementAgent Constructor", qmfe.getMessage());
            _agent = null; // Causes isConnected() to be false and thus prevents the "QMF2 Management Ready" message.
        }
        catch (Exception e)
        {
            _log.info("Exception {} caught in QmfManagementAgent Constructor", e.getMessage());
            _agent = null; // Causes isConnected() to be false and thus prevents the "QMF2 Management Ready" message.
        }
    }

    /**
     * Close the QmfManagementAgent clearing the QMF2 Agent and freeing its resources.
     */
    public void close()
    {
        if (isConnected())
        {
            _agent.destroy();
        }
    }

    /**
     * Returns whether the Agent is connected and running.
     * @return true if the Agent is connected and running otherwise return false.
     */
    public boolean isConnected()
    {
        return _agent != null;
    }

    /**
     * This method initialises the initial set of QmfAgentData Objects and tracks changes to the broker Management 
     * Objects via the childAdded() method call.
     */
    private void registerConfigurationChangeListeners()
    {
        childAdded(null, _broker);

        for (VirtualHost vhost : _broker.getVirtualHosts())
        {
            // We don't add QmfAgentData VirtualHost objects. Possibly TODO, but it's a bit awkward at the moment
            // because (as of Qpid 0.20) the C++ broker doesn't *seem* to do much with them and the command line
            // tools such as qpid-config don't appear to be VirtualHost aware. A way to stay compatible is to
            // mark queues, exchanges etc with [vhost:<vhost-name>/]<object-name> (see Constructor comments).
            vhost.addChangeListener(this);

            for (Connection connection : vhost.getConnections())
            {
                childAdded(vhost, connection);
 
                for (Session session : connection.getSessions())
                {
                    childAdded(connection, session);

                    if (session.getConsumers() != null)
                    {
                        for (Consumer subscription : session.getConsumers())
                        {
                            childAdded(session, subscription);
                        }
                    }
                }
            }

            // The code blocks for adding Bindings (and adding Queues) contain checks to see if what is being added
            // relates to Queues or Bindings for the QmfManagementAgent. If they are QmfManagementAgent related
            // we avoid registering the Object as a QMF Object, in other words we "hide" QmfManagementAgent QMF Objects.
            // This is done to be consistent with the C++ broker which also "hides" its own Connection, Queue & Binding.
            for (Exchange exchange : vhost.getExchanges())
            {
                childAdded(vhost, exchange);

                for (Binding binding : exchange.getBindings())
                {
                    String key = binding.getName();
                    if (key.equals("broker") || key.equals("console.request.agent_locate") ||
                        key.startsWith("apache.org:qpidd:") || key.startsWith("TempQueue"))
                    { // Don't add QMF related Bindings in registerConfigurationChangeListeners as those will relate
                    } // to the Agent and we want to "hide" those.
                    else
                    {
                        childAdded(exchange, binding);
                    }
                }
            }

            for (Queue queue : vhost.getQueues())
            {
                boolean agentQueue = false;
                for (Binding binding : queue.getBindings())
                {
                    String key = binding.getName();
                    if (key.equals("broker") || key.equals("console.request.agent_locate") ||
                        key.startsWith("apache.org:qpidd:"))
                    {
                        agentQueue = true;
                        break;
                    }
                }

                // Don't add QMF related bindings or Queues in registerConfigurationChangeListeners as those will
                // relate to the Agent itself and we want to "hide" those to be consistent with the C++ Broker.
                if (!agentQueue)
                {
                    childAdded(vhost, queue);

                    for (Binding binding : queue.getBindings())
                    {
                        childAdded(queue, binding);
                    }

                    for (Consumer subscription : queue.getConsumers())
                    {
                        childAdded(queue, subscription);
                    }
                }
            }
        }
    }


    // ************************* ConfigurationChangeListener implementation methods *************************

    /**
     * ConfigurationChangeListener method called when the state is changed (ignored here).
     * @param object the object being modified.
     * @param oldState the state of the object prior to this method call.
     * @param newState the desired state of the object.
     */
    @Override
    public void stateChanged(final ConfiguredObject object, final State oldState, final State newState)
    {
        // no-op
    }

    /**
     * ConfigurationChangeListener method called when an attribute is set (ignored here).
     * @param object the object being modified.
     * @param attributeName the name of the object attribute that we want to change.
     * @param oldAttributeValue the value of the attribute prior to this method call.
     * @param newAttributeValue the desired value of the attribute.
     */
    @Override
    public void attributeSet(ConfiguredObject object, String attributeName,
                             Object oldAttributeValue, Object newAttributeValue)
    {
        // no-op
    }

    /**
     * ConfigurationChangeListener method called when a child ConfiguredObject is added.
     * <p>
     * This method checks the type of the child ConfiguredObject that has been added and creates the equivalent
     * QMF2 Management Object if one doesn't already exist. In most cases it's a one-to-one mapping, but for
     * Binding for example the Binding child is added to both Queue and Exchange so we only create the Binding
     * QMF2 Management Object once and add the queueRef and exchangeRef reference properties referencing the Queue
     * and Exchange parent Objects respectively, Similarly for Consumer (AKA Subscription).
     * <p>
     * This method is also responsible for raising the appropriate QMF2 Events when Management Objects are created.
     * @param object the parent object that the child is being added to.
     * @param child the child object being added.
     */
    @Override
    public void childAdded(final ConfiguredObject object, final ConfiguredObject child)
    {
//System.out.println("childAdded() " + child.getClass().getSimpleName() + "." + child.getName());

        QmfAgentData data = null;

        if (child instanceof Broker)
        {
            data = new org.apache.qpid.server.qmf2.agentdata.Broker((Broker)child);
        }
        else if (child instanceof Connection)
        {
            if (!agentConnection && !_objects.containsKey(child))
            {
                // If the parent object is the default vhost set it to null so that the Connection ignores it.
                VirtualHost vhost = (object.getName().equals(_defaultVirtualHost)) ? null : (VirtualHost)object;
                data = new org.apache.qpid.server.qmf2.agentdata.Connection(vhost, (Connection)child);
                _objects.put(child, data);

                // Raise a Client Connect Event.
                _agent.raiseEvent(((org.apache.qpid.server.qmf2.agentdata.Connection)data).createClientConnectEvent());
            }
            agentConnection = false; // Only ignore the first Connection, which is the one from the Agent. 
        }
        else if (child instanceof Session)
        {
            if (!_objects.containsKey(child))
            {
                QmfAgentData ref = _objects.get(object); // Get the Connection QmfAgentData so we can get connectionRef.
                if (ref != null)
                {
                    data = new org.apache.qpid.server.qmf2.agentdata.Session((Session)child, ref.getObjectId());
                    _objects.put(child, data);
                }
            }
        }
        else if (child instanceof Exchange)
        {
            if (!_objects.containsKey(child))
            {
                // If the parent object is the default vhost set it to null so that the Connection ignores it.
                VirtualHost vhost = (object.getName().equals(_defaultVirtualHost)) ? null : (VirtualHost)object;
                data = new org.apache.qpid.server.qmf2.agentdata.Exchange(vhost, (Exchange)child);
                _objects.put(child, data);

                // Raise an Exchange Declare Event.
                _agent.raiseEvent(((org.apache.qpid.server.qmf2.agentdata.Exchange)data).createExchangeDeclareEvent());

            }
        }
        else if (child instanceof Queue)
        {
            if (!_objects.containsKey(child))
            {
                // If the parent object is the default vhost set it to null so that the Connection ignores it.
                VirtualHost vhost = (object.getName().equals(_defaultVirtualHost)) ? null : (VirtualHost)object;
                data = new org.apache.qpid.server.qmf2.agentdata.Queue(vhost, (Queue)child);
                _objects.put(child, data);

                // Raise a Queue Declare Event.
                _agent.raiseEvent(((org.apache.qpid.server.qmf2.agentdata.Queue)data).createQueueDeclareEvent());
            }
        }
        else if (child instanceof Binding)
        {
            // Bindings are a little more complex because in QMF bindings contain exchangeRef and queueRef properties
            // whereas with the Java Broker model Binding is a child of Queue and Exchange. To cope with this we
            // first try to create or retrieve the QMF Binding Object then add either the Queue or Exchange reference
            // depending on whether Queue or Exchange was the parent of this addChild() call.
            if (!_objects.containsKey(child))
            {
                data = new org.apache.qpid.server.qmf2.agentdata.Binding((Binding)child);
                _objects.put(child, data);

                String eName = child.getAttribute("exchange").toString();
                if (!eName.equals("<<default>>")) // Don't send Event for Binding to default direct.
                {
                    // Raise a Bind Event.
                    _agent.raiseEvent(((org.apache.qpid.server.qmf2.agentdata.Binding)data).createBindEvent());
                }
            }

            org.apache.qpid.server.qmf2.agentdata.Binding binding =
                (org.apache.qpid.server.qmf2.agentdata.Binding)_objects.get(child);

            QmfAgentData ref = _objects.get(object);
            if (ref != null)
            {
                if (object instanceof Queue)
                {
                    binding.setQueueRef(ref.getObjectId());
                }
                else if (object instanceof Exchange)
                {
                    binding.setExchangeRef(ref.getObjectId());
                }
            }
        }
        else if (child instanceof Consumer) // AKA Subscription
        {
            // Subscriptions are a little more complex because in QMF Subscriptions contain sessionRef and queueRef 
            // properties whereas with the Java Broker model Consumer is a child of Queue and Session. To cope with
            // this we first try to create or retrieve the QMF Subscription Object then add either the Queue or
            // Session reference depending on whether Queue or Session was the parent of this addChild() call.
            if (!_objects.containsKey(child))
            {
                data = new org.apache.qpid.server.qmf2.agentdata.Subscription((Consumer)child);
                _objects.put(child, data);
           }

            org.apache.qpid.server.qmf2.agentdata.Subscription subscription =
                (org.apache.qpid.server.qmf2.agentdata.Subscription)_objects.get(child);

            QmfAgentData ref = _objects.get(object);
            if (ref != null)
            {
                if (object instanceof Queue)
                {
                    subscription.setQueueRef(ref.getObjectId(), (Queue)object);
                    // Raise a Subscribe Event - N.B. Need to do it *after* we've set the queueRef.
                    _agent.raiseEvent(subscription.createSubscribeEvent());
                }
                else if (object instanceof Session)
                {
                    subscription.setSessionRef(ref.getObjectId());
                }
            }
        }

        try
        {
            // If we've created new QmfAgentData we register it with the Agent.
            if (data != null)
            {
                _agent.addObject(data);
            }
        }
        catch (QmfException qmfe)
        {
            _log.info("QmfException {} caught in QmfManagementAgent.addObject()", qmfe.getMessage());
        }

        child.addChangeListener(this);
    }


    /**
     * ConfigurationChangeListener method called when a child ConfiguredObject is removed.
     * <p>
     * This method checks the type of the child ConfiguredObject that has been removed and raises the appropriate
     * QMF2 Events, it then destroys the QMF2 Management Object and removes the mapping between child and the QMF Object.
     *
     * @param object the parent object that the child is being removed from.
     * @param child the child object being removed.
     */
    @Override
    public void childRemoved(final ConfiguredObject object, final ConfiguredObject child)
    {
//System.out.println("childRemoved: " + child.getClass().getSimpleName() + "." + child.getName());

        child.removeChangeListener(this);

        // Look up the associated QmfAgentData and mark it for deletion by the Agent.
        QmfAgentData data = _objects.get(child);
        if (data != null)
        {
            if (child instanceof Connection)
            {
                // Raise a Client Disconnect Event.
                _agent.raiseEvent(((org.apache.qpid.server.qmf2.agentdata.Connection)data).createClientDisconnectEvent());
            }
            else if (child instanceof Session)
            {
                // no-op, don't need to do anything specific when Session is removed.
            }
            else if (child instanceof Exchange)
            {
                // Raise an Exchange Delete Event.
                _agent.raiseEvent(((org.apache.qpid.server.qmf2.agentdata.Exchange)data).createExchangeDeleteEvent());
            }
            else if (child instanceof Queue)
            {
                // Raise a Queue Delete Event.
                _agent.raiseEvent(((org.apache.qpid.server.qmf2.agentdata.Queue)data).createQueueDeleteEvent());
            }
            else if (child instanceof Binding)
            {
                String eName = child.getAttribute("exchange").toString();
                if (!eName.equals("<<default>>")) // Don't send Event for Unbinding from default direct.
                {
                    // Raise an Unbind Event.
                    _agent.raiseEvent(((org.apache.qpid.server.qmf2.agentdata.Binding)data).createUnbindEvent());
                }
            }
            else if (child instanceof Consumer)
            {
                // Raise an Unsubscribe Event.
                _agent.raiseEvent(((org.apache.qpid.server.qmf2.agentdata.Subscription)data).createUnsubscribeEvent());
            }

            data.destroy();
        }

        // Remove the mapping from the internal ConfiguredObject->QmfAgentData Map.
        _objects.remove(child);
    }

    // ******************************* QmfEventListener implementation method *******************************

    /**
     * Callback method triggered when the underlying QMF2 Agent has WorkItems available for processing.
     * The purpose of this method is mainly to handle the METHOD_CALL WorkItem and demultiplex & delegate
     * to the invokeMethod() call on the relevant concrete QmfAgentData Object.
     * @param wi the WorkItem that has been passed by the QMF2 Agent to be processed here (mainly METHOD_CALL).
     */
    @Override
    public void onEvent(final WorkItem wi)
    {
        if (wi.getType() == METHOD_CALL)
        {
            MethodCallWorkItem item = (MethodCallWorkItem)wi;
            MethodCallParams methodCallParams = item.getMethodCallParams();

            String methodName = methodCallParams.getName();
            ObjectId objectId = methodCallParams.getObjectId();

            // Look up QmfAgentData by ObjectId from the Agent's internal Object store.
            QmfAgentData object = _agent.getObject(objectId);
            if (object == null)
            {
                _agent.raiseException(item.getHandle(), "No object found with ID=" + objectId);
            }
            else
            {
                // If we've found a valid QmfAgentData check it's a Broker or Queue and if so call the generic
                // invokeMethod on these objects, if not send an Exception as we don't support methods on
                // other classes yet.
                if (object instanceof org.apache.qpid.server.qmf2.agentdata.Broker)
                {
                    org.apache.qpid.server.qmf2.agentdata.Broker broker = 
                        (org.apache.qpid.server.qmf2.agentdata.Broker) object;
                    broker.invokeMethod(_agent, item.getHandle(), methodName, methodCallParams.getArgs());
                }
                else if (object instanceof org.apache.qpid.server.qmf2.agentdata.Queue)
                {
                    org.apache.qpid.server.qmf2.agentdata.Queue queue = 
                        (org.apache.qpid.server.qmf2.agentdata.Queue) object;
                    queue.invokeMethod(_agent, item.getHandle(), methodName, methodCallParams.getArgs());
                }
                else
                {
                    _agent.raiseException(item.getHandle(), "Unknown Method " + methodName + " on " + 
                                          object.getClass().getSimpleName());
                }
            }
        }
    }
}
