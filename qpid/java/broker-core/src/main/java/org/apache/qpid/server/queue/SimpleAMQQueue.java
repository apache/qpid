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

import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.model.ExclusivityPolicy;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.security.QpidSecurityException;
import org.apache.qpid.pool.ReferenceCountingExecutorService;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.QueueActor;
import org.apache.qpid.server.logging.messages.QueueMessages;
import org.apache.qpid.server.logging.subjects.QueueLogSubject;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.security.AuthorizationHolder;
import org.apache.qpid.server.consumer.Consumer;
import org.apache.qpid.server.consumer.ConsumerTarget;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.LocalTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.server.util.Deletable;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.server.util.StateChangeListener;
import org.apache.qpid.server.virtualhost.VirtualHost;

abstract class SimpleAMQQueue<E extends QueueEntryImpl<E,Q,L>, Q extends SimpleAMQQueue<E, Q,L>, L extends SimpleQueueEntryList<E,Q,L>> implements AMQQueue<E, Q, QueueConsumer<?,E,Q,L>>,
                                       StateChangeListener<QueueConsumer<?,E,Q,L>, QueueConsumer.State>,
                                       MessageGroupManager.ConsumerResetHelper<E,Q,L>
{

    private static final Logger _logger = Logger.getLogger(SimpleAMQQueue.class);

    public static final String SHARED_MSG_GROUP_ARG_VALUE = "1";
    private static final String QPID_NO_GROUP = "qpid.no-group";
    private static final String DEFAULT_SHARED_MESSAGE_GROUP = System.getProperty(BrokerProperties.PROPERTY_DEFAULT_SHARED_MESSAGE_GROUP, QPID_NO_GROUP);

    // TODO - should make this configurable at the vhost / broker level
    private static final int DEFAULT_MAX_GROUPS = 255;

    private final VirtualHost _virtualHost;

    private final String _name;

    /** null means shared */
    private String _description;

    private final boolean _durable;

    private Exchange _alternateExchange;


    private final L _entries;

    private final QueueConsumerList<E,Q,L> _consumerList = new QueueConsumerList<E,Q,L>();

    private volatile QueueConsumer<?,E,Q,L> _exclusiveSubscriber;



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
    private final AtomicLong _unackedMsgCount = new AtomicLong(0);
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

    private ExclusivityPolicy _exclusivityPolicy;
    private LifetimePolicy _lifetimePolicy;
    private Object _exclusiveOwner; // could be connection, session or Principal

    private final Set<NotificationCheck> _notificationChecks = EnumSet.noneOf(NotificationCheck.class);


    static final int MAX_ASYNC_DELIVERIES = 80;


    private final AtomicLong _stateChangeCount = new AtomicLong(Long.MIN_VALUE);

    private final Executor _asyncDelivery;
    private AtomicInteger _deliveredMessages = new AtomicInteger();
    private AtomicBoolean _stopped = new AtomicBoolean(false);

    private final Set<AMQSessionModel> _blockedChannels = new ConcurrentSkipListSet<AMQSessionModel>();

    private final AtomicBoolean _deleted = new AtomicBoolean(false);
    private final List<Action<? super Q>> _deleteTaskList =
            new CopyOnWriteArrayList<Action<? super Q>>();


    private LogSubject _logSubject;
    private LogActor _logActor;

    private boolean _noLocal;

    private final AtomicBoolean _overfull = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<Binding> _bindings = new CopyOnWriteArrayList<Binding>();
    private UUID _id;
    private final Map<String, Object> _arguments;

    //TODO : persist creation time
    private long _createTime = System.currentTimeMillis();

    /** the maximum delivery count for each message on this queue or 0 if maximum delivery count is not to be enforced. */
    private int _maximumDeliveryCount;
    private final MessageGroupManager<E,Q,L> _messageGroupManager;

    private final Collection<ConsumerRegistrationListener<Q>> _consumerListeners =
            new ArrayList<ConsumerRegistrationListener<Q>>();

    private AMQQueue.NotificationListener _notificationListener;
    private final long[] _lastNotificationTimes = new long[NotificationCheck.values().length];

    protected SimpleAMQQueue(VirtualHost virtualHost,
                             final AMQSessionModel<?,?> creatingSession,
                             Map<String, Object> attributes,
                             QueueEntryListFactory<E, Q, L> entryListFactory)
    {
        UUID id = MapValueConverter.getUUIDAttribute(Queue.ID, attributes);
        String name = MapValueConverter.getStringAttribute(Queue.NAME, attributes);
        boolean durable = MapValueConverter.getBooleanAttribute(Queue.DURABLE,attributes,false);


        _exclusivityPolicy = MapValueConverter.getEnumAttribute(ExclusivityPolicy.class,
                                                                Queue.EXCLUSIVE,
                                                                attributes,
                                                                ExclusivityPolicy.NONE);
        _lifetimePolicy = MapValueConverter.getEnumAttribute(LifetimePolicy.class,
                                                             Queue.LIFETIME_POLICY,
                                                             attributes,
                                                             LifetimePolicy.PERMANENT);
        if(creatingSession != null)
        {

            switch(_exclusivityPolicy)
            {

                case PRINCIPAL:
                    _exclusiveOwner = creatingSession.getConnectionModel().getAuthorizedPrincipal();
                    break;
                case CONTAINER:
                    _exclusiveOwner = creatingSession.getConnectionModel().getRemoteContainerName();
                    break;
                case CONNECTION:
                    _exclusiveOwner = creatingSession.getConnectionModel();
                    addExclusivityConstraint(creatingSession.getConnectionModel());
                    break;
                case SESSION:
                    _exclusiveOwner = creatingSession;
                    addExclusivityConstraint(creatingSession);
                    break;
                case NONE:
                case LINK:
                    // nothing to do as if link no link associated until there is a consumer associated
                    break;
                default:
                    throw new ServerScopedRuntimeException("Unknown exclusivity policy: "
                                                           + _exclusivityPolicy
                                                           + " this is a coding error inside Qpid");
            }
        }
        else if(_exclusivityPolicy == ExclusivityPolicy.PRINCIPAL)
        {
            String owner = MapValueConverter.getStringAttribute(Queue.OWNER, attributes, null);
            if(owner != null)
            {
                _exclusiveOwner = new AuthenticatedPrincipal(owner);
            }
        }
        else if(_exclusivityPolicy == ExclusivityPolicy.CONTAINER)
        {
            String owner = MapValueConverter.getStringAttribute(Queue.OWNER, attributes, null);
            if(owner != null)
            {
                _exclusiveOwner = owner;
            }
        }


        if(_lifetimePolicy == LifetimePolicy.DELETE_ON_CONNECTION_CLOSE)
        {
            if(creatingSession != null)
            {
                addLifetimeConstraint(creatingSession.getConnectionModel());
            }
            else
            {
                throw new IllegalArgumentException("Queues created with a lifetime policy of "
                                                   + _lifetimePolicy
                                                   + " must be created from a connection.");
            }
        }
        else if(_lifetimePolicy == LifetimePolicy.DELETE_ON_SESSION_END)
        {
            if(creatingSession != null)
            {
                addLifetimeConstraint(creatingSession);
            }
            else
            {
                throw new IllegalArgumentException("Queues created with a lifetime policy of "
                                                   + _lifetimePolicy
                                                   + " must be created from a connection.");
            }
        }

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
        _virtualHost = virtualHost;
        _entries = entryListFactory.createQueueEntryList((Q) this);
        final LinkedHashMap<String, Object> arguments = new LinkedHashMap<String, Object>(attributes);

        arguments.put(Queue.EXCLUSIVE, _exclusivityPolicy);
        arguments.put(Queue.LIFETIME_POLICY, _lifetimePolicy);

        _arguments = Collections.synchronizedMap(arguments);
        _description = MapValueConverter.getStringAttribute(Queue.DESCRIPTION, attributes, null);

        _noLocal = MapValueConverter.getBooleanAttribute(Queue.NO_LOCAL, attributes, false);


        _id = id;
        _asyncDelivery = ReferenceCountingExecutorService.getInstance().acquireExecutorService();

        _logSubject = new QueueLogSubject(this);
        _logActor = new QueueActor(this, CurrentActor.get().getRootMessageLogger());


        if (attributes.containsKey(Queue.ALERT_THRESHOLD_MESSAGE_AGE))
        {
            setMaximumMessageAge(MapValueConverter.getLongAttribute(Queue.ALERT_THRESHOLD_MESSAGE_AGE, attributes));
        }
        else
        {
            setMaximumMessageAge(virtualHost.getDefaultAlertThresholdMessageAge());
        }
        if (attributes.containsKey(Queue.ALERT_THRESHOLD_MESSAGE_SIZE))
        {
            setMaximumMessageSize(MapValueConverter.getLongAttribute(Queue.ALERT_THRESHOLD_MESSAGE_SIZE, attributes));
        }
        else
        {
            setMaximumMessageSize(virtualHost.getDefaultAlertThresholdMessageSize());
        }
        if (attributes.containsKey(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES))
        {
            setMaximumMessageCount(MapValueConverter.getLongAttribute(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES,
                                                                      attributes));
        }
        else
        {
            setMaximumMessageCount(virtualHost.getDefaultAlertThresholdQueueDepthMessages());
        }
        if (attributes.containsKey(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_BYTES))
        {
            setMaximumQueueDepth(MapValueConverter.getLongAttribute(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_BYTES,
                                                                    attributes));
        }
        else
        {
            setMaximumQueueDepth(virtualHost.getDefaultAlertThresholdQueueDepthBytes());
        }
        if (attributes.containsKey(Queue.ALERT_REPEAT_GAP))
        {
            setMinimumAlertRepeatGap(MapValueConverter.getLongAttribute(Queue.ALERT_REPEAT_GAP, attributes));
        }
        else
        {
            setMinimumAlertRepeatGap(virtualHost.getDefaultAlertRepeatGap());
        }
        if (attributes.containsKey(Queue.QUEUE_FLOW_CONTROL_SIZE_BYTES))
        {
            setCapacity(MapValueConverter.getLongAttribute(Queue.QUEUE_FLOW_CONTROL_SIZE_BYTES, attributes));
        }
        else
        {
            setCapacity(virtualHost.getDefaultQueueFlowControlSizeBytes());
        }
        if (attributes.containsKey(Queue.QUEUE_FLOW_RESUME_SIZE_BYTES))
        {
            setFlowResumeCapacity(MapValueConverter.getLongAttribute(Queue.QUEUE_FLOW_RESUME_SIZE_BYTES, attributes));
        }
        else
        {
            setFlowResumeCapacity(virtualHost.getDefaultQueueFlowResumeSizeBytes());
        }
        if (attributes.containsKey(Queue.MAXIMUM_DELIVERY_ATTEMPTS))
        {
            setMaximumDeliveryCount(MapValueConverter.getIntegerAttribute(Queue.MAXIMUM_DELIVERY_ATTEMPTS, attributes));
        }
        else
        {
            setMaximumDeliveryCount(virtualHost.getDefaultMaximumDeliveryAttempts());
        }

        final String ownerString;
        switch(_exclusivityPolicy)
        {
            case PRINCIPAL:
                ownerString = ((Principal) _exclusiveOwner).getName();
                break;
            case CONTAINER:
                ownerString = (String) _exclusiveOwner;
                break;
            default:
                ownerString = null;

        }

        // Log the creation of this Queue.
        // The priorities display is toggled on if we set priorities > 0
        CurrentActor.get().message(_logSubject,
                                   QueueMessages.CREATED(ownerString,
                                                         _entries.getPriorities(),
                                                         ownerString != null ,
                                                         _lifetimePolicy != LifetimePolicy.PERMANENT,
                                                         durable,
                                                         !durable,
                                                         _entries.getPriorities() > 0));

        if(attributes != null && attributes.containsKey(Queue.MESSAGE_GROUP_KEY))
        {
            if(attributes.get(Queue.MESSAGE_GROUP_SHARED_GROUPS) != null
               && (Boolean)(attributes.get(Queue.MESSAGE_GROUP_SHARED_GROUPS)))
            {
                Object defaultGroup = attributes.get(Queue.MESSAGE_GROUP_DEFAULT_GROUP);
                _messageGroupManager =
                        new DefinedGroupMessageGroupManager<E,Q,L>(String.valueOf(attributes.get(Queue.MESSAGE_GROUP_KEY)),
                                defaultGroup == null ? DEFAULT_SHARED_MESSAGE_GROUP : defaultGroup.toString(),
                                this);
            }
            else
            {
                _messageGroupManager = new AssignedConsumerMessageGroupManager<E,Q,L>(String.valueOf(attributes.get(
                        Queue.MESSAGE_GROUP_KEY)), DEFAULT_MAX_GROUPS);
            }
        }
        else
        {
            _messageGroupManager = null;
        }

        resetNotifications();

    }

    private void addLifetimeConstraint(final Deletable<? extends Deletable> lifetimeObject)
    {
        final Action<Deletable> deleteQueueTask = new Action<Deletable>()
        {
            @Override
            public void performAction(final Deletable object)
            {
                try
                {
                    getVirtualHost().removeQueue(SimpleAMQQueue.this);
                }
                catch (QpidSecurityException e)
                {
                    throw new ConnectionScopedRuntimeException("Unable to delete a queue even though the queue's " +
                                                               "lifetime was tied to an object being deleted");
                }
            }
        };

        lifetimeObject.addDeleteTask(deleteQueueTask);
        addDeleteTask(new DeleteDeleteTask(lifetimeObject, deleteQueueTask));
    }

    private void addExclusivityConstraint(final Deletable<? extends Deletable> lifetimeObject)
    {
        final ClearOwnerAction clearOwnerAction = new ClearOwnerAction(lifetimeObject);
        final DeleteDeleteTask deleteDeleteTask = new DeleteDeleteTask(lifetimeObject, clearOwnerAction);
        clearOwnerAction.setDeleteTask(deleteDeleteTask);
        lifetimeObject.addDeleteTask(clearOwnerAction);
        addDeleteTask(deleteDeleteTask);
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
            // Ignore - SubFlusherRunner or QueueRunner submitted execution as queue was being stopped.
            if(!_stopped.get())
            {
                _logger.error("Unexpected rejected execution", ree);
                throw ree;

            }

        }
    }

    public void setNoLocal(boolean nolocal)
    {
        _noLocal = nolocal;
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
        return _exclusivityPolicy != ExclusivityPolicy.NONE;
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


    @Override
    public Collection<String> getAvailableAttributes()
    {
        return new ArrayList<String>(_arguments.keySet());
    }

    @Override
    public Object getAttribute(String attrName)
    {
        return _arguments.get(attrName);
    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return _lifetimePolicy;
    }

    public String getOwner()
    {
        if(_exclusiveOwner != null)
        {
            switch(_exclusivityPolicy)
            {
                case CONTAINER:
                    return (String) _exclusiveOwner;
                case PRINCIPAL:
                    return ((Principal)_exclusiveOwner).getName();
            }
        }
        return null;
    }

    public VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    public String getName()
    {
        return _name;
    }

    // ------ Manage Consumers


    @Override
    public synchronized <T extends ConsumerTarget> QueueConsumer<T,E,Q,L> addConsumer(final T target,
                                     final FilterManager filters,
                                     final Class<? extends ServerMessage> messageClass,
                                     final String consumerName,
                                     EnumSet<Consumer.Option> optionSet)
            throws ExistingExclusiveConsumer, ExistingConsumerPreventsExclusive, QpidSecurityException,
                   ConsumerAccessRefused
    {

        // Access control
        if (!getVirtualHost().getSecurityManager().authoriseConsume(this))
        {
            throw new QpidSecurityException("Permission denied");
        }


        if (hasExclusiveConsumer())
        {
            throw new ExistingExclusiveConsumer();
        }

        switch(_exclusivityPolicy)
        {
            case CONNECTION:
                if(_exclusiveOwner == null)
                {
                    _exclusiveOwner = target.getSessionModel().getConnectionModel();
                    addExclusivityConstraint(target.getSessionModel().getConnectionModel());
                }
                else
                {
                    if(_exclusiveOwner != target.getSessionModel().getConnectionModel())
                    {
                        throw new ConsumerAccessRefused();
                    }
                }
                break;
            case SESSION:
                if(_exclusiveOwner == null)
                {
                    _exclusiveOwner = target.getSessionModel();
                    addExclusivityConstraint(target.getSessionModel());
                }
                else
                {
                    if(_exclusiveOwner != target.getSessionModel())
                    {
                        throw new ConsumerAccessRefused();
                    }
                }
                break;
            case LINK:
                if(getConsumerCount() != 0)
                {
                    throw new ConsumerAccessRefused();
                }
                break;
            case PRINCIPAL:
                if(_exclusiveOwner == null)
                {
                    _exclusiveOwner = target.getSessionModel().getConnectionModel().getAuthorizedPrincipal();
                }
                else
                {
                    if(!_exclusiveOwner.equals(target.getSessionModel().getConnectionModel().getAuthorizedPrincipal()))
                    {
                        throw new ConsumerAccessRefused();
                    }
                }
                break;
            case CONTAINER:
                if(_exclusiveOwner == null)
                {
                    _exclusiveOwner = target.getSessionModel().getConnectionModel().getRemoteContainerName();
                }
                else
                {
                    if(!_exclusiveOwner.equals(target.getSessionModel().getConnectionModel().getRemoteContainerName()))
                    {
                        throw new ConsumerAccessRefused();
                    }
                }
                break;
            case NONE:
                break;
            default:
                throw new ServerScopedRuntimeException("Unknown exclusivity policy " + _exclusivityPolicy);
        }

        boolean exclusive =  optionSet.contains(Consumer.Option.EXCLUSIVE);
        boolean isTransient =  optionSet.contains(Consumer.Option.TRANSIENT);

        QueueConsumer<T,E,Q,L> consumer = new QueueConsumer<T,E,Q,L>(filters, messageClass,
                                                         optionSet.contains(Consumer.Option.ACQUIRES),
                                                         optionSet.contains(Consumer.Option.SEES_REQUEUES),
                                                         consumerName, optionSet.contains(Consumer.Option.TRANSIENT), target);
        target.consumerAdded(consumer);


        if (exclusive && !isTransient)
        {
            _exclusiveSubscriber = consumer;
        }

        if(consumer.isActive())
        {
            _activeSubscriberCount.incrementAndGet();
        }

        consumer.setStateListener(this);
        consumer.setQueueContext(new QueueContext<E,Q,L>(_entries.getHead()));

        if (!isDeleted())
        {
            consumer.setQueue((Q)this, exclusive);
            if(_noLocal)
            {
                consumer.setNoLocal(true);
            }

            synchronized (_consumerListeners)
            {
                for(ConsumerRegistrationListener<Q> listener : _consumerListeners)
                {
                    listener.consumerAdded((Q)this, consumer);
                }
            }

            _consumerList.add(consumer);

            if (isDeleted())
            {
                consumer.queueDeleted();
            }
        }
        else
        {
            // TODO
        }

        deliverAsync(consumer);

        return consumer;

    }

    synchronized void unregisterConsumer(final QueueConsumer<?,E,Q,L> consumer)
    {
        if (consumer == null)
        {
            throw new NullPointerException("consumer argument is null");
        }

        boolean removed = _consumerList.remove(consumer);

        if (removed)
        {
            consumer.close();
            // No longer can the queue have an exclusive consumer
            setExclusiveSubscriber(null);

            consumer.setQueueContext(null);

            if(_exclusivityPolicy == ExclusivityPolicy.LINK)
            {
                _exclusiveOwner = null;
            }

            if(_messageGroupManager != null)
            {
                resetSubPointersForGroups(consumer, true);
            }

            synchronized (_consumerListeners)
            {
                for(ConsumerRegistrationListener<Q> listener : _consumerListeners)
                {
                    listener.consumerRemoved((Q)this, consumer);
                }
            }

            // auto-delete queues must be deleted if there are no remaining subscribers

            if(!consumer.isTransient()
               && ( _lifetimePolicy == LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS
                    || _lifetimePolicy == LifetimePolicy.DELETE_ON_NO_LINKS )
               && getConsumerCount() == 0)
            {

                if (_logger.isInfoEnabled())
                {
                    _logger.info("Auto-deleting queue:" + this);
                }

                try
                {
                    getVirtualHost().removeQueue(this);
                }
                catch (QpidSecurityException e)
                {
                    throw new ConnectionScopedRuntimeException("Auto delete queue unable to delete itself", e);
                }

                // we need to manually fire the event to the removed consumer (which was the last one left for this
                // queue. This is because the delete method uses the consumer set which has just been cleared
                consumer.queueDeleted();
            }
        }

    }

    public Collection<QueueConsumer<?,E,Q,L>> getConsumers()
    {
        List<QueueConsumer<?,E,Q,L>> consumers = new ArrayList<QueueConsumer<?,E,Q,L>>();
        QueueConsumerList.ConsumerNodeIterator<E,Q,L> iter = _consumerList.iterator();
        while(iter.advance())
        {
            consumers.add(iter.getNode().getConsumer());
        }
        return consumers;

    }

    public void addConsumerRegistrationListener(final ConsumerRegistrationListener<Q> listener)
    {
        synchronized (_consumerListeners)
        {
            _consumerListeners.add(listener);
        }
    }

    public void removeConsumerRegistrationListener(final ConsumerRegistrationListener<Q> listener)
    {
        synchronized (_consumerListeners)
        {
            _consumerListeners.remove(listener);
        }
    }

    public void resetSubPointersForGroups(QueueConsumer<?,E,Q,L> consumer, boolean clearAssignments)
    {
        E entry = _messageGroupManager.findEarliestAssignedAvailableEntry(consumer);
        if(clearAssignments)
        {
            _messageGroupManager.clearAssignments(consumer);
        }

        if(entry != null)
        {
            QueueConsumerList.ConsumerNodeIterator<E,Q,L> subscriberIter = _consumerList.iterator();
            // iterate over all the subscribers, and if they are in advance of this queue entry then move them backwards
            while (subscriberIter.advance())
            {
                QueueConsumer<?,E,Q,L> sub = subscriberIter.getNode().getConsumer();

                // we don't make browsers send the same stuff twice
                if (sub.seesRequeues())
                {
                    updateSubRequeueEntry(sub, entry);
                }
            }

            deliverAsync();

        }
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

    public void enqueue(ServerMessage message, Action<? super MessageInstance<?, QueueConsumer<?,E,Q,L>>> action)
    {
        incrementQueueCount();
        incrementQueueSize(message);

        _totalMessagesReceived.incrementAndGet();


        E entry;
        final QueueConsumer<?,E,Q,L> exclusiveSub = _exclusiveSubscriber;
        entry = _entries.add(message);

        if(action != null || (exclusiveSub == null  && _queueRunner.isIdle()))
        {
            /*

            iterate over consumers and if any is at the end of the queue and can deliver this message, then deliver the message

             */
            QueueConsumerList.ConsumerNode<E,Q,L> node = _consumerList.getMarkedNode();
            QueueConsumerList.ConsumerNode<E,Q,L> nextNode = node.findNext();
            if (nextNode == null)
            {
                nextNode = _consumerList.getHead().findNext();
            }
            while (nextNode != null)
            {
                if (_consumerList.updateMarkedNode(node, nextNode))
                {
                    break;
                }
                else
                {
                    node = _consumerList.getMarkedNode();
                    nextNode = node.findNext();
                    if (nextNode == null)
                    {
                        nextNode = _consumerList.getHead().findNext();
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
                    nextNode = _consumerList.getHead();
                }
                else
                {
                    // if consumer at end, and active, offer
                    QueueConsumer<?,E,Q,L> sub = nextNode.getConsumer();
                    deliverToConsumer(sub, entry);
                }
                nextNode = nextNode.findNext();

            }
        }


        if (entry.isAvailable())
        {
            checkConsumersNotAheadOfDelivery(entry);

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
            action.performAction(entry);
        }

    }

    private void deliverToConsumer(final QueueConsumer<?,E,Q,L> sub, final E entry)
    {

        if(sub.trySendLock())
        {
            try
            {
                if (!sub.isSuspended()
                    && consumerReadyAndHasInterest(sub, entry)
                    && mightAssign(sub, entry)
                    && !sub.wouldSuspend(entry))
                {
                    if (sub.acquires() && !assign(sub, entry))
                    {
                        // restore credit here that would have been taken away by wouldSuspend since we didn't manage
                        // to acquire the entry for this consumer
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

    private boolean assign(final QueueConsumer<?,E,Q,L> sub, final E entry)
    {
        if(_messageGroupManager == null)
        {
            //no grouping, try to acquire immediately.
            return entry.acquire(sub);
        }
        else
        {
            //the group manager is responsible for acquiring the message if/when appropriate
            return _messageGroupManager.acceptMessage(sub, entry);
        }
    }

    private boolean mightAssign(final QueueConsumer<?,E,Q,L> sub, final E entry)
    {
        if(_messageGroupManager == null || !sub.acquires())
        {
            return true;
        }
        QueueConsumer assigned = _messageGroupManager.getAssignedConsumer(entry);
        return (assigned == null) || (assigned == sub);
    }

    protected void checkConsumersNotAheadOfDelivery(final E entry)
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

    private void deliverMessage(final QueueConsumer<?,E,Q,L> sub, final E entry, boolean batch)
    {
        setLastSeenEntry(sub, entry);

        _deliveredMessages.incrementAndGet();
        incrementUnackedMsgCount(entry);

        sub.send(entry, batch);
    }

    private boolean consumerReadyAndHasInterest(final QueueConsumer<?,E,Q,L> sub, final E entry)
    {
        return sub.hasInterest(entry) && (getNextAvailableEntry(sub) == entry);
    }


    private void setLastSeenEntry(final QueueConsumer<?,E,Q,L> sub, final E entry)
    {
        QueueContext<E,Q,L> subContext = sub.getQueueContext();
        if (subContext != null)
        {
            E releasedEntry = subContext.getReleasedEntry();

            QueueContext._lastSeenUpdater.set(subContext, entry);
            if(releasedEntry == entry)
            {
               QueueContext._releasedUpdater.compareAndSet(subContext, releasedEntry, null);
            }
        }
    }

    private void updateSubRequeueEntry(final QueueConsumer<?,E,Q,L> sub, final E entry)
    {

        QueueContext<E,Q,L> subContext = sub.getQueueContext();
        if(subContext != null)
        {
            E oldEntry;

            while((oldEntry  = subContext.getReleasedEntry()) == null || oldEntry.compareTo(entry) > 0)
            {
                if(QueueContext._releasedUpdater.compareAndSet(subContext, oldEntry, entry))
                {
                    break;
                }
            }
        }
    }

    public void requeue(E entry)
    {
        QueueConsumerList.ConsumerNodeIterator<E,Q,L> subscriberIter = _consumerList.iterator();
        // iterate over all the subscribers, and if they are in advance of this queue entry then move them backwards
        while (subscriberIter.advance() && entry.isAvailable())
        {
            QueueConsumer<?,E,Q,L> sub = subscriberIter.getNode().getConsumer();

            // we don't make browsers send the same stuff twice
            if (sub.seesRequeues())
            {
                updateSubRequeueEntry(sub, entry);
            }
        }

        deliverAsync();

    }

    @Override
    public void dequeue(E entry)
    {
        decrementQueueCount();
        decrementQueueSize(entry);
        if (entry.acquiredByConsumer())
        {
            _deliveredMessages.decrementAndGet();
        }

        checkCapacity();

    }

    private void decrementQueueSize(final E entry)
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

    public boolean resend(final E entry, final QueueConsumer<?,E,Q,L> consumer)
    {
        /* TODO : This is wrong as the consumer may be suspended, we should instead change the state of the message
                  entry to resend and move back the consumer pointer. */

        consumer.getSendLock();
        try
        {
            if (!consumer.isClosed())
            {
                deliverMessage(consumer, entry, false);
                return true;
            }
            else
            {
                return false;
            }
        }
        finally
        {
            consumer.releaseSendLock();
        }
    }



    public int getConsumerCount()
    {
        return _consumerList.size();
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
        E entry = getOldestQueueEntry();
        return entry == null ? Long.MAX_VALUE : entry.getMessage().getArrivalTime();
    }

    protected E getOldestQueueEntry()
    {
        return _entries.next(_entries.getHead());
    }

    public boolean isDeleted()
    {
        return _deleted.get();
    }

    public List<E> getMessagesOnTheQueue()
    {
        ArrayList<E> entryList = new ArrayList<E>();
        QueueEntryIterator<E,Q,L,QueueConsumer<?,E,Q,L>> queueListIterator = _entries.iterator();
        while (queueListIterator.advance())
        {
            E node = queueListIterator.getNode();
            if (node != null && !node.isDeleted())
            {
                entryList.add(node);
            }
        }
        return entryList;

    }

    public void stateChanged(QueueConsumer<?,E,Q,L> sub, QueueConsumer.State oldState, QueueConsumer.State newState)
    {
        if (oldState == QueueConsumer.State.ACTIVE && newState != QueueConsumer.State.ACTIVE)
        {
            _activeSubscriberCount.decrementAndGet();

        }
        else if (newState == QueueConsumer.State.ACTIVE)
        {
            if (oldState != QueueConsumer.State.ACTIVE)
            {
                _activeSubscriberCount.incrementAndGet();

            }
            deliverAsync(sub);
        }
    }

    public int compareTo(final Q o)
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

    public boolean hasExclusiveConsumer()
    {
        return _exclusiveSubscriber != null;
    }

    private void setExclusiveSubscriber(QueueConsumer<?,E,Q,L> exclusiveSubscriber)
    {
        _exclusiveSubscriber = exclusiveSubscriber;
    }

    long getStateChangeCount()
    {
        return _stateChangeCount.get();
    }

    /** Used to track bindings to exchanges so that on deletion they can easily be cancelled. */
    protected L getEntries()
    {
        return _entries;
    }

    protected QueueConsumerList<E,Q,L> getConsumerList()
    {
        return _consumerList;
    }


    public static interface QueueEntryFilter<E extends QueueEntry>
    {
        public boolean accept(E entry);

        public boolean filterComplete();
    }



    public List<E> getMessagesOnTheQueue(final long fromMessageId, final long toMessageId)
    {
        return getMessagesOnTheQueue(new QueueEntryFilter<E>()
        {

            public boolean accept(E entry)
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

    public E getMessageOnTheQueue(final long messageId)
    {
        List<E> entries = getMessagesOnTheQueue(new QueueEntryFilter<E>()
        {
            private boolean _complete;

            public boolean accept(E entry)
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

    public List<E> getMessagesOnTheQueue(QueueEntryFilter<E> filter)
    {
        ArrayList<E> entryList = new ArrayList<E>();
        QueueEntryIterator<E,Q,L,QueueConsumer<?,E,Q,L>> queueListIterator = _entries.iterator();
        while (queueListIterator.advance() && !filter.filterComplete())
        {
            E node = queueListIterator.getNode();
            if (!node.isDeleted() && filter.accept(node))
            {
                entryList.add(node);
            }
        }
        return entryList;

    }

    public void visit(final QueueEntryVisitor<E> visitor)
    {
        QueueEntryIterator<E,Q,L,QueueConsumer<?,E,Q,L>> queueListIterator = _entries.iterator();

        while(queueListIterator.advance())
        {
            E node = queueListIterator.getNode();

            if(!node.isDeleted())
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
     * @param fromPosition first message position
     * @param toPosition last message position
     * @return list of messages
     */
    public List<E> getMessagesRangeOnTheQueue(final long fromPosition, final long toPosition)
    {
        return getMessagesOnTheQueue(new QueueEntryFilter<E>()
                                        {
                                            private long position = 0;

                                            public boolean accept(E entry)
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

    public void purge(final long request) throws QpidSecurityException
    {
        clear(request);
    }

    public long getCreateTime()
    {
        return _createTime;
    }

    // ------ Management functions

    public long clearQueue() throws QpidSecurityException
    {
        return clear(0l);
    }

    private long clear(final long request) throws QpidSecurityException
    {
        //Perform ACLs
        if (!getVirtualHost().getSecurityManager().authorisePurge(this))
        {
            throw new QpidSecurityException("Permission denied: queue " + getName());
        }

        QueueEntryIterator<E,Q,L,QueueConsumer<?,E,Q,L>> queueListIterator = _entries.iterator();
        long count = 0;

        ServerTransaction txn = new LocalTransaction(getVirtualHost().getMessageStore());

        while (queueListIterator.advance())
        {
            E node = queueListIterator.getNode();
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

    private void dequeueEntry(final E node)
    {
        ServerTransaction txn = new AutoCommitTransaction(getVirtualHost().getMessageStore());
        dequeueEntry(node, txn);
    }

    private void dequeueEntry(final E node, ServerTransaction txn)
    {
        txn.dequeue(this, node.getMessage(),
                    new ServerTransaction.Action()
                    {

                        public void postCommit()
                        {
                            node.delete();
                        }

                        public void onRollback()
                        {

                        }
                    });
    }

    @Override
    public void addDeleteTask(final Action<? super Q> task)
    {
        _deleteTaskList.add(task);
    }

    @Override
    public void removeDeleteTask(final Action<? super Q> task)
    {
        _deleteTaskList.remove(task);
    }

    // TODO list all thrown exceptions
    public int delete() throws QpidSecurityException
    {
        // Check access
        if (!_virtualHost.getSecurityManager().authoriseDelete(this))
        {
            throw new QpidSecurityException("Permission denied: " + getName());
        }

        if (!_deleted.getAndSet(true))
        {

            final ArrayList<Binding> bindingCopy = new ArrayList<Binding>(_bindings);

            for (Binding b : bindingCopy)
            {
                b.getExchange().removeBinding(b);
            }

            QueueConsumerList.ConsumerNodeIterator consumerNodeIterator = _consumerList.iterator();

            while (consumerNodeIterator.advance())
            {
                QueueConsumer s = consumerNodeIterator.getNode().getConsumer();
                if (s != null)
                {
                    s.queueDeleted();
                }
            }


            List<E> entries = getMessagesOnTheQueue(new QueueEntryFilter<E>()
            {

                public boolean accept(E entry)
                {
                    return entry.acquire();
                }

                public boolean filterComplete()
                {
                    return false;
                }
            });

            ServerTransaction txn = new LocalTransaction(getVirtualHost().getMessageStore());


            for(final E entry : entries)
            {
                // TODO log requeues with a post enqueue action
                int requeues = entry.routeToAlternate(null, txn);

                if(requeues == 0)
                {
                    // TODO log discard
                }
            }

            txn.commit();

            if(_alternateExchange != null)
            {
                _alternateExchange.removeReference(this);
            }


            for (Action<? super Q> task : _deleteTaskList)
            {
                task.performAction((Q)this);
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

    public void deliverAsync(QueueConsumer<?,E,Q,L> sub)
    {
        if(_exclusiveSubscriber == null)
        {
            deliverAsync();
        }
        else
        {
            SubFlushRunner flusher = sub.getRunner();
            flusher.execute(_asyncDelivery);
        }

    }

    void flushConsumer(QueueConsumer<?,E,Q,L> sub)
    {

        flushConsumer(sub, Long.MAX_VALUE);
    }

    boolean flushConsumer(QueueConsumer<?,E,Q,L> sub, long iterations)
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


        // if there's (potentially) more than one consumer the others will potentially not have been advanced to the
        // next entry they are interested in yet.  This would lead to holding on to references to expired messages, etc
        // which would give us memory "leak".

        if (!hasExclusiveConsumer())
        {
            advanceAllConsumers();
        }
        return atTail;
    }

    /**
     * Attempt delivery for the given consumer.
     *
     * Looks up the next node for the consumer and attempts to deliver it.
     *
     *
     * @param sub the consumer
     * @param batch true if processing can be batched
     * @return true if we have completed all possible deliveries for this sub.
     */
    private boolean attemptDelivery(QueueConsumer<?,E,Q,L> sub, boolean batch)
    {
        boolean atTail = false;

        boolean subActive = sub.isActive() && !sub.isSuspended();
        if (subActive)
        {

            E node  = getNextAvailableEntry(sub);

            if (node != null && node.isAvailable())
            {
                if (sub.hasInterest(node) && mightAssign(sub, node))
                {
                    if (!sub.wouldSuspend(node))
                    {
                        if (sub.acquires() && !assign(sub, node))
                        {
                            // restore credit here that would have been taken away by wouldSuspend since we didn't manage
                            // to acquire the entry for this consumer
                            sub.restoreCredit(node);
                        }
                        else
                        {
                            deliverMessage(sub, node, batch);
                        }

                    }
                    else // Not enough Credit for message and wouldSuspend
                    {
                        //QPID-1187 - Treat the consumer as suspended for this message
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

    protected void advanceAllConsumers()
    {
        QueueConsumerList.ConsumerNodeIterator<E,Q,L> consumerNodeIterator = _consumerList.iterator();
        while (consumerNodeIterator.advance())
        {
            QueueConsumerList.ConsumerNode<E,Q,L> subNode = consumerNodeIterator.getNode();
            QueueConsumer<?,E,Q,L> sub = subNode.getConsumer();
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

    private E getNextAvailableEntry(final QueueConsumer<?,E,Q,L> sub)
    {
        QueueContext<E,Q,L> context = sub.getQueueContext();
        if(context != null)
        {
            E lastSeen = context.getLastSeenEntry();
            E releasedNode = context.getReleasedEntry();

            E node = (releasedNode != null && lastSeen.compareTo(releasedNode)>=0) ? releasedNode : _entries.next(lastSeen);

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

    public boolean isEntryAheadOfConsumer(E entry, QueueConsumer<?,E,Q,L> sub)
    {
        QueueContext<E,Q,L> context = sub.getQueueContext();
        if(context != null)
        {
            E releasedNode = context.getReleasedEntry();
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
     * consumer (i.e. asynchronous delivery is required). Unless there are
     * SubFlushRunners operating (due to consumers unsuspending) which are
     * capable of accepting/delivering all messages then these messages would
     * otherwise remain on the queue.
     *
     * processQueue should be running while there are messages on the queue AND
     * there are consumers that can deliver them. If there are no
     * consumers capable of delivering the remaining messages on the queue
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
     */
    public long processQueue(QueueRunner runner)
    {
        long stateChangeCount;
        long previousStateChangeCount = Long.MIN_VALUE;
        long rVal = Long.MIN_VALUE;
        boolean deliveryIncomplete = true;

        boolean lastLoop = false;
        int iterations = MAX_ASYNC_DELIVERIES;

        final int numSubs = _consumerList.size();

        final int perSub = Math.max(iterations / Math.max(numSubs,1), 1);

        // For every message enqueue/requeue the we fire deliveryAsync() which
        // increases _stateChangeCount. If _sCC changes whilst we are in our loop
        // (detected by setting previousStateChangeCount to stateChangeCount in the loop body)
        // then we will continue to run for a maximum of iterations.
        // So whilst delivery/rejection is going on a processQueue thread will be running
        while (iterations != 0 && ((previousStateChangeCount != (stateChangeCount = _stateChangeCount.get())) || deliveryIncomplete))
        {
            // we want to have one extra loop after every consumer has reached the point where it cannot move
            // further, just in case the advance of one consumer in the last loop allows a different consumer to
            // move forward in the next iteration

            if (previousStateChangeCount != stateChangeCount)
            {
                //further asynchronous delivery is required since the
                //previous loop. keep going if iteration slicing allows.
                lastLoop = false;
                rVal = stateChangeCount;
            }

            previousStateChangeCount = stateChangeCount;
            boolean allConsumersDone = true;
            boolean consumerDone;

            QueueConsumerList.ConsumerNodeIterator<E,Q,L> consumerNodeIterator = _consumerList.iterator();
            //iterate over the subscribers and try to advance their pointer
            while (consumerNodeIterator.advance())
            {
                QueueConsumer<?,E,Q,L> sub = consumerNodeIterator.getNode().getConsumer();
                sub.getSendLock();

                    try
                    {
                        for(int i = 0 ; i < perSub; i++)
                        {
                            //attempt delivery. returns true if no further delivery currently possible to this sub
                            consumerDone = attemptDelivery(sub, true);
                            if (consumerDone)
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
                                //this consumer can accept additional deliveries, so we must
                                //keep going after this (if iteration slicing allows it)
                                allConsumersDone = false;
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

            if(allConsumersDone && lastLoop)
            {
                //We have done an extra loop already and there are again
                //again no further delivery attempts possible, only
                //keep going if state change demands it.
                deliveryIncomplete = false;
            }
            else if(allConsumersDone)
            {
                //All consumers reported being done, but we have to do
                //an extra loop if the iterations are not exhausted and
                //there is still any work to be done
                deliveryIncomplete = _consumerList.size() != 0;
                lastLoop = true;
            }
            else
            {
                //some consumers can still accept more messages,
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

    public void checkMessageStatus()
    {
        QueueEntryIterator<E,Q,L,QueueConsumer<?,E,Q,L>> queueListIterator = _entries.iterator();

        while (queueListIterator.advance())
        {
            E node = queueListIterator.getNode();
            // Only process nodes that are not currently deleted and not dequeued
            if (!node.isDeleted())
            {
                // If the node has expired then acquire it
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

    private static class DeleteDeleteTask implements Action<Deletable>
    {

        private final Deletable<? extends Deletable> _lifetimeObject;
        private final Action<? super Deletable> _deleteQueueOwnerTask;

        public DeleteDeleteTask(final Deletable<? extends Deletable> lifetimeObject,
                                final Action<? super Deletable> deleteQueueOwnerTask)
        {
            _lifetimeObject = lifetimeObject;
            _deleteQueueOwnerTask = deleteQueueOwnerTask;
        }

        @Override
        public void performAction(final Deletable object)
        {
            _lifetimeObject.removeDeleteTask(_deleteQueueOwnerTask);
        }
    }

    private final class QueueEntryListener implements StateChangeListener<E, QueueEntry.State>
    {

        private final QueueConsumer<?,E,Q,L> _sub;

        public QueueEntryListener(final QueueConsumer<?,E,Q,L> sub)
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

        public void stateChanged(E entry, QueueEntry.State oldSate, QueueEntry.State newState)
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

    public long getTotalEnqueueSize()
    {
        return _enqueueSize.get();
    }

    public long getTotalDequeueSize()
    {
        return _dequeueSize.get();
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
        return getName();
    }

    public long getUnackedMessageCount()
    {
        return _unackedMsgCount.get();
    }

    public long getUnackedMessageBytes()
    {
        return _unackedMsgBytes.get();
    }

    public void decrementUnackedMsgCount(E queueEntry)
    {
        _unackedMsgCount.decrementAndGet();
        _unackedMsgBytes.addAndGet(-queueEntry.getSize());
    }

    private void incrementUnackedMsgCount(E entry)
    {
        _unackedMsgCount.incrementAndGet();
        _unackedMsgBytes.addAndGet(entry.getSize());
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
    private void checkForNotification(ServerMessage<?> msg)
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
        _description = description;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    public final  <M extends ServerMessage<? extends StorableMessageMetaData>> int send(final M message,
                              final InstanceProperties instanceProperties,
                              final ServerTransaction txn,
                              final Action<? super MessageInstance<?, ? extends Consumer>> postEnqueueAction)
    {
            txn.enqueue(this,message, new ServerTransaction.Action()
            {
                MessageReference _reference = message.newReference();

                public void postCommit()
                {
                    try
                    {
                        SimpleAMQQueue.this.enqueue(message, postEnqueueAction);
                    }
                    finally
                    {
                        _reference.release();
                    }
                }

                public void onRollback()
                {
                    _reference.release();
                }
            });
            return 1;

    }

    @Override
    public boolean verifySessionAccess(final AMQSessionModel<?, ?> session)
    {
        boolean allowed;
        switch(_exclusivityPolicy)
        {
            case NONE:
                allowed = true;
                break;
            case SESSION:
                allowed = _exclusiveOwner == null || _exclusiveOwner == session;
                break;
            case CONNECTION:
                allowed = _exclusiveOwner == null || _exclusiveOwner == session.getConnectionModel();
                break;
            case PRINCIPAL:
                allowed = _exclusiveOwner == null || _exclusiveOwner.equals(session.getConnectionModel().getAuthorizedPrincipal());
                break;
            case CONTAINER:
                allowed = _exclusiveOwner == null || _exclusiveOwner.equals(session.getConnectionModel().getRemoteContainerName());
                break;
            case LINK:
                allowed = _exclusiveSubscriber == null || _exclusiveSubscriber.getSessionModel() == session;
                break;
            default:
                throw new ServerScopedRuntimeException("Unknown exclusivity policy " + _exclusivityPolicy);
        }
        return allowed;
    }

    @Override
    public synchronized void setExclusivityPolicy(final ExclusivityPolicy desiredPolicy)
            throws ExistingConsumerPreventsExclusive
    {
        if(desiredPolicy != _exclusivityPolicy && !(desiredPolicy == null && _exclusivityPolicy == ExclusivityPolicy.NONE))
        {
            switch(desiredPolicy)
            {
                case NONE:
                    _exclusiveOwner = null;
                    break;
                case PRINCIPAL:
                    switchToPrincipalExclusivity();
                    break;
                case CONTAINER:
                    switchToContainerExclusivity();
                    break;
                case CONNECTION:
                    switchToConnectionExclusivity();
                    break;
                case SESSION:
                    switchToSessionExclusivity();
                    break;
                case LINK:
                    switchToLinkExclusivity();
                    break;
            }
            _exclusivityPolicy = desiredPolicy;
        }
    }

    private void switchToLinkExclusivity() throws ExistingConsumerPreventsExclusive
    {
        switch (getConsumerCount())
        {
            case 1:
                _exclusiveSubscriber = getConsumerList().getHead().getConsumer();
                // deliberate fall through
            case 0:
                _exclusiveOwner = null;
                break;
            default:
                throw new ExistingConsumerPreventsExclusive();
        }

    }

    private void switchToSessionExclusivity() throws ExistingConsumerPreventsExclusive
    {

        switch(_exclusivityPolicy)
        {
            case NONE:
            case PRINCIPAL:
            case CONTAINER:
            case CONNECTION:
                AMQSessionModel session = null;
                for(Consumer c : getConsumers())
                {
                    if(session == null)
                    {
                        session = c.getSessionModel();
                    }
                    else if(!session.equals(c.getSessionModel()))
                    {
                        throw new ExistingConsumerPreventsExclusive();
                    }
                }
                _exclusiveOwner = session;
                break;
            case LINK:
                _exclusiveOwner = _exclusiveSubscriber == null ? null : _exclusiveSubscriber.getSessionModel().getConnectionModel();
        }
    }

    private void switchToConnectionExclusivity() throws ExistingConsumerPreventsExclusive
    {
        switch(_exclusivityPolicy)
        {
            case NONE:
            case CONTAINER:
            case PRINCIPAL:
                AMQConnectionModel con = null;
                for(Consumer c : getConsumers())
                {
                    if(con == null)
                    {
                        con = c.getSessionModel().getConnectionModel();
                    }
                    else if(!con.equals(c.getSessionModel().getConnectionModel()))
                    {
                        throw new ExistingConsumerPreventsExclusive();
                    }
                }
                _exclusiveOwner = con;
                break;
            case SESSION:
                _exclusiveOwner = _exclusiveOwner == null ? null : ((AMQSessionModel)_exclusiveOwner).getConnectionModel();
                break;
            case LINK:
                _exclusiveOwner = _exclusiveSubscriber == null ? null : _exclusiveSubscriber.getSessionModel().getConnectionModel();
        }
    }

    private void switchToContainerExclusivity() throws ExistingConsumerPreventsExclusive
    {
        switch(_exclusivityPolicy)
        {
            case NONE:
            case PRINCIPAL:
                String containerID = null;
                for(Consumer c : getConsumers())
                {
                    if(containerID == null)
                    {
                        containerID = c.getSessionModel().getConnectionModel().getRemoteContainerName();
                    }
                    else if(!containerID.equals(c.getSessionModel().getConnectionModel().getRemoteContainerName()))
                    {
                        throw new ExistingConsumerPreventsExclusive();
                    }
                }
                _exclusiveOwner = containerID;
                break;
            case CONNECTION:
                _exclusiveOwner = _exclusiveOwner == null ? null : ((AMQConnectionModel)_exclusiveOwner).getRemoteContainerName();
                break;
            case SESSION:
                _exclusiveOwner = _exclusiveOwner == null ? null : ((AMQSessionModel)_exclusiveOwner).getConnectionModel().getRemoteContainerName();
                break;
            case LINK:
                _exclusiveOwner = _exclusiveSubscriber == null ? null : _exclusiveSubscriber.getSessionModel().getConnectionModel().getRemoteContainerName();
        }
    }

    private void switchToPrincipalExclusivity() throws ExistingConsumerPreventsExclusive
    {
        switch(_exclusivityPolicy)
        {
            case NONE:
            case CONTAINER:
                Principal principal = null;
                for(Consumer c : getConsumers())
                {
                    if(principal == null)
                    {
                        principal = c.getSessionModel().getConnectionModel().getAuthorizedPrincipal();
                    }
                    else if(!principal.equals(c.getSessionModel().getConnectionModel().getAuthorizedPrincipal()))
                    {
                        throw new ExistingConsumerPreventsExclusive();
                    }
                }
                _exclusiveOwner = principal;
                break;
            case CONNECTION:
                _exclusiveOwner = _exclusiveOwner == null ? null : ((AMQConnectionModel)_exclusiveOwner).getAuthorizedPrincipal();
                break;
            case SESSION:
                _exclusiveOwner = _exclusiveOwner == null ? null : ((AMQSessionModel)_exclusiveOwner).getConnectionModel().getAuthorizedPrincipal();
                break;
            case LINK:
                _exclusiveOwner = _exclusiveSubscriber == null ? null : _exclusiveSubscriber.getSessionModel().getConnectionModel().getAuthorizedPrincipal();
        }
    }

    private class ClearOwnerAction implements Action<Deletable>
    {
        private final Deletable<? extends Deletable> _lifetimeObject;
        private DeleteDeleteTask _deleteTask;

        public ClearOwnerAction(final Deletable<? extends Deletable> lifetimeObject)
        {
            _lifetimeObject = lifetimeObject;
        }

        @Override
        public void performAction(final Deletable object)
        {
            if(SimpleAMQQueue.this._exclusiveOwner == _lifetimeObject)
            {
                SimpleAMQQueue.this._exclusiveOwner = null;
            }
            if(_deleteTask != null)
            {
                removeDeleteTask(_deleteTask);
            }
        }

        public void setDeleteTask(final DeleteDeleteTask deleteTask)
        {
            _deleteTask = deleteTask;
        }
    }
}
