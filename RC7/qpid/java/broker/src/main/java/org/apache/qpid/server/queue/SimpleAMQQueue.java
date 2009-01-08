package org.apache.qpid.server.queue;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.configuration.Configurator;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.subscription.SubscriptionList;
import org.apache.qpid.server.output.ProtocolOutputConverter;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.AMQException;
import org.apache.qpid.pool.ReadWriteRunnable;
import org.apache.qpid.pool.ReferenceCountingExecutorService;
import org.apache.qpid.configuration.Configured;
import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;

import javax.management.JMException;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
public class SimpleAMQQueue implements AMQQueue, Subscription.StateListener
{
    private static final Logger _logger = Logger.getLogger(SimpleAMQQueue.class);

    private final AMQShortString _name;

    /** null means shared */
    private final AMQShortString _owner;

    private final boolean _durable;

    /** If true, this queue is deleted when the last subscriber is removed */
    private final boolean _autoDelete;

    private final VirtualHost _virtualHost;

    /** Used to track bindings to exchanges so that on deletion they can easily be cancelled. */
    private final ExchangeBindings _bindings = new ExchangeBindings(this);

    private final AtomicBoolean _deleted = new AtomicBoolean(false);

    private final List<Task> _deleteTaskList = new CopyOnWriteArrayList<Task>();

    private final AtomicInteger _atomicQueueCount = new AtomicInteger(0);

    private final AtomicLong _atomicQueueSize = new AtomicLong(0L);

    private final AtomicInteger _activeSubscriberCount = new AtomicInteger();

    protected final SubscriptionList _subscriptionList = new SubscriptionList(this);
    private final AtomicReference<SubscriptionList.SubscriptionNode> _lastSubscriptionNode = new AtomicReference<SubscriptionList.SubscriptionNode>(_subscriptionList.getHead());

    private volatile Subscription _exclusiveSubscriber;

    protected final QueueEntryList _entries;

    private final AMQQueueMBean _managedObject;
    private final Executor _asyncDelivery;
    private final AtomicLong _totalMessagesReceived = new AtomicLong();

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

    private static final int MAX_ASYNC_DELIVERIES = 10;

    private final Set<NotificationCheck> _notificationChecks = EnumSet.noneOf(NotificationCheck.class);

    private final AtomicLong _stateChangeCount = new AtomicLong(Long.MIN_VALUE);
    private AtomicReference _asynchronousRunner = new AtomicReference(null);
    private AtomicInteger _deliveredMessages = new AtomicInteger();
    private AtomicBoolean _stopped = new AtomicBoolean(false);

    protected SimpleAMQQueue(AMQShortString name, boolean durable, AMQShortString owner, boolean autoDelete, VirtualHost virtualHost)
            throws AMQException
    {
        this(name, durable, owner, autoDelete, virtualHost, new SimpleQueueEntryList.Factory());
    }

    protected SimpleAMQQueue(AMQShortString name,
                             boolean durable,
                             AMQShortString owner,
                             boolean autoDelete,
                             VirtualHost virtualHost,
                             QueueEntryListFactory entryListFactory)
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
        _entries = entryListFactory.createQueueEntryList(this);

        _asyncDelivery = ReferenceCountingExecutorService.getInstance().acquireExecutorService();

        try
        {
            _managedObject = new AMQQueueMBean(this);
            _managedObject.register();
        }
        catch (JMException e)
        {
            throw new AMQException("AMQQueue MBean creation has failed ", e);
        }

        resetNotifications();

    }

    private void resetNotifications()
    {
        // This ensure that the notification checks for the configured alerts are created.
        setMaximumMessageAge(_maximumMessageAge);
        setMaximumMessageCount(_maximumMessageCount);
        setMaximumMessageSize(_maximumMessageSize);
        setMaximumQueueDepth(_maximumQueueDepth);
    }

    // ------ Getters and Setters

    public AMQShortString getName()
    {
        return _name;
    }

    public boolean isDurable()
    {
        return _durable;
    }

    public boolean isAutoDelete()
    {
        return _autoDelete;
    }

    public AMQShortString getOwner()
    {
        return _owner;
    }

    public VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    // ------ bind and unbind

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
        if (!removed)
        {
            _logger.error("Mismatch between queue bindings and exchange record of bindings");
        }
    }

    public List<ExchangeBinding> getExchangeBindings()
    {
        return new ArrayList<ExchangeBinding>(_bindings.getExchangeBindings());
    }

    // ------ Manage Subscriptions

    public synchronized void registerSubscription(final Subscription subscription, final boolean exclusive) throws AMQException
    {

        if (isExclusiveSubscriber())
        {
            throw new ExistingExclusiveSubscription();
        }

        if (exclusive)
        {
            if (getConsumerCount() != 0)
            {
                throw new ExistingSubscriptionPreventsExclusive();
            }
            else
            {
                _exclusiveSubscriber = subscription;

            }
        }

        _activeSubscriberCount.incrementAndGet();
        subscription.setStateListener(this);
        subscription.setLastSeenEntry(null, _entries.getHead());

        if (!isDeleted())
        {
            subscription.setQueue(this);
            _subscriptionList.add(subscription);
            if (isDeleted())
            {
                subscription.queueDeleted(this);
            }
        }
        else
        {
            // TODO
        }

        deliverAsync(subscription);

    }

    public synchronized void unregisterSubscription(final Subscription subscription) throws AMQException
    {
        if (subscription == null)
        {
            throw new NullPointerException("subscription argument is null");
        }

        boolean removed = _subscriptionList.remove(subscription);

        if (removed)
        {
            subscription.close();
            // No longer can the queue have an exclusive consumer
            setExclusiveSubscriber(null);

            QueueEntry lastSeen;

            while ((lastSeen = subscription.getLastSeenEntry()) != null)
            {
                subscription.setLastSeenEntry(lastSeen, null);
            }

            // auto-delete queues must be deleted if there are no remaining subscribers

            if (_autoDelete && getConsumerCount() == 0)
            {
                if (_logger.isInfoEnabled())
                {
                    _logger.info("Auto-deleteing queue:" + this);
                }

                delete();

                // we need to manually fire the event to the removed subscription (which was the last one left for this
                // queue. This is because the delete method uses the subscription set which has just been cleared
                subscription.queueDeleted(this);
            }
        }

    }

    // ------ Enqueue / Dequeue

    public QueueEntry enqueue(StoreContext storeContext, AMQMessage message) throws AMQException
    {

        incrementQueueCount();
        incrementQueueSize(message);

        _totalMessagesReceived.incrementAndGet();

        QueueEntry entry;
        Subscription exclusiveSub = _exclusiveSubscriber;

        if (exclusiveSub != null)
        {
            exclusiveSub.getSendLock();

            try
            {
                entry = _entries.add(message);

                deliverToSubscription(exclusiveSub, entry);

                // where there is more than one producer there's a reasonable chance that even though there is
                // no "queueing" we do not deliver because we get an interleving of _entries.add and
                // deliverToSubscription between threads.  Therefore have one more try. 
                if (!(entry.isAcquired() || entry.isDeleted()))
                {
                    deliverToSubscription(exclusiveSub, entry);
                }
            }
            finally
            {
                exclusiveSub.releaseSendLock();
            }
        }
        else
        {
            entry = _entries.add(message);
            /*

            iterate over subscriptions and if any is at the end of the queue and can deliver this message, then deliver the message

             */
            SubscriptionList.SubscriptionNode node = _lastSubscriptionNode.get();
            SubscriptionList.SubscriptionNode nextNode = node.getNext();
            if (nextNode == null)
            {
                nextNode = _subscriptionList.getHead().getNext();
            }
            while (nextNode != null)
            {
                if (_lastSubscriptionNode.compareAndSet(node, nextNode))
                {
                    break;
                }
                else
                {
                    node = _lastSubscriptionNode.get();
                    nextNode = node.getNext();
                    if (nextNode == null)
                    {
                        nextNode = _subscriptionList.getHead().getNext();
                    }
                }
            }

            // always do one extra loop after we believe we've finished
            // this catches the case where we *just* miss an update
            int loops = 2;

            while (!(entry.isAcquired() || entry.isDeleted()) && loops != 0)
            {
                if (nextNode == null)
                {
                    loops--;
                    nextNode = _subscriptionList.getHead();
                }
                else
                {
                    // if subscription at end, and active, offer
                    Subscription sub = nextNode.getSubscription();
                    deliverToSubscription(sub, entry);
                }
                nextNode = nextNode.getNext();

            }
        }

        if (entry.immediateAndNotDelivered())
        {
            dequeue(storeContext, entry);
            entry.dispose(storeContext);
        }
        else if (!(entry.isAcquired() || entry.isDeleted()))
        {
            checkSubscriptionsNotAheadOfDelivery(entry);

            deliverAsync();
        }

        try
        {
            _managedObject.checkForNotification(entry.getMessage());
        }
        catch (JMException e)
        {
            throw new AMQException("Unable to get notification from manage queue: " + e, e);
        }

        return entry;

    }

    private void deliverToSubscription(final Subscription sub, final QueueEntry entry)
            throws AMQException
    {

        sub.getSendLock();
        try
        {
            if (subscriptionReadyAndHasInterest(sub, entry)
                && !sub.isSuspended())
            {
                if (!sub.wouldSuspend(entry))
                {
                    if (!sub.isBrowser() && !entry.acquire(sub))
                    {
                        // restore credit here that would have been taken away by wouldSuspend since we didn't manage
                        // to acquire the entry for this subscription
                        sub.restoreCredit(entry);
                    }
                    else
                    {

                        deliverMessage(sub, entry);

                    }
                }
            }
        }
        finally
        {
            sub.releaseSendLock();
        }
    }

    protected void checkSubscriptionsNotAheadOfDelivery(final QueueEntry entry)
    {
        // This method is only required for queues which mess with ordering
        // Simple Queues don't :-)
    }

    private void incrementQueueSize(final AMQMessage message)
    {
        getAtomicQueueSize().addAndGet(message.getSize());
    }

    private void incrementQueueCount()
    {
        getAtomicQueueCount().incrementAndGet();
    }

    private void deliverMessage(final Subscription sub, final QueueEntry entry)
            throws AMQException
    {
        _deliveredMessages.incrementAndGet();
        sub.send(entry);

    }

    private boolean subscriptionReadyAndHasInterest(final Subscription sub, final QueueEntry entry)
    {

        // We need to move this subscription on, past entries which are already acquired, or deleted or ones it has no
        // interest in.
        QueueEntry node = sub.getLastSeenEntry();
        while (node != null && (node.isAcquired() || node.isDeleted() || !sub.hasInterest(node)))
        {

            QueueEntry newNode = _entries.next(node);
            if (newNode != null)
            {
                sub.setLastSeenEntry(node, newNode);
                node = sub.getLastSeenEntry();
            }
            else
            {
                node = null;
                break;
            }

        }

        if (node == entry)
        {
            // If the first entry that subscription can process is the one we are trying to deliver to it, then we are
            // good
            return true;
        }
        else
        {
            // Otherwise we should try to update the subscription's last seen entry to the entry we got to, providing
            // no-one else has updated it to something furhter on in the list
            //TODO - check
            //updateLastSeenEntry(sub, entry);
            return false;
        }

    }

    private void updateLastSeenEntry(final Subscription sub, final QueueEntry entry)
    {
        QueueEntry node = sub.getLastSeenEntry();

        if (node != null && entry.compareTo(node) < 0 && sub.hasInterest(entry))
        {
            do
            {
                if (sub.setLastSeenEntry(node, entry))
                {
                    return;
                }
                else
                {
                    node = sub.getLastSeenEntry();
                }
            }
            while (node != null && entry.compareTo(node) < 0);
        }

    }

    public void requeue(StoreContext storeContext, QueueEntry entry) throws AMQException
    {

        SubscriptionList.SubscriptionNodeIterator subscriberIter = _subscriptionList.iterator();
        // iterate over all the subscribers, and if they are in advance of this queue entry then move them backwards
        while (subscriberIter.advance())
        {
            Subscription sub = subscriberIter.getNode().getSubscription();

            // we don't make browsers send the same stuff twice
            if (!sub.isBrowser())
            {
                updateLastSeenEntry(sub, entry);
            }
        }

        deliverAsync();

    }

    public void dequeue(StoreContext storeContext, QueueEntry entry) throws FailedDequeueException
    {
        decrementQueueCount();
        decrementQueueSize(entry);
        if (entry.acquiredBySubscription())
        {
            _deliveredMessages.decrementAndGet();
        }

        try
        {
            AMQMessage msg = entry.getMessage();
            if (msg.isPersistent())
            {
                _virtualHost.getMessageStore().dequeueMessage(storeContext, this, msg.getMessageId());
            }
            //entry.dispose(storeContext);

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

    private void decrementQueueSize(final QueueEntry entry)
    {
        getAtomicQueueSize().addAndGet(-entry.getMessage().getSize());
    }

    void decrementQueueCount()
    {
        getAtomicQueueCount().decrementAndGet();
    }

    public boolean resend(final QueueEntry entry, final Subscription subscription) throws AMQException
    {
        /* TODO : This is wrong as the subscription may be suspended, we should instead change the state of the message
                  entry to resend and move back the subscription pointer. */

        subscription.getSendLock();
        try
        {
            if (!subscription.isClosed())
            {
                deliverMessage(subscription, entry);
                return true;
            }
            else
            {
                return false;
            }
        }
        finally
        {
            subscription.releaseSendLock();
        }
    }

    public int getConsumerCount()
    {
        return _subscriptionList.size();
    }

    public int getActiveConsumerCount()
    {
        return _activeSubscriberCount.get();
    }

    public boolean isUnused()
    {
        return getConsumerCount() == 0;
    }

    public boolean isEmpty()
    {
        return getMessageCount() == 0;
    }

    public int getMessageCount()
    {
        return getAtomicQueueCount().get();
    }

    public long getQueueDepth()
    {
        return getAtomicQueueSize().get();
    }

    public int getUndeliveredMessageCount()
    {
        int count = getMessageCount() - _deliveredMessages.get();
        if (count < 0)
        {
            return 0;
        }
        else
        {
            return count;
        }
    }

    public long getReceivedMessageCount()
    {
        return _totalMessagesReceived.get();
    }

    public long getOldestMessageArrivalTime()
    {
        QueueEntry entry = getOldestQueueEntry();
        return entry == null ? Long.MAX_VALUE : entry.getMessage().getArrivalTime();
    }

    protected QueueEntry getOldestQueueEntry()
    {
        return _entries.next(_entries.getHead());
    }

    public boolean isDeleted()
    {
        return _deleted.get();
    }

    public List<QueueEntry> getMessagesOnTheQueue()
    {
        ArrayList<QueueEntry> entryList = new ArrayList<QueueEntry>();
        QueueEntryIterator queueListIterator = _entries.iterator();
        while (queueListIterator.advance())
        {
            QueueEntry node = queueListIterator.getNode();
            if (node != null && !node.isDeleted())
            {
                entryList.add(node);
            }
        }
        return entryList;

    }

    public void stateChange(Subscription sub, Subscription.State oldState, Subscription.State newState)
    {
        if (oldState == Subscription.State.ACTIVE && newState != Subscription.State.ACTIVE)
        {
            _activeSubscriberCount.decrementAndGet();

        }
        else if (newState == Subscription.State.ACTIVE)
        {
            if (oldState != Subscription.State.ACTIVE)
            {
                _activeSubscriberCount.incrementAndGet();

            }
            deliverAsync(sub);
        }
    }

    public int compareTo(final AMQQueue o)
    {
        return _name.compareTo(o.getName());
    }

    public AtomicInteger getAtomicQueueCount()
    {
        return _atomicQueueCount;
    }

    public AtomicLong getAtomicQueueSize()
    {
        return _atomicQueueSize;
    }

    private boolean isExclusiveSubscriber()
    {
        return _exclusiveSubscriber != null;
    }

    private void setExclusiveSubscriber(Subscription exclusiveSubscriber)
    {
        _exclusiveSubscriber = exclusiveSubscriber;
    }

    public static interface QueueEntryFilter
    {
        public boolean accept(QueueEntry entry);

        public boolean filterComplete();
    }

    public List<QueueEntry> getMessagesOnTheQueue(final long fromMessageId, final long toMessageId)
    {
        return getMessagesOnTheQueue(new QueueEntryFilter()
        {

            public boolean accept(QueueEntry entry)
            {
                final long messageId = entry.getMessage().getMessageId();
                return messageId >= fromMessageId && messageId <= toMessageId;
            }

            public boolean filterComplete()
            {
                return false;
            }
        });
    }

    public QueueEntry getMessageOnTheQueue(final long messageId)
    {
        List<QueueEntry> entries = getMessagesOnTheQueue(new QueueEntryFilter()
        {
            private boolean _complete;

            public boolean accept(QueueEntry entry)
            {
                _complete = entry.getMessage().getMessageId() == messageId;
                return _complete;
            }

            public boolean filterComplete()
            {
                return _complete;
            }
        });
        return entries.isEmpty() ? null : entries.get(0);
    }

    public List<QueueEntry> getMessagesOnTheQueue(QueueEntryFilter filter)
    {
        ArrayList<QueueEntry> entryList = new ArrayList<QueueEntry>();
        QueueEntryIterator queueListIterator = _entries.iterator();
        while (queueListIterator.advance() && !filter.filterComplete())
        {
            QueueEntry node = queueListIterator.getNode();
            if (!node.isDeleted() && filter.accept(node))
            {
                entryList.add(node);
            }
        }
        return entryList;

    }

    public void moveMessagesToAnotherQueue(final long fromMessageId,
                                           final long toMessageId,
                                           String queueName,
                                           StoreContext storeContext)
    {

        AMQQueue toQueue = getVirtualHost().getQueueRegistry().getQueue(new AMQShortString(queueName));
        MessageStore store = getVirtualHost().getMessageStore();

        List<QueueEntry> entries = getMessagesOnTheQueue(new QueueEntryFilter()
        {

            public boolean accept(QueueEntry entry)
            {
                final long messageId = entry.getMessage().getMessageId();
                return (messageId >= fromMessageId)
                       && (messageId <= toMessageId)
                       && entry.acquire();
            }

            public boolean filterComplete()
            {
                return false;
            }
        });

        try
        {
            store.beginTran(storeContext);

            // Move the messages in on the message store.
            for (QueueEntry entry : entries)
            {
                AMQMessage message = entry.getMessage();

                if (message.isPersistent() && toQueue.isDurable())
                {
                    store.enqueueMessage(storeContext, toQueue, message.getMessageId());
                }
                // dequeue does not decrement the refence count
                entry.dequeue(storeContext);
            }

            // Commit and flush the move transcations.
            try
            {
                store.commitTran(storeContext);
            }
            catch (AMQException e)
            {
                throw new RuntimeException("Failed to commit transaction whilst moving messages on message store.", e);
            }
        }
        catch (AMQException e)
        {
            try
            {
                store.abortTran(storeContext);
            }
            catch (AMQException rollbackEx)
            {
                _logger.error("Failed to rollback transaction when error occured moving messages", rollbackEx);
            }
            throw new RuntimeException(e);
        }

        try
        {
            for (QueueEntry entry : entries)
            {
                toQueue.enqueue(storeContext, entry.getMessage());

            }
        }
        catch (MessageCleanupException e)
        {
            throw new RuntimeException(e);
        }
        catch (AMQException e)
        {
            throw new RuntimeException(e);
        }

    }

    public void copyMessagesToAnotherQueue(final long fromMessageId,
                                           final long toMessageId,
                                           String queueName,
                                           final StoreContext storeContext)
    {
        AMQQueue toQueue = getVirtualHost().getQueueRegistry().getQueue(new AMQShortString(queueName));
        MessageStore store = getVirtualHost().getMessageStore();

        List<QueueEntry> entries = getMessagesOnTheQueue(new QueueEntryFilter()
        {

            public boolean accept(QueueEntry entry)
            {
                final long messageId = entry.getMessage().getMessageId();
                if ((messageId >= fromMessageId)
                    && (messageId <= toMessageId))
                {
                    if (!entry.isDeleted())
                    {
                        return entry.getMessage().incrementReference();
                    }
                }

                return false;
            }

            public boolean filterComplete()
            {
                return false;
            }
        });

        try
        {
            store.beginTran(storeContext);

            // Move the messages in on the message store.
            for (QueueEntry entry : entries)
            {
                AMQMessage message = entry.getMessage();

                if (message.isReferenced() && message.isPersistent() && toQueue.isDurable())
                {
                    store.enqueueMessage(storeContext, toQueue, message.getMessageId());
                }
            }

            // Commit and flush the move transcations.
            try
            {
                store.commitTran(storeContext);
            }
            catch (AMQException e)
            {
                throw new RuntimeException("Failed to commit transaction whilst moving messages on message store.", e);
            }
        }
        catch (AMQException e)
        {
            try
            {
                store.abortTran(storeContext);
            }
            catch (AMQException rollbackEx)
            {
                _logger.error("Failed to rollback transaction when error occured moving messages", rollbackEx);
            }
            throw new RuntimeException(e);
        }

        try
        {
            for (QueueEntry entry : entries)
            {
                if (entry.getMessage().isReferenced())
                {
                    toQueue.enqueue(storeContext, entry.getMessage());
                }
            }
        }
        catch (MessageCleanupException e)
        {
            throw new RuntimeException(e);
        }
        catch (AMQException e)
        {
            throw new RuntimeException(e);
        }

    }

    public void removeMessagesFromQueue(long fromMessageId, long toMessageId, StoreContext storeContext)
    {

        try
        {
            QueueEntryIterator queueListIterator = _entries.iterator();

            while (queueListIterator.advance())
            {
                QueueEntry node = queueListIterator.getNode();

                final long messageId = node.getMessage().getMessageId();

                if ((messageId >= fromMessageId)
                    && (messageId <= toMessageId)
                    && !node.isDeleted()
                    && node.acquire())
                {
                    node.discard(storeContext);
                }

            }
        }
        catch (AMQException e)
        {
            throw new RuntimeException(e);
        }

    }

    // ------ Management functions

    public void deleteMessageFromTop(StoreContext storeContext) throws AMQException
    {
        QueueEntryIterator queueListIterator = _entries.iterator();
        boolean noDeletes = true;

        while (noDeletes && queueListIterator.advance())
        {
            QueueEntry node = queueListIterator.getNode();
            if (!node.isDeleted() && node.acquire())
            {
                node.discard(storeContext);
                noDeletes = false;
            }

        }
    }

    public long clearQueue(StoreContext storeContext) throws AMQException
    {

        QueueEntryIterator queueListIterator = _entries.iterator();
        long count = 0;

        while (queueListIterator.advance())
        {
            QueueEntry node = queueListIterator.getNode();
            if (!node.isDeleted() && node.acquire())
            {
                node.discard(storeContext);
                count++;
            }

        }
        return count;

    }

    public void addQueueDeleteTask(final Task task)
    {
        _deleteTaskList.add(task);
    }

    public int delete() throws AMQException
    {
        if (!_deleted.getAndSet(true))
        {

            SubscriptionList.SubscriptionNodeIterator subscriptionIter = _subscriptionList.iterator();

            while (subscriptionIter.advance())
            {
                Subscription s = subscriptionIter.getNode().getSubscription();
                if (s != null)
                {
                    s.queueDeleted(this);
                }
            }

            _bindings.deregister();
            _virtualHost.getQueueRegistry().unregisterQueue(_name);

            _managedObject.unregister();
            for (Task task : _deleteTaskList)
            {
                task.doTask(this);
            }

            _deleteTaskList.clear();
            stop();
        }
        return getMessageCount();

    }

    public void stop()
    {
        if (!_stopped.getAndSet(true))
        {
            ReferenceCountingExecutorService.getInstance().releaseExecutorService();
        }
    }

    public void deliverAsync()
    {
        _stateChangeCount.incrementAndGet();

        Runner runner = new Runner();

        if (_asynchronousRunner.compareAndSet(null, runner))
        {
            _asyncDelivery.execute(runner);
        }
    }

    public void deliverAsync(Subscription sub)
    {
        _asyncDelivery.execute(new SubFlushRunner(sub));
    }

    private class Runner implements ReadWriteRunnable
    {
        public void run()
        {
            try
            {
                processQueue(this);
            }
            catch (AMQException e)
            {
                _logger.error(e);
            }

        }

        public boolean isRead()
        {
            return false;
        }

        public boolean isWrite()
        {
            return true;
        }
    }

    private class SubFlushRunner implements ReadWriteRunnable
    {
        private final Subscription _sub;

        public SubFlushRunner(Subscription sub)
        {
            _sub = sub;
        }

        public void run()
        {
            boolean complete = false;
            try
            {
                complete = flushSubscription(_sub, new Long(MAX_ASYNC_DELIVERIES));

            }
            catch (AMQException e)
            {
                _logger.error(e);
            }
            if (!complete && !_sub.isSuspended())
            {
                _asyncDelivery.execute(this);
            }

        }

        public boolean isRead()
        {
            return false;
        }

        public boolean isWrite()
        {
            return true;
        }
    }

    public void flushSubscription(Subscription sub) throws AMQException
    {
        flushSubscription(sub, Long.MAX_VALUE);
    }

    public boolean flushSubscription(Subscription sub, Long iterations) throws AMQException
    {
        boolean atTail = false;

        while (!sub.isSuspended() && !atTail && iterations != 0)
        {
            try 
            {
                sub.getSendLock();
                atTail =  attemptDelivery(sub);
                if (atTail && sub.isAutoClose())
                {
                    unregisterSubscription(sub);

                    ProtocolOutputConverter converter = sub.getChannel().getProtocolSession().getProtocolOutputConverter();
                    converter.confirmConsumerAutoClose(sub.getChannel().getChannelId(), sub.getConsumerTag());
                }
                else if (!atTail)
                {
                    iterations--;
                }
            }
            finally
            {
                sub.releaseSendLock();
            }
        }

        // if there's (potentially) more than one subscription the others will potentially not have been advanced to the
        // next entry they are interested in yet.  This would lead to holding on to references to expired messages, etc
        // which would give us memory "leak".

        if (!isExclusiveSubscriber())
        {
            advanceAllSubscriptions();
        }
        return atTail;
    }

    private boolean attemptDelivery(Subscription sub) throws AMQException
    {
        boolean atTail = false;
        boolean advanced = false;
        boolean subActive = sub.isActive();
        if (subActive)
        {
            QueueEntry node = moveSubscriptionToNextNode(sub);
            if (!(node.isAcquired() || node.isDeleted()))
            {
                if (!sub.isSuspended())
                {
                    if (sub.hasInterest(node))
                    {
                        if (!sub.wouldSuspend(node))
                        {
                            if (!sub.isBrowser() && !node.acquire(sub))
                            {
                                sub.restoreCredit(node);
                            }
                            else
                            {
                                deliverMessage(sub, node);

                                if (sub.isBrowser())
                                {
                                    QueueEntry newNode = _entries.next(node);

                                    if (newNode != null)
                                    {
                                        advanced = true;
                                        sub.setLastSeenEntry(node, newNode);
                                        node = sub.getLastSeenEntry();
                                    }
                                }
                            }

                        }
                        else // Not enough Credit for message and wouldSuspend
                        {
                            //QPID-1187 - Treat the subscription as suspended for this message
                            // and wait for the message to be removed to continue delivery.
                            subActive = false;
                            node.addStateChangeListener(new QueueEntryListener(sub, node));
                        }
                    }
                    else
                    {
                        // this subscription is not interested in this node so we can skip over it
                        QueueEntry newNode = _entries.next(node);
                        if (newNode != null)
                        {
                            sub.setLastSeenEntry(node, newNode);
                        }
                    }
                }

            }
            atTail = (_entries.next(node) == null) && !advanced;
        }
        return atTail || !subActive;
    }

    protected void advanceAllSubscriptions() throws AMQException
    {
        SubscriptionList.SubscriptionNodeIterator subscriberIter = _subscriptionList.iterator();
        while (subscriberIter.advance())
        {
            SubscriptionList.SubscriptionNode subNode = subscriberIter.getNode();
            Subscription sub = subNode.getSubscription();
            moveSubscriptionToNextNode(sub);
        }
    }

    private QueueEntry moveSubscriptionToNextNode(final Subscription sub)
            throws AMQException
    {
        QueueEntry node = sub.getLastSeenEntry();

        while (node != null && (node.isAcquired() || node.isDeleted() || node.expired()))
        {
            if (!node.isAcquired() && !node.isDeleted() && node.expired())
            {
                if (node.acquire())
                {
                    final StoreContext reapingStoreContext = new StoreContext();
                    node.discard(reapingStoreContext);
                }
            }
            QueueEntry newNode = _entries.next(node);
            if (newNode != null)
            {
                sub.setLastSeenEntry(node, newNode);
                node = sub.getLastSeenEntry();
            }
            else
            {
                break;
            }

        }
        return node;
    }

    private void processQueue(Runnable runner) throws AMQException
    {
        long stateChangeCount;
        long previousStateChangeCount = Long.MIN_VALUE;
        boolean deliveryIncomplete = true;

        int extraLoops = 1;
        Long iterations = new Long(MAX_ASYNC_DELIVERIES);

        _asynchronousRunner.compareAndSet(runner, null);

        while (iterations != 0 && ((previousStateChangeCount != (stateChangeCount = _stateChangeCount.get())) || deliveryIncomplete) && _asynchronousRunner.compareAndSet(null, runner))
        {
            // we want to have one extra loop after every subscription has reached the point where it cannot move
            // further, just in case the advance of one subscription in the last loop allows a different subscription to
            // move forward in the next iteration

            if (previousStateChangeCount != stateChangeCount)
            {
                extraLoops = 1;
            }

            previousStateChangeCount = stateChangeCount;
            deliveryIncomplete = _subscriptionList.size() != 0;
            boolean done = true;

            SubscriptionList.SubscriptionNodeIterator subscriptionIter = _subscriptionList.iterator();
            //iterate over the subscribers and try to advance their pointer
            while (subscriptionIter.advance())
            {
                boolean closeConsumer = false;
                Subscription sub = subscriptionIter.getNode().getSubscription();
                sub.getSendLock();
                try
                {
                    if (sub != null)
                    {

                        QueueEntry node = moveSubscriptionToNextNode(sub);
                        if (node != null)
                        {
                            done = attemptDelivery(sub);
                        }
                    }
                    if (done)
                    {
                        if (extraLoops == 0)
                        {
                            deliveryIncomplete = false;
                            if (sub.isAutoClose())
                            {
                                unregisterSubscription(sub);

                                ProtocolOutputConverter converter = sub.getChannel().getProtocolSession().getProtocolOutputConverter();
                                converter.confirmConsumerAutoClose(sub.getChannel().getChannelId(), sub.getConsumerTag());
                            }
                        }
                        else
                        {
                            extraLoops--;
                        }
                    }
                    else
                    {
                        iterations--;
                        extraLoops = 1;
                    }
                }
                finally
                {
                    sub.releaseSendLock();
                }
            }
            _asynchronousRunner.set(null);
        }

        // If deliveries == 0 then the limitting factor was the time-slicing rather than available messages or credit
        // therefore we should schedule this runner again (unless someone beats us to it :-) ).
        if (iterations == 0 && _asynchronousRunner.compareAndSet(null, runner))
        {
            _asyncDelivery.execute(runner);
        }
    }

    public void removeExpiredIfNoSubscribers() throws AMQException
    {

        final StoreContext storeContext = new StoreContext();

        QueueEntryIterator queueListIterator = _entries.iterator();

        while (queueListIterator.advance())
        {
            QueueEntry node = queueListIterator.getNode();
            if (!node.isDeleted() && node.expired() && node.acquire())
            {

                node.discard(storeContext);
            }

        }

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
        if (maximumMessageAge == 0L)
        {
            _notificationChecks.remove(NotificationCheck.MESSAGE_AGE_ALERT);
        }
        else
        {
            _notificationChecks.add(NotificationCheck.MESSAGE_AGE_ALERT);
        }
    }

    public long getMaximumMessageCount()
    {
        return _maximumMessageCount;
    }

    public void setMaximumMessageCount(final long maximumMessageCount)
    {
        _maximumMessageCount = maximumMessageCount;
        if (maximumMessageCount == 0L)
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
        if (maximumQueueDepth == 0L)
        {
            _notificationChecks.remove(NotificationCheck.QUEUE_DEPTH_ALERT);
        }
        else
        {
            _notificationChecks.add(NotificationCheck.QUEUE_DEPTH_ALERT);
        }

    }

    public long getMaximumMessageSize()
    {
        return _maximumMessageSize;
    }

    public void setMaximumMessageSize(final long maximumMessageSize)
    {
        _maximumMessageSize = maximumMessageSize;
        if (maximumMessageSize == 0L)
        {
            _notificationChecks.remove(NotificationCheck.MESSAGE_SIZE_ALERT);
        }
        else
        {
            _notificationChecks.add(NotificationCheck.MESSAGE_SIZE_ALERT);
        }
    }

    public Set<NotificationCheck> getNotificationChecks()
    {
        return _notificationChecks;
    }

    public ManagedObject getManagedObject()
    {
        return _managedObject;
    }

    private final class QueueEntryListener implements QueueEntry.StateChangeListener
    {
        private final QueueEntry _entry;
        private final Subscription _sub;

        public QueueEntryListener(final Subscription sub, final QueueEntry entry)
        {
            _entry = entry;
            _sub = sub;
        }

        public boolean equals(Object o)
        {
            return _entry == ((QueueEntryListener) o)._entry && _sub == ((QueueEntryListener) o)._sub;
        }

        public int hashCode()
        {
            return System.identityHashCode(_entry) ^ System.identityHashCode(_sub);
        }

        public void stateChanged(QueueEntry entry, QueueEntry.State oldSate, QueueEntry.State newState)
        {
            entry.removeStateChangeListener(this);
            deliverAsync(_sub);
        }
    }

    public List<Long> getMessagesOnTheQueue(int num)
    {
        return getMessagesOnTheQueue(num, 0);
    }

    public List<Long> getMessagesOnTheQueue(int num, int offset)
    {
        ArrayList<Long> ids = new ArrayList<Long>(num);
        QueueEntryIterator it = _entries.iterator();
        for (int i = 0; i < offset; i++)
        {
            it.advance();
        }

        for (int i = 0; i < num && !it.atTail(); i++)
        {
            it.advance();
            ids.add(it.getNode().getMessage().getMessageId());
        }
        return ids;
    }

    public void configure(Configuration queueConfiguration)
    {
        Configurator.configure(this, queueConfiguration);
        resetNotifications();
    }
}