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
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.management.Managable;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.management.AMQManagedObject;
import org.apache.qpid.server.protocol.AMQProtocolSession;

import javax.management.openmbean.*;
import javax.management.MBeanNotificationInfo;
import javax.management.AttributeChangeNotification;
import javax.management.Notification;
import javax.management.JMException;
import javax.management.MBeanException;
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
     * max allowed size of a single message.
     */
    private long _maxAllowedMessageSize = 0;

    /**
     * max allowed number of messages on a queue.
     */
    private long _maxAllowedMessageCount = 0;

    /**
     * max allowed size in bytes for all the messages combined together in a queue.
     */
    private long _queueDepth = 0;

    /**
     * total messages received by the queue since startup.
     */
    private long _totalMessagesReceived = 0;

    /**
     * MBean interface for the implementation AMQQueueMBean.
     * This is required for making the implementation a compliant MBean.
     */
    public interface AMQQueueMBeanMBean extends ManagedQueue
    {

    }
    /**
     * MBean class for AMQQueue. It implements all the management features exposed
     * for an AMQQueue.
     */
    private final class AMQQueueMBean extends AMQManagedObject implements AMQQueueMBeanMBean
    {
        // AMQ message attribute names exposed.
        private String[] _msgAttributeNames = { "MessageId",
                                                "Redelivered",
                                                "Content's size",
                                                "Contents" };
        // AMQ Message attribute descriptions.
        private String[] _msgAttributeDescriptions = { "Message Id",
                                              "Redelivered",
                                              "Message content's size in bytes",
                                              "Message content bodies" };
        // AMQ message attribute types.
        private OpenType[]    _msgAttributeTypes = new OpenType[4];
        // Messages will be indexed according to the messageId.
        private String[]      _msgAttributeIndex = { "MessageId"};
        // Composite type for representing AMQ Message data.
        private CompositeType _messageDataType = null;
        // Datatype for representing AMQ messages list.
        private TabularType   _messagelistDataType = null;

        private String[]      _contentNames = {"SerialNumber", "ContentBody"};
        private String[]      _contentDesc  = {"SerialNumber", "Message Content"};
        private String[]      _contentIndex = {"SerialNumber"};
        private OpenType[]    _contentType  = new OpenType[2];
        private CompositeType _contentBodyType = null;
        private TabularType   _contentBodyListType = null;

        public AMQQueueMBean()
        {
            super(ManagedQueue.class, ManagedQueue.TYPE);
            init();
        }

        private void init()
        {
            try
            {
                _contentType[0]  = SimpleType.INTEGER;
                _contentType[1]  = new ArrayType(1, SimpleType.BYTE);
                _contentBodyType = new CompositeType("Content",
                                                    "Message body content",
                                                    _contentNames,
                                                    _contentDesc,
                                                    _contentType);
                _contentBodyListType = new TabularType("MessageContents",
                                                      "MessageContent",
                                                      _contentBodyType,
                                                      _contentIndex);

                _msgAttributeTypes[0] = SimpleType.LONG;
                _msgAttributeTypes[1] = SimpleType.BOOLEAN;
                _msgAttributeTypes[2] = SimpleType.LONG;
                _msgAttributeTypes[3] = _contentBodyListType;

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
            return _name;
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

        public int getMessageCount()
        {
            return _deliveryMgr.getQueueMessageCount();
        }

        public long getMaximumMessageSize()
        {
            return _maxAllowedMessageSize;
        }

        public void setMaximumMessageSize(long value)
        {
            _maxAllowedMessageSize = value;
        }

        public int getConsumerCount()
        {
            return _subscribers.size();
        }

        public int getActiveConsumerCount()
        {
            return _subscribers.getWeight();
        }

        public long getReceivedMessageCount()
        {
            return _totalMessagesReceived;
        }

        public long getMaximumMessageCount()
        {
            return _maxAllowedMessageCount;
        }

        public void setMaximumMessageCount( long value)
        {
            _maxAllowedMessageCount = value;
        }

        public long getQueueDepth()
        {
            return _queueDepth;
        }

        public void setQueueDepth(long value)
        {
           _queueDepth = value;
        }

        // Operations

        private void checkForNotification()
        {
            if (getMessageCount() >= getMaximumMessageCount())
            {
                Notification n = new Notification(
                        "Warning",
                        this,
                        ++_notificationSequenceNumber,
                        System.currentTimeMillis(),
                        "Queue has reached its size limit and is now full.");

                _broadcaster.sendNotification(n);
            }
        }

        public void deleteMessageFromTop() throws JMException
        {
            try
            {
                _deliveryMgr.removeAMessageFromTop();
            }
            catch(AMQException ex)
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
         * Returns the messages stored in this queue in tabular form.
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

            if (beginIndex > list.size())
            {
                throw new JMException("FromIndex = " + beginIndex + ". There are only " + list.size() + " messages in the queue");
            }

            endIndex = endIndex < list.size() ? endIndex : list.size();
            TabularDataSupport _messageList = new TabularDataSupport(_messagelistDataType);

            for (int i = beginIndex; i <= endIndex; i++)
            {
                AMQMessage msg = list.get(i - 1);
                long msgId = msg.getMessageId();

                List<ContentBody> cBodies = msg.getContentBodies();

                TabularDataSupport _contentList = new TabularDataSupport(_contentBodyListType);
                int  contentSerialNo = 1;
                long size = 0;

                for (ContentBody body : cBodies)
                {
                    if (body.getSize() != 0)
                    {
                        Byte[] byteArray = getByteArray(body.payload.slice().array());
                        size = size + byteArray.length;

                        Object[] contentValues = {contentSerialNo, byteArray};
                        CompositeData contentData = new CompositeDataSupport(_contentBodyType,
                                                                             _contentNames,
                                                                             contentValues);

                        _contentList.put(contentData);
                    }
                }

                Object[] itemValues = {msgId, true, size, _contentList};
                CompositeData messageData = new CompositeDataSupport(_messageDataType,
                                                                     _msgAttributeNames,
                                                                     itemValues);
                _messageList.put(messageData);
            }

            return _messageList;
        }

        /**
         * A utility to convert byte[] to Byte[]. Required to create composite
         * type for message contents.
         * @param byteArray  message content as byte[]
         * @return  Byte[]
         */
        private Byte[] getByteArray(byte[] byteArray)
        {
            int size = byteArray.length;
            List<Byte> list = new ArrayList<Byte>();

            for (int i = 0; i < size; i++)
            {
                list.add(byteArray[i]);
            }

            return list.toArray(new Byte[0]);
        }

        /**
         * Creates all the notifications this MBean can send.
         * @return Notifications broadcasted by this MBean.
         */
        public MBeanNotificationInfo[] getNotificationInfo()
        {
            String[] notificationTypes = new String[]
                                {AttributeChangeNotification.ATTRIBUTE_CHANGE};
            String name = AttributeChangeNotification.class.getName();
            String description = "An attribute of this MBean has changed";
            MBeanNotificationInfo info1 = new MBeanNotificationInfo(notificationTypes,
                                                                    name,
                                                                    description);

            return new MBeanNotificationInfo[] {info1};
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
        _managedObject = new AMQQueueMBean();
        _managedObject.register();

        _subscribers = subscribers;
        _subscriptionFactory = subscriptionFactory;
        _deliveryMgr = new DeliveryManager(_subscribers, this);
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
        if(checkUnused && !_subscribers.isEmpty())
        {
            _logger.info("Will not delete " + this + " as it is in use.");
            return 0;
        }
        else if(checkEmpty && _deliveryMgr.getQueueMessageCount() > 0)
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
        msg.enqueue(this);
        _deliveryMgr.deliver(getName(), msg);
        updateReceivedMessageCount();
    }

    public void deliverAsync()
    {
        _deliveryMgr.processAsync(_asyncDelivery);
    }

    protected SubscriptionManager getSubscribers()
    {
        return _subscribers;
    }

    protected void updateReceivedMessageCount()
    {
        _totalMessagesReceived++;
        _managedObject.checkForNotification();
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
        if(_logger.isDebugEnabled())
        {
            _logger.debug(MessageFormat.format(msg, args));
        }
    }
}
