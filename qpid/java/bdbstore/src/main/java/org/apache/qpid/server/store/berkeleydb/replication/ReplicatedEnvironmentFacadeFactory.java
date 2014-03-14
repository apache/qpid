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
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.berkeleydb.EnvironmentFacade;
import org.apache.qpid.server.store.berkeleydb.EnvironmentFacadeFactory;

import com.sleepycat.je.Durability;
import com.sleepycat.je.Durability.ReplicaAckPolicy;
import com.sleepycat.je.Durability.SyncPolicy;

public class ReplicatedEnvironmentFacadeFactory implements EnvironmentFacadeFactory
{
    public static final String DURABILITY = "haDurability";
    public static final String GROUP_NAME = "haGroupName";
    public static final String HELPER_ADDRESS = "haHelperAddress";
    public static final String NODE_ADDRESS = "haNodeAddress";
    public static final String NODE_NAME = "haNodeName";
    public static final String REPLICATION_CONFIG = "haReplicationConfig";
    public static final String COALESCING_SYNC = "haCoalescingSync";
    public static final String DESIGNATED_PRIMARY = "haDesignatedPrimary";

    private static final int DEFAULT_NODE_PRIORITY = 1;
    private static final Durability DEFAULT_DURABILITY = new Durability(SyncPolicy.NO_SYNC, SyncPolicy.NO_SYNC,
            ReplicaAckPolicy.SIMPLE_MAJORITY);
    private static final boolean DEFAULT_COALESCING_SYNC = true;

    @Override
    public EnvironmentFacade createEnvironmentFacade(VirtualHost<?> virtualHost, boolean isMessageStore)
    {
        final Map<String, Object> messageStoreSettings = virtualHost.getMessageStoreSettings();
        ReplicatedEnvironmentConfiguration configuration = new ReplicatedEnvironmentConfiguration()
        {
            @Override
            public boolean isDesignatedPrimary()
            {
                return convertBoolean(messageStoreSettings.get(DESIGNATED_PRIMARY), false);
            }

            @Override
            public boolean isCoalescingSync()
            {
                return convertBoolean(messageStoreSettings.get(COALESCING_SYNC), DEFAULT_COALESCING_SYNC);
            }

            @Override
            public String getStorePath()
            {
                return (String) messageStoreSettings.get(MessageStore.STORE_PATH);
            }

            @SuppressWarnings("unchecked")
            @Override
            public Map<String, String> getParameters()
            {
                return (Map<String, String>) messageStoreSettings.get(EnvironmentFacadeFactory.ENVIRONMENT_CONFIGURATION);
            }

            @SuppressWarnings("unchecked")
            @Override
            public Map<String, String> getReplicationParameters()
            {
                return (Map<String, String>) messageStoreSettings.get(REPLICATION_CONFIG);
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
                return (String)messageStoreSettings.get(NODE_NAME);
            }

            @Override
            public String getHostPort()
            {
                return (String)messageStoreSettings.get(NODE_ADDRESS);
            }

            @Override
            public String getHelperHostPort()
            {
                return (String)messageStoreSettings.get(HELPER_ADDRESS);
            }

            @Override
            public String getGroupName()
            {
                return (String)messageStoreSettings.get(GROUP_NAME);
            }

            @Override
            public String getDurability()
            {
                String durability = (String)messageStoreSettings.get(DURABILITY);
                return durability == null ? DEFAULT_DURABILITY.toString() : durability;
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
