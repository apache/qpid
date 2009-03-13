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
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.AMQBrokerManagerMBean;
import org.apache.qpid.server.configuration.ExchangeConfiguration;
import org.apache.qpid.server.configuration.QueueConfiguration;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.connection.ConnectionRegistry;
import org.apache.qpid.server.connection.IConnectionRegistry;
import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.exchange.DefaultExchangeRegistry;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.management.AMQManagedObject;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.DefaultQueueRegistry;
import org.apache.qpid.server.queue.FileQueueBackingStoreFactory;
import org.apache.qpid.server.queue.QueueBackingStoreFactory;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.routing.RoutingTable;
import org.apache.qpid.server.security.access.ACLManager;
import org.apache.qpid.server.security.access.Accessable;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.transactionlog.TransactionLog;

import javax.management.NotCompliantMBeanException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class VirtualHost implements Accessable
{
    private static final Logger _logger = Logger.getLogger(VirtualHost.class);

    private final String _name;

    private ConnectionRegistry _connectionRegistry;

    private QueueRegistry _queueRegistry;

    private ExchangeRegistry _exchangeRegistry;

    private ExchangeFactory _exchangeFactory;

    private TransactionLog _transactionLog;

    private RoutingTable _routingTable;

    protected VirtualHostMBean _virtualHostMBean;

    private AMQBrokerManagerMBean _brokerMBean;

    private AuthenticationManager _authenticationManager;

    private ACLManager _accessManager;

    private final Timer _houseKeepingTimer;

    private VirtualHostConfiguration _configuration;
    private QueueBackingStoreFactory _queueBackingStoreFactory;

    public void setAccessableName(String name)
    {
        _logger.warn("Setting Accessable Name for VirualHost is not allowed. ("
                     + name + ") ignored remains :" + getAccessableName());
    }

    public String getAccessableName()
    {
        return _name;
    }

    public IConnectionRegistry getConnectionRegistry()
    {
        return _connectionRegistry;
    }

    public RoutingTable getRoutingTable()
    {
        return _routingTable;
    }

    public VirtualHostConfiguration getConfiguration()
    {
        return _configuration;
    }

    public QueueBackingStoreFactory getQueueBackingStoreFactory()
    {
        return _queueBackingStoreFactory;
    }

    /**
     * Abstract MBean class. This has some of the methods implemented from management intrerface for exchanges. Any
     * implementaion of an Exchange MBean should extend this class.
     */
    public class VirtualHostMBean extends AMQManagedObject implements ManagedVirtualHost
    {
        public VirtualHostMBean() throws NotCompliantMBeanException
        {
            super(ManagedVirtualHost.class, ManagedVirtualHost.TYPE, ManagedVirtualHost.VERSION);
        }

        public String getObjectInstanceName()
        {
            return _name.toString();
        }

        public String getName()
        {
            return _name.toString();
        }

        public VirtualHost getVirtualHost()
        {
            return VirtualHost.this;
        }

    } // End of MBean class

    /**
     * Normal Constructor
     *
     * @param hostConfig
     *
     * @throws Exception
     */
    public VirtualHost(VirtualHostConfiguration hostConfig) throws Exception
    {
        this(hostConfig, null);
    }

    public VirtualHost(VirtualHostConfiguration hostConfig, TransactionLog transactionLog) throws Exception
    {
        _configuration = hostConfig;
        _name = hostConfig.getName();

        if (_name == null || _name.length() == 0)
        {
            throw new IllegalArgumentException("Illegal name (" + _name + ") for virtualhost.");
        }

        _virtualHostMBean = new VirtualHostMBean();

        _connectionRegistry = new ConnectionRegistry(this);

        _houseKeepingTimer = new Timer("Queue-housekeeping-" + _name, true);

        _queueRegistry = new DefaultQueueRegistry(this);

        _exchangeFactory = new DefaultExchangeFactory(this);
        _exchangeFactory.initialise(hostConfig);

        _exchangeRegistry = new DefaultExchangeRegistry(this);

        _queueBackingStoreFactory = new FileQueueBackingStoreFactory();
        _queueBackingStoreFactory.configure(this, hostConfig);

        //Create a temporary RT to store the durable entries from the config file
        // so we can replay them in to the real _RT after it has been loaded.
        /// This should be removed after the _RT has been fully split from the the TL

        StartupRoutingTable configFileRT = new StartupRoutingTable();

        _routingTable = configFileRT;

        // This needs to be after the RT has been defined as it creates the default durable exchanges.
        _exchangeRegistry.initialise();
        initialiseModel(hostConfig);

        if (transactionLog != null)
        {
            _transactionLog = transactionLog;
            if (_transactionLog instanceof RoutingTable)
            {
                _routingTable = (RoutingTable) _transactionLog;
            }
        }
        else
        {
            initialiseTransactionLog(hostConfig);
            initialiseRoutingTable(hostConfig);
        }

        //Now that the RT has been initialised loop through the persistent queues/exchanges created from the config
        // file and write them in to the new routing Table.
        for (StartupRoutingTable.CreateQueueTuple cqt : configFileRT.queue)
        {
            _routingTable.createQueue(cqt.queue, cqt.arguments);
        }

        for (Exchange exchange : configFileRT.exchange)
        {
            _routingTable.createExchange(exchange);
        }

        for (StartupRoutingTable.CreateBindingTuple cbt : configFileRT.bindings)
        {
            _routingTable.bindQueue(cbt.exchange, cbt.routingKey, cbt.queue, cbt.arguments);
        }

        _authenticationManager = new PrincipalDatabaseAuthenticationManager(_name, hostConfig);

        _accessManager = ApplicationRegistry.getInstance().getAccessManager();
        _accessManager.configureHostPlugins(hostConfig.getSecurityConfiguration());

        _brokerMBean = new AMQBrokerManagerMBean(_virtualHostMBean);
        _brokerMBean.register();
        initialiseHouseKeeping(hostConfig.getHousekeepingExpiredMessageCheckPeriod());
    }

    private void initialiseHouseKeeping(long period)
    {
        /* add a timer task to iterate over queues, cleaning expired messages from queues with no consumers */
        if (period != 0L)
        {
            class RemoveExpiredMessagesTask extends TimerTask
            {
                public void run()
                {
                    for (AMQQueue q : _queueRegistry.getQueues())
                    {

                        try
                        {
                            q.checkMessageStatus();
                        }
                        catch (AMQException e)
                        {
                            _logger.error("Exception in housekeeping for queue: " + q.getName().toString(), e);
                            throw new RuntimeException(e);
                        }
                    }
                }
            }

            _houseKeepingTimer.scheduleAtFixedRate(new RemoveExpiredMessagesTask(),
                                                   period / 2,
                                                   period);
        }
    }

    //todo we need to move from store.class to transactionlog.class
    private void initialiseTransactionLog(VirtualHostConfiguration config) throws Exception
    {
        String transactionLogClass = config.getTransactionLogClass();

        Class clazz = Class.forName(transactionLogClass);
        Object o = clazz.newInstance();

        if (!(o instanceof TransactionLog))
        {
            throw new ClassCastException("TransactionLog class must implement " + TransactionLog.class + ". Class " + clazz +
                                         " does not.");
        }
        _transactionLog = (TransactionLog) o;

        //Assign RoutingTable as old MessageStores converted to TransactionLog will require the _routingTable.
        if (_transactionLog instanceof RoutingTable)
        {
            _routingTable = (RoutingTable) _transactionLog;
        }

        _transactionLog.configure(this, "store", config);
    }

    //todo we need to move from store.class to transactionlog.class
    private void initialiseRoutingTable(VirtualHostConfiguration hostConfig) throws Exception
    {
        String transactionLogClass = hostConfig.getRoutingTableClass();

        if (transactionLogClass != null)
        {
            Class clazz = Class.forName(transactionLogClass);
            Object o = clazz.newInstance();

            if (!(o instanceof RoutingTable))
            {
                throw new ClassCastException("RoutingTable class must implement " + RoutingTable.class + ". Class " + clazz +
                                             " does not.");
            }
            _routingTable = (RoutingTable) o;
            _routingTable.configure(this, "routingtable", hostConfig);
        }
        else
        {
            if (_routingTable == null)
            {
                throw new RuntimeException("No Routing Table configured unable to startup.");
            }
        }
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
        }
    }

    private void configureQueue(QueueConfiguration queueConfiguration) throws AMQException, ConfigurationException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(queueConfiguration, this);

        if (queue.isDurable())
        {
            _routingTable.createQueue(queue);
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
            routingKeys = Collections.singletonList(queue.getName());
        }

        for (Object routingKeyNameObj : routingKeys)
        {
            AMQShortString routingKey = new AMQShortString(String.valueOf(routingKeyNameObj));
            if (_logger.isInfoEnabled())
            {
                _logger.info("Binding queue:" + queue + " with routing key '" + routingKey + "' to exchange:" + this);
            }
            queue.bind(exchange, routingKey, null);
        }

        if (exchange != _exchangeRegistry.getDefaultExchange())
        {
            queue.bind(_exchangeRegistry.getDefaultExchange(), queue.getName(), null);
        }
    }

    public String getName()
    {
        return _name;
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

    public ApplicationRegistry getApplicationRegistry()
    {
        throw new UnsupportedOperationException();
    }

    public TransactionLog getTransactionLog()
    {
        return _transactionLog;
    }

    public AuthenticationManager getAuthenticationManager()
    {
        return _authenticationManager;
    }

    public ACLManager getAccessManager()
    {
        return _accessManager;
    }

    public void close() throws Exception
    {

        //Stop Connections
        _connectionRegistry.close();

        //Stop Housekeeping
        if (_houseKeepingTimer != null)
        {
            _houseKeepingTimer.cancel();
        }

        //Stop the Queues processing
        if (_queueRegistry != null)
        {
            for (AMQQueue queue : _queueRegistry.getQueues())
            {
                queue.stop();
            }
        }

        //Close TransactionLog
        if (_transactionLog != null)
        {
            _transactionLog.close();
        }
    }

    public ManagedObject getBrokerMBean()
    {
        return _brokerMBean;
    }

    public ManagedObject getManagedObject()
    {
        return _virtualHostMBean;
    }

    /**
     * Temporary Startup RT class to record the creation of persistent queues / exchanges.
     *
     *
     * This is so we can replay the creation of queues/exchanges in to the real _RT after it has been loaded.
     * This should be removed after the _RT has been fully split from the the TL
     */
    private class StartupRoutingTable implements RoutingTable
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

        public void createExchange(Exchange exchange) throws AMQException
        {
            if (exchange.isDurable())
            {
                this.exchange.add(exchange);
            }
        }

        public void removeExchange(Exchange exchange) throws AMQException
        {
        }

        public void bindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
        {
            if (exchange.isDurable() && queue.isDurable())
            {
                bindings.add(new CreateBindingTuple(exchange, routingKey, queue, args));
            }
        }

        public void unbindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
        {
        }

        public void createQueue(AMQQueue queue) throws AMQException
        {
            createQueue(queue, null);
        }

        public void createQueue(AMQQueue queue, FieldTable arguments) throws AMQException
        {
            if (queue.isDurable())
            {
                this.queue.add(new CreateQueueTuple(queue, arguments));
            }
        }

        public void removeQueue(AMQQueue queue) throws AMQException
        {
        }

        private class CreateQueueTuple
        {
            public AMQQueue queue;
            public FieldTable arguments;

            public CreateQueueTuple(AMQQueue queue, FieldTable arguments)
            {
                this.queue = queue;
                this.arguments = arguments;
            }
        }

        private class CreateBindingTuple
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
    }
}
