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
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.management.NotCompliantMBeanException;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
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
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.access.ACLManager;
import org.apache.qpid.server.security.access.Accessable;
import org.apache.qpid.server.security.access.plugins.SimpleXML;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.store.MessageStore;

public class VirtualHost implements Accessable
{
    private static final Logger _logger = Logger.getLogger(VirtualHost.class);


    private final String _name;

    private ConnectionRegistry _connectionRegistry;

    private QueueRegistry _queueRegistry;

    private ExchangeRegistry _exchangeRegistry;

    private ExchangeFactory _exchangeFactory;

    private MessageStore _messageStore;

    protected VirtualHostMBean _virtualHostMBean;

    private AMQBrokerManagerMBean _brokerMBean;

    private AuthenticationManager _authenticationManager;

    private ACLManager _accessManager;

    private final Timer _houseKeepingTimer;
     
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


    public VirtualHost(VirtualHostConfiguration hostConfig, MessageStore store) throws Exception
    {
    	_name = hostConfig.getName();
    	if (_name == null || _name.length() == 0)
        {
    		throw new IllegalArgumentException("Illegal name (" + _name + ") for virtualhost.");
        }

        _virtualHostMBean = new VirtualHostMBean();

        _connectionRegistry = new ConnectionRegistry(this);

        _houseKeepingTimer = new Timer("Queue-housekeeping-"+_name, true);
        
        if (store != null)
        {
            _messageStore = store;
        }
        else
        {
            if (hostConfig == null)
            {
                throw new IllegalAccessException("HostConfig and MessageStore cannot be null");
            }
            initialiseMessageStore(hostConfig);
        }
        
        _queueRegistry = new DefaultQueueRegistry(this);
        
        _exchangeFactory = new DefaultExchangeFactory(this);
        _exchangeFactory.initialise(hostConfig);
        _exchangeRegistry = new DefaultExchangeRegistry(this);
        _exchangeRegistry.initialise();
        	
        initialiseModel(hostConfig);
        _authenticationManager = new PrincipalDatabaseAuthenticationManager(_name, hostConfig);

        _accessManager = ApplicationRegistry.getInstance().getAccessManager();
        _accessManager.configureHostPlugins(hostConfig.getSecurityConfiguration());
        
        _brokerMBean = new AMQBrokerManagerMBean(_virtualHostMBean);
        _brokerMBean.register();
        initialiseHouseKeeping(hostConfig.getHousekeepingExpiredMessageCheckPeriod());
    }

    public VirtualHost(VirtualHostConfiguration virtualHostConfig) throws Exception 
    {
		this(virtualHostConfig, null);
	}

	private void initialiseHouseKeeping(long period)
    {
        /* add a timer task to iterate over queues, cleaning expired messages from queues with no consumers */
        if(period != 0L)
        {
            class RemoveExpiredMessagesTask extends TimerTask
            {
                public void run()
                {
                    for(AMQQueue q : _queueRegistry.getQueues())
                    {

                        try
                        {
                            q.checkMessageStatus();
                        }
                        catch (AMQException e)
                        {
                            _logger.error("Exception in housekeeping for queue: " + q.getName().toString(),e);
                            throw new RuntimeException(e);
                        }
                    }
                }
            }

            _houseKeepingTimer.scheduleAtFixedRate(new RemoveExpiredMessagesTask(),
                    period/2,
                    period);
        }
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
        _messageStore = (MessageStore) o;
        _messageStore.configure(this, "store", hostConfig);
    }

    private void initialiseModel(VirtualHostConfiguration config) throws ConfigurationException, AMQException
    {
    	_logger.debug("Loading configuration for virtualhost: "+config.getName());

    	List exchangeNames = config.getExchanges();

    	for(Object exchangeNameObj : exchangeNames)
    	{
    		String exchangeName = String.valueOf(exchangeNameObj);
    		configureExchange(config.getExchangeConfiguration(exchangeName));
    	}

    	String[] queueNames = config.getQueueNames();

    	for(Object queueNameObj : queueNames)
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
    	if(exchange == null)
    	{

    		AMQShortString type = new AMQShortString(exchangeConfiguration.getType());
    		boolean durable = exchangeConfiguration.getDurable();
    		boolean autodelete = exchangeConfiguration.getAutoDelete();

    		Exchange newExchange = _exchangeFactory.createExchange(exchangeName,type,durable,autodelete,0);
    		_exchangeRegistry.registerExchange(newExchange);
    	}
    }

    private void configureQueue(QueueConfiguration queueConfiguration) throws AMQException, ConfigurationException
    {
    	AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(queueConfiguration, this);

    	if (queue.isDurable())
    	{
    		_messageStore.createQueue(queue);
    	}

    	String exchangeName = queueConfiguration.getExchange();

    	Exchange exchange = _exchangeRegistry.getExchange(exchangeName == null ? null : new AMQShortString(exchangeName));

    	if(exchange == null)
    	{
    		exchange = _exchangeRegistry.getDefaultExchange();
    	}

    	if (exchange == null)
    	{
    		throw new ConfigurationException("Attempt to bind queue to unknown exchange:" + exchangeName);
    	}

    	List routingKeys = queueConfiguration.getRoutingKeys();
    	if(routingKeys == null || routingKeys.isEmpty())
    	{
    		routingKeys = Collections.singletonList(queue.getName());
    	}

    	for(Object routingKeyNameObj : routingKeys)
    	{
    		AMQShortString routingKey = new AMQShortString(String.valueOf(routingKeyNameObj));
    		queue.bind(exchange, routingKey, null);
    		_logger.info("Queue '" + queue.getName() + "' bound to exchange:" + exchangeName + " RK:'" + routingKey + "'");
    	}

    	if(exchange != _exchangeRegistry.getDefaultExchange())
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

    public MessageStore getMessageStore()
    {
        return _messageStore;
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

        //Stop the Queues processing
        if (_queueRegistry != null)
        {
            for (AMQQueue queue : _queueRegistry.getQueues())
            {
                queue.stop();
            }
        }        

        //Stop Housekeeping
        if (_houseKeepingTimer != null)
        {
            _houseKeepingTimer.cancel();
        }        

        //Close MessageStore
        if (_messageStore != null)
        {
            _messageStore.close();
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
}
