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
package org.apache.qpid.server.configuration;

import java.util.Collections;
import java.util.List;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class VirtualHostConfiguration
{
    private static final Logger _logger = Logger.getLogger(VirtualHostConfiguration.class);

    private static XMLConfiguration _config;

    private static final String VIRTUALHOST_PROPERTY_BASE = "virtualhost.";


    public VirtualHostConfiguration(String configFile) throws ConfigurationException
    {
        _logger.info("Loading Config file:" + configFile);

        _config = new XMLConfiguration(configFile);

    }



    private void configureVirtualHost(String virtualHostName, Configuration configuration) throws ConfigurationException, AMQException
    {
        _logger.debug("Loding configuration for virtaulhost: "+virtualHostName);


        VirtualHost virtualHost = ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost(virtualHostName);



        if(virtualHost == null)
        {
            throw new ConfigurationException("Unknown virtual host: " + virtualHostName);
        }

        List exchangeNames = configuration.getList("exchanges.exchange.name");

        for(Object exchangeNameObj : exchangeNames)
        {
            String exchangeName = String.valueOf(exchangeNameObj);
            configureExchange(virtualHost, exchangeName, configuration);
        }


        List queueNames = configuration.getList("queues.queue.name");

        for(Object queueNameObj : queueNames)
        {
            String queueName = String.valueOf(queueNameObj);
            configureQueue(virtualHost, queueName, configuration);
        }

    }

    private void configureExchange(VirtualHost virtualHost, String exchangeNameString, Configuration configuration) throws AMQException
    {

        CompositeConfiguration exchangeConfiguration = new CompositeConfiguration();

        exchangeConfiguration.addConfiguration(configuration.subset("exchanges.exchange."+ exchangeNameString));
        exchangeConfiguration.addConfiguration(configuration.subset("exchanges"));

        QueueRegistry queueRegistry = virtualHost.getQueueRegistry();
        MessageStore messageStore = virtualHost.getMessageStore();
        ExchangeRegistry exchangeRegistry = virtualHost.getExchangeRegistry();
        ExchangeFactory exchangeFactory = virtualHost.getExchangeFactory();

        AMQShortString exchangeName = new AMQShortString(exchangeNameString);


        Exchange exchange;



        synchronized (exchangeRegistry)
        {
            exchange = exchangeRegistry.getExchange(exchangeName);
            if(exchange == null)
            {

                AMQShortString type = new AMQShortString(exchangeConfiguration.getString("type","direct"));
                boolean durable = exchangeConfiguration.getBoolean("durable",false);
                boolean autodelete = exchangeConfiguration.getBoolean("autodelete",false);

                Exchange newExchange = exchangeFactory.createExchange(exchangeName,type,durable,autodelete,0);
                exchangeRegistry.registerExchange(newExchange);
            }

        }
    }

    public static CompositeConfiguration getDefaultQueueConfiguration(VirtualHost host)
    {
        CompositeConfiguration queueConfiguration = null;
        if (_config == null)
            return null;

        Configuration vHostConfiguration = _config.subset(VIRTUALHOST_PROPERTY_BASE + host.getName());

        if (vHostConfiguration == null)
            return null;

        Configuration defaultQueueConfiguration = vHostConfiguration.subset("queues");
        if (defaultQueueConfiguration != null)
        {
            queueConfiguration = new CompositeConfiguration();
            queueConfiguration.addConfiguration(defaultQueueConfiguration);
        }

        return queueConfiguration;
    }

    private void configureQueue(VirtualHost virtualHost, String queueNameString, Configuration configuration) throws AMQException, ConfigurationException
    {
        CompositeConfiguration queueConfiguration = new CompositeConfiguration();

        queueConfiguration.addConfiguration(configuration.subset("queues.queue."+ queueNameString));
        queueConfiguration.addConfiguration(configuration.subset("queues"));

        QueueRegistry queueRegistry = virtualHost.getQueueRegistry();
        MessageStore messageStore = virtualHost.getMessageStore();
        ExchangeRegistry exchangeRegistry = virtualHost.getExchangeRegistry();


        AMQShortString queueName = new AMQShortString(queueNameString);

        AMQQueue queue;

        synchronized (queueRegistry)
        {
            queue = queueRegistry.getQueue(queueName);

            if (queue == null)
            {
                _logger.info("Creating queue '" + queueName + "' on virtual host " + virtualHost.getName());

                boolean durable = queueConfiguration.getBoolean("durable" ,false);
                boolean autodelete = queueConfiguration.getBoolean("autodelete", false);
                String owner = queueConfiguration.getString("owner", null);
                FieldTable arguments = null;
                boolean priority = queueConfiguration.getBoolean("priority", false);
                int priorities = queueConfiguration.getInt("priorities", -1);
                if(priority || priorities > 0)
                {
                    if(arguments == null)
                    {
                        arguments = new FieldTable();
                    }
                    if (priorities < 0)
                    {
                        priorities = 10;
                    }
                    arguments.put(new AMQShortString("x-qpid-priorities"), priorities);
                }


                queue = AMQQueueFactory.createAMQQueueImpl(queueName,
                        durable,
                        owner == null ? null : new AMQShortString(owner) /* These queues will have no owner */,
                        autodelete /* Therefore autodelete makes no sence */, 
                        virtualHost, 
                        arguments,
                        queueConfiguration);

                if (queue.isDurable())
                {
                    messageStore.createQueue(queue);
                }

                queueRegistry.registerQueue(queue);
            }
            else
            {
                _logger.info("Queue '" + queueNameString + "' already exists on virtual host "+virtualHost.getName()+", not creating.");
            }

            String exchangeName = queueConfiguration.getString("exchange", null);

            Exchange exchange = exchangeRegistry.getExchange(exchangeName == null ? null : new AMQShortString(exchangeName));

            if(exchange == null)
            {
                exchange = virtualHost.getExchangeRegistry().getDefaultExchange();
            }

            if (exchange == null)
            {
                throw new ConfigurationException("Attempt to bind queue to unknown exchange:" + exchangeName);
            }

            synchronized (exchange)
            {
                List routingKeys = queueConfiguration.getList("routingKey");
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

                if(exchange != virtualHost.getExchangeRegistry().getDefaultExchange())
                {
                    queue.bind(virtualHost.getExchangeRegistry().getDefaultExchange(), queue.getName(), null);
                }
            }

        }
    }


    public void performBindings() throws AMQException, ConfigurationException
    {
        List virtualHostNames = _config.getList(VIRTUALHOST_PROPERTY_BASE + "name");
        String defaultVirtualHostName = _config.getString("default");
        if(defaultVirtualHostName != null)
        {
            ApplicationRegistry.getInstance().getVirtualHostRegistry().setDefaultVirtualHostName(defaultVirtualHostName);            
        }
        _logger.info("Configuring " + virtualHostNames == null ? 0 : virtualHostNames.size() + " virtual hosts: " + virtualHostNames);

        for(Object nameObject : virtualHostNames)
        {
            String name = String.valueOf(nameObject);
            configureVirtualHost(name, _config.subset(VIRTUALHOST_PROPERTY_BASE + name));
        }

        if (virtualHostNames == null || virtualHostNames.isEmpty())
        {
            throw new ConfigurationException(
                    "Virtualhost Configuration document does not contain a valid virtualhost.");
        }
    }



}
