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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.AMQBrokerManagerMBean;
import org.apache.qpid.server.binding.BindingFactory;
import org.apache.qpid.server.configuration.BrokerConfig;
import org.apache.qpid.server.configuration.ConfigStore;
import org.apache.qpid.server.configuration.ConfiguredObject;
import org.apache.qpid.server.configuration.ExchangeConfiguration;
import org.apache.qpid.server.configuration.QueueConfiguration;
import org.apache.qpid.server.configuration.VirtualHostConfigType;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.connection.ConnectionRegistry;
import org.apache.qpid.server.connection.IConnectionRegistry;
import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.exchange.DefaultExchangeRegistry;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.federation.BrokerLink;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.VirtualHostMessages;
import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.management.AMQManagedObject;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.DefaultQueueRegistry;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.TransactionLog;
import org.apache.qpid.server.virtualhost.plugins.VirtualHostPlugin;
import org.apache.qpid.server.virtualhost.plugins.VirtualHostPluginFactory;

public class VirtualHostImpl implements VirtualHost
{
    private static final Logger _logger = Logger.getLogger(VirtualHostImpl.class);

    private final String _name;

    private ConnectionRegistry _connectionRegistry;

    private QueueRegistry _queueRegistry;

    private ExchangeRegistry _exchangeRegistry;

    private ExchangeFactory _exchangeFactory;

    private MessageStore _messageStore;

    protected VirtualHostMBean _virtualHostMBean;

    private AMQBrokerManagerMBean _brokerMBean;

    private final AuthenticationManager _authenticationManager;

    private SecurityManager _securityManager;

    private final ScheduledThreadPoolExecutor _houseKeepingTasks;
    private final IApplicationRegistry _appRegistry;
    private VirtualHostConfiguration _configuration;
    private DurableConfigurationStore _durableConfigurationStore;
    private BindingFactory _bindingFactory;
    private BrokerConfig _broker;
    private UUID _id;

    private boolean _statisticsEnabled = false;
    private StatisticsCounter _messagesDelivered, _dataDelivered, _messagesReceived, _dataReceived;

    private final long _createTime = System.currentTimeMillis();
    private final ConcurrentHashMap<BrokerLink,BrokerLink> _links = new ConcurrentHashMap<BrokerLink, BrokerLink>();
    private static final int HOUSEKEEPING_SHUTDOWN_TIMEOUT = 5;

    public IConnectionRegistry getConnectionRegistry()
    {
        return _connectionRegistry;
    }

    public VirtualHostConfiguration getConfiguration()
    {
        return _configuration;
    }

    public UUID getId()
    {
        return _id;
    }

    public VirtualHostConfigType getConfigType()
    {
        return VirtualHostConfigType.getInstance();
    }

    public ConfiguredObject getParent()
    {
        return getBroker();
    }

    public boolean isDurable()
    {
        return false;
    }

    /**
     * Virtual host JMX MBean class.
     *
     * This has some of the methods implemented from management intrerface for exchanges. Any
     * implementaion of an Exchange MBean should extend this class.
     */
    public class VirtualHostMBean extends AMQManagedObject implements ManagedVirtualHost
    {
        public VirtualHostMBean() throws NotCompliantMBeanException
        {
            super(ManagedVirtualHost.class, ManagedVirtualHost.TYPE);
        }

        public String getObjectInstanceName()
        {
            return ObjectName.quote(_name);
        }

        public String getName()
        {
            return _name;
        }

        public VirtualHostImpl getVirtualHost()
        {
            return VirtualHostImpl.this;
        }
    }

    public VirtualHostImpl(IApplicationRegistry appRegistry, VirtualHostConfiguration hostConfig) throws Exception
    {
        this(appRegistry, hostConfig, null);
    }


    public VirtualHostImpl(VirtualHostConfiguration hostConfig, MessageStore store) throws Exception
    {
        this(ApplicationRegistry.getInstance(),hostConfig,store);
    }

    private VirtualHostImpl(IApplicationRegistry appRegistry, VirtualHostConfiguration hostConfig, MessageStore store) throws Exception
    {
		if (hostConfig == null)
		{
			throw new IllegalAccessException("HostConfig and MessageStore cannot be null");
		}
		
        _appRegistry = appRegistry;
        _broker = _appRegistry.getBroker();
        _configuration = hostConfig;
        _name = _configuration.getName();

        _id = _appRegistry.getConfigStore().createId();

        CurrentActor.get().message(VirtualHostMessages.CREATED(_name));

        if (_name == null || _name.length() == 0)
        {
    		throw new IllegalArgumentException("Illegal name (" + _name + ") for virtualhost.");
        }

        _securityManager = new SecurityManager(_appRegistry.getSecurityManager());
        _securityManager.configureHostPlugins(_configuration);

        _virtualHostMBean = new VirtualHostMBean();

        _connectionRegistry = new ConnectionRegistry();

        _houseKeepingTasks = new ScheduledThreadPoolExecutor(_configuration.getHouseKeepingThreadCount());

        _queueRegistry = new DefaultQueueRegistry(this);

        _exchangeFactory = new DefaultExchangeFactory(this);
        _exchangeFactory.initialise(_configuration);

        _exchangeRegistry = new DefaultExchangeRegistry(this);

        StartupRoutingTable configFileRT = new StartupRoutingTable();

        _durableConfigurationStore = configFileRT;

        // This needs to be after the RT has been defined as it creates the default durable exchanges.
        _exchangeRegistry.initialise();

        _bindingFactory = new BindingFactory(this);

        initialiseModel(_configuration);

        if (store != null)
        {
            _messageStore = store;
            _durableConfigurationStore = store;
        }
        else
        {
			initialiseMessageStore(hostConfig);
        }
		
        _authenticationManager = ApplicationRegistry.getInstance().getAuthenticationManager();

        _brokerMBean = new AMQBrokerManagerMBean(_virtualHostMBean);
        _brokerMBean.register();
        initialiseHouseKeeping(hostConfig.getHousekeepingExpiredMessageCheckPeriod());
        
        initialiseStatistics();
    }

	private void initialiseHouseKeeping(long period)
    {
        /* add a timer task to iterate over queues, cleaning expired messages from queues with no consumers */
        if (period != 0L)
        {
            class ExpiredMessagesTask extends HouseKeepingTask
            {
                public ExpiredMessagesTask(VirtualHost vhost)
                {
                    super(vhost);
                }

                public void execute()
                {
                    for (AMQQueue q : _queueRegistry.getQueues())
                    {
                        _logger.debug("Checking message status for queue: "
                                      + q.getName());
                        try
                        {
                            q.checkMessageStatus();
                        }
                        catch (Exception e)
                        {
                            _logger.error("Exception in housekeeping for queue: "
                                          + q.getNameShortString().toString(), e);
                            //Don't throw exceptions as this will stop the
                            // house keeping task from running.
                        }
                    }
                    for (AMQConnectionModel connection : getConnectionRegistry().getConnections())
                    {
                        _logger.debug("Checking for long running open transactions on connection " + connection);
                        for (AMQSessionModel session : connection.getSessionModels())
                        {
	                        _logger.debug("Checking for long running open transactions on session " + session);
                            try
                            {
                                session.checkTransactionStatus(_configuration.getTransactionTimeoutOpenWarn(),
	                                                           _configuration.getTransactionTimeoutOpenClose(),
	                                                           _configuration.getTransactionTimeoutIdleWarn(),
	                                                           _configuration.getTransactionTimeoutIdleClose());
                            }
                            catch (Exception e)
                            {
                                _logger.error("Exception in housekeeping for connection: " + connection.toString(), e);
                            }
                        }
                    }
                }
            }

            scheduleHouseKeepingTask(period, new ExpiredMessagesTask(this));

            Map<String, VirtualHostPluginFactory> plugins =
                ApplicationRegistry.getInstance().getPluginManager().getVirtualHostPlugins();

            if (plugins != null)
            {
                for (Map.Entry<String, VirtualHostPluginFactory> entry : plugins.entrySet())
                {
                    String pluginName = entry.getKey();
                    VirtualHostPluginFactory factory = entry.getValue();
                    try
                    {
                        VirtualHostPlugin plugin = factory.newInstance(this);

                        // If we had configuration for the plugin the schedule it.
                        if (plugin != null)
                        {
                            _houseKeepingTasks.scheduleAtFixedRate(plugin, plugin.getDelay() / 2,
                                                           plugin.getDelay(), plugin.getTimeUnit());

                            _logger.info("Loaded VirtualHostPlugin:" + plugin);
                        }
                    }
                    catch (RuntimeException e)
                    {
                        _logger.error("Unable to load VirtualHostPlugin:" + pluginName + " due to:" + e.getMessage(), e);
                    }
                }
            }
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


    private void initialiseMessageStore(VirtualHostConfiguration hostConfig) throws Exception
    {
        String messageStoreClass = hostConfig.getMessageStoreClass();

        Class clazz = Class.forName(messageStoreClass);
        Object o = clazz.newInstance();

        if (!(o instanceof MessageStore))
        {
            throw new ClassCastException("Message store class must implement " + MessageStore.class + ". Class " + clazz +
                                         " does not.");
        }
        MessageStore messageStore = (MessageStore) o;
        VirtualHostConfigRecoveryHandler recoveryHandler = new VirtualHostConfigRecoveryHandler(this);

        MessageStoreLogSubject storeLogSubject = new MessageStoreLogSubject(this, messageStore);

        messageStore.configureConfigStore(this.getName(),
                                          recoveryHandler,
                                          hostConfig.getStoreConfiguration(),
                                          storeLogSubject);

        messageStore.configureMessageStore(this.getName(),
                                           recoveryHandler,
                                           hostConfig.getStoreConfiguration(),
                                           storeLogSubject);
        messageStore.configureTransactionLog(this.getName(),
                                           recoveryHandler,
                                           hostConfig.getStoreConfiguration(),
                                           storeLogSubject);

        _messageStore = messageStore;
        _durableConfigurationStore = messageStore;
    }

    private void initialiseModel(VirtualHostConfiguration config) throws ConfigurationException, AMQException
    {
        _logger.debug("Loading configuration for virtualhost: " + config.getName());

    	List exchangeNames = config.getExchanges();

        for (Object exchangeNameObj : exchangeNames)
        {
            String exchangeName = String.valueOf(exchangeNameObj);
            configureExchange(config.getExchangeConfiguration(exchangeName));
        }

    	String[] queueNames = config.getQueueNames();

        for (Object queueNameObj : queueNames)
        {
            String queueName = String.valueOf(queueNameObj);
            configureQueue(config.getQueueConfiguration(queueName));
        }
    }

    private void configureExchange(ExchangeConfiguration exchangeConfiguration) throws AMQException
    {
    	AMQShortString exchangeName = new AMQShortString(exchangeConfiguration.getName());

        Exchange exchange;
        exchange = _exchangeRegistry.getExchange(exchangeName);
        if (exchange == null)
        {

    		AMQShortString type = new AMQShortString(exchangeConfiguration.getType());
    		boolean durable = exchangeConfiguration.getDurable();
    		boolean autodelete = exchangeConfiguration.getAutoDelete();

            Exchange newExchange = _exchangeFactory.createExchange(exchangeName, type, durable, autodelete, 0);
            _exchangeRegistry.registerExchange(newExchange);

            if (newExchange.isDurable())
            {
                _durableConfigurationStore.createExchange(newExchange);
            }
        }
    }

    private void configureQueue(QueueConfiguration queueConfiguration) throws AMQException, ConfigurationException
    {
    	AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(queueConfiguration, this);

    	if (queue.isDurable())
    	{
    		getDurableConfigurationStore().createQueue(queue);
    	}

    	String exchangeName = queueConfiguration.getExchange();

    	Exchange exchange = _exchangeRegistry.getExchange(exchangeName == null ? null : new AMQShortString(exchangeName));

        if (exchange == null)
        {
            exchange = _exchangeRegistry.getDefaultExchange();
        }

    	if (exchange == null)
    	{
    		throw new ConfigurationException("Attempt to bind queue to unknown exchange:" + exchangeName);
    	}

        List routingKeys = queueConfiguration.getRoutingKeys();
        if (routingKeys == null || routingKeys.isEmpty())
        {
            routingKeys = Collections.singletonList(queue.getNameShortString());
        }

        for (Object routingKeyNameObj : routingKeys)
        {
            AMQShortString routingKey = new AMQShortString(String.valueOf(routingKeyNameObj));
            if (_logger.isInfoEnabled())
            {
                _logger.info("Binding queue:" + queue + " with routing key '" + routingKey + "' to exchange:" + this);
            }
            _bindingFactory.addBinding(routingKey.toString(), queue, exchange, null);
        }

        if (exchange != _exchangeRegistry.getDefaultExchange())
        {
            _bindingFactory.addBinding(queue.getNameShortString().toString(), queue, exchange, null);
        }
    }

    public String getName()
    {
        return _name;
    }

    public BrokerConfig getBroker()
    {
        return _broker;
    }

    public String getFederationTag()
    {
        return _broker.getFederationTag();
    }

    public void setBroker(final BrokerConfig broker)
    {
        _broker = broker;
    }

    public long getCreateTime()
    {
        return _createTime;
    }

    public QueueRegistry getQueueRegistry()
    {
        return _queueRegistry;
    }

    public ExchangeRegistry getExchangeRegistry()
    {
        return _exchangeRegistry;
    }

    public ExchangeFactory getExchangeFactory()
    {
        return _exchangeFactory;
    }

    public MessageStore getMessageStore()
    {
        return _messageStore;
    }

    public TransactionLog getTransactionLog()
    {
        return _messageStore;
    }

    public DurableConfigurationStore getDurableConfigurationStore()
    {
        return _durableConfigurationStore;
    }

    public AuthenticationManager getAuthenticationManager()
    {
        return _authenticationManager;
    }

    public SecurityManager getSecurityManager()
    {
        return _securityManager;
    }

    public void close()
    {
        //Stop Connections
        _connectionRegistry.close();

        //Stop the Queues processing
        if (_queueRegistry != null)
        {
            for (AMQQueue queue : _queueRegistry.getQueues())
            {
                queue.stop();
            }
        }

        //Stop Housekeeping
        if (_houseKeepingTasks != null)
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
                _logger.warn("Interrupted during Housekeeping shutdown:" + e.getMessage());
                // Swallowing InterruptedException ok as we are shutting down.
            }
        }

        //Close MessageStore
        if (_messageStore != null)
        {
            //Remove MessageStore Interface should not throw Exception
            try
            {
                _messageStore.close();
            }
            catch (Exception e)
            {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }

        CurrentActor.get().message(VirtualHostMessages.CLOSED());
    }

    public ManagedObject getBrokerMBean()
    {
        return _brokerMBean;
    }

    public ManagedObject getManagedObject()
    {
        return _virtualHostMBean;
    }

    public UUID getBrokerId()
    {
        return _appRegistry.getBrokerId();
    }

    public IApplicationRegistry getApplicationRegistry()
    {
        return _appRegistry;
    }

    public BindingFactory getBindingFactory()
    {
        return _bindingFactory;
    }
    
    public void registerMessageDelivered(long messageSize)
    {
        if (isStatisticsEnabled())
        {
            _messagesDelivered.registerEvent(1L);
            _dataDelivered.registerEvent(messageSize);
        }
        _appRegistry.registerMessageDelivered(messageSize);
    }
    
    public void registerMessageReceived(long messageSize, long timestamp)
    {
        if (isStatisticsEnabled())
        {
            _messagesReceived.registerEvent(1L, timestamp);
            _dataReceived.registerEvent(messageSize, timestamp);
        }
        _appRegistry.registerMessageReceived(messageSize, timestamp);
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
        setStatisticsEnabled(!StatisticsCounter.DISABLE_STATISTICS &&
                _appRegistry.getConfiguration().isStatisticsGenerationVirtualhostsEnabled());
        
        _messagesDelivered = new StatisticsCounter("messages-delivered-" + getName());
        _dataDelivered = new StatisticsCounter("bytes-delivered-" + getName());
        _messagesReceived = new StatisticsCounter("messages-received-" + getName());
        _dataReceived = new StatisticsCounter("bytes-received-" + getName());
    }

    public boolean isStatisticsEnabled()
    {
        return _statisticsEnabled;
    }

    public void setStatisticsEnabled(boolean enabled)
    {
        _statisticsEnabled = enabled;
    }

    public void createBrokerConnection(final String transport,
                                       final String host,
                                       final int port,
                                       final String vhost,
                                       final boolean durable,
                                       final String authMechanism,
                                       final String username,
                                       final String password)
    {
        BrokerLink blink = new BrokerLink(this, transport, host, port, vhost, durable, authMechanism, username, password);
        if(_links.putIfAbsent(blink,blink) != null)
        {
            getConfigStore().addConfiguredObject(blink);
        }
    }

    public void removeBrokerConnection(final String transport,
                                       final String host,
                                       final int port,
                                       final String vhost)
    {
        removeBrokerConnection(new BrokerLink(this, transport, host, port, vhost, false, null,null,null));

    }

    public void removeBrokerConnection(BrokerLink blink)
    {
        blink = _links.get(blink);
        if(blink != null)
        {
            blink.close();
            getConfigStore().removeConfiguredObject(blink);
        }
    }

    public ConfigStore getConfigStore()
    {
        return getApplicationRegistry().getConfigStore();
    }

    /**
     * Temporary Startup RT class to record the creation of persistent queues / exchanges.
     *
     *
     * This is so we can replay the creation of queues/exchanges in to the real _RT after it has been loaded.
     * This should be removed after the _RT has been fully split from the the TL
     */
    private static class StartupRoutingTable implements DurableConfigurationStore
    {
        public List<Exchange> exchange = new LinkedList<Exchange>();
        public List<CreateQueueTuple> queue = new LinkedList<CreateQueueTuple>();
        public List<CreateBindingTuple> bindings = new LinkedList<CreateBindingTuple>();

        public void configure(VirtualHost virtualHost, String base, VirtualHostConfiguration config) throws Exception
        {
        }

        public void close() throws Exception
        {
        }

        public void removeMessage(Long messageId) throws AMQException
        {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        public void configureConfigStore(String name,
                                         ConfigurationRecoveryHandler recoveryHandler,
                                         Configuration config,
                                         LogSubject logSubject) throws Exception
        {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        public void createExchange(Exchange exchange) throws AMQStoreException
        {
            if (exchange.isDurable())
            {
                this.exchange.add(exchange);
            }
        }

        public void removeExchange(Exchange exchange) throws AMQStoreException
        {
        }

        public void bindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQStoreException
        {
            if (exchange.isDurable() && queue.isDurable())
            {
                bindings.add(new CreateBindingTuple(exchange, routingKey, queue, args));
            }
        }

        public void unbindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQStoreException
        {
        }

        public void createQueue(AMQQueue queue) throws AMQStoreException
        {
            createQueue(queue, null);
        }

        public void createQueue(AMQQueue queue, FieldTable arguments) throws AMQStoreException
        {
            if (queue.isDurable())
            {
                this.queue.add(new CreateQueueTuple(queue, arguments));
            }
        }

        public void removeQueue(AMQQueue queue) throws AMQStoreException
        {
        }


        private static class CreateQueueTuple
        {
            public AMQQueue queue;
            public FieldTable arguments;

            public CreateQueueTuple(AMQQueue queue, FieldTable arguments)
            {
                this.queue = queue;
                this.arguments = arguments;
            }
        }

        private static class CreateBindingTuple
        {
            public AMQQueue queue;
            public FieldTable arguments;
            public Exchange exchange;
            public AMQShortString routingKey;

            public CreateBindingTuple(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args)
            {
                this.exchange = exchange;
                this.routingKey = routingKey;
                this.queue = queue;
                arguments = args;
            }
        }

        public void updateQueue(AMQQueue queue) throws AMQStoreException
        {
        }
    }

    @Override
    public String toString()
    {
        return _name;
    }
}
