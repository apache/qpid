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
import org.apache.log4j.Logger;
import org.apache.qpid.server.AMQBrokerManagerMBean;
import org.apache.qpid.server.configuration.Configurator;
import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.exchange.DefaultExchangeRegistry;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.management.AMQManagedObject;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.queue.DefaultQueueRegistry;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.MessageStore;

public class VirtualHost
{
    private static final Logger _logger = Logger.getLogger(VirtualHost.class);


    private final String _name;

    private QueueRegistry _queueRegistry;

    private ExchangeRegistry _exchangeRegistry;

    private ExchangeFactory _exchangeFactory;

   private MessageStore _messageStore;

    protected VirtualHostMBean _virtualHostMBean;

    private AMQBrokerManagerMBean _brokerMBean;


    /**
         * Abstract MBean class. This has some of the methods implemented from
         * management intrerface for exchanges. Any implementaion of an
         * Exchange MBean should extend this class.
         */
        public class VirtualHostMBean extends AMQManagedObject implements ManagedVirtualHost
        {
            public VirtualHostMBean() throws NotCompliantMBeanException
            {
                super(ManagedVirtualHost.class, "VirtualHost");
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


    public VirtualHost(String name, MessageStore store) throws Exception
    {
        _name = name;

        _virtualHostMBean = new VirtualHostMBean();
        // This isn't needed to be registered
        //_virtualHostMBean.register();

        _queueRegistry = new DefaultQueueRegistry(this);
        _exchangeFactory = new DefaultExchangeFactory(this);
        _exchangeRegistry = new DefaultExchangeRegistry(_exchangeFactory);

        _messageStore = store;

        _brokerMBean = new AMQBrokerManagerMBean(_virtualHostMBean);
        _brokerMBean.register();

    }
    public VirtualHost(String name, Configuration hostConfig) throws Exception
    {
        _name = name;

        _virtualHostMBean = new VirtualHostMBean();
        // This isn't needed to be registered
        //_virtualHostMBean.register();
        
        _queueRegistry = new DefaultQueueRegistry(this);
        _exchangeFactory = new DefaultExchangeFactory(this);
        _exchangeRegistry = new DefaultExchangeRegistry(_exchangeFactory);

        initialiseMessageStore(hostConfig);

        _brokerMBean = new AMQBrokerManagerMBean(_virtualHostMBean);
        _brokerMBean.register();

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
            throw new IllegalArgumentException("Unable to instantiate configuration class " + instanceType + " - ensure it has a public default constructor");
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

    public void close() throws Exception
    {
        if(_messageStore != null)
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
