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

import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.exchange.DefaultExchangeRegistry;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.management.ManagedObjectRegistry;
import org.apache.qpid.server.management.NoopManagedObjectRegistry;
import org.apache.qpid.server.queue.DefaultQueueRegistry;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.security.auth.AuthenticationManager;
import org.apache.qpid.server.security.auth.NullAuthenticationManager;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.MapConfiguration;

import java.util.HashMap;
import java.util.Collection;

public class TestApplicationRegistry extends ApplicationRegistry
{
    private QueueRegistry _queueRegistry;

    private ExchangeRegistry _exchangeRegistry;

    private ExchangeFactory _exchangeFactory;

    private ManagedObjectRegistry _managedObjectRegistry;

    private AuthenticationManager _authenticationManager;

    private MessageStore _messageStore;
    private VirtualHost _vHost;

    public TestApplicationRegistry()
    {
        super(new MapConfiguration(new HashMap()));
    }

    public void initialise() throws Exception
    {
        IApplicationRegistry appRegistry = ApplicationRegistry.getInstance();
        _managedObjectRegistry = appRegistry.getManagedObjectRegistry();
        _vHost = appRegistry.getVirtualHostRegistry().getVirtualHost("test");
        _queueRegistry = _vHost.getQueueRegistry();
        _exchangeFactory = _vHost.getExchangeFactory();
        _exchangeRegistry = _vHost.getExchangeRegistry();
        _authenticationManager = new NullAuthenticationManager();
        _messageStore = new TestableMemoryMessageStore();

        _configuration.addProperty("heartbeat.delay", 10 * 60); // 10 minutes
    }

    public Configuration getConfiguration()
    {
        return _configuration;
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

    public ManagedObjectRegistry getManagedObjectRegistry()
    {
        return _managedObjectRegistry;
    }

    public AuthenticationManager getAuthenticationManager()
    {
        return _authenticationManager;
    }

    public Collection<String> getVirtualHostNames()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public VirtualHostRegistry getVirtualHostRegistry()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public MessageStore getMessageStore()
    {
        return _messageStore;
    }
}

