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
package org.apache.qpid.server.store.berkeleydb;

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.VirtualHost;

import com.sleepycat.je.Durability;
import com.sleepycat.je.Durability.ReplicaAckPolicy;
import com.sleepycat.je.Durability.SyncPolicy;

public class ReplicatedEnvironmentFacadeFactory implements EnvironmentFacadeFactory
{

    private static final Durability DEFAULT_DURABILITY = new Durability(SyncPolicy.NO_SYNC, SyncPolicy.NO_SYNC,
            ReplicaAckPolicy.SIMPLE_MAJORITY);

    @SuppressWarnings("unchecked")
    @Override
    public EnvironmentFacade createEnvironmentFacade(String name, String storeLocation, VirtualHost virtualHost)
    {
        // Mandatory configuration
        String groupName = getValidatedStringAttribute(virtualHost, "haGroupName");
        String nodeName = getValidatedStringAttribute(virtualHost, "haNodeName");
        String nodeHostPort = getValidatedStringAttribute(virtualHost, "haNodeAddress");
        String helperHostPort = getValidatedStringAttribute(virtualHost, "haHelperAddress");

        // Optional configuration
        Durability durability = null;
        String durabilitySetting = getStringAttribute(virtualHost, "haDurability", null);
        if (durabilitySetting == null)
        {
            durability = DEFAULT_DURABILITY;
        }
        else
        {
            durability = Durability.parse(durabilitySetting);
        }
        Boolean designatedPrimary = getBooleanAttribute(virtualHost, "haDesignatedPrimary", Boolean.FALSE);
        Boolean coalescingSync = getBooleanAttribute(virtualHost, "haCoalescingSync", Boolean.TRUE);

        Map<String, String> replicationConfig = null;
        Object repConfigAttr = virtualHost.getAttribute("haReplicationConfig");
        if (repConfigAttr instanceof Map)
        {
            replicationConfig = new HashMap<String, String>((Map<String, String>) repConfigAttr);
        }

        if (coalescingSync && durability.getLocalSync() == SyncPolicy.SYNC)
        {
            throw new IllegalConfigurationException("Coalescing sync cannot be used with master sync policy " + SyncPolicy.SYNC
                    + "! Please set highAvailability.coalescingSync to false in store configuration.");
        }

        Map<String, String> envConfigMap = null;
        Object bdbEnvConfigAttr = virtualHost.getAttribute("bdbEnvironmentConfig");
        if (bdbEnvConfigAttr instanceof Map)
        {
            envConfigMap = new HashMap<String, String>((Map<String, String>) bdbEnvConfigAttr);
        }

        return new ReplicatedEnvironmentFacade(name, storeLocation, groupName, nodeName, nodeHostPort, helperHostPort, durability,
                designatedPrimary, coalescingSync, envConfigMap, replicationConfig);
    }

    private String getValidatedStringAttribute(org.apache.qpid.server.model.VirtualHost virtualHost, String attributeName)
    {
        Object attrValue = virtualHost.getAttribute(attributeName);
        if (attrValue != null)
        {
            return attrValue.toString();
        }
        else
        {
            throw new IllegalConfigurationException("BDB HA configuration key not found. Please specify configuration attribute: "
                    + attributeName);
        }
    }

    private String getStringAttribute(org.apache.qpid.server.model.VirtualHost virtualHost, String attributeName, String defaultVal)
    {
        Object attrValue = virtualHost.getAttribute(attributeName);
        if (attrValue != null)
        {
            return attrValue.toString();
        }
        return defaultVal;
    }

    private boolean getBooleanAttribute(org.apache.qpid.server.model.VirtualHost virtualHost, String attributeName, boolean defaultVal)
    {
        Object attrValue = virtualHost.getAttribute(attributeName);
        if (attrValue != null)
        {
            if (attrValue instanceof Boolean)
            {
                return ((Boolean) attrValue).booleanValue();
            }
            else if (attrValue instanceof String)
            {
                return Boolean.parseBoolean((String) attrValue);
            }

        }
        return defaultVal;
    }

}
