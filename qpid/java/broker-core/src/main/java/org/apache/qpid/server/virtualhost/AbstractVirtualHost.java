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

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.exchange.AMQUnknownExchangeType;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.exchange.NonDefaultExchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.configuration.ExchangeConfiguration;
import org.apache.qpid.server.configuration.QueueConfiguration;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.connection.ConnectionRegistry;
import org.apache.qpid.server.connection.IConnectionRegistry;
import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.exchange.DefaultExchangeRegistry;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.VirtualHostMessages;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.message.MessageNode;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.adapter.VirtualHostAdapter;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.plugin.SystemNodeCreator;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.LinkRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.DefaultQueueRegistry;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.DurableConfigurationStoreHelper;
import org.apache.qpid.server.store.DurableConfiguredObjectRecoverer;
import org.apache.qpid.server.store.Event;
import org.apache.qpid.server.store.EventListener;
import org.apache.qpid.server.txn.DtxRegistry;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

public abstract class AbstractVirtualHost implements VirtualHost, IConnectionRegistry.RegistryChangeListener, EventListener
{
    private static final Logger _logger = Logger.getLogger(AbstractVirtualHost.class);

    private static final int HOUSEKEEPING_SHUTDOWN_TIMEOUT = 5;

    private final String _name;

    private final UUID _id;

    private final long _createTime = System.currentTimeMillis();

    private final ScheduledThreadPoolExecutor _houseKeepingTasks;

    private final VirtualHostRegistry _virtualHostRegistry;

    private final StatisticsGatherer _brokerStatisticsGatherer;

    private final SecurityManager _securityManager;

    private final VirtualHostConfiguration _vhostConfig;

    private final QueueRegistry _queueRegistry;

    private final ExchangeRegistry _exchangeRegistry;

    private final ExchangeFactory _exchangeFactory;

    private final ConnectionRegistry _connectionRegistry;

    private final DtxRegistry _dtxRegistry;
    private final AMQQueueFactory _queueFactory;
    private final SystemNodeRegistry _systemNodeRegistry = new SystemNodeRegistry();
    private final org.apache.qpid.server.model.VirtualHost _model;

    private volatile State _state = State.INITIALISING;

    private StatisticsCounter _messagesDelivered, _dataDelivered, _messagesReceived, _dataReceived;

    private final Map<String, LinkRegistry> _linkRegistry = new HashMap<String, LinkRegistry>();
    private boolean _blocked;

    private final Map<String, MessageDestination> _systemNodeDestinations =
            Collections.synchronizedMap(new HashMap<String,MessageDestination>());

    private final Map<String, MessageSource> _systemNodeSources =
            Collections.synchronizedMap(new HashMap<String,MessageSource>());


    public AbstractVirtualHost(VirtualHostRegistry virtualHostRegistry,
                               StatisticsGatherer brokerStatisticsGatherer,
                               SecurityManager parentSecurityManager,
                               VirtualHostConfiguration hostConfig,
                               org.apache.qpid.server.model.VirtualHost virtualHost)
    {
        if (hostConfig == null)
        {
            throw new IllegalArgumentException("HostConfig cannot be null");
        }

        if (hostConfig.getName() == null || hostConfig.getName().length() == 0)
        {
            throw new IllegalArgumentException("Illegal name (" + hostConfig.getName() + ") for virtualhost.");
        }

        _virtualHostRegistry = virtualHostRegistry;
        _brokerStatisticsGatherer = brokerStatisticsGatherer;
        _vhostConfig = hostConfig;
        _name = _vhostConfig.getName();
        _dtxRegistry = new DtxRegistry();
        _model = virtualHost;

        _id = UUIDGenerator.generateVhostUUID(_name);

        CurrentActor.get().message(VirtualHostMessages.CREATED(_name));

        _securityManager = new SecurityManager(parentSecurityManager, _vhostConfig.getConfig().getString("security.acl"), _name);

        _connectionRegistry = new ConnectionRegistry();
        _connectionRegistry.addRegistryChangeListener(this);

        _houseKeepingTasks = new ScheduledThreadPoolExecutor(_vhostConfig.getHouseKeepingThreadCount());


        _queueRegistry = new DefaultQueueRegistry(this);

        _queueFactory = new AMQQueueFactory(this, _queueRegistry);

        _exchangeFactory = new DefaultExchangeFactory(this);

        _exchangeRegistry = new DefaultExchangeRegistry(this, _queueRegistry);

        registerSystemNodes();

        initialiseStatistics();

        initialiseStorage(hostConfig, virtualHost);

        getMessageStore().addEventListener(this, Event.PERSISTENT_MESSAGE_SIZE_OVERFULL);
        getMessageStore().addEventListener(this, Event.PERSISTENT_MESSAGE_SIZE_UNDERFULL);
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

    abstract protected void initialiseStorage(VirtualHostConfiguration hostConfig,
                                              org.apache.qpid.server.model.VirtualHost virtualHost);

    public IConnectionRegistry getConnectionRegistry()
    {
        return _connectionRegistry;
    }

    public VirtualHostConfiguration getConfiguration()
    {
        return _vhostConfig;
    }

    public UUID getId()
    {
        return _id;
    }

    public boolean isDurable()
    {
        return false;
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


    protected void initialiseModel(VirtualHostConfiguration config)
    {
        _logger.debug("Loading configuration for virtualhost: " + config.getName());


        _exchangeRegistry.initialise(_exchangeFactory);

        List<String> exchangeNames = config.getExchanges();

        for (String exchangeName : exchangeNames)
        {
            try
            {
                configureExchange(config.getExchangeConfiguration(exchangeName));
            }
            catch (UnknownExchangeException e)
            {
                throw new ServerScopedRuntimeException("Could not configure exchange " + exchangeName, e);
            }
            catch (ReservedExchangeNameException e)
            {
                throw new ServerScopedRuntimeException("Could not configure exchange " + exchangeName, e);
            }
            catch (AMQUnknownExchangeType e)
            {
                throw new ServerScopedRuntimeException("Could not configure exchange " + exchangeName, e);
            }
        }

        String[] queueNames = config.getQueueNames();

        for (Object queueNameObj : queueNames)
        {
            String queueName = String.valueOf(queueNameObj);
            try
            {
                configureQueue(config.getQueueConfiguration(queueName));
            }
            catch (ConfigurationException e)
            {
                throw new ServerScopedRuntimeException("Could not configure queue " + queueName, e);
            }
        }
    }

    private void configureExchange(ExchangeConfiguration exchangeConfiguration)
            throws UnknownExchangeException, ReservedExchangeNameException,
                   AMQUnknownExchangeType
    {
        boolean durable = exchangeConfiguration.getDurable();
        boolean autodelete = exchangeConfiguration.getAutoDelete();
        try
        {
            Map<String,Object> attributes = new HashMap<String, Object>();

            attributes.put(org.apache.qpid.server.model.Exchange.ID, null);
            attributes.put(org.apache.qpid.server.model.Exchange.NAME, exchangeConfiguration.getName());
            attributes.put(org.apache.qpid.server.model.Exchange.TYPE, exchangeConfiguration.getType());
            attributes.put(org.apache.qpid.server.model.Exchange.DURABLE, durable);
            attributes.put(org.apache.qpid.server.model.Exchange.LIFETIME_POLICY,
                           autodelete ? LifetimePolicy.DELETE_ON_NO_LINKS : LifetimePolicy.PERMANENT);
            attributes.put(org.apache.qpid.server.model.Exchange.ALTERNATE_EXCHANGE, null);
            ExchangeImpl newExchange = createExchange(attributes);
        }
        catch(ExchangeExistsException e)
        {
            _logger.info("Exchange " + exchangeConfiguration.getName() + " already defined. Configuration in XML file ignored");
        }

    }

    private void configureQueue(QueueConfiguration queueConfiguration)
            throws ConfigurationException
    {
        AMQQueue queue = _queueFactory.createAMQQueueImpl(queueConfiguration);
        String queueName = queue.getName();

        if (queue.isDurable())
        {
            DurableConfigurationStoreHelper.createQueue(getDurableConfigurationStore(), queue);
        }

        //get the exchange name (returns default exchange name if none was specified)
        String exchangeName = queueConfiguration.getExchange();

        ExchangeImpl exchange = _exchangeRegistry.getExchange(exchangeName);
        if (exchange == null)
        {
            throw new ConfigurationException("Attempt to bind queue '" + queueName + "' to unknown exchange:" + exchangeName);
        }

        ExchangeImpl defaultExchange = _exchangeRegistry.getDefaultExchange();

        //get routing keys in configuration (returns empty list if none are defined)
        List<?> routingKeys = queueConfiguration.getRoutingKeys();

        for (Object routingKeyNameObj : routingKeys)
        {
            String routingKey = String.valueOf(routingKeyNameObj);

            if (exchange.equals(defaultExchange))
            {
                if(!queueName.equals(routingKey))
                {
                    throw new ConfigurationException("Illegal attempt to bind queue '" + queueName +
                                                     "' to the default exchange with a key other than the queue name: " + routingKey);
                }
            }
            else
            {
                configureBinding(queue, exchange, routingKey, (Map) queueConfiguration.getBindingArguments(routingKey));
            }
        }

        if (!exchange.equals(defaultExchange) && !routingKeys.contains(queueName))
        {
            //bind the queue to the named exchange using its name
            configureBinding(queue, exchange, queueName, null);
        }

    }

    private void configureBinding(AMQQueue queue, ExchangeImpl exchange, String routingKey, Map<String,Object> arguments)
    {
        if (_logger.isInfoEnabled())
        {
            _logger.info("Binding queue:" + queue + " with routing key '" + routingKey + "' to exchange:" + exchange.getName());
        }
        exchange.addBinding(routingKey, queue, arguments);
    }

    public String getName()
    {
        return _name;
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
    public AMQQueue getQueue(String name)
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
    public AMQQueue getQueue(UUID id)
    {
        return _queueRegistry.getQueue(id);
    }

    @Override
    public Collection<AMQQueue> getQueues()
    {
        return _queueRegistry.getQueues();
    }

    @Override
    public int removeQueue(AMQQueue queue)
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

    public AMQQueue createQueue(Map<String, Object> attributes) throws QueueExistsException
    {
        // make a copy as we may augment (with an ID for example)
        attributes = new LinkedHashMap<String, Object>(attributes);

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
    public ExchangeImpl getDefaultExchange()
    {
        return _exchangeRegistry.getDefaultExchange();
    }

    @Override
    public Collection<ExchangeImpl> getExchanges()
    {
        return Collections.unmodifiableCollection(_exchangeRegistry.getExchanges());
    }

    @Override
    public Collection<NonDefaultExchange> getExchangesExceptDefault()
    {
        return Collections.unmodifiableCollection(_exchangeRegistry.getExchangesExceptDefault());
    }

    @Override
    public Collection<ExchangeType<? extends ExchangeImpl>> getExchangeTypes()
    {
        return _exchangeFactory.getRegisteredTypes();
    }

    @Override
    public NonDefaultExchange createExchange(Map<String,Object> attributes)
            throws ExchangeExistsException, ReservedExchangeNameException,
                   UnknownExchangeException, AMQUnknownExchangeType
    {
        String name = MapValueConverter.getStringAttribute(org.apache.qpid.server.model.Exchange.NAME, attributes);
        boolean durable =
                MapValueConverter.getBooleanAttribute(org.apache.qpid.server.model.Exchange.DURABLE, attributes);


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

            NonDefaultExchange exchange = _exchangeFactory.createExchange(attributes);

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
        return _securityManager;
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

        _state = State.STOPPED;

        CurrentActor.get().message(VirtualHostMessages.CLOSED());
    }

    protected void closeStorage()
    {
        //Close MessageStore
        if (getMessageStore() != null)
        {
            //Remove MessageStore Interface should not throw Exception
            try
            {
                getMessageStore().close();
            }
            catch (Exception e)
            {
                _logger.error("Failed to close message store", e);
            }
        }
        if (getDurableConfigurationStore() != null)
        {
            //Remove MessageStore Interface should not throw Exception
            try
            {
                getDurableConfigurationStore().close();
            }
            catch (Exception e)
            {
                _logger.error("Failed to close message store", e);
            }
        }
    }


    protected Logger getLogger()
    {
        return _logger;
    }



    public VirtualHostRegistry getVirtualHostRegistry()
    {
        return _virtualHostRegistry;
    }

    public void registerMessageDelivered(long messageSize)
    {
        _messagesDelivered.registerEvent(1L);
        _dataDelivered.registerEvent(messageSize);
        _brokerStatisticsGatherer.registerMessageDelivered(messageSize);
    }

    public void registerMessageReceived(long messageSize, long timestamp)
    {
        _messagesReceived.registerEvent(1L, timestamp);
        _dataReceived.registerEvent(messageSize, timestamp);
        _brokerStatisticsGatherer.registerMessageReceived(messageSize, timestamp);
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
        return _name;
    }

    public State getState()
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
    }

    public void connectionUnregistered(final AMQConnectionModel connection)
    {
    }

    public void event(final Event event)
    {
        switch(event)
        {
            case PERSISTENT_MESSAGE_SIZE_OVERFULL:
                block();
                break;
            case PERSISTENT_MESSAGE_SIZE_UNDERFULL:
                unblock();
                break;
        }
    }

    protected void setState(State state)
    {
        _state = state;
    }

    protected void attainActivation()
    {
        State finalState = State.ERRORED;

        try
        {
            initialiseHouseKeeping(_vhostConfig.getHousekeepingCheckPeriod());
            finalState = State.ACTIVE;
        }
        finally
        {
            _state = finalState;
            reportIfError(_state);
        }
    }

    protected void reportIfError(State state)
    {
        if (state == State.ERRORED)
        {
            CurrentActor.get().message(VirtualHostMessages.ERRORED());
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
            for (AMQQueue q : _queueRegistry.getQueues())
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
                        session.checkTransactionStatus(_vhostConfig.getTransactionTimeoutOpenWarn(),
                                _vhostConfig.getTransactionTimeoutOpenClose(),
                                _vhostConfig.getTransactionTimeoutIdleWarn(),
                                _vhostConfig.getTransactionTimeoutIdleClose());
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
        public VirtualHost getVirtualHost()
        {
            return AbstractVirtualHost.this;
        }

        @Override
        public org.apache.qpid.server.model.VirtualHost getVirtualHostModel()
        {
            return _model;
        }

    }

    @Override
    public long getDefaultAlertThresholdMessageAge()
    {
        return getConfiguration().getMaximumMessageAge();
    }

    @Override
    public long getDefaultAlertThresholdMessageSize()
    {
        return getConfiguration().getMaximumMessageSize();
    }

    @Override
    public long getDefaultAlertThresholdQueueDepthMessages()
    {
        return getConfiguration().getMaximumMessageCount();
    }

    @Override
    public long getDefaultAlertThresholdQueueDepthBytes()
    {
        return getConfiguration().getMaximumQueueDepth();
    }

    @Override
    public long getDefaultAlertRepeatGap()
    {
        return getConfiguration().getMinimumAlertRepeatGap();
    }

    @Override
    public long getDefaultQueueFlowControlSizeBytes()
    {
        return getConfiguration().getCapacity();
    }

    @Override
    public long getDefaultQueueFlowResumeSizeBytes()
    {
        return getConfiguration().getFlowResumeCapacity();
    }

    @Override
    public int getDefaultMaximumDeliveryAttempts()
    {
        return getConfiguration().getMaxDeliveryCount();
    }

    @Override
    public TaskExecutor getTaskExecutor()
    {
        // todo - remove this once virtualhost is a configured object itself
        return _model.getTaskExecutor();
    }

    @Override
    public org.apache.qpid.server.model.VirtualHost getModel()
    {
        return _model;
    }
}
