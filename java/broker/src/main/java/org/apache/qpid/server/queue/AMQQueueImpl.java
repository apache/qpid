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
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.virtualhost.VirtualHost;

import javax.management.JMException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This is an AMQ Queue, and should not be confused with a JMS queue or any other abstraction like that. It is described
 * fully in RFC 006.
 */
public class AMQQueueImpl implements AMQQueue, Subscription.StateListener
{

    private static final Logger _logger = Logger.getLogger(AMQQueueImpl.class);

    private final AMQShortString _name;

    /** null means shared */
    private final AMQShortString _owner;

    private final boolean _durable;

    /** If true, this queue is deleted when the last subscriber is removed */
    private final boolean _autoDelete;

    private final AtomicInteger _subscriberCount = new AtomicInteger();

    private final AtomicBoolean _isExclusive = new AtomicBoolean();

    private final AtomicBoolean _deleted = new AtomicBoolean(false);

    private List<Task> _deleteTaskList = new CopyOnWriteArrayList<Task>();

    /** Manages message delivery. */
    private final ConcurrentSelectorDeliveryManager _deliveryMgr;

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


    private final Set<NotificationCheck> _notificationChecks = EnumSet.noneOf(NotificationCheck.class);
    private final AtomicLong _queueEntryId = new AtomicLong(Long.MIN_VALUE);


    protected AMQQueueImpl(AMQShortString name, boolean durable, AMQShortString owner, boolean autoDelete, VirtualHost virtualHost)
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
        _asyncDelivery = AsyncDeliveryConfig.getAsyncDeliveryExecutor();

        _managedObject = createMBean();
        _managedObject.register();

        _deliveryMgr = new ConcurrentSelectorDeliveryManager(this);

        // This ensure that the notification checks for the configured alerts are created.
        setMaximumMessageAge(_maximumMessageAge);
        setMaximumMessageCount(_maximumMessageCount);
        setMaximumMessageSize(_maximumMessageSize);
        setMaximumQueueDepth(_maximumQueueDepth);

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

    public final AMQShortString getName()
    {
        return _name;
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

    public int getUndeliveredMessageCount()
    {
        return getMessageCount();
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
            quiesce();
            toQueue.quiesce();

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
                    toStore.enqueueMessage(storeContext, toQueue.getName(), message.getMessageId());
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
            toQueue.start();
            start();
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
            quiesce();
            toQueue.quiesce();

            // Get the list of messages to move.
            List<QueueEntry> foundMessagesList = getMessagesOnTheQueue(fromMessageId, toMessageId);

            try
            {
                fromStore.beginTran(storeContext);

                // Move the messages in on the message store.
                for (QueueEntry entry : foundMessagesList)
                {
                    AMQMessage message = entry.getMessage();
                    toStore.enqueueMessage(storeContext, toQueue.getName(), message.getMessageId());
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
            toQueue.start();
            start();
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
            quiesce();

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
            start();
        }
    }

    public void quiesce()
    {
        _deliveryMgr.startMovingMessages();
    }

    public void enqueueMovedMessages(StoreContext storeContext, List<QueueEntry> messageList)
    {
        _deliveryMgr.enqueueMovedMessages(storeContext, messageList);
        _totalMessagesReceived.addAndGet(messageList.size());
    }

    public void start()
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

    public void setMaximumMessageSize(final long maximumMessageSize)
    {
        _maximumMessageSize = maximumMessageSize;
        if(maximumMessageSize == 0L)
        {
            _notificationChecks.remove(NotificationCheck.MESSAGE_SIZE_ALERT);
        }
        else
        {
            _notificationChecks.add(NotificationCheck.MESSAGE_SIZE_ALERT);
        }
    }

    public int getConsumerCount()
    {
        return getSubscribers().getConsumerCount();
    }

    public int getActiveConsumerCount()
    {
        return getSubscribers().getActiveConsumerCount();
    }

    public long getReceivedMessageCount()
    {
        return _totalMessagesReceived.get();
    }

    public long getMaximumMessageCount()
    {
        return _maximumMessageCount;
    }

    public void setMaximumMessageCount(final long maximumMessageCount)
    {
        _maximumMessageCount = maximumMessageCount;
        if(maximumMessageCount == 0L)
        {
            _notificationChecks.remove(NotificationCheck.MESSAGE_COUNT_ALERT);
        }
        else
        {
            _notificationChecks.add(NotificationCheck.MESSAGE_COUNT_ALERT);
        }



    }

    public long getMaximumQueueDepth()
    {
        return _maximumQueueDepth;
    }

    // Sets the queue depth, the max queue size
    public void setMaximumQueueDepth(final long maximumQueueDepth)
    {
        _maximumQueueDepth = maximumQueueDepth;
        if(maximumQueueDepth == 0L)
        {
            _notificationChecks.remove(NotificationCheck.QUEUE_DEPTH_ALERT);
        }
        else
        {
            _notificationChecks.add(NotificationCheck.QUEUE_DEPTH_ALERT);
        }

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

    public void bind(Exchange exchange, AMQShortString routingKey, FieldTable arguments) throws AMQException
    {
        exchange.registerQueue(routingKey, this, arguments);
        if (isDurable() && exchange.isDurable())
        {
            _virtualHost.getMessageStore().bindQueue(exchange, routingKey, this, arguments);
        }

        _bindings.addBinding(routingKey, arguments, exchange);
    }

    public void unBind(Exchange exchange, AMQShortString routingKey, FieldTable arguments) throws AMQException
    {
        exchange.deregisterQueue(routingKey, this, arguments);
        if (isDurable() && exchange.isDurable())
        {
            _virtualHost.getMessageStore().unbindQueue(exchange, routingKey, this, arguments);
        }

        boolean removed = _bindings.remove(routingKey, arguments, exchange);
        if(!removed)
        {
            _logger.error("Mismatch between queue bindings and exchange record of bindings");
        }
    }

    public void registerSubscription(final Subscription subscription, final boolean exclusive) throws AMQException
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
            _logger.debug(MessageFormat.format("Registering protocol subscription {0} with queue {1}."
                                               , subscription, this));
        }


        subscription.setQueue(this);

        if (subscription.filtersMessages())
        {
            if (_deliveryMgr.hasQueuedMessages())
            {
                _deliveryMgr.populatePreDeliveryQueue(subscription);
            }
        }

        subscription.setStateListener(this);
        getSubscribers().addSubscriber(subscription);
        if(exclusive)
        {
            getSubscribers().setExclusive(true);
        }        
        _deliveryMgr.start(subscription);
        deliverAsync();
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

    public void unregisterSubscription(final Subscription subscription) throws AMQException
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug(MessageFormat.format(
                    "Unregistering subscription {0} from {1}",
                    subscription, this));
        }


        getSubscribers().setExclusive(false);
        _deliveryMgr.closeSubscription(subscription);

        if ((getSubscribers().removeSubscriber(subscription)) == null)
        {
            throw new AMQException("Subscription " + subscription + " not registered with queue " + this);
        }


        setExclusive(false);
        decrementSubscriberCount();

        // if we are eligible for auto deletion, unregister from the queue registry
        if (_autoDelete && getSubscribers().isEmpty())
        {
            if (_logger.isInfoEnabled())
            {
                _logger.info("Auto-deleteing queue:" + this);
            }

            autodelete();
            // we need to manually fire the event to the removed subscription (which was the last one left for this
            // queue. This is because the delete method uses the subscription set which has just been cleared
            subscription.queueDeleted(this);
        }
    }

    public boolean isUnused()
    {
        return getSubscribers().isEmpty();
    }

    public boolean isEmpty()
    {
        return !_deliveryMgr.hasQueuedMessages();
    }

    public int delete() throws AMQException
    {
        if (!_deleted.getAndSet(true))
        {
            getSubscribers().queueDeleted(this);
            _bindings.deregister();
            _virtualHost.getQueueRegistry().unregisterQueue(_name);
            _managedObject.unregister();
            for (Task task : _deleteTaskList)
            {
                task.doTask(this);
            }

            _deleteTaskList.clear();
        }
        return _deliveryMgr.getQueueMessageCount();
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
            msg.immediateAndNotDelivered();
            updateReceivedMessageCount(msg);
        }
        catch (NoConsumersException e)
        {
            // as this message will be returned, it should be removed
            // from the queue:
            dequeue(storeContext, msg);
        }
    }*/

    public void requeue(StoreContext storeContext, QueueEntry entry) throws AMQException
    {
        process(storeContext,entry,true);
    }


    public QueueEntry enqueue(StoreContext storeContext, AMQMessage message) throws AMQException
    {
        QueueEntryImpl entry = createEntry(message);
        process(storeContext, entry, false);
        return entry;
    }

    // public DeliveryManager getDeliveryManager()
    // {
    // return _deliveryMgr;
    // }

    public void process(StoreContext storeContext, QueueEntry entry, boolean deliverFirst) throws AMQException
    {
        AMQMessage msg = entry.getMessage();
        _deliveryMgr.deliver(storeContext, _name, entry, deliverFirst);
        if(msg.immediateAndNotDelivered())
        {
            dequeue(storeContext, entry);
        }
        else
        {
            updateReceivedMessageCount(entry);
        }
    }

    public void dequeue(StoreContext storeContext, QueueEntry entry) throws FailedDequeueException
    {
        try
        {
            AMQMessage msg = entry.getMessage();
            if(isDurable() && msg.isPersistent())
            {
                _virtualHost.getMessageStore().dequeueMessage(storeContext, getName(), msg.getMessageId());
            }
            
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

    public SubscriptionManager getSubscribers()
    {
        return _deliveryMgr.getSubscribers();
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

        return (_name.equals(amqQueue.getName()));
    }

    public int hashCode()
    {
        return _name.hashCode();
    }

    public String toString()
    {
        return  _name.toString();
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

    public void addQueueDeleteTask(Task task)
    {
        _deleteTaskList.add(task);
    }

    public boolean resend(final QueueEntry entry, final Subscription sub)
    {


        // Get the lock so we can tell if the sub scription has closed.
        // will stop delivery to this subscription until the lock is released.
        // note: this approach would allow the use of a single queue if the
        // PreDeliveryQueue would allow head additions.
        // In the Java Qpid client we are suspended whilst doing this so it is all rather Mute..
        // needs guidance from AMQP WG Model SIG
        synchronized (sub.getSendLock())
        {
            if (sub.isClosed())
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Subscription(" + System.identityHashCode(sub)
                               + ") closed during resend so requeuing message");
                }
                // move this message to requeue
                return false;

            }
            else
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Requeuing " + entry.debugIdentity() + " for resend via sub:"
                               + System.identityHashCode(sub));
                }

                entry.release();        
                _deliveryMgr.resend(entry, sub);
                return true;
            }
        } // sync(sub.getSendLock)




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
        if(maximumMessageAge == 0L)
        {
            _notificationChecks.remove(NotificationCheck.MESSAGE_AGE_ALERT);
        }
        else
        {
            _notificationChecks.add(NotificationCheck.MESSAGE_AGE_ALERT);
        }
    }

    public void subscriberHasPendingResend(boolean hasContent, Subscription subscription, QueueEntry entry)
    {
        _deliveryMgr.subscriberHasPendingResend(hasContent, subscription, entry);
    }

    public QueueEntryImpl createEntry(AMQMessage amqMessage)
    {
        return new QueueEntryImpl(this, amqMessage, _queueEntryId.getAndIncrement());
    }

    public int compareTo(Object o)
    {
        return _name.compareTo(((AMQQueue) o).getName());
    }


    public void removeExpiredIfNoSubscribers() throws AMQException
    {
        synchronized(getSubscribers().getChangeLock())
        {
            if(getSubscribers().isEmpty())
            {
                _deliveryMgr.removeExpired();
            }
        }
    }

    public final Set<NotificationCheck> getNotificationChecks()
    {
        return _notificationChecks;
    }

    public void stateChange(Subscription sub, Subscription.State oldState, Subscription.State newState)
    {
        if(newState == Subscription.State.ACTIVE)
        {
            deliverAsync();
        }
    }
}
