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
package org.apache.qpid.server.util;

import org.apache.commons.configuration.MapConfiguration;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.management.NoopManagedObjectRegistry;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.access.ACLManager;
import org.apache.qpid.server.security.access.ACLPlugin;
import org.apache.qpid.server.security.access.plugins.AllowAll;
import org.apache.qpid.server.security.auth.database.PropertiesPrincipalDatabaseManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Properties;
import java.util.Arrays;

public class TestApplicationRegistry extends ApplicationRegistry
{
    private QueueRegistry _queueRegistry;

    private ExchangeRegistry _exchangeRegistry;

    private ExchangeFactory _exchangeFactory;

    private MessageStore _messageStore;

    private VirtualHost _vHost;


    public TestApplicationRegistry()
    {
        super(new MapConfiguration(new HashMap()));
    }

    public void initialise() throws Exception
    {
        Properties users = new Properties();

        users.put("guest", "guest");

        _databaseManager = new PropertiesPrincipalDatabaseManager("default", users);

        _accessManager = new ACLManager(_configuration, _pluginManager, AllowAll.FACTORY);

        _authenticationManager = new PrincipalDatabaseAuthenticationManager(null, null);

        _managedObjectRegistry = new NoopManagedObjectRegistry();

        _messageStore = new TestableMemoryMessageStore();

        _virtualHostRegistry = new VirtualHostRegistry();

        _vHost = new VirtualHost("test", _messageStore);

        _virtualHostRegistry.registerVirtualHost(_vHost);

        _queueRegistry = _vHost.getQueueRegistry();
        _exchangeFactory = _vHost.getExchangeFactory();
        _exchangeRegistry = _vHost.getExchangeRegistry();

        _configuration.addProperty("heartbeat.delay", 10 * 60); // 10 minutes
    }

    public QueueRegistry getQueueRegistry()
    {
        return _queueRegistry;
    }

    public ExchangeRegistry getExchangeRegistry()
    {
        return _exchangeRegistry;
    }

    public ExchangeFactory getExchangeFactory()
    {
        return _exchangeFactory;
    }

    public Collection<String> getVirtualHostNames()
    {
        String[] hosts = {"test"};
        return Arrays.asList(hosts);
    }

    public void setAccessManager(ACLManager newManager)
    {
        _accessManager = newManager;
    }

    public MessageStore getMessageStore()
    {
        return _messageStore;
    }

}


