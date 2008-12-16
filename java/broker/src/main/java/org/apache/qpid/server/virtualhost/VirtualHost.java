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

import javax.management.NotCompliantMBeanException;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Logger;
import org.apache.qpid.server.AMQBrokerManagerMBean;
import org.apache.qpid.server.connection.ConnectionRegistry;
import org.apache.qpid.server.connection.IConnectionRegistry;
import org.apache.qpid.server.security.access.ACLPlugin;
import org.apache.qpid.server.security.access.ACLManager;
import org.apache.qpid.server.security.access.Accessable;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.configuration.Configurator;
import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.exchange.DefaultExchangeRegistry;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.management.AMQManagedObject;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.queue.DefaultQueueRegistry;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.AMQException;

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

    private MessageStore _messageStore;

    protected VirtualHostMBean _virtualHostMBean;

    private AMQBrokerManagerMBean _brokerMBean;

    private AuthenticationManager _authenticationManager;

    private ACLPlugin _accessManager;

    private final Timer _houseKeepingTimer;
     
    private static final long DEFAULT_HOUSEKEEPING_PERIOD = 30000L;


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

    /**
     * Used for testing only
     * @param name
     * @param store
     * @throws Exception
     */
    public VirtualHost(String name, MessageStore store) throws Exception
    {
        this(name, new PropertiesConfiguration(), store);
    }

    /**
     * Normal Constructor
     * @param name
     * @param hostConfig
     * @throws Exception
     */
    public VirtualHost(String name, Configuration hostConfig) throws Exception
    {
        this(name, hostConfig, null);
    }

    public VirtualHost(String name, Configuration hostConfig, MessageStore store) throws Exception
    {
        if (name == null || name.length() == 0)
        {
            throw new IllegalArgumentException("Illegal name (" + name + ") for virtualhost.");
        }

        _name = name;

        _virtualHostMBean = new VirtualHostMBean();

        _connectionRegistry = new ConnectionRegistry(this);

        _houseKeepingTimer = new Timer("Queue-housekeeping-"+name, true);
        _queueRegistry = new DefaultQueueRegistry(this);
        _exchangeFactory = new DefaultExchangeFactory(this);
        _exchangeFactory.initialise(hostConfig);
        _exchangeRegistry = new DefaultExchangeRegistry(this);

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

        _exchangeRegistry.initialise();

        _authenticationManager = new PrincipalDatabaseAuthenticationManager(name, hostConfig);

        _accessManager = ACLManager.loadACLManager(name, hostConfig);

        _brokerMBean = new AMQBrokerManagerMBean(_virtualHostMBean);
        _brokerMBean.register();
        initialiseHouseKeeping(hostConfig);
    }

    private void initialiseHouseKeeping(final Configuration hostConfig)
    {
     
        long period = hostConfig.getLong("housekeeping.expiredMessageCheckPeriod", DEFAULT_HOUSEKEEPING_PERIOD);
    
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
                            q.removeExpiredIfNoSubscribers();
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
    
    private void initialiseMessageStore(Configuration config) throws Exception
    {
        String messageStoreClass = config.getString("store.class");

        Class clazz = Class.forName(messageStoreClass);
        Object o = clazz.newInstance();

        if (!(o instanceof MessageStore))
        {
            throw new ClassCastException("Message store class must implement " + MessageStore.class + ". Class " + clazz +
                                         " does not.");
        }
        _messageStore = (MessageStore) o;
        _messageStore.configure(this, "store", config);
    }


    public <T> T getConfiguredObject(Class<T> instanceType, Configuration config)
    {
        T instance;
        try
        {
            instance = instanceType.newInstance();
        }
        catch (Exception e)
        {
            _logger.error("Unable to instantiate configuration class " + instanceType + " - ensure it has a public default constructor");
            throw new IllegalArgumentException("Unable to instantiate configuration class " + instanceType + " - ensure it has a public default constructor", e);
        }
        Configurator.configure(instance);

        return instance;
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

    public ACLPlugin getAccessManager()
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
