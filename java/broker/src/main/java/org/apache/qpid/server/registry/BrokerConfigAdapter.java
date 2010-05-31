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
package org.apache.qpid.server.registry;

import org.apache.qpid.server.configuration.*;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.common.QpidProperties;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BrokerConfigAdapter implements BrokerConfig
{
    private final IApplicationRegistry _instance;
    private SystemConfig _system;

    private final Map<UUID, VirtualHostConfig> _vhosts = new ConcurrentHashMap<UUID, VirtualHostConfig>();
    private final long _createTime = System.currentTimeMillis();
    private UUID _id;
    private String _federationTag;

    public BrokerConfigAdapter(final IApplicationRegistry instance)
    {
        _instance = instance;
        _id = instance.getConfigStore().createId();
        _federationTag = UUID.randomUUID().toString();
    }

    public void setSystem(final SystemConfig system)
    {
        _system = system;
    }

    public SystemConfig getSystem()
    {
        return _system;
    }

    public Integer getPort()
    {
        List ports = _instance.getConfiguration().getPorts();
        if(ports.size() > 0)
        {
            return Integer.valueOf(ports.get(0).toString());
        }
        else
        {
            return 0;
        }
    }

    public Integer getWorkerThreads()
    {
        return _instance.getConfiguration().getProcessors();
    }

    public Integer getMaxConnections()
    {
        return 0;
    }

    public Integer getConnectionBacklogLimit()
    {
        return 0;
    }

    public Long getStagingThreshold()
    {
        return 0L;
    }

    public Integer getManagementPublishInterval()
    {
        return 10000;
    }

    public String getVersion()
    {
        return QpidProperties.getReleaseVersion() + " [Build: " + QpidProperties.getBuildVersion() +  "]";
    }

    public String getDataDirectory()
    {
        return _instance.getConfiguration().getQpidWork();
    }

    public void addVirtualHost(final VirtualHostConfig virtualHost)
    {
        virtualHost.setBroker(this);
        _vhosts.put(virtualHost.getId(), virtualHost);
        getConfigStore().addConfiguredObject(virtualHost);

    }

    private ConfigStore getConfigStore()
    {
        return _instance.getConfigStore();
    }

    public long getCreateTime()
    {
        return _createTime;
    }

    public void createBrokerConnection(final String transport,
                                       final String host,
                                       final int port,
                                       final boolean durable,
                                       final String authMechanism,
                                       final String username,
                                       final String password)
    {
        VirtualHost vhost = _instance.getVirtualHostRegistry().getDefaultVirtualHost();
        vhost.createBrokerConnection(transport, host, port, "", durable, authMechanism, username, password);
    }

    public UUID getId()
    {
        return _id;
    }

    public BrokerConfigType getConfigType()
    {
        return BrokerConfigType.getInstance();
    }

    public ConfiguredObject getParent()
    {
        return _system;
    }

    public boolean isDurable()
    {
        return false;
    }

    public String getFederationTag()
    {
        return _federationTag;
    }
}
