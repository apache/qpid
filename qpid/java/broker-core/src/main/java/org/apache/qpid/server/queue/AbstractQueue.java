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

import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import org.apache.qpid.pool.ReferenceCountingExecutorService;
import org.apache.qpid.server.binding.BindingImpl;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.connection.SessionPrincipal;
import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.consumer.ConsumerTarget;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.messages.QueueMessages;
import org.apache.qpid.server.logging.subjects.QueueLogSubject;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageDeletedException;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.ExclusivityPolicy;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.QueueNotificationListener;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.store.MessageDurability;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.LocalTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.server.util.Deletable;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.server.util.StateChangeListener;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.transport.TransportException;

public abstract class AbstractQueue<X extends AbstractQueue<X>>
        extends AbstractConfiguredObject<X>
        implements AMQQueue<X>,
                   StateChangeListener<QueueConsumer<?>, State>,
                   MessageGroupManager.ConsumerResetHelper
{

    private static final Logger _logger = Logger.getLogger(AbstractQueue.class);

    public static final String SHARED_MSG_GROUP_ARG_VALUE = "1";

    private static final QueueNotificationListener NULL_NOTIFICATION_LISTENER = new QueueNotificationListener()
    {
        @Override
        public void notifyClients(final NotificationCheck notification,
                                  final Queue queue,
                                  final String notificationMsg)
        {

        }
    };

    private static final long INITIAL_TARGET_QUEUE_SIZE = 102400l;

    private final VirtualHostImpl _virtualHost;
    private final DeletedChildListener _deletedChildListener = new DeletedChildListener();

    @ManagedAttributeField( beforeSet = "preSetAlternateExchange", afterSet = "postSetAlternateExchange")
    private Exchange _alternateExchange;


    private final QueueConsumerList _consumerList = new QueueConsumerList();

    private volatile QueueConsumer<?> _exclusiveSubscriber;



    private final AtomicInteger _atomicQueueCount = new AtomicInteger(0);

    private final AtomicLong _atomicQueueSize = new AtomicLong(0L);

    private final AtomicLong _targetQueueSize = new AtomicLong(INITIAL_TARGET_QUEUE_SIZE);

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
    @ManagedAttributeField( afterSet = "updateAlertChecks" )
    private long _alertThresholdMessageSize;

    /** max allowed number of messages on a queue. */
    @ManagedAttributeField( afterSet = "updateAlertChecks" )
    private long _alertThresholdQueueDepthMessages;

    /** max queue depth for the queue */
    @ManagedAttributeField( afterSet = "updateAlertChecks" )
    private long _alertThresholdQueueDepthBytes;

    /** maximum message age before alerts occur */
    @ManagedAttributeField( afterSet = "updateAlertChecks" )
    private long _alertThresholdMessageAge;

    /** the minimum interval between sending out consecutive alerts of the same type */
    @ManagedAttributeField
    private long _alertRepeatGap;

    @ManagedAttributeField
    private long _queueFlowControlSizeBytes;

    @ManagedAttributeField( afterSet = "checkCapacity" )
    private long _queueFlowResumeSizeBytes;

    @ManagedAttributeField
    private ExclusivityPolicy _exclusive;

    @ManagedAttributeField
    private MessageDurability _messageDurability;

    private Object _exclusiveOwner; // could be connection, session, Principal or a String for the container name

    private final Set<NotificationCheck> _notificationChecks =
            Collections.synchronizedSet(EnumSet.noneOf(NotificationCheck.class));


    static final int MAX_ASYNC_DELIVERIES = 80;


    private final AtomicLong _stateChangeCount = new AtomicLong(Long.MIN_VALUE);

    private final Executor _asyncDelivery;
    private AtomicInteger _deliveredMessages = new AtomicInteger();
    private AtomicBoolean _stopped = new AtomicBoolean(false);

    private final Set<AMQSessionModel> _blockedChannels = new ConcurrentSkipListSet<AMQSessionModel>();

    private final AtomicBoolean _deleted = new AtomicBoolean(false);
    private final List<Action<? super AMQQueue>> _deleteTaskList =
            new CopyOnWriteArrayList<Action<? super AMQQueue>>();


    private LogSubject _logSubject;

    @ManagedAttributeField
    private boolean _noLocal;

    private final AtomicBoolean _overfull = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<BindingImpl> _bindings = new CopyOnWriteArrayList<BindingImpl>();
    private Map<String, Object> _arguments;

    /** the maximum delivery count for each message on this queue or 0 if maximum delivery count is not to be enforced. */
    @ManagedAttributeField
    private int _maximumDeliveryAttempts;

    private MessageGroupManager _messageGroupManager;

    private final Collection<ConsumerRegistrationListener<? super MessageSource>> _consumerListeners =
            new ArrayList<ConsumerRegistrationListener<? super MessageSource>>();

    private QueueNotificationListener  _notificationListener;
    private final long[] _lastNotificationTimes = new long[NotificationCheck.values().length];

    @ManagedAttributeField
    private String _messageGroupKey;
    @ManagedAttributeField
    private boolean _messageGroupSharedGroups;
    @ManagedAttributeField
    private String _messageGroupDefaultGroup;
    @ManagedAttributeField
    private int _maximumDistinctGroups;
    @ManagedAttributeField
    private long _minimumMessageTtl;
    @ManagedAttributeField
    private long _maximumMessageTtl;

    private final AtomicBoolean _recovering = new AtomicBoolean(true);
    private final ConcurrentLinkedQueue<EnqueueRequest> _postRecoveryQueue = new ConcurrentLinkedQueue<>();

    protected AbstractQueue(Map<String, Object> attributes, VirtualHostImpl virtualHost)
    {
        super(parentsMap(virtualHost), attributes);

        _virtualHost = virtualHost;
        _asyncDelivery = ReferenceCountingExecutorService.getInstance().acquireExecutorService();

    }

    @Override
    protected void onCreate()
    {
        super.onCreate();

        if(isDurable() && (getLifetimePolicy()  == LifetimePolicy.DELETE_ON_CONNECTION_CLOSE
                            || getLifetimePolicy() == LifetimePolicy.DELETE_ON_SESSION_END))
        {
            Subject.doAs(SecurityManager.getSubjectWithAddedSystemRights(),
                         new PrivilegedAction<Object>()
                         {
                             @Override
                             public Object run()
                             {
                                 setAttribute(AbstractConfiguredObject.DURABLE, true, false);
                                 return null;
                             }
                         });
        }

        if (isDurable())
        {
            _virtualHost.getDurableConfigurationStore().create(asObjectRecord());
        }
        else if(getMessageDurability() != MessageDurability.NEVER)
        {
            Subject.doAs(SecurityManager.getSubjectWithAddedSystemRights(),
                         new PrivilegedAction<Object>()
                         {
                             @Override
                             public Object run()
                             {
                                 setAttribute(Queue.MESSAGE_DURABILITY, getMessageDurability(), MessageDurability.NEVER);
                                 return null;
                             }
                         });
        }

        _recovering.set(false);
    }

    @Override
    public void onValidate()
    {
        super.onValidate();
        if (_queueFlowResumeSizeBytes > _queueFlowControlSizeBytes)
        {
            throw new IllegalConfigurationException("Flow resume size can't be greater than flow control size");
        }
    }

    protected void onOpen()
    {
        super.onOpen();

        Map<String,Object> attributes = getActualAttributes();

        final LinkedHashMap<String, Object> arguments = new LinkedHashMap<String, Object>(attributes);

        arguments.put(Queue.EXCLUSIVE, _exclusive);
        arguments.put(Queue.LIFETIME_POLICY, getLifetimePolicy());

        _arguments = Collections.synchronizedMap(arguments);

        _logSubject = new QueueLogSubject(this);

        try
        {

            _virtualHost.getSecurityManager().authoriseCreateQueue(this);
        }
        catch(AccessControlException e)
        {
            deleted();
            throw e;
        }

        Subject activeSubject = Subject.getSubject(AccessController.getContext());
        Set<SessionPrincipal> sessionPrincipals = activeSubject == null ? Collections.<SessionPrincipal>emptySet() : activeSubject.getPrincipals(SessionPrincipal.class);
        AMQSessionModel<?,?> sessionModel;
        if(sessionPrincipals.isEmpty())
        {
            sessionModel = null;
        }
        else
        {
            final SessionPrincipal sessionPrincipal = sessionPrincipals.iterator().next();
            sessionModel = sessionPrincipal.getSession();
        }

        if(sessionModel != null)
        {

            switch(_exclusive)
            {

                case PRINCIPAL:
                    _exclusiveOwner = sessionModel.getConnectionModel().getAuthorizedPrincipal();
                    if(isDurable())
                    {
                        _virtualHost.getDurableConfigurationStore().update(false,asObjectRecord());
                    }
                    break;
                case CONTAINER:
                    _exclusiveOwner = sessionModel.getConnectionModel().getRemoteContainerName();
                    if(isDurable())
                    {
                        _virtualHost.getDurableConfigurationStore().update(false,asObjectRecord());
                    }
                    break;
                case CONNECTION:
                    _exclusiveOwner = sessionModel.getConnectionModel();
                    addExclusivityConstraint(sessionModel.getConnectionModel());
                    break;
                case SESSION:
                    _exclusiveOwner = sessionModel;
                    addExclusivityConstraint(sessionModel);
                    break;
                case NONE:
                case LINK:
                    // nothing to do as if link no link associated until there is a consumer associated
                    break;
                default:
                    throw new ServerScopedRuntimeException("Unknown exclusivity policy: "
                                                           + _exclusive
                                                           + " this is a coding error inside Qpid");
            }
        }
        else if(_exclusive == ExclusivityPolicy.PRINCIPAL)
        {
            String owner = MapValueConverter.getStringAttribute(Queue.OWNER, attributes, null);
            if(owner != null)
            {
                _exclusiveOwner = new AuthenticatedPrincipal(owner);
            }
        }
        else if(_exclusive == ExclusivityPolicy.CONTAINER)
        {
            String owner = MapValueConverter.getStringAttribute(Queue.OWNER, attributes, null);
            if(owner != null)
            {
                _exclusiveOwner = owner;
            }
        }


        if(getLifetimePolicy() == LifetimePolicy.DELETE_ON_CONNECTION_CLOSE)
        {
            if(sessionModel != null)
            {
                addLifetimeConstraint(sessionModel.getConnectionModel());
            }
            else
            {
                throw new IllegalArgumentException("Queues created with a lifetime policy of "
                                                   + getLifetimePolicy()
                                                   + " must be created from a connection.");
            }
        }
        else if(getLifetimePolicy() == LifetimePolicy.DELETE_ON_SESSION_END)
        {
            if(sessionModel != null)
            {
                addLifetimeConstraint(sessionModel);
            }
            else
            {
                throw new IllegalArgumentException("Queues created with a lifetime policy of "
                                                   + getLifetimePolicy()
                                                   + " must be created from a connection.");
            }
        }


        // Log the creation of this Queue.
        // The priorities display is toggled on if we set priorities > 0
        getEventLogger().message(_logSubject,
                                 getCreatedLogMessage());

        if(getMessageGroupKey() != null)
        {
            if(isMessageGroupSharedGroups())
            {
                _messageGroupManager =
                        new DefinedGroupMessageGroupManager(getMessageGroupKey(), getMessageGroupDefaultGroup(), this);
            }
            else
            {
                _messageGroupManager = new AssignedConsumerMessageGroupManager(getMessageGroupKey(), getMaximumDistinctGroups());
            }
        }
        else
        {
            _messageGroupManager = null;
        }

        updateAlertChecks();
    }

    protected LogMessage getCreatedLogMessage()
    {
        String ownerString = getOwner();
        return QueueMessages.CREATED(ownerString,
                                     0,
                                     ownerString != null,
                                     getLifetimePolicy() != LifetimePolicy.PERMANENT,
                                     isDurable(),
                                     !isDurable(),
                                     false);
    }

    private void addLifetimeConstraint(final Deletable<? extends Deletable> lifetimeObject)
    {
        final Action<Deletable> deleteQueueTask = new Action<Deletable>()
        {
            @Override
            public void performAction(final Deletable object)
            {
                Subject.doAs(SecurityManager.getSubjectWithAddedSystemRights(),
                             new PrivilegedAction<Void>()
                             {
                                 @Override
                                 public Void run()
                                 {
                                     getVirtualHost().removeQueue(AbstractQueue.this);
                                     return null;
                                 }
                             });
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

    public boolean isExclusive()
    {
        return _exclusive != ExclusivityPolicy.NONE;
    }

    public Exchange<?> getAlternateExchange()
    {
        return _alternateExchange;
    }

    public void setAlternateExchange(ExchangeImpl exchange)
    {
        _alternateExchange = exchange;
    }

    private void postSetAlternateExchange()
    {
        if(_alternateExchange instanceof ExchangeImpl)
        {
            ((ExchangeImpl)_alternateExchange).addReference(this);
        }
    }

    private void preSetAlternateExchange()
    {
        if(_alternateExchange instanceof ExchangeImpl)
        {
            ((ExchangeImpl)_alternateExchange).removeReference(this);
        }
    }

    @Override
    public final MessageDurability getMessageDurability()
    {
        return _messageDurability;
    }

    @Override
    public long getMinimumMessageTtl()
    {
        return _minimumMessageTtl;
    }

    @Override
    public long getMaximumMessageTtl()
    {
        return _maximumMessageTtl;
    }

    @Override
    public Collection<String> getAvailableAttributes()
    {
        return new ArrayList<String>(_arguments.keySet());
    }

    public String getOwner()
    {
        if(_exclusiveOwner != null)
        {
            switch(_exclusive)
            {
                case CONTAINER:
                    return (String) _exclusiveOwner;
                case PRINCIPAL:
                    return ((Principal)_exclusiveOwner).getName();
            }
        }
        return null;
    }

    public VirtualHostImpl getVirtualHost()
    {
        return _virtualHost;
    }

    // ------ Manage Consumers


    @Override
    public synchronized QueueConsumerImpl addConsumer(final ConsumerTarget target,
                                     final FilterManager filters,
                                     final Class<? extends ServerMessage> messageClass,
                                     final String consumerName,
                                     EnumSet<ConsumerImpl.Option> optionSet)
            throws ExistingExclusiveConsumer, ExistingConsumerPreventsExclusive,
                   ConsumerAccessRefused
    {


        if (hasExclusiveConsumer())
        {
            throw new ExistingExclusiveConsumer();
        }

        Object exclusiveOwner = _exclusiveOwner;
        switch(_exclusive)
        {
            case CONNECTION:
                if(exclusiveOwner == null)
                {
                    exclusiveOwner = target.getSessionModel().getConnectionModel();
                    addExclusivityConstraint(target.getSessionModel().getConnectionModel());
                }
                else
                {
                    if(exclusiveOwner != target.getSessionModel().getConnectionModel())
                    {
                        throw new ConsumerAccessRefused();
                    }
                }
                break;
            case SESSION:
                if(exclusiveOwner == null)
                {
                    exclusiveOwner = target.getSessionModel();
                    addExclusivityConstraint(target.getSessionModel());
                }
                else
                {
                    if(exclusiveOwner != target.getSessionModel())
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
                if(exclusiveOwner == null)
                {
                    exclusiveOwner = target.getSessionModel().getConnectionModel().getAuthorizedPrincipal();
                }
                else
                {
                    if(!exclusiveOwner.equals(target.getSessionModel().getConnectionModel().getAuthorizedPrincipal()))
                    {
                        throw new ConsumerAccessRefused();
                    }
                }
                break;
            case CONTAINER:
                if(exclusiveOwner == null)
                {
                    exclusiveOwner = target.getSessionModel().getConnectionModel().getRemoteContainerName();
                }
                else
                {
                    if(!exclusiveOwner.equals(target.getSessionModel().getConnectionModel().getRemoteContainerName()))
                    {
                        throw new ConsumerAccessRefused();
                    }
                }
                break;
            case NONE:
                break;
            default:
                throw new ServerScopedRuntimeException("Unknown exclusivity policy " + _exclusive);
        }

        boolean exclusive =  optionSet.contains(ConsumerImpl.Option.EXCLUSIVE);
        boolean isTransient =  optionSet.contains(ConsumerImpl.Option.TRANSIENT);

        if(_noLocal && !optionSet.contains(ConsumerImpl.Option.NO_LOCAL))
        {
            optionSet = EnumSet.copyOf(optionSet);
            optionSet.add(ConsumerImpl.Option.NO_LOCAL);
        }

        if(exclusive && getConsumerCount() != 0)
        {
            throw new ExistingConsumerPreventsExclusive();
        }

        QueueConsumerImpl consumer = new QueueConsumerImpl(this,
                                                           target,
                                                           consumerName,
                                                           filters, 
                                                           messageClass,
                                                           optionSet);

        _exclusiveOwner = exclusiveOwner;
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
        consumer.setQueueContext(new QueueContext(getEntries().getHead()));

        if (!isDeleted())
        {
            synchronized (_consumerListeners)
            {
                for(ConsumerRegistrationListener<? super MessageSource> listener : _consumerListeners)
                {
                    listener.consumerAdded(this, consumer);
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

        childAdded(consumer);
        consumer.addChangeListener(_deletedChildListener);

        deliverAsync(consumer);

        return consumer;

    }

    synchronized void unregisterConsumer(final QueueConsumerImpl consumer)
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

            if(_exclusive == ExclusivityPolicy.LINK)
            {
                _exclusiveOwner = null;
            }

            if(_messageGroupManager != null)
            {
                resetSubPointersForGroups(consumer, true);
            }

            synchronized (_consumerListeners)
            {
                for(ConsumerRegistrationListener<? super MessageSource> listener : _consumerListeners)
                {
                    listener.consumerRemoved(this, consumer);
                }
            }

            // auto-delete queues must be deleted if there are no remaining subscribers

            if(!consumer.isTransient()
               && ( getLifetimePolicy() == LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS
                    || getLifetimePolicy() == LifetimePolicy.DELETE_ON_NO_LINKS )
               && getConsumerCount() == 0)
            {

                if (_logger.isInfoEnabled())
                {
                    _logger.info("Auto-deleting queue:" + this);
                }

                Subject.doAs(SecurityManager.getSubjectWithAddedSystemRights(), new PrivilegedAction<Object>()
                             {
                                 @Override
                                 public Object run()
                                 {
                                     getVirtualHost().removeQueue(AbstractQueue.this);
                                     return null;
                                 }
                             });


                // we need to manually fire the event to the removed consumer (which was the last one left for this
                // queue. This is because the delete method uses the consumer set which has just been cleared
                consumer.queueDeleted();

            }
        }

    }

    public Collection<QueueConsumer<?>> getConsumers()
    {
        List<QueueConsumer<?>> consumers = new ArrayList<QueueConsumer<?>>();
        QueueConsumerList.ConsumerNodeIterator iter = _consumerList.iterator();
        while(iter.advance())
        {
            consumers.add(iter.getNode().getConsumer());
        }
        return consumers;

    }

    public void addConsumerRegistrationListener(final ConsumerRegistrationListener<? super MessageSource> listener)
    {
        synchronized (_consumerListeners)
        {
            _consumerListeners.add(listener);
        }
    }

    public void removeConsumerRegistrationListener(final ConsumerRegistrationListener<? super MessageSource> listener)
    {
        synchronized (_consumerListeners)
        {
            _consumerListeners.remove(listener);
        }
    }

    public void resetSubPointersForGroups(QueueConsumer<?> consumer, boolean clearAssignments)
    {
        QueueEntry entry = _messageGroupManager.findEarliestAssignedAvailableEntry(consumer);
        if(clearAssignments)
        {
            _messageGroupManager.clearAssignments(consumer);
        }

        if(entry != null)
        {
            QueueConsumerList.ConsumerNodeIterator subscriberIter = _consumerList.iterator();
            // iterate over all the subscribers, and if they are in advance of this queue entry then move them backwards
            while (subscriberIter.advance())
            {
                QueueConsumer<?> sub = subscriberIter.getNode().getConsumer();

                // we don't make browsers send the same stuff twice
                if (sub.seesRequeues())
                {
                    updateSubRequeueEntry(sub, entry);
                }
            }

            deliverAsync();

        }
    }

    public void addBinding(final BindingImpl binding)
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
        childAdded(binding);
    }

    public void removeBinding(final BindingImpl binding)
    {
        _bindings.remove(binding);
        childRemoved(binding);
    }

    public Collection<BindingImpl> getBindings()
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

    public final void enqueue(ServerMessage message, Action<? super MessageInstance> action)
    {
        incrementQueueCount();
        incrementQueueSize(message);

        if((_atomicQueueSize.get() + _atomicQueueCount.get()*1024l) > _targetQueueSize.get() && message.getStoredMessage().isInMemory())
        {
            message.getStoredMessage().flowToDisk();
        }

        _totalMessagesReceived.incrementAndGet();

        if(_recovering.get())
        {
            EnqueueRequest request = new EnqueueRequest(message, action);
            _postRecoveryQueue.add(request);

            // deal with the case the recovering status changed just as we added to the post recovery queue
            if(!_recovering.get() && _postRecoveryQueue.remove(request))
            {
                doEnqueue(message, action);
            }
        }
        else
        {
            doEnqueue(message, action);
        }

    }

    public final void recover(ServerMessage message)
    {
        incrementQueueCount();
        incrementQueueSize(message);

        _totalMessagesReceived.incrementAndGet();

        doEnqueue(message, null);
    }


    @Override
    public final void completeRecovery()
    {
        if(_recovering.get())
        {
            enqueueFromPostRecoveryQueue();

            _recovering.set(false);

            // deal with any enqueues that occurred just as we cleared the queue
            enqueueFromPostRecoveryQueue();
        }
    }

    private void enqueueFromPostRecoveryQueue()
    {
        while(!_postRecoveryQueue.isEmpty())
        {
            EnqueueRequest request = _postRecoveryQueue.poll();
            MessageReference<?> messageReference = request.getMessage();
            doEnqueue(messageReference.getMessage(), request.getAction());
            messageReference.release();
        }
    }

    protected void doEnqueue(final ServerMessage message, final Action<? super MessageInstance> action)
    {
        final QueueConsumer<?> exclusiveSub = _exclusiveSubscriber;
        final QueueEntry entry = getEntries().add(message);
        updateExpiration(entry);

        try
        {
            if (action != null || (exclusiveSub == null  && _queueRunner.isIdle()))
            {
                Subject.doAs(SecurityManager.getSystemTaskSubject("Immediate Delivery"),
                             new PrivilegedAction<Void>()
                             {
                                 @Override
                                 public Void run()
                                 {
                                     tryDeliverStraightThrough(entry);
                                     return null;
                                 }
                             }
                            );
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
        }
        finally
        {
            if(action != null)
            {
                action.performAction(entry);
            }
        }

    }

    private void updateExpiration(final QueueEntry entry)
    {
        long expiration = entry.getMessage().getExpiration();
        long arrivalTime = entry.getMessage().getArrivalTime();
        if(_minimumMessageTtl != 0l)
        {
            if(arrivalTime == 0)
            {
                arrivalTime = System.currentTimeMillis();
            }
            if(expiration != 0l)
            {
                long calculatedExpiration = arrivalTime+_minimumMessageTtl;
                if(calculatedExpiration > expiration)
                {
                    entry.setExpiration(calculatedExpiration);
                    expiration = calculatedExpiration;
                }
            }
        }
        if(_maximumMessageTtl != 0l)
        {
            if(arrivalTime == 0)
            {
                arrivalTime = System.currentTimeMillis();
            }
            long calculatedExpiration = arrivalTime+_maximumMessageTtl;
            if(expiration == 0l || expiration > calculatedExpiration)
            {
                entry.setExpiration(calculatedExpiration);
            }
        }
    }

    /**
     * iterate over consumers and if any is at the end of the queue and can deliver this message,
     * then deliver the message
     */
    private void tryDeliverStraightThrough(final QueueEntry entry)
    {
        try
        {
            QueueConsumerList.ConsumerNode node = _consumerList.getMarkedNode();
            QueueConsumerList.ConsumerNode nextNode = node.findNext();
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
                    final QueueConsumer<?> sub = nextNode.getConsumer();
                    deliverToConsumer(sub, entry);


                }
                nextNode = nextNode.findNext();

            }
        }
        catch (ConnectionScopedRuntimeException | TransportException e)
        {
            String errorMessage = "Suppressing " + e.getClass().getSimpleName() +
                              " during straight through delivery, as this" +
                              " can only indicate an issue with a consumer.";
            if(_logger.isDebugEnabled())
            {
                _logger.debug(errorMessage, e);
            }
            else
            {
                _logger.info(errorMessage + ' ' + e.getMessage());
            }
        }
    }

    private void deliverToConsumer(final QueueConsumer<?> sub, final QueueEntry entry)
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
                        if(sub.acquires())
                        {
                            entry.unlockAcquisition();
                        }
                    }
                }
            }
            finally
            {
                sub.releaseSendLock();
            }
        }
    }

    private boolean assign(final QueueConsumer<?> sub, final QueueEntry entry)
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

    private boolean mightAssign(final QueueConsumer sub, final QueueEntry entry)
    {
        if(_messageGroupManager == null || !sub.acquires())
        {
            return true;
        }
        QueueConsumer assigned = _messageGroupManager.getAssignedConsumer(entry);
        return (assigned == null) || (assigned == sub);
    }

    protected void checkConsumersNotAheadOfDelivery(final QueueEntry entry)
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

    @Override
    public void setTargetSize(final long targetSize)
    {
        _targetQueueSize.set(targetSize);
    }

    public long getTotalDequeuedMessages()
    {
        return _dequeueCount.get();
    }

    public long getTotalEnqueuedMessages()
    {
        return _enqueueCount.get();
    }

    private void incrementQueueCount()
    {
        getAtomicQueueCount().incrementAndGet();
    }

    private void deliverMessage(final QueueConsumer<?> sub, final QueueEntry entry, boolean batch)
    {
        setLastSeenEntry(sub, entry);

        _deliveredMessages.incrementAndGet();
        incrementUnackedMsgCount(entry);

        sub.send(entry, batch);
    }

    private boolean consumerReadyAndHasInterest(final QueueConsumer<?> sub, final QueueEntry entry)
    {
        return sub.hasInterest(entry) && (getNextAvailableEntry(sub) == entry);
    }


    private void setLastSeenEntry(final QueueConsumer<?> sub, final QueueEntry entry)
    {
        QueueContext subContext = sub.getQueueContext();
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

    private void updateSubRequeueEntry(final QueueConsumer<?> sub, final QueueEntry entry)
    {

        QueueContext subContext = sub.getQueueContext();
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
        QueueConsumerList.ConsumerNodeIterator subscriberIter = _consumerList.iterator();
        // iterate over all the subscribers, and if they are in advance of this queue entry then move them backwards
        while (subscriberIter.advance() && entry.isAvailable())
        {
            QueueConsumer<?> sub = subscriberIter.getNode().getConsumer();

            // we don't make browsers send the same stuff twice
            if (sub.seesRequeues())
            {
                updateSubRequeueEntry(sub, entry);
            }
        }

        deliverAsync();

    }

    @Override
    public void dequeue(QueueEntry entry)
    {
        decrementQueueCount();
        decrementQueueSize(entry);
        if (entry.acquiredByConsumer())
        {
            _deliveredMessages.decrementAndGet();
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

    public boolean resend(final QueueEntry entry, final QueueConsumer<?> consumer)
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

    public int getConsumerCountWithCredit()
    {
        return _activeSubscriberCount.get();
    }

    public boolean isUnused()
    {
        return getConsumerCount() == 0;
    }

    public boolean isEmpty()
    {
        return getQueueDepthMessages() == 0;
    }

    @Override
    public int getQueueDepthMessages()
    {
        return getAtomicQueueCount().get();
    }

    public long getQueueDepthBytes()
    {
        return getAtomicQueueSize().get();
    }

    public int getUndeliveredMessageCount()
    {
        int count = getQueueDepthMessages() - _deliveredMessages.get();
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

    @Override
    public long getOldestMessageArrivalTime()
    {
        long oldestMessageArrivalTime = -1l;

        while(oldestMessageArrivalTime == -1l)
        {
            QueueEntry entry = getEntries().getOldestEntry();
            if (entry != null)
            {
                ServerMessage message = entry.getMessage();

                if(message != null)
                {
                    try
                    {
                        MessageReference reference = message.newReference();
                        try
                        {
                            oldestMessageArrivalTime = reference.getMessage().getArrivalTime();
                        }
                        finally
                        {
                            reference.release();
                        }


                    }
                    catch (MessageDeletedException e)
                    {
                        // ignore - the oldest message was deleted after it was discovered - we need to find the new oldest message
                    }
                }
            }
            else
            {
                oldestMessageArrivalTime = 0;
            }
        }
        return oldestMessageArrivalTime;
    }

    @Override
    public long getOldestMessageAge()
    {
        long oldestMessageArrivalTime = getOldestMessageArrivalTime();
        return oldestMessageArrivalTime == 0 ? 0 : System.currentTimeMillis() - oldestMessageArrivalTime;
    }

    public boolean isDeleted()
    {
        return _deleted.get();
    }

    public List<QueueEntry> getMessagesOnTheQueue()
    {
        ArrayList<QueueEntry> entryList = new ArrayList<QueueEntry>();
        QueueEntryIterator queueListIterator = getEntries().iterator();
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

    public void stateChanged(QueueConsumer<?> sub, State oldState, State newState)
    {
        if (oldState == State.ACTIVE && newState != State.ACTIVE)
        {
            _activeSubscriberCount.decrementAndGet();

        }
        else if (newState == State.ACTIVE)
        {
            if (oldState != State.ACTIVE)
            {
                _activeSubscriberCount.incrementAndGet();

            }
            deliverAsync(sub);
        }
    }

    public int compareTo(final AMQQueue o)
    {
        return getName().compareTo(o.getName());
    }

    public AtomicInteger getAtomicQueueCount()
    {
        return _atomicQueueCount;
    }

    public AtomicLong getAtomicQueueSize()
    {
        return _atomicQueueSize;
    }

    private boolean hasExclusiveConsumer()
    {
        return _exclusiveSubscriber != null;
    }

    private void setExclusiveSubscriber(QueueConsumer<?> exclusiveSubscriber)
    {
        _exclusiveSubscriber = exclusiveSubscriber;
    }

    long getStateChangeCount()
    {
        return _stateChangeCount.get();
    }

    /** Used to track bindings to exchanges so that on deletion they can easily be cancelled. */
    abstract QueueEntryList getEntries();

    protected QueueConsumerList getConsumerList()
    {
        return _consumerList;
    }

    public EventLogger getEventLogger()
    {
        return _virtualHost.getEventLogger();
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
        QueueEntryIterator queueListIterator = getEntries().iterator();
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

    public void visit(final QueueEntryVisitor visitor)
    {
        QueueEntryIterator queueListIterator = getEntries().iterator();

        while(queueListIterator.advance())
        {
            QueueEntry node = queueListIterator.getNode();

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

    // ------ Management functions

    public long clearQueue()
    {
        return clear(0l);
    }

    private long clear(final long request)
    {
        //Perform ACLs
        getVirtualHost().getSecurityManager().authorisePurge(this);

        QueueEntryIterator queueListIterator = getEntries().iterator();
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
                            node.delete();
                        }

                        public void onRollback()
                        {

                        }
                    });
    }

    @Override
    public void addDeleteTask(final Action<? super AMQQueue> task)
    {
        _deleteTaskList.add(task);
    }

    @Override
    public void removeDeleteTask(final Action<? super AMQQueue> task)
    {
        _deleteTaskList.remove(task);
    }

    // TODO list all thrown exceptions
    public int deleteAndReturnCount()
    {
        // Check access
        _virtualHost.getSecurityManager().authoriseDelete(this);

        if (!_deleted.getAndSet(true))
        {

            final ArrayList<BindingImpl> bindingCopy = new ArrayList<BindingImpl>(_bindings);

            for (BindingImpl b : bindingCopy)
            {
                b.delete();
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


            for(final QueueEntry entry : entries)
            {
                // TODO log requeues with a post enqueue action
                int requeues = entry.routeToAlternate(null, txn);

                if(requeues == 0)
                {
                    // TODO log discard
                }
            }

            txn.commit();

            preSetAlternateExchange();

            for (Action<? super AMQQueue> task : _deleteTaskList)
            {
                task.performAction(this);
            }

            _deleteTaskList.clear();
            close();
            deleted();
            //Log Queue Deletion
            getEventLogger().message(_logSubject, QueueMessages.DELETED());

        }
        return getQueueDepthMessages();

    }

    @Override
    protected void onClose()
    {
        super.onClose();
        if (!_stopped.getAndSet(true))
        {
            ReferenceCountingExecutorService.getInstance().releaseExecutorService();
        }
    }

    public void checkCapacity(AMQSessionModel channel)
    {
        if(_queueFlowControlSizeBytes != 0l)
        {
            if(_atomicQueueSize.get() > _queueFlowControlSizeBytes)
            {
                _overfull.set(true);
                //Overfull log message
                getEventLogger().message(_logSubject, QueueMessages.OVERFULL(_atomicQueueSize.get(),
                                                                             _queueFlowControlSizeBytes));

                _blockedChannels.add(channel);

                channel.block(this);

                if(_atomicQueueSize.get() <= _queueFlowResumeSizeBytes)
                {

                    //Underfull log message
                    getEventLogger().message(_logSubject,
                                             QueueMessages.UNDERFULL(_atomicQueueSize.get(), _queueFlowResumeSizeBytes));

                   channel.unblock(this);
                   _blockedChannels.remove(channel);

                }
            }



        }
    }

    private void checkCapacity()
    {
        if(_queueFlowControlSizeBytes != 0L)
        {
            if(_overfull.get() && _atomicQueueSize.get() <= _queueFlowResumeSizeBytes)
            {
                if(_overfull.compareAndSet(true,false))
                {//Underfull log message
                    getEventLogger().message(_logSubject,
                                             QueueMessages.UNDERFULL(_atomicQueueSize.get(), _queueFlowResumeSizeBytes));
                }

                for(final AMQSessionModel blockedChannel : _blockedChannels)
                {
                    blockedChannel.unblock(this);
                    _blockedChannels.remove(blockedChannel);
                }
            }
        }
    }

    private final QueueRunner _queueRunner = new QueueRunner(this);

    public void deliverAsync()
    {
        _stateChangeCount.incrementAndGet();

        _queueRunner.execute();

    }

    public void deliverAsync(QueueConsumer<?> sub)
    {
        if(_exclusiveSubscriber == null)
        {
            deliverAsync();
        }
        else
        {
            SubFlushRunner flusher = sub.getRunner();
            flusher.execute();
        }

    }

    void flushConsumer(QueueConsumer<?> sub)
    {

        flushConsumer(sub, Long.MAX_VALUE);
    }

    boolean flushConsumer(QueueConsumer<?> sub, long iterations)
    {
        boolean atTail = false;
        final boolean keepSendLockHeld = iterations <=  AbstractQueue.MAX_ASYNC_DELIVERIES;
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
    private boolean attemptDelivery(QueueConsumer<?> sub, boolean batch)
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
                        if (sub.acquires() && !assign(sub, node))
                        {
                            // restore credit here that would have been taken away by wouldSuspend since we didn't manage
                            // to acquire the entry for this consumer
                            sub.restoreCredit(node);
                        }
                        else
                        {
                            deliverMessage(sub, node, batch);
                            if(sub.acquires())
                            {
                                node.unlockAcquisition();
                            }
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
            atTail = (node == null) || (getEntries().next(node) == null);
        }
        return atTail || !subActive;
    }

    protected void advanceAllConsumers()
    {
        QueueConsumerList.ConsumerNodeIterator consumerNodeIterator = _consumerList.iterator();
        while (consumerNodeIterator.advance())
        {
            QueueConsumerList.ConsumerNode subNode = consumerNodeIterator.getNode();
            QueueConsumer sub = subNode.getConsumer();
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

    private QueueEntry getNextAvailableEntry(final QueueConsumer sub)
    {
        QueueContext context = sub.getQueueContext();
        if(context != null)
        {
            QueueEntry lastSeen = context.getLastSeenEntry();
            QueueEntry releasedNode = context.getReleasedEntry();

            QueueEntry node = (releasedNode != null && lastSeen.compareTo(releasedNode)>=0) ? releasedNode : getEntries()
                    .next(lastSeen);

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
                node = (releasedNode != null && lastSeen.compareTo(releasedNode)>=0) ? releasedNode : getEntries().next(
                        lastSeen);
            }
            return node;
        }
        else
        {
            return null;
        }
    }

    public boolean isEntryAheadOfConsumer(QueueEntry entry, QueueConsumer<?> sub)
    {
        QueueContext context = sub.getQueueContext();
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

            QueueConsumerList.ConsumerNodeIterator consumerNodeIterator = _consumerList.iterator();
            //iterate over the subscribers and try to advance their pointer
            while (consumerNodeIterator.advance())
            {
                QueueConsumer<?> sub = consumerNodeIterator.getNode().getConsumer();
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
        QueueEntryIterator queueListIterator = getEntries().iterator();

        long totalSize = getContextValue(Long.class, QUEUE_ESTIMATED_MESSAGE_MEMORY_OVERHEAD) * getQueueDepthMessages();
        long targetSize = _targetQueueSize.get();

        while (queueListIterator.advance())
        {
            QueueEntry node = queueListIterator.getNode();
            // Only process nodes that are not currently deleted and not dequeued
            if (!node.isDeleted())
            {
                // If the node has expired then acquire it
                if (node.expired())
                {
                    boolean acquiredForDequeueing = node.acquire();
                    if(!acquiredForDequeueing && node.getDeliveredToConsumer())
                    {
                        QueueConsumer consumer = (QueueConsumer) node.getDeliveredConsumer();
                        acquiredForDequeueing = node.removeAcquisitionFromConsumer(consumer);
                        if(acquiredForDequeueing)
                        {
                            consumer.acquisitionRemoved(node);
                        }
                    }

                    if(acquiredForDequeueing)
                    {
                        if (_logger.isDebugEnabled())
                        {
                            _logger.debug("Dequeuing expired node " + node);
                        }
                        // Then dequeue it.
                        dequeueEntry(node);
                    }
                }
                else
                {
                    // There is a chance that the node could be deleted by
                    // the time the check actually occurs. So verify we
                    // can actually get the message to perform the check.
                    ServerMessage msg = node.getMessage();

                    if (msg != null)
                    {
                        totalSize += msg.getSize();
                        StoredMessage storedMessage = msg.getStoredMessage();
                        if(totalSize > targetSize && storedMessage.isInMemory())
                        {
                            storedMessage.flowToDisk();
                        }
                        checkForNotification(msg);
                    }
                }
            }
        }

    }

    @Override
    public long getPotentialMemoryFootprint()
    {
        return Math.max(getContextValue(Long.class,QUEUE_MINIMUM_ESTIMATED_MEMORY_FOOTPRINT),
                        getQueueDepthBytes() + getContextValue(Long.class, QUEUE_ESTIMATED_MESSAGE_MEMORY_OVERHEAD) * getQueueDepthMessages());
    }

    public long getAlertRepeatGap()
    {
        return _alertRepeatGap;
    }

    public long getAlertThresholdMessageAge()
    {
        return _alertThresholdMessageAge;
    }

    public long getAlertThresholdQueueDepthMessages()
    {
        return _alertThresholdQueueDepthMessages;
    }

    private void updateAlertChecks()
    {
        updateNotificationCheck(getAlertThresholdQueueDepthMessages(), NotificationCheck.MESSAGE_COUNT_ALERT);
        updateNotificationCheck(getAlertThresholdQueueDepthBytes(), NotificationCheck.QUEUE_DEPTH_ALERT);
        updateNotificationCheck(getAlertThresholdMessageAge(), NotificationCheck.MESSAGE_AGE_ALERT);
        updateNotificationCheck(getAlertThresholdMessageSize(), NotificationCheck.MESSAGE_SIZE_ALERT);
    }

    private void updateNotificationCheck(final long checkValue, final NotificationCheck notificationCheck)
    {
        if (checkValue == 0L)
        {
            _notificationChecks.remove(notificationCheck);
        }
        else
        {
            _notificationChecks.add(notificationCheck);
        }
    }

    public long getAlertThresholdQueueDepthBytes()
    {
        return _alertThresholdQueueDepthBytes;
    }

    public long getAlertThresholdMessageSize()
    {
        return _alertThresholdMessageSize;
    }

    public long getQueueFlowControlSizeBytes()
    {
        return _queueFlowControlSizeBytes;
    }

    public long getQueueFlowResumeSizeBytes()
    {
        return _queueFlowResumeSizeBytes;
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

    private final class QueueEntryListener implements StateChangeListener<MessageInstance, QueueEntry.State>
    {

        private final QueueConsumer<?> _sub;

        public QueueEntryListener(final QueueConsumer<?> sub)
        {
            _sub = sub;
        }

        public boolean equals(Object o)
        {
            return o instanceof AbstractQueue.QueueEntryListener
                    && _sub == ((QueueEntryListener) o)._sub;
        }

        public int hashCode()
        {
            return System.identityHashCode(_sub);
        }

        public void stateChanged(MessageInstance entry, QueueEntry.State oldSate, QueueEntry.State newState)
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
        QueueEntryIterator it = getEntries().iterator();
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

    public long getTotalEnqueuedBytes()
    {
        return _enqueueSize.get();
    }

    public long getTotalDequeuedBytes()
    {
        return _dequeueSize.get();
    }

    public long getPersistentEnqueuedBytes()
    {
        return _persistentMessageEnqueueSize.get();
    }

    public long getPersistentDequeuedBytes()
    {
        return _persistentMessageDequeueSize.get();
    }

    public long getPersistentEnqueuedMessages()
    {
        return _persistentMessageEnqueueCount.get();
    }

    public long getPersistentDequeuedMessages()
    {
        return _persistentMessageDequeueCount.get();
    }


    @Override
    public String toString()
    {
        return getName();
    }

    public long getUnacknowledgedMessages()
    {
        return _unackedMsgCount.get();
    }

    public long getUnacknowledgedBytes()
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
        _unackedMsgCount.incrementAndGet();
        _unackedMsgBytes.addAndGet(entry.getSize());
    }

    @Override
    public int getMaximumDeliveryAttempts()
    {
        return _maximumDeliveryAttempts;
    }

    /**
     * Checks if there is any notification to send to the listeners
     */
    private void checkForNotification(ServerMessage<?> msg)
    {
        final Set<NotificationCheck> notificationChecks = getNotificationChecks();
        QueueNotificationListener  listener = _notificationListener;
        if(listener == null)
        {
            listener = NULL_NOTIFICATION_LISTENER;
        }
        if(listener != null && !notificationChecks.isEmpty())
        {
            final long currentTime = System.currentTimeMillis();
            final long thresholdTime = currentTime - getAlertRepeatGap();

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

    public void setNotificationListener(QueueNotificationListener  listener)
    {
        _notificationListener = listener;
    }

    public final  <M extends ServerMessage<? extends StorableMessageMetaData>> int send(final M message,
                                                                                        final String routingAddress,
                                                                                        final InstanceProperties instanceProperties,
                                                                                        final ServerTransaction txn,
                                                                                        final Action<? super MessageInstance> postEnqueueAction)
    {
        if(!message.isReferenced(this))
        {
            txn.enqueue(this, message, new ServerTransaction.Action()
            {
                MessageReference _reference = message.newReference();

                public void postCommit()
                {
                    try
                    {
                        AbstractQueue.this.enqueue(message, postEnqueueAction);
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
        else
        {
            return 0;
        }

    }

    @Override
    public boolean verifySessionAccess(final AMQSessionModel<?, ?> session)
    {
        boolean allowed;
        switch(_exclusive)
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
                throw new ServerScopedRuntimeException("Unknown exclusivity policy " + _exclusive);
        }
        return allowed;
    }

    private synchronized void updateExclusivityPolicy(ExclusivityPolicy desiredPolicy)
            throws ExistingConsumerPreventsExclusive
    {
        if(desiredPolicy == null)
        {
            desiredPolicy = ExclusivityPolicy.NONE;
        }

        if(desiredPolicy != _exclusive)
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
            _exclusive = desiredPolicy;
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

        switch(_exclusive)
        {
            case NONE:
            case PRINCIPAL:
            case CONTAINER:
            case CONNECTION:
                AMQSessionModel session = null;
                for(ConsumerImpl c : getConsumers())
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
        switch(_exclusive)
        {
            case NONE:
            case CONTAINER:
            case PRINCIPAL:
                AMQConnectionModel con = null;
                for(ConsumerImpl c : getConsumers())
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
        switch(_exclusive)
        {
            case NONE:
            case PRINCIPAL:
                String containerID = null;
                for(ConsumerImpl c : getConsumers())
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
        switch(_exclusive)
        {
            case NONE:
            case CONTAINER:
                Principal principal = null;
                for(ConsumerImpl c : getConsumers())
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
            if(AbstractQueue.this._exclusiveOwner == _lifetimeObject)
            {
                AbstractQueue.this._exclusiveOwner = null;
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

    //=============

    @StateTransition(currentState = State.UNINITIALIZED, desiredState = State.ACTIVE)
    private void activate()
    {
        setState(State.ACTIVE);
    }

    @StateTransition(currentState = State.UNINITIALIZED, desiredState = State.DELETED)
    private void doDeleteBeforeInitialize()
    {
        preSetAlternateExchange();
        setState(State.DELETED);
    }

    @StateTransition(currentState = State.ACTIVE, desiredState = State.DELETED)
    private void doDelete()
    {
        _virtualHost.removeQueue(this);
        preSetAlternateExchange();
        setState(State.DELETED);
    }


    @Override
    public ExclusivityPolicy getExclusive()
    {
        return _exclusive;
    }

    @Override
    public boolean isNoLocal()
    {
        return _noLocal;
    }

    @Override
    public String getMessageGroupKey()
    {
        return _messageGroupKey;
    }

    @Override
    public boolean isMessageGroupSharedGroups()
    {
        return _messageGroupSharedGroups;
    }

    @Override
    public String getMessageGroupDefaultGroup()
    {
        return _messageGroupDefaultGroup;
    }

    @Override
    public int getMaximumDistinctGroups()
    {
        return _maximumDistinctGroups;
    }

    @Override
    public boolean isQueueFlowStopped()
    {
        return _overfull.get();
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(final Class<C> clazz)
    {
        if(clazz == Binding.class)
        {
            return (Collection<C>) getBindings();
        }
        else if(clazz == org.apache.qpid.server.model.Consumer.class)
        {
            return (Collection<C>) getConsumers();
        }
        else return Collections.emptySet();
    }

    @Override
    protected <C extends ConfiguredObject> C addChild(final Class<C> childClass,
                                                      final Map<String, Object> attributes,
                                                      final ConfiguredObject... otherParents)
    {
        if(childClass == Binding.class && otherParents.length == 1 && otherParents[0] instanceof Exchange)
        {
            final String bindingKey = (String) attributes.get("name");
            ((ExchangeImpl)otherParents[0]).addBinding(bindingKey, this,
                                                       (Map<String,Object>) attributes.get(Binding.ARGUMENTS));
            for(Binding binding : _bindings)
            {
                if(binding.getExchange() == otherParents[0] && binding.getName().equals(bindingKey))
                {
                    return (C) binding;
                }
            }
            return null;
        }
        return super.addChild(childClass, attributes, otherParents);
    }

    @Override
    public boolean changeAttribute(String name, Object expected, Object desired) throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        try
        {
            if(EXCLUSIVE.equals(name))
            {
                ExclusivityPolicy existingPolicy = getExclusive();
                if(super.changeAttribute(name, expected, desired))
                {
                    try
                    {
                        if(existingPolicy != _exclusive)
                        {
                            ExclusivityPolicy newPolicy = _exclusive;
                            _exclusive = existingPolicy;
                            updateExclusivityPolicy(newPolicy);
                        }
                        return true;
                    }
                    catch (ExistingConsumerPreventsExclusive existingConsumerPreventsExclusive)
                    {
                        throw new IllegalArgumentException("Unable to set exclusivity policy to " + desired + " as an existing combinations of consumers prevents this");
                    }
                }
                return false;
            }

            return super.changeAttribute(name, expected, desired);
        }
        finally
        {
            if (isDurable() && getState() != State.DELETED)
            {
                this.getVirtualHost().getDurableConfigurationStore().update(false, asObjectRecord());
            }
        }
    }

    @Override
    protected void authoriseSetAttributes(ConfiguredObject<?> modified, Set<String> attributes) throws AccessControlException
    {
        _virtualHost.getSecurityManager().authoriseUpdate(this);
    }

    private static final String[] NON_NEGATIVE_NUMBERS = {
        ALERT_REPEAT_GAP,
        ALERT_THRESHOLD_MESSAGE_AGE,
        ALERT_THRESHOLD_MESSAGE_SIZE,
        ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES,
        ALERT_THRESHOLD_QUEUE_DEPTH_BYTES,
        QUEUE_FLOW_CONTROL_SIZE_BYTES,
        QUEUE_FLOW_RESUME_SIZE_BYTES,
        MAXIMUM_DELIVERY_ATTEMPTS
    };

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);
        Queue<?> queue = (Queue) proxyForValidation;
        long queueFlowControlSize = queue.getQueueFlowControlSizeBytes();
        long queueFlowControlResumeSize = queue.getQueueFlowResumeSizeBytes();
        if (queueFlowControlResumeSize > queueFlowControlSize)
        {
            throw new IllegalConfigurationException("Flow resume size can't be greater than flow control size");
        }


        for (String attrName : NON_NEGATIVE_NUMBERS)
        {
            if (changedAttributes.contains(attrName))
            {
                Object value = queue.getAttribute(attrName);
                if (!(value instanceof Number) || ((Number) value).longValue() < 0)
                {
                    throw new IllegalConfigurationException(
                            "Only positive integer value can be specified for the attribute "
                            + attrName);
                }
            }
        }
    }

    private class DeletedChildListener implements ConfigurationChangeListener
    {
        @Override
        public void stateChanged(final ConfiguredObject object, final State oldState, final State newState)
        {
            if(newState == State.DELETED)
            {
                AbstractQueue.this.childRemoved(object);
            }
        }

        @Override
        public void childAdded(final ConfiguredObject object, final ConfiguredObject child)
        {

        }

        @Override
        public void childRemoved(final ConfiguredObject object, final ConfiguredObject child)
        {

        }

        @Override
        public void attributeSet(final ConfiguredObject object,
                                 final String attributeName,
                                 final Object oldAttributeValue,
                                 final Object newAttributeValue)
        {

        }
    }

    private static class EnqueueRequest
    {
        private final MessageReference<?> _message;
        private final Action<? super MessageInstance> _action;

        public EnqueueRequest(final ServerMessage message,
                              final Action<? super MessageInstance> action)
        {
            _message = message.newReference();
            _action = action;
        }

        public MessageReference<?> getMessage()
        {
            return _message;
        }

        public Action<? super MessageInstance> getAction()
        {
            return _action;
        }
    }
}
