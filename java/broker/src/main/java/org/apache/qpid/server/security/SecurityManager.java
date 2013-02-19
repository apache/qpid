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

import org.apache.log4j.Logger;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.exchange.Exchange;

import org.apache.qpid.server.plugin.AccessControlFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;

import static org.apache.qpid.server.security.access.ObjectType.EXCHANGE;
import static org.apache.qpid.server.security.access.ObjectType.GROUP;
import static org.apache.qpid.server.security.access.ObjectType.METHOD;
import static org.apache.qpid.server.security.access.ObjectType.QUEUE;
import static org.apache.qpid.server.security.access.ObjectType.USER;
import static org.apache.qpid.server.security.access.ObjectType.VIRTUALHOST;
import static org.apache.qpid.server.security.access.Operation.BIND;
import static org.apache.qpid.server.security.access.Operation.CONSUME;
import static org.apache.qpid.server.security.access.Operation.CREATE;
import static org.apache.qpid.server.security.access.Operation.DELETE;
import static org.apache.qpid.server.security.access.Operation.PUBLISH;
import static org.apache.qpid.server.security.access.Operation.PURGE;
import static org.apache.qpid.server.security.access.Operation.UNBIND;

import javax.security.auth.Subject;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The security manager contains references to all loaded {@link AccessControl}s and delegates security decisions to them based
 * on virtual host name. The plugins can be external <em>OSGi</em> .jar files that export the required classes or just internal
 * objects for simpler plugins.
 *
 * @see AccessControl
 */
public class SecurityManager
{
    private static final Logger _logger = Logger.getLogger(SecurityManager.class);

    /** Container for the {@link java.security.Principal} that is using to this thread. */
    private static final ThreadLocal<Subject> _subject = new ThreadLocal<Subject>();

    public static final ThreadLocal<Boolean> _accessChecksDisabled = new ClearingThreadLocal(false);

    private Map<String, AccessControl> _globalPlugins = new HashMap<String, AccessControl>();
    private Map<String, AccessControl> _hostPlugins = new HashMap<String, AccessControl>();

    /**
     * A special ThreadLocal, which calls remove() on itself whenever the value is
     * the default, to avoid leaving a default value set after its use has passed.
     */
    private static final class ClearingThreadLocal extends ThreadLocal<Boolean>
    {
        private Boolean _defaultValue;

        public ClearingThreadLocal(Boolean defaultValue)
        {
            super();
            _defaultValue = defaultValue;
        }

        @Override
        protected Boolean initialValue()
        {
            return _defaultValue;
        }

        @Override
        public void set(Boolean value)
        {
            if (value == _defaultValue)
            {
                super.remove();
            }
            else
            {
                super.set(value);
            }
        }

        @Override
        public Boolean get()
        {
            Boolean value = super.get();
            if (value == _defaultValue)
            {
                super.remove();
            }
            return value;
        }
    }

    /*
     * Used by the VirtualHost to allow deferring to the broker level security plugins if required.
     */
    public SecurityManager(SecurityManager parent, String aclFile)
    {
        this(aclFile);

        // our global plugins are the parent's host plugins
        _globalPlugins = parent._hostPlugins;
    }

    public SecurityManager(String aclFile)
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("aclFile", aclFile);
        for (AccessControlFactory provider : (new QpidServiceLoader<AccessControlFactory>()).instancesOf(AccessControlFactory.class))
        {
            AccessControl accessControl = provider.createInstance(attributes);
            if(accessControl != null)
            {
                addHostPlugin(accessControl);
            }
        }

        if(_logger.isDebugEnabled())
        {
            _logger.debug("Configured " + _hostPlugins.size() + " access control plugins");
        }
    }

    public static Subject getThreadSubject()
    {
        return _subject.get();
    }

    public static void setThreadSubject(final Subject subject)
    {
        _subject.set(subject);
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
        abstract Result allowed(AccessControl plugin);
    }

    private boolean checkAllPlugins(AccessCheck checker)
    {
        if(_accessChecksDisabled.get())
        {
            return true;
        }

        Map<String, AccessControl> remainingPlugins = _globalPlugins.isEmpty()
                ? Collections.<String, AccessControl>emptyMap()
                : _hostPlugins.isEmpty() ? _globalPlugins : new HashMap<String, AccessControl>(_globalPlugins);

        if(!_hostPlugins.isEmpty())
        {
            for (Entry<String, AccessControl> hostEntry : _hostPlugins.entrySet())
            {
                // Create set of global only plugins
                AccessControl globalPlugin = remainingPlugins.get(hostEntry.getKey());
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

        for (AccessControl plugin : remainingPlugins.values())
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
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(BIND, EXCHANGE, new ObjectProperties(exch, queue, routingKey));
            }
        });
    }

    public boolean authoriseMethod(final Operation operation, final String componentName, final String methodName)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
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

    public boolean accessManagement()
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.access(ObjectType.MANAGEMENT, null);
            }
        });
    }

    public boolean accessVirtualhost(final String vhostname, final SocketAddress remoteAddress)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.access(VIRTUALHOST, remoteAddress);
            }
        });
    }

    public boolean authoriseConsume(final AMQQueue queue)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
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
            Result allowed(AccessControl plugin)
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
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(CREATE, QUEUE, new ObjectProperties(autoDelete, durable, exclusive, nowait, passive, queueName, owner));
            }
        });
    }

    public boolean authoriseDelete(final AMQQueue queue)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(DELETE, QUEUE, new ObjectProperties(queue));
            }
        });
    }

    public boolean authoriseDelete(final Exchange exchange)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(DELETE, EXCHANGE, new ObjectProperties(exchange.getName()));
            }
        });
    }

    public boolean authoriseGroupOperation(final Operation operation, final String groupName)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(operation, GROUP, new ObjectProperties(groupName));
            }
        });
    }

    public boolean authoriseUserOperation(final Operation operation, final String userName)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(operation, USER, new ObjectProperties(userName));
            }
        });
    }

    private ConcurrentHashMap<String, ConcurrentHashMap<String, PublishAccessCheck>> _immediatePublishPropsCache
            = new ConcurrentHashMap<String, ConcurrentHashMap<String, PublishAccessCheck>>();
    private ConcurrentHashMap<String, ConcurrentHashMap<String, PublishAccessCheck>> _publishPropsCache
            = new ConcurrentHashMap<String, ConcurrentHashMap<String, PublishAccessCheck>>();

    public boolean authorisePublish(final boolean immediate, String routingKey, String exchangeName)
    {
        if(routingKey == null)
        {
            routingKey = "";
        }
        if(exchangeName == null)
        {
            exchangeName = "";
        }
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
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(PURGE, QUEUE, new ObjectProperties(queue));
            }
        });
    }

    public boolean authoriseUnbind(final Exchange exch, final AMQShortString routingKey, final AMQQueue queue)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
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

        Result allowed(AccessControl plugin)
        {
            return plugin.authorise(PUBLISH, EXCHANGE, _props);
        }
    }


    public void addHostPlugin(AccessControl plugin)
    {
        _hostPlugins.put(plugin.getClass().getName(), plugin);
    }

}
