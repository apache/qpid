/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.qpid.server.security;

import static org.apache.qpid.server.security.access.ObjectType.EXCHANGE;
import static org.apache.qpid.server.security.access.ObjectType.METHOD;
import static org.apache.qpid.server.security.access.ObjectType.QUEUE;
import static org.apache.qpid.server.security.access.ObjectType.VIRTUALHOST;
import static org.apache.qpid.server.security.access.Operation.BIND;
import static org.apache.qpid.server.security.access.Operation.CONSUME;
import static org.apache.qpid.server.security.access.Operation.CREATE;
import static org.apache.qpid.server.security.access.Operation.DELETE;
import static org.apache.qpid.server.security.access.Operation.PUBLISH;
import static org.apache.qpid.server.security.access.Operation.PURGE;
import static org.apache.qpid.server.security.access.Operation.UNBIND;

import java.net.SocketAddress;
import java.security.Principal;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.security.auth.Subject;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.plugins.PluginManager;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.Operation;

/**
 * The security manager contains references to all loaded {@link SecurityPlugin}s and delegates security decisions to them based
 * on virtual host name. The plugins can be external <em>OSGi</em> .jar files that export the required classes or just internal
 * objects for simpler plugins.
 * 
 * @see SecurityPlugin
 */
public class SecurityManager
{
    private static final Logger _logger = Logger.getLogger(SecurityManager.class);
    
    /** Container for the {@link Principal} that is using to this thread. */
    private static final ThreadLocal<Subject> _subject = new ThreadLocal<Subject>();
    private static final ThreadLocal<Boolean> _accessChecksDisabled = new ThreadLocal<Boolean>()
    {
        protected Boolean initialValue()
        {
            return false;
        }
    };

    private PluginManager _pluginManager;
    private Map<String, SecurityPluginFactory> _pluginFactories = new HashMap<String, SecurityPluginFactory>();
    private Map<String, SecurityPlugin> _globalPlugins = new HashMap<String, SecurityPlugin>();
    private Map<String, SecurityPlugin> _hostPlugins = new HashMap<String, SecurityPlugin>();

    public static class SecurityConfiguration extends ConfigurationPlugin
    {
        public static final ConfigurationPluginFactory FACTORY = new ConfigurationPluginFactory()
        {
            public ConfigurationPlugin newInstance(String path, Configuration config) throws ConfigurationException
            {
                ConfigurationPlugin instance = new SecurityConfiguration();
                instance.setConfiguration(path, config);
                return instance;
            }

            public List<String> getParentPaths()
            {
                return Arrays.asList("security", "virtualhosts.virtualhost.security");
            }
        };

        @Override
        public String[] getElementsProcessed()
        {
            return new String[]{"security"};
        }

        public void validateConfiguration() throws ConfigurationException
        {
            if (_configuration.isEmpty())
            {
                throw new ConfigurationException("security section is incomplete, no elements found.");
            }
        }
    }


    public SecurityManager(SecurityManager parent) throws ConfigurationException
    {
        _pluginManager = parent._pluginManager;
        _pluginFactories = parent._pluginFactories;
        
        // our global plugins are the parent's host plugins
        _globalPlugins = parent._hostPlugins;
    }

    public SecurityManager(ConfigurationPlugin configuration, PluginManager manager) throws ConfigurationException
    {
        this(configuration, manager, null);
    }

    public SecurityManager(ConfigurationPlugin configuration, PluginManager manager, SecurityPluginFactory plugin) throws ConfigurationException
    {
        _pluginManager = manager;
        if (manager == null) // No plugin manager, no plugins
        {
            return;
        }

        _pluginFactories = _pluginManager.getSecurityPlugins();
        if (plugin != null)
        {
            _pluginFactories.put(plugin.getPluginName(), plugin);
        }

        configureHostPlugins(configuration);
    }

    public static Subject getThreadSubject()
    {
        return _subject.get();
    }

    public static void setThreadSubject(final Subject subject)
    {
        _subject.set(subject);
    }

    public void configureHostPlugins(ConfigurationPlugin hostConfig) throws ConfigurationException
    {
        _hostPlugins = configurePlugins(hostConfig);
    }
    
    public void configureGlobalPlugins(ConfigurationPlugin configuration) throws ConfigurationException
    {
        _globalPlugins = configurePlugins(configuration);
    }

    public Map<String, SecurityPlugin> configurePlugins(ConfigurationPlugin hostConfig) throws ConfigurationException
    {
        Map<String, SecurityPlugin> plugins = new HashMap<String, SecurityPlugin>();
        SecurityConfiguration securityConfig = hostConfig.getConfiguration(SecurityConfiguration.class.getName());

        // If we have no security Configuration then there is nothing to configure.        
        if (securityConfig != null)
        {
            for (SecurityPluginFactory<?> factory : _pluginFactories.values())
            {
                SecurityPlugin plugin = factory.newInstance(securityConfig);
                if (plugin != null)
                {
                    plugins.put(factory.getPluginName(), plugin);
                }
            }
        }
        return plugins;
    }

    public void addHostPlugin(SecurityPlugin plugin)
    {
        _hostPlugins.put(plugin.getClass().getName(), plugin);
    }

    public static Logger getLogger()
    {
        return _logger;
    }

    private static class CachedPropertiesMap extends LinkedHashMap<String, PublishAccessCheck>
    {
        @Override
        protected boolean removeEldestEntry(Entry<String, PublishAccessCheck> eldest)
        {
            return size() >= 200;
        }
    }

    private abstract class AccessCheck
    {
        abstract Result allowed(SecurityPlugin plugin);
    }

    private boolean checkAllPlugins(AccessCheck checker)
    {
        if(_accessChecksDisabled.get())
        {
            return true;
        }

        Map<String, SecurityPlugin> remainingPlugins = _globalPlugins.isEmpty()
                ? Collections.<String, SecurityPlugin>emptyMap()
                : _hostPlugins.isEmpty() ? _globalPlugins : new HashMap<String, SecurityPlugin>(_globalPlugins);
		
		if(!_hostPlugins.isEmpty())
        {
            for (Entry<String, SecurityPlugin> hostEntry : _hostPlugins.entrySet())
            {
                // Create set of global only plugins
                SecurityPlugin globalPlugin = remainingPlugins.get(hostEntry.getKey());
                if (globalPlugin != null)
                {
                    remainingPlugins.remove(hostEntry.getKey());
                }

                Result host = checker.allowed(hostEntry.getValue());

                if (host == Result.DENIED)
                {
                    // Something vetoed the access, we're done
                    return false;
                }

                // host allow overrides global allow, so only check global on abstain or defer
                if (host != Result.ALLOWED)
                {
                    if (globalPlugin == null)
                    {
                        if (host == Result.DEFER)
                        {
                            host = hostEntry.getValue().getDefault();
                        }
                        if (host == Result.DENIED)
                        {
                            return false;
                        }
                    }
                    else
                    {
                        Result global = checker.allowed(globalPlugin);
                        if (global == Result.DEFER)
                        {
                            global = globalPlugin.getDefault();
                        }
                        if (global == Result.ABSTAIN && host == Result.DEFER)
                        {
                            global = hostEntry.getValue().getDefault();
                        }
                        if (global == Result.DENIED)
                        {
                            return false;
                        }
                    }
                }
            }
        }

        for (SecurityPlugin plugin : remainingPlugins.values())
        {
            Result remaining = checker.allowed(plugin);
			if (remaining == Result.DEFER)
            {
                remaining = plugin.getDefault();
            }
			if (remaining == Result.DENIED)
            {
                return false;
            }
        }
        
        // getting here means either allowed or abstained from all plugins
        return true;
    }
    
    public boolean authoriseBind(final Exchange exch, final AMQQueue queue, final AMQShortString routingKey)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(SecurityPlugin plugin)
            {
                return plugin.authorise(BIND, EXCHANGE, new ObjectProperties(exch, queue, routingKey));
            }
        });
    }

    public boolean authoriseMethod(final Operation operation, final String componentName, final String methodName)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(SecurityPlugin plugin)
            {
                ObjectProperties properties = new ObjectProperties();
                properties.setName(methodName);
                if (componentName != null)
                {
                    // Only set the property if there is a component name
	                properties.put(ObjectProperties.Property.COMPONENT, componentName);
                }
                return plugin.authorise(operation, METHOD, properties);
            }
        });
    }

    public boolean accessVirtualhost(final String vhostname, final SocketAddress remoteAddress)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(SecurityPlugin plugin)
            {
                return plugin.access(VIRTUALHOST, remoteAddress);
            }
        });
    }

    public boolean authoriseConsume(final AMQQueue queue)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(SecurityPlugin plugin)
            {
                return plugin.authorise(CONSUME, QUEUE, new ObjectProperties(queue));
            }
        });
    }

    public boolean authoriseCreateExchange(final Boolean autoDelete, final Boolean durable, final AMQShortString exchangeName,
            final Boolean internal, final Boolean nowait, final Boolean passive, final AMQShortString exchangeType)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(SecurityPlugin plugin)
            {
                return plugin.authorise(CREATE, EXCHANGE, new ObjectProperties(autoDelete, durable, exchangeName,
                        internal, nowait, passive, exchangeType));
            }
        });
    }

    public boolean authoriseCreateQueue(final Boolean autoDelete, final Boolean durable, final Boolean exclusive,
            final Boolean nowait, final Boolean passive, final AMQShortString queueName, final String owner)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(SecurityPlugin plugin)
            {
                return plugin.authorise(CREATE, QUEUE, new ObjectProperties(autoDelete, durable, exclusive, nowait, passive, queueName, owner));
            }
        });
    }

    public boolean authoriseDelete(final AMQQueue queue)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(SecurityPlugin plugin)
            {
                return plugin.authorise(DELETE, QUEUE, new ObjectProperties(queue));
            }
        });
    }

    public boolean authoriseDelete(final Exchange exchange)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(SecurityPlugin plugin)
            {
                return plugin.authorise(DELETE, EXCHANGE, new ObjectProperties(exchange.getName()));
            }
        });
    }


    private ConcurrentHashMap<String, ConcurrentHashMap<String, PublishAccessCheck>> _immediatePublishPropsCache
            = new ConcurrentHashMap<String, ConcurrentHashMap<String, PublishAccessCheck>>();
    private ConcurrentHashMap<String, ConcurrentHashMap<String, PublishAccessCheck>> _publishPropsCache
            = new ConcurrentHashMap<String, ConcurrentHashMap<String, PublishAccessCheck>>();

    public boolean authorisePublish(final boolean immediate, final String routingKey, final String exchangeName)
    {
        PublishAccessCheck check;
        ConcurrentHashMap<String, ConcurrentHashMap<String, PublishAccessCheck>> cache =
                immediate ? _immediatePublishPropsCache : _publishPropsCache;

        ConcurrentHashMap<String, PublishAccessCheck> exchangeMap = cache.get(exchangeName);
        if(exchangeMap == null)
        {
            cache.putIfAbsent(exchangeName, new ConcurrentHashMap<String, PublishAccessCheck>());
            exchangeMap = cache.get(exchangeName);
        }

            check = exchangeMap.get(routingKey);
            if(check == null)
            {
                check = new PublishAccessCheck(new ObjectProperties(exchangeName, routingKey, immediate));
                exchangeMap.put(routingKey, check);
            }

        return checkAllPlugins(check);
    }

    public boolean authorisePurge(final AMQQueue queue)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(SecurityPlugin plugin)
            {
                return plugin.authorise(PURGE, QUEUE, new ObjectProperties(queue));
            }
        });
    }

    public boolean authoriseUnbind(final Exchange exch, final AMQShortString routingKey, final AMQQueue queue)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(SecurityPlugin plugin)
            {
                return plugin.authorise(UNBIND, EXCHANGE, new ObjectProperties(exch, queue, routingKey));
            }
        });
    }

    public static boolean setAccessChecksDisabled(final boolean status)
    {
        //remember current value
        boolean current = _accessChecksDisabled.get();

        _accessChecksDisabled.set(status);

        return current;
    }

    private class PublishAccessCheck extends AccessCheck
    {
        private final ObjectProperties _props;

        public PublishAccessCheck(ObjectProperties props)
        {
            _props = props;
        }

        Result allowed(SecurityPlugin plugin)
        {
            return plugin.authorise(PUBLISH, EXCHANGE, _props);
        }
    }
}
