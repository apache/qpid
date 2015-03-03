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
package org.apache.qpid.server.virtualhost;

import java.io.File;
import java.security.AccessControlException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.pool.SuppressingInheritedAccessControlContextThreadFactory;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.connection.ConnectionRegistry;
import org.apache.qpid.server.connection.IConnectionRegistry;
import org.apache.qpid.server.exchange.DefaultDestination;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.messages.MessageStoreMessages;
import org.apache.qpid.server.logging.messages.VirtualHostMessages;
import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageNode;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.*;
import org.apache.qpid.server.model.adapter.ConnectionAdapter;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.plugin.ConnectionValidator;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.plugin.SystemNodeCreator;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.LinkRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueConsumer;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.Event;
import org.apache.qpid.server.store.EventListener;
import org.apache.qpid.server.store.GenericRecoverer;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreProvider;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;
import org.apache.qpid.server.txn.DtxRegistry;
import org.apache.qpid.server.txn.LocalTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.server.util.MapValueConverter;

public abstract class AbstractVirtualHost<X extends AbstractVirtualHost<X>> extends AbstractConfiguredObject<X>
        implements VirtualHostImpl<X, AMQQueue<?>, ExchangeImpl<?>>, IConnectionRegistry.RegistryChangeListener, EventListener
{
    private final Collection<ConnectionValidator> _connectionValidators = new ArrayList<>();

    private static enum BlockingType { STORE, FILESYSTEM };

    private static final String USE_ASYNC_RECOVERY = "use_async_message_store_recovery";

    public static final String DEFAULT_DLQ_NAME_SUFFIX = "_DLQ";
    public static final String DLQ_ROUTING_KEY = "dlq";
    public static final String CREATE_DLQ_ON_CREATION = "x-qpid-dlq-enabled"; // TODO - this value should change
    private static final int MAX_LENGTH = 255;

    private static final Logger _logger = Logger.getLogger(AbstractVirtualHost.class);

    private static final int HOUSEKEEPING_SHUTDOWN_TIMEOUT = 5;

    private ScheduledThreadPoolExecutor _houseKeepingTasks;

    private final Broker<?> _broker;

    private final ConnectionRegistry _connectionRegistry;

    private final DtxRegistry _dtxRegistry;

    private final SystemNodeRegistry _systemNodeRegistry = new SystemNodeRegistry();

    private final StatisticsCounter _messagesDelivered, _dataDelivered, _messagesReceived, _dataReceived;

    private final Map<String, LinkRegistry> _linkRegistry = new HashMap<String, LinkRegistry>();
    private AtomicBoolean _blocked = new AtomicBoolean();

    private final Map<String, MessageDestination> _systemNodeDestinations =
            Collections.synchronizedMap(new HashMap<String,MessageDestination>());

    private final Map<String, MessageSource> _systemNodeSources =
            Collections.synchronizedMap(new HashMap<String,MessageSource>());

    private final EventLogger _eventLogger;

    private final List<VirtualHostAlias> _aliases = new ArrayList<VirtualHostAlias>();
    private final AtomicBoolean _deleted = new AtomicBoolean();
    private final VirtualHostNode<?> _virtualHostNode;

    private final AtomicLong _targetSize = new AtomicLong(1024*1024);

    private MessageStoreLogSubject _messageStoreLogSubject;

    private final Set<BlockingType> _blockingReasons = Collections.synchronizedSet(EnumSet.noneOf(BlockingType.class));


    @ManagedAttributeField
    private boolean _queue_deadLetterQueueEnabled;

    @ManagedAttributeField
    private long _housekeepingCheckPeriod;

    @ManagedAttributeField
    private long _storeTransactionIdleTimeoutClose;

    @ManagedAttributeField
    private long _storeTransactionIdleTimeoutWarn;

    @ManagedAttributeField
    private long _storeTransactionOpenTimeoutClose;

    @ManagedAttributeField
    private long _storeTransactionOpenTimeoutWarn;

    @ManagedAttributeField
    private int _housekeepingThreadCount;

    @ManagedAttributeField
    private List<String> _enabledConnectionValidators;

    @ManagedAttributeField
    private List<String> _disabledConnectionValidators;

    @ManagedAttributeField
    private List<String> _globalAddressDomains;

    private boolean _useAsyncRecoverer;

    private MessageDestination _defaultDestination;

    private MessageStore _messageStore;
    private MessageStoreRecoverer _messageStoreRecoverer;
    private final FileSystemSpaceChecker _fileSystemSpaceChecker = new FileSystemSpaceChecker();
    private int _fileSystemMaxUsagePercent;

    public AbstractVirtualHost(final Map<String, Object> attributes, VirtualHostNode<?> virtualHostNode)
    {
        super(parentsMap(virtualHostNode), attributes);
        _broker = virtualHostNode.getParent(Broker.class);
        _virtualHostNode = virtualHostNode;

        _dtxRegistry = new DtxRegistry();

        _eventLogger = _broker.getParent(SystemConfig.class).getEventLogger();

        _eventLogger.message(VirtualHostMessages.CREATED(getName()));

        _connectionRegistry = new ConnectionRegistry();
        _connectionRegistry.addRegistryChangeListener(this);

        _defaultDestination = new DefaultDestination(this);

        _messagesDelivered = new StatisticsCounter("messages-delivered-" + getName());
        _dataDelivered = new StatisticsCounter("bytes-delivered-" + getName());
        _messagesReceived = new StatisticsCounter("messages-received-" + getName());
        _dataReceived = new StatisticsCounter("bytes-received-" + getName());
    }

    public void onValidate()
    {
        super.onValidate();
        String name = getName();
        if (name == null || "".equals(name.trim()))
        {
            throw new IllegalConfigurationException("Virtual host name must be specified");
        }
        String type = getType();
        if (type == null || "".equals(type.trim()))
        {
            throw new IllegalConfigurationException("Virtual host type must be specified");
        }
        if(!isDurable())
        {
            throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
        }
        if(getGlobalAddressDomains() != null)
        {
            for(String domain : getGlobalAddressDomains())
            {
                validateGlobalAddressDomain(domain);
            }
        }
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);
        if(changedAttributes.contains(DURABLE) && !proxyForValidation.isDurable())
        {
            throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
        }
        if (changedAttributes.contains(DESIRED_STATE))
        {
            if (State.DELETED == proxyForValidation.getDesiredState()
                && getName().equals(_broker.getDefaultVirtualHost()))
            {
                throw new IntegrityViolationException("Cannot delete default virtual host '" + getName() + "'");
            }
        }
        if(changedAttributes.contains(GLOBAL_ADDRESS_DOMAINS))
        {
            VirtualHost<?, ?, ?> virtualHost = (VirtualHost<?, ?, ?>) proxyForValidation;
            if(virtualHost.getGlobalAddressDomains() != null)
            {
                for(String name : virtualHost.getGlobalAddressDomains())
                {
                    validateGlobalAddressDomain(name);
                }
            }
        }
    }

    private void validateGlobalAddressDomain(final String name)
    {
        String regex = "/(/?)([\\w_\\-:.\\$]+/)*[\\w_\\-:.\\$]+";
        if(!name.matches(regex))
        {
            throw new IllegalArgumentException("'"+name+"' is not a valid global address domain");
        }
    }

    @Override
    public MessageStore getMessageStore()
    {
        return _messageStore;
    }

    @Override
    public void validateOnCreate()
    {
        super.validateOnCreate();
        validateMessageStoreCreation();
        if(getGlobalAddressDomains() != null)
        {
            for(String name : getGlobalAddressDomains())
            {
                validateGlobalAddressDomain(name);
            }
        }
    }



    private void validateMessageStoreCreation()
    {
        MessageStore store = createMessageStore();
        if (store != null)
        {
            try
            {
                store.openMessageStore(this);
            }
            catch (Exception e)
            {
                throw new IllegalConfigurationException("Cannot open virtual host message store:" + e.getMessage(), e);
            }
            finally
            {
                try
                {
                    store.closeMessageStore();
                }
                catch(Exception e)
                {
                    _logger.warn("Failed to close database", e);
                }
            }
        }
    }

    @Override
    protected void onExceptionInOpen(RuntimeException e)
    {
        super.onExceptionInOpen(e);
        closeMessageStore();
    }

    @Override
    protected void onOpen()
    {
        super.onOpen();

        registerSystemNodes();

        _messageStore = createMessageStore();

        _messageStoreLogSubject = new MessageStoreLogSubject(getName(), _messageStore.getClass().getSimpleName());

        _messageStore.addEventListener(this, Event.PERSISTENT_MESSAGE_SIZE_OVERFULL);
        _messageStore.addEventListener(this, Event.PERSISTENT_MESSAGE_SIZE_UNDERFULL);

        _fileSystemMaxUsagePercent = getContextValue(Integer.class, Broker.STORE_FILESYSTEM_MAX_USAGE_PERCENT);


        QpidServiceLoader serviceLoader = new QpidServiceLoader();
        for(ConnectionValidator validator : serviceLoader.instancesOf(ConnectionValidator.class))
        {
            if((_enabledConnectionValidators.isEmpty()
                && (_disabledConnectionValidators.isEmpty()) || !_disabledConnectionValidators.contains(validator.getType()))
               || _enabledConnectionValidators.contains(validator.getType()))
            {
                _connectionValidators.add(validator);
            }

        }
    }

    private void checkVHostStateIsActive()
    {
        if (getState() != State.ACTIVE)
        {
            throw new IllegalStateException("The virtual host state of " + getState()
                                            + " does not permit this operation.");
        }
    }

    private void registerSystemNodes()
    {
        QpidServiceLoader qpidServiceLoader = new QpidServiceLoader();
        Iterable<SystemNodeCreator> factories = qpidServiceLoader.instancesOf(SystemNodeCreator.class);
        for(SystemNodeCreator creator : factories)
        {
            creator.register(_systemNodeRegistry);
        }
    }

    protected abstract MessageStore createMessageStore();

    protected boolean isStoreEmpty()
    {
        final IsStoreEmptyHandler isStoreEmptyHandler = new IsStoreEmptyHandler();

        getDurableConfigurationStore().visitConfiguredObjectRecords(isStoreEmptyHandler);

        return isStoreEmptyHandler.isEmpty();
    }

    protected void createDefaultExchanges()
    {
        Subject.doAs(getSecurityManager().getSubjectWithAddedSystemRights(), new PrivilegedAction<Void>()
        {
            @Override
            public Void run()
            {
                addStandardExchange(ExchangeDefaults.DIRECT_EXCHANGE_NAME, ExchangeDefaults.DIRECT_EXCHANGE_CLASS);
                addStandardExchange(ExchangeDefaults.TOPIC_EXCHANGE_NAME, ExchangeDefaults.TOPIC_EXCHANGE_CLASS);
                addStandardExchange(ExchangeDefaults.HEADERS_EXCHANGE_NAME, ExchangeDefaults.HEADERS_EXCHANGE_CLASS);
                addStandardExchange(ExchangeDefaults.FANOUT_EXCHANGE_NAME, ExchangeDefaults.FANOUT_EXCHANGE_CLASS);
                return null;
            }

            void addStandardExchange(String name, String type)
            {
                Map<String, Object> attributes = new HashMap<String, Object>();
                attributes.put(Exchange.NAME, name);
                attributes.put(Exchange.TYPE, type);
                attributes.put(Exchange.ID, UUIDGenerator.generateExchangeUUID(name, getName()));
                childAdded(addExchange(attributes));
            }
        });
    }

    protected MessageStoreLogSubject getMessageStoreLogSubject()
    {
        return _messageStoreLogSubject;
    }

    public IConnectionRegistry getConnectionRegistry()
    {
        return _connectionRegistry;
    }

    @Override
    protected void authoriseSetDesiredState(State desiredState) throws AccessControlException
    {
        if(desiredState == State.DELETED)
        {
            _broker.getSecurityManager().authoriseVirtualHost(getName(), Operation.DELETE);
        }
        else
        {
            _broker.getSecurityManager().authoriseVirtualHost(getName(), Operation.UPDATE);
        }
    }

    @Override
    protected void authoriseSetAttributes(ConfiguredObject<?> modified, Set<String> attributes) throws AccessControlException
    {
        _broker.getSecurityManager().authoriseVirtualHost(getName(), Operation.UPDATE);
    }

    public Collection<Connection> getConnections()
    {
        return getChildren(Connection.class);
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        if(clazz == VirtualHostAlias.class)
        {
            return (Collection<C>) getAliases();
        }
        else
        {
            return super.getChildren(clazz);
        }
    }

    @Override
    protected <C extends ConfiguredObject> C addChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        checkVHostStateIsActive();
        if(childClass == Exchange.class)
        {
            return (C) addExchange(attributes);

        }
        else if(childClass == Queue.class)
        {
            return (C) addQueue(attributes);

        }
        else if(childClass == VirtualHostAlias.class)
        {
            throw new UnsupportedOperationException();
        }
        else if(childClass == Connection.class)
        {
            throw new UnsupportedOperationException();
        }
        throw new IllegalArgumentException("Cannot create a child of class " + childClass.getSimpleName());
    }

    public Collection<String> getExchangeTypeNames()
    {
        return getObjectFactory().getSupportedTypes(Exchange.class);
    }


    @Override
    public EventLogger getEventLogger()
    {
        return _eventLogger;
    }

    @Override
    public boolean authoriseCreateConnection(final AMQConnectionModel<?, ?> connection)
    {
        getSecurityManager().authoriseCreateConnection(connection);
        for(ConnectionValidator validator : _connectionValidators)
        {
            if(!validator.validateConnectionCreation(connection))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Initialise a housekeeping task to iterate over queues cleaning expired messages with no consumers
     * and checking for idle or open transactions that have exceeded the permitted thresholds.
     *
     * @param period
     */
    private void initialiseHouseKeeping(long period)
    {
        if (period != 0L)
        {
            scheduleHouseKeepingTask(period, new VirtualHostHouseKeepingTask());
        }
    }

    protected void shutdownHouseKeeping()
    {
        if(_houseKeepingTasks != null)
        {
            _houseKeepingTasks.shutdown();

            try
            {
                if (!_houseKeepingTasks.awaitTermination(HOUSEKEEPING_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS))
                {
                    _houseKeepingTasks.shutdownNow();
                }
            }
            catch (InterruptedException e)
            {
                _logger.warn("Interrupted during Housekeeping shutdown:", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    protected void removeHouseKeepingTasks()
    {
        BlockingQueue<Runnable> taskQueue = _houseKeepingTasks.getQueue();
        for (final Runnable runnable : taskQueue)
        {
            _houseKeepingTasks.remove(runnable);
        }
    }

    /**
     * Allow other broker components to register a HouseKeepingTask
     *
     * @param period How often this task should run, in ms.
     * @param task The task to run.
     */
    public void scheduleHouseKeepingTask(long period, HouseKeepingTask task)
    {
        _houseKeepingTasks.scheduleAtFixedRate(task, period / 2, period,
                                               TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> scheduleTask(long delay, Runnable task)
    {
        return _houseKeepingTasks.schedule(task, delay, TimeUnit.MILLISECONDS);
    }

    public long getHouseKeepingTaskCount()
    {
        return _houseKeepingTasks.getTaskCount();
    }

    public long getHouseKeepingCompletedTaskCount()
    {
        return _houseKeepingTasks.getCompletedTaskCount();
    }

    public int getHouseKeepingPoolSize()
    {
        return _houseKeepingTasks.getCorePoolSize();
    }

    public void setHouseKeepingPoolSize(int newSize)
    {
        _houseKeepingTasks.setCorePoolSize(newSize);
    }


    public int getHouseKeepingActiveCount()
    {
        return _houseKeepingTasks.getActiveCount();
    }

    @Override
    public List<String> getEnabledConnectionValidators()
    {
        return _enabledConnectionValidators;
    }

    @Override
    public List<String> getDisabledConnectionValidators()
    {
        return _disabledConnectionValidators;
    }

    @Override
    public List<String> getGlobalAddressDomains()
    {
        return _globalAddressDomains;
    }

    @Override
    public AMQQueue<?> getQueue(String name)
    {
        AMQQueue<?> childByName = (AMQQueue<?>) getChildByName(Queue.class, name);
        if(childByName == null && getGlobalAddressDomains() != null)
        {
            for(String domain : getGlobalAddressDomains())
            {
                if(name.startsWith(domain + "/"))
                {
                    childByName = (AMQQueue<?>) getChildByName(Queue.class,name.substring(domain.length()));
                    if(childByName != null)
                    {
                        break;
                    }
                }
            }
        }
        return childByName;
    }

    @Override
    public MessageSource getMessageSource(final String name)
    {
        MessageSource systemSource = _systemNodeSources.get(name);
        return systemSource == null ? getQueue(name) : systemSource;
    }

    @Override
    public AMQQueue<?> getQueue(UUID id)
    {
        return (AMQQueue<?>) getChildById(Queue.class, id);
    }

    @Override
    public Collection<AMQQueue<?>> getQueues()
    {
        Collection children = getChildren(Queue.class);
        return children;
    }

    @Override
    public int removeQueue(AMQQueue<?> queue)
    {
        int purged = queue.deleteAndReturnCount();

        return purged;
}

    public AMQQueue<?> createQueue(Map<String, Object> attributes) throws QueueExistsException
    {
        checkVHostStateIsActive();

        AMQQueue<?> queue = addQueue(attributes);
        childAdded(queue);
        return queue;
    }

    private AMQQueue<?> addQueue(Map<String, Object> attributes) throws QueueExistsException
    {
        if (shouldCreateDLQ(attributes))
        {
            // TODO - this isn't really correct - what if the name has ${foo} in it?
            String queueName = String.valueOf(attributes.get(Queue.NAME));
            validateDLNames(queueName);
            String altExchangeName = createDLQ(queueName);
            attributes = new LinkedHashMap<String, Object>(attributes);
            attributes.put(Queue.ALTERNATE_EXCHANGE, altExchangeName);
        }
        return addQueueWithoutDLQ(attributes);
    }

    private AMQQueue<?> addQueueWithoutDLQ(Map<String, Object> attributes) throws QueueExistsException
    {
        try
        {
            return (AMQQueue) getObjectFactory().create(Queue.class, attributes, this);
        }
        catch (DuplicateNameException e)
        {
            throw new QueueExistsException(getQueue(e.getName()));
        }

    }


    @Override
    public MessageDestination getMessageDestination(final String name)
    {
        MessageDestination destination = _systemNodeDestinations.get(name);
        return destination == null ? getExchange(name) : destination;
    }

    @Override
    public ExchangeImpl getExchange(String name)
    {
        ExchangeImpl childByName = getChildByName(ExchangeImpl.class, name);
        if(childByName == null && getGlobalAddressDomains() != null)
        {
            for(String domain : getGlobalAddressDomains())
            {
                if(name.startsWith(domain + "/"))
                {
                    childByName = getChildByName(ExchangeImpl.class,name.substring(domain.length()));
                    if(childByName != null)
                    {
                        break;
                    }
                }
            }
        }
        return childByName;
    }

    @Override
    public ExchangeImpl getExchange(UUID id)
    {
        return getChildById(ExchangeImpl.class, id);
    }

    @Override
    public MessageDestination getDefaultDestination()
    {
        return _defaultDestination;
    }

    @Override
    public Collection<ExchangeImpl<?>> getExchanges()
    {
        Collection children = getChildren(Exchange.class);
        return children;
    }


    @Override
    public ExchangeImpl createExchange(Map<String,Object> attributes)
            throws ExchangeExistsException, ReservedExchangeNameException,
                   NoFactoryForTypeException
    {
        checkVHostStateIsActive();
        ExchangeImpl child = addExchange(attributes);
        childAdded(child);
        return child;
    }


    private ExchangeImpl addExchange(Map<String,Object> attributes)
            throws ExchangeExistsException, ReservedExchangeNameException,
                   NoFactoryForTypeException
    {
        try
        {
            return (ExchangeImpl) getObjectFactory().create(Exchange.class, attributes, this);
        }
        catch (DuplicateNameException e)
        {
            throw new ExchangeExistsException(getExchange(e.getName()));
        }

    }

    @Override
    public void removeExchange(ExchangeImpl exchange, boolean force)
            throws ExchangeIsAlternateException, RequiredExchangeException
    {
        exchange.deleteWithChecks();
    }

    @Override
    public String getLocalAddress(final String routingAddress)
    {
        String localAddress = routingAddress;
        if(getGlobalAddressDomains() != null)
        {
            for(String domain : getGlobalAddressDomains())
            {
                if(localAddress.length() > routingAddress.length() - domain.length() && routingAddress.startsWith(domain + "/"))
                {
                    localAddress = routingAddress.substring(domain.length());
                }
            }
        }
        return localAddress;
    }

    public SecurityManager getSecurityManager()
    {
        return _broker.getSecurityManager();
    }

    @Override
    protected void beforeClose()
    {
        setState(State.UNAVAILABLE);
    }

    @Override
    protected void onClose()
    {
        //Stop Connections
        _connectionRegistry.close();
        _dtxRegistry.close();
        closeMessageStore();
        shutdownHouseKeeping();

        _eventLogger.message(VirtualHostMessages.CLOSED(getName()));
    }

    private void closeMessageStore()
    {
        if (getMessageStore() != null)
        {
            try
            {
                if (_messageStoreRecoverer != null)
                {
                    _messageStoreRecoverer.cancel();
                }

                getMessageStore().closeMessageStore();

            }
            catch (StoreException e)
            {
                _logger.error("Failed to close message store", e);
            }

            if (!(_virtualHostNode.getConfigurationStore() instanceof MessageStoreProvider))
            {
                getEventLogger().message(getMessageStoreLogSubject(), MessageStoreMessages.CLOSED());
            }
        }
    }

    public void registerMessageDelivered(long messageSize)
    {
        _messagesDelivered.registerEvent(1L);
        _dataDelivered.registerEvent(messageSize);
        _broker.registerMessageDelivered(messageSize);
    }

    public void registerMessageReceived(long messageSize, long timestamp)
    {
        _messagesReceived.registerEvent(1L, timestamp);
        _dataReceived.registerEvent(messageSize, timestamp);
        _broker.registerMessageReceived(messageSize, timestamp);
    }

    public StatisticsCounter getMessageReceiptStatistics()
    {
        return _messagesReceived;
    }

    public StatisticsCounter getDataReceiptStatistics()
    {
        return _dataReceived;
    }

    public StatisticsCounter getMessageDeliveryStatistics()
    {
        return _messagesDelivered;
    }

    public StatisticsCounter getDataDeliveryStatistics()
    {
        return _dataDelivered;
    }

    public void resetStatistics()
    {
        _messagesDelivered.reset();
        _dataDelivered.reset();
        _messagesReceived.reset();
        _dataReceived.reset();

        for (AMQConnectionModel connection : _connectionRegistry.getConnections())
        {
            connection.resetStatistics();
        }
    }

    public synchronized LinkRegistry getLinkRegistry(String remoteContainerId)
    {
        LinkRegistry linkRegistry = _linkRegistry.get(remoteContainerId);
        if(linkRegistry == null)
        {
            linkRegistry = new LinkRegistry();
            _linkRegistry.put(remoteContainerId, linkRegistry);
        }
        return linkRegistry;
    }

    public DtxRegistry getDtxRegistry()
    {
        return _dtxRegistry;
    }

    private void block(BlockingType blockingType)
    {
        synchronized (_connectionRegistry)
        {
            _blockingReasons.add(blockingType);
            if(!_blocked.compareAndSet(false,true))
            {
                for(AMQConnectionModel conn : _connectionRegistry.getConnections())
                {
                    conn.block();
                }
            }
        }
    }


    private void unblock(BlockingType blockingType)
    {

        synchronized (_connectionRegistry)
        {
            _blockingReasons.remove(blockingType);
            if(_blockingReasons.isEmpty() && _blocked.compareAndSet(true,false))
            {
                for(AMQConnectionModel conn : _connectionRegistry.getConnections())
                {
                    conn.unblock();
                }
            }
        }
    }

    public void connectionRegistered(final AMQConnectionModel connection)
    {
        if(_blocked.get())
        {
            connection.block();
        }

        ConnectionAdapter c = new ConnectionAdapter(connection);
        c.create();
        childAdded(c);

    }

    public void connectionUnregistered(final AMQConnectionModel connection)
    {
        // ConnectionAdapter installs delete task to cause connection model object to delete
    }

    public void event(final Event event)
    {
        switch(event)
        {
            case PERSISTENT_MESSAGE_SIZE_OVERFULL:
                block(BlockingType.STORE);
                _eventLogger.message(getMessageStoreLogSubject(), MessageStoreMessages.OVERFULL());
                break;
            case PERSISTENT_MESSAGE_SIZE_UNDERFULL:
                unblock(BlockingType.STORE);
                _eventLogger.message(getMessageStoreLogSubject(), MessageStoreMessages.UNDERFULL());
                break;
        }
    }

    protected void reportIfError(State state)
    {
        if (state == State.ERRORED)
        {
            _eventLogger.message(VirtualHostMessages.ERRORED(getName()));
        }
    }

    private static class IsStoreEmptyHandler implements ConfiguredObjectRecordHandler
    {
        private boolean _empty = true;

        @Override
        public void begin()
        {
        }

        @Override
        public boolean handle(final ConfiguredObjectRecord record)
        {
            // if there is a non vhost record then the store is not empty and we can stop looking at the records
            _empty = record.getType().equals(VirtualHost.class.getSimpleName());
            return _empty;
        }

        @Override
        public void end()
        {

        }

        public boolean isEmpty()
        {
            return _empty;
        }
    }

    @Override
    public String getRedirectHost(final AmqpPort<?> port)
    {
        return null;
    }

    private class VirtualHostHouseKeepingTask extends HouseKeepingTask
    {
        public VirtualHostHouseKeepingTask()
        {
            super(AbstractVirtualHost.this);
        }

        public void execute()
        {
            VirtualHostNode<?> virtualHostNode = getParent(VirtualHostNode.class);
            Broker<?> broker = virtualHostNode.getParent(Broker.class);
            broker.assignTargetSizes();

            for (AMQQueue<?> q : getQueues())
            {
                if (q.getState() == State.ACTIVE)
                {
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("Checking message status for queue: "
                                      + q.getName());
                    }
                    try
                    {
                        q.checkMessageStatus();
                    }
                    catch (Exception e)
                    {
                        _logger.error("Exception in housekeeping for queue: " + q.getName(), e);
                        //Don't throw exceptions as this will stop the
                        // house keeping task from running.
                    }
                }
            }
            for (AMQConnectionModel<?,?> connection : getConnectionRegistry().getConnections())
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Checking for long running open transactions on connection " + connection);
                }
                for (AMQSessionModel<?,?> session : connection.getSessionModels())
                {
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("Checking for long running open transactions on session " + session);
                    }
                    try
                    {
                        session.checkTransactionStatus(getStoreTransactionOpenTimeoutWarn(),
                                                       getStoreTransactionOpenTimeoutClose(),
                                                       getStoreTransactionIdleTimeoutWarn(),
                                                       getStoreTransactionIdleTimeoutClose());
                    } catch (Exception e)
                    {
                        _logger.error("Exception in housekeeping for connection: " + connection.toString(), e);
                    }
                }
            }
        }
    }

    private class SystemNodeRegistry implements SystemNodeCreator.SystemNodeRegistry
    {
        @Override
        public void registerSystemNode(final MessageNode node)
        {
            if(node instanceof MessageDestination)
            {
                _systemNodeDestinations.put(node.getName(), (MessageDestination) node);
            }
            if(node instanceof MessageSource)
            {
                _systemNodeSources.put(node.getName(), (MessageSource)node);
            }
        }

        @Override
        public void removeSystemNode(final MessageNode node)
        {
            if(node instanceof MessageDestination)
            {
                _systemNodeDestinations.remove(node.getName());
            }
            if(node instanceof MessageSource)
            {
                _systemNodeSources.remove(node.getName());
            }
        }

        @Override
        public VirtualHostImpl getVirtualHost()
        {
            return AbstractVirtualHost.this;
        }

    }


    @Override
    public boolean getDefaultDeadLetterQueueEnabled()
    {
        return isQueue_deadLetterQueueEnabled();
    }


    public void executeTransaction(TransactionalOperation op)
    {
        MessageStore store = getMessageStore();
        final LocalTransaction txn = new LocalTransaction(store);

        op.withinTransaction(new Transaction()
        {
            public void dequeue(final MessageInstance messageInstance)
            {
                boolean acquired = messageInstance.acquire();
                if(!acquired && messageInstance instanceof QueueEntry)
                {
                    QueueEntry entry = (QueueEntry) messageInstance;
                    QueueConsumer consumer = (QueueConsumer) entry.getDeliveredConsumer();
                    acquired = messageInstance.removeAcquisitionFromConsumer(consumer);
                    if(acquired)
                    {
                        consumer.acquisitionRemoved((QueueEntry)messageInstance);
                    }
                }
                if(acquired)
                {
                    txn.dequeue(messageInstance.getOwningResource(), messageInstance.getMessage(), new ServerTransaction.Action()
                    {
                        public void postCommit()
                        {
                            messageInstance.delete();
                        }

                        public void onRollback()
                        {
                        }
                    });
                }
            }

            public void copy(MessageInstance entry, Queue queue)
            {
                final ServerMessage message = entry.getMessage();
                final AMQQueue toQueue = (AMQQueue)queue;

                txn.enqueue(toQueue, message, new ServerTransaction.Action()
                {
                    public void postCommit()
                    {
                        toQueue.enqueue(message, null);
                    }

                    public void onRollback()
                    {
                    }
                });

            }

            public void move(final MessageInstance entry, Queue queue)
            {
                final ServerMessage message = entry.getMessage();
                final AMQQueue toQueue = (AMQQueue)queue;
                if(entry.acquire())
                {
                    txn.enqueue(toQueue, message,
                                new ServerTransaction.Action()
                                {

                                    public void postCommit()
                                    {
                                        toQueue.enqueue(message, null);
                                    }

                                    public void onRollback()
                                    {
                                        entry.release();
                                    }
                                });
                    txn.dequeue(entry.getOwningResource(), message,
                                new ServerTransaction.Action()
                                {

                                    public void postCommit()
                                    {
                                        entry.delete();
                                    }

                                    public void onRollback()
                                    {

                                    }
                                });
                }
            }

        });
        txn.commit();
    }

    @Override
    public boolean isQueue_deadLetterQueueEnabled()
    {
        return _queue_deadLetterQueueEnabled;
    }

    @Override
    public long getHousekeepingCheckPeriod()
    {
        return _housekeepingCheckPeriod;
    }

    @Override
    public long getStoreTransactionIdleTimeoutClose()
    {
        return _storeTransactionIdleTimeoutClose;
    }

    @Override
    public long getStoreTransactionIdleTimeoutWarn()
    {
        return _storeTransactionIdleTimeoutWarn;
    }

    @Override
    public long getStoreTransactionOpenTimeoutClose()
    {
        return _storeTransactionOpenTimeoutClose;
    }

    @Override
    public long getStoreTransactionOpenTimeoutWarn()
    {
        return _storeTransactionOpenTimeoutWarn;
    }

    @Override
    public long getQueueCount()
    {
        return getQueues().size();
    }

    @Override
    public long getExchangeCount()
    {
        return getExchanges().size();
    }

    @Override
    public long getConnectionCount()
    {
        return getConnectionRegistry().getConnections().size();
    }

    @Override
    public long getBytesIn()
    {
        return getDataReceiptStatistics().getTotal();
    }

    @Override
    public long getBytesOut()
    {
        return getDataDeliveryStatistics().getTotal();
    }

    @Override
    public long getMessagesIn()
    {
        return getMessageReceiptStatistics().getTotal();
    }

    @Override
    public long getMessagesOut()
    {
        return getMessageDeliveryStatistics().getTotal();
    }

    @Override
    public int getHousekeepingThreadCount()
    {
        return _housekeepingThreadCount;
    }

    @StateTransition( currentState = { State.UNINITIALIZED, State.ACTIVE, State.ERRORED }, desiredState = State.STOPPED )
    protected void doStop()
    {
        closeChildren();
        shutdownHouseKeeping();
        closeMessageStore();
        setState(State.STOPPED);
    }

    @StateTransition( currentState = { State.ACTIVE, State.ERRORED }, desiredState = State.DELETED )
    private void doDelete()
    {
        if(_deleted.compareAndSet(false,true))
        {
            String hostName = getName();

            close();

            MessageStore ms = getMessageStore();
            if (ms != null)
            {
                try
                {
                    ms.onDelete(this);
                }
                catch (Exception e)
                {
                    _logger.warn("Exception occurred on message store deletion", e);
                }
            }
            deleted();
            setState(State.DELETED);
        }
    }

    public Collection<VirtualHostAlias> getAliases()
    {
        return Collections.unmodifiableCollection(_aliases);
    }

    private String createDLQ(final String queueName)
    {
        final String dlExchangeName = getDeadLetterExchangeName(queueName);
        final String dlQueueName = getDeadLetterQueueName(queueName);

        ExchangeImpl dlExchange = null;
        final UUID dlExchangeId = UUID.randomUUID();

        try
        {
            Map<String,Object> attributes = new HashMap<String, Object>();

            attributes.put(org.apache.qpid.server.model.Exchange.ID, dlExchangeId);
            attributes.put(org.apache.qpid.server.model.Exchange.NAME, dlExchangeName);
            attributes.put(org.apache.qpid.server.model.Exchange.TYPE, ExchangeDefaults.FANOUT_EXCHANGE_CLASS);
            attributes.put(org.apache.qpid.server.model.Exchange.DURABLE, true);
            attributes.put(org.apache.qpid.server.model.Exchange.LIFETIME_POLICY,
                           false ? LifetimePolicy.DELETE_ON_NO_LINKS : LifetimePolicy.PERMANENT);
            attributes.put(org.apache.qpid.server.model.Exchange.ALTERNATE_EXCHANGE, null);
            dlExchange = createExchange(attributes);
        }
        catch(ExchangeExistsException e)
        {
            // We're ok if the exchange already exists
            dlExchange = e.getExistingExchange();
        }
        catch (ReservedExchangeNameException | NoFactoryForTypeException | UnknownConfiguredObjectException e)
        {
            throw new ConnectionScopedRuntimeException("Attempt to create an alternate exchange for a queue failed",e);
        }

        AMQQueue dlQueue = null;

        {
            dlQueue = getQueue(dlQueueName);

            if(dlQueue == null)
            {
                //set args to disable DLQ-ing/MDC from the DLQ itself, preventing loops etc
                final Map<String, Object> args = new HashMap<String, Object>();
                args.put(CREATE_DLQ_ON_CREATION, false);
                args.put(Queue.MAXIMUM_DELIVERY_ATTEMPTS, 0);

                try
                {


                    args.put(Queue.ID, UUID.randomUUID());
                    args.put(Queue.NAME, dlQueueName);
                    args.put(Queue.DURABLE, true);
                    dlQueue = addQueueWithoutDLQ(args);
                    childAdded(dlQueue);
                }
                catch (QueueExistsException e)
                {
                    // TODO - currently theoretically for two threads to be creating a queue at the same time.
                    // All model changing operations should be moved to the task executor of the virtual host
                }
            }
        }

        //ensure the queue is bound to the exchange
        if(!dlExchange.isBound(AbstractVirtualHost.DLQ_ROUTING_KEY, dlQueue))
        {
            //actual routing key used does not matter due to use of fanout exchange,
            //but we will make the key 'dlq' as it can be logged at creation.
            dlExchange.addBinding(AbstractVirtualHost.DLQ_ROUTING_KEY, dlQueue, null);
        }
        return dlExchangeName;
    }

    private static void validateDLNames(String name)
    {
        // check if DLQ name and DLQ exchange name do not exceed 255
        String exchangeName = getDeadLetterExchangeName(name);
        if (exchangeName.length() > MAX_LENGTH)
        {
            throw new IllegalArgumentException("DL exchange name '" + exchangeName
                                               + "' length exceeds limit of " + MAX_LENGTH + " characters for queue " + name);
        }
        String queueName = getDeadLetterQueueName(name);
        if (queueName.length() > MAX_LENGTH)
        {
            throw new IllegalArgumentException("DLQ queue name '" + queueName + "' length exceeds limit of "
                                               + MAX_LENGTH + " characters for queue " + name);
        }
    }

    private boolean shouldCreateDLQ(Map<String, Object> arguments)
    {

        boolean autoDelete = MapValueConverter.getEnumAttribute(LifetimePolicy.class,
                                                                Queue.LIFETIME_POLICY,
                                                                arguments,
                                                                LifetimePolicy.PERMANENT) != LifetimePolicy.PERMANENT;

        //feature is not to be enabled for temporary queues or when explicitly disabled by argument
        if (!(autoDelete || (arguments != null && arguments.containsKey(Queue.ALTERNATE_EXCHANGE))))
        {
            boolean dlqArgumentPresent = arguments != null
                                         && arguments.containsKey(CREATE_DLQ_ON_CREATION);
            if (dlqArgumentPresent)
            {
                boolean dlqEnabled = true;
                if (dlqArgumentPresent)
                {
                    Object argument = arguments.get(CREATE_DLQ_ON_CREATION);
                    dlqEnabled = (argument instanceof Boolean && ((Boolean)argument).booleanValue())
                                 || (argument instanceof String && Boolean.parseBoolean(argument.toString()));
                }
                return dlqEnabled;
            }
            return isQueue_deadLetterQueueEnabled();
        }
        return false;
    }

    private static String getDeadLetterQueueName(String name)
    {
        return name + System.getProperty(BrokerProperties.PROPERTY_DEAD_LETTER_QUEUE_SUFFIX, AbstractVirtualHost.DEFAULT_DLQ_NAME_SUFFIX);
    }

    private static String getDeadLetterExchangeName(String name)
    {
        return name + System.getProperty(BrokerProperties.PROPERTY_DEAD_LETTER_EXCHANGE_SUFFIX, VirtualHostImpl.DEFAULT_DLE_NAME_SUFFIX);
    }

    @Override
    public String getModelVersion()
    {
        return BrokerModel.MODEL_VERSION;
    }

    @Override
    public DurableConfigurationStore getDurableConfigurationStore()
    {
        return _virtualHostNode.getConfigurationStore();
    }

    @Override
    public void setTargetSize(final long targetSize)
    {
        _targetSize.set(targetSize);
        allocateTargetSizeToQueues();
    }

    private void allocateTargetSizeToQueues()
    {
        long targetSize = _targetSize.get();
        Collection<AMQQueue<?>> queues = getQueues();
        long totalSize = calculateTotalEnqueuedSize(queues);
        if(targetSize > 0l)
        {
            for (AMQQueue<?> q : queues)
            {
                long size = (long) ((((double) q.getPotentialMemoryFootprint() / (double) totalSize))
                                             * (double) targetSize);

                q.setTargetSize(size);
            }
        }
    }

    @Override
    public long getTotalQueueDepthBytes()
    {
        return calculateTotalEnqueuedSize(getQueues());
    }

    private long calculateTotalEnqueuedSize(final Collection<AMQQueue<?>> queues)
    {
        long total = 0;
        for(AMQQueue<?> queue : queues)
        {
            total += queue.getPotentialMemoryFootprint();
        }
        return total;
    }

    @StateTransition( currentState = { State.UNINITIALIZED,State.ERRORED }, desiredState = State.ACTIVE )
    private void onActivate()
    {
        _houseKeepingTasks = new ScheduledThreadPoolExecutor(getHousekeepingThreadCount(), new SuppressingInheritedAccessControlContextThreadFactory());

        MessageStore messageStore = getMessageStore();
        messageStore.openMessageStore(this);

        startFileSystemSpaceChecking();


        if (!(_virtualHostNode.getConfigurationStore() instanceof MessageStoreProvider))
        {
            getEventLogger().message(getMessageStoreLogSubject(), MessageStoreMessages.CREATED());
            getEventLogger().message(getMessageStoreLogSubject(), MessageStoreMessages.STORE_LOCATION(messageStore.getStoreLocation()));
        }

        messageStore.upgradeStoreStructure();

        if (isStoreEmpty())
        {
            createDefaultExchanges();
        }

        if(getContextValue(Boolean.class, USE_ASYNC_RECOVERY))
        {
            _messageStoreRecoverer = new AsynchronousMessageStoreRecoverer();
        }
        else
        {
           _messageStoreRecoverer = new SynchronousMessageStoreRecoverer();
        }
        _messageStoreRecoverer.recover(this);


        State finalState = State.ERRORED;
        try
        {
            initialiseHouseKeeping(getHousekeepingCheckPeriod());
            finalState = State.ACTIVE;
        }
        finally
        {
            setState(finalState);
            reportIfError(getState());
        }
    }

    protected void startFileSystemSpaceChecking()
    {
        File storeLocationAsFile = _messageStore.getStoreLocationAsFile();
        if (storeLocationAsFile != null && _fileSystemMaxUsagePercent > 0)
        {
            _fileSystemSpaceChecker.setFileSystem(storeLocationAsFile);

            scheduleHouseKeepingTask(getHousekeepingCheckPeriod(), _fileSystemSpaceChecker);
        }
    }

    @StateTransition( currentState = { State.STOPPED, State.ERRORED }, desiredState = State.ACTIVE )
    private void onRestart()
    {
        resetStatistics();

        final List<ConfiguredObjectRecord> records = new ArrayList<>();

        // Transitioning to STOPPED will have closed all our children.  Now we are transition
        // back to ACTIVE, we need to recover and re-open them.

        getDurableConfigurationStore().visitConfiguredObjectRecords(new ConfiguredObjectRecordHandler()
        {
            @Override
            public void begin()
            {
            }

            @Override
            public boolean handle(final ConfiguredObjectRecord record)
            {
                records.add(record);
                return true;
            }

            @Override
            public void end()
            {
            }
        });

        new GenericRecoverer(this).recover(records);

        Subject.doAs(SecurityManager.getSubjectWithAddedSystemRights(), new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                applyToChildren(new Action<ConfiguredObject<?>>()
                {
                    @Override
                    public void performAction(final ConfiguredObject<?> object)
                    {
                        object.open();
                    }
                });
                return null;
            }
        });

        onActivate();
    }

    private class FileSystemSpaceChecker extends HouseKeepingTask
    {
        private boolean _fileSystemFull;
        private File _fileSystem;

        public FileSystemSpaceChecker()
        {
            super(AbstractVirtualHost.this);

        }

        @Override
        public void execute()
        {
            long totalSpace = _fileSystem.getTotalSpace();
            long freeSpace = _fileSystem.getFreeSpace();

            long usagePercent = (100l * (totalSpace - freeSpace)) / totalSpace;

            if (_fileSystemFull && (usagePercent < _fileSystemMaxUsagePercent))
            {
                _fileSystemFull = false;
                getEventLogger().message(getMessageStoreLogSubject(), VirtualHostMessages.FILESYSTEM_NOTFULL(
                        _fileSystemMaxUsagePercent));
                unblock(BlockingType.FILESYSTEM);
            }
            else if(!_fileSystemFull && usagePercent > _fileSystemMaxUsagePercent)
            {
                _fileSystemFull = true;
                getEventLogger().message(getMessageStoreLogSubject(), VirtualHostMessages.FILESYSTEM_FULL(
                        _fileSystemMaxUsagePercent));
                block(BlockingType.FILESYSTEM);
            }

        }

        public void setFileSystem(final File fileSystem)
        {
            _fileSystem = fileSystem;
        }
    }


}
