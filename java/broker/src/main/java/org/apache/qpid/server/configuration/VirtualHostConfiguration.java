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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
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
        //Currently doesn't do validation
    }

    public int getHouseKeepingThreadCount()
    {
        return getIntValue("housekeeping.poolSize", Runtime.getRuntime().availableProcessors());
    }
}
