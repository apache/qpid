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
import org.apache.qpid.AMQException;
import org.apache.qpid.configuration.Configured;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.management.Managable;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.virtualhost.VirtualHost;

import javax.management.JMException;
import java.text.MessageFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This is an AMQ Queue, and should not be confused with a JMS queue or any other abstraction like
 * that. It is described fully in RFC 006.
 */
public class AMQQueue implements Managable, Comparable
{


    public static final class ExistingExclusiveSubscription extends AMQException
    {

        public ExistingExclusiveSubscription()
        {
            super("");
        }
    }

    public static final class ExistingSubscriptionPreventsExclusive extends AMQException
    {

        public ExistingSubscriptionPreventsExclusive()
        {
            super("");
        }
    }

    private static final ExistingExclusiveSubscription EXISTING_EXCLUSIVE = new ExistingExclusiveSubscription();
    private static final ExistingSubscriptionPreventsExclusive EXISTING_SUBSCRIPTION = new ExistingSubscriptionPreventsExclusive();



    private static final Logger _logger = Logger.getLogger(AMQQueue.class);

    private final AMQShortString _name;

    /**
     * null means shared
     */
    private final AMQShortString _owner;

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

    private final AtomicInteger _subscriberCount = new AtomicInteger();

    private final AtomicBoolean _isExclusive = new AtomicBoolean();

    private final AtomicBoolean _deleted = new AtomicBoolean(false);

    private List<Task> _deleteTaskList = new CopyOnWriteArrayList<Task>();

    /**
     * Manages message delivery.
     */
    private final DeliveryManager _deliveryMgr;

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

    private final VirtualHost _virtualHost;


    /**
     * max allowed size(KB) of a single message
     */
    private long _maximumMessageSize = 10000;

    /**
     * max allowed number of messages on a queue.
     */
    @Configured(path = "maximumMessageCount", defaultValue = "0")
    public int _maximumMessageCount;

    /**
     * max queue depth for the queue
     */
    @Configured(path = "maximumQueueDepth", defaultValue = "0")
    public long _maximumQueueDepth = 10000000;

/*
     * maximum message age before alerts occur
     */
    @Configured(path = "maximumMessageAge", defaultValue = "0")
    public long _maximumMessageAge = 30000; //0

    /*
     * the minimum interval between sending out consequetive alerts of the same type
     */
    @Configured(path = "minimumAlertRepeatGap", defaultValue = "0")
    public long _minimumAlertRepeatGap = 30000;

    /**
     * total messages received by the queue since startup.
     */
    public long _totalMessagesReceived = 0;

    public int compareTo(Object o)
    {
        return _name.compareTo(((AMQQueue) o).getName());
    }

    public AMQQueue(AMQShortString name, boolean durable, AMQShortString owner,
                    boolean autoDelete, VirtualHost virtualHost)
            throws AMQException
    {
        this(name, durable, owner, autoDelete, virtualHost,
             AsyncDeliveryConfig.getAsyncDeliveryExecutor(), new SubscriptionSet(), new SubscriptionImpl.Factory());
    }



    protected AMQQueue(AMQShortString name, boolean durable, AMQShortString owner,
                       boolean autoDelete, VirtualHost virtualHost,
                       SubscriptionSet subscribers)
            throws AMQException
    {
        this(name, durable, owner, autoDelete, virtualHost,
             AsyncDeliveryConfig.getAsyncDeliveryExecutor(), subscribers, new SubscriptionImpl.Factory());
    }

    protected AMQQueue(AMQShortString name, boolean durable, AMQShortString owner,
                       boolean autoDelete, VirtualHost virtualHost,
                       Executor asyncDelivery, SubscriptionSet subscribers, SubscriptionFactory subscriptionFactory)
            throws AMQException
    {
        if (name == null)
        {
            throw new IllegalArgumentException("Queue name must not be null");
        }
        if (virtualHost == null)
        {
            throw new IllegalArgumentException("Virtual Host must not be null");
        }
        _name = name;
        _durable = durable;
        _owner = owner;
        _autoDelete = autoDelete;
        _virtualHost = virtualHost;
        _asyncDelivery = asyncDelivery;

        _managedObject = createMBean();
        _managedObject.register();

        _subscribers = subscribers;
        _subscriptionFactory = subscriptionFactory;
		_deliveryMgr = new ConcurrentSelectorDeliveryManager(_subscribers, this);
    }

    private AMQQueueMBean createMBean() throws AMQException
    {
        try
        {
            return new AMQQueueMBean(this);
        }
        catch (JMException ex)
        {
            throw new AMQException("AMQQueue MBean creation has failed ", ex);
        }
    }

    public AMQShortString getName()
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

    public AMQShortString getOwner()
    {
        return _owner;
    }

    public boolean isAutoDelete()
    {
        return _autoDelete;
    }

    /**
     * @return no of messages(undelivered) on the queue.
     */
    public int getMessageCount()
    {
        return _deliveryMgr.getQueueMessageCount();
    }

    /**
     * @return List of messages(undelivered) on the queue.
     */
    public List<AMQMessage> getMessagesOnTheQueue()
    {
        return _deliveryMgr.getMessages();
    }

    public long getQueueDepth()
    {
        return _deliveryMgr.getTotalMessageSize();
    }


    /**
     * @param messageId
     * @return AMQMessage with give id if exists. null if AMQMessage with given id doesn't exist.
     */
    public AMQMessage getMessageOnTheQueue(long messageId)
    {
        List<AMQMessage> list = getMessagesOnTheQueue();
        AMQMessage msg = null;
        for (AMQMessage message : list)
        {
            if (message.getMessageId() == messageId)
            {
                msg = message;
                break;
            }
        }

        return msg;
    }

    /**
     * @see ManagedQueue#moveMessages
     * @param fromMessageId
     * @param toMessageId
     * @param queueName
     * @param storeContext
     * @throws AMQException
     */
    public synchronized void moveMessagesToAnotherQueue(long fromMessageId, long toMessageId, String queueName,
                                                        StoreContext storeContext) throws AMQException
    {
        AMQQueue anotherQueue = getVirtualHost().getQueueRegistry().getQueue(new AMQShortString(queueName));
        List<AMQMessage> list = getMessagesOnTheQueue();
        List<AMQMessage> foundMessagesList = new ArrayList<AMQMessage>();
        int maxMessageCountToBeMoved = (int)(toMessageId - fromMessageId + 1);
        for (AMQMessage message : list)
        {
            long msgId = message.getMessageId();
            if (msgId >= fromMessageId && msgId <= toMessageId)
            {
                foundMessagesList.add(message);
            }
            // break the loop as soon as messages to be removed are found
            if (foundMessagesList.size() == maxMessageCountToBeMoved)
            {
                break;
            }
        }

        // move messages to another queue
        for (AMQMessage message : foundMessagesList)
        {
            try
            {
                anotherQueue.process(storeContext, message);
            }
            catch(AMQException ex)
            {
                foundMessagesList.subList(foundMessagesList.indexOf(message), foundMessagesList.size()).clear();
                // Exception occured, so rollback the changes
                anotherQueue.removeMessages(foundMessagesList);
                throw ex;
            }
        }

        // moving is successful, now remove from original queue
        removeMessages(foundMessagesList);
    }

    public synchronized void removeMessages(List<AMQMessage> messageList)
    {
        _deliveryMgr.removeMessages(messageList);
    }

    /**
     * @return MBean object associated with this Queue
     */
    public ManagedObject getManagedObject()
    {
        return _managedObject;
    }

    public long getMaximumMessageSize()
    {
        return _maximumMessageSize;
    }

    public void setMaximumMessageSize(long value)
    {
        _maximumMessageSize = value;
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

    public int getMaximumMessageCount()
    {
        return _maximumMessageCount;
    }

    public void setMaximumMessageCount(int value)
    {
        _maximumMessageCount = value;
    }

    public long getMaximumQueueDepth()
    {
        return _maximumQueueDepth;
    }

    // Sets the queue depth, the max queue size
    public void setMaximumQueueDepth(long value)
    {
        _maximumQueueDepth = value;
    }

    public long getOldestMessageArrivalTime()
    {
        return _deliveryMgr.getOldestMessageArrival();
        
    }

    /**
     * Removes the AMQMessage from the top of the queue.
     */
    public void deleteMessageFromTop(StoreContext storeContext) throws AMQException
    {
        _deliveryMgr.removeAMessageFromTop(storeContext);
    }

    /**
     * removes all the messages from the queue.
     */
    public long clearQueue(StoreContext storeContext) throws AMQException
    {
        return _deliveryMgr.clearAllMessages(storeContext);
    }

    public void bind(AMQShortString routingKey, Exchange exchange)
    {
        _bindings.addBinding(routingKey, exchange);
    }


    public void registerProtocolSession(AMQProtocolSession ps, int channel, AMQShortString consumerTag, boolean acks,
                                        FieldTable filters, boolean noLocal, boolean exclusive)
            throws AMQException
    {
        if(incrementSubscriberCount() > 1)
        {
            if(isExclusive())
            {
                decrementSubscriberCount();
                throw EXISTING_EXCLUSIVE;
            }
            else if(exclusive)
            {
                decrementSubscriberCount();
                throw EXISTING_SUBSCRIPTION;
            }

        }
        else if(exclusive)
        {
            setExclusive(true);
        }

        debug("Registering protocol session {0} with channel {1} and consumer tag {2} with {3}", ps, channel, consumerTag, this);

        Subscription subscription = _subscriptionFactory.createSubscription(channel, ps, consumerTag, acks, filters, noLocal);

        if(subscription.hasFilters())
        {
            if (_deliveryMgr.hasQueuedMessages())
            {
                _deliveryMgr.populatePreDeliveryQueue(subscription);
            }
        }

        _subscribers.addSubscriber(subscription);
    }


    private boolean isExclusive()
    {
        return _isExclusive.get();
    }

    private void setExclusive(boolean exclusive)
    {
        _isExclusive.set(exclusive);
    }

    private int incrementSubscriberCount()
    {
        return _subscriberCount.incrementAndGet();
    }

    private int decrementSubscriberCount()
    {
        return _subscriberCount.decrementAndGet();
    }


    public void unregisterProtocolSession(AMQProtocolSession ps, int channel, AMQShortString consumerTag) throws AMQException
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

        setExclusive(false);
        decrementSubscriberCount();


        // if we are eligible for auto deletion, unregister from the queue registry
        if (_autoDelete && _subscribers.isEmpty())
        {
            autodelete();
            // we need to manually fire the event to the removed subscription (which was the last one left for this
            // queue. This is because the delete method uses the subscription set which has just been cleared
            removedSubscription.queueDeleted(this);
        }
    }

    public boolean isUnused()
    {
        return _subscribers.isEmpty();
    }

    public boolean isEmpty()
    {
        return !_deliveryMgr.hasQueuedMessages();
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
        if(!_deleted.getAndSet(true))
        {
            _subscribers.queueDeleted(this);
            _bindings.deregister();
            _virtualHost.getQueueRegistry().unregisterQueue(_name);
            _managedObject.unregister();
            for(Task task : _deleteTaskList)
            {
                task.doTask(this);
            }
            _deleteTaskList.clear();
        }
    }

    protected void autodelete() throws AMQException
    {
        debug("autodeleting {0}", this);
        delete();
    }

    public void processGet(StoreContext storeContext, AMQMessage msg) throws AMQException
    {
        _deliveryMgr.deliver(storeContext, getName(), msg);
        try
        {
            msg.checkDeliveredToConsumer();
            updateReceivedMessageCount(msg);
        }
        catch (NoConsumersException e)
        {
            // as this message will be returned, it should be removed
            // from the queue:
            dequeue(storeContext, msg);
        }
    }


    public void process(StoreContext storeContext, AMQMessage msg) throws AMQException
    {
        _deliveryMgr.deliver(storeContext, getName(), msg);
        try
        {
            msg.checkDeliveredToConsumer();
            updateReceivedMessageCount(msg);
        }
        catch (NoConsumersException e)
        {
            // as this message will be returned, it should be removed
            // from the queue:
            dequeue(storeContext, msg);
        }
    }

    void dequeue(StoreContext storeContext, AMQMessage msg) throws FailedDequeueException
    {
        try
        {
            msg.dequeue(storeContext, this);
            msg.decrementReference(storeContext);
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
            throw new FailedDequeueException(_name.toString(), e);
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

    protected void updateReceivedMessageCount(AMQMessage msg) throws AMQException
    {
        _totalMessagesReceived++;
        try
        {
            _managedObject.checkForNotification(msg);
        }
        catch (JMException e)
        {
            throw new AMQException("Unable to get notification from manage queue: " + e, e);
        }
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

    public boolean performGet(AMQProtocolSession session, AMQChannel channel, boolean acks) throws AMQException
    {
        return _deliveryMgr.performGet(session, channel, acks);
    }

    public QueueRegistry getQueueRegistry()
    {
        return _virtualHost.getQueueRegistry();
    }

    public VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    public static interface Task
    {
        public void doTask(AMQQueue queue) throws AMQException;        
    }

    public void addQueueDeleteTask(Task task)
    {
        _deleteTaskList.add(task);
    }

    public long getMinimumAlertRepeatGap()
    {
        return _minimumAlertRepeatGap;
    }

    public void setMinimumAlertRepeatGap(long minimumAlertRepeatGap)
    {
        _minimumAlertRepeatGap = minimumAlertRepeatGap;
    }

    public long getMaximumMessageAge()
    {
        return _maximumMessageAge;
    }

    public void setMaximumMessageAge(long maximumMessageAge)
    {
        _maximumMessageAge = maximumMessageAge;
    }

}
