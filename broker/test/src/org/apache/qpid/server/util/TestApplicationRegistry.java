/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import org.apache.qpid.server.security.auth.AuthenticationManager;
import org.apache.qpid.server.security.auth.NullAuthenticationManager;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.MapConfiguration;

import java.util.HashMap;

public class TestApplicationRegistry extends ApplicationRegistry
{
    private QueueRegistry _queueRegistry;

    private ExchangeRegistry _exchangeRegistry;

    private ExchangeFactory _exchangeFactory;

    private ManagedObjectRegistry _managedObjectRegistry;

    private AuthenticationManager _authenticationManager;

    private MessageStore _messageStore;

    public TestApplicationRegistry()
    {
        super(new MapConfiguration(new HashMap()));
    }

    public void initialise() throws Exception
    {
        _managedObjectRegistry = new NoopManagedObjectRegistry();
        _queueRegistry = new DefaultQueueRegistry();
        _exchangeFactory = new DefaultExchangeFactory();
        _exchangeRegistry = new DefaultExchangeRegistry(_exchangeFactory);
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

    public MessageStore getMessageStore()
    {
        return _messageStore;
    }
}

