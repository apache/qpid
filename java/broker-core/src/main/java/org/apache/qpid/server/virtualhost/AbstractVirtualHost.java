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

import java.lang.reflect.Type;
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
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.connection.ConnectionRegistry;
import org.apache.qpid.server.connection.IConnectionRegistry;
import org.apache.qpid.server.exchange.AMQUnknownExchangeType;
import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.exchange.DefaultExchangeRegistry;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.messages.ConfigStoreMessages;
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
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.plugin.SystemNodeCreator;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.LinkRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.ConflationQueue;
import org.apache.qpid.server.queue.DefaultQueueRegistry;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.DurableConfigurationStoreHelper;
import org.apache.qpid.server.store.DurableConfiguredObjectRecoverer;
import org.apache.qpid.server.store.Event;
import org.apache.qpid.server.store.EventListener;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.txn.DtxRegistry;
import org.apache.qpid.server.txn.LocalTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.util.ParameterizedTypeImpl;

public abstract class AbstractVirtualHost<X extends AbstractVirtualHost<X>> extends AbstractConfiguredObject<X>
        implements VirtualHostImpl<X, AMQQueue<?>, ExchangeImpl<?>>, IConnectionRegistry.RegistryChangeListener, EventListener,
                   VirtualHost<X,AMQQueue<?>, ExchangeImpl<?>>
{
    private static final Logger _logger = Logger.getLogger(AbstractVirtualHost.class);

    private static final int HOUSEKEEPING_SHUTDOWN_TIMEOUT = 5;

    private final long _createTime = System.currentTimeMillis();

    private final ScheduledThreadPoolExecutor _houseKeepingTasks;

    private final Broker<?> _broker;

    private final QueueRegistry _queueRegistry;

    private final ExchangeRegistry _exchangeRegistry;

    private final ExchangeFactory _exchangeFactory;

    private final ConnectionRegistry _connectionRegistry;

    private final DtxRegistry _dtxRegistry;
    private final AMQQueueFactory _queueFactory;
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

    @SuppressWarnings("serial")
    public static final Map<String, Type> ATTRIBUTE_TYPES = Collections.unmodifiableMap(new HashMap<String, Type>(){{
        put(NAME, String.class);
        put(TYPE, String.class);
        put(STATE, State.class);

        put(QUEUE_ALERT_REPEAT_GAP, Long.class);
        put(QUEUE_ALERT_THRESHOLD_MESSAGE_AGE, Long.class);
        put(QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE, Long.class);
        put(QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES, Long.class);
        put(QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES, Long.class);
        put(QUEUE_DEAD_LETTER_QUEUE_ENABLED, Boolean.class);
        put(QUEUE_MAXIMUM_DELIVERY_ATTEMPTS, Integer.class);
        put(QUEUE_FLOW_CONTROL_SIZE_BYTES, Long.class);
        put(QUEUE_FLOW_RESUME_SIZE_BYTES, Long.class);

        put(HOUSEKEEPING_CHECK_PERIOD, Long.class);
        put(STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE, Long.class);
        put(STORE_TRANSACTION_IDLE_TIMEOUT_WARN, Long.class);
        put(STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE, Long.class);
        put(STORE_TRANSACTION_OPEN_TIMEOUT_WARN, Long.class);

        put(MESSAGE_STORE_SETTINGS, new ParameterizedTypeImpl(Map.class, String.class, Object.class));
        put(CONFIGURATION_STORE_SETTINGS, new ParameterizedTypeImpl(Map.class, String.class, Object.class));

    }});

    @SuppressWarnings("serial")
    private static final Map<String, Object> DEFAULTS = Collections.unmodifiableMap(new HashMap<String, Object>(){{
        put(HOUSE_KEEPING_THREAD_COUNT, Runtime.getRuntime().availableProcessors());
    }});


    private final Map<AMQConnectionModel, ConnectionAdapter> _connectionAdapters =
            new HashMap<AMQConnectionModel, ConnectionAdapter>();

    private final List<VirtualHostAlias> _aliases = new ArrayList<VirtualHostAlias>();
    private final AtomicBoolean _deleted = new AtomicBoolean();

    @ManagedAttributeField
    private Map<String, Object> _messageStoreSettings;

    @ManagedAttributeField
    private Map<String, Object> _configurationStoreSettings;


    public AbstractVirtualHost(final Map<String, Object> attributes, Broker<?> broker)
    {
        super(parentsMap(broker), DEFAULTS, enhanceWithId(attributes), broker.getTaskExecutor());
        _broker = broker;
        _dtxRegistry = new DtxRegistry();

        _eventLogger = _broker.getVirtualHostRegistry().getEventLogger();

        _eventLogger.message(VirtualHostMessages.CREATED(getName()));

        _connectionRegistry = new ConnectionRegistry();
        _connectionRegistry.addRegistryChangeListener(this);

        _houseKeepingTasks = new ScheduledThreadPoolExecutor(getHouseKeepingThreadCount());


        _queueRegistry = new DefaultQueueRegistry(this);

        _queueFactory = new AMQQueueFactory(this, _queueRegistry);

        _exchangeFactory = new DefaultExchangeFactory(this);

        _exchangeRegistry = new DefaultExchangeRegistry(this, _queueRegistry);

    }

    private static Map<String, Object> enhanceWithId(Map<String, Object> attributes)
    {
        if(attributes.get(ID) == null)
        {
            attributes = new HashMap<String, Object>(attributes);
            attributes.put(ID, UUIDGenerator.generateVhostUUID((String)attributes.get(NAME)));
        }
        return attributes;
    }

    private static Map<Class<? extends ConfiguredObject>, ConfiguredObject<?>> parentsMap(Broker<?> broker)
    {
        final Map<Class<? extends ConfiguredObject>, ConfiguredObject<?>> parentsMap = new HashMap<Class<? extends ConfiguredObject>, ConfiguredObject<?>>();
        parentsMap.put(Broker.class, broker);
        return parentsMap;
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
    }

    protected void onOpen()
    {
        super.onOpen();

        registerSystemNodes();

        initialiseStatistics();

        Subject.doAs(getSecurityManager().getSubjectWithAddedSystemRights(), new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                initialiseStorage(AbstractVirtualHost.this);
                return null;
            }
        });

        getMessageStore().addEventListener(this, Event.PERSISTENT_MESSAGE_SIZE_OVERFULL);
        getMessageStore().addEventListener(this, Event.PERSISTENT_MESSAGE_SIZE_UNDERFULL);

        _broker.getVirtualHostRegistry().registerVirtualHost(this);


        synchronized(_aliases)
        {
            for(Port port :_broker.getPorts())
            {
                if (Protocol.hasAmqpProtocol(port.getProtocols()))
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

    abstract protected void initialiseStorage(org.apache.qpid.server.model.VirtualHost<?,?,?> virtualHost);

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
    protected void authoriseSetAttribute(String name, Object expected, Object desired) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), org.apache.qpid.server.model.VirtualHost.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of virtual host attributes is denied");
        }
    }

    @Override
    protected void authoriseSetAttributes(Map<String, Object> attributes) throws AccessControlException
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

    public String setName(final String currentName, final String desiredName)
            throws IllegalStateException, AccessControlException
    {
        throw new IllegalStateException();
    }


    public String getType()
    {
        return (String)getAttribute(TYPE);
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

    public boolean isDurable()
    {
        return true;
    }

    public void setDurable(final boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    public LifetimePolicy setLifetimePolicy(final LifetimePolicy expected, final LifetimePolicy desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }


    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        if(clazz == Exchange.class)
        {
            return (Collection<C>) getExchanges();
        }
        else if(clazz == Queue.class)
        {
            return (Collection<C>) getQueues();
        }
        else if(clazz == Connection.class)
        {
            return (Collection<C>) getConnections();
        }
        else if(clazz == VirtualHostAlias.class)
        {
            return (Collection<C>) getAliases();
        }
        else
        {
            return Collections.emptySet();
        }
    }

    @Override
    public <C extends ConfiguredObject> C addChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
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
        Collection<String> exchangeTypes = new ArrayList<String>();

        for(ExchangeType<? extends ExchangeImpl> type : getExchangeTypes())
        {
            exchangeTypes.add(type.getType());
        }
        return Collections.unmodifiableCollection(exchangeTypes);
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


    protected void initialiseModel()
    {
        Subject.doAs(getSecurityManager().getSubjectWithAddedSystemRights(), new PrivilegedAction<Object>()
                     {
                         @Override
                         public Object run()
                         {
                             _exchangeRegistry.initialise(_exchangeFactory);
                             return null;
                         }
                     });
    }


    public long getCreateTime()
    {
        return _createTime;
    }

    public QueueRegistry getQueueRegistry()
    {
        return _queueRegistry;
    }

    protected ExchangeRegistry getExchangeRegistry()
    {
        return _exchangeRegistry;
    }

    protected ExchangeFactory getExchangeFactory()
    {
        return _exchangeFactory;
    }

    @Override
    public void addVirtualHostListener(final VirtualHostListener listener)
    {
        _exchangeRegistry.addRegistryChangeListener(new ExchangeRegistry.RegistryChangeListener()
        {
            @Override
            public void exchangeRegistered(ExchangeImpl exchange)
            {
                listener.exchangeRegistered(exchange);
            }

            @Override
            public void exchangeUnregistered(ExchangeImpl exchange)
            {
                listener.exchangeUnregistered(exchange);
            }
        });
        _queueRegistry.addRegistryChangeListener(new QueueRegistry.RegistryChangeListener()
        {
            @Override
            public void queueRegistered(AMQQueue queue)
            {
                listener.queueRegistered(queue);
            }

            @Override
            public void queueUnregistered(AMQQueue queue)
            {
                listener.queueUnregistered(queue);
            }
        });
        _connectionRegistry.addRegistryChangeListener(new IConnectionRegistry.RegistryChangeListener()
        {
            @Override
            public void connectionRegistered(AMQConnectionModel connection)
            {
                listener.connectionRegistered(connection);
            }

            @Override
            public void connectionUnregistered(AMQConnectionModel connection)
            {
                listener.connectionUnregistered(connection);
            }
        });
    }

    @Override
    public AMQQueue<?> getQueue(String name)
    {
        return _queueRegistry.getQueue(name);
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
        return _queueRegistry.getQueue(id);
    }

    @Override
    public Collection<AMQQueue<?>> getQueues()
    {
        return _queueRegistry.getQueues();
    }

    @Override
    public int removeQueue(AMQQueue<?> queue)
    {
        synchronized (getQueueRegistry())
        {
            int purged = queue.delete();

            getQueueRegistry().unregisterQueue(queue.getName());
            if (queue.isDurable() && !(queue.getLifetimePolicy()
                                       == LifetimePolicy.DELETE_ON_CONNECTION_CLOSE
                                       || queue.getLifetimePolicy()
                                          == LifetimePolicy.DELETE_ON_SESSION_END))
            {
                DurableConfigurationStore store = getDurableConfigurationStore();
                DurableConfigurationStoreHelper.removeQueue(store, queue);
            }
            return purged;
        }
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

        // make a copy as we may augment (with an ID for example)
        attributes = new LinkedHashMap<String, Object>(attributes);
        if (attributes.containsKey(Queue.QUEUE_TYPE))
        {
            String typeAttribute = MapValueConverter.getStringAttribute(Queue.QUEUE_TYPE, attributes, null);
            QueueType queueType = null;
            try
            {
                queueType = QueueType.valueOf(typeAttribute.toUpperCase());
            }
            catch(Exception e)
            {
                throw new IllegalArgumentException("Unsupported queue type :" + typeAttribute);
            }
            if (queueType == QueueType.LVQ && attributes.get(Queue.LVQ_KEY) == null)
            {
                attributes.put(Queue.LVQ_KEY, ConflationQueue.DEFAULT_LVQ_KEY);
            }
            else if (queueType == QueueType.PRIORITY && attributes.get(Queue.PRIORITIES) == null)
            {
                attributes.put(Queue.PRIORITIES, 10);
            }
            else if (queueType == QueueType.SORTED && attributes.get(Queue.SORT_KEY) == null)
            {
                throw new IllegalArgumentException("Sort key is not specified for sorted queue");
            }
        }

        String queueName = MapValueConverter.getStringAttribute(Queue.NAME, attributes);

        synchronized (_queueRegistry)
        {
            if(_queueRegistry.getQueue(queueName) != null)
            {
                throw new QueueExistsException("Queue with name " + queueName + " already exists", _queueRegistry.getQueue(queueName));
            }
            if(!attributes.containsKey(Queue.ID))
            {

                UUID id = UUIDGenerator.generateQueueUUID(queueName, getName());
                while(_queueRegistry.getQueue(id) != null)
                {
                    id = UUID.randomUUID();
                }
                attributes.put(Queue.ID, id);

            }
            else if(_queueRegistry.getQueue(MapValueConverter.getUUIDAttribute(Queue.ID, attributes)) != null)
            {
                throw new QueueExistsException("Queue with id "
                                               + MapValueConverter.getUUIDAttribute(Queue.ID,
                                                                                    attributes)
                                               + " already exists", _queueRegistry.getQueue(queueName));
            }


            return _queueFactory.createQueue(attributes);
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
        return _exchangeRegistry.getExchange(name);
    }

    @Override
    public ExchangeImpl getExchange(UUID id)
    {
        return _exchangeRegistry.getExchange(id);
    }

    @Override
    public MessageDestination getDefaultDestination()
    {
        return _exchangeRegistry.getDefaultExchange();
    }

    @Override
    public Collection<ExchangeImpl<?>> getExchanges()
    {
        return Collections.unmodifiableCollection(_exchangeRegistry.getExchanges());
    }

    @Override
    public Collection<ExchangeType<? extends ExchangeImpl>> getExchangeTypes()
    {
        return _exchangeFactory.getRegisteredTypes();
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
        catch(UnknownExchangeException e)
        {
            throw new IllegalArgumentException("Alternate Exchange with name '" + e.getExchangeName() + "' does not exist");
        }
        catch(AMQUnknownExchangeType e)
        {
            throw new IllegalArgumentException(e);
        }
    }


    @Override
    public ExchangeImpl createExchange(Map<String,Object> attributes)
            throws ExchangeExistsException, ReservedExchangeNameException,
                   UnknownExchangeException, AMQUnknownExchangeType
    {
        checkVHostStateIsActive();
        ExchangeImpl child = addExchange(attributes);
        childAdded(child);
        return child;
    }


    private ExchangeImpl addExchange(Map<String,Object> attributes)
            throws ExchangeExistsException, ReservedExchangeNameException,
                   UnknownExchangeException, AMQUnknownExchangeType
    {
        String name = MapValueConverter.getStringAttribute(org.apache.qpid.server.model.Exchange.NAME, attributes);
        if(attributes.get(Exchange.DURABLE) == null)
        {
            attributes = new HashMap<String, Object>(attributes);
            attributes.put(Exchange.DURABLE, false);
        }
        boolean durable =
                MapValueConverter.getBooleanAttribute(Exchange.DURABLE, attributes);


        synchronized (_exchangeRegistry)
        {
            ExchangeImpl existing;
            if((existing = _exchangeRegistry.getExchange(name)) !=null)
            {
                throw new ExchangeExistsException(name,existing);
            }
            if(_exchangeRegistry.isReservedExchangeName(name))
            {
                throw new ReservedExchangeNameException(name);
            }


            if(attributes.get(org.apache.qpid.server.model.Exchange.ID) == null)
            {
                attributes = new LinkedHashMap<String, Object>(attributes);
                attributes.put(org.apache.qpid.server.model.Exchange.ID,
                               UUIDGenerator.generateExchangeUUID(name, getName()));
            }

            ExchangeImpl exchange = _exchangeFactory.createExchange(attributes);

            _exchangeRegistry.registerExchange(exchange);
            if(durable)
            {
                DurableConfigurationStoreHelper.createExchange(getDurableConfigurationStore(), exchange);
            }
            return exchange;
        }
    }

    @Override
    public void removeExchange(ExchangeImpl exchange, boolean force)
            throws ExchangeIsAlternateException, RequiredExchangeException
    {
        if(exchange.hasReferrers())
        {
            throw new ExchangeIsAlternateException(exchange.getName());
        }

        for(ExchangeType type : getExchangeTypes())
        {
            if(type.getDefaultExchangeName().equals( exchange.getName() ))
            {
                throw new RequiredExchangeException(exchange.getName());
            }
        }
        _exchangeRegistry.unregisterExchange(exchange.getName(), !force);
        if (exchange.isDurable() && !exchange.isAutoDelete())
        {
            DurableConfigurationStoreHelper.removeExchange(getDurableConfigurationStore(), exchange);
        }

    }

    public SecurityManager getSecurityManager()
    {
        return _broker.getSecurityManager();
    }

    public void close()
    {
        //Stop Connections
        _connectionRegistry.close();
        _queueRegistry.stopAllAndUnregisterMBeans();
        _dtxRegistry.close();
        closeStorage();
        shutdownHouseKeeping();

        // clear exchange objects
        _exchangeRegistry.clearAndUnregisterMbeans();

        _state = VirtualHostState.STOPPED;

        _eventLogger.message(VirtualHostMessages.CLOSED(getName()));
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
        if (getDurableConfigurationStore() != null)
        {
            try
            {
                getDurableConfigurationStore().closeConfigurationStore();
                MessageStoreLogSubject configurationStoreSubject = getConfigurationStoreLogSubject();
                if (configurationStoreSubject != null)
                {
                    getEventLogger().message(configurationStoreSubject, ConfigStoreMessages.CLOSE());
                }
            }
            catch (StoreException e)
            {
                _logger.error("Failed to close configuration store", e);
            }
        }
        getEventLogger().message(getMessageStoreLogSubject(), MessageStoreMessages.CLOSED());
    }

    protected MessageStoreLogSubject getConfigurationStoreLogSubject()
    {
        return null;
    }

    public VirtualHostRegistry getVirtualHostRegistry()
    {
        return _broker.getVirtualHostRegistry();
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

    public String toString()
    {
        return getName();
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

    protected void attainActivation()
    {
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

    protected void reportIfError(VirtualHostState state)
    {
        if (state == VirtualHostState.ERRORED)
        {
            _eventLogger.message(VirtualHostMessages.ERRORED(getName()));
        }
    }

    protected Map<String, DurableConfiguredObjectRecoverer> getDurableConfigurationRecoverers()
    {
        DurableConfiguredObjectRecoverer[] recoverers = {
          new QueueRecoverer(this, getExchangeRegistry(), _queueFactory),
          new ExchangeRecoverer(getExchangeRegistry(), getExchangeFactory()),
          new BindingRecoverer(this, getExchangeRegistry())
        };

        final Map<String, DurableConfiguredObjectRecoverer> recovererMap= new HashMap<String, DurableConfiguredObjectRecoverer>();
        for(DurableConfiguredObjectRecoverer recoverer : recoverers)
        {
            recovererMap.put(recoverer.getType(), recoverer);
        }
        return recovererMap;
    }

    private class VirtualHostHouseKeepingTask extends HouseKeepingTask
    {
        public VirtualHostHouseKeepingTask()
        {
            super(AbstractVirtualHost.this);
        }

        public void execute()
        {
            for (AMQQueue<?> q : _queueRegistry.getQueues())
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

        @Override
        public org.apache.qpid.server.model.VirtualHost getVirtualHostModel()
        {
            return AbstractVirtualHost.this;
        }

    }

    @Override
    public long getDefaultAlertThresholdMessageAge()
    {
        return getQueue_alertThresholdMessageAge();
    }

    @Override
    public long getDefaultAlertThresholdMessageSize()
    {
        return getQueue_alertThresholdMessageSize();
    }

    @Override
    public long getDefaultAlertThresholdQueueDepthMessages()
    {
        return getQueue_alertThresholdQueueDepthMessages();
    }

    @Override
    public long getDefaultAlertThresholdQueueDepthBytes()
    {
        return getQueue_alertThresholdQueueDepthBytes();
    }

    @Override
    public long getDefaultAlertRepeatGap()
    {
        return getQueue_alertRepeatGap();
    }

    @Override
    public long getDefaultQueueFlowControlSizeBytes()
    {
        return getQueue_flowControlSizeBytes();
    }

    @Override
    public long getDefaultQueueFlowResumeSizeBytes()
    {
        return getQueue_flowResumeSizeBytes();
    }

    @Override
    public int getDefaultMaximumDeliveryAttempts()
    {
        return getQueue_maximumDeliveryAttempts();
    }

    @Override
    public boolean getDefaultDeadLetterQueueEnabled()
    {
        return isQueue_deadLetterQueueEnabled();
    }

    @Override
    public org.apache.qpid.server.model.VirtualHost getModel()
    {
        return this;
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
        if(ID.equals(name))
        {
            return getId();
        }
        else if(STATE.equals(name))
        {
            return getState();
        }
        else if(DURABLE.equals(name))
        {
            return isDurable();
        }
        else if(LIFETIME_POLICY.equals(name))
        {
            return LifetimePolicy.PERMANENT;
        }
        else if(QUEUE_ALERT_REPEAT_GAP.equals(name))
        {
            return getAttribute(QUEUE_ALERT_REPEAT_GAP, Broker.QUEUE_ALERT_REPEAT_GAP);
        }
        else if(QUEUE_ALERT_THRESHOLD_MESSAGE_AGE.equals(name))
        {
            return getAttribute(QUEUE_ALERT_THRESHOLD_MESSAGE_AGE, Broker.QUEUE_ALERT_THRESHOLD_MESSAGE_AGE);
        }
        else if(QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE.equals(name))
        {
            return getAttribute(QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE, Broker.QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE);
        }
        else if(QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES.equals(name))
        {
            return getAttribute(QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES, Broker.QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES);
        }
        else if(QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES.equals(name))
        {
            return getAttribute(QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES, Broker.QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES);
        }
        else if(QUEUE_DEAD_LETTER_QUEUE_ENABLED.equals(name))
        {
            return getAttribute(QUEUE_DEAD_LETTER_QUEUE_ENABLED, Broker.QUEUE_DEAD_LETTER_QUEUE_ENABLED);
        }
        else if(QUEUE_MAXIMUM_DELIVERY_ATTEMPTS.equals(name))
        {
            return getAttribute(QUEUE_MAXIMUM_DELIVERY_ATTEMPTS, Broker.QUEUE_MAXIMUM_DELIVERY_ATTEMPTS);
        }
        else if(QUEUE_FLOW_CONTROL_SIZE_BYTES.equals(name))
        {
            return getAttribute(QUEUE_FLOW_CONTROL_SIZE_BYTES, Broker.QUEUE_FLOW_CONTROL_SIZE_BYTES);
        }
        else if(QUEUE_FLOW_RESUME_SIZE_BYTES.equals(name))
        {
            return getAttribute(QUEUE_FLOW_RESUME_SIZE_BYTES, Broker.QUEUE_FLOW_CONTROL_RESUME_SIZE_BYTES);
        }
        else if(HOUSEKEEPING_CHECK_PERIOD.equals(name))
        {
            return getAttribute(HOUSEKEEPING_CHECK_PERIOD, Broker.VIRTUALHOST_HOUSEKEEPING_CHECK_PERIOD);
        }
        else if(STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE.equals(name))
        {
            return getAttribute(STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE, Broker.VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE);
        }
        else if(STORE_TRANSACTION_IDLE_TIMEOUT_WARN.equals(name))
        {
            return getAttribute(STORE_TRANSACTION_IDLE_TIMEOUT_WARN, Broker.VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_WARN);
        }
        else if(STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE.equals(name))
        {
            return getAttribute(STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE, Broker.VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE);
        }
        else if(STORE_TRANSACTION_OPEN_TIMEOUT_WARN.equals(name))
        {
            return getAttribute(STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE, Broker.VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_WARN);
        }
        else if(SUPPORTED_EXCHANGE_TYPES.equals(name))
        {
            List<String> types = new ArrayList<String>();
            for(ExchangeType<?> type : getExchangeTypes())
            {
                types.add(type.getType());
            }
            return Collections.unmodifiableCollection(types);
        }
        else if(SUPPORTED_QUEUE_TYPES.equals(name))
        {
            // TODO
        }

        return super.getAttribute(name);
    }


    Object getAttribute(String name, String brokerAttributeName)
    {
        return getAttribute(name, _broker, brokerAttributeName);
    }


    @Override
    public Collection<String> getAttributeNames()
    {
        return getAttributeNames(org.apache.qpid.server.model.VirtualHost.class);
    }

    @Override
    public Collection<String> getSupportedExchangeTypes()
    {
        List<String> types = new ArrayList<String>();
        for(ExchangeType<?> type : getExchangeTypes())
        {
            types.add(type.getType());
        }
        return Collections.unmodifiableCollection(types);
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
        return (Boolean)getAttribute(org.apache.qpid.server.model.VirtualHost.QUEUE_DEAD_LETTER_QUEUE_ENABLED);
    }

    @Override
    public long getHousekeepingCheckPeriod()
    {
        return (Long)getAttribute(org.apache.qpid.server.model.VirtualHost.HOUSEKEEPING_CHECK_PERIOD);
    }

    @Override
    public int getQueue_maximumDeliveryAttempts()
    {
        return (Integer)getAttribute(org.apache.qpid.server.model.VirtualHost.QUEUE_MAXIMUM_DELIVERY_ATTEMPTS);
    }

    @Override
    public long getQueue_flowControlSizeBytes()
    {
        return (Long)getAttribute(org.apache.qpid.server.model.VirtualHost.QUEUE_FLOW_CONTROL_SIZE_BYTES);
    }

    @Override
    public long getQueue_flowResumeSizeBytes()
    {
        return (Long)getAttribute(org.apache.qpid.server.model.VirtualHost.QUEUE_FLOW_RESUME_SIZE_BYTES);
    }

    @Override
    public long getStoreTransactionIdleTimeoutClose()
    {
        return (Long)getAttribute(org.apache.qpid.server.model.VirtualHost.STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE);
    }

    @Override
    public long getStoreTransactionIdleTimeoutWarn()
    {
        return (Long)getAttribute(org.apache.qpid.server.model.VirtualHost.STORE_TRANSACTION_IDLE_TIMEOUT_WARN);
    }

    @Override
    public long getStoreTransactionOpenTimeoutClose()
    {
        return (Long)getAttribute(org.apache.qpid.server.model.VirtualHost.STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE);
    }

    @Override
    public long getStoreTransactionOpenTimeoutWarn()
    {
        return (Long)getAttribute(org.apache.qpid.server.model.VirtualHost.STORE_TRANSACTION_OPEN_TIMEOUT_WARN);
    }

    @Override
    public long getQueue_alertRepeatGap()
    {
        return (Long)getAttribute(org.apache.qpid.server.model.VirtualHost.QUEUE_ALERT_REPEAT_GAP);
    }

    @Override
    public long getQueue_alertThresholdMessageAge()
    {
        return (Long)getAttribute(org.apache.qpid.server.model.VirtualHost.QUEUE_ALERT_THRESHOLD_MESSAGE_AGE);
    }

    @Override
    public long getQueue_alertThresholdMessageSize()
    {
        return (Long)getAttribute(org.apache.qpid.server.model.VirtualHost.QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE);
    }

    @Override
    public long getQueue_alertThresholdQueueDepthBytes()
    {
        return (Long)getAttribute(org.apache.qpid.server.model.VirtualHost.QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES);
    }

    @Override
    public long getQueue_alertThresholdQueueDepthMessages()
    {
        return (Long)getAttribute(org.apache.qpid.server.model.VirtualHost.QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> getMessageStoreSettings()
    {
        return _messageStoreSettings;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> getConfigurationStoreSettings()
    {
        return _configurationStoreSettings;
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
    public String getSecurityAcl()
    {
        return (String)getAttribute(SECURITY_ACL);
    }

    @Override
    public int getHouseKeepingThreadCount()
    {
        return (Integer)getAttribute(HOUSE_KEEPING_THREAD_COUNT);
    }



    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        if (desiredState == State.ACTIVE)
        {
/*
            try
            {
                onOpen();
            }
            catch(RuntimeException e)
            {
                changeAttribute(STATE, State.INITIALISING, State.ERRORED);
                if (_broker.isManagementMode())
                {
                    _logger.warn("Failed to activate virtual host: " + getName(), e);
                }
                else
                {
                    throw e;
                }
            }
*/
            return true;
        }
        else if (desiredState == State.STOPPED)
        {
            try
            {
                close();
            }
            finally
            {
                _broker.getVirtualHostRegistry().unregisterVirtualHost(this);
            }

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
                setAttribute(org.apache.qpid.server.model.VirtualHost.STATE, getState(), State.DELETED);
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

}
