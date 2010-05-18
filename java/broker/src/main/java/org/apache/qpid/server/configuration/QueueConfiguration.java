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
import java.util.Map;
import java.util.HashMap;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;

public class QueueConfiguration extends ConfigurationPlugin
{

    private Configuration _config;
    private String _name;
    private VirtualHostConfiguration _vHostConfig;

    public QueueConfiguration(String name, VirtualHostConfiguration virtualHostConfiguration) throws ConfigurationException
    {
        _vHostConfig = virtualHostConfiguration;
        _name = name;

        CompositeConfiguration mungedConf = new CompositeConfiguration();
        mungedConf.addConfiguration(_vHostConfig.getConfig().subset("queues.queue." + name));
        mungedConf.addConfiguration(_vHostConfig.getConfig().subset("queues"));
        _config = mungedConf;

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

    public VirtualHostConfiguration getVirtualHostConfiguration()
    {
        return _vHostConfig;
    }

    public boolean getDurable()
    {
        return _config.getBoolean("durable" ,false);
    }

    public boolean getAutoDelete()
    {
        return _config.getBoolean("autodelete", false);
    }

    public String getOwner()
    {
        return _config.getString("owner", null);
    }

    public boolean getPriority()
    {
        return _config.getBoolean("priority", false);
    }

    public int getPriorities()
    {
        return _config.getInt("priorities", -1);
    }

    public String getExchange()
    {
        return _config.getString("exchange", null);
    }

    public List getRoutingKeys()
    {
        return _config.getList("routingKey");
    }

    public String getName()
    {
        return _name;
    }

    public int getMaximumMessageAge()
    {
        return _config.getInt("maximumMessageAge", _vHostConfig.getMaximumMessageAge());
    }

    public long getMaximumQueueDepth()
    {
        return _config.getLong("maximumQueueDepth", _vHostConfig.getMaximumQueueDepth());
    }

    public long getMaximumMessageSize()
    {
        return _config.getLong("maximumMessageSize", _vHostConfig.getMaximumMessageSize());
    }

    public long getMaximumMessageCount()
    {
        return _config.getLong("maximumMessageCount", _vHostConfig.getMaximumMessageCount());
    }

    public long getMinimumAlertRepeatGap()
    {
        return _config.getLong("minimumAlertRepeatGap", _vHostConfig.getMinimumAlertRepeatGap());
    }

    public long getCapacity()
    {
        return _config.getLong("capacity", _vHostConfig.getCapacity());
    }

    public long getFlowResumeCapacity()
    {
        return _config.getLong("flowResumeCapacity", _vHostConfig.getFlowResumeCapacity());
    }

    public boolean isLVQ()
    {
        return _config.getBoolean("lvq", false);
    }

    public String getLVQKey()
    {
        return _config.getString("lvqKey", null);
    }
}
