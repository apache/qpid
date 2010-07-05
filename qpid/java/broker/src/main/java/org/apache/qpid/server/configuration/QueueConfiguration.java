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

import java.util.List;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;

public class QueueConfiguration extends ConfigurationPlugin
{
    private String _name;
    private VirtualHostConfiguration _vHostConfig;

    public QueueConfiguration(String name, VirtualHostConfiguration virtualHostConfiguration) throws ConfigurationException
    {
        _vHostConfig = virtualHostConfiguration;
        _name = name;

        CompositeConfiguration mungedConf = new CompositeConfiguration();
        mungedConf.addConfiguration(_vHostConfig.getConfig().subset("queues.queue." + name));
        mungedConf.addConfiguration(_vHostConfig.getConfig().subset("queues"));

        setConfiguration("virtualhosts.virtualhost.queues.queue", mungedConf);
    }

    public String[] getElementsProcessed()
    {
        return new String[]{"maximumMessageSize",
                            "maximumQueueDepth",
                            "maximumMessageCount",
                            "maximumMessageAge",
                            "minimumAlertRepeatGap",
                            "durable",
                            "exchange",
                            "exclusive",
                            "queue",
                            "autodelete",
                            "priority",
                            "priorities",
                            "routingKey",
                            "capacity",
                            "flowResumeCapacity",
                            "lvq",
                            "lvqKey"
        };
    }

    @Override
    public void validateConfiguration() throws ConfigurationException
    {
        //Currently doesn't do validation
    }

    public VirtualHostConfiguration getVirtualHostConfiguration()
    {
        return _vHostConfig;
    }

    public boolean getDurable()
    {
        return getBooleanValue("durable");
    }
    
    public boolean getExclusive()
    {
        return getBooleanValue("exclusive");
    }

    public boolean getAutoDelete()
    {
        return getBooleanValue("autodelete");
    }

    public String getOwner()
    {
        return getStringValue("owner", null);
    }

    public boolean getPriority()
    {
        return getBooleanValue("priority");
    }

    public int getPriorities()
    {
        return getIntValue("priorities", -1);
    }

    public String getExchange()
    {
        return getStringValue("exchange", ExchangeDefaults.DEFAULT_EXCHANGE_NAME.asString());
    }

    public List getRoutingKeys()
    {
        return getListValue("routingKey");
    }

    public String getName()
    {
        return _name;
    }

    public int getMaximumMessageAge()
    {
        return getIntValue("maximumMessageAge", _vHostConfig.getMaximumMessageAge());
    }

    public long getMaximumQueueDepth()
    {
        return getLongValue("maximumQueueDepth", _vHostConfig.getMaximumQueueDepth());
    }

    public long getMaximumMessageSize()
    {
        return getLongValue("maximumMessageSize", _vHostConfig.getMaximumMessageSize());
    }

    public long getMaximumMessageCount()
    {
        return getLongValue("maximumMessageCount", _vHostConfig.getMaximumMessageCount());
    }

    public long getMinimumAlertRepeatGap()
    {
        return getLongValue("minimumAlertRepeatGap", _vHostConfig.getMinimumAlertRepeatGap());
    }

    public long getCapacity()
    {
        return getLongValue("capacity", _vHostConfig.getCapacity());
    }

    public long getFlowResumeCapacity()
    {
        return getLongValue("flowResumeCapacity", _vHostConfig.getFlowResumeCapacity());
    }

    public boolean isLVQ()
    {
        return getBooleanValue("lvq");
    }

    public String getLVQKey()
    {
        return getStringValue("lvqKey", null);
    }


    public static class QueueConfig extends ConfigurationPlugin
    {
        @Override
        public String[] getElementsProcessed()
        {
            return new String[]{"name"};
        }

        public String getName()
        {
            return getStringValue("name");
        }


        public void validateConfiguration() throws ConfigurationException
        {
            if (_configuration.isEmpty())
            {
                throw new ConfigurationException("Queue section cannot be empty.");
            }

            if (getName() == null)
            {
                throw new ConfigurationException("Queue section must have a 'name' element.");
            }

        }


        @Override
        public String formatToString()
        {
            return "Name:"+getName();
        }
          

    }
}
