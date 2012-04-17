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

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.management.AMQBrokerManagerMBean;
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
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.VirtualHostMessages;
import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.management.VirtualHostMBean;
import org.apache.qpid.server.protocol.v1_0.LinkRegistry;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.DefaultQueueRegistry;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.store.Event;
import org.apache.qpid.server.store.EventListener;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreFactory;
import org.apache.qpid.server.txn.DtxRegistry;
import org.apache.qpid.server.virtualhost.plugins.VirtualHostPlugin;
import org.apache.qpid.server.virtualhost.plugins.VirtualHostPluginFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.management.JMException;

public class VirtualHostImpl implements VirtualHost
{
    private static final Logger _logger = Logger.getLogger(VirtualHostImpl.class);

    private static final int HOUSEKEEPING_SHUTDOWN_TIMEOUT = 5;

    private final UUID _id;

    private final String _name;

    private final long _createTime = System.currentTimeMillis();

    private final ConcurrentHashMap<BrokerLink,BrokerLink> _links = new ConcurrentHashMap<BrokerLink, BrokerLink>();

    private final ScheduledThreadPoolExecutor _houseKeepingTasks;

    private final IApplicationRegistry _appRegistry;

    private final SecurityManager _securityManager;

    private final BrokerConfig _brokerConfig;

    private final VirtualHostConfiguration _vhostConfig;

    private final VirtualHostMBean _virtualHostMBean;

    private final AMQBrokerManagerMBean _brokerMBean;

    private final QueueRegistry _queueRegistry;

    private final ExchangeRegistry _exchangeRegistry;

    private final ExchangeFactory _exchangeFactory;

    private final ConnectionRegistry _connectionRegistry;

    private final BindingFactory _bindingFactory;

    private final DtxRegistry _dtxRegistry;

    private final MessageStore _messageStore;

    private State _state = State.INITIALISING;

    private boolean _statisticsEnabled = false;

    private StatisticsCounter _messagesDelivered, _dataDelivered, _messagesReceived, _dataReceived;

    private final Map<String, LinkRegistry> _linkRegistry = new HashMap<String, LinkRegistry>();

    public VirtualHostImpl(IApplicationRegistry appRegistry, VirtualHostConfiguration hostConfig) throws Exception
    {
        if (hostConfig == null)
        {
            throw new IllegalArgumentException("HostConfig cannot be null");
        }

        if (hostConfig.getName() == null || hostConfig.getName().length() == 0)
        {
            throw new IllegalArgumentException("Illegal name (" + hostConfig.getName() + ") for virtualhost.");
        }

        _appRegistry = appRegistry;
        _brokerConfig = _appRegistry.getBroker();
        _vhostConfig = hostConfig;
        _name = _vhostConfig.getName();
        _dtxRegistry = new DtxRegistry();

        _id = _appRegistry.getConfigStore().createId();

        CurrentActor.get().message(VirtualHostMessages.CREATED(_name));

        _virtualHostMBean = new VirtualHostMBean(this);
        _securityManager = new SecurityManager(_appRegistry.getSecurityManager());
        _securityManager.configureHostPlugins(_vhostConfig);

        _connectionRegistry = new ConnectionRegistry();

        _houseKeepingTasks = new ScheduledThreadPoolExecutor(_vhostConfig.getHouseKeepingThreadCount());

        _queueRegistry = new DefaultQueueRegistry(this);

        _exchangeFactory = new DefaultExchangeFactory(this);
        _exchangeFactory.initialise(_vhostConfig);

        _exchangeRegistry = new DefaultExchangeRegistry(this);

        _bindingFactory = new BindingFactory(this);

        _brokerMBean = new AMQBrokerManagerMBean(_virtualHostMBean);

        _messageStore = initialiseMessageStore(hostConfig.getMessageStoreFactoryClass());

        configureMessageStore(hostConfig);

        activateNonHAMessageStore();

        initialiseStatistics();
    }

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

            Map<String, VirtualHostPluginFactory> plugins = _appRegistry.getPluginManager().getVirtualHostPlugins();

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

    private void shutdownHouseKeeping()
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

    private void removeHouseKeepingTasks()
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


    private MessageStore initialiseMessageStore(final String messageStoreFactoryClass) throws Exception
    {
        final Class<?> clazz = Class.forName(messageStoreFactoryClass);
        final Object o = clazz.newInstance();

        if (!(o instanceof MessageStoreFactory))
        {
            throw new ClassCastException("Message store factory class must implement " + MessageStoreFactory.class +
                                        ". Class " + clazz + " does not.");
        }

        final MessageStoreFactory messageStoreFactory = (MessageStoreFactory) o;
        final MessageStoreLogSubject storeLogSubject = new MessageStoreLogSubject(this, messageStoreFactory.getStoreClassName());
        final MessageStore messageStore = messageStoreFactory.createMessageStore(storeLogSubject);

        messageStore.addEventListener(new BeforeActivationListener(), Event.BEFORE_ACTIVATE);
        messageStore.addEventListener(new AfterActivationListener(), Event.AFTER_ACTIVATE);
        messageStore.addEventListener(new BeforeCloseListener(), Event.BEFORE_CLOSE);
        messageStore.addEventListener(new BeforePassivationListener(), Event.BEFORE_PASSIVATE);

        return messageStore;
    }

    private void configureMessageStore(VirtualHostConfiguration hostConfig) throws Exception
    {

        VirtualHostConfigRecoveryHandler recoveryHandler = new VirtualHostConfigRecoveryHandler(this);

        // TODO perhaps pass config on construction??
        _messageStore.configureConfigStore(getName(), recoveryHandler, hostConfig.getStoreConfiguration());
        _messageStore.configureMessageStore(getName(), recoveryHandler, recoveryHandler, hostConfig.getStoreConfiguration());

    }

    private void activateNonHAMessageStore() throws Exception
    {
        _messageStore.activate();
    }

    private void initialiseModel(VirtualHostConfiguration config) throws ConfigurationException, AMQException
    {
        _logger.debug("Loading configuration for virtualhost: " + config.getName());

        List<String> exchangeNames = config.getExchanges();

        for (String exchangeName : exchangeNames)
        {
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
                _messageStore.createExchange(newExchange);
            }
        }
    }

    private void configureQueue(QueueConfiguration queueConfiguration) throws AMQException, ConfigurationException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(queueConfiguration, this);
        String queueName = queue.getName();

        if (queue.isDurable())
        {
            getMessageStore().createQueue(queue);
        }

        //get the exchange name (returns default exchange name if none was specified)
        String exchangeName = queueConfiguration.getExchange();

        Exchange exchange = _exchangeRegistry.getExchange(exchangeName);
        if (exchange == null)
        {
            throw new ConfigurationException("Attempt to bind queue '" + queueName + "' to unknown exchange:" + exchangeName);
        }

        Exchange defaultExchange = _exchangeRegistry.getDefaultExchange();

        //get routing keys in configuration (returns empty list if none are defined)
        List<?> routingKeys = queueConfiguration.getRoutingKeys();

        for (Object routingKeyNameObj : routingKeys)
        {
            String routingKey = String.valueOf(routingKeyNameObj);

            if (exchange.equals(defaultExchange) && !queueName.equals(routingKey))
            {
                throw new ConfigurationException("Illegal attempt to bind queue '" + queueName +
                        "' to the default exchange with a key other than the queue name: " + routingKey);
            }

            configureBinding(queue, exchange, routingKey);
        }

        if (!exchange.equals(defaultExchange))
        {
            //bind the queue to the named exchange using its name
            configureBinding(queue, exchange, queueName);
        }

        //ensure the queue is bound to the default exchange using its name
        configureBinding(queue, defaultExchange, queueName);
    }

    private void configureBinding(AMQQueue queue, Exchange exchange, String routingKey) throws AMQException
    {
        if (_logger.isInfoEnabled())
        {
            _logger.info("Binding queue:" + queue + " with routing key '" + routingKey + "' to exchange:" + exchange.getName());
        }
        _bindingFactory.addBinding(routingKey, queue, exchange, null);
    }

    public String getName()
    {
        return _name;
    }

    public BrokerConfig getBroker()
    {
        return _brokerConfig;
    }

    public String getFederationTag()
    {
        return _brokerConfig.getFederationTag();
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
                _logger.error("Failed to close message store", e);
            }
        }

        _state = State.STOPPED;

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

    public BrokerLink createBrokerConnection(UUID id, long createTime, Map<String,String> arguments)
    {
        BrokerLink blink = new BrokerLink(this, id, createTime, arguments);
        // TODO - cope with duplicate broker link creation requests
        _links.putIfAbsent(blink,blink);
        getConfigStore().addConfiguredObject(blink);
        return blink;
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

        // TODO - cope with duplicate broker link creation requests
        _links.putIfAbsent(blink,blink);
        getConfigStore().addConfiguredObject(blink);

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

    public ConfigStore getConfigStore()
    {
        return getApplicationRegistry().getConfigStore();
    }

    public DtxRegistry getDtxRegistry()
    {
        return _dtxRegistry;
    }

    @Override
    public String toString()
    {
        return _name;
    }

    @Override
    public State getState()
    {
        return _state;
    }

    private final class BeforeActivationListener implements EventListener
   {
       @Override
       public void event(Event event)
       {
           try
           {
               _exchangeRegistry.initialise();
               initialiseModel(_vhostConfig);
           }
           catch (Exception e)
           {
               throw new RuntimeException("Failed to initialise virtual host after state change", e);
           }
       }
   }

   private final class AfterActivationListener implements EventListener
   {
       @Override
       public void event(Event event)
       {
           initialiseHouseKeeping(_vhostConfig.getHousekeepingCheckPeriod());
           try
           {
               _brokerMBean.register();
           }
           catch (JMException e)
           {
               throw new RuntimeException("Failed to register virtual host mbean for virtual host " + getName(), e);
           }

           _state = State.ACTIVE;
       }
   }

    public class BeforePassivationListener implements EventListener
    {

        @Override
        public void event(Event event)
        {
            _connectionRegistry.close(IConnectionRegistry.VHOST_PASSIVATE_REPLY_TEXT);
            _brokerMBean.unregister();
            removeHouseKeepingTasks();

            _queueRegistry.stopAllAndUnregisterMBeans();
            _exchangeRegistry.clearAndUnregisterMbeans();
            _dtxRegistry.close();

            _state = State.PASSIVE;
        }
    }

   private final class BeforeCloseListener implements EventListener
   {
       @Override
       public void event(Event event)
       {
           _brokerMBean.unregister();
           shutdownHouseKeeping();
       }
   }

   private class VirtualHostHouseKeepingTask extends HouseKeepingTask
   {
       public VirtualHostHouseKeepingTask()
       {
           super(VirtualHostImpl.this);
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
               if (_logger.isDebugEnabled())
               {
                   _logger.debug("Checking for long running open transactions on connection " + connection);
               }
               for (AMQSessionModel session : connection.getSessionModels())
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
                   }
                   catch (Exception e)
                   {
                       _logger.error("Exception in housekeeping for connection: " + connection.toString(), e);
                   }
               }
           }
       }
   }
}
