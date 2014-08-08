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
package org.apache.qpid.qmf2.console;

// JMS Imports
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.MessageListener;
import javax.jms.Session;

// Used to get the PID equivalent
import java.lang.management.ManagementFactory;

// Simple Logging Facade 4 Java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Misc Imports
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// QMF2 Imports
import org.apache.qpid.qmf2.common.AMQPMessage;
import org.apache.qpid.qmf2.common.Handle;
import org.apache.qpid.qmf2.common.Notifier;
import org.apache.qpid.qmf2.common.NotifierWrapper;
import org.apache.qpid.qmf2.common.NullQmfEventListener;
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfCallback;
import org.apache.qpid.qmf2.common.QmfData;
import org.apache.qpid.qmf2.common.QmfEvent;
import org.apache.qpid.qmf2.common.QmfEventListener;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.QmfQuery;
import org.apache.qpid.qmf2.common.QmfQueryTarget;
import org.apache.qpid.qmf2.common.SchemaClass;
import org.apache.qpid.qmf2.common.SchemaClassId;
import org.apache.qpid.qmf2.common.SchemaEventClass;
import org.apache.qpid.qmf2.common.SchemaObjectClass;
import org.apache.qpid.qmf2.common.WorkItem;
import org.apache.qpid.qmf2.common.WorkQueue;

// Reuse this class as it provides a handy mechanism to parse an options String into a Map
import org.apache.qpid.messaging.util.AddressParser;

/**
 * The Console class is the top-level object used by a console application. All QMF console functionality
 * is made available by this object. A console application must instatiate one of these objects.
 * <p>
 * If a name is supplied, it must be unique across all Consoles attached to the AMQP bus under the given
 * domain. If no name is supplied, a unique name will be synthesized in the format: {@literal "qmfc-<hostname>.<pid>"}
 * <p>
 * <h3>Interactions with remote Agent</h3>
 * As noted below, some Console methods require contacting a remote Agent. For these methods, the caller
 * has the option to either block for a (non-infinite) timeout waiting for a reply, or to allow the method
 * to complete asynchonously. When the asynchronous approach is used, the caller must provide a unique
 * handle that identifies the request. When the method eventually completes, a WorkItem will be placed on
 * the work queue. The WorkItem will contain the handle that was provided to the corresponding method call.
 * <p>
 * The following diagram illustrates the interactions between the Console, Agent and client side Agent proxy.
 * <p>
 * <img alt="" src="doc-files/Console.png">
 * <p>
 * All blocking calls are considered thread safe - it is possible to have a multi-threaded implementation
 * have multiple blocking calls in flight simultaneously.
 * <p>
 * <h3>Subscriptions</h3>
 * This implementation of the QMF2 API has full support for QMF2 Subscriptions where they are supported by an Agent.
 * <p>
 * N.B. That the 0.12 C++ broker does not <i>actually</i> support subscriptions, however it does periodically "push" 
 * QmfConsoleData Object updates as _data indications. The Console class uses these to provide client side
 * emulation of broker subscriptions.
 * The System Property "disable_subscription_emulation" may be set to true to disable this behaviour.
 * <p>
 * The diagram below shows the relationship between the Console, the SubscriptionManager (which is used to manage the
 * lifecycle of Subscriptions on the client side) and the local Agent representation.
 * <p>
 * The SubscriptionManager is also used to maintain the Subscription query to enable ManagementAgent _data indications
 * to be tested in order to emulate Subscriptions to the broker ManagementAgent on the client side.
 * <p>
 * <img alt="" src="doc-files/Subscriptions.png">
 * <p>
 * <h3>Receiving Asynchronous Notifications</h3>
 * This implementation of the QMF2 Console actually supports two independent APIs to enable clients to receive
 * Asynchronous notifications.
 * <p>
 * A QmfEventListener object is used to receive asynchronously delivered WorkItems.
 * <p>
 * This provides an alternative (simpler) API to the official QMF2 WorkQueue API that some (including the Author)
 * may prefer over the official API.
 * <p>
 * The following diagram illustrates the QmfEventListener Event model.
 * <p>
 * Notes
 * <ol>
 *  <li>This is provided as an alternative to the official QMF2 WorkQueue and Notifier Event model.</li>
 *  <li>Agent and Console methods are sufficiently thread safe that it is possible to call them from a callback fired
 *      from the onEvent() method that may have been called from the JMS MessageListener. Internally the synchronous
 *      and asynchronous calls are processed on different JMS Sessions to facilitate this</li>
 * </ol>
 * <p>
 * <img alt="" src="doc-files/QmfEventListenerModel.png">
 * <p>
 * The QMF2 API has a work-queue Callback approach. All asynchronous events are represented by a WorkItem object.
 * When a QMF event occurs it is translated into a WorkItem object and placed in a FIFO queue. It is left to the
 * application to drain this queue as needed.
 * <p>
 * This new API does require the application to provide a single callback. The callback is used to notify the
 * application that WorkItem object(s) are pending on the work queue. This callback is invoked by QMF when one or
 * more new WorkItem objects are added to the queue. To avoid any potential threading issues, the application is
 * not allowed to call any QMF API from within the context of the callback. The purpose of the callback is to
 * notify the application to schedule itself to drain the work queue at the next available opportunity.
 * <p>
 * For example, a console application may be designed using a select() loop. The application waits in the select()
 * for any of a number of different descriptors to become ready. In this case, the callback could be written to
 * simply make one of the descriptors ready, and then return. This would cause the application to exit the wait state,
 * and start processing pending events.
 * <p>
 * The callback is represented by the Notifier virtual base class. This base class contains a single method. An
 * application derives a custom notification handler from this class, and makes it available to the Console or
 * Agent object.
 * <p>
 * The following diagram illustrates the Notifier and WorkQueue QMF2 API Event model.
 * <p>
 * Notes
 * <ol>
 *  <li>There is an alternative (simpler but not officially QMF2) API based on implementing the QmfEventListener as 
 *      described previously.</li>
 *  <li>BlockingNotifier is not part of QMF2 either but is how most people would probably write a Notifier.</li>
 *  <li>It's generally not necessary to use a Notifier as the Console provides a blocking getNextWorkitem() method.</li>
 * </ol>
 * <p>
 * <img alt="" src="doc-files/WorkQueueEventModel.png">


 * <h3>Potential Issues with Qpid versions earlier than 0.12</h3>
 * Note 1: This uses QMF2 so requires that the "--mgmt-qmf2 yes" option is applied to the broker (this is the default
 * from Qpid 0.10).
 * <p>
 * Note 2: In order to use QMF2 the app-id field needs to be set. There appears to be no way to set the AMQP 0-10
 * specific app-id field on a message which the brokers QMFv2 implementation currently requires.
 * <p>
 * Gordon Sim has put together a patch for org.apache.qpid.client.message.AMQMessageDelegate_0_10
 * Found in client/src/main/java/org/apache/qpid/client/message/AMQMessageDelegate_0_10.java
 * <pre>
 *   public void setStringProperty(String propertyName, String value) throws JMSException
 *   {
 *       checkPropertyName(propertyName);
 *       checkWritableProperties();
 *       setApplicationHeader(propertyName, value);
 *
 *       if ("x-amqp-0-10.app-id".equals(propertyName))
 *       {
 *           _messageProps.setAppId(value.getBytes());
 *       }
 *   }
 * </pre>
 * This gets things working.
 * <p>
 * A jira <a href=https://issues.apache.org/jira/browse/QPID-3302>QPID-3302</a> has been raised. 
 * This is fixed in Qpid 0.12.
 *
 * @author Fraser Adams
 */
public final class Console implements MessageListener, AgentProxy
{
    private static final Logger _log = LoggerFactory.getLogger(Console.class);

    //                                             Attributes
    // ********************************************************************************************************

    /**
     * The eventListener may be a real application QmfEventListener, a NullQmfEventListener or an application
     * Notifier wrapped in a QmfEventListener. In all cases the Console may call _eventListener.onEvent() at
     * various places to pass a WorkItem to an asynchronous receiver.
     */
    private QmfEventListener _eventListener;

    /**
     * Explicitly store Agents in a ConcurrentHashMap, as we know the MessageListener thread may modify its contents.
     */
    private Map<String, Agent> _agents = new ConcurrentHashMap<String, Agent>();

    /**
     * This Map is used to look up a Subscription by consoleHandle. 
     */
    private Map<String, SubscriptionManager> _subscriptionByHandle = new ConcurrentHashMap<String, SubscriptionManager>();

    /**
     * This Map is used to look up a Subscription by subscriptionId
     */
    private Map<String, SubscriptionManager> _subscriptionById = new ConcurrentHashMap<String, SubscriptionManager>();

    /**
     * Used to implement a thread safe queue of WorkItem objects used to implement the Notifier API
     */
    private WorkQueue _workQueue = new WorkQueue();

    /**
     * The name of the broker Agent is explicitly recorded when the broker Agent is discovered, we use this so
     * we can support the synonyms "broker" and "qpidd" for the broker Agent, as its full name isn't especially
     * easy to use givent that it contains a UUID "instance" component.
     */
    private String _brokerAgentName = null;

    /**
     * A flag to indicate that an Agent has been registered, used as a condition variable.
     */
    private boolean _agentAvailable = false;

    /**
     * The domain string is used to construct the name of the AMQP exchange to which the component's     
     * name string will be bound. If not supplied, the value of the domain defaults to "default". Both
     * Agents and Components must belong to the same domain in order to communicate.
     */
    private String _domain;

    /**
     * A QMF address is composed of two parts - an optional domain string, and a mandatory
     * name string "qmf.<domain-string>.direct/<name-string>"
     */
    private String _address;

    /**
     * This flag enables AGENT_* work items to be sent to the Event Listener. Note this is enabled by default
     */
    private boolean _discoverAgents = true;

    /**
     * This Query is set by the enableAgentDiscovery() method that takes a QmfQuery as a parameter. It is used
     * in conjunction with _discoverAgents to decide whether to send notifications of Agent activity
     */
    private QmfQuery _agentQuery = null;

    /**
     * This flag disbles asynchronous behaviour such as QMF Events, Agent discovery etc. useful in simple
     * Use Cases such as getObjects() on the broker. Note that asynchronous behaviour enabled by default.
     */
    private boolean _disableEvents = false;

    /**
     * If the "disable_subscription_emulation" System Property is set then we disable Console side emulation
     * of broker subscriptions
     */
    private boolean _subscriptionEmulationEnabled = !Boolean.getBoolean("disable_subscription_emulation");

    /**
     * Various timeouts used internally.
     * replyTimeout is the default maximum time we wait for synchronous responses
     * agentTimeout is the maximum time we wait for any Agent activity before expiring the Agent
     * subscriptionDuration is the default maximum time we keep a subscription active
     */
    private int _replyTimeout = 10;
    private int _agentTimeout = 60; // 1 minute
    private int _subscriptionDuration = 300; // 5 minutes

    /**
     * This timer is used tidy up Subscription references where a Subscription has expired. Ideally a client should
     * call cancelSubscription(), but we can't rely on it.
     */
    private Timer _timer;

    /**
     * Various JMS related fields
     */
    private Connection      _connection = null;
    private Session         _asyncSession;
    private Session         _syncSession; 
    private MessageConsumer _eventConsumer;
    private MessageConsumer _responder; 
    private MessageConsumer _asyncResponder;
    private MessageProducer _requester; 
    private MessageProducer _broadcaster;
    private Destination     _replyAddress;
    private Destination     _asyncReplyAddress;

    //                                  private implementation methods
    // ********************************************************************************************************

    /**
     * Send an asynchronous _agent_locate_request to the topic broadcast address with the subject
     * "console.request.agent_locate". This should cause all active Agents to respond on the async
     * direct address, which gets handled by onMessage()
     */
    private void broadcastAgentLocate()
    {
        try
        {
            Message request = AMQPMessage.createListMessage(_syncSession);
            request.setJMSReplyTo(_asyncReplyAddress);
            request.setStringProperty("x-amqp-0-10.app-id", "qmf2");
            request.setStringProperty("method", "request");
            request.setStringProperty("qmf.opcode", "_agent_locate_request");
            request.setStringProperty("qpid.subject", "console.request.agent_locate");
            AMQPMessage.setList(request, Collections.emptyList());
            _broadcaster.send(request);
        }
        catch (JMSException jmse)
        {
            _log.info("JMSException {} caught in broadcastAgentLocate()", jmse.getMessage());
        }
    }

    /**
     * Check whether any of the registered Agents has expired by comparing their timestamp against the 
     * current time. We explicitly use an iterator rather than a foreach loop because if the Agent has
     * expired we want to remove it and the only safe way to do that whilst still iterating is to use
     * iterator.remove() and the foreach loop hides the underlying iterator from us.
     */
    private void handleAgentExpiry()
    {
        long currentTime = System.currentTimeMillis()*1000000l;

        // Use the iterator approach rather than foreach as we may want to call iterator.remove() to zap an entry
        Iterator<Agent> i = _agents.values().iterator();
        while (i.hasNext())
        {
            Agent agent = i.next();
            // Get the time difference in seconds between now and the last Agent update.
            long diff = (currentTime - agent.getTimestamp())/1000000000l;
            if (diff > _agentTimeout)
            {
                if (agent.getVendor().equals("apache.org") && agent.getProduct().equals("qpidd"))
                {            
                    _brokerAgentName = null;
                }
                agent.deactivate();
                i.remove();
                _log.info("Agent {} has expired", agent.getName());
                if (_discoverAgents && (_agentQuery == null || _agentQuery.evaluate(agent)))
                {
                    _eventListener.onEvent(new AgentDeletedWorkItem(agent));
                }
            }
        }
    }

    /**
     * MessageListener for QMF2 Agent Events, Hearbeats and Asynchronous data indications
     *
     * @param message the JMS Message passed to the listener
     */
    public void onMessage(Message message)
    {
        try
        {
            String agentName = QmfData.getString(message.getObjectProperty("qmf.agent"));
            String content = QmfData.getString(message.getObjectProperty("qmf.content"));
            String opcode = QmfData.getString(message.getObjectProperty("qmf.opcode"));
            //String routingKey = ((javax.jms.Topic)message.getJMSDestination()).getTopicName();
            //String contentType = ((org.apache.qpid.client.message.AbstractJMSMessage)message).getContentType();

//System.out.println();
//System.out.println("agentName = " + agentName);
//System.out.println("content = " + content);
//System.out.println("opcode = " + opcode);
//System.out.println("routingKey = " + routingKey);
//System.out.println("contentType = " + contentType);

            if (opcode.equals("_agent_heartbeat_indication") || opcode.equals("_agent_locate_response"))
            { // This block handles Agent lifecycle information (discover, register, delete)
                if (_agents.containsKey(agentName))
                { // This block handles Agents that have previously been registered
                    Agent agent = _agents.get(agentName);
                    long originalEpoch = agent.getEpoch();

                    // If we already know about an Agent we simply update the Agent's state using initialise()
                    agent.initialise(AMQPMessage.getMap(message));

                    // If the Epoch has changed it means the Agent has been restarted so we send a notification
                    if (agent.getEpoch() != originalEpoch)
                    {
                        agent.clearSchemaCache(); // Clear cache to force a lookup
                        List<SchemaClassId> classes = getClasses(agent);
                        getSchema(classes, agent); // Discover the schema for this Agent and cache it
                        _log.info("Agent {} has been restarted", agentName);
                        if (_discoverAgents && (_agentQuery == null || _agentQuery.evaluate(agent)))
                        {
                            _eventListener.onEvent(new AgentRestartedWorkItem(agent));
                        }
                    }
                    else
                    { // Otherwise just send a heartbeat notification
                        _log.info("Agent {} heartbeat", agent.getName());
                        if (_discoverAgents && (_agentQuery == null || _agentQuery.evaluate(agent)))
                        {
                            _eventListener.onEvent(new AgentHeartbeatWorkItem(agent));
                        }
                    }
                }
                else
                { // This block handles Agents that haven't already been registered
                    Agent agent = new Agent(AMQPMessage.getMap(message), this);
                    List<SchemaClassId> classes = getClasses(agent);
                    getSchema(classes, agent); // Discover the schema for this Agent and cache it
                    _agents.put(agentName, agent);
                    _log.info("Adding Agent {}", agentName);

                    // If the Agent is the Broker Agent we record it as _brokerAgentName to make retrieving
                    // the Agent more "user friendly" than using the full Agent name.
                    if (agent.getVendor().equals("apache.org") && agent.getProduct().equals("qpidd"))
                    {
                        _log.info("Recording {} as _brokerAgentName", agentName);
                        _brokerAgentName = agentName;
                    }

                    // Notify any waiting threads that an Agent has been registered. Note that we only notify if
                    // we've already found the broker Agent to avoid a race condition in addConnection(), as another
                    // Agent could in theory trigger this block first. In addConnection() we *explicitly* want to
                    // wait for the broker Agent to become available.
                    if (_brokerAgentName != null)
                    {
                        synchronized(this)
                        {
                            _agentAvailable = true;
                            notifyAll();
                        }
                    }

                    if (_discoverAgents && (_agentQuery == null || _agentQuery.evaluate(agent)))
                    {
                        _eventListener.onEvent(new AgentAddedWorkItem(agent));
                    }
                }

                // The broker Agent sends periodic heartbeats and that Agent should *always* be available given
                // a running broker, so we should get here every "--mgmt-pub-interval" seconds or so, so it's
                // a good place to periodically check for the expiry of any other Agents.
                handleAgentExpiry();
                return;
            }

            if (!_agents.containsKey(agentName))
            {
                _log.info("Ignoring Event from unregistered Agent {}", agentName);
                return;
            }

            Agent agent = _agents.get(agentName);
            if (!agent.eventsEnabled())
            {
                _log.info("{} has disabled Event reception, ignoring Event", agentName);
                return;
            }

            // If we get to here the Agent from whence the Event came should be registered and should
            // have Event reception enabled, so we should be able to send events to the EventListener

            Handle handle = new Handle(message.getJMSCorrelationID());
            if (opcode.equals("_method_response") || opcode.equals("_exception"))
            {
                if (AMQPMessage.isAMQPMap(message))
                {
                    _eventListener.onEvent(
                        new MethodResponseWorkItem(handle, new MethodResult(AMQPMessage.getMap(message)))
                    );
                }
                else
                {
                    _log.info("onMessage() Received Method Response message in incorrect format");
                }
            }
 
            // Query Response. The only asynchronous query response we expect to see is the result of an async
            // refresh() call on QmfConsoleData so the number of results in the returned list *should* be one.
            if (opcode.equals("_query_response") && content.equals("_data"))
            {
                if (AMQPMessage.isAMQPList(message))
                {
                    List<Map> list = AMQPMessage.getList(message);
                    for (Map m : list)
                    {
                        _eventListener.onEvent(new ObjectUpdateWorkItem(handle, new QmfConsoleData(m, agent)));
                    }
                }
                else
                {
                    _log.info("onMessage() Received Query Response message in incorrect format");
                }
            }

            // This block handles responses to createSubscription and refreshSubscription
            if (opcode.equals("_subscribe_response"))
            {
                if (AMQPMessage.isAMQPMap(message))
                {
                    String correlationId = message.getJMSCorrelationID();
                    SubscribeParams params = new SubscribeParams(correlationId, AMQPMessage.getMap(message));
                    String subscriptionId = params.getSubscriptionId();

                    if (subscriptionId != null && correlationId != null)
                    {
                        SubscriptionManager subscription = _subscriptionById.get(subscriptionId);
                        if (subscription == null)
                        { // This is a createSubscription response so the correlationId should be the consoleHandle
                            subscription = _subscriptionByHandle.get(correlationId);
                            if (subscription != null)
                            {
                                _subscriptionById.put(subscriptionId, subscription);
                                subscription.setSubscriptionId(subscriptionId);
                                subscription.setDuration(params.getLifetime());
                                String replyHandle = subscription.getReplyHandle();
                                if (replyHandle == null)
                                {
                                    subscription.signal();
                                }
                                else
                                {
                                    _eventListener.onEvent(new SubscribeResponseWorkItem(new Handle(replyHandle), params));
                                }
                            }
                        }
                        else
                        { // This is a refreshSubscription response
                            params.setConsoleHandle(subscription.getConsoleHandle());
                            subscription.setDuration(params.getLifetime());
                            subscription.refresh();
                            _eventListener.onEvent(new SubscribeResponseWorkItem(handle, params));
                        }
                    }
                }
                else
                {
                    _log.info("onMessage() Received Subscribe Response message in incorrect format");
                }
            }

            // Subscription Indication - in other words the asynchronous results of a Subscription
            if (opcode.equals("_data_indication") && content.equals("_data"))
            {
                if (AMQPMessage.isAMQPList(message))
                {
                    String consoleHandle = handle.getCorrelationId();
                    if (consoleHandle != null && _subscriptionByHandle.containsKey(consoleHandle))
                    { // If we have a valid consoleHandle the data has come from a "real" Subscription.
                        List<Map> list = AMQPMessage.getList(message);
                        List<QmfConsoleData> resultList = new ArrayList<QmfConsoleData>(list.size());
                        for (Map m : list)
                        {
                            resultList.add(new QmfConsoleData(m, agent));
                        }
                        _eventListener.onEvent(
                            new SubscriptionIndicationWorkItem(new SubscribeIndication(consoleHandle, resultList))
                        );
                    }
                    else if (_subscriptionEmulationEnabled && agentName.equals(_brokerAgentName))
                    { // If the data has come from is the broker Agent we emulate a Subscription on the Console
                        for (SubscriptionManager subscription : _subscriptionByHandle.values())
                        {
                            QmfQuery query = subscription.getQuery();
                            if (subscription.getAgent().getName().equals(_brokerAgentName) &&
                                query.getTarget() == QmfQueryTarget.OBJECT)
                            { // Only evaluate broker Agent subscriptions with QueryTarget == OBJECT on the Console.
                                long objectEpoch = 0;
                                consoleHandle = subscription.getConsoleHandle();
                                List<Map> list = AMQPMessage.getList(message);
                                List<QmfConsoleData> resultList = new ArrayList<QmfConsoleData>(list.size());
                                for (Map m : list)
                                { // Evaluate the QmfConsoleData object against the query
                                    QmfConsoleData object = new QmfConsoleData(m, agent);
                                    if (query.evaluate(object))
                                    {
                                        long epoch = object.getObjectId().getAgentEpoch();
                                        objectEpoch = (epoch > objectEpoch && !object.isDeleted()) ? epoch : objectEpoch;
                                        resultList.add(object);
                                    }
                                }

                                if (resultList.size() > 0)
                                {   // If there are any results available after evaluating the query we deliver them
                                    // via a SubscribeIndicationWorkItem.

                                    // Before we send the WorkItem we take a peek at the Agent Epoch value that forms
                                    // part of the ObjectID and compare it against the current Epoch value. If they
                                    // are different we send an AgentRestartedWorkItem. We *normally* check for Epoch
                                    // changes when we receive heartbeat indications, but unfortunately the broker 
                                    // ManagementAgent pushes data *before* it pushes heartbeats. Its more useful
                                    // however for clients to know that an Agent has been restarted *before* they get
                                    // data from the restarted Agent (in case they need to reset any state).
                                    if (objectEpoch > agent.getEpoch())
                                    {
                                        agent.setEpoch(objectEpoch);
                                        agent.clearSchemaCache(); // Clear cache to force a lookup
                                        List<SchemaClassId> classes = getClasses(agent);
                                        getSchema(classes, agent); // Discover the schema for this Agent and cache it
                                        _log.info("Agent {} has been restarted", agentName);
                                        if (_discoverAgents && (_agentQuery == null || _agentQuery.evaluate(agent)))
                                        {
                                            _eventListener.onEvent(new AgentRestartedWorkItem(agent));
                                        }
                                    }

                                    _eventListener.onEvent(
                                        new SubscriptionIndicationWorkItem(
                                            new SubscribeIndication(consoleHandle, resultList))
                                    );
                                }
                            }
                        }
                    }
                }
                else
                {
                    _log.info("onMessage() Received Subscribe Indication message in incorrect format");
                }
            }

            // The results of an Event delivered from an Agent
            if (opcode.equals("_data_indication") && content.equals("_event"))
            { // There are differences in the type of message sent by Qpid 0.8 and 0.10 onwards.
                if (AMQPMessage.isAMQPMap(message))
                { // 0.8 broker passes Events as amqp/map encoded as MapMessages (we convert into java.util.Map)
                    _eventListener.onEvent(new EventReceivedWorkItem(agent, new QmfEvent(AMQPMessage.getMap(message))));
                }
                else if (AMQPMessage.isAMQPList(message))
                { // 0.10 and above broker passes Events as amqp/list encoded as BytesMessage (needs decoding)
                  // 0.20 encodes amqp/list in a MapMessage!!?? AMQPMessage hopefully abstracts this detail.
                    List<Map> list = AMQPMessage.getList(message);
                    for (Map m : list)
                    {
                        _eventListener.onEvent(new EventReceivedWorkItem(agent, new QmfEvent(m)));
                    }
                }
                else
                {
                    _log.info("onMessage() Received Event message in incorrect format");
                }
            }
        }
        catch (JMSException jmse)
        {
            _log.info("JMSException {} caught in onMessage()", jmse.getMessage());
        }
    } // end of onMessage() 

    /**
     * Retrieve the schema for a List of classes.
     * This method explicitly retrieves the schema from the remote Agent and is generally used for schema
     * discovery when an Agent is added or updated.
     *
     * @param classes the list of SchemaClassId of the classes who's schema we want to retrieve
     * @param agent the Agent we want to retrieve the schema from
     */
    private List<SchemaClass> getSchema(final List<SchemaClassId> classes, final Agent agent)
    {
        List<SchemaClass> results = new ArrayList<SchemaClass>();
        for (SchemaClassId classId : classes)
        {
            agent.setSchema(classId, Collections.<SchemaClass>emptyList()); // Clear Agent's schema value for classId
            results.addAll(getSchema(classId, agent));
        }
        return results;
    }

    /**
     * Perform a query for QmfConsoleData objects. Returns a list (possibly empty) of matching objects.
     * If replyHandle is null this method will block until the agent replies, or the timeout expires.
     * Once the timeout expires, all data retrieved to date is returned. If replyHandle is non-null an
     * asynchronous request is performed
     * 
     * @param agent the Agent being queried
     * @param query the ObjectId or SchemaClassId being queried for.
     * @param replyHandle the correlation handle used to tie asynchronous method requests with responses
     * @param timeout the time to wait for a reply from the Agent, a value of -1 means use the default timeout
     * @return a List of QMF Objects describing that class
     */
    private List<QmfConsoleData> getObjects(final Agent agent, final QmfData query,
                                            final String replyHandle, int timeout)
    {
        String agentName = agent.getName();
        timeout = (timeout < 1) ? _replyTimeout : timeout;
        List<QmfConsoleData> results = Collections.emptyList();
        try
        {
            Destination destination = (replyHandle == null) ? _replyAddress : _asyncReplyAddress;
            MapMessage request = _syncSession.createMapMessage();
            request.setJMSReplyTo(destination);
            request.setJMSCorrelationID(replyHandle);
            request.setStringProperty("x-amqp-0-10.app-id", "qmf2");
            request.setStringProperty("method", "request");
            request.setStringProperty("qmf.opcode", "_query_request");
            request.setStringProperty("qpid.subject", agentName);

            // Create a QMF Query for an "OBJECT" target using either a schema ID or object ID
            String queryType = (query instanceof SchemaClassId) ? "_schema_id" : "_object_id";
            request.setObject("_what", "OBJECT");
            request.setObject(queryType, query.mapEncode());

            // Wrap request & response in synchronized block in case any other threads invoke a request
            // it would be somewhat unfortunate if their response got interleaved with ours!!
            synchronized(this)
            {
                _requester.send(request);
                if (replyHandle == null)
                {
                    boolean lastResult = true;
                    ArrayList<QmfConsoleData> partials = new ArrayList<QmfConsoleData>();
                    do
                    { // Wrap in a do/while loop to cater for the case where the Agent may send partial results.
                        Message response = _responder.receive(timeout*1000);
                        if (response == null)
                        {
                            _log.info("No response received in getObjects()");
                            return partials;
                        }

                        lastResult = !response.propertyExists("partial");

                        if (AMQPMessage.isAMQPList(response))
                        {
                            List<Map> mapResults = AMQPMessage.getList(response);
                            partials.ensureCapacity(partials.size() + mapResults.size());
                            for (Map content : mapResults)
                            {
                                partials.add(new QmfConsoleData(content, agent));
                            }
                        }
                        else if (AMQPMessage.isAMQPMap(response))
                        {
                            // Error responses are returned as MapMessages, though they are being ignored here.
                            //QmfData exception = new QmfData(AMQPMessage.getMap(response));
                            //System.out.println(agentName + " " + exception.getStringValue("error_text"));
                        }
                        else
                        {
                            _log.info("getObjects() Received response message in incorrect format");
                        }
                    } while (!lastResult);
                    results = partials;
                }
            }
        }
        catch (JMSException jmse)
        {
            _log.info("JMSException {} caught in getObjects()", jmse.getMessage());
        }
        return results;
    }

    //                                methods implementing AgentProxy interface
    // ********************************************************************************************************

    /**
     * Releases the specified Agent instance. Once called, the console application should not reference this
     * instance again.
     * <p>
     * Intended to by called by the AgentProxy. Shouldn't generally be called directly by Console applications.
     *
     * @param agent the Agent that we wish to destroy.
     */
    public void destroy(final Agent agent)
    {
        handleAgentExpiry();
    }

    /**
     * Request that the Agent update the value of an object's contents.
     * <p>
     * Intended to by called by the AgentProxy. Shouldn't generally be called directly by Console applications.
     *
     * @param agent the Agent to get the refresh from.
     * @param objectId the ObjectId being queried for
     * @param replyHandle the correlation handle used to tie asynchronous method requests with responses
     * @param timeout the time to wait for a reply from the Agent, a value of -1 means use the default timeout
     * @return the refreshed object
     */
    public QmfConsoleData refresh(final Agent agent, final ObjectId objectId, final String replyHandle, final int timeout)
    {
        List<QmfConsoleData> objects = getObjects(agent, objectId, replyHandle, timeout);
        return (objects.size() == 0) ? null : objects.get(0);
    }

    /**
     * Invoke the named method on the named Agent.
     * <p>
     * Intended to by called by the AgentProxy. Shouldn't generally be called directly by Console applications.
     *
     * @param agent the Agent to invoke the method on.
     * @param content an unordered set of key/value pairs comprising the method arguments.
     * @param replyHandle the correlation handle used to tie asynchronous method requests with responses
     * @param timeout the time to wait for a reply from the Agent, a value of -1 means use the default timeout
     * @return the method response Arguments in Map form
     */
    public MethodResult invokeMethod(final Agent agent, final Map<String, Object> content,
                                     final String replyHandle, int timeout) throws QmfException
    {
        if (!agent.isActive())
        {
            throw new QmfException("Called invokeMethod() with inactive agent");
        }
        String agentName = agent.getName();
        timeout = (timeout < 1) ? _replyTimeout : timeout;
        try
        {
            Destination destination = (replyHandle == null) ? _replyAddress : _asyncReplyAddress;
            MapMessage request = _syncSession.createMapMessage();
            request.setJMSReplyTo(destination);
            request.setJMSCorrelationID(replyHandle);
            request.setStringProperty("x-amqp-0-10.app-id", "qmf2");
            request.setStringProperty("method", "request");
            request.setStringProperty("qmf.opcode", "_method_request");
            request.setStringProperty("qpid.subject", agentName);

            for (Map.Entry<String, Object> entry : content.entrySet())
            {
                request.setObject(entry.getKey(), entry.getValue());
            }

            // Wrap request & response in synchronized block in case any other threads invoke a request
            // it would be somewhat unfortunate if their response got interleaved with ours!!
            synchronized(this)
            {
                _requester.send(request);
                if (replyHandle == null)
                { // If this is a synchronous request get the response
                    Message response = _responder.receive(timeout*1000);
                    if (response == null)
                    {
                        _log.info("No response received in invokeMethod()");
                        throw new QmfException("No response received for Console.invokeMethod()");
                    }
                    MethodResult result = new MethodResult(AMQPMessage.getMap(response));
                    QmfException exception = result.getQmfException();
                    if (exception != null)
                    {
                        throw exception;
                    }
                    return result;
                }
            }
            // If this is an asynchronous request return without waiting for a response
            return null;
        }
        catch (JMSException jmse)
        {
            _log.info("JMSException {} caught in invokeMethod()", jmse.getMessage());
            throw new QmfException(jmse.getMessage());
        }
    }

    /**
     * Remove a Subscription.
     *
     * @param subscription the SubscriptionManager that we wish to remove.
     */
    public void removeSubscription(final SubscriptionManager subscription)
    {
        String consoleHandle = subscription.getConsoleHandle();
        String subscriptionId = subscription.getSubscriptionId();
        if (consoleHandle != null)
        {
            _subscriptionByHandle.remove(consoleHandle);
        }
        if (subscriptionId != null)
        {
            _subscriptionById.remove(subscriptionId);
        }
    }

    //                                          QMF API Methods
    // ********************************************************************************************************

    /**
     * Constructor that provides defaults for name and domain and has no Notifier/Listener
     * <p>
     * Warning!! If more than one Console is needed in a process be sure to use the Constructor that allows one to 
     * supply a name as &lt;hostname&gt;.&lt;pid&gt; isn't unique enough and will result in "odd results" (trust me!!).
     */
    public Console() throws QmfException
    {
        this(null, null, null, null);
    }

    /**
     * Constructor that provides defaults for name and domain and takes a Notifier/Listener.
     * <p>
     * Warning!! If more than one Console is needed in a process be sure to use the Constructor that allows one to 
     * supply a name as &lt;hostname&gt;.&lt;pid&gt; isn't unique enough and will result in "odd results" (trust me!!).
     *
     * @param notifier this may be either a QMF2 API Notifier object OR a QMFEventListener.
     * <p>
     *        The latter is an alternative API that avoids the need for an explicit Notifier thread to be created the 
     *        EventListener is called from the JMS MessageListener thread. This API may be simpler and more convenient
     *        than the QMF2 Notifier API for many applications.
     */
    public Console(final QmfCallback notifier) throws QmfException
    {
        this(null, null, notifier, null);
    }

    /**
     * Main constructor, creates a Console, but does NOT start it, that requires us to do addConnection()
     *
     * @param name if no name is supplied we synthesise one in the format: <pre>"qmfc-&lt;hostname&gt;.&lt;pid&gt;"</pre>
     *        if we can, otherwise we create a name using a randomUUID.
     * <p>
     *        Warning!! If more than one Console is needed in a process be sure to supply a name as 
     *        &lt;hostname&gt;.&lt;pid&gt; isn't unique enough and will result in "odd results" (trust me!!).
     * @param domain the QMF "domain". A QMF address is composed of two parts - an optional domain string, and a 
     *        mandatory name string <pre>"qmf.&lt;domain-string&gt;.direct/&lt;name-string&gt;"</pre>
     *        The domain string is used to construct the name of the AMQP exchange to which the component's     
     *        name string will be bound. If not supplied, the value of the domain defaults to "default". Both
     *        Agents and Components must belong to the same domain in order to communicate.
     * @param notifier this may be either a QMF2 API Notifier object OR a QMFEventListener.
     * <p>
     *        The latter is an alternative API that avoids the need for an explicit Notifier thread to be created the 
     *        EventListener is called from the JMS MessageListener thread. This API may be simpler and more convenient
     *        than the QMF2 Notifier API for many applications.
     * @param options a String representation of a Map containing the options in the form
     *        <pre>"{replyTimeout:&lt;value&gt;, agentTimeout:&lt;value&gt;, subscriptionDuration:&lt;value&gt;}"</pre>
     *        they are all optional and may appear in any order.
     * <pre>
     *         <b>replyTimeout</b>=&lt;default for all blocking calls&gt;
     *         <b>agentTimeout</b>=&lt;default timeout for agent heartbeat&gt;,
     *         <b>subscriptionDuration</b>=&lt;default lifetime of a subscription&gt;
     * </pre>
     */
    public Console(String name, final String domain,
                   final QmfCallback notifier, final String options) throws QmfException
    {
        if (name == null)
        {
            // ManagementFactory.getRuntimeMXBean().getName()) returns the name representing the running virtual machine.
            // The returned name string can be any arbitrary string and a Java virtual machine implementation can choose
            // to embed platform-specific useful information in the returned name string.
            // As it happens on Linux the format for this is PID@hostname
            String vmName = ManagementFactory.getRuntimeMXBean().getName();
            String[] split = vmName.split("@");
            if (split.length == 2)
            {
                name = "qmfc-" + split[1] + "." + split[0];
            }
            else
            {
                name = "qmfc-" + UUID.randomUUID();
            }
        }

        _domain = (domain == null) ? "default" : domain;
        _address = "qmf." + _domain + ".direct" + "/" + name;

        if (notifier == null)
        {
            _eventListener = new NullQmfEventListener();
        }
        else if (notifier instanceof Notifier)
        {
            _eventListener = new NotifierWrapper((Notifier)notifier, _workQueue);
        }
        else if (notifier instanceof QmfEventListener)
        {
            _eventListener = (QmfEventListener)notifier;
        }
        else
        {
            throw new QmfException("QmfCallback listener must be either a Notifier or QmfEventListener");
        }

        if (options != null)
        { // We wrap the Map in a QmfData object to avoid potential class cast issues with the parsed options
            QmfData optMap = new QmfData(new AddressParser(options).map());
            if (optMap.hasValue("replyTimeout"))
            {
                _replyTimeout = (int)optMap.getLongValue("replyTimeout");
            }

            if (optMap.hasValue("agentTimeout"))
            {
                _agentTimeout = (int)optMap.getLongValue("agentTimeout");
            }

            if (optMap.hasValue("subscriptionDuration"))
            {
                _subscriptionDuration = (int)optMap.getLongValue("subscriptionDuration");
            }
        }
    }

    /**
     * Release the Console's resources.
     */
    public void destroy()
    {
        try
        {
            if (_connection != null)
            {
                removeConnection(_connection);
            }        
        }
        catch (QmfException qmfe)
        {
            // Ignore as we've already tested for _connection != null this should never occur
        }
    }

    /**
     * Connect the console to the AMQP cloud.
     *
     * @param conn a javax.jms.Connection
     */
    public void addConnection(final Connection conn) throws QmfException
    {
        addConnection(conn, "");
    }

    /**
     * Connect the console to the AMQP cloud.
     * <p>
     * This is an extension to the standard QMF2 API allowing the user to specify address options in order to allow
     * finer control over the Console's request and reply queues, e.g. explicit name, non-default size or durability.
     *
     * @param conn a javax.jms.Connection
     * @param addressOptions options String giving finer grained control of the receiver queue.
     * <p>
     * As an example the following gives the Console's queues the name test-console, size = 500000000 and ring policy.
     * <pre>
     * " ; {link: {name:'test-console', x-declare: {arguments: {'qpid.policy_type': ring, 'qpid.max_size': 500000000}}}}"
     * </pre>
     * Note that the Console uses several queues so this will actually create a test-console queue plus a
     * test-console-async queue and a test-console-event queue.
     * <p>
     * If a name parameter is not present temporary queues will be created, but the other options will still be applied.
     */
    public void addConnection(final Connection conn, final String addressOptions) throws QmfException
    {
        // Make the test and set of _connection synchronized just in case multiple threads attempt to add a _connection
        // to the same Console instance at the same time.
        synchronized(this)
        {
            if (_connection != null)
            {
                throw new QmfException("Multiple connections per Console is not supported");
            }
            _connection = conn;
        }
        try
        {
            String syncReplyAddressOptions = addressOptions;
            String asyncReplyAddressOptions = addressOptions;
            String eventAddressOptions = addressOptions;

            if (!addressOptions.equals(""))
            { // If there are address options supplied we need to check if a name parameter is present.
                String[] split = addressOptions.split("name");
                if (split.length == 2)
                { // If options contains a name parameter we extract it and create variants for async and event queues.
                    split = split[1].split("[,}]"); // Look for the end of the key/value block
                    String nameValue = split[0].replaceAll("[ :'\"]", ""); // Remove initial colon, space any any quotes.
                    // Hopefully at this point nameValue is actually the value of the name parameter.
                    asyncReplyAddressOptions = asyncReplyAddressOptions.replace(nameValue, nameValue + "-async");
                    eventAddressOptions = eventAddressOptions.replace(nameValue, nameValue + "-event");
                }
            }

            String topicBase  = "qmf." + _domain + ".topic";
            _syncSession = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Create a MessageProducer for the QMF topic address used to broadcast requests
            Destination topicAddress = _syncSession.createQueue(topicBase);
            _broadcaster = _syncSession.createProducer(topicAddress);

            // If Asynchronous Behaviour is enabled we create the Queues used to receive async responses
            // Data Indications, QMF Events, Heartbeats etc. from the broker (or other Agents).
            if (!_disableEvents)
            {
                // TODO it should be possible to bind _eventConsumer and _asyncResponder to the same queue
                // if I can figure out the correct AddressString to use, probably not a big deal though.

                _asyncSession = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

                // Set up MessageListener on the Event Address
                Destination eventAddress = _asyncSession.createQueue(topicBase + "/agent.ind.#" + eventAddressOptions);
                _eventConsumer = _asyncSession.createConsumer(eventAddress);
                _eventConsumer.setMessageListener(this);

                // Create the asynchronous JMSReplyTo _replyAddress and MessageConsumer
                _asyncReplyAddress = _asyncSession.createQueue(_address + ".async" + asyncReplyAddressOptions);
                _asyncResponder = _asyncSession.createConsumer(_asyncReplyAddress);
                _asyncResponder.setMessageListener(this);
            }

            // I've extended the synchronized block to include creating the _requester and _responder. I don't believe
            // that this is strictly necessary, but it stops findbugs moaning about inconsistent synchronization
            // so makes sense if only to get that warm and fuzzy feeling of keeping findbugs happy :-)
            synchronized(this)
            {
                // Create a MessageProducer for the QMF direct address, mainly used for request/response
                Destination directAddress = _syncSession.createQueue("qmf." + _domain + ".direct");
                _requester = _syncSession.createProducer(directAddress);

                // Create the JMSReplyTo _replyAddress and MessageConsumer
                _replyAddress = _syncSession.createQueue(_address + syncReplyAddressOptions);
                _responder = _syncSession.createConsumer(_replyAddress);

                _connection.start();

                // If Asynchronous Behaviour is disabled we create an Agent instance to represent the broker
                // ManagementAgent the only info that needs to be populated is the _name and we can use the
                // "broker" synonym. We populate this fake Agent so getObjects() behaviour is consistent whether
                // we've any received *real* Agent updates or not.
                if (_disableEvents)
                {
                    _brokerAgentName = "broker";
                    Map<String, String> map = new HashMap<String, String>();
                    map.put("_name", _brokerAgentName);
                    Agent agent = new Agent(map, this);
                    _agents.put(_brokerAgentName, agent);
                    _agentAvailable = true;
                }
                else
                {
                    // If Asynchronous Behaviour is enabled Broadcast an Agent Locate message to get Agent info quickly.
                    broadcastAgentLocate();
                }

                // Wait until the Broker Agent has been located (this should generally be pretty quick)
                while (!_agentAvailable)
                {
                    long startTime = System.currentTimeMillis();
                    try
                    {
                        wait(_replyTimeout*1000);
                    }
                    catch (InterruptedException ie)
                    {
                        continue;
                    }
                    // Measure elapsed time to test against spurious wakeups and ensure we really have timed out
                    long elapsedTime = (System.currentTimeMillis() - startTime)/1000;
                    if (!_agentAvailable && elapsedTime >= _replyTimeout)
                    {
                        _log.info("Broker Agent not found");
                        throw new QmfException("Broker Agent not found");
                    }
                }

                // Timer used for tidying up Subscriptions.
                _timer = new Timer(true);
            }
        }
        catch (JMSException jmse)
        {
            // If we can't create the QMF Destinations there's not much else we can do
            _log.info("JMSException {} caught in addConnection()", jmse.getMessage());
            throw new QmfException("Failed to create sessions or destinations " + jmse.getMessage());
        }
    }

    /**
     * Remove the AMQP connection from the console. Un-does the addConnection() operation, and releases
     * any Agents associated with the connection. All blocking methods are unblocked and given a failure
     * status. All outstanding asynchronous operations are cancelled without producing WorkItems.
     *
     * @param conn a javax.jms.Connection
     */
    public void removeConnection(final Connection conn) throws QmfException
    {
        if (conn != _connection)
        {
            throw new QmfException("Attempt to delete unknown connection");
        }

        try
        {
            _timer.cancel();
            _connection.close(); // Should we close() the connection here or just stop() it ???
        }
        catch (JMSException jmse)
        {
            throw new QmfException("Failed to remove connection, caught JMSException " + jmse.getMessage());
        }
        _connection = null;
    }

    /**
     * Get the AMQP address this Console is listening to.
     *
     * @return the console's replyTo address. Note that there are actually two, there's a synchronous one
     *         which is the return address for synchronous request/response type invocations and there's an
     *         asynchronous address with a ".async" suffix which is the return address for asynchronous invocations
     */
    public String getAddress()
    {
        return _address;
    }

    /**
     * Query for the presence of a specific agent in the QMF domain. Returns a class Agent if the agent is
     * present.  If the agent is not already known to the console, this call will send a query for the agent
     * and block (with default timeout override) waiting for a response.
     *
     * @param agentName the name of the Agent to be returned.
     * <p>
     * "broker" or "qpidd" may be used as synonyms for the broker Agent name and the method will try to match
     * agentName against the Agent name, the Agent product name and will also check if the Agent name contains
     * the agentName String.
     * <p>
     * Checking against a partial match is useful because the full Agent name has a UUID representing
     * the "instance" so it's hard to know the full name without having actually retrieved the Agent.
     * @return the found Agent instance or null if an Agent of the given name could not be found
     */
    public Agent findAgent(final String agentName)
    {
        return findAgent(agentName, _replyTimeout);
    }

    /**
     * Query for the presence of a specific agent in the QMF domain. Returns a class Agent if the agent is
     * present.  If the agent is not already known to the console, this call will send a query for the agent
     * and block (with specified timeout override) waiting for a response.
     *
     * @param agentName the name of the Agent to be returned.
     * <p>
     * "broker" or "qpidd" may be used as synonyms for the broker Agent name and the method will try to match
     * agentName against the Agent name, the Agent product name and will also check if the Agent name contains
     * the agentName String.
     * <p>
     * Checking against a partial match is useful because the full Agent name has a UUID representing
     * the "instance" so it's hard to know the full name without having actually retrieved the Agent.
     * @param timeout the time (in seconds) to wait for the Agent to be found
     * @return the found Agent instance or null if an Agent of the given name could not be found
     */
    public Agent findAgent(final String agentName, final int timeout)
    {
        Agent agent = getAgent(agentName);
        if (agent == null)
        {
            broadcastAgentLocate();
            long startTime = System.currentTimeMillis();
            do
            {
                agent = getAgent(agentName);
                if (agent != null)
                {
                    return agent;
                }

                synchronized(this)
                {
                    try
                    { // At worst this behaves as a 1 second sleep, but will return sooner if an Agent gets registered
                        wait(1000);
                    }
                    catch (InterruptedException ie)
                    {
                    }
                }
            } while ((System.currentTimeMillis() - startTime)/1000 < _replyTimeout);
        }
        return agent;
    }

    /**
     * Called to enable the asynchronous Agent Discovery process. Once enabled, AGENT_ADDED and AGENT_DELETED
     * work items can arrive on the WorkQueue.
     * <p>
     * Note that in this implementation Agent Discovery is enabled by default. Note too that enableAgentDiscovery()
     * or disableAgentDiscovery() should be called before addConnection(), as this starts a MessageListener Thread
     * that could place events on the work queue.
     */
    public void enableAgentDiscovery()
    {
        _discoverAgents = true;
        _agentQuery = null;
    }

    /**
     * Called to enable the asynchronous Agent Discovery process. Once enabled, AGENT_ADDED and AGENT_DELETED
     * work items can arrive on the WorkQueue. The supplied query will be used to filter agent notifications.
     * <p>
     * Note that in this implementation Agent Discovery is enabled by default. Note too that enableAgentDiscovery()
     * or disableAgentDiscovery() should be called before addConnection(), as this starts a MessageListener Thread
     * that could place events on the work queue.
     *
     * @param query the query used to filter agent notifications.
     */
    public void enableAgentDiscovery(final QmfQuery query)
    {
        _discoverAgents = true;
        _agentQuery = query;
    }

    /**
     * Called to disable the async Agent Discovery process enabled by calling enableAgentDiscovery().
     * <p>
     * Note that in this implementation Agent Discovery is enabled by default. Note too that enableAgentDiscovery()
     * or disableAgentDiscovery() should be called before addConnection() as this starts a MessageListener Thread
     * that could place events on the work queue.
     */
    public void disableAgentDiscovery()
    {
        _discoverAgents = false;
        _agentQuery = null;
    }

    /**
     * Called to disable asynchronous behaviour such as QMF Events, Agent discovery etc. useful in simple
     * Use Cases such as getObjects() on the broker. Note that asynchronous behaviour enabled by default.
     * <p>
     * Note too that disableEvents() should be called <b>before</b> addConnection() as this 
     * starts the MessageListener Thread and creates the additional queues used for Asynchronous Behaviour.
     * <p>
     * This method is <b>not</b> an official method specified in the QMF2 API, however it is a useful extension
     * as Consoles that only call getObjects() on the broker ManagementAgent is an extremely common scenario.
     */
    public void disableEvents()
    {
        _disableEvents = true;
    }

    /**
     * Return the count of pending WorkItems that can be retrieved.
     * @return the count of pending WorkItems that can be retrieved.
     */
    public int getWorkitemCount()
    {
        return _workQueue.size();
    }

    /**
     * Obtains the next pending work item - blocking version.
     * <p>
     * The blocking getNextWorkitem() can be used without the need for a Notifier as it will block until
     * a new item gets added to the work queue e.g. the following usage pattern.
     * <pre>
     *   while ((wi = console.getNextWorkitem()) != null)
     *   {
     *       System.out.println("WorkItem type: " + wi.getType());
     *   }
     * </pre>
     * @return the next pending work item, or null if none available.
     */
    public WorkItem getNextWorkitem()
    {
        return _workQueue.getNextWorkitem();
    }

    /**
     * Obtains the next pending work item - balking version.
     * <p>
     * The balking getNextWorkitem() is generally used with a Notifier which can be used as a gate to determine
     * if any work items are available. e.g. the following usage pattern.
     * <pre>
     *   while (true)
     *   {
     *       notifier.waitForWorkItem(); // Assuming a BlockingNotifier has been used here
     *       System.out.println("WorkItem available, count = " + console.getWorkitemCount());
     *
     *       WorkItem wi;
     *       while ((wi = console.getNextWorkitem(0)) != null)
     *       {
     *           System.out.println("WorkItem type: " + wi.getType());
     *       }
     *   }
     * </pre>
     * Note that it is possible for the getNextWorkitem() loop to retrieve multiple items from the workQueue
     * and for the Console to add new items as the loop is looping, thus when it finally exits and goes
     * back to the outer loop notifier.waitForWorkItems() may return immediately as it had been notified
     * whilst we were in the getNextWorkitem() loop. This will be evident by a getWorkitemCount() of 0
     * after returning from waitForWorkItem().
     * <p>
     * This is the expected behaviour, but illustrates the need to check for nullness of the value returned
     * by getNextWorkitem(), or alternatively to use getWorkitemCount() to put getNextWorkitem() in a
     * bounded loop.
     *
     * @param timeout the timeout in seconds. If timeout = 0 it returns immediately with either a WorkItem or null
     * @return the next pending work item, or null if none available.
     */
    public WorkItem getNextWorkitem(final long timeout)
    {
        return _workQueue.getNextWorkitem(timeout);
    }

    /**
     * Releases a WorkItem instance obtained by getNextWorkItem(). Called when the application has finished
     * processing the WorkItem.
     */
    public void releaseWorkitem()
    {
        // To be honest I'm not clear what the intent of this method actually is. One thought is that it's here
        // to support equivalent behaviour to the Python Queue.task_done() which is used by queue consumer threads.
        // For each get() used to fetch a task, a subsequent call to task_done() tells the queue that the processing
        // on the task is complete.
        //
        // The problem with that theory is there is no equivalent QMF2 API call that would invoke the
        // Queue.join() which is used in conjunction with Queue.task_done() to enable a synchronisation gate to
        // be implemented to wait for completion of all worker thread.
        //
        // I'm a bit stumped and there's no obvious Java equivalent on BlockingQueue, so for now this does nothing.
    }

    /**
     * Returns a list of all known Agents
     * <p>
     * Note that this call is synchronous and non-blocking. It only returns locally cached data and will
     * not send any messages to the remote agent.
     *
     * @return a list of available Agents
     */
    public List<Agent> getAgents()
    {
        return new ArrayList<Agent>(_agents.values());
    }

    /**
     * Return the named Agent, if known.
     *
     * @param agentName the name of the Agent to be returned.
     * <p>
     * "broker" or "qpidd" may be used as synonyms for the broker Agent name and the method will try to match
     * agentName against the Agent name, the Agent product name and will also check if the Agent name contains
     * the agentName String.
     * <p>
     * Checking against a partial match is useful because the full Agent name has a UUID representing
     * the "instance" so it's hard to know the full name without having actually retrieved the Agent.
     * @return the found Agent instance or null if an Agent of the given name could not be found
     */
    public Agent getAgent(final String agentName)
    {
        if (agentName == null)
        {
            return null;
        }

        // First we check if the Agent name is one of the aliases of the broker Agent
        if (_brokerAgentName != null)
        {
            if (agentName.equals("broker") || agentName.equals("qpidd") || agentName.equals(_brokerAgentName))
            {
                return _agents.get(_brokerAgentName);
            }
        }

        for (Agent agent : getAgents())
        {
            String product = agent.getProduct();
            String name = agent.getName();
            if (agentName.equals(product) || agentName.equals(name) || name.contains(agentName))
            {
                return agent;
            }
        }
        
        return null;
    }

    /**
     * Return a list of all known Packages.
     * @return a list of all known Packages.
     */
    public List<String> getPackages()
    {
        List<String> results = new ArrayList<String>();
        for (Agent agent : getAgents())
        {
            results.addAll(getPackages(agent));
        }
        return results;
    }

    /**
     * Return a list of Packages for the specified Agent.
     * @param agent the Agent being queried.
     * @return a list of Packages for the specified Agent.
     */
    public List<String> getPackages(final Agent agent)
    {
        return agent.getPackages();
    }

    /**
     * Return a List of SchemaClassId for all available Schema.
     * @return a List of SchemaClassId for all available Schema.
     */
    public List<SchemaClassId> getClasses()
    {
        List<SchemaClassId> results = new ArrayList<SchemaClassId>();
        for (Agent agent : getAgents())
        {
            results.addAll(getClasses(agent));
        }
        return results;
    }

    /**
     * Return a list of SchemaClassIds for all available Schema for the specified Agent.
     * @param agent the Agent being queried
     * @return a list of SchemaClassIds for all available Schema for the specified Agent.
     */
    public List<SchemaClassId> getClasses(final Agent agent)
    {
        // First look to see if there are cached results and if there are return those.
        List<SchemaClassId> results = agent.getClasses();
        if (results.size() > 0)
        {
            return results;
        }

        String agentName = agent.getName();
        results = new ArrayList<SchemaClassId>();
        try
        {
            MapMessage request = _syncSession.createMapMessage();
            request.setJMSReplyTo(_replyAddress);
            request.setStringProperty("x-amqp-0-10.app-id", "qmf2");
            request.setStringProperty("method", "request");
            request.setStringProperty("qmf.opcode", "_query_request");
            request.setStringProperty("qpid.subject", agentName);

            // Create a QMF Query for an "SCHEMA_ID" target
            request.setObject("_what", "SCHEMA_ID");
            // Wrap request & response in synchronized block in case any other threads invoke a request
            // it would be somewhat unfortunate if their response got interleaved with ours!!
            synchronized(this) 
            {
                _requester.send(request);
                Message response = _responder.receive(_replyTimeout*1000);
                if (response == null)
                {
                    _log.info("No response received in getClasses()");
                    return Collections.emptyList();
                }

                if (AMQPMessage.isAMQPList(response))
                {
                    List<Map> mapResults = AMQPMessage.getList(response);
                    for (Map content : mapResults)
                    {
//new SchemaClassId(content).listValues();
                        results.add(new SchemaClassId(content));
                    }
                }
                else if (AMQPMessage.isAMQPMap(response))
                {
                    // Error responses are returned as MapMessages, though they are being ignored here.
                    //System.out.println("Console.getClasses() no results for " + agentName);
                    //QmfData exception = new QmfData(AMQPMessage.getMap(response));
                    //System.out.println(agentName + " " + exception.getStringValue("error_text"));
                }
                else
                {
                    _log.info("getClasses() Received response message in incorrect format");
                }
            }
        }
        catch (JMSException jmse)
        {
            _log.info("JMSException {} caught in getClasses()", jmse.getMessage());
        }
        agent.setClasses(results);
        return results;
    }

    /**
     * Return a list of all available class SchemaClass across all known Agents. If an optional Agent is
     * provided, restrict the returned schema to those supported by that Agent.
     * <p>
     * This call will return cached information if it is available.  If not, it will send a query message
     * to the remote agent and block waiting for a response. The timeout argument specifies the maximum time
     * to wait for a response from the agent.
     *
     * @param schemaClassId the SchemaClassId we wish to return schema information for.
     */
    public List<SchemaClass> getSchema(final SchemaClassId schemaClassId)
    {
        List<SchemaClass> results = new ArrayList<SchemaClass>();
        List<Agent> agentList = getAgents();
        for (Agent agent : agentList)
        {
            results.addAll(getSchema(schemaClassId, agent));
        }
        return results;
    }

    /**
     * Return a list of all available class SchemaClass from a specified Agent.
     * <p>
     * This call will return cached information if it is available.  If not, it will send a query message
     * to the remote agent and block waiting for a response. The timeout argument specifies the maximum time
     * to wait for a response from the agent.
     *
     * @param schemaClassId the SchemaClassId we wish to return schema information for.
     * @param agent the Agent we want to retrieve the schema from
     */
    public List<SchemaClass> getSchema(final SchemaClassId schemaClassId, final Agent agent)
    {
        // First look to see if there are cached results and if there are return those.
        List<SchemaClass> results = agent.getSchema(schemaClassId);
        if (results.size() > 0)
        {
            return results;
        }

        String agentName = agent.getName();
//System.out.println("getSchema for agent " + agentName);
        results = new ArrayList<SchemaClass>();
        try
        {
            MapMessage request = _syncSession.createMapMessage();
            request.setJMSReplyTo(_replyAddress);
            request.setStringProperty("x-amqp-0-10.app-id", "qmf2");
            request.setStringProperty("method", "request");
            request.setStringProperty("qmf.opcode", "_query_request");
            request.setStringProperty("qpid.subject", agentName);

            // Create a QMF Query for an "SCHEMA" target
            request.setObject("_what", "SCHEMA");
            request.setObject("_schema_id", schemaClassId.mapEncode());

            // Wrap request & response in synchronized block in case any other threads invoke a request
            // it would be somewhat unfortunate if their response got interleaved with ours!!
            synchronized(this)
            {
                _requester.send(request);
                Message response = _responder.receive(_replyTimeout*1000);
                if (response == null)
                {
                    _log.info("No response received in getSchema()");
                    return Collections.emptyList();
                }

                if (AMQPMessage.isAMQPList(response))
                {
                    List<Map> mapResults = AMQPMessage.getList(response);
                    for (Map content : mapResults)
                    {
                        SchemaClass schema = new SchemaObjectClass(content);
                        if (schema.getClassId().getType().equals("_event"))
                        {
                            schema = new SchemaEventClass(content);
                        }
//schema.listValues();
                        results.add(schema);
                    }
                }
                else if (AMQPMessage.isAMQPMap(response))
                {
                    // Error responses are returned as MapMessages, though they are being ignored here.
                    //System.out.println("Console.getSchema() no results for " + agentName);
                    //QmfData exception = new QmfData(AMQPMessage.getMap(response));
                    //System.out.println(agentName + " " + exception.getStringValue("error_text"));
                }
                else
                {
                    _log.info("getSchema() Received response message in incorrect format");
                }
            }
        }
        catch (JMSException jmse)
        {
            _log.info("JMSException {} caught in getSchema()", jmse.getMessage());
        }
        agent.setSchema(schemaClassId, results);
        return results;
    }

    /**
     * Perform a blocking query for QmfConsoleData objects. Returns a list (possibly empty) of matching objects
     * This method will block until all known Agents reply, or the timeout expires. Once the timeout expires, all
     * data retrieved to date is returned.
     * 
     * @param className the schema class name we're looking up objects for.
     * @return a List of QMF Objects describing that class.
     */
    public List<QmfConsoleData> getObjects(final String className)
    {
        return getObjects(new SchemaClassId(className));
    }

    /**
     * Perform a blocking query for QmfConsoleData objects. Returns a list (possibly empty) of matching objects
     * This method will block until all known Agents reply, or the timeout expires. Once the timeout expires, all
     * data retrieved to date is returned.
     * 
     * @param className the schema class name we're looking up objects for.
     * @param timeout overrides the default replyTimeout.
     * @return a List of QMF Objects describing that class.
     */
    public List<QmfConsoleData> getObjects(final String className, final int timeout)
    {
        return getObjects(new SchemaClassId(className), timeout);
    }

    /**
     * Perform a blocking query for QmfConsoleData objects. Returns a list (possibly empty) of matching objects
     * This method will block until all known Agents reply, or the timeout expires. Once the timeout expires, all
     * data retrieved to date is returned.
     * 
     * @param className the schema class name we're looking up objects for.
     * @param agentList if this parameter is supplied then the query is sent to only those Agents.
     * @return a List of QMF Objects describing that class.
     */
    public List<QmfConsoleData> getObjects(final String className, final List<Agent> agentList)
    {
        return getObjects(new SchemaClassId(className), agentList);
    }

    /**
     * Perform a blocking query for QmfConsoleData objects. Returns a list (possibly empty) of matching objects
     * This method will block until all known Agents reply, or the timeout expires. Once the timeout expires, all
     * data retrieved to date is returned.
     * 
     * @param className the schema class name we're looking up objects for.
     * @param timeout overrides the default replyTimeout.
     * @param agentList if this parameter is supplied then the query is sent to only those Agents.
     * @return a List of QMF Objects describing that class.
     */
    public List<QmfConsoleData> getObjects(final String className, final int timeout, final List<Agent> agentList)
    {
        return getObjects(new SchemaClassId(className), timeout, agentList);
    }

    /**
     * Perform a blocking query for QmfConsoleData objects. Returns a list (possibly empty) of matching objects
     * This method will block until all known Agents reply, or the timeout expires. Once the timeout expires, all
     * data retrieved to date is returned.
     * 
     * @param packageName the schema package name we're looking up objects for.
     * @param className the schema class name we're looking up objects for.
     * @return a List of QMF Objects describing that class
     */
    public List<QmfConsoleData> getObjects(final String packageName, final String className)
    {
        return getObjects(new SchemaClassId(packageName, className));
    }

    /**
     * Perform a blocking query for QmfConsoleData objects. Returns a list (possibly empty) of matching objects
     * This method will block until all known Agents reply, or the timeout expires. Once the timeout expires, all
     * data retrieved to date is returned.
     * 
     * @param packageName the schema package name we're looking up objects for.
     * @param className the schema class name we're looking up objects for.
     * @param timeout overrides the default replyTimeout.
     * @return a List of QMF Objects describing that class.
     */
    public List<QmfConsoleData> getObjects(final String packageName, final String className, final int timeout)
    {
        return getObjects(new SchemaClassId(packageName, className), timeout);
    }

    /**
     * Perform a blocking query for QmfConsoleData objects. Returns a list (possibly empty) of matching objects
     * This method will block until all known Agents reply, or the timeout expires. Once the timeout expires, all
     * data retrieved to date is returned.
     * 
     * @param packageName the schema package name we're looking up objects for.
     * @param className the schema class name we're looking up objects for.
     * @param agentList if this parameter is supplied then the query is sent to only those Agents.
     * @return a List of QMF Objects describing that class.
     */
    public List<QmfConsoleData> getObjects(final String packageName, final String className, final List<Agent> agentList)
    {
        return getObjects(new SchemaClassId(packageName, className), agentList);
    }

    /**
     * Perform a blocking query for QmfConsoleData objects. Returns a list (possibly empty) of matching objects
     * This method will block until all known Agents reply, or the timeout expires. Once the timeout expires, all
     * data retrieved to date is returned.
     * 
     * @param packageName the schema package name we're looking up objects for.
     * @param className the schema class name we're looking up objects for.
     * @param timeout overrides the default replyTimeout.
     * @param agentList if this parameter is supplied then the query is sent to only those Agents.
     * @return a List of QMF Objects describing that class.
     */
    public List<QmfConsoleData> getObjects(final String packageName, final String className,
                                           final int timeout, final List<Agent> agentList)
    {
        return getObjects(new SchemaClassId(packageName, className), timeout, agentList);
    }

    /**
     * Perform a blocking query for QmfConsoleData objects. Returns a list (possibly empty) of matching objects
     * This method will block until all known Agents reply, or the timeout expires. Once the timeout expires, all
     * data retrieved to date is returned.
     * 
     * @param query the SchemaClassId or ObjectId we're looking up objects for.
     * @return a List of QMF Objects describing that class.
     */
    public List<QmfConsoleData> getObjects(final QmfData query)
    {
        return getObjects(query, _replyTimeout, getAgents());
    }

    /**
     * Perform a blocking query for QmfConsoleData objects. Returns a list (possibly empty) of matching objects
     * This method will block until all known Agents reply, or the timeout expires. Once the timeout expires, all
     * data retrieved to date is returned.
     * 
     * @param query the SchemaClassId or ObjectId we're looking up objects for.
     * @param timeout overrides the default replyTimeout.
     * @return a List of QMF Objects describing that class.
     */
    public List<QmfConsoleData> getObjects(final QmfData query, final int timeout)
    {
        return getObjects(query, timeout, getAgents());
    }

    /**
     * Perform a blocking query for QmfConsoleData objects. Returns a list (possibly empty) of matching objects
     * This method will block until all known Agents reply, or the timeout expires. Once the timeout expires, all
     * data retrieved to date is returned.
     * 
     * @param query the SchemaClassId or ObjectId we're looking up objects for.
     * @param agentList if this parameter is supplied then the query is sent to only those Agents.
     * @return a List of QMF Objects describing that class.
     */
    public List<QmfConsoleData> getObjects(final QmfData query, final List<Agent> agentList)
    {
        return getObjects(query, _replyTimeout, agentList);
    }

    /**
     * Perform a blocking query for QmfConsoleData objects. Returns a list (possibly empty) of matching objects
     * This method will block until all known Agents reply, or the timeout expires. Once the timeout expires, all
     * data retrieved to date is returned.
     * 
     * @param query the SchemaClassId or ObjectId we're looking up objects for.
     * @param timeout overrides the default replyTimeout.
     * @param agentList if this parameter is supplied then the query is sent to only those Agents.
     * @return a List of QMF Objects describing that class.
     */
    public List<QmfConsoleData> getObjects(final QmfData query, final int timeout, final List<Agent> agentList)
    {
        List<QmfConsoleData> results = new ArrayList<QmfConsoleData>();
        for (Agent agent : agentList)
        {
            results.addAll(getObjects(agent, query, null, timeout));
        }
        return results;
    }

    /**
     * Creates a subscription to the agent using the given Query.
     * <p>
     * The consoleHandle is an application-provided handle that will accompany each subscription update sent from
     * the Agent. Subscription updates will appear as SUBSCRIPTION_INDICATION WorkItems on the Console's work queue.
     *
     * @param agent the Agent on which to create the subscription.
     * @param query the Query to perform on the Agent
     * @param consoleHandle an application-provided handle that will accompany each subscription update sent
     *        from the Agent.
     */
    public SubscribeParams createSubscription(final Agent agent, final QmfQuery query, 
                                              final String consoleHandle) throws QmfException
    {
        return createSubscription(agent, query, consoleHandle, null);
    }

    /**
     * Creates a subscription to the agent using the given Query.
     * <p>
     * The consoleHandle is an application-provided handle that will accompany each subscription update sent from
     * the Agent. Subscription updates will appear as SUBSCRIPTION_INDICATION WorkItems on the Console's work queue.
     * <p>
     * The publishInterval is the requested time interval in seconds on which the Agent should publish updates.
     * <p>
     * The lifetime parameter is the requested time interval in seconds for which this subscription should remain in
     * effect. Both the requested lifetime and publishInterval may be overridden by the Agent, as indicated in the 
     * subscription response.
     * <p>
     * This method may be called asynchronously by providing a replyHandle argument. When called
     * asynchronously, the result of this method call is returned in a SUBSCRIBE_RESPONSE WorkItem with a
     * handle matching the value of replyHandle.
     * <p>
     * Timeout can be used to override the console's default reply timeout.
     * <p>
     * When called synchronously, this method returns a SubscribeParams object containing the result of the
     * subscription request.
     *
     * @param agent the Agent on which to create the subscription.
     * @param query the Query to perform on the Agent
     * @param consoleHandle an application-provided handle that will accompany each subscription update sent
     *        from the Agent.
     * @param options a String representation of a Map containing the options in the form
     *        <pre>"{lifetime:&lt;value&gt;, publishInterval:&lt;value&gt;, replyHandle:&lt;value&gt;, timeout:&lt;value&gt;}"</pre>
     *        they are optional and may appear in any order.
     * <pre>
     *        <b>lifetime</b> the requested time interval in seconds for which this subscription should remain in effect.
     *        <b>publishInterval</b> the requested time interval in seconds on which the Agent should publish updates
     *        <b>replyHandle</b> the correlation handle used to tie asynchronous method requests with responses.
     *        <b>timeout</b> the time to wait for a reply from the Agent.
     * </pre>
     */
    public synchronized SubscribeParams createSubscription(final Agent agent, final QmfQuery query,
                                                final String consoleHandle, final String options) throws QmfException
    {
        if (consoleHandle == null)
        {
            throw new QmfException("Called createSubscription() with null consoleHandle");
        }
        if (_subscriptionByHandle.get(consoleHandle) != null)
        {
            throw new QmfException("Called createSubscription() with a consoleHandle that is already in use");
        }
        if (agent == null)
        {
            throw new QmfException("Called createSubscription() with null agent");
        }
        if (!agent.isActive())
        {
            throw new QmfException("Called createSubscription() with inactive agent");
        }
        String agentName = agent.getName();

        // Initialise optional values to defaults;
        long lifetime = _subscriptionDuration;
        long publishInterval = 10000;
        long timeout = _replyTimeout;
        String replyHandle = null;

        if (options != null)
        { // We wrap the Map in a QmfData object to avoid potential class cast issues with the parsed options
            QmfData optMap = new QmfData(new AddressParser(options).map());
            if (optMap.hasValue("lifetime"))
            {
                lifetime = optMap.getLongValue("lifetime");
            }

            if (optMap.hasValue("publishInterval"))
            { // Multiply publishInterval by 1000 because the QMF2 protocol spec says interval is
              // "The request time (in milliseconds) between periodic updates of data in this subscription"
                publishInterval = 1000*optMap.getLongValue("publishInterval");
            }

            if (optMap.hasValue("timeout"))
            {
                timeout = optMap.getLongValue("timeout");
            }

            if (optMap.hasValue("replyHandle"))
            {
                replyHandle = optMap.getStringValue("replyHandle");
            }
        }

        try
        {
            MapMessage request = _syncSession.createMapMessage();
            request.setJMSReplyTo(_asyncReplyAddress);  // Deliberately forcing all replies to the _asyncReplyAddress
            request.setJMSCorrelationID(consoleHandle); // Deliberately using consoleHandle not replyHandle here
            request.setStringProperty("x-amqp-0-10.app-id", "qmf2");
            request.setStringProperty("method", "request");
            request.setStringProperty("qmf.opcode", "_subscribe_request");
            request.setStringProperty("qpid.subject", agentName);

            request.setObject("_query", query.mapEncode());
            request.setObject("_interval", publishInterval);
            request.setObject("_duration", lifetime);

            SubscriptionManager subscription =
                new SubscriptionManager(agent, query, consoleHandle, replyHandle, publishInterval, lifetime);
            _subscriptionByHandle.put(consoleHandle, subscription);
            _timer.schedule(subscription, 0, publishInterval);

            if (_subscriptionEmulationEnabled && agentName.equals(_brokerAgentName))
            { // If the Agent is the broker Agent we emulate the Subscription on the Console
                String subscriptionId = UUID.randomUUID().toString();
                _subscriptionById.put(subscriptionId, subscription);
                subscription.setSubscriptionId(subscriptionId);
                final SubscribeParams params = new SubscribeParams(consoleHandle, subscription.mapEncode());
                if (replyHandle == null)
                {
                    return params;
                }
                else
                {
                    final String handle = replyHandle;
                    Thread thread = new Thread()
                    {
                        public void run()
                        {
                            _eventListener.onEvent(new SubscribeResponseWorkItem(new Handle(handle), params));
                        }
                    };
                    thread.start();
                }
                return null;
            }

            _requester.send(request);
            if (replyHandle == null)
            { // If this is an synchronous request get the response
                subscription.await(timeout*1000);
                if (subscription.getSubscriptionId() == null)
                {
                    _log.info("No response received in createSubscription()");
                    throw new QmfException("No response received for Console.createSubscription()");
                }
                return new SubscribeParams(consoleHandle, subscription.mapEncode());
            }

            // If this is an asynchronous request return without waiting for a response
            return null;
        }
        catch (JMSException jmse)
        {
            _log.info("JMSException {} caught in createSubscription()", jmse.getMessage());
            throw new QmfException(jmse.getMessage());
        }
    } // end of createSubscription()

    /**
     * Renews a subscription identified by SubscriptionId.
     *
     * @param subscriptionId the ID of the subscription to be refreshed
     */
    public void refreshSubscription(final String subscriptionId) throws QmfException
    {
        refreshSubscription(subscriptionId, null);
    }

    /**
     * Renews a subscription identified by SubscriptionId.
     * <p>
     * The Console may request a new subscription duration by providing a requested lifetime. This method may be called 
     * asynchronously by providing a replyHandle argument.
     * <p>
     * When called asynchronously, the result of this method call is returned in a SUBSCRIBE_RESPONSE WorkItem.
     * <p>
     * Timeout can be used to override the console's default reply timeout.
     * <p>  
     * When called synchronously, this method returns a class SubscribeParams object containing the result of the       
     * subscription request.
     *
     * @param subscriptionId the ID of the subscription to be refreshed
     * @param options a String representation of a Map containing the options in the form
     *        <pre>"{lifetime:&lt;value&gt;, replyHandle:&lt;value&gt;, timeout:&lt;value&gt;}"</pre>
     *        they are optional and may appear in any order.
     * <pre>
     *        <b>lifetime</b> requests a new subscription duration.
     *        <b>replyHandle</b> the correlation handle used to tie asynchronous method requests with responses.
     *        <b>timeout</b> the time to wait for a reply from the Agent.
     * </pre>
     */
    public SubscribeParams refreshSubscription(String subscriptionId, final String options) throws QmfException
    {
        if (subscriptionId == null)
        {
            throw new QmfException("Called refreshSubscription() with null subscriptionId");
        }
        SubscriptionManager subscription = _subscriptionById.get(subscriptionId);
        if (subscription == null)
        {
            throw new QmfException("Called refreshSubscription() with invalid subscriptionId");
        }
        String consoleHandle = subscription.getConsoleHandle();
        Agent agent = subscription.getAgent();
        if (!agent.isActive())
        {
            throw new QmfException("Called refreshSubscription() with inactive agent");
        }
        String agentName = agent.getName();

        // Initialise optional values to defaults;
        long lifetime = 0;
        long timeout = _replyTimeout;
        String replyHandle = null;

        if (options != null)
        { // We wrap the Map in a QmfData object to avoid potential class cast issues with the parsed options
            QmfData optMap = new QmfData(new AddressParser(options).map());
            if (optMap.hasValue("lifetime"))
            {
                lifetime = optMap.getLongValue("lifetime");
            }

            if (optMap.hasValue("timeout"))
            {
                timeout = optMap.getLongValue("timeout");
            }

            if (optMap.hasValue("replyHandle"))
            {
                replyHandle = optMap.getStringValue("replyHandle");
            }
        }

        try
        {
            Destination destination = (replyHandle == null) ? _replyAddress : _asyncReplyAddress;
            MapMessage request = _syncSession.createMapMessage();
            request.setJMSReplyTo(destination);
            request.setJMSCorrelationID(replyHandle);
            request.setStringProperty("x-amqp-0-10.app-id", "qmf2");
            request.setStringProperty("method", "request");
            request.setStringProperty("qmf.opcode", "_subscribe_refresh_indication");
            request.setStringProperty("qpid.subject", agentName);

            request.setObject("_subscription_id", subscriptionId);
            if (lifetime > 0)
            {
                request.setObject("_duration", lifetime);
            }

            // Wrap request & response in synchronized block in case any other threads invoke a request
            // it would be somewhat unfortunate if their response got interleaved with ours!!
            synchronized(this)
            {
                if (_subscriptionEmulationEnabled && agentName.equals(_brokerAgentName))
                { // If the Agent is the broker Agent we emulate the Subscription on the Console
                    subscription.refresh();
                    final SubscribeParams params = new SubscribeParams(consoleHandle, subscription.mapEncode());
                    if (replyHandle == null)
                    {
                        return params;
                    }
                    else
                    {
                        final String handle = replyHandle;
                        Thread thread = new Thread()
                        {
                            public void run()
                            {
                                _eventListener.onEvent(new SubscribeResponseWorkItem(new Handle(handle), params));
                            }
                        };
                        thread.start();
                    }
                    return null;
                }

                _requester.send(request);
                if (replyHandle == null)
                { // If this is an synchronous request get the response
                    Message response = _responder.receive(timeout*1000);
                    if (response == null)
                    {
                        subscription.cancel();
                        _log.info("No response received in refreshSubscription()");
                        throw new QmfException("No response received for Console.refreshSubscription()");
                    }
                    SubscribeParams result = new SubscribeParams(consoleHandle, AMQPMessage.getMap(response));
                    subscriptionId = result.getSubscriptionId();
                    if (subscriptionId == null)
                    {
                        subscription.cancel();
                    }
                    else
                    {
                        subscription.setDuration(result.getLifetime());
                        subscription.refresh();
                    }
                    return result;
                }
            }
            // If this is an asynchronous request return without waiting for a response
            return null;
        }
        catch (JMSException jmse)
        {
            _log.info("JMSException {} caught in refreshSubscription()", jmse.getMessage());
            throw new QmfException(jmse.getMessage());
        }
    } // end of refreshSubscription()

    /**
     * Terminates the given subscription.
     *
     * @param subscriptionId the ID of the subscription to be cancelled
     */
    public void cancelSubscription(final String subscriptionId) throws QmfException
    {
        if (subscriptionId == null)
        {
            throw new QmfException("Called cancelSubscription() with null subscriptionId");
        }
        SubscriptionManager subscription = _subscriptionById.get(subscriptionId);
        if (subscription == null)
        {
            throw new QmfException("Called cancelSubscription() with invalid subscriptionId");
        }
        String consoleHandle = subscription.getConsoleHandle();
        Agent agent = subscription.getAgent();
        if (!agent.isActive())
        {
            throw new QmfException("Called cancelSubscription() with inactive agent");
        }
        String agentName = agent.getName();

        try
        {
            MapMessage request = _syncSession.createMapMessage();
            request.setStringProperty("x-amqp-0-10.app-id", "qmf2");
            request.setStringProperty("method", "request");
            request.setStringProperty("qmf.opcode", "_subscribe_cancel_indication");
            request.setStringProperty("qpid.subject", agentName);
            request.setObject("_subscription_id", subscriptionId);

            synchronized(this)
            {
                if (!_subscriptionEmulationEnabled || !agentName.equals(_brokerAgentName))
                {
                    _requester.send(request);
                }
            }
            subscription.cancel();
        }
        catch (JMSException jmse)
        {
            _log.info("JMSException {} caught in cancelSubscription()", jmse.getMessage());
        }
    }
}
