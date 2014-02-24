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
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.configuration.plugins.AbstractConfiguration;

import java.util.List;

public class QueueConfiguration extends AbstractConfiguration
{
    private String _name;
    private VirtualHostConfiguration _vHostConfig;

    public QueueConfiguration(String name, VirtualHostConfiguration virtualHostConfiguration) throws ConfigurationException
    {
        _vHostConfig = virtualHostConfiguration;
        _name = name;

        CompositeConfiguration mungedConf = new CompositeConfiguration();
        mungedConf.addConfiguration(_vHostConfig.getConfig().subset("queues.queue." + escapeTagName(name)));

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
                            "lvqKey",
                            "sortKey",
                            "maximumDeliveryCount",
                            "deadLetterQueues",
                            "argument"
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

    private boolean getDefaultedBoolean(String attribute)
    {
        final Configuration config = _vHostConfig.getConfig();
        if(config.containsKey("queues."+attribute))
        {
            final boolean defaultValue = config.getBoolean("queues." + attribute);
            return getBooleanValue(attribute, defaultValue);
        }
        else
        {
            return getBooleanValue(attribute);
        }
    }

    public boolean getDurable()
    {
        return getDefaultedBoolean("durable");
    }

    public boolean getExclusive()
    {
        return getDefaultedBoolean("exclusive");
    }

    public boolean getAutoDelete()
    {
        return getDefaultedBoolean("autodelete");
    }

    public String getOwner()
    {
        return getStringValue("owner", null);
    }

    public boolean getPriority()
    {
        return getDefaultedBoolean("priority");
    }

    public int getPriorities()
    {
        final Configuration config = _vHostConfig.getConfig();

        int defaultValue;
        if(config.containsKey("queues.priorities"))
        {
            defaultValue = config.getInt("queues.priorities");
        }
        else
        {
            defaultValue = -1;
        }
        return getIntValue("priorities", defaultValue);
    }

    public String getExchange()
    {
        final Configuration config = _vHostConfig.getConfig();

        String defaultValue;

        if(config.containsKey("queues.exchange"))
        {
            defaultValue = config.getString("queues.exchange");
        }
        else
        {
            defaultValue = "";
        }

        return getStringValue("exchange", defaultValue);
    }

    public List getRoutingKeys()
    {
        return getListValue("routingKey");
    }

    public String getName()
    {
        return _name;
    }

    public String getDescription()
    {
        return getStringValue("description");
    }

    public int getMaximumMessageAge()
    {
        return getIntValue("maximumMessageAge");
    }

    public long getMaximumQueueDepth()
    {
        return getLongValue("maximumQueueDepth");
    }

    public long getMaximumMessageSize()
    {
        return getLongValue("maximumMessageSize");
    }

    public long getMaximumMessageCount()
    {
        return getLongValue("maximumMessageCount");
    }

    public long getMinimumAlertRepeatGap()
    {
        return getLongValue("minimumAlertRepeatGap");
    }

    public long getCapacity()
    {
        return getLongValue("capacity");
    }

    public long getFlowResumeCapacity()
    {
        return getLongValue("flowResumeCapacity");
    }

    public boolean isLVQ()
    {
        return getBooleanValue("lvq");
    }

    public String getLVQKey()
    {
        return getStringValue("lvqKey", null);
    }

    public String getQueueSortKey()
    {
        return getStringValue("sortKey", null);
    }

    public int getMaxDeliveryCount()
    {
        return getIntValue("maximumDeliveryCount", _vHostConfig.getMaxDeliveryCount());
    }

    /**
     * Check if dead letter queue delivery is enabled, deferring to the virtualhost configuration if not set.
     */
    public boolean isDeadLetterQueueEnabled()
    {
        return getBooleanValue("deadLetterQueues", _vHostConfig.isDeadLetterQueueEnabled());
    }

    public Map<String,String> getArguments()
    {
        return getMap("argument");
    }

    public Map<String,String> getBindingArguments(String routingKey)
    {

        return getConfig().containsKey(routingKey+".bindingArgument") ? getMap(routingKey+".bindingArgument") : null;
    }
}
