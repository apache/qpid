/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.queue;

import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentBody;
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
import javax.management.NotCompliantMBeanException;
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
public class AMQQueue implements Managable
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
     * max allowed size of a single message(in KBytes).
     */
    private long _maxAllowedMessageSize = 10000;  // 10 MB

    /**
     * max allowed number of messages on a queue.
     */
    private Integer _maxAllowedMessageCount = 10000;

    /**
     * max allowed size in  KBytes for all the messages combined together in a queue.
     */
    private long _queueDepth = 10000000;          //   10 GB

    /**
     * total messages received by the queue since startup.
     */
    private long _totalMessagesReceived = 0;

    /**
     * MBean class for AMQQueue. It implements all the management features exposed
     * for an AMQQueue.
     */
    @MBeanDescription("Management Interface for AMQQueue")
    private final class AMQQueueMBean extends AMQManagedObject implements ManagedQueue
    {
        private String _queueName = null;

        // AMQ message attribute names
        private String[] _msgAttributeNames = {"MessageId",
                                               "Header",
                                               "Size",
                                               "Redelivered"
                                              };
        // AMQ Message attribute descriptions.
        private String[] _msgAttributeDescriptions = {"Message Id",
                                                      "Header",
                                                      "Message size in bytes",
                                                      "Redelivered"
                                                     };

        private OpenType[] _msgAttributeTypes = new OpenType[4];  // AMQ message attribute types.
        private String[]   _msgAttributeIndex = {"MessageId"};    // Messages will be indexed according to the messageId.
        private CompositeType _messageDataType = null;            // Composite type for representing AMQ Message data.
        private TabularType   _messagelistDataType = null;        // Datatype for representing AMQ messages list.


        private CompositeType _msgContentType = null;    // For message content
        private String[]      _msgContentAttributes = {"MessageId",
                                                       "MimeType",
                                                       "Encoding",
                                                       "Content"
                                                      };
        private String[]    _msgContentDescriptions = {"Message Id",
                                                       "MimeType",
                                                       "Encoding",
                                                       "Message content"
                                                      };
        private OpenType[]  _msgContentAttributeTypes = new OpenType[4];


        @MBeanConstructor("Creates an MBean exposing an AMQQueue.")
        public AMQQueueMBean() throws NotCompliantMBeanException
        {
            super(ManagedQueue.class, ManagedQueue.TYPE);
            init();
        }

        private void init()
        {
            _queueName = jmxEncode(new StringBuffer(_name), 0).toString();
            try
            {
                _msgContentAttributeTypes[0] = SimpleType.LONG;                    // For message id
                _msgContentAttributeTypes[1] = SimpleType.STRING;                  // For MimeType
                _msgContentAttributeTypes[2] = SimpleType.STRING;                  // For Encoding
                _msgContentAttributeTypes[3] = new ArrayType(1, SimpleType.BYTE);  // For message content
                _msgContentType = new CompositeType("MessageContent",
                                                     "AMQ Message Content",
                                                     _msgContentAttributes,
                                                     _msgContentDescriptions,
                                                     _msgContentAttributeTypes);


                _msgAttributeTypes[0] = SimpleType.LONG;                      // For message id
                _msgAttributeTypes[1] = new ArrayType(1, SimpleType.STRING);  // For header attributes
                _msgAttributeTypes[2] = SimpleType.LONG;                      // For size
                _msgAttributeTypes[3] = SimpleType.BOOLEAN;                   // For redelivered

                _messageDataType = new CompositeType("Message",
                                                     "AMQ Message",
                                                     _msgAttributeNames,
                                                     _msgAttributeDescriptions,
                                                     _msgAttributeTypes);
                _messagelistDataType = new TabularType("Messages",
                                                       "List of messages",
                                                       _messageDataType,
                                                       _msgAttributeIndex);
            }
            catch (OpenDataException ex)
            {
                _logger.error("OpenDataTypes could not be created.", ex);
                throw new RuntimeException(ex);
            }
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
            return _maxAllowedMessageSize;
        }

        public void setMaximumMessageSize(Long value)
        {
            _maxAllowedMessageSize = value;
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
            return _maxAllowedMessageCount;
        }

        public void setMaximumMessageCount(Integer value)
        {
            _maxAllowedMessageCount = value;
        }

        public Long getQueueDepth()
        {
            return _queueDepth;
        }

        // Sets the queue depth, the max queue size
        public void setQueueDepth(Long value)
        {
            _queueDepth = value;
        }

        // Returns the size of messages in the queue
        public Long getQueueSize()
        {
            List<AMQMessage> list = _deliveryMgr.getMessages();
            if (list.size() == 0)
            {
                return 0l;
            }

            long queueSize = 0;
            for (AMQMessage message : list)
            {
                queueSize = queueSize + getMessageSize(message);
            }
            return new Long(Math.round(queueSize / 100));
        }

        // calculates the size of an AMQMessage
        private long getMessageSize(AMQMessage msg)
        {
            if (msg == null)
            {
                return 0l;
            }

            List<ContentBody> cBodies = msg.getContentBodies();
            long messageSize = 0;
            for (ContentBody body : cBodies)
            {
                if (body != null)
                {
                    messageSize = messageSize + body.getSize();
                }
            }
            return messageSize;
        }

        // Checks if there is any notification to be send to the listeners
        private void checkForNotification(AMQMessage msg)
        {
            // Check for message count
            Integer msgCount = getMessageCount();
            if (msgCount >= getMaximumMessageCount())
            {
                notifyClients("MessageCount = " + msgCount + ", Queue has reached its size limit and is now full.");
            }

            // Check for received message size
            long messageSize = getMessageSize(msg);
            if (messageSize >= getMaximumMessageSize())
            {
                notifyClients("MessageSize = " + messageSize + ", Message size (MessageID=" + msg.getMessageId() +
                              ")is higher than the threshold value");
            }

            // Check for queue size in bytes
            long queueSize = getQueueSize();
            if (queueSize >= getQueueDepth())
            {
                notifyClients("QueueSize = " + queueSize + ", Queue size has reached the threshold value");
            }
        }

        // Send the notification to the listeners
        private void notifyClients(String notificationMsg)
        {
            Notification n = new Notification(
                    MonitorNotification.THRESHOLD_VALUE_EXCEEDED,
                    this,
                    ++_notificationSequenceNumber,
                    System.currentTimeMillis(),
                    notificationMsg);

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

        public CompositeData viewMessageContent(long msgId) throws JMException
        {
            List<AMQMessage> list = _deliveryMgr.getMessages();
            CompositeData messageContent = null;
            AMQMessage msg = null;
            for (AMQMessage message : list)
            {
                if (message.getMessageId() == msgId)
                {
                    msg = message;
                    break;
                }
            }

            if (msg != null)
            {
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
                messageContent = new CompositeDataSupport(_msgContentType, _msgContentAttributes, itemValues);
            }
            else
            {
                throw new JMException("AMQMessage with message id = " + msgId + " is not in the " + _queueName );
            }

            return messageContent;
        }

        /**
         * Returns the messages stored in this queue in tabular form.
         *
         * @param beginIndex
         * @param endIndex
         * @return AMQ messages in tabular form.
         * @throws JMException
         */
        public TabularData viewMessages(int beginIndex, int endIndex) throws JMException
        {
            if ((beginIndex > endIndex) || (beginIndex < 1))
            {
                throw new JMException("FromIndex = " + beginIndex + ", ToIndex = " + endIndex +
                                      "\nFromIndex should be greater than 0 and less than ToIndex");
            }

            List<AMQMessage> list = _deliveryMgr.getMessages();
            TabularDataSupport _messageList = new TabularDataSupport(_messagelistDataType);

            if (beginIndex > list.size())
            {
                return _messageList;
            }
            endIndex = endIndex < list.size() ? endIndex : list.size();

            for (int i = beginIndex; i <= endIndex; i++)
            {
                AMQMessage msg = list.get(i - 1);
                long size = 0;
                // get message content
                List<ContentBody> cBodies = msg.getContentBodies();
                for (ContentBody body : cBodies)
                {
                    size = size + body.getSize();
                }

                // Create header attributes list
                BasicContentHeaderProperties headerProperties = (BasicContentHeaderProperties)msg.getContentHeaderBody().properties;
                List<String> headerAttribsList = new ArrayList<String>();
                headerAttribsList.add("App Id=" + headerProperties.getAppId());
                headerAttribsList.add("MimeType=" + headerProperties.getContentType());
                headerAttribsList.add("Correlation Id=" + headerProperties.getCorrelationId());
                headerAttribsList.add("Encoding=" + headerProperties.getEncoding());
                headerAttribsList.add(headerProperties.toString());

                Object[] itemValues = {msg.getMessageId(),
                                       headerAttribsList.toArray(new String[0]),
                                       size, msg.isRedelivered()};

                CompositeData messageData = new CompositeDataSupport(_messageDataType,
                                                                     _msgAttributeNames,
                                                                     itemValues);
                _messageList.put(messageData);
            }

            return _messageList;
        }

        /**
         * Creates all the notifications this MBean can send.
         *
         * @return Notifications broadcasted by this MBean.
         */
        @Override
        public MBeanNotificationInfo[] getNotificationInfo()
        {
            String[] notificationTypes = new String[]
                    {MonitorNotification.THRESHOLD_VALUE_EXCEEDED};
            String name = MonitorNotification.class.getName();
            String description = "An attribute of this MBean has reached threshold value";
            MBeanNotificationInfo info1 = new MBeanNotificationInfo(notificationTypes,
                                                                    name,
                                                                    description);

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
        _deliveryMgr = new DeliveryManager(_subscribers, this);
    }

    private AMQQueueMBean createMBean() throws AMQException
    {
        try
        {
            return new AMQQueueMBean();
        }
        catch (NotCompliantMBeanException ex)
        {
            throw new AMQException("AMQQueue MBean creation has failed.", ex);
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
        else if (checkEmpty && _deliveryMgr.getQueueMessageCount() > 0)
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
