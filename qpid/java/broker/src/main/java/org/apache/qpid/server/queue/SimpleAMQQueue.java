/*
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
 */
package org.apache.qpid.server.queue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQSecurityException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.pool.ReferenceCountingExecutorService;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.configuration.QueueConfiguration;
import org.apache.qpid.server.configuration.plugins.AbstractConfiguration;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.QueueActor;
import org.apache.qpid.server.logging.messages.QueueMessages;
import org.apache.qpid.server.logging.subjects.QueueLogSubject;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.security.AuthorizationHolder;
import org.apache.qpid.server.subscription.AssignedSubscriptionMessageGroupManager;
import org.apache.qpid.server.subscription.DefinedGroupMessageGroupManager;
import org.apache.qpid.server.subscription.MessageGroupManager;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.subscription.SubscriptionList;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.LocalTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class SimpleAMQQueue implements AMQQueue, Subscription.StateListener, MessageGroupManager.SubscriptionResetHelper
{
    private static final Logger _logger = Logger.getLogger(SimpleAMQQueue.class);

    private static final String QPID_GROUP_HEADER_KEY = "qpid.group_header_key";
    private static final String QPID_SHARED_MSG_GROUP = "qpid.shared_msg_group";
    private static final String QPID_DEFAULT_MESSAGE_GROUP = "qpid.default-message-group";
    private static final String QPID_NO_GROUP = "qpid.no-group";
    // TODO - should make this configurable at the vhost / broker level
    private static final int DEFAULT_MAX_GROUPS = 255;

    private final VirtualHost _virtualHost;

    private final AMQShortString _name;

    /** null means shared */
    private final AMQShortString _owner;

    private AuthorizationHolder _authorizationHolder;

    private boolean _exclusive = false;
    private AMQSessionModel _exclusiveOwner;


    private final boolean _durable;

    /** If true, this queue is deleted when the last subscriber is removed */
    private final boolean _autoDelete;

    private Exchange _alternateExchange;


    private final QueueEntryList<QueueEntry> _entries;

    private final SubscriptionList _subscriptionList = new SubscriptionList();

    private volatile Subscription _exclusiveSubscriber;



    private final AtomicInteger _atomicQueueCount = new AtomicInteger(0);

    private final AtomicLong _atomicQueueSize = new AtomicLong(0L);

    private final AtomicInteger _activeSubscriberCount = new AtomicInteger();

    private final AtomicLong _totalMessagesReceived = new AtomicLong();

    private final AtomicLong _dequeueCount = new AtomicLong();
    private final AtomicLong _dequeueSize = new AtomicLong();
    private final AtomicLong _enqueueCount = new AtomicLong();
    private final AtomicLong _enqueueSize = new AtomicLong();
    private final AtomicLong _persistentMessageEnqueueSize = new AtomicLong();
    private final AtomicLong _persistentMessageDequeueSize = new AtomicLong();
    private final AtomicLong _persistentMessageEnqueueCount = new AtomicLong();
    private final AtomicLong _persistentMessageDequeueCount = new AtomicLong();
    private final AtomicInteger _counsumerCountHigh = new AtomicInteger(0);
    private final AtomicLong _msgTxnEnqueues = new AtomicLong(0);
    private final AtomicLong _byteTxnEnqueues = new AtomicLong(0);
    private final AtomicLong _msgTxnDequeues = new AtomicLong(0);
    private final AtomicLong _byteTxnDequeues = new AtomicLong(0);
    private final AtomicLong _unackedMsgCount = new AtomicLong(0);
    private final AtomicLong _unackedMsgCountHigh = new AtomicLong(0);
    private final AtomicLong _unackedMsgBytes = new AtomicLong();

    private final AtomicInteger _bindingCountHigh = new AtomicInteger();

    /** max allowed size(KB) of a single message */
    private long _maximumMessageSize;

    /** max allowed number of messages on a queue. */
    private long _maximumMessageCount;

    /** max queue depth for the queue */
    private long _maximumQueueDepth;

    /** maximum message age before alerts occur */
    private long _maximumMessageAge;

    /** the minimum interval between sending out consecutive alerts of the same type */
    private long _minimumAlertRepeatGap;

    private long _capacity;

    private long _flowResumeCapacity;

    private final Set<NotificationCheck> _notificationChecks = EnumSet.noneOf(NotificationCheck.class);


    static final int MAX_ASYNC_DELIVERIES = 80;


    private final AtomicLong _stateChangeCount = new AtomicLong(Long.MIN_VALUE);

    private final Executor _asyncDelivery;
    private AtomicInteger _deliveredMessages = new AtomicInteger();
    private AtomicBoolean _stopped = new AtomicBoolean(false);

    private final Set<AMQSessionModel> _blockedChannels = new ConcurrentSkipListSet<AMQSessionModel>();

    private final AtomicBoolean _deleted = new AtomicBoolean(false);
    private final List<Task> _deleteTaskList = new CopyOnWriteArrayList<Task>();


    private LogSubject _logSubject;
    private LogActor _logActor;

    private static final String SUB_FLUSH_RUNNER = "SUB_FLUSH_RUNNER";
    private boolean _nolocal;

    private final AtomicBoolean _overfull = new AtomicBoolean(false);
    private boolean _deleteOnNoConsumers;
    private final CopyOnWriteArrayList<Binding> _bindings = new CopyOnWriteArrayList<Binding>();
    private UUID _id;
    private final Map<String, Object> _arguments;

    //TODO : persist creation time
    private long _createTime = System.currentTimeMillis();
    private AbstractConfiguration _queueConfiguration;

    /** the maximum delivery count for each message on this queue or 0 if maximum delivery count is not to be enforced. */
    private int _maximumDeliveryCount;
    private final MessageGroupManager _messageGroupManager;

    private final Collection<SubscriptionRegistrationListener> _subscriptionListeners =
            new ArrayList<SubscriptionRegistrationListener>();

    private AMQQueue.NotificationListener _notificationListener;
    private final long[] _lastNotificationTimes = new long[NotificationCheck.values().length];

    protected SimpleAMQQueue(UUID id, AMQShortString name, boolean durable, AMQShortString owner, boolean autoDelete, boolean exclusive, VirtualHost virtualHost, Map<String,Object> arguments)
    {
        this(id, name, durable, owner, autoDelete, exclusive,virtualHost, new SimpleQueueEntryList.Factory(), arguments);
    }

    public SimpleAMQQueue(UUID id, String queueName, boolean durable, String owner, boolean autoDelete, boolean exclusive, VirtualHost virtualHost, Map<String, Object> arguments)
    {
        this(id, queueName, durable, owner, autoDelete, exclusive, virtualHost, new SimpleQueueEntryList.Factory(), arguments);
    }

    public SimpleAMQQueue(UUID id, String queueName, boolean durable, String owner, boolean autoDelete, boolean exclusive, VirtualHost virtualHost, QueueEntryListFactory entryListFactory, Map<String, Object> arguments)
    {
        this(id, queueName == null ? null : new AMQShortString(queueName), durable, owner == null ? null : new AMQShortString(owner), autoDelete, exclusive, virtualHost, entryListFactory, arguments);
    }

    protected SimpleAMQQueue(UUID id,
                             AMQShortString name,
                             boolean durable,
                             AMQShortString owner,
                             boolean autoDelete,
                             boolean exclusive,
                             VirtualHost virtualHost,
                             QueueEntryListFactory entryListFactory, Map<String,Object> arguments)
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
        _exclusive = exclusive;
        _virtualHost = virtualHost;
        _entries = entryListFactory.createQueueEntryList(this);
        _arguments = arguments == null ? new HashMap<String, Object>() : new HashMap<String, Object>(arguments);

        _id = id;
        _asyncDelivery = ReferenceCountingExecutorService.getInstance().acquireExecutorService();

        _logSubject = new QueueLogSubject(this);
        _logActor = new QueueActor(this, CurrentActor.get().getRootMessageLogger());

        // Log the creation of this Queue.
        // The priorities display is toggled on if we set priorities > 0
        CurrentActor.get().message(_logSubject,
                                   QueueMessages.CREATED(String.valueOf(_owner),
                                                         _entries.getPriorities(),
                                                         _owner != null,
                                                         autoDelete,
                                                         durable, !durable,
                                                         _entries.getPriorities() > 0));

        if(arguments != null && arguments.containsKey(QPID_GROUP_HEADER_KEY))
        {
            if(arguments.containsKey(QPID_SHARED_MSG_GROUP) && String.valueOf(arguments.get(QPID_SHARED_MSG_GROUP)).equals("1"))
            {
                Object defaultGroup = arguments.get(QPID_DEFAULT_MESSAGE_GROUP);
                _messageGroupManager =
                        new DefinedGroupMessageGroupManager(String.valueOf(arguments.get(QPID_GROUP_HEADER_KEY)),
                                defaultGroup == null ? QPID_NO_GROUP : defaultGroup.toString(),
                                this);
            }
            else
            {
                _messageGroupManager = new AssignedSubscriptionMessageGroupManager(String.valueOf(arguments.get(QPID_GROUP_HEADER_KEY)), DEFAULT_MAX_GROUPS);
            }
        }
        else
        {
            _messageGroupManager = null;
        }

        resetNotifications();

    }

    public void resetNotifications()
    {
        // This ensure that the notification checks for the configured alerts are created.
        setMaximumMessageAge(_maximumMessageAge);
        setMaximumMessageCount(_maximumMessageCount);
        setMaximumMessageSize(_maximumMessageSize);
        setMaximumQueueDepth(_maximumQueueDepth);
    }

    // ------ Getters and Setters

    public void execute(Runnable runnable)
    {
        try
        {
            _asyncDelivery.execute(runnable);
        }
        catch (RejectedExecutionException ree)
        {
            if (_stopped.get())
            {
                // Ignore - SubFlusherRunner or QueueRunner submitted execution as queue was being stopped.
            }
            else
            {
                _logger.error("Unexpected rejected execution", ree);
                throw ree;
            }
        }
    }

    public AMQShortString getNameShortString()
    {
        return _name;
    }

    public void setNoLocal(boolean nolocal)
    {
        _nolocal = nolocal;
    }

    public UUID getId()
    {
        return _id;
    }

    public boolean isDurable()
    {
        return _durable;
    }

    public boolean isExclusive()
    {
        return _exclusive;
    }

    public void setExclusive(boolean exclusive)
    {
        _exclusive = exclusive;
    }

    public Exchange getAlternateExchange()
    {
        return _alternateExchange;
    }

    public void setAlternateExchange(Exchange exchange)
    {
        if(_alternateExchange != null)
        {
            _alternateExchange.removeReference(this);
        }
        if(exchange != null)
        {
            exchange.addReference(this);
        }
        _alternateExchange = exchange;
    }

    /**
     * Arguments used to create this queue.  The caller is assured
     * that null will never be returned.
     */
    public Map<String, Object> getArguments()
    {
        return _arguments;
    }

    public boolean isAutoDelete()
    {
        return _autoDelete;
    }

    public AMQShortString getOwner()
    {
        return _owner;
    }

    public AuthorizationHolder getAuthorizationHolder()
    {
        return _authorizationHolder;
    }

    public void setAuthorizationHolder(final AuthorizationHolder authorizationHolder)
    {
        _authorizationHolder = authorizationHolder;
    }


    public VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    public String getName()
    {
        return getNameShortString().toString();
    }

    // ------ Manage Subscriptions

    public synchronized void registerSubscription(final Subscription subscription, final boolean exclusive)
            throws AMQSecurityException, ExistingExclusiveSubscription, ExistingSubscriptionPreventsExclusive
    {
        // Access control
        if (!getVirtualHost().getSecurityManager().authoriseConsume(this))
        {
            throw new AMQSecurityException("Permission denied");
        }


        if (hasExclusiveSubscriber())
        {
            throw new ExistingExclusiveSubscription();
        }

        if (exclusive && !subscription.isTransient())
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

        if(subscription.isActive())
        {
            _activeSubscriberCount.incrementAndGet();
        }
        subscription.setStateListener(this);
        subscription.setQueueContext(new QueueContext(_entries.getHead()));

        if (!isDeleted())
        {
            subscription.setQueue(this, exclusive);
            if(_nolocal)
            {
                subscription.setNoLocal(_nolocal);
            }

            synchronized (_subscriptionListeners)
            {
                for(SubscriptionRegistrationListener listener : _subscriptionListeners)
                {
                    listener.subscriptionRegistered(this, subscription);
                }
            }

            _subscriptionList.add(subscription);

            //Increment consumerCountHigh if necessary. (un)registerSubscription are both
            //synchronized methods so we don't need additional synchronization here
            if(_counsumerCountHigh.get() < getConsumerCount())
            {
                _counsumerCountHigh.incrementAndGet();
            }

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
            subscription.setQueueContext(null);

            if(_messageGroupManager != null)
            {
                resetSubPointersForGroups(subscription, true);
            }

            synchronized (_subscriptionListeners)
            {
                for(SubscriptionRegistrationListener listener : _subscriptionListeners)
                {
                    listener.subscriptionUnregistered(this, subscription);
                }
            }

            // auto-delete queues must be deleted if there are no remaining subscribers

            if (_autoDelete && getDeleteOnNoConsumers() && !subscription.isTransient() && getConsumerCount() == 0  )
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

    public Collection<Subscription> getConsumers()
    {
        List<Subscription> consumers = new ArrayList<Subscription>();
        SubscriptionList.SubscriptionNodeIterator iter = _subscriptionList.iterator();
        while(iter.advance())
        {
            consumers.add(iter.getNode().getSubscription());
        }
        return consumers;

    }

    public void addSubscriptionRegistrationListener(final SubscriptionRegistrationListener listener)
    {
        synchronized (_subscriptionListeners)
        {
            _subscriptionListeners.add(listener);
        }
    }

    public void removeSubscriptionRegistrationListener(final SubscriptionRegistrationListener listener)
    {
        synchronized (_subscriptionListeners)
        {
            _subscriptionListeners.remove(listener);
        }
    }

    public void resetSubPointersForGroups(Subscription subscription, boolean clearAssignments)
    {
        QueueEntry entry = _messageGroupManager.findEarliestAssignedAvailableEntry(subscription);
        if(clearAssignments)
        {
            _messageGroupManager.clearAssignments(subscription);
        }

        if(entry != null)
        {
            SubscriptionList.SubscriptionNodeIterator subscriberIter = _subscriptionList.iterator();
            // iterate over all the subscribers, and if they are in advance of this queue entry then move them backwards
            while (subscriberIter.advance())
            {
                Subscription sub = subscriberIter.getNode().getSubscription();

                // we don't make browsers send the same stuff twice
                if (sub.seesRequeues())
                {
                    updateSubRequeueEntry(sub, entry);
                }
            }

            deliverAsync();

        }
    }

    public boolean getDeleteOnNoConsumers()
    {
        return _deleteOnNoConsumers;
    }

    public void setDeleteOnNoConsumers(boolean b)
    {
        _deleteOnNoConsumers = b;
    }

    public void addBinding(final Binding binding)
    {
        _bindings.add(binding);
        int bindingCount = _bindings.size();
        int bindingCountHigh;
        while(bindingCount > (bindingCountHigh = _bindingCountHigh.get()))
        {
            if(_bindingCountHigh.compareAndSet(bindingCountHigh, bindingCount))
            {
                break;
            }
        }
    }

    public int getBindingCountHigh()
    {
        return _bindingCountHigh.get();
    }

    public void removeBinding(final Binding binding)
    {
        _bindings.remove(binding);
    }

    public List<Binding> getBindings()
    {
        return Collections.unmodifiableList(_bindings);
    }

    public int getBindingCount()
    {
        return getBindings().size();
    }

    public LogSubject getLogSubject()
    {
        return _logSubject;
    }

    // ------ Enqueue / Dequeue
    public void enqueue(ServerMessage message) throws AMQException
    {
        enqueue(message, null);
    }

    public void enqueue(ServerMessage message, PostEnqueueAction action) throws AMQException
    {
        enqueue(message, false, action);
    }

    public void enqueue(ServerMessage message, boolean transactional, PostEnqueueAction action) throws AMQException
    {

        if(transactional)
        {
            incrementTxnEnqueueStats(message);
        }
        incrementQueueCount();
        incrementQueueSize(message);

        _totalMessagesReceived.incrementAndGet();


        QueueEntry entry;
        final Subscription exclusiveSub = _exclusiveSubscriber;
        entry = _entries.add(message);

        if(action != null || (exclusiveSub == null  && _queueRunner.isIdle()))
        {
            /*

            iterate over subscriptions and if any is at the end of the queue and can deliver this message, then deliver the message

             */
            SubscriptionList.SubscriptionNode node = _subscriptionList.getMarkedNode();
            SubscriptionList.SubscriptionNode nextNode = node.findNext();
            if (nextNode == null)
            {
                nextNode = _subscriptionList.getHead().findNext();
            }
            while (nextNode != null)
            {
                if (_subscriptionList.updateMarkedNode(node, nextNode))
                {
                    break;
                }
                else
                {
                    node = _subscriptionList.getMarkedNode();
                    nextNode = node.findNext();
                    if (nextNode == null)
                    {
                        nextNode = _subscriptionList.getHead().findNext();
                    }
                }
            }

            // always do one extra loop after we believe we've finished
            // this catches the case where we *just* miss an update
            int loops = 2;

            while (entry.isAvailable() && loops != 0)
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
                nextNode = nextNode.findNext();

            }
        }


        if (entry.isAvailable())
        {
            checkSubscriptionsNotAheadOfDelivery(entry);

            if (exclusiveSub != null)
            {
                deliverAsync(exclusiveSub);
            }
            else
            {
                deliverAsync();
           }
        }

        checkForNotification(entry.getMessage());

        if(action != null)
        {
            action.onEnqueue(entry);
        }

    }

    private void deliverToSubscription(final Subscription sub, final QueueEntry entry)
            throws AMQException
    {

        if(sub.trySendLock())
        {
            try
            {
                if (!sub.isSuspended()
                    && subscriptionReadyAndHasInterest(sub, entry)
                    && mightAssign(sub, entry)
                    && !sub.wouldSuspend(entry))
                {
                    if (sub.acquires() && !(assign(sub, entry) && entry.acquire(sub)))
                    {
                        // restore credit here that would have been taken away by wouldSuspend since we didn't manage
                        // to acquire the entry for this subscription
                        sub.restoreCredit(entry);
                    }
                    else
                    {
                        deliverMessage(sub, entry, false);
                    }
                }
            }
            finally
            {
                sub.releaseSendLock();
            }
        }
    }

    private boolean assign(final Subscription sub, final QueueEntry entry)
    {
        return _messageGroupManager == null || _messageGroupManager.acceptMessage(sub, entry);
    }


    private boolean mightAssign(final Subscription sub, final QueueEntry entry)
    {
        if(_messageGroupManager == null || !sub.acquires())
        {
            return true;
        }
        Subscription assigned = _messageGroupManager.getAssignedSubscription(entry);
        return (assigned == null) || (assigned == sub);
    }

    protected void checkSubscriptionsNotAheadOfDelivery(final QueueEntry entry)
    {
        // This method is only required for queues which mess with ordering
        // Simple Queues don't :-)
    }

    private void incrementQueueSize(final ServerMessage message)
    {
        long size = message.getSize();
        getAtomicQueueSize().addAndGet(size);
        _enqueueCount.incrementAndGet();
        _enqueueSize.addAndGet(size);
        if(message.isPersistent() && isDurable())
        {
            _persistentMessageEnqueueSize.addAndGet(size);
            _persistentMessageEnqueueCount.incrementAndGet();
        }
    }

    public long getTotalDequeueCount()
    {
        return _dequeueCount.get();
    }

    public long getTotalEnqueueCount()
    {
        return _enqueueCount.get();
    }

    private void incrementQueueCount()
    {
        getAtomicQueueCount().incrementAndGet();
    }

    private void incrementTxnEnqueueStats(final ServerMessage message)
    {
        _msgTxnEnqueues.incrementAndGet();
        _byteTxnEnqueues.addAndGet(message.getSize());
    }

    private void incrementTxnDequeueStats(QueueEntry entry)
    {
        _msgTxnDequeues.incrementAndGet();
        _byteTxnDequeues.addAndGet(entry.getSize());
    }

    private void deliverMessage(final Subscription sub, final QueueEntry entry, boolean batch)
            throws AMQException
    {
        setLastSeenEntry(sub, entry);

        _deliveredMessages.incrementAndGet();
        incrementUnackedMsgCount(entry);

        sub.send(entry, batch);
    }

    private boolean subscriptionReadyAndHasInterest(final Subscription sub, final QueueEntry entry) throws AMQException
    {
        return sub.hasInterest(entry) && (getNextAvailableEntry(sub) == entry);
    }


    private void setLastSeenEntry(final Subscription sub, final QueueEntry entry)
    {
        QueueContext subContext = (QueueContext) sub.getQueueContext();
        if (subContext != null)
        {
            QueueEntry releasedEntry = subContext.getReleasedEntry();

            QueueContext._lastSeenUpdater.set(subContext, entry);
            if(releasedEntry == entry)
            {
               QueueContext._releasedUpdater.compareAndSet(subContext, releasedEntry, null);
            }
        }
    }

    private void updateSubRequeueEntry(final Subscription sub, final QueueEntry entry)
    {

        QueueContext subContext = (QueueContext) sub.getQueueContext();
        if(subContext != null)
        {
            QueueEntry oldEntry;

            while((oldEntry  = subContext.getReleasedEntry()) == null || oldEntry.compareTo(entry) > 0)
            {
                if(QueueContext._releasedUpdater.compareAndSet(subContext, oldEntry, entry))
                {
                    break;
                }
            }
        }
    }

    public void requeue(QueueEntry entry)
    {
        SubscriptionList.SubscriptionNodeIterator subscriberIter = _subscriptionList.iterator();
        // iterate over all the subscribers, and if they are in advance of this queue entry then move them backwards
        while (subscriberIter.advance() && entry.isAvailable())
        {
            Subscription sub = subscriberIter.getNode().getSubscription();

            // we don't make browsers send the same stuff twice
            if (sub.seesRequeues())
            {
                updateSubRequeueEntry(sub, entry);
            }
        }

        deliverAsync();

    }

    public void dequeue(QueueEntry entry, Subscription sub)
    {
        decrementQueueCount();
        decrementQueueSize(entry);
        if (entry.acquiredBySubscription())
        {
            _deliveredMessages.decrementAndGet();
        }

        if(sub != null && sub.isSessionTransactional())
        {
            incrementTxnDequeueStats(entry);
        }

        checkCapacity();

    }

    private void decrementQueueSize(final QueueEntry entry)
    {
        final ServerMessage message = entry.getMessage();
        long size = message.getSize();
        getAtomicQueueSize().addAndGet(-size);
        _dequeueSize.addAndGet(size);
        if(message.isPersistent() && isDurable())
        {
            _persistentMessageDequeueSize.addAndGet(size);
            _persistentMessageDequeueCount.incrementAndGet();
        }
    }

    void decrementQueueCount()
    {
        getAtomicQueueCount().decrementAndGet();
        _dequeueCount.incrementAndGet();
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
                deliverMessage(subscription, entry, false);
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

    public int getConsumerCountHigh()
    {
        return _counsumerCountHigh.get();
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
            if (node != null && !node.isDispensed())
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
        return _name.compareTo(o.getNameShortString());
    }

    public AtomicInteger getAtomicQueueCount()
    {
        return _atomicQueueCount;
    }

    public AtomicLong getAtomicQueueSize()
    {
        return _atomicQueueSize;
    }

    public boolean hasExclusiveSubscriber()
    {
        return _exclusiveSubscriber != null;
    }

    private void setExclusiveSubscriber(Subscription exclusiveSubscriber)
    {
        _exclusiveSubscriber = exclusiveSubscriber;
    }

    long getStateChangeCount()
    {
        return _stateChangeCount.get();
    }

    /** Used to track bindings to exchanges so that on deletion they can easily be cancelled. */
    protected QueueEntryList getEntries()
    {
        return _entries;
    }

    protected SubscriptionList getSubscriptionList()
    {
        return _subscriptionList;
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
                final long messageId = entry.getMessage().getMessageNumber();
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
                _complete = entry.getMessage().getMessageNumber() == messageId;
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
            if (!node.isDispensed() && filter.accept(node))
            {
                entryList.add(node);
            }
        }
        return entryList;

    }

    public void visit(final QueueEntryVisitor visitor)
    {
        QueueEntryIterator queueListIterator = _entries.iterator();

        while(queueListIterator.advance())
        {
            QueueEntry node = queueListIterator.getNode();

            if(!node.isDispensed())
            {
                if(visitor.visit(node))
                {
                    break;
                }
            }
        }
    }

    /**
     * Returns a list of QueEntries from a given range of queue positions, eg messages 5 to 10 on the queue.
     *
     * The 'queue position' index starts from 1. Using 0 in 'from' will be ignored and continue from 1.
     * Using 0 in the 'to' field will return an empty list regardless of the 'from' value.
     * @param fromPosition
     * @param toPosition
     * @return
     */
    public List<QueueEntry> getMessagesRangeOnTheQueue(final long fromPosition, final long toPosition)
    {
        return getMessagesOnTheQueue(new QueueEntryFilter()
                                        {
                                            private long position = 0;

                                            public boolean accept(QueueEntry entry)
                                            {
                                                position++;
                                                return (position >= fromPosition) && (position <= toPosition);
                                            }

                                            public boolean filterComplete()
                                            {
                                                return position >= toPosition;
                                            }
                                        });

    }

    public void purge(final long request) throws AMQException
    {
        clear(request);
    }

    public long getCreateTime()
    {
        return _createTime;
    }

    // ------ Management functions

    // TODO - now only used by the tests
    public void deleteMessageFromTop()
    {
        QueueEntryIterator queueListIterator = _entries.iterator();
        boolean noDeletes = true;

        while (noDeletes && queueListIterator.advance())
        {
            QueueEntry node = queueListIterator.getNode();
            if (node.acquire())
            {
                dequeueEntry(node);
                noDeletes = false;
            }

        }
    }

    public long clearQueue() throws AMQException
    {
        return clear(0l);
    }

    private long clear(final long request) throws AMQSecurityException
    {
        //Perform ACLs
        if (!getVirtualHost().getSecurityManager().authorisePurge(this))
        {
            throw new AMQSecurityException("Permission denied: queue " + getName());
        }

        QueueEntryIterator queueListIterator = _entries.iterator();
        long count = 0;

        ServerTransaction txn = new LocalTransaction(getVirtualHost().getMessageStore());

        while (queueListIterator.advance())
        {
            QueueEntry node = queueListIterator.getNode();
            if (node.acquire())
            {
                dequeueEntry(node, txn);
                if(++count == request)
                {
                    break;
                }
            }

        }

        txn.commit();

        return count;
    }

    private void dequeueEntry(final QueueEntry node)
    {
        ServerTransaction txn = new AutoCommitTransaction(getVirtualHost().getMessageStore());
        dequeueEntry(node, txn);
    }

    private void dequeueEntry(final QueueEntry node, ServerTransaction txn)
    {
        txn.dequeue(this, node.getMessage(),
                    new ServerTransaction.Action()
                    {

                        public void postCommit()
                        {
                            node.discard();
                        }

                        public void onRollback()
                        {

                        }
                    });
    }

    public void addQueueDeleteTask(final Task task)
    {
        _deleteTaskList.add(task);
    }

    public void removeQueueDeleteTask(final Task task)
    {
        _deleteTaskList.remove(task);
    }

    // TODO list all thrown exceptions
    public int delete() throws AMQSecurityException, AMQException
    {
        // Check access
        if (!_virtualHost.getSecurityManager().authoriseDelete(this))
        {
            throw new AMQSecurityException("Permission denied: " + getName());
        }

        if (!_deleted.getAndSet(true))
        {

            for (Binding b : getBindings())
            {
                _virtualHost.getBindingFactory().removeBinding(b);
            }

            SubscriptionList.SubscriptionNodeIterator subscriptionIter = _subscriptionList.iterator();

            while (subscriptionIter.advance())
            {
                Subscription s = subscriptionIter.getNode().getSubscription();
                if (s != null)
                {
                    s.queueDeleted(this);
                }
            }

            _virtualHost.getQueueRegistry().unregisterQueue(_name);

            List<QueueEntry> entries = getMessagesOnTheQueue(new QueueEntryFilter()
            {

                public boolean accept(QueueEntry entry)
                {
                    return entry.acquire();
                }

                public boolean filterComplete()
                {
                    return false;
                }
            });

            ServerTransaction txn = new LocalTransaction(getVirtualHost().getMessageStore());

            if(_alternateExchange != null)
            {

                InboundMessageAdapter adapter = new InboundMessageAdapter();
                for(final QueueEntry entry : entries)
                {
                    adapter.setEntry(entry);
                    List<? extends BaseQueue> queues = _alternateExchange.route(adapter);
                    if((queues == null || queues.size() == 0) && _alternateExchange.getAlternateExchange() != null)
                    {
                        queues = _alternateExchange.getAlternateExchange().route(adapter);
                    }

                    final ServerMessage message = entry.getMessage();
                    if(queues != null && queues.size() != 0)
                    {
                        final List<? extends BaseQueue> rerouteQueues = queues;
                        txn.enqueue(rerouteQueues, entry.getMessage(),
                                    new ServerTransaction.Action()
                                    {

                                        public void postCommit()
                                        {
                                            try
                                            {
                                                for(BaseQueue queue : rerouteQueues)
                                                {
                                                    queue.enqueue(message);
                                                }
                                            }
                                            catch (AMQException e)
                                            {
                                                throw new RuntimeException(e);
                                            }

                                        }

                                        public void onRollback()
                                        {

                                        }
                                    });
                        txn.dequeue(this, entry.getMessage(),
                                    new ServerTransaction.Action()
                                    {

                                        public void postCommit()
                                        {
                                            entry.discard();
                                        }

                                        public void onRollback()
                                        {
                                        }
                                    });
                    }

                }

                _alternateExchange.removeReference(this);
            }
            else
            {
                // TODO log discard

                for(final QueueEntry entry : entries)
                {
                    final ServerMessage message = entry.getMessage();
                    if(message != null)
                    {
                        txn.dequeue(this, message,
                                    new ServerTransaction.Action()
                                    {

                                        public void postCommit()
                                        {
                                            entry.discard();
                                        }

                                        public void onRollback()
                                        {
                                        }
                                    });
                    }
                }
            }

            txn.commit();

            for (Task task : _deleteTaskList)
            {
                task.doTask(this);
            }

            _deleteTaskList.clear();
            stop();

            //Log Queue Deletion
            CurrentActor.get().message(_logSubject, QueueMessages.DELETED());

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

    public void checkCapacity(AMQSessionModel channel)
    {
        if(_capacity != 0l)
        {
            if(_atomicQueueSize.get() > _capacity)
            {
                _overfull.set(true);
                //Overfull log message
                _logActor.message(_logSubject, QueueMessages.OVERFULL(_atomicQueueSize.get(), _capacity));

                _blockedChannels.add(channel);

                channel.block(this);

                if(_atomicQueueSize.get() <= _flowResumeCapacity)
                {

                    //Underfull log message
                    _logActor.message(_logSubject, QueueMessages.UNDERFULL(_atomicQueueSize.get(), _flowResumeCapacity));

                   channel.unblock(this);
                   _blockedChannels.remove(channel);

                }

            }



        }
    }

    private void checkCapacity()
    {
        if(_capacity != 0L)
        {
            if(_overfull.get() && _atomicQueueSize.get() <= _flowResumeCapacity)
            {
                if(_overfull.compareAndSet(true,false))
                {//Underfull log message
                    _logActor.message(_logSubject, QueueMessages.UNDERFULL(_atomicQueueSize.get(), _flowResumeCapacity));
                }

                for(final AMQSessionModel blockedChannel : _blockedChannels)
                {
                    blockedChannel.unblock(this);
                    _blockedChannels.remove(blockedChannel);
                }
            }
        }
    }

    private QueueRunner _queueRunner = new QueueRunner(this);

    public void deliverAsync()
    {
        _stateChangeCount.incrementAndGet();

        _queueRunner.execute(_asyncDelivery);

    }

    public void deliverAsync(Subscription sub)
    {
        if(_exclusiveSubscriber == null)
        {
            deliverAsync();
        }
        else
        {
            SubFlushRunner flusher = (SubFlushRunner) sub.get(SUB_FLUSH_RUNNER);
            if(flusher == null)
            {
                flusher = new SubFlushRunner(sub);
                sub.set(SUB_FLUSH_RUNNER, flusher);
            }
            flusher.execute(_asyncDelivery);
        }

    }

    public void flushSubscription(Subscription sub) throws AMQException
    {
        // Access control
        if (!getVirtualHost().getSecurityManager().authoriseConsume(this))
        {
            throw new AMQSecurityException("Permission denied: " + getName());
        }
        flushSubscription(sub, Long.MAX_VALUE);
    }

    public boolean flushSubscription(Subscription sub, long iterations) throws AMQException
    {
        boolean atTail = false;
        final boolean keepSendLockHeld = iterations <=  SimpleAMQQueue.MAX_ASYNC_DELIVERIES;
        boolean queueEmpty = false;

        try
        {
            if(keepSendLockHeld)
            {
                sub.getSendLock();
            }
            while (!sub.isSuspended() && !atTail && iterations != 0)
            {
                try
                {
                    if(!keepSendLockHeld)
                    {
                        sub.getSendLock();
                    }

                    atTail = attemptDelivery(sub, true);
                    if (atTail && getNextAvailableEntry(sub) == null)
                    {
                        queueEmpty = true;
                    }
                    else if (!atTail)
                    {
                        iterations--;
                    }
                }
                finally
                {
                    if(!keepSendLockHeld)
                    {
                        sub.releaseSendLock();
                    }
                }
            }
        }
        finally
        {
            if(keepSendLockHeld)
            {
                sub.releaseSendLock();
            }
            if(queueEmpty)
            {
                sub.queueEmpty();
            }

            sub.flushBatched();

        }


        // if there's (potentially) more than one subscription the others will potentially not have been advanced to the
        // next entry they are interested in yet.  This would lead to holding on to references to expired messages, etc
        // which would give us memory "leak".

        if (!hasExclusiveSubscriber())
        {
            advanceAllSubscriptions();
        }
        return atTail;
    }

    /**
     * Attempt delivery for the given subscription.
     *
     * Looks up the next node for the subscription and attempts to deliver it.
     *
     *
     * @param sub
     * @param batch
     * @return true if we have completed all possible deliveries for this sub.
     * @throws AMQException
     */
    private boolean attemptDelivery(Subscription sub, boolean batch) throws AMQException
    {
        boolean atTail = false;

        boolean subActive = sub.isActive() && !sub.isSuspended();
        if (subActive)
        {

            QueueEntry node  = getNextAvailableEntry(sub);

            if (node != null && node.isAvailable())
            {
                if (sub.hasInterest(node) && mightAssign(sub, node))
                {
                    if (!sub.wouldSuspend(node))
                    {
                        if (sub.acquires() && !(assign(sub, node) && node.acquire(sub)))
                        {
                            // restore credit here that would have been taken away by wouldSuspend since we didn't manage
                            // to acquire the entry for this subscription
                            sub.restoreCredit(node);
                        }
                        else
                        {
                            deliverMessage(sub, node, batch);
                        }

                    }
                    else // Not enough Credit for message and wouldSuspend
                    {
                        //QPID-1187 - Treat the subscription as suspended for this message
                        // and wait for the message to be removed to continue delivery.
                        subActive = false;
                        node.addStateChangeListener(new QueueEntryListener(sub));
                    }
                }

            }
            atTail = (node == null) || (_entries.next(node) == null);
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
            if(sub.acquires())
            {
                getNextAvailableEntry(sub);
            }
            else
            {
                // TODO
            }
        }
    }

    private QueueEntry getNextAvailableEntry(final Subscription sub)
            throws AMQException
    {
        QueueContext context = (QueueContext) sub.getQueueContext();
        if(context != null)
        {
            QueueEntry lastSeen = context.getLastSeenEntry();
            QueueEntry releasedNode = context.getReleasedEntry();

            QueueEntry node = (releasedNode != null && lastSeen.compareTo(releasedNode)>=0) ? releasedNode : _entries.next(lastSeen);

            boolean expired = false;
            while (node != null && (!node.isAvailable() || (expired = node.expired()) || !sub.hasInterest(node) ||
                                    !mightAssign(sub,node)))
            {
                if (expired)
                {
                    expired = false;
                    if (node.acquire())
                    {
                        dequeueEntry(node);
                    }
                }

                if(QueueContext._lastSeenUpdater.compareAndSet(context, lastSeen, node))
                {
                    QueueContext._releasedUpdater.compareAndSet(context, releasedNode, null);
                }

                lastSeen = context.getLastSeenEntry();
                releasedNode = context.getReleasedEntry();
                node = (releasedNode != null && lastSeen.compareTo(releasedNode)>0) ? releasedNode : _entries.next(lastSeen);
            }
            return node;
        }
        else
        {
            return null;
        }
    }

    public boolean isEntryAheadOfSubscription(QueueEntry entry, Subscription sub)
    {
        QueueContext context = (QueueContext) sub.getQueueContext();
        if(context != null)
        {
            QueueEntry releasedNode = context.getReleasedEntry();
            return releasedNode != null && releasedNode.compareTo(entry) < 0;
        }
        else
        {
            return false;
        }
    }

    /**
     * Used by queue Runners to asynchronously deliver messages to consumers.
     *
     * A queue Runner is started whenever a state change occurs, e.g when a new
     * message arrives on the queue and cannot be immediately delivered to a
     * subscription (i.e. asynchronous delivery is required). Unless there are
     * SubFlushRunners operating (due to subscriptions unsuspending) which are
     * capable of accepting/delivering all messages then these messages would
     * otherwise remain on the queue.
     *
     * processQueue should be running while there are messages on the queue AND
     * there are subscriptions that can deliver them. If there are no
     * subscriptions capable of delivering the remaining messages on the queue
     * then processQueue should stop to prevent spinning.
     *
     * Since processQueue is runs in a fixed size Executor, it should not run
     * indefinitely to prevent starving other tasks of CPU (e.g jobs to process
     * incoming messages may not be able to be scheduled in the thread pool
     * because all threads are working on clearing down large queues). To solve
     * this problem, after an arbitrary number of message deliveries the
     * processQueue job stops iterating, resubmits itself to the executor, and
     * ends the current instance
     *
     * @param runner the Runner to schedule
     * @throws AMQException
     */
    public long processQueue(QueueRunner runner) throws AMQException
    {
        long stateChangeCount = Long.MIN_VALUE;
        long previousStateChangeCount = Long.MIN_VALUE;
        long rVal = Long.MIN_VALUE;
        boolean deliveryIncomplete = true;

        boolean lastLoop = false;
        int iterations = MAX_ASYNC_DELIVERIES;

        final int numSubs = _subscriptionList.size();

        final int perSub = Math.max(iterations / Math.max(numSubs,1), 1);

        // For every message enqueue/requeue the we fire deliveryAsync() which
        // increases _stateChangeCount. If _sCC changes whilst we are in our loop
        // (detected by setting previousStateChangeCount to stateChangeCount in the loop body)
        // then we will continue to run for a maximum of iterations.
        // So whilst delivery/rejection is going on a processQueue thread will be running
        while (iterations != 0 && ((previousStateChangeCount != (stateChangeCount = _stateChangeCount.get())) || deliveryIncomplete))
        {
            // we want to have one extra loop after every subscription has reached the point where it cannot move
            // further, just in case the advance of one subscription in the last loop allows a different subscription to
            // move forward in the next iteration

            if (previousStateChangeCount != stateChangeCount)
            {
                //further asynchronous delivery is required since the
                //previous loop. keep going if iteration slicing allows.
                lastLoop = false;
                rVal = stateChangeCount;
            }

            previousStateChangeCount = stateChangeCount;
            boolean allSubscriptionsDone = true;
            boolean subscriptionDone;

            SubscriptionList.SubscriptionNodeIterator subscriptionIter = _subscriptionList.iterator();
            //iterate over the subscribers and try to advance their pointer
            while (subscriptionIter.advance())
            {
                Subscription sub = subscriptionIter.getNode().getSubscription();
                sub.getSendLock();

                    try
                    {
                        for(int i = 0 ; i < perSub; i++)
                        {
                            //attempt delivery. returns true if no further delivery currently possible to this sub
                            subscriptionDone = attemptDelivery(sub, true);
                            if (subscriptionDone)
                            {
                                sub.flushBatched();
                                if (lastLoop && !sub.isSuspended())
                                {
                                    sub.queueEmpty();
                                }
                                break;
                            }
                            else
                            {
                                //this subscription can accept additional deliveries, so we must
                                //keep going after this (if iteration slicing allows it)
                                allSubscriptionsDone = false;
                                lastLoop = false;
                                if(--iterations == 0)
                                {
                                    sub.flushBatched();
                                    break;
                                }
                            }

                        }

                        sub.flushBatched();
                    }
                    finally
                    {
                        sub.releaseSendLock();
                    }
            }

            if(allSubscriptionsDone && lastLoop)
            {
                //We have done an extra loop already and there are again
                //again no further delivery attempts possible, only
                //keep going if state change demands it.
                deliveryIncomplete = false;
            }
            else if(allSubscriptionsDone)
            {
                //All subscriptions reported being done, but we have to do
                //an extra loop if the iterations are not exhausted and
                //there is still any work to be done
                deliveryIncomplete = _subscriptionList.size() != 0;
                lastLoop = true;
            }
            else
            {
                //some subscriptions can still accept more messages,
                //keep going if iteration count allows.
                lastLoop = false;
                deliveryIncomplete = true;
            }

        }

        // If iterations == 0 then the limiting factor was the time-slicing rather than available messages or credit
        // therefore we should schedule this runner again (unless someone beats us to it :-) ).
        if (iterations == 0)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Rescheduling runner:" + runner);
            }
            return 0L;
        }
        return rVal;

    }

    public void checkMessageStatus() throws AMQException
    {
        QueueEntryIterator queueListIterator = _entries.iterator();

        while (queueListIterator.advance())
        {
            QueueEntry node = queueListIterator.getNode();
            // Only process nodes that are not currently deleted and not dequeued
            if (!node.isDispensed())
            {
                // If the node has exired then acquire it
                if (node.expired() && node.acquire())
                {
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("Dequeuing expired node " + node);
                    }
                    // Then dequeue it.
                    dequeueEntry(node);
                }
                else
                {
                    // There is a chance that the node could be deleted by
                    // the time the check actually occurs. So verify we
                    // can actually get the message to perform the check.
                    ServerMessage msg = node.getMessage();
                    if (msg != null)
                    {
                        checkForNotification(msg);
                    }
                }
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

    public long getCapacity()
    {
        return _capacity;
    }

    public void setCapacity(long capacity)
    {
        _capacity = capacity;
    }

    public long getFlowResumeCapacity()
    {
        return _flowResumeCapacity;
    }

    public void setFlowResumeCapacity(long flowResumeCapacity)
    {
        _flowResumeCapacity = flowResumeCapacity;

        checkCapacity();
    }

    public boolean isOverfull()
    {
        return _overfull.get();
    }

    public Set<NotificationCheck> getNotificationChecks()
    {
        return _notificationChecks;
    }

    private final class QueueEntryListener implements QueueEntry.StateChangeListener
    {

        private final Subscription _sub;

        public QueueEntryListener(final Subscription sub)
        {
            _sub = sub;
        }

        public boolean equals(Object o)
        {
            return o instanceof SimpleAMQQueue.QueueEntryListener
                    && _sub == ((QueueEntryListener) o)._sub;
        }

        public int hashCode()
        {
            return System.identityHashCode(_sub);
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
            ids.add(it.getNode().getMessage().getMessageNumber());
        }
        return ids;
    }

    public AMQSessionModel getExclusiveOwningSession()
    {
        return _exclusiveOwner;
    }

    public void setExclusiveOwningSession(AMQSessionModel exclusiveOwner)
    {
        _exclusive = true;
        _exclusiveOwner = exclusiveOwner;
    }


    public void configure(QueueConfiguration config)
    {
        if (config != null)
        {
            setMaximumMessageAge(config.getMaximumMessageAge());
            setMaximumQueueDepth(config.getMaximumQueueDepth());
            setMaximumMessageSize(config.getMaximumMessageSize());
            setMaximumMessageCount(config.getMaximumMessageCount());
            setMinimumAlertRepeatGap(config.getMinimumAlertRepeatGap());
            setMaximumDeliveryCount(config.getMaxDeliveryCount());
            _capacity = config.getCapacity();
            _flowResumeCapacity = config.getFlowResumeCapacity();
        }
    }

    public long getMessageDequeueCount()
    {
        return  _dequeueCount.get();
    }

    public long getTotalEnqueueSize()
    {
        return _enqueueSize.get();
    }

    public long getTotalDequeueSize()
    {
        return _dequeueSize.get();
    }

    public long getByteTxnEnqueues()
    {
        return _byteTxnEnqueues.get();
    }

    public long getByteTxnDequeues()
    {
        return _byteTxnDequeues.get();
    }

    public long getMsgTxnEnqueues()
    {
        return _msgTxnEnqueues.get();
    }

    public long getMsgTxnDequeues()
    {
        return _msgTxnDequeues.get();
    }

    public long getPersistentByteEnqueues()
    {
        return _persistentMessageEnqueueSize.get();
    }

    public long getPersistentByteDequeues()
    {
        return _persistentMessageDequeueSize.get();
    }

    public long getPersistentMsgEnqueues()
    {
        return _persistentMessageEnqueueCount.get();
    }

    public long getPersistentMsgDequeues()
    {
        return _persistentMessageDequeueCount.get();
    }


    @Override
    public String toString()
    {
        return String.valueOf(getNameShortString());
    }

    public long getUnackedMessageCountHigh()
    {
        return _unackedMsgCountHigh.get();
    }

    public long getUnackedMessageCount()
    {
        return _unackedMsgCount.get();
    }

    public long getUnackedMessageBytes()
    {
        return _unackedMsgBytes.get();
    }

    public void decrementUnackedMsgCount(QueueEntry queueEntry)
    {
        _unackedMsgCount.decrementAndGet();
        _unackedMsgBytes.addAndGet(-queueEntry.getSize());
    }

    private void incrementUnackedMsgCount(QueueEntry entry)
    {
        long unackedMsgCount = _unackedMsgCount.incrementAndGet();
        _unackedMsgBytes.addAndGet(entry.getSize());

        long unackedMsgCountHigh;
        while(unackedMsgCount > (unackedMsgCountHigh = _unackedMsgCountHigh.get()))
        {
            if(_unackedMsgCountHigh.compareAndSet(unackedMsgCountHigh, unackedMsgCount))
            {
                break;
            }
        }
    }

    public LogActor getLogActor()
    {
        return _logActor;
    }

    public int getMaximumDeliveryCount()
    {
        return _maximumDeliveryCount;
    }

    public void setMaximumDeliveryCount(final int maximumDeliveryCount)
    {
        _maximumDeliveryCount = maximumDeliveryCount;
    }

    /**
     * Checks if there is any notification to send to the listeners
     */
    private void checkForNotification(ServerMessage<?> msg) throws AMQException
    {
        final Set<NotificationCheck> notificationChecks = getNotificationChecks();
        final AMQQueue.NotificationListener listener = _notificationListener;

        if(listener != null && !notificationChecks.isEmpty())
        {
            final long currentTime = System.currentTimeMillis();
            final long thresholdTime = currentTime - getMinimumAlertRepeatGap();

            for (NotificationCheck check : notificationChecks)
            {
                if (check.isMessageSpecific() || (_lastNotificationTimes[check.ordinal()] < thresholdTime))
                {
                    if (check.notifyIfNecessary(msg, this, listener))
                    {
                        _lastNotificationTimes[check.ordinal()] = currentTime;
                    }
                }
            }
        }
    }

    public void setNotificationListener(AMQQueue.NotificationListener listener)
    {
        _notificationListener = listener;
    }

    @Override
    public void setDescription(String description)
    {
        if (description == null)
        {
            _arguments.remove(AMQQueueFactory.X_QPID_DESCRIPTION);
        }
        else
        {
            _arguments.put(AMQQueueFactory.X_QPID_DESCRIPTION, description);
        }
    }

    @Override
    public String getDescription()
    {
        return (String) _arguments.get(AMQQueueFactory.X_QPID_DESCRIPTION);
    }

}
