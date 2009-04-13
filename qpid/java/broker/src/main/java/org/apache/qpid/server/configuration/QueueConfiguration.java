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

import org.apache.commons.configuration.Configuration;

public class QueueConfiguration
{

    // FIXME AIDAN XXX -- deal with defaults
    
    private Configuration _config;
    private String _name;

    public QueueConfiguration(String name, Configuration config)
    {
        _config = config;
        _name = name;
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

    public long getMaximumMessageAge()
    {
        return _config.getLong("maximumMessageAge", 0);
    }

    public long getMaximumQueueDepth()
    {
        return _config.getLong("maximumQueueDepth", 0);
    }

    public long getMaximumMessageSize()
    {
        return _config.getLong("maximumMessageSize", 0);
    }

    public long getMaximumMessageCount()
    {
        return _config.getLong("maximumMessageCount", 0);
    }

    public long getMinimumAlertRepeatGap()
    {
        return _config.getLong("minimumAlertRepeatGap", 0);
    }

}
