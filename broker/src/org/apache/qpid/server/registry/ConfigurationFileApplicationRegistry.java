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
package org.apache.qpid.server.registry;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.SystemConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.exchange.DefaultExchangeRegistry;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.management.JMXManagedObjectRegistry;
import org.apache.qpid.server.management.ManagedObjectRegistry;
import org.apache.qpid.server.management.ManagementConfiguration;
import org.apache.qpid.server.management.NoopManagedObjectRegistry;
import org.apache.qpid.server.queue.DefaultQueueRegistry;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.security.auth.AuthenticationManager;
import org.apache.qpid.server.security.auth.SASLAuthenticationManager;
import org.apache.qpid.server.store.MessageStore;

import java.io.File;

public class ConfigurationFileApplicationRegistry extends ApplicationRegistry
{
    private QueueRegistry _queueRegistry;

    private ExchangeRegistry _exchangeRegistry;

    private ExchangeFactory _exchangeFactory;

    private ManagedObjectRegistry _managedObjectRegistry;

    private AuthenticationManager _authenticationManager;

    private MessageStore _messageStore;

    public ConfigurationFileApplicationRegistry(File configurationURL) throws ConfigurationException
    {
        super(config(configurationURL));
    }

    // Our configuration class needs to make the interpolate method
    // public so it can be called below from the config method.
    private static class MyConfiguration extends CompositeConfiguration {
        public String interpolate(String obj) {
            return super.interpolate(obj);
        }
    }

    private static final Configuration config(File url) throws ConfigurationException {
        // We have to override the interpolate methods so that
        // interpolation takes place accross the entirety of the
        // composite configuration. Without doing this each
        // configuration object only interpolates variables defined
        // inside itself.
        final MyConfiguration conf = new MyConfiguration();
        conf.addConfiguration(new SystemConfiguration() {
            protected String interpolate(String o) {
                return conf.interpolate(o);
            }
        });
        conf.addConfiguration(new XMLConfiguration(url) {
            protected String interpolate(String o) {
                return conf.interpolate(o);
            }
        });
        return conf;
    }

    public void initialise() throws Exception
    {
        initialiseManagedObjectRegistry();
        _queueRegistry = new DefaultQueueRegistry();
        _exchangeFactory = new DefaultExchangeFactory();
        _exchangeRegistry = new DefaultExchangeRegistry(_exchangeFactory);
        _authenticationManager = new SASLAuthenticationManager();
        initialiseMessageStore();
    }

    private void initialiseManagedObjectRegistry()
    {
        ManagementConfiguration config = getConfiguredObject(ManagementConfiguration.class);
        if (config.enabled)
        {
            _managedObjectRegistry = new JMXManagedObjectRegistry();
        }
        else
        {
            _managedObjectRegistry = new NoopManagedObjectRegistry();
        }
    }

    private void initialiseMessageStore() throws Exception
    {
        String messageStoreClass = _configuration.getString("store.class");
        Class clazz = Class.forName(messageStoreClass);
        Object o = clazz.newInstance();

        if (!(o instanceof MessageStore))
        {
            throw new Exception("Message store class must implement " + MessageStore.class + ". Class " + clazz +
                                " does not.");
        }
        _messageStore = (MessageStore) o;
        _messageStore.configure(getQueueRegistry(), "store", _configuration);
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
