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

import static org.apache.qpid.server.security.access.ObjectType.BROKER;
import static org.apache.qpid.server.security.access.ObjectType.EXCHANGE;
import static org.apache.qpid.server.security.access.ObjectType.GROUP;
import static org.apache.qpid.server.security.access.ObjectType.METHOD;
import static org.apache.qpid.server.security.access.ObjectType.QUEUE;
import static org.apache.qpid.server.security.access.ObjectType.USER;
import static org.apache.qpid.server.security.access.ObjectType.VIRTUALHOST;
import static org.apache.qpid.server.security.access.ObjectType.VIRTUALHOSTNODE;
import static org.apache.qpid.server.security.access.Operation.*;

import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.security.auth.Subject;

import org.apache.qpid.server.binding.BindingImpl;
import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.model.AccessControlProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectProperties.Property;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.access.OperationLoggingDetails;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.security.auth.TaskPrincipal;

public class SecurityManager implements ConfigurationChangeListener
{
    private static final Subject SYSTEM = new Subject(true,
                                                     Collections.singleton(new SystemPrincipal()),
                                                     Collections.emptySet(),
                                                     Collections.emptySet());

    private final ConcurrentMap<String, AccessControl> _plugins = new ConcurrentHashMap<String, AccessControl>();
    private final boolean _managementMode;
    private final Broker<?> _broker;

    private final ConcurrentMap<PublishAccessCheckCacheEntry, PublishAccessCheck> _publishAccessCheckCache = new ConcurrentHashMap<SecurityManager.PublishAccessCheckCacheEntry, SecurityManager.PublishAccessCheck>();

    public SecurityManager(Broker<?> broker, boolean managementMode)
    {
        _managementMode = managementMode;
        _broker = broker;
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

    private String getPluginTypeName(AccessControl accessControl)
    {
        return accessControl.getClass().getName();
    }

    public static boolean isSystemProcess()
    {
        Subject subject = Subject.getSubject(AccessController.getContext());
        return !(subject == null  || subject.getPrincipals(SystemPrincipal.class).isEmpty());
    }

    public static AuthenticatedPrincipal getCurrentUser()
    {
        Subject subject = Subject.getSubject(AccessController.getContext());
        final AuthenticatedPrincipal user;
        if(subject != null)
        {
            Set<AuthenticatedPrincipal> principals = subject.getPrincipals(AuthenticatedPrincipal.class);
            if(principals != null && !principals.isEmpty())
            {
                user = principals.iterator().next();
            }
            else
            {
                user = null;
            }
        }
        else
        {
            user = null;
        }
        return user;
    }

    public void addPlugin(final AccessControl accessControl)
    {

        synchronized (_plugins)
        {
            String pluginTypeName = getPluginTypeName(accessControl);

            _plugins.put(pluginTypeName, accessControl);
        }
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

        for (AccessControl plugin : _plugins.values())
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

    public void authoriseCreateBinding(final BindingImpl binding)
    {
        boolean allowed = checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(BIND, EXCHANGE, new ObjectProperties(binding));
            }
        });

        if(!allowed)
        {
            throw new AccessControlException("Permission denied: binding " + binding.getBindingKey());
        }
    }

    public void authoriseMethod(final Operation operation, final String componentName, final String methodName, final String virtualHostName)
    {
        boolean allowed =  checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                ObjectProperties properties = new ObjectProperties();
                properties.setName(methodName);
                if (componentName != null)
                {
                    properties.put(ObjectProperties.Property.COMPONENT, componentName);
                }
                if (virtualHostName != null)
                {
                    properties.put(ObjectProperties.Property.VIRTUALHOST_NAME, virtualHostName);
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

    public void authoriseVirtualHostNode(final String virtualHostNodeName, final Operation operation)
    {
        if(!checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                ObjectProperties properties = new ObjectProperties(virtualHostNodeName);
                return plugin.authorise(operation, VIRTUALHOSTNODE, properties);
            }
        }))
        {
            throw new AccessControlException(operation + " permission denied for " + VIRTUALHOSTNODE
                                             + " : " + virtualHostNodeName);
        }
    }

    public void authoriseVirtualHost(final String virtualHostName, final Operation operation)
    {
        if(!checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                // We put the name into the properties under both name and virtualhost_name so the user may express predicates using either.
                ObjectProperties properties = new ObjectProperties(virtualHostName);
                properties.put(Property.VIRTUALHOST_NAME, virtualHostName);
                return plugin.authorise(operation, VIRTUALHOST, properties);
            }
        }))
        {
            throw new AccessControlException(operation + " permission denied for " + VIRTUALHOST
                                             + " : " + virtualHostName);
        }
    }

    public void authoriseCreateConnection(final AMQConnectionModel connection)
    {
        String virtualHostName = connection.getVirtualHostName();
        try
        {
            authoriseVirtualHost(virtualHostName, Operation.ACCESS);
        }
        catch (AccessControlException ace)
        {
            throw new AccessControlException("Permission denied: " + virtualHostName);
        }
    }

    public void authoriseCreateConsumer(final ConsumerImpl consumer)
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
                return plugin.authorise(CREATE, EXCHANGE, new ObjectProperties(exchange));
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
                return plugin.authorise(CREATE, QUEUE, new ObjectProperties(queue));
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
                return plugin.authorise(UPDATE, EXCHANGE, new ObjectProperties(exchange));
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
                return plugin.authorise(DELETE, EXCHANGE, new ObjectProperties(exchange));
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

    public void authorisePublish(final boolean immediate, String routingKey, String exchangeName, String virtualHostName)
    {
        PublishAccessCheckCacheEntry key = new PublishAccessCheckCacheEntry(immediate, routingKey, exchangeName, virtualHostName);
        PublishAccessCheck check = _publishAccessCheckCache.get(key);
        if (check == null)
        {
            check = new PublishAccessCheck(new ObjectProperties(virtualHostName, exchangeName, routingKey, immediate));
            _publishAccessCheckCache.putIfAbsent(key, check);
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

    public void authoriseUnbind(final BindingImpl binding)
    {
        if(! checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(UNBIND, EXCHANGE, new ObjectProperties(binding));
            }
        }))
        {
            throw new AccessControlException("Permission denied: unbinding " + binding.getBindingKey());
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
                synchronized (_plugins)
                {
                    AccessControl accessControl = ((AccessControlProvider)object).getAccessControl();
                    String pluginTypeName = getPluginTypeName(accessControl);

                    _plugins.put(pluginTypeName, accessControl);
                }
            }
            else if(newState == State.DELETED)
            {
                synchronized (_plugins)
                {
                    AccessControl control = ((AccessControlProvider)object).getAccessControl();
                    String pluginTypeName = getPluginTypeName(control);

                    // Remove the type->control mapping for this type key only if the
                    // given control is actually referred to.
                    if(_plugins.containsValue(control))
                    {
                        // If we are removing this control, check if another of the same
                        // type already exists on the broker and use it in instead.
                        AccessControl other = null;
                        Collection<AccessControlProvider<?>> providers = _broker.getAccessControlProviders();
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
                            _plugins.replace(pluginTypeName, control, other);
                        }
                        else
                        {
                            //No other was found, remove the type entirely
                            _plugins.remove(pluginTypeName);
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

    public static class PublishAccessCheckCacheEntry
    {
        private final boolean _immediate;
        private final String _routingKey;
        private final String _exchangeName;
        private final String _virtualHostName;

        public PublishAccessCheckCacheEntry(boolean immediate, String routingKey, String exchangeName, String virtualHostName)
        {
            super();
            _immediate = immediate;
            _routingKey = routingKey;
            _exchangeName = exchangeName;
            _virtualHostName = virtualHostName;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((_exchangeName == null) ? 0 : _exchangeName.hashCode());
            result = prime * result + (_immediate ? 1231 : 1237);
            result = prime * result + ((_routingKey == null) ? 0 : _routingKey.hashCode());
            result = prime * result + ((_virtualHostName == null) ? 0 : _virtualHostName.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (getClass() != obj.getClass())
            {
                return false;
            }
            PublishAccessCheckCacheEntry other = (PublishAccessCheckCacheEntry) obj;
            if (_exchangeName == null)
            {
                if (other._exchangeName != null)
                {
                    return false;
                }
            }
            else if (!_exchangeName.equals(other._exchangeName))
            {
                return false;
            }
            if (_immediate != other._immediate)
            {
                return false;
            }
            if (_routingKey == null)
            {
                if (other._routingKey != null)
                {
                    return false;
                }
            }
            else if (!_routingKey.equals(other._routingKey))
            {
                return false;
            }
            if (_virtualHostName == null)
            {
                if (other._virtualHostName != null)
                {
                    return false;
                }
            }
            else if (!_virtualHostName.equals(other._virtualHostName))
            {
                return false;
            }
            return true;
        }


    }
}
