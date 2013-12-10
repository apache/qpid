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
package org.apache.qpid.qmf2.agent;

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
import java.util.TimerTask;
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

/**
 * A QMF agent component is represented by a instance of the Agent class. This class is the topmost object
 * of the Agent application's object model. Associated with a particular agent are:
 * <pre>
 * * The set of objects managed by that agent
 * * The set of schema that describes the structured objects owned by the agent
 * * A collection of Consoles that are interfacing with the agent
 * </pre>
 * The Agent class communicates with the application using the same work-queue model as the Console.
 * The agent maintains a work-queue of pending requests. Each pending request is associated with a handle.
 * When the application is done servicing the work request, it passes the response to the agent along with
 * the handle associated with the originating request.
 * <p>
 * The base class for the Agent object is the Agent class. This base class represents a single agent
 * implementing internal store.
 *
 * <h3>Subscriptions</h3>
 * This implementation of the QMF2 API has full support for QMF2 Subscriptions.
 * <p>
 * The diagram below shows the relationship between the Agent, the Subscription and SubscribableAgent interface.
 * <p>
 * <img src="doc-files/Subscriptions.png"/>
 * <p>
 * <h3>Receiving Asynchronous Notifications</h3>
 * This implementation of the QMF2 Agent actually supports two independent APIs to enable clients to receive
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
 * <img src="doc-files/QmfEventListenerModel.png"/>
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
 * application derives a custom notification handler from this class, and makes it available to the Console or Agent object.
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
 * <img src="doc-files/WorkQueueEventModel.png"/>
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
public class Agent extends QmfData implements MessageListener, SubscribableAgent
{
    private static final Logger _log = LoggerFactory.getLogger(Agent.class);

    /** 
     * This TimerTask causes the Agent to sent a Hearbeat when it gets scheduled
     */
    private final class Heartbeat extends TimerTask
    {
        public void run()
        {
            try
            {
                String vendorKey = _vendor.replace(".", "_");
                String productKey = _product.replace(".", "_");
                String instanceKey = _instance.replace(".", "_");
                String subject = "agent.ind.heartbeat." + vendorKey + "." + productKey + "." + instanceKey;

                MapMessage response = _syncSession.createMapMessage();
                response.setStringProperty("x-amqp-0-10.app-id", "qmf2");
                response.setStringProperty("method", "indication");
                response.setStringProperty("qmf.opcode", "_agent_heartbeat_indication");
                response.setStringProperty("qmf.agent", _name);
                response.setStringProperty("qpid.subject", subject);
                setValue("_timestamp", System.currentTimeMillis()*1000000l);
                response.setObject("_values", mapEncode());
            
                // Send heartbeat messages with a Time To Live (in msecs) set to two times the _heartbeatInterval
                // to prevent stale heartbeats from getting to the consoles.
                _broadcaster.send(response, Message.DEFAULT_DELIVERY_MODE, Message.DEFAULT_PRIORITY,
                                  _heartbeatInterval*2000);
            }
            catch (JMSException jmse)
            {
                _log.info("JMSException {} caught in sendHeartbeat()", jmse.getMessage());
            }

            // Reap any QmfAgentData Objects that have been marked as Deleted
            // Use the iterator approach rather than foreach as we may want to call iterator.remove() to zap an entry
            Iterator<QmfAgentData> i = _objectIndex.values().iterator();
            while (i.hasNext())
            {
                QmfAgentData object = i.next();
                if (object.isDeleted())
                {
                    _log.debug("Removing deleted QmfAgentData Object from store");
                    i.remove();
                }
            }
        }
    }

    //                                             Attributes
    // ********************************************************************************************************

    /**
     * The _eventListener may be a real application QmfEventListener, a NullQmfEventListener or an application
     * Notifier wrapped in a QmfEventListener. In all cases the Agent may call _eventListener.onEvent() at
     * various places to pass a WorkItem to an asynchronous receiver.
     */
    private QmfEventListener _eventListener;

    /**
     * _schemaCache holds references to the Schema objects for easy lookup so we can return the info to
     * the Console if necessary
     */
    private Map<SchemaClassId, SchemaClass> _schemaCache = new ConcurrentHashMap<SchemaClassId, SchemaClass>();

    /**
     * _objectIndex is the global index of QmfAgentData objects registered with this Agent.
     * The capacity of 100 is pretty arbitrary but the default of 16 seems too low for most Agents.
     */
    private Map<ObjectId, QmfAgentData> _objectIndex = new ConcurrentHashMap<ObjectId, QmfAgentData>(100);

    /**
     * This Map is used to look up Subscriptions by SubscriptionId
     */
    private Map<String, Subscription> _subscriptions = new ConcurrentHashMap<String, Subscription>();

    /**
     * Used to implement a thread safe queue of WorkItem objects used to implement the Notifier API
     */
    private WorkQueue _workQueue = new WorkQueue();

    /**
     * If a name is supplied, it must be unique across all attached to the AMQP bus under the given domain.
     * The name must comprise three parts separated by colons: <vendor>:<product>[:<instance>], where the
     * vendor is the Agent vendor name, the product is the Agent product itself and the instance is a UUID
     * representing the running instance. If the instance is not supplied then a random UUID will be generated
     */
    private String _name;

    /**
     * The Agent vendor name
     */
    private String _vendor;

    /**
     * The Agent product name
     */
    private String _product;

    /**
     * A UUID representing the running instance
     */
    private String _instance = UUID.randomUUID().toString();

    /**
     * The epoch may be used to maintain a count of the number of times an agent has been restarted. By
     * incrementing this value and keeping a constant instance value an Agent can indicate to a client
     * that it is a persistent Agent and has been restarted. The Broker Management Agent behaves in this way.
     */
    private int _epoch = 1;

    /**
     * The interval that this Agent waits between sending out hearbeat messages
     */
    private int _heartbeatInterval = 30;

    /**
     * The domain string is used to construct the name of the AMQP exchange to which the component's 
     * name string will be bound. If not supplied, the value of the domain defaults to "default". Both
     * Agents and Components must belong to the same domain in order to communicate.
     */
    private String _domain;

    /**
     * This timer is used to schedule periodic events such as sending Heartbeats and subscription updates
     */
    private Timer _timer;

    /**
     * Various JMS related fields
     */
    private Connection _connection = null;
    private Session _asyncSession;
    private Session _syncSession; 
    private MessageConsumer _locateConsumer;
    private MessageConsumer _mainConsumer;
    // _aliasConsumer is used for the alias address if the Agent is a broker Agent (used in Java Broker QMF plugin)
    private MessageConsumer _aliasConsumer;
    private MessageProducer _responder; 
    private MessageProducer _broadcaster;

    /**
     * This contains a String representation of the broadcastAddress used to decide whether to use _responder or
     * _broadcaster as the MessageProducer for Message responses. See the comments for the sendResponse() method below.
     */
    private String _broadcastAddress;

    //                                  private implementation methods
    // ********************************************************************************************************


    /**
     * There's some slight "hackery" below. The Agent clearly needs to respond to requests and quite possibly using
     * the JMS replyTo is the correct thing to do, however in older versions of Qpid invoking send() on the replyTo 
     * causes spurious exchangeDeclares to occur and the caching of replyTo wasn't as good as it might be.
     * To get around this the Agent actually uses the relevant exchange name as the core address and sets the Message
     * "qpid.subject" with an appropriate Routing Key. The problem occurs if Console clients decide to use the
     * qmf.default.topic as a replyTo instead of qmf.default.direct so we check the start of the replyTo using
     * _broadcastAddress. It's slightly hacky because the Destination.toString() could change as it's implemenation
     * specific. That shouldn't be too much of a pain as most clients should use qmf.default.direct.
     * @param handle the reply handle that contains the replyTo Address.
     * @param message the JMS Message to be sent.
     */
    private final void sendResponse(final Handle handle, final Message message) throws JMSException
    {
        // A replyTo looks a bit like 'qmf.default.direct'/'direct.95dab79b-0d3e-4214-9f55-f9efb146c101'; None
        // so we check if it starts with 'qmf.default.topic' and if so use the _broadcaster MessageProducer
        // otherwise use the _responder. N.B. if the Destination.toString() format changes this will fail and
        // always sent to _responder, though *most* clients (hear me qpid-config!!!) use qmf.default.direct.
        String replyTo = handle.getReplyTo().toString();

        if (replyTo.startsWith(_broadcastAddress))
        {
            _broadcaster.send(message);
        }
        else
        {
            _responder.send(message);
        }
    }

    /**
     * Send an _agent_locate_response back to the Console that requested the locate.
     * @param handle the reply handle that contains the replyTo Address.
     */
    private final void handleLocateRequest(final Handle handle)
    {
        try
        {
            MapMessage response = _syncSession.createMapMessage();
            response.setStringProperty("x-amqp-0-10.app-id", "qmf2");
            response.setStringProperty("method", "indication");
            response.setStringProperty("qmf.opcode", "_agent_locate_response");
            response.setStringProperty("qmf.agent", _name);
            response.setStringProperty("qpid.subject", handle.getRoutingKey());
            setValue("_timestamp", System.currentTimeMillis()*1000000l);
            response.setObject("_values", mapEncode());
            sendResponse(handle, response);
        }
        catch (JMSException jmse)
        {
            _log.info("JMSException {} caught in handleLocateRequest()", jmse.getMessage());
        }
    }

    /**
     * Handle the query request and send the response back to the Console.
     * @param handle the reply handle that contains the replyTo Address.
     * @param query the inbound query from the Console.
     */
    @SuppressWarnings("unchecked")
    private final void handleQueryRequest(final Handle handle, final QmfQuery query)
    {
        QmfQueryTarget target = query.getTarget();

        if (target == QmfQueryTarget.SCHEMA_ID)
        {
            List<Map> results = new ArrayList<Map>(_schemaCache.size());
            // Look up all SchemaClassId objects
            for (SchemaClassId classId : _schemaCache.keySet())
            {
                results.add(classId.mapEncode());
            }
            queryResponse(handle, results, "_schema_id"); // Send the response back to the Console.
        }
        else if (target == QmfQueryTarget.SCHEMA)
        {
            List<Map> results = new ArrayList<Map>(1);
            // Look up a SchemaClass object by the SchemaClassId obtained from the query
            SchemaClassId classId = query.getSchemaClassId();
            SchemaClass schema = _schemaCache.get(classId);
            if (schema != null)
            {
                results.add(schema.mapEncode());
            }
            queryResponse(handle, results, "_schema"); // Send the response back to the Console.
        }
        else if (target == QmfQueryTarget.OBJECT_ID)
        {
            List<Map> results = new ArrayList<Map>(_objectIndex.size());
            // Look up all ObjectId objects
            for (ObjectId objectId : _objectIndex.keySet())
            {
                results.add(objectId.mapEncode());
            }
            queryResponse(handle, results, "_object_id"); // Send the response back to the Console.
        }
        else if (target == QmfQueryTarget.OBJECT)
        {
            // If this is implementing the AgentExternal model we pass the QmfQuery on in a QueryWorkItem
            if (this instanceof AgentExternal)
            {
                _eventListener.onEvent(new QueryWorkItem(handle, query));
                return;
            }
            else
            { // If not implementing the AgentExternal model we handle the Query ourself.
                //qmfContentType = "_data";
                if (query.getObjectId() != null)
                {
                    List<Map> results = new ArrayList<Map>(1);
                    // Look up a QmfAgentData object by the ObjectId obtained from the query
                    ObjectId objectId = query.getObjectId();
                    QmfAgentData object = _objectIndex.get(objectId);
                    if (object != null && !object.isDeleted())
                    {
                        results.add(object.mapEncode());
                    }
                    queryResponse(handle, results, "_data"); // Send the response back to the Console.
                }
                else
                {
                    // Look up QmfAgentData objects by the SchemaClassId obtained from the query
                    // This is implemented by a linear search and allows searches with only the className specified.
                    // Linear searches clearly don't scale brilliantly, but the number of QmfAgentData objects managed
                    // by an Agent is generally fairly small, so it should be OK. Note that this is the same approach
                    // taken by the C++ broker ManagementAgent, so if it's a problem here........

                    // N.B. the results list declared here is a generic List of Objects. We *must* only pass a List of
                    // Map to queryResponse(), but conversely if the response items are sortable we need tp sort them
                    // before doing mapEncode(). Unfortunately we don't know if the items are sortable a priori so
                    // we either add a Map or we add a QmfAgentData, then sort then mapEncode() each item. I'm not
                    // sure of a more elegant way to do this without creating two lists, which might not be so bad
                    // but we don't know the size of the list a priori either.
                    List results = new ArrayList(_objectIndex.size());
                    // It's unlikely that evaluating this query will return a mixture of sortable and notSortable 
                    // QmfAgentData objects, but it's best to check if that has occurred as accidentally passing a
                    // List of QmfAgentData instead of a List of Map to queryResponse() will break things.
                    boolean sortable = false;
                    boolean notSortable = false;
                    for (QmfAgentData object : _objectIndex.values())
                    {
                        if (!object.isDeleted() && query.evaluate(object))
                        {
                            if (object.isSortable())
                            { // If QmfAgentData is marked sortable we add the QmfAgentData object to the List
                              // so we can sort first before mapEncoding.
                                results.add(object);
                                sortable = true;
                            }
                            else
                            { // If QmfAgentData is not marked sortable we mapEncode immediately and add the Map to List.
                                results.add(object.mapEncode());
                                notSortable = true;
                            }
                        }
                    }

                    // If both flags have been set something has gone a bit weird, so we log an error and clear the
                    // results List to avoid sending unconvertable data. Hopefully this condition should never occur.
                    if (sortable && notSortable)
                    {
                        _log.info("Query resulted in inconsistent mixture of sortable and non-sortable data.");
                        results.clear();
                    }
                    else if (sortable)
                    {
                        Collections.sort(results);
                        int length = results.size();
                        for (int i = 0; i < length; i++)
                        {
                            QmfAgentData object = (QmfAgentData)results.get(i);
                            results.set(i, object.mapEncode());
                        }
                    }
                    queryResponse(handle, results, "_data"); // Send the response back to the Console.
                }
            }
        }
        else
        {
            raiseException(handle, "Query for _what => '" + target + "' not supported");
            return;
        }
    } // end of handleQueryRequest()

    /**
     * Return a QmfAgentData from the internal Object store given its ObjectId.
     * N.B. This method isn't part of the *official* QMF2 public API, however it is pretty useful and probably
     * should be (as should evaluateQuery()). Given that wi.getType() == METHOD_CALL primarily uses the pattern:
     * <pre>
     * MethodCallWorkItem item = (MethodCallWorkItem)wi;
     * MethodCallParams methodCallParams = item.getMethodCallParams();
     * String methodName = methodCallParams.getName();
     * ObjectId objectId = methodCallParams.getObjectId();
     * </pre>
     * to identify the method name and Object instance it seems odd not to have a public API method to look up
     * said Object by ObjectId. Clearly a separate Map could be maintained in client code but that seems pointless.
     */
    public final QmfAgentData getObject(ObjectId objectId)
    {
        return _objectIndex.get(objectId);
    }

    /**
     * Send an exception back to the Console.
     * @param handle the reply handle that contains the replyTo Address.
     * @param message the exception message.
     */
    public final void raiseException(final Handle handle, final String message)
    {
        try
        {
            MapMessage response = _syncSession.createMapMessage();
            response.setJMSCorrelationID(handle.getCorrelationId());
            response.setStringProperty("x-amqp-0-10.app-id", "qmf2");
            response.setStringProperty("method", "response");
            response.setStringProperty("qmf.opcode", "_exception");
            response.setStringProperty("qmf.agent", _name);
            response.setStringProperty("qpid.subject", handle.getRoutingKey());

            QmfData exception = new QmfData();
            exception.setValue("error_text", message);
            response.setObject("_values", exception.mapEncode());
            sendResponse(handle, response);
        }
        catch (JMSException jmse)
        {
            _log.info("JMSException {} caught in handleLocateRequest()", jmse.getMessage());
        }
    }

    //                               methods implementing SubscribableAgent interface
    // ********************************************************************************************************

    /**
     * Send a list of updated subscribed data to the Console.
     *
     * @param handle the console reply handle.
     * @param results a list of subscribed data in Map encoded form.
     */
    public final void sendSubscriptionIndicate(final Handle handle, final List<Map> results)
    {
        try
        {
            Message response = AMQPMessage.createListMessage(_syncSession);
            response.setJMSCorrelationID(handle.getCorrelationId());
            response.setStringProperty("x-amqp-0-10.app-id", "qmf2");
            response.setStringProperty("method", "indication");
            response.setStringProperty("qmf.opcode", "_data_indication");
            response.setStringProperty("qmf.content", "_data");
            response.setStringProperty("qmf.agent", _name);
            response.setStringProperty("qpid.subject", handle.getRoutingKey());
            AMQPMessage.setList(response, results);
            sendResponse(handle, response);
        }
        catch (JMSException jmse)
        {
            _log.info("JMSException {} caught in sendSubscriptionIndicate()", jmse.getMessage());
        }
    }

    /**
     * This method evaluates a QmfQuery over the Agent's data on behalf of a Subscription.
     *
     * @param query the QmfQuery that the Subscription wants to be evaluated over the Agent's data.
     * @return a List of QmfAgentData objects that match the specified QmfQuery.
     */
    public final List<QmfAgentData> evaluateQuery(final QmfQuery query)
    {
        List<QmfAgentData> results = new ArrayList<QmfAgentData>(_objectIndex.size());
        if (query.getTarget() == QmfQueryTarget.OBJECT)
        { // Note that we don't include objects marked as deleted in the results here, because if an object gets
          // destroyed we asynchronously publish its new state to subscribers, see QmfAgentData.destroy() method.
            if (query.getObjectId() != null)
            {
                // Look up a QmfAgentData object by the ObjectId obtained from the query
                ObjectId objectId = query.getObjectId();
                QmfAgentData object = _objectIndex.get(objectId);
                if (object != null && !object.isDeleted())
                {
                    results.add(object);
                }
            }
            else
            {
                // Look up QmfAgentData objects evaluating the query
                for (QmfAgentData object : _objectIndex.values())
                {
                    if (!object.isDeleted() && query.evaluate(object))
                    {
                        results.add(object);
                    }
                }
            }
        }
        return results;
    }

    /**
     * This method is called by the Subscription to tell the SubscribableAgent that the Subscription has been cancelled.
     *
     * @param subscription the Subscription that has been cancelled and is requesting removal.
     */
    public final void removeSubscription(final Subscription subscription)
    {
        _subscriptions.remove(subscription.getSubscriptionId());
    }

    //                                          MessageListener
    // ********************************************************************************************************

    /**
     * MessageListener for QMF2 Console requests.
     *
     * @param message the JMS Message passed to the listener.
     */
    public final void onMessage(final Message message)
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

            Handle handle = new Handle(message.getJMSCorrelationID(), message.getJMSReplyTo());

            if (opcode.equals("_agent_locate_request"))
            {
                handleLocateRequest(handle);
            }
            else if (opcode.equals("_method_request"))
            {
                if (AMQPMessage.isAMQPMap(message))
                {
                    _eventListener.onEvent
                    (
                        new MethodCallWorkItem(handle, new MethodCallParams(AMQPMessage.getMap(message)))
                    );
                }
                else
                {
                    _log.info("onMessage() Received Method Request message in incorrect format");
                }
            }
            else if (opcode.equals("_query_request"))
            {
                if (AMQPMessage.isAMQPMap(message))
                {
                    try
                    {
                        QmfQuery query = new QmfQuery(AMQPMessage.getMap(message));
                        handleQueryRequest(handle, query);
                    }
                    catch (QmfException qmfe)
                    {
                        raiseException(handle, "Query Request failed, invalid Query: " + qmfe.getMessage());
                    }
                }
                else
                {
                    _log.info("onMessage() Received Query Request message in incorrect format");
                }
            }
            else if (opcode.equals("_subscribe_request"))
            {
                if (AMQPMessage.isAMQPMap(message))
                {
                    try
                    {
                        SubscriptionParams subscriptionParams =
                            new SubscriptionParams(handle, AMQPMessage.getMap(message));
                        if (this instanceof AgentExternal)
                        {
                            _eventListener.onEvent(new SubscribeRequestWorkItem(handle, subscriptionParams));
                        }
                        else
                        {
                            Subscription subscription = new Subscription(this, subscriptionParams);
                            String subscriptionId = subscription.getSubscriptionId();
                            _subscriptions.put(subscriptionId, subscription);
                            _timer.schedule(subscription, 0, subscriptionParams.getPublishInterval());
                            subscriptionResponse(handle, subscription.getConsoleHandle(), subscriptionId, 
                                                 subscription.getDuration(), subscription.getInterval(), null);
                        }
                    }
                    catch (QmfException qmfe)
                    {
                        raiseException(handle, "Subscribe Request failed, invalid Query: " + qmfe.getMessage());
                    }
                }
                else
                {
                    _log.info("onMessage() Received Subscribe Request message in incorrect format");
                }
            }
            else if (opcode.equals("_subscribe_refresh_indication"))
            {
                if (AMQPMessage.isAMQPMap(message))
                {
                    ResubscribeParams resubscribeParams = new ResubscribeParams(AMQPMessage.getMap(message));
                    if (this instanceof AgentExternal)
                    {
                        _eventListener.onEvent(new ResubscribeRequestWorkItem(handle, resubscribeParams));
                    }
                    else
                    {
                        String subscriptionId = resubscribeParams.getSubscriptionId();
                        Subscription subscription = _subscriptions.get(subscriptionId);
                        if (subscription != null)
                        {
                            subscription.refresh(resubscribeParams);
                            subscriptionResponse(handle,
                                                 subscription.getConsoleHandle(), subscription.getSubscriptionId(), 
                                                 subscription.getDuration(), subscription.getInterval(), null);
                        }
                    }
                }
                else
                {
                    _log.info("onMessage() Received Resubscribe Request message in incorrect format");
                }
            }
            else if (opcode.equals("_subscribe_cancel_indication"))
            {
                if (AMQPMessage.isAMQPMap(message))
                {
                    QmfData qmfSubscribe = new QmfData(AMQPMessage.getMap(message));
                    String subscriptionId = qmfSubscribe.getStringValue("_subscription_id");
                    if (this instanceof AgentExternal)
                    {
                        _eventListener.onEvent(new UnsubscribeRequestWorkItem(subscriptionId));
                    }
                    else
                    {
                        Subscription subscription = _subscriptions.get(subscriptionId);
                        if (subscription != null)
                        {
                            subscription.cancel();
                        }
                    }
                }
                else
                {
                    _log.info("onMessage() Received Subscribe Cancel Request message in incorrect format");
                }
            }
        }
        catch (JMSException jmse)
        {
            _log.info("JMSException {} caught in onMessage()", jmse.getMessage());
        }
    } // end of onMessage()

    //                                          QMF API Methods
    // ********************************************************************************************************

    /**
     * Constructor that provides defaults for name, domain  and heartbeat interval and takes a Notifier/Listener.
     *
     * @param notifier this may be either a QMF2 API Notifier object OR a QMFEventListener.
     * <p>
     * The latter is an alternative API that avoids the need for an explicit Notifier thread to be created the
     * EventListener is called from the JMS MessageListener thread.
     * <p>
     * This API may be simpler and more convenient than the QMF2 Notifier API for many applications.
     */
    public Agent(final QmfCallback notifier) throws QmfException
    {
        this(null, null, notifier, 30);
    }

    /**
     * Constructor that provides defaults for name and domain and takes a Notifier/Listener
     *
     * @param notifier this may be either a QMF2 API Notifier object OR a QMFEventListener.
     * <p>
     * The latter is an alternative API that avoids the need for an explicit Notifier thread to be created the
     * EventListener is called from the JMS MessageListener thread.
     * <p>
     * This API may be simpler and more convenient than the QMF2 Notifier API for many applications.
     * @param interval is the heartbeat interval in seconds.
     */
    public Agent(final QmfCallback notifier, final int interval) throws QmfException
    {
        this(null, null, notifier, interval);
    }

    /**
     * Main constructor, creates a Agent, but does NOT start it, that requires us to do setConnection()
     *
     * @param name If a name is supplied, it must be unique across all agents attached to the AMQP bus under the
     * given domain.
     * <p>
     * The name must comprise three parts separated by colons: <pre>&lt;vendor&gt;:&lt;product&gt;[:&lt;instance&gt;]</pre>
     * where the vendor is the Agent vendor name, the product is the Agent product itself and the instance is a UUID
     * representing the running instance.
     * <p>
     * If the instance is not supplied then a random UUID will be generated.
     * @param domain the QMF "domain".
     * <p>
     * A QMF address is composed of two parts - an optional domain string, and a mandatory name string
     * <pre>"qmf.&lt;domain-string&gt;.direct/&lt;name-string&gt;"</pre>
     * The domain string is used to construct the name of the AMQP exchange to which the component's name string will
     * be bound. If not supplied, the value of the domain defaults to "default".
     * <p>
     * Both Agents and Consoles must belong to the same domain in order to communicate.
     * @param notifier this may be either a QMF2 API Notifier object OR a QMFEventListener.
     * <p>
     * The latter is an alternative API that avoids the need for an explicit Notifier thread to be created the
     * EventListener is called from the JMS MessageListener thread.
     * <p>
     * This API may be simpler and more convenient than the QMF2 Notifier API for many applications.
     * @param interval is the heartbeat interval in seconds.
     */
    public Agent(final String name, final String domain,
                 final QmfCallback notifier, final int interval) throws QmfException
    {
        if (name != null)
        {
            String[] split = name.split(":");
            if (split.length < 2 || split.length > 3)
            {
                throw new QmfException("Agent name must be in the format <vendor>:<product>[:<instance>]");
            }

            _vendor = split[0];
            _product = split[1];

            if (split.length == 3)
            {
                _instance = split[2];
            }

            _name = _vendor + ":" + _product + ":" + _instance;
        }

        _domain = (domain == null) ? "default" : domain;

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

        if (interval > 0)
        {
            _heartbeatInterval = interval;
        }
    }

    /**
     * Returns the name string of the agent.
     * @return the name string of the agent.
     */
    public final String getName()
    {
        return _name;
    }

    /**
     * Set the vendor String, must be called before setConnection().
     * @param vendor the vendor name.
     */
    public final void setVendor(final String vendor)
    {
        _vendor = vendor;
        _name = _vendor + ":" + _product + ":" + _instance;
    }

    /**
     * Set the product String, must be called before setConnection().
     * @param product the product name.
     */
    public final void setProduct(final String product)
    {
        _product = product;
        _name = _vendor + ":" + _product + ":" + _instance;
    }

    /**
     * Set the instance String, must be called before setConnection().
     * @param instance the instance value.
     */
    public final void setInstance(final String instance)
    {
        _instance = instance;
        _name = _vendor + ":" + _product + ":" + _instance;
    }

    /**
     * Returns the current epoch value.
     * @return the current epoch value.
     */
    public final int getEpoch()
    {
        return _epoch;
    }

    /**
     * Set the new epoch value.
     * @param epoch the new epoch value.
     */
    public final void setEpoch(final int epoch)
    {
        _epoch = epoch;
    }

    /**
     * Releases Agent's resources.
     */
    public final void destroy()
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
     * Connect the Agent to the AMQP cloud.
     *
     * @param conn a javax.jms.Connection.
     */
    public final void setConnection(final Connection conn) throws QmfException
    {
        setConnection(conn, "");
    }

    /**
     * Connect the Agent to the AMQP cloud.
     * <p>
     * This is an extension to the standard QMF2 API allowing the user to specify address options in order to allow
     * finer control over the Agent's ingest queue, such as an explicit name, non-default size or durability.
     *
     * @param conn a javax.jms.Connection.
     * @param addressOptions options String giving finer grained control of the receiver queue.
     * <p>
     * As an example the following gives the Agent's ingest queue the name test-agent, size = 500000000 and ring policy.
     * <pre>
     * " ; {link: {name:'test-agent', x-declare: {arguments: {'qpid.policy_type': ring, 'qpid.max_size': 500000000}}}}"
     * </pre>
     */
    public final void setConnection(final Connection conn, final String addressOptions) throws QmfException
    {
        // Make the test and set of _connection synchronized just in case multiple threads attempt to add a _connection
        // to the same Agent instance at the same time.
        synchronized(this)
        {
            if (_connection != null)
            {
                throw new QmfException("Multiple connections per Agent is not supported");
            }
            _connection = conn;
        }

        if (_name == null || _vendor == null || _product == null)
        {
            throw new QmfException("The vendor, product or name is not set");
        }

        setValue("_epoch", _epoch);
        setValue("_heartbeat_interval", _heartbeatInterval);
        setValue("_name", _name);
        setValue("_product", _product);
        setValue("_vendor", _vendor);
        setValue("_instance", _instance);

        try
        {
            String directBase = "qmf." + _domain + ".direct";
            String topicBase  = "qmf." + _domain + ".topic";
            String address = directBase + "/" + _name + addressOptions;

            _asyncSession = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            _syncSession = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Create a MessageProducer for the QMF topic address used to broadcast Events & Heartbeats.
            Destination topicAddress = _syncSession.createQueue(topicBase);
            _broadcaster = _syncSession.createProducer(topicAddress);
            _broadcastAddress = "'" + topicBase + "'";

            // Create a MessageProducer for the QMF direct address, mainly used for request/response
            Destination directAddress = _syncSession.createQueue(directBase);
            _responder = _syncSession.createProducer(directAddress);

            // TODO it should be possible to bind _locateConsumer, _mainConsumer and _aliasConsumer to the
            // same queue if I can figure out the correct AddressString to use, probably not a big deal though.

            // Set up MessageListener on the Agent Locate Address
            Destination locateAddress = _asyncSession.createQueue(topicBase + "/console.request.agent_locate");
            _locateConsumer = _asyncSession.createConsumer(locateAddress);
            _locateConsumer.setMessageListener(this);

            // Set up MessageListener on the Agent address
            Destination agentAddress = _asyncSession.createQueue(address);
            _mainConsumer = _asyncSession.createConsumer(agentAddress);
            _mainConsumer.setMessageListener(this);

            // If the product name has been set to qpidd we create an additional consumer address of
            // "qmf.default.direct/broker" in addition to the main address so that Consoles can talk to the
            // broker Agent without needing to do Agent discovery. This is only really needed when the Agent
            // class has been used to create the QmfManagementAgent for the Java broker QmfManagementPlugin.
            // It's important to do this as many tools (such as qpid-config) and demo code tend to use the
            // alias address rather than the discovered address when talking to the broker ManagementAgent.
            if (_product.equals("qpidd"))
            {
                String alias = directBase + "/broker";
                _log.info("Creating address {} as an alias address for the broker Agent", alias);
                Destination aliasAddress = _asyncSession.createQueue(alias);
                _aliasConsumer = _asyncSession.createConsumer(aliasAddress);
                _aliasConsumer.setMessageListener(this);
            }

            _connection.start();

            // Schedule a Heartbeat every _heartbeatInterval seconds sending the first one immediately
            _timer = new Timer(true);
            _timer.schedule(new Heartbeat(), 0, _heartbeatInterval*1000);
        }
        catch (JMSException jmse)
        {
            // If we can't create the QMF Destinations there's not much else we can do
            _log.info("JMSException {} caught in setConnection()", jmse.getMessage());
            throw new QmfException("Failed to create sessions or destinations " + jmse.getMessage());
        }
    } // end of setConnection()

    /**
     * Remove the AMQP connection from the Agent. Un-does the setConnection() operation.
     *
     * @param conn a javax.jms.Connection.
     */
    public final void removeConnection(final Connection conn) throws QmfException
    {
        if (conn != _connection)
        {
            throw new QmfException("Attempt to delete unknown connection");
        }

        try
        {
            _timer.cancel();
            _connection.close();
        }
        catch (JMSException jmse)
        {
            throw new QmfException("Failed to remove connection, caught JMSException " + jmse.getMessage());
        }
        _connection = null;
    }

    /**
     * Register a schema for an object class with the Agent.
     * <p>
     * The Agent must have a registered schema for an object class before it can handle objects of that class.
     *
     * @param schema the SchemaObjectClass to be registered
     */
    public final void registerObjectClass(final SchemaObjectClass schema)
    {
        SchemaClassId classId = schema.getClassId();
        _schemaCache.put(classId, schema);
    }

    /**
     * Register a schema for an event class with the Agent.
     * <p>
     * The Agent must have a registered schema for an event class before it can handle events of that class.
     *
     * @param schema the SchemaEventClass to be registered
     */
    public final void registerEventClass(final SchemaEventClass schema)
    {
        SchemaClassId classId = schema.getClassId();
        _schemaCache.put(classId, schema);
    }

    /**
     * Cause the agent to raise the given event.
     *
     * @param event the QmfEvent to be raised
     */
    public final void raiseEvent(final QmfEvent event)
    {
        try
        {
            String packageKey = event.getSchemaClassId().getPackageName().replace(".", "_");
            String nameKey = event.getSchemaClassId().getClassName().replace(".", "_");
            String severity = event.getSeverity();
            String vendorKey = _vendor.replace(".", "_");
            String productKey = _product.replace(".", "_");
            String instanceKey = _instance.replace(".", "_");

            String subject = "agent.ind.event." + packageKey + "." + nameKey + "." + severity + "." + vendorKey + "." + 
                              productKey + "." + instanceKey;

            Message response = AMQPMessage.createListMessage(_syncSession);
            response.setStringProperty("x-amqp-0-10.app-id", "qmf2");
            response.setStringProperty("method", "indication");
            response.setStringProperty("qmf.opcode", "_data_indication");
            response.setStringProperty("qmf.content", "_event");
            response.setStringProperty("qmf.agent", _name);
            response.setStringProperty("qpid.subject", subject);
            List<Map> results = new ArrayList<Map>();
            results.add(event.mapEncode());
            AMQPMessage.setList(response, results);
            _broadcaster.send(response);
        }
        catch (JMSException jmse)
        {
            _log.info("JMSException {} caught in raiseEvent()", jmse.getMessage());
        }
    }

    /**
     * Passes a reference to an instance of a managed QMF object to the Agent.
     * <p>
     * The object's name must uniquely identify this object among all objects known to this Agent.
     * <p>
     * This method creates an ObjectId for the QmfAgentData being added, it does this by first checking
     * the schema.
     * <p>
     * If an associated schema exists we look for the set of property names that have been
     * specified as idNames. If idNames exists we look for their values within the object and use that
     * to create the objectName. If we can't create a sensible name we use a randomUUID.
     * @param object the QmfAgentData object to be added
     */
    public void addObject(final QmfAgentData object) throws QmfException
    {
        // There are some cases where a QmfAgentData Object might have already set its ObjectId, for example where
        // it may need to have a "well known" ObjectId. This is the case with the Java Broker Management Agent
        // where tools such as qpid-config might have made assumptions about its ObjectId rather than doing "discovery".
        ObjectId addr = object.getObjectId();
        if (addr == null)
        {
            SchemaClassId classId = object.getSchemaClassId();
            SchemaClass schema = _schemaCache.get(classId);

            // Try to create an objectName using the property names that have been specified as idNames in the schema
            StringBuilder buf = new StringBuilder();
            // Initialise idNames as an empty array as we want to check if a key has been used to construct the name.
            String[] idNames = {}; 
            if (schema != null && schema instanceof SchemaObjectClass)
            {
                idNames = ((SchemaObjectClass)schema).getIdNames();
                for (String property : idNames)
                {
                    buf.append(object.getStringValue(property));
                }
            }
            String objectName = buf.toString();

            // If the schema hasn't given any help we use a UUID. Note that we check the length of idNames too
            // as a given named key property might legitimately be an empty string (e.g. the default direct
            // exchange has name == "")
            if (objectName.length() == 0 && idNames.length == 0) objectName = UUID.randomUUID().toString();

            // Finish up the name by incorporating package and class names
            objectName = classId.getPackageName() + ":" + classId.getClassName() + ":" + objectName;

            // Now we've got a good name for the object we create its ObjectId and add that to the object
            addr = new ObjectId(_name, objectName, _epoch);

            object.setObjectId(addr);
        }

        QmfAgentData foundObject = _objectIndex.get(addr);
        if (foundObject != null)
        {
            // If a duplicate object has actually been Deleted we can reuse the address.
            if (!foundObject.isDeleted())
            {
                throw new QmfException("Duplicate QmfAgentData Address");
            }
        }

        _objectIndex.put(addr, object);

        // Does the new object match any Subscriptions? If so add a reference to the matching Subscription and publish.
        for (Subscription subscription : _subscriptions.values())
        {
            QmfQuery query = subscription.getQuery();
            if (query.getObjectId() != null)
            {
                if (query.getObjectId().equals(addr))
                {
                    object.addSubscription(subscription.getSubscriptionId(), subscription);
                    object.publish();
                }
            }
            else if (query.evaluate(object))
            {
                object.addSubscription(subscription.getSubscriptionId(), subscription);
                object.publish();
            }
        }
    } // end of addObject()

    /**
     * Returns the count of pending WorkItems that can be retrieved.
     * @return the count of pending WorkItems that can be retrieved.
     */
    public final int getWorkitemCount()
    {
        return _workQueue.size();
    }

    /**
     * Obtains the next pending work item - blocking version.
     * <p>
     * The blocking getNextWorkitem() can be used without the need for a Notifier as it will block until
     * a new item gets added to the work queue e.g. the following usage pattern.
     * <pre>
     * while ((wi = agent.getNextWorkitem()) != null)
     * {
     *     System.out.println("WorkItem type: " + wi.getType());
     * }
     * </pre>
     * @return the next pending work item, or null if none available.
     */
    public final WorkItem getNextWorkitem()
    {
        return _workQueue.getNextWorkitem();
    }

    /**
     * Obtains the next pending work item - balking version.
     * <p>
     * The balking getNextWorkitem() is generally used with a Notifier which can be used as a gate to determine
     * if any work items are available e.g. the following usage pattern.
     * <pre>
     * while (true)
     * {
     *     notifier.waitForWorkItem(); // Assuming a BlockingNotifier has been used here
     *     System.out.println("WorkItem available, count = " + agent.getWorkitemCount());
     *
     *     WorkItem wi;
     *     while ((wi = agent.getNextWorkitem(0)) != null)
     *     {
     *         System.out.println("WorkItem type: " + wi.getType());
     *     }
     * }
     * </pre>
     * Note that it is possible for the getNextWorkitem() loop to retrieve multiple items from the _workQueue
     * and for the Agent to add new items as the loop is looping thus when it finally exits and goes
     * back to the outer loop notifier.waitForWorkItems() may return immediately as it had been notified
     * whilst we were in the getNextWorkitem() loop. This will be evident by a getWorkitemCount() of 0
     * after returning from waitForWorkItem().
     * <p>
     * This is the expected behaviour, but illustrates the need to check for nullness of the value returned
     * by getNextWorkitem() - or alternatively to use getWorkitemCount() to put getNextWorkitem() in a
     * bounded loop.
     *
     * @param timeout the timeout in seconds. If timeout = 0 it returns immediately with either a WorkItem or null
     * @return the next pending work item, or null if none available.
     */
    public final WorkItem getNextWorkitem(final long timeout)
    {
        return _workQueue.getNextWorkitem(timeout);
    }

    /**
     * Releases a WorkItem instance obtained by getNextWorkItem(). Called when the application has finished
     * processing the WorkItem.
     */
    public final void releaseWorkitem()
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
     * Indicate to the Agent that the application has completed processing a method request.
     * <p>
     * See the description of the METHOD_CALL WorkItem.
     * @param methodName the method's name.
     * @param handle the reply handle from WorkItem.
     * @param outArgs the output argument map.
     * @param error the error object that was created if the method failed in any way, otherwise null.
     */
    public final void methodResponse(final String methodName, final Handle handle,
                                     final QmfData outArgs, final QmfData error)
    {
        try
        {
            MapMessage response = _syncSession.createMapMessage();
            response.setJMSCorrelationID(handle.getCorrelationId());
            response.setStringProperty("x-amqp-0-10.app-id", "qmf2");
            response.setStringProperty("method", "response");
            response.setStringProperty("qmf.opcode", "_method_response");
            response.setStringProperty("qmf.agent", _name);
            response.setStringProperty("qpid.subject", handle.getRoutingKey());

            if (error == null)
            {
                if (outArgs != null)
                {
                    response.setObject("_arguments", outArgs.mapEncode());
                    if (outArgs.getSubtypes() != null)
                    {
                        response.setObject("_subtypes", outArgs.getSubtypes());
                    }
                }
            }
            else
            {
                Map<String, Object> errorMap = error.mapEncode();
                for (Map.Entry<String, Object> entry : errorMap.entrySet())
                {
                    response.setObject(entry.getKey(), entry.getValue());
                }
            }
            sendResponse(handle, response);
        }
        catch (JMSException jmse)
        {
            _log.info("JMSException {} caught in methodResponse()", jmse.getMessage());
        }
    }

    /**
     * Send the query response back to the Console.
     * @param handle the reply handle that contains the replyTo Address.
     * @param results the list of mapEncoded query results.
     * @param qmfContentType the value to be passed to the qmf.content Header.
     */
    protected final void queryResponse(final Handle handle, List<Map> results, final String qmfContentType)
    {
        try
        {
            Message response = AMQPMessage.createListMessage(_syncSession);
            response.setJMSCorrelationID(handle.getCorrelationId());
            response.setStringProperty("x-amqp-0-10.app-id", "qmf2");
            response.setStringProperty("method", "response");
            response.setStringProperty("qmf.opcode", "_query_response");
            response.setStringProperty("qmf.agent", _name);
            response.setStringProperty("qmf.content", qmfContentType);
            response.setStringProperty("qpid.subject", handle.getRoutingKey());
            AMQPMessage.setList(response, results);
            sendResponse(handle, response);
        }
        catch (JMSException jmse)
        {
            _log.info("JMSException {} caught in queryResponse()", jmse.getMessage());
        }
    }

    /**
     * If the subscription request is successful, the Agent application must provide a unique subscriptionId.
     * <p>
     * If replying to a sucessful subscription refresh, the original subscriptionId must be supplied.
     * <p>
     * If the subscription or refresh fails, the subscriptionId should be set to null and error may be set to
     * an application-specific QmfData instance that describes the error.
     * <p>
     * Should a refresh request fail, the consoleHandle may be set to null if unknown.
     *
     * @param handle the handle from the WorkItem.
     * @param consoleHandle the console reply handle.
     * @param subscriptionId a unique handle for the subscription supplied by the Agent.
     * @param lifetime should be set to the duration of the subscription in seconds.
     * @param publishInterval should be set to the time interval in seconds between successive publications
     *          on this subscription.
     * @param error an application-specific QmfData instance that describes the error.
     */
    public final void subscriptionResponse(final Handle handle, final Handle consoleHandle, final String subscriptionId, 
                                           final long lifetime, final long publishInterval, final QmfData error)
    {
        try
        {
            MapMessage response = _syncSession.createMapMessage();
            response.setJMSCorrelationID(handle.getCorrelationId());
            response.setStringProperty("x-amqp-0-10.app-id", "qmf2");
            response.setStringProperty("method", "response");
            response.setStringProperty("qmf.opcode", "_subscribe_response");
            response.setStringProperty("qmf.agent", _name);
            response.setStringProperty("qpid.subject", handle.getRoutingKey());
    
            if (error == null)
            {
                response.setObject("_subscription_id", subscriptionId);
                response.setObject("_duration", lifetime);
                response.setObject("_interval", publishInterval);
            }
            else
            {
                Map<String, Object> errorMap = error.mapEncode();
                for (Map.Entry<String, Object> entry : errorMap.entrySet())
                {
                    response.setObject(entry.getKey(), entry.getValue());
                }
            }
            sendResponse(handle, response);
        }
        catch (JMSException jmse)
        {
            _log.info("JMSException {} caught in subscriptionResponse()", jmse.getMessage());
        }
    }
}
