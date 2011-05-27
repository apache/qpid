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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.MemoryMessageStore;

public class VirtualHostConfiguration extends ConfigurationPlugin
{
    private String _name;
    private Map<String, QueueConfiguration> _queues = new HashMap<String, QueueConfiguration>();
    private Map<String, ExchangeConfiguration> _exchanges = new HashMap<String, ExchangeConfiguration>();

    public VirtualHostConfiguration(String name, Configuration config) throws ConfigurationException
    {
        _name = name;
        setConfiguration(config);
    }

    /**
     * Apply the given configuration to this VirtualHostConfiguration
     *
     * @param config the config to apply
     * @throws ConfigurationException if a problem occurs with configuration
     */
    public void setConfiguration(Configuration config) throws ConfigurationException
    {
        setConfiguration("virtualhosts.virtualhost", config);

        Iterator i = getListValue("queues.queue.name").iterator();

        while (i.hasNext())
        {
            String queueName = (String) i.next();
            _queues.put(queueName, new QueueConfiguration(queueName, this));
        }

        i = getListValue("exchanges.exchange.name").iterator();
        int count = 0;
        while (i.hasNext())
        {
            CompositeConfiguration mungedConf = new CompositeConfiguration();
            mungedConf.addConfiguration(config.subset("exchanges.exchange(" + count++ + ")"));
            mungedConf.addConfiguration(_configuration.subset("exchanges"));
            String exchName = (String) i.next();
            _exchanges.put(exchName, new ExchangeConfiguration(exchName, mungedConf));
        }
    }

    public String getName()
    {
        return _name;
    }

    public long getHousekeepingExpiredMessageCheckPeriod()
    {
        return getLongValue("housekeeping.expiredMessageCheckPeriod", ApplicationRegistry.getInstance().getConfiguration().getHousekeepingCheckPeriod());
    }

    public String getAuthenticationDatabase()
    {
        return getStringValue("security.authentication.name");
    }

    public List getCustomExchanges()
    {
        return getListValue("custom-exchanges.class-name");
    }

    public Configuration getStoreConfiguration()
    {
        return _configuration.subset("store");
    }

    public String getMessageStoreClass()
    {
        return getStringValue("store.class", MemoryMessageStore.class.getName());
    }

    public void setMessageStoreClass(String storeClass)
    {
        _configuration.setProperty("store.class", storeClass);
    }

    public List getExchanges()
    {
        return getListValue("exchanges.exchange.name");
    }

    public String[] getQueueNames()
    {
        return _queues.keySet().toArray(new String[_queues.size()]);
    }

    public ExchangeConfiguration getExchangeConfiguration(String exchangeName)
    {
        return _exchanges.get(exchangeName);
    }

    public QueueConfiguration getQueueConfiguration(String queueName)
    {
        // We might be asked for the config for a queue we don't know about,
        // such as one that's been dynamically created. Those get the defaults by default.
        if (_queues.containsKey(queueName))
        {
            return _queues.get(queueName);
        }
        else
        {
            try
            {
                return new QueueConfiguration(queueName, this);
            }
            catch (ConfigurationException e)
            {
                // The configuration is empty so there can't be an error.
                return null;
            }
        }
    }

    public ConfigurationPlugin getQueueConfiguration(AMQQueue queue)
    {
        VirtualHostConfiguration hostConfig = queue.getVirtualHost().getConfiguration();

        // First check if we have a named queue configuration (the easy case)
        if (Arrays.asList(hostConfig.getQueueNames()).contains(queue.getName()))
        {
            return null;
        }

        // We don't have an explicit queue config we must find out what we need.
        ArrayList<Binding> bindings = new ArrayList<Binding>(queue.getBindings());

        List<AMQShortString> exchangeClasses = new ArrayList<AMQShortString>(bindings.size());

        //Remove default exchange
        for (int index = 0; index < bindings.size(); index++)
        {
            // Ignore the DEFAULT Exchange binding
            if (bindings.get(index).getExchange().getNameShortString().equals(ExchangeDefaults.DEFAULT_EXCHANGE_NAME))
            {
                bindings.remove(index);
            }
            else
            {
                exchangeClasses.add(bindings.get(index).getExchange().getType().getName());

                if (exchangeClasses.size() > 1)
                {
                    // If we have more than 1 class of exchange then we can only use the global queue configuration.
                    // and this will be returned from the default getQueueConfiguration
                    return null;
                }
            }
        }

        // If we are just bound the the default exchange then use the default.
        if (bindings.isEmpty())
        {
            return null;
        }

        // If we are bound to only one type of exchange then we are going
        // to have to resolve the configuration for that exchange.

        String exchangeName = bindings.get(0).getExchange().getType().getName().toString();

        // Lookup a Configuration handler for this Exchange.

        // Build the expected class name. <Exchangename>sConfiguration
        // i.e. TopicConfiguration or HeadersConfiguration
        String exchangeClass = "org.apache.qpid.server.configuration."
                               + exchangeName.substring(0, 1).toUpperCase()
                               + exchangeName.substring(1) + "Configuration";

        ExchangeConfigurationPlugin exchangeConfiguration
                = (ExchangeConfigurationPlugin) queue.getVirtualHost().getConfiguration().getConfiguration(exchangeClass);

        // now need to perform the queue-topic-topics-queues magic.
        // So make a new ConfigurationObject that will hold all the configuration for this queue.
        ConfigurationPlugin queueConfig = new QueueConfiguration.QueueConfig();

        // Initialise the queue with any Global values we may have
        PropertiesConfiguration newQueueConfig = new PropertiesConfiguration();
        newQueueConfig.setProperty("name", queue.getName());

        try
        {
            //Set the queue name
            CompositeConfiguration mungedConf = new CompositeConfiguration();
            //Set the queue name
            mungedConf.addConfiguration(newQueueConfig);
            //Set the global queue configuration
            mungedConf.addConfiguration(getConfig().subset("queues"));

            // Set configuration
            queueConfig.setConfiguration("virtualhosts.virtualhost.queues", mungedConf);
        }
        catch (ConfigurationException e)
        {
            // This will not occur as queues only require a name.
            _logger.error("QueueConfiguration requirements have changed.");
        }

        // Merge any configuration the Exchange wishes to apply        
        if (exchangeConfiguration != null)
        {
            queueConfig.addConfiguration(exchangeConfiguration.getConfiguration(queue));
        }

        //Finally merge in any specific queue configuration we have.
        if (_queues.containsKey(queue.getName()))
        {
            queueConfig.addConfiguration(_queues.get(queue.getName()));
        }

        return queueConfig;
    }

    public long getMemoryUsageMaximum()
    {
        return getLongValue("queues.maximumMemoryUsage");
    }

    public long getMemoryUsageMinimum()
    {
        return getLongValue("queues.minimumMemoryUsage");
    }

    public int getMaximumMessageAge()
    {
        return getIntValue("queues.maximumMessageAge");
    }

    public Long getMaximumQueueDepth()
    {
        return getLongValue("queues.maximumQueueDepth");
    }

    public Long getMaximumMessageSize()
    {
        return getLongValue("queues.maximumMessageSize");
    }

    public Long getMaximumMessageCount()
    {
        return getLongValue("queues.maximumMessageCount");
    }

    public Long getMinimumAlertRepeatGap()
    {
        return getLongValue("queues.minimumAlertRepeatGap");
    }

    public long getCapacity()
    {
        return getLongValue("queues.capacity");
    }

    public long getFlowResumeCapacity()
    {
        return getLongValue("queues.flowResumeCapacity", getCapacity());
    }

    public String[] getElementsProcessed()
    {
        return new String[]{"queues", "exchanges", "custom-exchanges", "store", "housekeeping"};

    }

    @Override
    public void validateConfiguration() throws ConfigurationException
    {
        // QPID-3249.  Support for specifying authentication name at vhost level is no longer supported.
        if (getListValue("security.authentication.name").size() > 0)
        {
            String message = "Validation error : security/authentication/name is no longer a supported element within the configuration xml."
                    + " It appears in virtual host definition : " + _name;
            throw new ConfigurationException(message);
        }
    }

    public int getHouseKeepingThreadCount()
    {
        return getIntValue("housekeeping.poolSize", Runtime.getRuntime().availableProcessors());
    }

    public long getTransactionTimeoutOpenWarn()
    {
        return getLongValue("transactionTimeout.openWarn", 0L);
    }

    public long getTransactionTimeoutOpenClose()
    {
        return getLongValue("transactionTimeout.openClose", 0L);
    }

    public long getTransactionTimeoutIdleWarn()
    {
        return getLongValue("transactionTimeout.idleWarn", 0L);
    }

    public long getTransactionTimeoutIdleClose()
    {
        return getLongValue("transactionTimeout.idleClose", 0L);
    }
}
