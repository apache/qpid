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

import org.apache.qpid.server.binding.BindingImpl;
import org.apache.qpid.server.consumer.Consumer;
import org.apache.qpid.server.exchange.ExchangeImpl;

import org.apache.qpid.server.model.*;
import org.apache.qpid.server.plugin.AccessControlFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.access.FileAccessControlProviderConstants;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.access.OperationLoggingDetails;
import org.apache.qpid.server.security.auth.TaskPrincipal;

import javax.security.auth.Subject;

import static org.apache.qpid.server.security.access.ObjectType.BROKER;
import static org.apache.qpid.server.security.access.ObjectType.EXCHANGE;
import static org.apache.qpid.server.security.access.ObjectType.GROUP;
import static org.apache.qpid.server.security.access.ObjectType.METHOD;
import static org.apache.qpid.server.security.access.ObjectType.QUEUE;
import static org.apache.qpid.server.security.access.ObjectType.USER;
import static org.apache.qpid.server.security.access.ObjectType.VIRTUALHOST;
import static org.apache.qpid.server.security.access.Operation.ACCESS_LOGS;
import static org.apache.qpid.server.security.access.Operation.BIND;
import static org.apache.qpid.server.security.access.Operation.CONFIGURE;
import static org.apache.qpid.server.security.access.Operation.CONSUME;
import static org.apache.qpid.server.security.access.Operation.CREATE;
import static org.apache.qpid.server.security.access.Operation.DELETE;
import static org.apache.qpid.server.security.access.Operation.PUBLISH;
import static org.apache.qpid.server.security.access.Operation.PURGE;
import static org.apache.qpid.server.security.access.Operation.UNBIND;
import static org.apache.qpid.server.security.access.Operation.UPDATE;

import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class SecurityManager implements ConfigurationChangeListener
{
    private static final Logger _logger = Logger.getLogger(SecurityManager.class);

    private static final Subject SYSTEM = new Subject(true,
                                                     Collections.singleton(new SystemPrincipal()),
                                                     Collections.emptySet(),
                                                     Collections.emptySet());


    private ConcurrentHashMap<String, AccessControl> _globalPlugins = new ConcurrentHashMap<String, AccessControl>();
    private ConcurrentHashMap<String, AccessControl> _hostPlugins = new ConcurrentHashMap<String, AccessControl>();

    private boolean _managementMode;

    private Broker _broker;

    /*
     * Used by the Broker.
     */
    public SecurityManager(Broker broker, boolean managementMode)
    {
        _managementMode = managementMode;
        _broker = broker;
    }

    /*
     * Used by the VirtualHost to allow deferring to the broker level security plugins if required.
     */
    public SecurityManager(SecurityManager parent, String aclFile, String vhostName)
    {
        _managementMode = parent._managementMode;
        _broker = parent._broker;
        if(!_managementMode)
        {
            configureVirtualHostAclPlugin(aclFile, vhostName);

            // our global plugins are the parent's host plugins
            _globalPlugins = parent._hostPlugins;
        }
    }

    public static Subject getSubjectWithAddedSystemRights()
    {
        Subject subject = Subject.getSubject(AccessController.getContext());
        if(subject == null)
        {
            subject = new Subject();
        }
        else
        {
            subject = new Subject(false, subject.getPrincipals(), subject.getPublicCredentials(), subject.getPrivateCredentials());
        }
        subject.getPrincipals().addAll(SYSTEM.getPrincipals());
        subject.setReadOnly();
        return subject;
    }


    public static Subject getSystemTaskSubject(String taskName)
    {
        Subject subject = new Subject(false, SYSTEM.getPrincipals(), SYSTEM.getPublicCredentials(), SYSTEM.getPrivateCredentials());
        subject.getPrincipals().add(new TaskPrincipal(taskName));
        subject.setReadOnly();
        return subject;
    }

    private void configureVirtualHostAclPlugin(String aclFile, String vhostName)
    {
        if(aclFile != null)
        {
            Map<String, Object> attributes = new HashMap<String, Object>();

            attributes.put(AccessControlProvider.TYPE, FileAccessControlProviderConstants.ACL_FILE_PROVIDER_TYPE);
            attributes.put(FileAccessControlProviderConstants.PATH, aclFile);

            for (AccessControlFactory provider : (new QpidServiceLoader<AccessControlFactory>()).instancesOf(AccessControlFactory.class))
            {
                AccessControl accessControl = provider.createInstance(attributes, _broker);
                accessControl.open();
                if(accessControl != null)
                {
                    String pluginTypeName = getPluginTypeName(accessControl);
                    _hostPlugins.put(pluginTypeName, accessControl);

                    if(_logger.isDebugEnabled())
                    {
                        _logger.debug("Added access control to host plugins with name: " + vhostName);
                    }

                    break;
                }
            }
        }

        if(_logger.isDebugEnabled())
        {
            _logger.debug("Configured " + _hostPlugins.size() + " access control plugins");
        }
    }

    private String getPluginTypeName(AccessControl accessControl)
    {
        return accessControl.getClass().getName();
    }

    public static Logger getLogger()
    {
        return _logger;
    }

    public static boolean isSystemProcess()
    {
        Subject subject = Subject.getSubject(AccessController.getContext());
        return !(subject == null  || subject.getPrincipals(SystemPrincipal.class).isEmpty());
    }

    private static final class SystemPrincipal implements Principal
    {
        private SystemPrincipal()
        {
        }

        @Override
        public String getName()
        {
            return "SYSTEM";
        }
    }

    private abstract class AccessCheck
    {
        abstract Result allowed(AccessControl plugin);
    }

    private boolean checkAllPlugins(AccessCheck checker)
    {
        // If we are running as SYSTEM then no ACL checking
        if(isSystemProcess())
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

    public void authoriseCreateBinding(BindingImpl binding)
    {
        final ExchangeImpl exch = binding.getExchange();
        final AMQQueue queue = binding.getAMQQueue();
        final String bindingKey = binding.getBindingKey();

        boolean allowed =
            checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(BIND, EXCHANGE, new ObjectProperties(exch, queue, bindingKey));
            }
        });

        if(!allowed)
        {
            throw new AccessControlException("Permission denied: binding " + bindingKey);
        }
    }

    public void authoriseMethod(final Operation operation, final String componentName, final String methodName)
    {
        boolean allowed =  checkAllPlugins(new AccessCheck()
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
        if(!allowed)
        {
            throw new AccessControlException("Permission denied: " + operation.name() + " " + methodName);
        }
    }

    public void accessManagement()
    {
        if(!checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(Operation.ACCESS, ObjectType.MANAGEMENT, ObjectProperties.EMPTY);
            }
        }))
        {
            throw new AccessControlException("User not authorised for management");
        }
    }

    public void authoriseCreateConnection(final AMQConnectionModel connection)
    {
        if(!checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(Operation.ACCESS, VIRTUALHOST, ObjectProperties.EMPTY);
            }
        }))
        {
            throw new AccessControlException("Permission denied: " + connection.getVirtualHostName());
        }
    }

    public void authoriseCreateConsumer(final Consumer consumer)
    {
        // TODO - remove cast to AMQQueue and allow testing of consumption from any MessageSource
        final AMQQueue queue = (AMQQueue) consumer.getMessageSource();

        if(!checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(CONSUME, QUEUE, new ObjectProperties(queue));
            }
        }))
        {
            throw new AccessControlException("Permission denied: consume from queue '" + queue.getName() + "'.");
        }
    }

    public void authoriseCreateExchange(final ExchangeImpl exchange)
    {
        final String exchangeName = exchange.getName();
        if(!checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(CREATE, EXCHANGE, new ObjectProperties(exchange.isAutoDelete(),
                                                                               exchange.isDurable(),
                                                                               exchangeName,
                                                                               exchange.getTypeName()));
            }
        }))
        {
            throw new AccessControlException("Permission denied: exchange-name '" + exchangeName + "'");
        }
    }

    public void authoriseCreateQueue(final AMQQueue queue)
    {
        final String queueName = queue.getName();
        if(! checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(CREATE, QUEUE, new ObjectProperties(queue.getAttribute(Queue.LIFETIME_POLICY) != LifetimePolicy.PERMANENT,
                                                                            Boolean.TRUE.equals(queue.getAttribute(Queue.DURABLE)),
                                                                            queue.getAttribute(Queue.EXCLUSIVE) != ExclusivityPolicy.NONE,
                                                                            queueName,
                                                                            queue.getOwner()));
            }
        }))
        {
            throw new AccessControlException("Permission denied: queue-name '" + queueName + "'");
        }
    }


    public void authoriseDelete(final AMQQueue queue)
    {
        if(!checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(DELETE, QUEUE, new ObjectProperties(queue));
            }
        }))
        {
            throw new AccessControlException("Permission denied, delete queue: " + queue.getName());
        }
    }


    public void authoriseUpdate(final AMQQueue queue)
    {
        if(!checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(UPDATE, QUEUE, new ObjectProperties(queue));
            }
        }))
        {
            throw new AccessControlException("Permission denied: update queue: " + queue.getName());
        }
    }


    public void authoriseUpdate(final ExchangeImpl exchange)
    {
        if(!checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(UPDATE, EXCHANGE, new ObjectProperties(exchange.getName()));
            }
        }))
        {
            throw new AccessControlException("Permission denied: update exchange: " + exchange.getName());
        }
    }

    public void authoriseDelete(final ExchangeImpl exchange)
    {
        if(! checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(DELETE, EXCHANGE, new ObjectProperties(exchange.getName()));
            }
        }))
        {
            throw new AccessControlException("Permission denied, delete exchange: '" + exchange.getName() + "'");
        }
    }

    public void authoriseGroupOperation(final Operation operation, final String groupName)
    {
        if(!checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(operation, GROUP, new ObjectProperties(groupName));
            }
        }))
        {
            throw new AccessControlException("Do not have permission" +
                                             " to perform the " + operation + " on the group " + groupName);
        }
    }

    public void authoriseUserOperation(final Operation operation, final String userName)
    {
        if(! checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(operation, USER, new ObjectProperties(userName));
            }
        }))
        {
            throw new AccessControlException("Do not have permission" +
                                             " to perform the " + operation + " on the user " + userName);
        }
    }

    private ConcurrentHashMap<String, ConcurrentHashMap<String, PublishAccessCheck>> _immediatePublishPropsCache
            = new ConcurrentHashMap<String, ConcurrentHashMap<String, PublishAccessCheck>>();
    private ConcurrentHashMap<String, ConcurrentHashMap<String, PublishAccessCheck>> _publishPropsCache
            = new ConcurrentHashMap<String, ConcurrentHashMap<String, PublishAccessCheck>>();

    public void authorisePublish(final boolean immediate, String routingKey, String exchangeName)
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

        if(!checkAllPlugins(check))
        {
            throw new AccessControlException("Permission denied, publish to: exchange-name '" + exchangeName + "'");
        }
    }

    public void authorisePurge(final AMQQueue queue)
    {
        if(!checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(PURGE, QUEUE, new ObjectProperties(queue));
            }
        }))
        {
            throw new AccessControlException("Permission denied: queue " + queue.getName());
        }
    }

    public void authoriseUnbind(final ExchangeImpl exch, final String routingKey, final AMQQueue queue)
    {
        if(! checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(UNBIND, EXCHANGE, new ObjectProperties(exch, queue, routingKey));
            }
        }))
        {
            throw new AccessControlException("Permission denied: unbinding " + routingKey);
        }
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

    @Override
    public void stateChanged(ConfiguredObject object, State oldState, State newState)
    {
        if(_managementMode)
        {
            //AccessControl is disabled in ManagementMode
            return;
        }

        if(object instanceof AccessControlProvider)
        {
            if(newState == State.ACTIVE)
            {
                synchronized (_hostPlugins)
                {
                    AccessControl accessControl = ((AccessControlProvider)object).getAccessControl();
                    String pluginTypeName = getPluginTypeName(accessControl);

                    _hostPlugins.put(pluginTypeName, accessControl);
                }
            }
            else if(newState == State.DELETED)
            {
                synchronized (_hostPlugins)
                {
                    AccessControl control = ((AccessControlProvider)object).getAccessControl();
                    String pluginTypeName = getPluginTypeName(control);

                    // Remove the type->control mapping for this type key only if the
                    // given control is actually referred to.
                    if(_hostPlugins.containsValue(control))
                    {
                        // If we are removing this control, check if another of the same
                        // type already exists on the broker and use it in instead.
                        AccessControl other = null;
                        Collection<AccessControlProvider> providers = _broker.getAccessControlProviders();
                        for(AccessControlProvider p : providers)
                        {
                            if(p == object || p.getState() != State.ACTIVE)
                            {
                                //we don't count ourself as another
                                continue;
                            }

                            AccessControl ac = p.getAccessControl();
                            if(pluginTypeName.equals(getPluginTypeName(ac)))
                            {
                                other = ac;
                                break;
                            }
                        }

                        if(other != null)
                        {
                            //Another control of this type was found, use it instead
                            _hostPlugins.replace(pluginTypeName, control, other);
                        }
                        else
                        {
                            //No other was found, remove the type entirely
                            _hostPlugins.remove(pluginTypeName);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void childAdded(ConfiguredObject object, ConfiguredObject child)
    {
        // no op
    }

    @Override
    public void childRemoved(ConfiguredObject object, ConfiguredObject child)
    {
        // no op
    }

    @Override
    public void attributeSet(ConfiguredObject object, String attributeName, Object oldAttributeValue, Object newAttributeValue)
    {
        // no op
    }

    public boolean authoriseConfiguringBroker(String configuredObjectName, Class<? extends ConfiguredObject> configuredObjectType, Operation configuredObjectOperation)
    {
        String description = String.format("%s %s '%s'",
                configuredObjectOperation == null? null : configuredObjectOperation.name().toLowerCase(),
                configuredObjectType == null ? null : configuredObjectType.getSimpleName().toLowerCase(),
                configuredObjectName);
        final OperationLoggingDetails properties = new OperationLoggingDetails(description);
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(CONFIGURE, BROKER, properties);
            }
        });
    }

    public boolean authoriseLogsAccess()
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(ACCESS_LOGS, BROKER, ObjectProperties.EMPTY);
            }
        });
    }

}
