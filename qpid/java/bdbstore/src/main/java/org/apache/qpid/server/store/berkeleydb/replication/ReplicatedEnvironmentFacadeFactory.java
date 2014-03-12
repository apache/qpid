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
package org.apache.qpid.server.store.berkeleydb.replication;

import java.util.Map;

import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.berkeleydb.EnvironmentFacade;
import org.apache.qpid.server.store.berkeleydb.EnvironmentFacadeFactory;

import com.sleepycat.je.Durability;
import com.sleepycat.je.Durability.ReplicaAckPolicy;
import com.sleepycat.je.Durability.SyncPolicy;

public class ReplicatedEnvironmentFacadeFactory implements EnvironmentFacadeFactory
{
    
    private static final int DEFAULT_NODE_PRIORITY = 1;
    private static final Durability DEFAULT_DURABILITY = new Durability(SyncPolicy.NO_SYNC, SyncPolicy.NO_SYNC,
            ReplicaAckPolicy.SIMPLE_MAJORITY);
    private static final boolean DEFAULT_COALESCING_SYNC = true;

    

    @Override
    public EnvironmentFacade createEnvironmentFacade(final VirtualHost virtualHost, boolean isMessageStore)
    {
        ReplicatedEnvironmentConfiguration configuration = new ReplicatedEnvironmentConfiguration()
        {
            @Override
            public boolean isDesignatedPrimary()
            {
                return convertBoolean(virtualHost.getAttribute("haDesignatedPrimary"), false);
            }

            @Override
            public boolean isCoalescingSync()
            {
                return convertBoolean(virtualHost.getAttribute("haCoalescingSync"), DEFAULT_COALESCING_SYNC);
            }

            @Override
            public String getStorePath()
            {
                return (String) virtualHost.getAttribute(VirtualHost.STORE_PATH);
            }

            @Override
            public Map<String, String> getParameters()
            {
                return (Map<String, String>) virtualHost.getAttribute("bdbEnvironmentConfig");
            }

            @Override
            public Map<String, String> getReplicationParameters()
            {
                return (Map<String, String>) virtualHost.getAttribute("haReplicationConfig");
            }

            @Override
            public int getQuorumOverride()
            {
                return 0;
            }

            @Override
            public int getPriority()
            {
                return DEFAULT_NODE_PRIORITY;
            }



            @Override
            public String getName()
            {
                return (String)virtualHost.getAttribute("haNodeName");
            }

            @Override
            public String getHostPort()
            {
                return (String)virtualHost.getAttribute("haNodeAddress");
            }

            @Override
            public String getHelperHostPort()
            {
                return (String)virtualHost.getAttribute("haHelperAddress");
            }

            @Override
            public String getGroupName()
            {
                return (String)virtualHost.getAttribute("haGroupName");
            }

            @Override
            public String getDurability()
            {
                return virtualHost.getAttribute("haDurability") == null ? DEFAULT_DURABILITY.toString() : (String)virtualHost.getAttribute("haDurability");
            }
        };
        return new ReplicatedEnvironmentFacade(configuration);

    }

    @Override
    public String getType()
    {
        return ReplicatedEnvironmentFacade.TYPE;
    }

    private boolean convertBoolean(final Object value, boolean defaultValue)
    {
        if(value instanceof Boolean)
        {
            return (Boolean) value;
        }
        else if(value instanceof String)
        {
            return Boolean.valueOf((String) value);
        }
        else if(value == null)
        {
            return defaultValue;
        }
        else
        {
            throw new IllegalArgumentException("Cannot convert type " + value.getClass() + " to a Boolean");
        }
    }

}
