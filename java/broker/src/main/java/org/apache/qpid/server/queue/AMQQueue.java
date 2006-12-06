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
package org.apache.qpid.server.queue;

import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.management.AMQManagedObject;
import org.apache.qpid.server.management.MBeanConstructor;
import org.apache.qpid.server.management.MBeanDescription;
import org.apache.qpid.server.management.Managable;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.txn.TxnBuffer;
import org.apache.qpid.server.txn.TxnOp;

import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.monitor.MonitorNotification;
import javax.management.openmbean.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * This is an AMQ Queue, and should not be confused with a JMS queue or any other abstraction like
 * that. It is described fully in RFC 006.
 */
public class AMQQueue implements Managable, Comparable
{
    private static final Logger _logger = Logger.getLogger(AMQQueue.class);

    private final String _name;

    /**
     * null means shared
     */
    private final String _owner;

    private final boolean _durable;

    /**
     * If true, this queue is deleted when the last subscriber is removed
     */
    private final boolean _autoDelete;

    /**
     * Holds subscribers to the queue.
     */
    private final SubscriptionSet _subscribers;

    private final SubscriptionFactory _subscriptionFactory;

    /**
     * Manages message delivery.
     */
    private final DeliveryManager _deliveryMgr;

    /**
     * The queue registry with which this queue is registered.
     */
    private final QueueRegistry _queueRegistry;

    /**
     * Used to track bindings to exchanges so that on deletion they can easily
     * be cancelled.
     */
    private final ExchangeBindings _bindings = new ExchangeBindings(this);

    /**
     * Executor on which asynchronous delivery will be carriedout where required
     */
    private final Executor _asyncDelivery;

    private final AMQQueueMBean _managedObject;

    /**
     * max allowed size(KB) of a single message
     */
    private long _maxMessageSize = 10000;

    /**
     * max allowed number of messages on a queue.
     */
    private Integer _maxMessageCount = 10000;

    /**
     * max queue depth(KB) for the queue
     */
    private long _maxQueueDepth = 10000000;

    /**
     * total messages received by the queue since startup.
     */
    private long _totalMessagesReceived = 0;

    public int compareTo(Object o)
    {
        return _name.compareTo(((AMQQueue) o).getName());
    }

    /**
     * MBean class for AMQQueue. It implements all the management features exposed
     * for an AMQQueue.
     */
    @MBeanDescription("Management Interface for AMQQueue")
    private final class AMQQueueMBean extends AMQManagedObject implements ManagedQueue
    {
        private String _queueName = null;
        // OpenMBean data types for viewMessages method
        private String[] _msgAttributeNames = {"Message Id", "Header", "Size(bytes)", "Redelivered"};
        private String[] _msgAttributeIndex = {_msgAttributeNames[0]};
        private OpenType[] _msgAttributeTypes = new OpenType[4]; // AMQ message attribute types.
        private CompositeType _messageDataType = null;           // Composite type for representing AMQ Message data.
        private TabularType _messagelistDataType = null;         // Datatype for representing AMQ messages list.

        // OpenMBean data types for viewMessageContent method
        private CompositeType _msgContentType = null;
        private String[]      _msgContentAttributes = {"Message Id", "MimeType", "Encoding", "Content"};
        private OpenType[]    _msgContentAttributeTypes = new OpenType[4];

        @MBeanConstructor("Creates an MBean exposing an AMQQueue")
        public AMQQueueMBean() throws JMException
        {
            super(ManagedQueue.class, ManagedQueue.TYPE);
            init();
        }

        /**
         * initialises the openmbean data types
         */
        private void init() throws OpenDataException
        {
            _queueName = jmxEncode(new StringBuffer(_name), 0).toString();
            _msgContentAttributeTypes[0] = SimpleType.LONG;                    // For message id
            _msgContentAttributeTypes[1] = SimpleType.STRING;                  // For MimeType
            _msgContentAttributeTypes[2] = SimpleType.STRING;                  // For Encoding
            _msgContentAttributeTypes[3] = new ArrayType(1, SimpleType.BYTE);  // For message content
            _msgContentType = new CompositeType("Message Content", "AMQ Message Content", _msgContentAttributes,
                                                 _msgContentAttributes, _msgContentAttributeTypes);

            _msgAttributeTypes[0] = SimpleType.LONG;                      // For message id
            _msgAttributeTypes[1] = new ArrayType(1, SimpleType.STRING);  // For header attributes
            _msgAttributeTypes[2] = SimpleType.LONG;                      // For size
            _msgAttributeTypes[3] = SimpleType.BOOLEAN;                   // For redelivered

            _messageDataType = new CompositeType("Message","AMQ Message", _msgAttributeNames, _msgAttributeNames, _msgAttributeTypes);
            _messagelistDataType = new TabularType("Messages", "List of messages", _messageDataType, _msgAttributeIndex);
        }

        public String getObjectInstanceName()
        {
            return _queueName;
        }

        public String getName()
        {
            return _name;
        }

        public boolean isDurable()
        {
            return _durable;
        }

        public String getOwner()
        {
            return _owner;
        }

        public boolean isAutoDelete()
        {
            return _autoDelete;
        }

        public Integer getMessageCount()
        {
            return _deliveryMgr.getQueueMessageCount();
        }

        public Long getMaximumMessageSize()
        {
            return _maxMessageSize;
        }

        public void setMaximumMessageSize(Long value)
        {
            _maxMessageSize = value;
        }

        public Integer getConsumerCount()
        {
            return _subscribers.size();
        }

        public Integer getActiveConsumerCount()
        {
            return _subscribers.getWeight();
        }

        public Long getReceivedMessageCount()
        {
            return _totalMessagesReceived;
        }

        public Integer getMaximumMessageCount()
        {
            return _maxMessageCount;
        }

        public void setMaximumMessageCount(Integer value)
        {
            _maxMessageCount = value;
        }

        public Long getMaximumQueueDepth()
        {
            return _maxQueueDepth;
        }

        // Sets the queue depth, the max queue size
        public void setMaximumQueueDepth(Long value)
        {
            _maxQueueDepth = value;
        }

        /**
         * returns the size of messages(KB) in the queue.
         */
        public Long getQueueDepth()
        {
            List<AMQMessage> list = _deliveryMgr.getMessages();
            if (list.size() == 0)
            {
                return 0l;
            }

            long queueDepth = 0;
            for (AMQMessage message : list)
            {
                queueDepth = queueDepth + getMessageSize(message);
            }
            return (long)Math.round(queueDepth / 1000);
        }

        /**
         * returns size of message in bytes
         */
        private long getMessageSize(AMQMessage msg)
        {
            if (msg == null)
            {
                return 0l;
            }

            return msg.getContentHeaderBody().bodySize;
        }

        /**
         * Checks if there is any notification to be send to the listeners
         */
        private void checkForNotification(AMQMessage msg)
        {
            // Check for threshold message count
            Integer msgCount = getMessageCount();
            if (msgCount >= getMaximumMessageCount())
            {
                notifyClients("Message count(" + msgCount + ") has reached or exceeded the threshold high value");
            }

            // Check for threshold message size
            long messageSize = getMessageSize(msg);
            if (messageSize >= _maxMessageSize)
            {
                notifyClients("Message size(ID=" + msg.getMessageId() + ", size=" + messageSize + " bytes) is higher than the threshold value");
            }

            // Check for threshold queue depth in bytes
            long queueDepth = getQueueDepth();
            if (queueDepth >= _maxQueueDepth)
            {
                notifyClients("Queue depth(" + queueDepth + "), Queue size has reached the threshold high value");
            }
        }

        /**
         * Sends the notification to the listeners
         */
        private void notifyClients(String notificationMsg)
        {
            Notification n = new Notification(MonitorNotification.THRESHOLD_VALUE_EXCEEDED, this,
                                 ++_notificationSequenceNumber, System.currentTimeMillis(), notificationMsg);

            _broadcaster.sendNotification(n);
        }

        public void deleteMessageFromTop() throws JMException
        {
            try
            {
                _deliveryMgr.removeAMessageFromTop();
            }
            catch (AMQException ex)
            {
                throw new MBeanException(ex, ex.toString());
            }
        }

        public void clearQueue() throws JMException
        {
            try
            {
                _deliveryMgr.clearAllMessages();
            }
            catch (AMQException ex)
            {
                throw new MBeanException(ex, ex.toString());
            }
        }

        /**
         * returns message content as byte array and related attributes for the given message id.
         */
        public CompositeData viewMessageContent(long msgId) throws JMException
        {
            List<AMQMessage> list = _deliveryMgr.getMessages();
            AMQMessage msg = null;
            for (AMQMessage message : list)
            {
                if (message.getMessageId() == msgId)
                {
                    msg = message;
                    break;
                }
            }

            if (msg == null)
            {
                throw new JMException("AMQMessage with message id = " + msgId + " is not in the " + _queueName );
            }
            // get message content
            List<ContentBody> cBodies = msg.getContentBodies();
            List<Byte> msgContent = new ArrayList<Byte>();
            for (ContentBody body : cBodies)
            {
                if (body.getSize() != 0)
                {
                    ByteBuffer slice = body.payload.slice();
                    for (int j = 0; j < slice.limit(); j++)
                    {
                        msgContent.add(slice.get());
                    }
                }
            }

            // Create header attributes list
            BasicContentHeaderProperties headerProperties = (BasicContentHeaderProperties)msg.getContentHeaderBody().properties;
            String mimeType = headerProperties.getContentType();
            String encoding = headerProperties.getEncoding() == null ? "" : headerProperties.getEncoding();
            Object[] itemValues = {msgId, mimeType, encoding, msgContent.toArray(new Byte[0])};

            return new CompositeDataSupport(_msgContentType, _msgContentAttributes, itemValues);
        }

        /**
         * Returns the header contents of the messages stored in this queue in tabular form.
         */
        public TabularData viewMessages(int beginIndex, int endIndex) throws JMException
        {
            if ((beginIndex > endIndex) || (beginIndex < 1))
            {
                throw new JMException("From Index = " + beginIndex + ", To Index = " + endIndex +
                                      "\nFrom Index should be greater than 0 and less than To Index");
            }

            List<AMQMessage> list = _deliveryMgr.getMessages();
            TabularDataSupport _messageList = new TabularDataSupport(_messagelistDataType);

            // Create the tabular list of message header contents
            for (int i = beginIndex; i <= endIndex && i <=  list.size(); i++)
            {
                AMQMessage msg = list.get(i - 1);
                ContentHeaderBody headerBody =  msg.getContentHeaderBody();
                // Create header attributes list
                BasicContentHeaderProperties headerProperties = (BasicContentHeaderProperties)headerBody.properties;
                List<String> headerAttribsList = new ArrayList<String>();
                headerAttribsList.add("App Id=" + headerProperties.getAppId());
                headerAttribsList.add("MimeType=" + headerProperties.getContentType());
                headerAttribsList.add("Correlation Id=" + headerProperties.getCorrelationId());
                headerAttribsList.add("Encoding=" + headerProperties.getEncoding());
                headerAttribsList.add(headerProperties.toString());

                Object[] itemValues = {msg.getMessageId(), headerAttribsList.toArray(new String[0]),
                                       headerBody.bodySize, msg.isRedelivered()};
                CompositeData messageData = new CompositeDataSupport(_messageDataType, _msgAttributeNames, itemValues);
                _messageList.put(messageData);
            }

            return _messageList;
        }

        /**
         * returns Notifications sent by this MBean.
         */
        @Override
        public MBeanNotificationInfo[] getNotificationInfo()
        {
            String[] notificationTypes = new String[] {MonitorNotification.THRESHOLD_VALUE_EXCEEDED};
            String name = MonitorNotification.class.getName();
            String description = "Either Message count or Queue depth or Message size has reached threshold high value";
            MBeanNotificationInfo info1 = new MBeanNotificationInfo(notificationTypes, name, description);

            return new MBeanNotificationInfo[]{info1};
        }

    } // End of AMQMBean class

    public AMQQueue(String name, boolean durable, String owner,
                    boolean autoDelete, QueueRegistry queueRegistry)
            throws AMQException
    {
        this(name, durable, owner, autoDelete, queueRegistry,
             AsyncDeliveryConfig.getAsyncDeliveryExecutor(), new SubscriptionImpl.Factory());
    }

    public AMQQueue(String name, boolean durable, String owner,
                    boolean autoDelete, QueueRegistry queueRegistry, SubscriptionFactory subscriptionFactory)
            throws AMQException
    {
        this(name, durable, owner, autoDelete, queueRegistry,
             AsyncDeliveryConfig.getAsyncDeliveryExecutor(), subscriptionFactory);
    }

    public AMQQueue(String name, boolean durable, String owner,
                    boolean autoDelete, QueueRegistry queueRegistry, Executor asyncDelivery,
                    SubscriptionFactory subscriptionFactory)
            throws AMQException
    {

        this(name, durable, owner, autoDelete, queueRegistry, asyncDelivery, new SubscriptionSet(), subscriptionFactory);
    }

    public AMQQueue(String name, boolean durable, String owner,
                    boolean autoDelete, QueueRegistry queueRegistry, Executor asyncDelivery)
            throws AMQException
    {

        this(name, durable, owner, autoDelete, queueRegistry, asyncDelivery, new SubscriptionSet(),
             new SubscriptionImpl.Factory());
    }

    protected AMQQueue(String name, boolean durable, String owner,
                       boolean autoDelete, QueueRegistry queueRegistry,
                       SubscriptionSet subscribers, SubscriptionFactory subscriptionFactory)
            throws AMQException
    {
        this(name, durable, owner, autoDelete, queueRegistry,
             AsyncDeliveryConfig.getAsyncDeliveryExecutor(), subscribers, subscriptionFactory);
    }

    protected AMQQueue(String name, boolean durable, String owner,
                       boolean autoDelete, QueueRegistry queueRegistry,
                       SubscriptionSet subscribers)
            throws AMQException
    {
        this(name, durable, owner, autoDelete, queueRegistry,
             AsyncDeliveryConfig.getAsyncDeliveryExecutor(), subscribers, new SubscriptionImpl.Factory());
    }

    protected AMQQueue(String name, boolean durable, String owner,
                       boolean autoDelete, QueueRegistry queueRegistry,
                       Executor asyncDelivery, SubscriptionSet subscribers, SubscriptionFactory subscriptionFactory)
            throws AMQException
    {
        if (name == null)
        {
            throw new IllegalArgumentException("Queue name must not be null");
        }
        if (queueRegistry == null)
        {
            throw new IllegalArgumentException("Queue registry must not be null");
        }
        _name = name;
        _durable = durable;
        _owner = owner;
        _autoDelete = autoDelete;
        _queueRegistry = queueRegistry;
        _asyncDelivery = asyncDelivery;
        _managedObject = createMBean();
        _managedObject.register();
        _subscribers = subscribers;
        _subscriptionFactory = subscriptionFactory;

        //fixme - Pick one.
        if (Boolean.getBoolean("concurrentdeliverymanager"))
        {
            _logger.info("Using ConcurrentDeliveryManager");
            _deliveryMgr = new ConcurrentDeliveryManager(_subscribers, this);
        }
        else
        {
            _logger.info("Using SynchronizedDeliveryManager");
            _deliveryMgr = new SynchronizedDeliveryManager(_subscribers, this);
        }
    }

    private AMQQueueMBean createMBean() throws AMQException
    {
        try
        {
            return new AMQQueueMBean();
        }
        catch (JMException ex)
        {
            throw new AMQException("AMQQueue MBean creation has failed ", ex);
        }
    }

    public String getName()
    {
        return _name;
    }

    public boolean isShared()
    {
        return _owner == null;
    }

    public boolean isDurable()
    {
        return _durable;
    }

    public String getOwner()
    {
        return _owner;
    }

    public boolean isAutoDelete()
    {
        return _autoDelete;
    }

    public int getMessageCount()
    {
        return _deliveryMgr.getQueueMessageCount();
    }

    public ManagedObject getManagedObject()
    {
        return _managedObject;
    }

    public void bind(String routingKey, Exchange exchange)
    {
        _bindings.addBinding(routingKey, exchange);
    }

    public void registerProtocolSession(AMQProtocolSession ps, int channel, String consumerTag, boolean acks)
            throws AMQException
    {
        debug("Registering protocol session {0} with channel {1} and consumer tag {2} with {3}", ps, channel, consumerTag, this);

        Subscription subscription = _subscriptionFactory.createSubscription(channel, ps, consumerTag, acks);
        _subscribers.addSubscriber(subscription);
    }

    public void unregisterProtocolSession(AMQProtocolSession ps, int channel, String consumerTag) throws AMQException
    {
        debug("Unregistering protocol session {0} with channel {1} and consumer tag {2} from {3}", ps, channel, consumerTag,
              this);

        Subscription removedSubscription;
        if ((removedSubscription = _subscribers.removeSubscriber(_subscriptionFactory.createSubscription(channel,
                                                                                                         ps,
                                                                                                         consumerTag)))
            == null)
        {
            throw new AMQException("Protocol session with channel " + channel + " and consumer tag " + consumerTag +
                                   " and protocol session key " + ps.getKey() + " not registered with queue " + this);
        }

        // if we are eligible for auto deletion, unregister from the queue registry
        if (_autoDelete && _subscribers.isEmpty())
        {
            autodelete();
            // we need to manually fire the event to the removed subscription (which was the last one left for this
            // queue. This is because the delete method uses the subscription set which has just been cleared
            removedSubscription.queueDeleted(this);
        }
    }

    public int delete(boolean checkUnused, boolean checkEmpty) throws AMQException
    {
        if (checkUnused && !_subscribers.isEmpty())
        {
            _logger.info("Will not delete " + this + " as it is in use.");
            return 0;
        }
        else if (checkEmpty && _deliveryMgr.hasQueuedMessages())
        {
            _logger.info("Will not delete " + this + " as it is not empty.");
            return 0;
        }
        else
        {
            delete();
            return _deliveryMgr.getQueueMessageCount();
        }
    }

    public void delete() throws AMQException
    {
        _subscribers.queueDeleted(this);
        _bindings.deregister();
        _queueRegistry.unregisterQueue(_name);
        _managedObject.unregister();
    }

    protected void autodelete() throws AMQException
    {
        debug("autodeleting {0}", this);
        delete();
    }

    public void deliver(AMQMessage msg) throws AMQException
    {
        TxnBuffer buffer = msg.getTxnBuffer();
        if (buffer == null)
        {
            //non-transactional
            record(msg);
            process(msg);
        }
        else
        {
            buffer.enlist(new Deliver(msg));
        }
    }

    private void record(AMQMessage msg) throws AMQException
    {
        msg.enqueue(this);
        msg.incrementReference();
    }

    private void process(AMQMessage msg) throws FailedDequeueException
    {
        _deliveryMgr.deliver(getName(), msg);
        try
        {
            msg.checkDeliveredToConsumer();
            updateReceivedMessageCount(msg);
        }
        catch (NoConsumersException e)
        {
            // as this message will be returned, it should be removed
            // from the queue:
            dequeue(msg);
        }
    }

    void dequeue(AMQMessage msg) throws FailedDequeueException
    {
        try
        {
            msg.dequeue(this);
            msg.decrementReference();
        }
        catch (MessageCleanupException e)
        {
            //Message was dequeued, but could notthen be deleted
            //though it is no longer referenced. This should be very
            //rare and can be detected and cleaned up on recovery or
            //done through some form of manual intervention.
            _logger.error(e, e);
        }
        catch (AMQException e)
        {
            throw new FailedDequeueException(_name, e);
        }
    }

    public void deliverAsync()
    {
        _deliveryMgr.processAsync(_asyncDelivery);
    }

    protected SubscriptionManager getSubscribers()
    {
        return _subscribers;
    }

    protected void updateReceivedMessageCount(AMQMessage msg)
    {
        _totalMessagesReceived++;
        _managedObject.checkForNotification(msg);
    }

    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final AMQQueue amqQueue = (AMQQueue) o;

        return (_name.equals(amqQueue._name));
    }

    public int hashCode()
    {
        return _name.hashCode();
    }

    public String toString()
    {
        return "Queue(" + _name + ")@" + System.identityHashCode(this);
    }

    private void debug(String msg, Object... args)
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug(MessageFormat.format(msg, args));
        }
    }

    private class Deliver implements TxnOp
    {
        private final AMQMessage _msg;

        Deliver(AMQMessage msg)
        {
            _msg = msg;
        }

        public void prepare() throws AMQException
        {
            //do the persistent part of the record()
            _msg.enqueue(AMQQueue.this);
        }

        public void undoPrepare()
        {
        }

        public void commit()
        {
            //do the memeory part of the record()
            _msg.incrementReference();
            //then process the message
            try
            {
                process(_msg);
            }
            catch (FailedDequeueException e)
            {
                //TODO: is there anything else we can do here? I think not...
                _logger.error("Error during commit of a queue delivery: " + e, e);
            }
        }

        public void rollback()
        {
        }
    }

}
