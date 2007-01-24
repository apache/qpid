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

import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.log4j.Logger;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;


import java.util.Collection;

public class VirtualHostConfiguration
{
    private static final Logger _logger = Logger.getLogger(VirtualHostConfiguration.class);

    XMLConfiguration _config;

    private static final String XML_VIRTUALHOST = "virtualhost";
    private static final String XML_PATH = "path";
    private static final String XML_BIND = "bind";
    private static final String XML_VIRTUALHOST_PATH = "virtualhost.path";
    private static final String XML_VIRTUALHOST_BIND = "virtualhost.bind";


    public VirtualHostConfiguration(String configFile) throws ConfigurationException
    {
        _logger.info("Loading Config file:" + configFile);

        _config = new XMLConfiguration(configFile);

        if (_config.getProperty(XML_VIRTUALHOST_PATH) == null)
        {
            throw new ConfigurationException(
                    "Virtualhost Configuration document does not contain a valid virtualhost.");
        }
    }

    public void performBindings() throws AMQException, ConfigurationException, URLSyntaxException
    {
        Object prop = _config.getProperty(XML_VIRTUALHOST_PATH);

        if (prop instanceof Collection)
        {
            _logger.debug("Number of VirtualHosts: " + ((Collection) prop).size());

            int virtualhosts = ((Collection) prop).size();
            for (int vhost = 0; vhost < virtualhosts; vhost++)
            {
                loadVirtualHost(vhost);
            }
        }
        else
        {
            loadVirtualHost(-1);
        }
    }

    private void loadVirtualHost(int index) throws AMQException, ConfigurationException, URLSyntaxException
    {
        String path = XML_VIRTUALHOST;

        if (index != -1)
        {
            path = path + "(" + index + ")";
        }

        Object prop = _config.getProperty(path + "." + XML_PATH);

        if (prop == null)
        {
            prop = _config.getProperty(path + "." + XML_BIND);
            String error = "Virtual Host not defined for binding";

            if (prop != null)
            {
                if (prop instanceof Collection)
                {
                    error += "s";
                }

                error += ": " + prop;
            }

            throw new ConfigurationException(error);
        }

        _logger.info("VirtualHost:'" + prop + "'");
        String virtualHost = prop.toString();

        prop = _config.getProperty(path + "." + XML_BIND);
        if (prop instanceof Collection)
        {
            int bindings = ((Collection) prop).size();
            _logger.debug("Number of Bindings: " + bindings);
            for (int dest = 0; dest < bindings; dest++)
            {
                loadBinding(virtualHost, path, dest);
            }
        }
        else
        {
            loadBinding(virtualHost,path, -1);
        }
    }

    private void loadBinding(String virtualHost, String rootpath, int index) throws AMQException, ConfigurationException, URLSyntaxException
    {
        String path = rootpath + "." + XML_BIND;
        if (index != -1)
        {
            path = path + "(" + index + ")";
        }

        String bindingString = _config.getString(path);

        AMQBindingURL binding = new AMQBindingURL(bindingString);

        _logger.debug("Loaded Binding:" + binding);

        try
        {
            bind(virtualHost, binding);
        }
        catch (AMQException amqe)
        {
            _logger.info("Unable to bind url: " + binding);
            throw amqe;
        }
    }

    private void bind(String virtualHostName, AMQBindingURL binding) throws AMQException, ConfigurationException
    {

        AMQShortString queueName = binding.getQueueName();

        // This will occur if the URL is a Topic
        if (queueName == null)
        {
            //todo register valid topic
            ///queueName = binding.getDestinationName();
            throw new AMQException("Topics cannot be bound. TODO Register valid topic");
        }

        //Get references to Broker Registries
        VirtualHost virtualHost = ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost(virtualHostName);
        QueueRegistry queueRegistry = virtualHost.getQueueRegistry();
        MessageStore messageStore = virtualHost.getMessageStore();
        ExchangeRegistry exchangeRegistry = virtualHost.getExchangeRegistry();

        synchronized (queueRegistry)
        {
            AMQQueue queue = queueRegistry.getQueue(queueName);

            if (queue == null)
            {
                _logger.info("Queue '" + binding.getQueueName() + "' does not exists. Creating.");

                queue = new AMQQueue(queueName,
                        Boolean.parseBoolean(binding.getOption(AMQBindingURL.OPTION_DURABLE)),
                        null /* These queues will have no owner */,
                        false /* Therefore autodelete makes no sence */, virtualHost);

                if (queue.isDurable())
                {
                    messageStore.createQueue(queue);
                }

                queueRegistry.registerQueue(queue);
            }
            else
            {
                _logger.info("Queue '" + binding.getQueueName() + "' already exists not creating.");
            }

            Exchange defaultExchange = exchangeRegistry.getExchange(binding.getExchangeName());
            synchronized (defaultExchange)
            {
                if (defaultExchange == null)
                {
                    throw new ConfigurationException("Attempt to bind queue to unknown exchange:" + binding);
                }

                defaultExchange.registerQueue(queue.getName(), queue, null);

                if (binding.getRoutingKey() == null || binding.getRoutingKey().equals(""))
                {
                    throw new ConfigurationException("Unknown binding not specified on url:" + binding);
                }

                queue.bind(binding.getRoutingKey(), defaultExchange);
            }
            _logger.info("Queue '" + queue.getName() + "' bound to exchange:" + binding.getExchangeName() + " RK:'" + binding.getRoutingKey() + "'");
        }
    }
}
