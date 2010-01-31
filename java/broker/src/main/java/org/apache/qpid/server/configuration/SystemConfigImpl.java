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

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class SystemConfigImpl implements SystemConfig
{
    private static final String OS_NAME = System.getProperty("os.name");
    private static final String OS_ARCH = System.getProperty("os.arch");
    private static final String OS_VERSION = System.getProperty("os.version");

    private final UUID _id;
    private String _name;

    private final String _host;

    private final Map<UUID, BrokerConfig> _brokers = new ConcurrentHashMap<UUID, BrokerConfig>();

    private final long _createTime = System.currentTimeMillis();
    private final ConfigStore _store;

    public SystemConfigImpl(ConfigStore store)
    {
        this(store.createId(), store);
    }

    public SystemConfigImpl(UUID id, ConfigStore store)
    {
        _id = id;
        _store = store;
        String host;
        try
        {
            InetAddress addr = InetAddress.getLocalHost();
            host = addr.getHostName();
        }
        catch (UnknownHostException e)
        {
            host="localhost";
        }
        _host = host;
    }

    public String getName()
    {
        return _name;
    }

    public String getOperatingSystemName()
    {
        return OS_NAME;
    }

    public String getNodeName()
    {
        return _host;
    }

    public String getOSRelease()
    {
        return OS_VERSION;
    }

    public String getOSVersion()
    {
        return "";
    }

    public String getOSArchitecture()
    {
        return OS_ARCH;
    }

    public UUID getId()
    {
        return _id;
    }

    public SystemConfigType getConfigType()
    {
        return SystemConfigType.getInstance();
    }

    public ConfiguredObject getParent()
    {
        return null;
    }

    public boolean isDurable()
    {
        return false;
    }

    public void addBroker(final BrokerConfig broker)
    {
        broker.setSystem(this);
        _store.addConfiguredObject(broker);
        _brokers.put(broker.getId(), broker);
    }

    public void removeBroker(final BrokerConfig broker)
    {
        _brokers.remove(broker.getId());
        _store.removeConfiguredObject(broker);
    }

    public long getCreateTime()
    {
        return _createTime;
    }

}
