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

import java.security.AccessControlException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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

import javax.security.auth.Subject;

import org.apache.log4j.Logger;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.connection.ConnectionRegistry;
import org.apache.qpid.server.connection.IConnectionRegistry;
import org.apache.qpid.server.exchange.AMQUnknownExchangeType;
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
import org.apache.qpid.server.model.adapter.VirtualHostAliasAdapter;
import org.apache.qpid.server.plugin.MessageStoreFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.plugin.SystemNodeCreator;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.LinkRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.Event;
import org.apache.qpid.server.store.EventListener;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;
import org.apache.qpid.server.txn.DtxRegistry;
import org.apache.qpid.server.txn.LocalTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.server.util.MapValueConverter;

public abstract class AbstractVirtualHost<X extends AbstractVirtualHost<X>> extends AbstractConfiguredObject<X>
        implements VirtualHostImpl<X, AMQQueue<?>, ExchangeImpl<?>>, IConnectionRegistry.RegistryChangeListener, EventListener,
                   VirtualHost<X,AMQQueue<?>, ExchangeImpl<?>>
{
    public static final String DEFAULT_DLQ_NAME_SUFFIX = "_DLQ";
    public static final String DLQ_ROUTING_KEY = "dlq";
    public static final String CREATE_DLQ_ON_CREATION = "x-qpid-dlq-enabled"; // TODO - this value should change
    private static final int MAX_LENGTH = 255;

    private static final Logger _logger = Logger.getLogger(AbstractVirtualHost.class);

    private static final int HOUSEKEEPING_SHUTDOWN_TIMEOUT = 5;

    private final long _createTime = System.currentTimeMillis();

    private ScheduledThreadPoolExecutor _houseKeepingTasks;

    private final Broker<?> _broker;

    private final ConnectionRegistry _connectionRegistry;

    private final DtxRegistry _dtxRegistry;

    private final SystemNodeRegistry _systemNodeRegistry = new SystemNodeRegistry();

    private volatile VirtualHostState _state = VirtualHostState.INITIALISING;

    private StatisticsCounter _messagesDelivered, _dataDelivered, _messagesReceived, _dataReceived;

    private final Map<String, LinkRegistry> _linkRegistry = new HashMap<String, LinkRegistry>();
    private boolean _blocked;

    private final Map<String, MessageDestination> _systemNodeDestinations =
            Collections.synchronizedMap(new HashMap<String,MessageDestination>());

    private final Map<String, MessageSource> _systemNodeSources =
            Collections.synchronizedMap(new HashMap<String,MessageSource>());

    private final EventLogger _eventLogger;

    private final Map<AMQConnectionModel, ConnectionAdapter> _connectionAdapters =
            new HashMap<AMQConnectionModel, ConnectionAdapter>();

    private final List<VirtualHostAlias> _aliases = new ArrayList<VirtualHostAlias>();
    private final AtomicBoolean _deleted = new AtomicBoolean();
    private final VirtualHostNode<?> _virtualHostNode;

    @ManagedAttributeField
    private Map<String, Object> _messageStoreSettings;

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

    private MessageDestination _defaultDestination;

    public AbstractVirtualHost(final Map<String, Object> attributes, VirtualHostNode<?> virtualHostNode)
    {
        super(Collections.<Class<? extends ConfiguredObject>,ConfiguredObject<?>>singletonMap(VirtualHostNode.class, virtualHostNode),
              enhanceWithId(attributes), ((Broker<?>)virtualHostNode.getParent(Broker.class)).getTaskExecutor());
        _broker = virtualHostNode.getParent(Broker.class);
        _virtualHostNode = virtualHostNode;

        _dtxRegistry = new DtxRegistry();

        _eventLogger = _broker.getParent(SystemContext.class).getEventLogger();

        _eventLogger.message(VirtualHostMessages.CREATED(getName()));

        _connectionRegistry = new ConnectionRegistry();
        _connectionRegistry.addRegistryChangeListener(this);

        _defaultDestination = new DefaultDestination(this);

    }

    private static Map<String, Object> enhanceWithId(Map<String, Object> attributes)
    {
        if(attributes.get(ID) == null)
        {
            attributes = new HashMap<String, Object>(attributes);
            attributes.put(ID, UUID.randomUUID());
        }
        return attributes;
    }

    public void validate()
    {
        super.validate();
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

        DurableConfigurationStore durableConfigurationStore = _virtualHostNode.getConfigurationStore();

        boolean nodeIsMessageStoreProvider = _virtualHostNode.isMessageStoreProvider();
        if (nodeIsMessageStoreProvider)
        {
            if (!(durableConfigurationStore instanceof MessageStore))
            {
                throw new IllegalConfigurationException("Virtual host node " + _virtualHostNode.getName()
                        + " is configured as a provider of message store but the MessageStore interface is not implemented on a configuration store of type "
                        + durableConfigurationStore.getClass().getName());
            }
        }
        else
        {
            Map<String, Object> messageStoreSettings = getMessageStoreSettings();
            if (messageStoreSettings == null)
            {
                throw new IllegalConfigurationException("Message store settings are missed for VirtualHost " + getName()
                        + ". You can either configure the message store setting on the host or "
                        + (durableConfigurationStore instanceof MessageStore ?
                                " configure VirtualHostNode " + _virtualHostNode.getName() + " as a provider of message store" :
                                " change the node type to one having configuration store implementing the MessageStore inteface") );
            }
            String storeType = (String) messageStoreSettings.get(MessageStore.STORE_TYPE);
            if (storeType == null)
            {
                throw new IllegalConfigurationException("Message store  type  setting is not set");
            }
            MessageStoreFactory  factory = MessageStoreFactory.FACTORY_LOADER.get(storeType);
            if (factory == null)
            {
                throw new IllegalConfigurationException("Message store  factory is not found for type " + storeType + " for VirtualHost " + getName());
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
    }

    protected void onOpen()
    {
        super.onOpen();

        registerSystemNodes();

        initialiseStatistics();

        Subject.doAs(SecurityManager.getSubjectWithAddedSystemRights(), new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                initialiseStorage();
                return null;
            }
        });

        getMessageStore().addEventListener(this, Event.PERSISTENT_MESSAGE_SIZE_OVERFULL);
        getMessageStore().addEventListener(this, Event.PERSISTENT_MESSAGE_SIZE_UNDERFULL);


        synchronized(_aliases)
        {
            for(Port port :_broker.getPorts())
            {
                if (Protocol.hasAmqpProtocol(port.getAvailableProtocols()))
                {
                    _aliases.add(new VirtualHostAliasAdapter(this, port));
                }
            }
        }
    }

    private void checkVHostStateIsActive()
    {
        checkVHostState(VirtualHostState.ACTIVE);
    }

    private void checkVHostState(VirtualHostState... states)
    {
        if (!Arrays.asList(states).contains(getVirtualHostState()))
        {
            throw new IllegalStateException("The virtual hosts state of " + getVirtualHostState()
                                            + " does not permit this operation.");
        }
    }

    private void registerSystemNodes()
    {
        QpidServiceLoader<SystemNodeCreator> qpidServiceLoader = new QpidServiceLoader<SystemNodeCreator>();
        Iterable<SystemNodeCreator> factories = qpidServiceLoader.instancesOf(SystemNodeCreator.class);
        for(SystemNodeCreator creator : factories)
        {
            creator.register(_systemNodeRegistry);
        }
    }

    abstract protected void initialiseStorage();

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

    abstract protected MessageStoreLogSubject getMessageStoreLogSubject();

    public IConnectionRegistry getConnectionRegistry()
    {
        return _connectionRegistry;
    }

    @Override
    protected void changeAttributes(Map<String, Object> attributes)
    {
        throw new UnsupportedOperationException("Changing attributes on virtualhosts is not supported.");
    }

    @Override
    protected void authoriseSetDesiredState(State currentState, State desiredState) throws AccessControlException
    {
        if(desiredState == State.DELETED)
        {
            if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), org.apache.qpid.server.model.VirtualHost.class, Operation.DELETE))
            {
                throw new AccessControlException("Deletion of virtual host is denied");
            }
        }
    }

    @Override
    protected void authoriseSetAttributes(ConfiguredObject<?> modified, Set<String> attributes) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), org.apache.qpid.server.model.VirtualHost.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of virtual host attributes is denied");
        }
    }

    public Collection<Connection> getConnections()
    {
        synchronized(_connectionAdapters)
        {
            return new ArrayList<Connection>(_connectionAdapters.values());
        }

    }

    /**
     * Retrieve the ConnectionAdapter instance keyed by the AMQConnectionModel from this VirtualHost.
     * @param connection the AMQConnectionModel used to index the ConnectionAdapter.
     * @return the requested ConnectionAdapter.
     */
    ConnectionAdapter getConnectionAdapter(AMQConnectionModel connection)
    {
        synchronized (_connectionAdapters)
        {
            return _connectionAdapters.get(connection);
        }
    }

    public String setType(final String currentType, final String desiredType)
            throws IllegalStateException, AccessControlException
    {
        throw new IllegalStateException();
    }



    @Override
    public State getState()
    {
        if(_deleted.get())
        {
            return State.DELETED;
        }
        VirtualHostState implementationState = getVirtualHostState();
        switch(implementationState)
        {
            case INITIALISING:
                return State.INITIALISING;
            case ACTIVE:
                return State.ACTIVE;
            case PASSIVE:
                return State.REPLICA;
            case STOPPED:
                return State.STOPPED;
            case ERRORED:
                return State.ERRORED;
            default:
                throw new IllegalStateException("Unsupported state:" + implementationState);
        }

    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        if(clazz == Connection.class)
        {
            return (Collection<C>) getConnections();
        }
        else if(clazz == VirtualHostAlias.class)
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

    public long getCreateTime()
    {
        return _createTime;
    }

    @Override
    public AMQQueue<?> getQueue(String name)
    {
        return (AMQQueue<?>) getChildByName(Queue.class, name);
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
        int purged = queue.delete();

        if (queue.isDurable() && !(queue.getLifetimePolicy()
                                   == LifetimePolicy.DELETE_ON_CONNECTION_CLOSE
                                   || queue.getLifetimePolicy()
                                      == LifetimePolicy.DELETE_ON_SESSION_END))
        {
            DurableConfigurationStore store = getDurableConfigurationStore();
            store.remove(queue.asObjectRecord());
        }
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
        return getChildByName(ExchangeImpl.class,name);
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

    public ExchangeImpl<?> createExchange(final String name,
                                   final State initialState,
                                   final boolean durable,
                                   final LifetimePolicy lifetime,
                                   final String type,
                                   final Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException
    {
        checkVHostStateIsActive();

        try
        {
            String alternateExchange = null;
            if(attributes.containsKey(Exchange.ALTERNATE_EXCHANGE))
            {
                Object altExchangeObject = attributes.get(Exchange.ALTERNATE_EXCHANGE);
                if(altExchangeObject instanceof Exchange)
                {
                    alternateExchange = ((Exchange) altExchangeObject).getName();
                }
                else if(altExchangeObject instanceof UUID)
                {
                    for(Exchange ex : getExchanges())
                    {
                        if(altExchangeObject.equals(ex.getId()))
                        {
                            alternateExchange = ex.getName();
                            break;
                        }
                    }
                }
                else if(altExchangeObject instanceof String)
                {

                    for(Exchange ex : getExchanges())
                    {
                        if(altExchangeObject.equals(ex.getName()))
                        {
                            alternateExchange = ex.getName();
                            break;
                        }
                    }
                    if(alternateExchange == null)
                    {
                        try
                        {
                            UUID id = UUID.fromString(altExchangeObject.toString());
                            for(Exchange ex : getExchanges())
                            {
                                if(id.equals(ex.getId()))
                                {
                                    alternateExchange = ex.getName();
                                    break;
                                }
                            }
                        }
                        catch(IllegalArgumentException e)
                        {
                            // ignore
                        }

                    }
                }
            }
            Map<String,Object> attributes1 = new HashMap<String, Object>();

            attributes1.put(ID, null);
            attributes1.put(NAME, name);
            attributes1.put(Exchange.TYPE, type);
            attributes1.put(Exchange.DURABLE, durable);
            attributes1.put(Exchange.LIFETIME_POLICY,
                            lifetime != null && lifetime != LifetimePolicy.PERMANENT
                                    ? LifetimePolicy.DELETE_ON_NO_LINKS : LifetimePolicy.PERMANENT);
            attributes1.put(Exchange.ALTERNATE_EXCHANGE, alternateExchange);
            ExchangeImpl exchange = createExchange(attributes1);
            return exchange;

        }
        catch(ExchangeExistsException e)
        {
            throw new IllegalArgumentException("Exchange with name '" + name + "' already exists");
        }
        catch(ReservedExchangeNameException e)
        {
            throw new UnsupportedOperationException("'" + name + "' is a reserved exchange name");
        }
        catch(AMQUnknownExchangeType e)
        {
            throw new IllegalArgumentException(e);
        }
    }


    @Override
    public ExchangeImpl createExchange(Map<String,Object> attributes)
            throws ExchangeExistsException, ReservedExchangeNameException,
                   AMQUnknownExchangeType
    {
        checkVHostStateIsActive();
        ExchangeImpl child = addExchange(attributes);
        childAdded(child);
        return child;
    }


    private ExchangeImpl addExchange(Map<String,Object> attributes)
            throws ExchangeExistsException, ReservedExchangeNameException,
                   AMQUnknownExchangeType
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
        exchange.delete();
    }

    public SecurityManager getSecurityManager()
    {
        return _broker.getSecurityManager();
    }

    public void close()
    {
        //Stop Connections
        _connectionRegistry.close();
        _dtxRegistry.close();
        closeStorage();
        shutdownHouseKeeping();

        _state = VirtualHostState.STOPPED;

        _eventLogger.message(VirtualHostMessages.CLOSED(getName()));

        // TODO: The state work will replace this with closure of the virtualhost, rather than deleting it.
        deleted();
    }

    private void closeStorage()
    {
        if (getMessageStore() != null)
        {
            try
            {
                getMessageStore().closeMessageStore();
            }
            catch (StoreException e)
            {
                _logger.error("Failed to close message store", e);
            }
        }

        if (!_virtualHostNode.isMessageStoreProvider())
        {
            getEventLogger().message(getMessageStoreLogSubject(), MessageStoreMessages.CLOSED());
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

    public void initialiseStatistics()
    {
        _messagesDelivered = new StatisticsCounter("messages-delivered-" + getName());
        _dataDelivered = new StatisticsCounter("bytes-delivered-" + getName());
        _messagesReceived = new StatisticsCounter("messages-received-" + getName());
        _dataReceived = new StatisticsCounter("bytes-received-" + getName());
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

    public VirtualHostState getVirtualHostState()
    {
        return _state;
    }

    public void block()
    {
        synchronized (_connectionRegistry)
        {
            if(!_blocked)
            {
                _blocked = true;
                for(AMQConnectionModel conn : _connectionRegistry.getConnections())
                {
                    conn.block();
                }
            }
        }
    }


    public void unblock()
    {
        synchronized (_connectionRegistry)
        {
            if(_blocked)
            {
                _blocked = false;
                for(AMQConnectionModel conn : _connectionRegistry.getConnections())
                {
                    conn.unblock();
                }
            }
        }
    }

    public void connectionRegistered(final AMQConnectionModel connection)
    {
        if(_blocked)
        {
            connection.block();
        }
        ConnectionAdapter adapter = null;
        synchronized (_connectionAdapters)
        {
            if(!_connectionAdapters.containsKey(connection))
            {
                adapter = new ConnectionAdapter(connection, getTaskExecutor());
                _connectionAdapters.put(connection, adapter);

            }

        }
        if(adapter != null)
        {
            childAdded(adapter);
        }

    }

    public void connectionUnregistered(final AMQConnectionModel connection)
    {
        ConnectionAdapter adapter;
        synchronized (_connectionAdapters)
        {
            adapter = _connectionAdapters.remove(connection);

        }

        if(adapter != null)
        {
            // Call getSessions() first to ensure that any SessionAdapter children are cleanly removed and any
            // corresponding ConfigurationChangeListener childRemoved() callback is called for child SessionAdapters.
            adapter.getSessions();

            childRemoved(adapter);
        }
    }

    public void event(final Event event)
    {
        switch(event)
        {
            case PERSISTENT_MESSAGE_SIZE_OVERFULL:
                block();
                _eventLogger.message(getMessageStoreLogSubject(), MessageStoreMessages.OVERFULL());
                break;
            case PERSISTENT_MESSAGE_SIZE_UNDERFULL:
                unblock();
                _eventLogger.message(getMessageStoreLogSubject(), MessageStoreMessages.UNDERFULL());
                break;
        }
    }

    protected void setState(VirtualHostState state)
    {
        _state = state;
    }

    protected void reportIfError(VirtualHostState state)
    {
        if (state == VirtualHostState.ERRORED)
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

    private class VirtualHostHouseKeepingTask extends HouseKeepingTask
    {
        public VirtualHostHouseKeepingTask()
        {
            super(AbstractVirtualHost.this);
        }

        public void execute()
        {
            for (AMQQueue<?> q : getQueues())
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Checking message status for queue: "
                            + q.getName());
                }
                try
                {
                    q.checkMessageStatus();
                } catch (Exception e)
                {
                    _logger.error("Exception in housekeeping for queue: " + q.getName(), e);
                    //Don't throw exceptions as this will stop the
                    // house keeping task from running.
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
            public void dequeue(final MessageInstance entry)
            {
                if(entry.acquire())
                {
                    txn.dequeue(entry.getOwningResource(), entry.getMessage(), new ServerTransaction.Action()
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
    public Object getAttribute(String name)
    {
        if(STATE.equals(name))
        {
            return getState();
        }
        else if(SUPPORTED_EXCHANGE_TYPES.equals(name))
        {
            return getObjectFactory().getSupportedTypes(Exchange.class);
        }
        else if(SUPPORTED_QUEUE_TYPES.equals(name))
        {
            // TODO
        }

        return super.getAttribute(name);
    }

    @Override
    public Collection<String> getSupportedExchangeTypes()
    {
        return getObjectFactory().getSupportedTypes(Exchange.class);
    }

    @Override
    public Collection<String> getSupportedQueueTypes()
    {
        // TODO
        return null;
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
    public Map<String, Object> getMessageStoreSettings()
    {
        return _messageStoreSettings;
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



    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        if (desiredState == State.ACTIVE)
        {
            activate();
            return true;
        }
        else if (desiredState == State.STOPPED)
        {
            close();
            return true;
        }
        else if (desiredState == State.DELETED)
        {
            if(_deleted.compareAndSet(false,true))
            {
                String hostName = getName();

                if (hostName.equals(_broker.getAttribute(Broker.DEFAULT_VIRTUAL_HOST)))
                {
                    throw new IntegrityViolationException("Cannot delete default virtual host '" + hostName + "'");
                }
                if (getVirtualHostState() == VirtualHostState.ACTIVE
                    || getVirtualHostState() == VirtualHostState.INITIALISING)
                {
                    setDesiredState(currentState, State.STOPPED);
                }

                MessageStore ms = getMessageStore();
                if (ms != null)
                {
                    try
                    {
                        ms.onDelete();
                    }
                    catch (Exception e)
                    {
                        _logger.warn("Exception occurred on store deletion", e);
                    }
                }
                setAttribute(VirtualHost.STATE, getState(), State.DELETED);
                deleted();
            }

            return true;
        }
        return false;
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
        catch (ReservedExchangeNameException | AMQUnknownExchangeType | UnknownConfiguredObjectException e)
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
    protected void onCreate()
    {
        super.onCreate();
        getDurableConfigurationStore().create(asObjectRecord());
    }

    protected void activate()
    {
        _houseKeepingTasks = new ScheduledThreadPoolExecutor(getHousekeepingThreadCount());

        boolean nodeIsMessageStoreProvider = _virtualHostNode.isMessageStoreProvider();

        MessageStore messageStore = getMessageStore();
        Map<String, Object> messageStoreSettings = getMessageStoreSettings();
        if (messageStoreSettings == null)
        {
            messageStoreSettings = Collections.emptyMap();
        }
        messageStore.openMessageStore(this, messageStoreSettings);

        if (!nodeIsMessageStoreProvider)
        {
            getEventLogger().message(getMessageStoreLogSubject(), MessageStoreMessages.CREATED());
            getEventLogger().message(getMessageStoreLogSubject(), MessageStoreMessages.STORE_LOCATION(messageStore.getStoreLocation()));
        }

        if (isStoreEmpty())
        {
            createDefaultExchanges();
        }

        new MessageStoreRecoverer(this, getMessageStoreLogSubject()).recover();

        VirtualHostState finalState = VirtualHostState.ERRORED;
        try
        {
            initialiseHouseKeeping(getHousekeepingCheckPeriod());
            finalState = VirtualHostState.ACTIVE;
        }
        finally
        {
            _state = finalState;
            reportIfError(_state);
        }
    }

}
