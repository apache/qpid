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
 *
 * 
 */
package org.apache.qpid.server.security.access;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.plugins.PluginManager;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.access.ACLPlugin.AuthzResult;
import org.apache.qpid.server.security.access.plugins.SimpleXML;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class ACLManager
{
    private static final Logger _logger = Logger.getLogger(ACLManager.class);
    private PluginManager _pluginManager;
    private Map<String, ACLPluginFactory> _allSecurityPlugins = new HashMap<String, ACLPluginFactory>();
    private Map<String, ACLPlugin> _globalPlugins = new HashMap<String, ACLPlugin>();
    private Map<String, ACLPlugin> _hostPlugins = new HashMap<String, ACLPlugin>();

    public ACLManager(Configuration configuration, PluginManager manager)
    {
        this(configuration, manager, null);
    }

    public ACLManager(Configuration configuration, PluginManager manager, ACLPluginFactory securityPlugin)
    {
        _pluginManager = manager;

        if (manager == null) // No plugin manager, no plugins
        {
            return;
        }

        _allSecurityPlugins = _pluginManager.getSecurityPlugins();
        if (securityPlugin != null)
        {
            _allSecurityPlugins.put(securityPlugin.getClass().getName(), securityPlugin);
        }

        _globalPlugins = configurePlugins(configuration);
    }


    public void configureHostPlugins(Configuration hostConfig)
    {
        _hostPlugins = configurePlugins(hostConfig);
    }
    
    public Map<String, ACLPlugin> configurePlugins(Configuration configuration)
    {
        Configuration securityConfig = configuration.subset("security");
        Map<String, ACLPlugin> plugins = new HashMap<String, ACLPlugin>();
        Iterator keys = securityConfig.getKeys();
        Collection<String> handledTags = new HashSet();
        while (keys.hasNext())
        {
            // Splitting the string is necessary here because of the way that getKeys() returns only
            // bottom level children
            String tag = ((String) keys.next()).split("\\.", 2)[0];
            
            if (!handledTags.contains(tag))
            {
                for (ACLPluginFactory plugin : _allSecurityPlugins.values())
                {
                    if (plugin.supportsTag(tag))
                    {
                        _logger.warn("Plugin handling security section "+tag+" is "+plugin.getClass().getSimpleName());
                        handledTags.add(tag);
                        plugins.put(plugin.getClass().getName(), plugin.newInstance(securityConfig));
                    }
                }
            }
            if (!handledTags.contains(tag))
            {
                _logger.warn("No plugin handled security section "+tag);
            }
        }
        return plugins;
    }    

    public static Logger getLogger()
    {
        return _logger;
    }

    private abstract class AccessCheck
    {
        abstract AuthzResult allowed(ACLPlugin plugin);
    }

    private boolean checkAllPlugins(AccessCheck checker)
    {
        AuthzResult result = AuthzResult.ABSTAIN;
        HashMap<String, ACLPlugin> remainingPlugins = new HashMap<String, ACLPlugin>();
        remainingPlugins.putAll(_globalPlugins);
        for (Entry<String, ACLPlugin> plugin : _hostPlugins.entrySet())
        {
            result = checker.allowed(plugin.getValue());
            if (result == AuthzResult.DENIED)
            {
                // Something vetoed the access, we're done
                return false; 
            }
            else if (result == AuthzResult.ALLOWED)
            {
                // Remove plugin from global check list since 
                // host allow overrides global allow
                remainingPlugins.remove(plugin.getKey());
            }
        }
        
        for (ACLPlugin plugin : remainingPlugins.values())
        {   
            result = checker.allowed(plugin);
            if (result == AuthzResult.DENIED)
            {
                return false;
            }
        }
        return true;
    }

    public boolean authoriseBind(final AMQProtocolSession session, final Exchange exch, final AMQQueue queue,
            final AMQShortString routingKey)
    {
        return checkAllPlugins(new AccessCheck()
        {

            @Override
            AuthzResult allowed(ACLPlugin plugin)
            {
                return plugin.authoriseBind(session, exch, queue, routingKey);
            }

        });
    }

    public boolean authoriseConnect(final AMQProtocolSession session, final VirtualHost virtualHost)
    {
        return checkAllPlugins(new AccessCheck()
        {

            @Override
            AuthzResult allowed(ACLPlugin plugin)
            {
                return plugin.authoriseConnect(session, virtualHost);
            }

        });
    }

    public boolean authoriseConsume(final AMQProtocolSession session, final boolean noAck, final AMQQueue queue)
    {
        return checkAllPlugins(new AccessCheck()
        {

            @Override
            AuthzResult allowed(ACLPlugin plugin)
            {
                return plugin.authoriseConsume(session, noAck, queue);
            }

        });
    }

    public boolean authoriseConsume(final AMQProtocolSession session, final boolean exclusive, final boolean noAck,
            final boolean noLocal, final boolean nowait, final AMQQueue queue)
    {
        return checkAllPlugins(new AccessCheck()
        {

            @Override
            AuthzResult allowed(ACLPlugin plugin)
            {
                return plugin.authoriseConsume(session, exclusive, noAck, noLocal, nowait, queue);
            }

        });
    }

    public boolean authoriseCreateExchange(final AMQProtocolSession session, final boolean autoDelete,
            final boolean durable, final AMQShortString exchangeName, final boolean internal, final boolean nowait,
            final boolean passive, final AMQShortString exchangeType)
    {
        return checkAllPlugins(new AccessCheck()
        {

            @Override
            AuthzResult allowed(ACLPlugin plugin)
            {
                return plugin.authoriseCreateExchange(session, autoDelete, durable, exchangeName, internal, nowait,
                        passive, exchangeType);
            }

        });
    }

    public boolean authoriseCreateQueue(final AMQProtocolSession session, final boolean autoDelete,
            final boolean durable, final boolean exclusive, final boolean nowait, final boolean passive,
            final AMQShortString queue)
    {
        return checkAllPlugins(new AccessCheck()
        {

            @Override
            AuthzResult allowed(ACLPlugin plugin)
            {
                return plugin.authoriseCreateQueue(session, autoDelete, durable, exclusive, nowait, passive, queue);
            }

        });
    }

    public boolean authoriseDelete(final AMQProtocolSession session, final AMQQueue queue)
    {
        return checkAllPlugins(new AccessCheck()
        {

            @Override
            AuthzResult allowed(ACLPlugin plugin)
            {
                return plugin.authoriseDelete(session, queue);
            }

        });
    }

    public boolean authoriseDelete(final AMQProtocolSession session, final Exchange exchange)
    {
        return checkAllPlugins(new AccessCheck()
        {

            @Override
            AuthzResult allowed(ACLPlugin plugin)
            {
                return plugin.authoriseDelete(session, exchange);
            }

        });
    }
    
    public boolean authorisePublish(final AMQProtocolSession session, final boolean immediate, final boolean mandatory,
            final AMQShortString routingKey, final Exchange e)
    {
        return checkAllPlugins(new AccessCheck()
        {

            @Override
            AuthzResult allowed(ACLPlugin plugin)
            {
                return plugin.authorisePublish(session, immediate, mandatory, routingKey, e);
            }

        });
    }

    public boolean authorisePurge(final AMQProtocolSession session, final AMQQueue queue)
    {
        return checkAllPlugins(new AccessCheck()
        {

            @Override
            AuthzResult allowed(ACLPlugin plugin)
            {
                return plugin.authorisePurge(session, queue);
            }

        });
    }

    public boolean authoriseUnbind(final AMQProtocolSession session, final Exchange exch,
            final AMQShortString routingKey, final AMQQueue queue)
    {
        return checkAllPlugins(new AccessCheck()
        {

            @Override
            AuthzResult allowed(ACLPlugin plugin)
            {
                return plugin.authoriseUnbind(session, exch, routingKey, queue);
            }

        });
    }

    public void addHostPlugin(ACLPlugin aclPlugin)
    {
        _hostPlugins.put(aclPlugin.getClass().getName(), aclPlugin);
    }
}
