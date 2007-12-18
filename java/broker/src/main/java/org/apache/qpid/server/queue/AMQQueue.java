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
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.management.Managable;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.virtualhost.VirtualHost;

import javax.management.JMException;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This is an AMQ Queue, and should not be confused with a JMS queue or any other abstraction like that. It is described
 * fully in RFC 006.
 */
public class AMQQueue implements Managable, Comparable
{

    /**
     * ExistingExclusiveSubscription signals a failure to create a subscription, because an exclusive subscription
     * already exists.
     *
     * <p/><table id="crc"><caption>CRC Card</caption>
     * <tr><th> Responsibilities <th> Collaborations
     * <tr><td> Represent failure to create a subscription, because an exclusive subscription already exists.
     * </table>
     *
     * @todo Not an AMQP exception as no status code.
     *
     * @todo Move to top level, used outside this class.
     */
    public static final class ExistingExclusiveSubscription extends AMQException
    {

        public ExistingExclusiveSubscription()
        {
            super("");
        }
    }

    /**
     * ExistingSubscriptionPreventsExclusive signals a failure to create an exclusize subscription, as a subscription
     * already exists.
     *
     * <p/><table id="crc"><caption>CRC Card</caption>
     * <tr><th> Responsibilities <th> Collaborations
     * <tr><td> Represent failure to create an exclusize subscription, as a subscription already exists.
     * </table>
     *
     * @todo Not an AMQP exception as no status code.
     *
     * @todo Move to top level, used outside this class.
     */
    public static final class ExistingSubscriptionPreventsExclusive extends AMQException
    {
        public ExistingSubscriptionPreventsExclusive()
        {
            super("");
        }
    }

    private static final Logger _logger = Logger.getLogger(AMQQueue.class);

    private final AMQShortString _name;

    /** null means shared */
    private final AMQShortString _owner;

    private final boolean _durable;

    /** If true, this queue is deleted when the last subscriber is removed */
    private final boolean _autoDelete;

    /** Holds subscribers to the queue. */
    private final SubscriptionSet _subscribers;

    private final SubscriptionFactory _subscriptionFactory;

    private final AtomicInteger _subscriberCount = new AtomicInteger();

    private final AtomicBoolean _isExclusive = new AtomicBoolean();

    private final AtomicBoolean _deleted = new AtomicBoolean(false);

    private List<Task> _deleteTaskList = new CopyOnWriteArrayList<Task>();

    /** Manages message delivery. */
    private final DeliveryManager _deliveryMgr;

    /** Used to track bindings to exchanges so that on deletion they can easily be cancelled. */
    private final ExchangeBindings _bindings = new ExchangeBindings(this);

    /** Executor on which asynchronous delivery will be carriedout where required */
    private final Executor _asyncDelivery;

    private final AMQQueueMBean _managedObject;

    private final VirtualHost _virtualHost;

    /** max allowed size(KB) of a single message */
    @Configured(path = "maximumMessageSize", defaultValue = "0")
    public long _maximumMessageSize;

    /** max allowed number of messages on a queue. */
    @Configured(path = "maximumMessageCount", defaultValue = "0")
    public long _maximumMessageCount;

    /** max queue depth for the queue */
    @Configured(path = "maximumQueueDepth", defaultValue = "0")
    public long _maximumQueueDepth;

    /** maximum message age before alerts occur */
    @Configured(path = "maximumMessageAge", defaultValue = "0")
    public long _maximumMessageAge;

    /** the minimum interval between sending out consequetive alerts of the same type */
    @Configured(path = "minimumAlertRepeatGap", defaultValue = "0")
    public long _minimumAlertRepeatGap;

    /** total messages received by the queue since startup. */
    public AtomicLong _totalMessagesReceived = new AtomicLong();

    public int compareTo(Object o)
    {
        return _name.compareTo(((AMQQueue) o).getName());
    }

    public AMQQueue(AMQShortString name, boolean durable, AMQShortString owner, boolean autoDelete, VirtualHost virtualHost)
            throws AMQException
    {
        this(name, durable, owner, autoDelete, virtualHost, AsyncDeliveryConfig.getAsyncDeliveryExecutor(),
             new SubscriptionSet(), new SubscriptionImpl.Factory());
    }

    protected AMQQueue(AMQShortString name, boolean durable, AMQShortString owner, boolean autoDelete,
                       VirtualHost virtualHost, SubscriptionSet subscribers) throws AMQException
    {
        this(name, durable, owner, autoDelete, virtualHost, AsyncDeliveryConfig.getAsyncDeliveryExecutor(), subscribers,
             new SubscriptionImpl.Factory());
    }

    protected AMQQueue(AMQShortString name, boolean durable, AMQShortString owner, boolean autoDelete,
                       VirtualHost virtualHost, Executor asyncDelivery, SubscriptionSet subscribers,
                       SubscriptionFactory subscriptionFactory) throws AMQException
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

    public boolean isDeleted()
    {
        return _deleted.get();
    }    

    /** @return no of messages(undelivered) on the queue. */
    public int getMessageCount()
    {
        return _deliveryMgr.getQueueMessageCount();
    }

    /** @return List of messages(undelivered) on the queue. */
    public List<QueueEntry> getMessagesOnTheQueue()
    {
        return _deliveryMgr.getMessages();
    }

    /**
     * Returns messages within the given range of message Ids.
     *
     * @param fromMessageId
     * @param toMessageId
     *
     * @return List of messages
     */
    public List<QueueEntry> getMessagesOnTheQueue(long fromMessageId, long toMessageId)
    {
        return _deliveryMgr.getMessages(fromMessageId, toMessageId);
    }

    public long getQueueDepth()
    {
        return _deliveryMgr.getTotalMessageSize();
    }

    /**
     * @param messageId
     *
     * @return QueueEntry with give id if exists. null if QueueEntry with given id doesn't exist.
     */
    public QueueEntry getMessageOnTheQueue(long messageId)
    {
        List<QueueEntry> list = getMessagesOnTheQueue(messageId, messageId);
        if ((list == null) || (list.size() == 0))
        {
            return null;
        }

        return list.get(0);
    }

    /**
     * Moves messages from this queue to another queue, and also commits the move on the message store. Delivery activity
     * on the queues being moved between is suspended during the move.
     *
     * @param fromMessageId The first message id to move.
     * @param toMessageId   The last message id to move.
     * @param queueName     The queue to move the messages to.
     * @param storeContext  The context of the message store under which to perform the move. This is associated with
     *                      the stores transactional context.
     */
    public synchronized void moveMessagesToAnotherQueue(long fromMessageId, long toMessageId, String queueName,
                                                        StoreContext storeContext)
    {
        AMQQueue toQueue = getVirtualHost().getQueueRegistry().getQueue(new AMQShortString(queueName));

        MessageStore fromStore = getVirtualHost().getMessageStore();
        MessageStore toStore = toQueue.getVirtualHost().getMessageStore();

        if (toStore != fromStore)
        {
            throw new RuntimeException("Can only move messages between queues on the same message store.");
        }

        try
        {
            // Obtain locks to prevent activity on the queues being moved between.
            startMovingMessages();
            toQueue.startMovingMessages();

            // Get the list of messages to move.
            List<QueueEntry> foundMessagesList = getMessagesOnTheQueue(fromMessageId, toMessageId);

            try
            {
                fromStore.beginTran(storeContext);

                // Move the messages in on the message store.
                for (QueueEntry entry : foundMessagesList)
                {
                    AMQMessage message = entry.getMessage();
                    fromStore.dequeueMessage(storeContext, _name, message.getMessageId());
                    toStore.enqueueMessage(storeContext, toQueue._name, message.getMessageId());
                }

                // Commit and flush the move transcations.
                try
                {
                    fromStore.commitTran(storeContext);
                }
                catch (AMQException e)
                {
                    throw new RuntimeException("Failed to commit transaction whilst moving messages on message store.", e);
                }

                // Move the messages on the in-memory queues.
                toQueue.enqueueMovedMessages(storeContext, foundMessagesList);
                _deliveryMgr.removeMovedMessages(foundMessagesList);
            }
            // Abort the move transactions on move failures.
            catch (AMQException e)
            {
                try
                {
                    fromStore.abortTran(storeContext);
                }
                catch (AMQException ae)
                {
                    throw new RuntimeException("Failed to abort transaction whilst moving messages on message store.", ae);
                }
            }
        }
        // Release locks to allow activity on the queues being moved between to continue.
        finally
        {
            toQueue.stopMovingMessages();
            stopMovingMessages();
        }
    }

    /**
     * Copies messages on this queue to another queue, and also commits the move on the message store. Delivery activity
     * on the queues being moved between is suspended during the move.
     *
     * @param fromMessageId The first message id to move.
     * @param toMessageId   The last message id to move.
     * @param queueName     The queue to move the messages to.
     * @param storeContext  The context of the message store under which to perform the move. This is associated with
     *                      the stores transactional context.
     */
    public synchronized void copyMessagesToAnotherQueue(long fromMessageId, long toMessageId, String queueName,
                                                        StoreContext storeContext)
    {
        AMQQueue toQueue = getVirtualHost().getQueueRegistry().getQueue(new AMQShortString(queueName));

        MessageStore fromStore = getVirtualHost().getMessageStore();
        MessageStore toStore = toQueue.getVirtualHost().getMessageStore();

        if (toStore != fromStore)
        {
            throw new RuntimeException("Can only move messages between queues on the same message store.");
        }

        try
        {
            // Obtain locks to prevent activity on the queues being moved between.
            startMovingMessages();
            toQueue.startMovingMessages();

            // Get the list of messages to move.
            List<QueueEntry> foundMessagesList = getMessagesOnTheQueue(fromMessageId, toMessageId);

            try
            {
                fromStore.beginTran(storeContext);

                // Move the messages in on the message store.
                for (QueueEntry entry : foundMessagesList)
                {
                    AMQMessage message = entry.getMessage();
                    toStore.enqueueMessage(storeContext, toQueue._name, message.getMessageId());
                    message.takeReference();
                }

                // Commit and flush the move transcations.
                try
                {
                    fromStore.commitTran(storeContext);
                }
                catch (AMQException e)
                {
                    throw new RuntimeException("Failed to commit transaction whilst moving messages on message store.", e);
                }

                // Move the messages on the in-memory queues.
                toQueue.enqueueMovedMessages(storeContext, foundMessagesList);
            }
            // Abort the move transactions on move failures.
            catch (AMQException e)
            {
                try
                {
                    fromStore.abortTran(storeContext);
                }
                catch (AMQException ae)
                {
                    throw new RuntimeException("Failed to abort transaction whilst moving messages on message store.", ae);
                }
            }
        }
        // Release locks to allow activity on the queues being moved between to continue.
        finally
        {
            toQueue.stopMovingMessages();
            stopMovingMessages();
        }
    }

    /**
     * Removes messages from this queue, and also commits the remove on the message store. Delivery activity
     * on the queues being moved between is suspended during the remove.
     *
     * @param fromMessageId The first message id to move.
     * @param toMessageId   The last message id to move.
     * @param storeContext  The context of the message store under which to perform the move. This is associated with
     *                      the stores transactional context.
     */
    public synchronized void removeMessagesFromQueue(long fromMessageId, long toMessageId, StoreContext storeContext)
    {
        MessageStore fromStore = getVirtualHost().getMessageStore();

        try
        {
            // Obtain locks to prevent activity on the queues being moved between.
            startMovingMessages();

            // Get the list of messages to move.
            List<QueueEntry> foundMessagesList = getMessagesOnTheQueue(fromMessageId, toMessageId);

            try
            {
                fromStore.beginTran(storeContext);

                // remove the messages in on the message store.
                for (QueueEntry entry : foundMessagesList)
                {
                    AMQMessage message = entry.getMessage();
                    fromStore.dequeueMessage(storeContext, _name, message.getMessageId());
                }

                // Commit and flush the move transcations.
                try
                {
                    fromStore.commitTran(storeContext);
                }
                catch (AMQException e)
                {
                    throw new RuntimeException("Failed to commit transaction whilst moving messages on message store.", e);
                }

                // remove the messages on the in-memory queues.
                _deliveryMgr.removeMovedMessages(foundMessagesList);
            }
            // Abort the move transactions on move failures.
            catch (AMQException e)
            {
                try
                {
                    fromStore.abortTran(storeContext);
                }
                catch (AMQException ae)
                {
                    throw new RuntimeException("Failed to abort transaction whilst moving messages on message store.", ae);
                }
            }
        }
        // Release locks to allow activity on the queues being moved between to continue.
        finally
        {
            stopMovingMessages();
        }
    }

    public void startMovingMessages()
    {
        _deliveryMgr.startMovingMessages();
    }

    private void enqueueMovedMessages(StoreContext storeContext, List<QueueEntry> messageList)
    {
        _deliveryMgr.enqueueMovedMessages(storeContext, messageList);
        _totalMessagesReceived.addAndGet(messageList.size());
    }

    public void stopMovingMessages()
    {
        _deliveryMgr.stopMovingMessages();
        _deliveryMgr.processAsync(_asyncDelivery);
    }

    /** @return MBean object associated with this Queue */
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
        return _totalMessagesReceived.get();
    }

    public long getMaximumMessageCount()
    {
        return _maximumMessageCount;
    }

    public void setMaximumMessageCount(long value)
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

    /** Removes the QueueEntry from the top of the queue. */
    public synchronized void deleteMessageFromTop(StoreContext storeContext) throws AMQException
    {
        _deliveryMgr.removeAMessageFromTop(storeContext, this);
    }

    /** removes all the messages from the queue. */
    public synchronized long clearQueue(StoreContext storeContext) throws AMQException
    {
        return _deliveryMgr.clearAllMessages(storeContext);
    }

    public void bind(AMQShortString routingKey, FieldTable arguments, Exchange exchange) throws AMQException
    {
        exchange.registerQueue(routingKey, this, arguments);
        if (isDurable() && exchange.isDurable())
        {
            _virtualHost.getMessageStore().bindQueue(exchange, routingKey, this, arguments);
        }

        _bindings.addBinding(routingKey, arguments, exchange);
    }

    public void unBind(AMQShortString routingKey, FieldTable arguments, Exchange exchange) throws AMQException
    {
        exchange.deregisterQueue(routingKey, this, arguments);
        if (isDurable() && exchange.isDurable())
        {
            _virtualHost.getMessageStore().unbindQueue(exchange, routingKey, this, arguments);
        }

        _bindings.remove(routingKey, arguments, exchange);
    }

    public void registerProtocolSession(AMQProtocolSession ps, int channel, AMQShortString consumerTag, boolean acks,
                                        FieldTable filters, boolean noLocal, boolean exclusive) throws AMQException
    {
        if (incrementSubscriberCount() > 1)
        {
            if (isExclusive())
            {
                decrementSubscriberCount();
                throw new ExistingExclusiveSubscription();
            }
            else if (exclusive)
            {
                decrementSubscriberCount();
                throw new ExistingSubscriptionPreventsExclusive();
            }

        }
        else if (exclusive)
        {
            setExclusive(true);
        }

        if (_logger.isDebugEnabled())
        {
            _logger.debug(MessageFormat.format("Registering protocol session {0} with channel {1} and "
                                               + "consumer tag {2} with {3}", ps, channel, consumerTag, this));
        }

        Subscription subscription =
                _subscriptionFactory.createSubscription(channel, ps, consumerTag, acks, filters, noLocal, this);

        if (subscription.filtersMessages())
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
        if (_logger.isDebugEnabled())
        {
            _logger.debug(MessageFormat.format(
                    "Unregistering protocol session {0} with channel {1} and consumer tag {2} from {3}",
                    ps, channel, consumerTag, this));
        }

        Subscription removedSubscription;
        if ((removedSubscription = _subscribers.removeSubscriber(_subscriptionFactory.createSubscription(channel, ps,
                                                                                                         consumerTag)))
            == null)
        {
            throw new AMQException("Protocol session with channel " + channel + " and consumer tag " + consumerTag
                                   + " and protocol session key " + ps.getKey() + " not registered with queue " + this);
        }

        removedSubscription.close();
        setExclusive(false);
        decrementSubscriberCount();

        // if we are eligible for auto deletion, unregister from the queue registry
        if (_autoDelete && _subscribers.isEmpty())
        {
            if (_logger.isInfoEnabled())
            {
                _logger.info("Auto-deleteing queue:" + this);
            }

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
        if (!_deleted.getAndSet(true))
        {
            _subscribers.queueDeleted(this);
            _bindings.deregister();
            _virtualHost.getQueueRegistry().unregisterQueue(_name);
            _managedObject.unregister();
            for (Task task : _deleteTaskList)
            {
                task.doTask(this);
            }

            _deleteTaskList.clear();
        }
    }

    protected void autodelete() throws AMQException
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug(MessageFormat.format("autodeleting {0}", this));
        }

        delete();
    }

    /*public void processGet(StoreContext storeContext, AMQMessage msg, boolean deliverFirst) throws AMQException
    {
        // fixme not sure what this is doing. should we be passing deliverFirst through here?
        // This code is not used so when it is perhaps it should
        _deliveryMgr.deliver(storeContext, getName(), msg, deliverFirst);
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
    }*/

    // public DeliveryManager getDeliveryManager()
    // {
    // return _deliveryMgr;
    // }

    public void process(StoreContext storeContext, QueueEntry entry, boolean deliverFirst) throws AMQException
    {
        AMQMessage msg = entry.getMessage();
        _deliveryMgr.deliver(storeContext, getName(), entry, deliverFirst);
        try
        {
            msg.checkDeliveredToConsumer();
            updateReceivedMessageCount(entry);
        }
        catch (NoConsumersException e)
        {
            // as this message will be returned, it should be removed
            // from the queue:
            dequeue(storeContext, entry);
        }
    }

    public void dequeue(StoreContext storeContext, QueueEntry entry) throws FailedDequeueException
    {
        try
        {
            entry.getMessage().dequeue(storeContext, this);
        }
        catch (MessageCleanupException e)
        {
            // Message was dequeued, but could not then be deleted
            // though it is no longer referenced. This should be very
            // rare and can be detected and cleaned up on recovery or
            // done through some form of manual intervention.
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

    protected void updateReceivedMessageCount(QueueEntry entry) throws AMQException
    {
        AMQMessage msg = entry.getMessage();

        if (!msg.isRedelivered())
        {
            _totalMessagesReceived.incrementAndGet();
        }

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

        if ((o == null) || (getClass() != o.getClass()))
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

    public void subscriberHasPendingResend(boolean hasContent, SubscriptionImpl subscription, QueueEntry entry)
    {
        _deliveryMgr.subscriberHasPendingResend(hasContent, subscription, entry);
    }

    public QueueEntry createEntry(AMQMessage amqMessage)
    {
        return new QueueEntry(this, amqMessage);
    }

}
