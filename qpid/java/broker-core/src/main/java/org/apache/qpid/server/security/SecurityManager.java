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
import static org.apache.qpid.server.security.access.ObjectType.METHOD;
import static org.apache.qpid.server.security.access.ObjectType.QUEUE;
import static org.apache.qpid.server.security.access.ObjectType.USER;
import static org.apache.qpid.server.security.access.Operation.ACCESS_LOGS;
import static org.apache.qpid.server.security.access.Operation.PUBLISH;
import static org.apache.qpid.server.security.access.Operation.PURGE;

import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.security.auth.Subject;

import org.apache.qpid.server.model.AccessControlProvider;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.Consumer;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.ExclusivityPolicy;
import org.apache.qpid.server.model.Group;
import org.apache.qpid.server.model.GroupMember;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.RemoteReplicationNode;
import org.apache.qpid.server.model.Session;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostAlias;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.queue.QueueConsumer;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectProperties.Property;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.access.OperationLoggingDetails;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.security.auth.TaskPrincipal;

public class SecurityManager
{

    private static final Subject SYSTEM = new Subject(true,
                                                     Collections.singleton(new SystemPrincipal()),
                                                     Collections.emptySet(),
                                                     Collections.emptySet());

    private final boolean _managementMode;
    private final ConfiguredObject<?> _aclProvidersParent;

    private final ConcurrentMap<PublishAccessCheckCacheEntry, PublishAccessCheck> _publishAccessCheckCache = new ConcurrentHashMap<>();

    public SecurityManager(ConfiguredObject<?> aclProvidersParent, boolean managementMode)
    {
        _managementMode = managementMode;
        _aclProvidersParent = aclProvidersParent;
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
        if(isSystemProcess() || _managementMode)
        {
            return true;
        }


        Collection<AccessControlProvider> accessControlProviders = _aclProvidersParent.getChildren(AccessControlProvider.class);
        if(accessControlProviders != null && !accessControlProviders.isEmpty())
        {
            AccessControlProvider<?> accessControlProvider = accessControlProviders.iterator().next();
            if (accessControlProvider != null
                && accessControlProvider.getState() == State.ACTIVE
                && accessControlProvider.getAccessControl() != null)
            {
                Result remaining = checker.allowed(accessControlProvider.getAccessControl());
                if (remaining == Result.DEFER)
                {
                    remaining = accessControlProvider.getAccessControl().getDefault();
                }
                if (remaining == Result.DENIED)
                {
                    return false;
                }
            }
        }
        // getting here means either allowed or abstained from all plugins
        return true;
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

    public void authoriseCreateConnection(final AMQConnectionModel connection)
    {
        String virtualHostName = connection.getVirtualHostName();
        ObjectProperties properties = new ObjectProperties(virtualHostName);
        properties.put(Property.VIRTUALHOST_NAME, virtualHostName);
        if (!checkAllPlugins(ObjectType.VIRTUALHOST,  properties, Operation.ACCESS))
        {
            throw new AccessControlException("Permission denied: " + virtualHostName);
        }
    }

    public void authoriseCreate(ConfiguredObject<?> object)
    {
        authorise(Operation.CREATE, object);
    }

    public void authoriseUpdate(ConfiguredObject<?> configuredObject)
    {
        authorise(Operation.UPDATE, configuredObject);
    }

    public void authoriseDelete(ConfiguredObject<?> configuredObject)
    {
        authorise(Operation.DELETE, configuredObject);
    }

    public void authorise(Operation operation, ConfiguredObject<?> configuredObject)
    {
        // If we are running as SYSTEM then no ACL checking
        if(isSystemProcess() || _managementMode)
        {
            return;
        }

        if (isAllowedOperation(operation, configuredObject))
        {
            // creation of remote replication node is out of control for user of this broker
            return;
        }

        Class<? extends ConfiguredObject> categoryClass = configuredObject.getCategoryClass();
        ObjectType objectType = getACLObjectTypeManagingConfiguredObjectOfCategory(categoryClass);
        if (objectType == null)
        {
            throw new IllegalArgumentException("Cannot identify object type for category " + categoryClass );
        }

        ObjectProperties properties = getACLObjectProperties(configuredObject, operation);
        Operation authoriseOperation = validateAuthoriseOperation(operation, categoryClass);
        if(!checkAllPlugins(objectType, properties, authoriseOperation))
        {
            String objectName = (String)configuredObject.getAttribute(ConfiguredObject.NAME);
            StringBuilder exceptionMessage = new StringBuilder(String.format("Permission %s %s is denied for : %s %s '%s'",
                    authoriseOperation.name(), objectType.name(), operation.name(), categoryClass.getSimpleName(), objectName ));
            Model model = getModel();

            Collection<Class<? extends ConfiguredObject>> parentClasses = model.getParentTypes(categoryClass);
            if (parentClasses != null)
            {
                exceptionMessage.append(" on");
                for (Class<? extends ConfiguredObject> parentClass: parentClasses)
                {
                    String objectCategory = parentClass.getSimpleName();
                    ConfiguredObject<?> parent = configuredObject.getParent(parentClass);
                    exceptionMessage.append(" ").append(objectCategory);
                    if (parent != null)
                    {
                        exceptionMessage.append(" '").append(parent.getAttribute(ConfiguredObject.NAME)).append("'");
                    }
                }
            }
            throw new AccessControlException(exceptionMessage.toString());
        }
    }

    private boolean isAllowedOperation(Operation operation, ConfiguredObject<?> configuredObject)
    {
        if (configuredObject instanceof Session && (operation == Operation.CREATE || operation == Operation.UPDATE
                || operation ==  Operation.DELETE))
        {
            return true;

        }

        if (configuredObject instanceof Consumer && (operation == Operation.UPDATE || operation ==  Operation.DELETE))
        {
            return true;
        }

        if (configuredObject instanceof Connection && (operation == Operation.UPDATE || operation ==  Operation.DELETE))
        {
            return true;
        }

        return false;
    }

    private Model getModel()
    {
        return _aclProvidersParent.getModel();
    }

    private boolean checkAllPlugins(final ObjectType objectType, final ObjectProperties properties, final Operation authoriseOperation)
    {
        return checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(authoriseOperation, objectType, properties);
            }
        });
    }

    private Operation validateAuthoriseOperation(Operation operation, Class<? extends ConfiguredObject> category)
    {
        if (operation == Operation.CREATE || operation == Operation.UPDATE)
        {
            if (Binding.class.isAssignableFrom(category))
            {
                // CREATE BINDING is transformed into BIND EXCHANGE rule
                return Operation.BIND;
            }
            else if (Consumer.class.isAssignableFrom(category))
            {
                // CREATE CONSUMER is transformed into CONSUME QUEUE rule
                return Operation.CONSUME;
            }
            else if (GroupMember.class.isAssignableFrom(category))
            {
                // CREATE GROUP MEMBER is transformed into UPDATE GROUP rule
                return Operation.UPDATE;
            }
            else if (isBrokerOrBrokerChildOrPreferencesProvider(category))
            {
                // CREATE/UPDATE broker child is transformed into CONFIGURE BROKER rule
                return Operation.CONFIGURE;
            }
        }
        else if (operation == Operation.DELETE)
        {
            if (Binding.class.isAssignableFrom(category))
            {
                // DELETE BINDING is transformed into UNBIND EXCHANGE rule
                return Operation.UNBIND;
            }
            else if (isBrokerOrBrokerChildOrPreferencesProvider(category))
            {
                // DELETE broker child is transformed into CONFIGURE BROKER rule
                return Operation.CONFIGURE;

            }
            else if (GroupMember.class.isAssignableFrom(category))
            {
                // DELETE GROUP MEMBER is transformed into UPDATE GROUP rule
                return Operation.UPDATE;
            }
        }
        return operation;
    }

    private boolean isBrokerOrBrokerChildOrPreferencesProvider(Class<? extends ConfiguredObject> category)
    {
        return Broker.class.isAssignableFrom(category) ||
               PreferencesProvider.class.isAssignableFrom(category) ||
               ( !VirtualHostNode.class.isAssignableFrom(category) && getModel().getChildTypes(Broker.class).contains(category));
    }

    private ObjectProperties getACLObjectProperties(ConfiguredObject<?> configuredObject, Operation configuredObjectOperation)
    {
        String objectName = (String)configuredObject.getAttribute(ConfiguredObject.NAME);
        Class<? extends ConfiguredObject> configuredObjectType = configuredObject.getCategoryClass();
        ObjectProperties properties = new ObjectProperties(objectName);
        if (configuredObject instanceof Binding)
        {
            Exchange<?> exchange = (Exchange<?>)configuredObject.getParent(Exchange.class);
            Queue<?> queue = (Queue<?>)configuredObject.getParent(Queue.class);
            properties.setName((String)exchange.getAttribute(Exchange.NAME));
            properties.put(Property.QUEUE_NAME, (String)queue.getAttribute(Queue.NAME));
            properties.put(Property.ROUTING_KEY, (String)configuredObject.getAttribute(Binding.NAME));
            properties.put(Property.VIRTUALHOST_NAME, (String)queue.getParent(VirtualHost.class).getAttribute(VirtualHost.NAME));

            // The temporary attribute (inherited from the binding's queue) seems to exist to allow the user to
            // express rules about the binding of temporary queues (whose names cannot be predicted).
            properties.put(Property.TEMPORARY, queue.getAttribute(Queue.LIFETIME_POLICY) != LifetimePolicy.PERMANENT);
            properties.put(Property.DURABLE, (Boolean)queue.getAttribute(Queue.DURABLE));
        }
        else if (configuredObject instanceof Queue)
        {
            setQueueProperties(configuredObject, properties);
        }
        else if (configuredObject instanceof Exchange)
        {
            Object lifeTimePolicy = configuredObject.getAttribute(ConfiguredObject.LIFETIME_POLICY);
            properties.put(Property.AUTO_DELETE, lifeTimePolicy != LifetimePolicy.PERMANENT);
            properties.put(Property.TEMPORARY, lifeTimePolicy != LifetimePolicy.PERMANENT);
            properties.put(Property.DURABLE, (Boolean) configuredObject.getAttribute(ConfiguredObject.DURABLE));
            properties.put(Property.TYPE, (String) configuredObject.getAttribute(Exchange.TYPE));
            VirtualHost virtualHost = configuredObject.getParent(VirtualHost.class);
            properties.put(Property.VIRTUALHOST_NAME, (String)virtualHost.getAttribute(virtualHost.NAME));
        }
        else if (configuredObject instanceof QueueConsumer)
        {
            Queue<?> queue = (Queue<?>)configuredObject.getParent(Queue.class);
            setQueueProperties(queue, properties);
        }
        else if (isBrokerOrBrokerChildOrPreferencesProvider(configuredObjectType))
        {
            String description = String.format("%s %s '%s'",
                    configuredObjectOperation == null? null : configuredObjectOperation.name().toLowerCase(),
                    configuredObjectType == null ? null : configuredObjectType.getSimpleName().toLowerCase(),
                    objectName);
            properties = new OperationLoggingDetails(description);
        }
        return properties;
    }

    private void setQueueProperties(ConfiguredObject<?>  queue, ObjectProperties properties)
    {
        properties.setName((String)queue.getAttribute(Exchange.NAME));
        Object lifeTimePolicy = queue.getAttribute(ConfiguredObject.LIFETIME_POLICY);
        properties.put(Property.AUTO_DELETE, lifeTimePolicy!= LifetimePolicy.PERMANENT);
        properties.put(Property.TEMPORARY, lifeTimePolicy != LifetimePolicy.PERMANENT);
        properties.put(Property.DURABLE, (Boolean)queue.getAttribute(ConfiguredObject.DURABLE));
        properties.put(Property.EXCLUSIVE, queue.getAttribute(Queue.EXCLUSIVE) != ExclusivityPolicy.NONE);
        Object alternateExchange = queue.getAttribute(Queue.ALTERNATE_EXCHANGE);
        if (alternateExchange != null)
        {
            String name = alternateExchange instanceof ConfiguredObject ?
                    (String)((ConfiguredObject)alternateExchange).getAttribute(ConfiguredObject.NAME) :
                    String.valueOf(alternateExchange);
            properties.put(Property.ALTERNATE,name);
        }
        String owner = (String)queue.getAttribute(Queue.OWNER);
        if (owner != null)
        {
            properties.put(Property.OWNER, owner);
        }
        VirtualHost virtualHost = queue.getParent(VirtualHost.class);
        properties.put(Property.VIRTUALHOST_NAME, (String)virtualHost.getAttribute(virtualHost.NAME));
    }

    private ObjectType getACLObjectTypeManagingConfiguredObjectOfCategory(Class<? extends ConfiguredObject> category)
    {
        if (Binding.class.isAssignableFrom(category))
        {
            return ObjectType.EXCHANGE;
        }
        else if (VirtualHostNode.class.isAssignableFrom(category))
        {
            return ObjectType.VIRTUALHOSTNODE;
        }
        else if (isBrokerOrBrokerChildOrPreferencesProvider(category))
        {
            return ObjectType.BROKER;
        }
        else if (Group.class.isAssignableFrom(category))
        {
            return ObjectType.GROUP;
        }
        else if (GroupMember.class.isAssignableFrom(category))
        {
            // UPDATE GROUP
            return ObjectType.GROUP;
        }
        else if (User.class.isAssignableFrom(category))
        {
            return ObjectType.USER;
        }
        else if (VirtualHost.class.isAssignableFrom(category))
        {
            return ObjectType.VIRTUALHOST;
        }
        else if (VirtualHostAlias.class.isAssignableFrom(category))
        {
            return ObjectType.VIRTUALHOST;
        }
        else if (Queue.class.isAssignableFrom(category))
        {
            return ObjectType.QUEUE;
        }
        else if (Exchange.class.isAssignableFrom(category))
        {
            return ObjectType.EXCHANGE;
        }
        else if (Connection.class.isAssignableFrom(category))
        {
            // ACCESS VIRTUALHOST
            return ObjectType.VIRTUALHOST;
        }
        else if (Session.class.isAssignableFrom(category))
        {
            // PUBLISH EXCHANGE
            return ObjectType.EXCHANGE;
        }
        else if (Consumer.class.isAssignableFrom(category))
        {
            // CONSUME QUEUE
            return ObjectType.QUEUE;
        }
        else if (RemoteReplicationNode.class.isAssignableFrom(category))
        {
            // VHN permissions apply to remote nodes
            return ObjectType.VIRTUALHOSTNODE;
        }
        return null;
    }

    public void authoriseUserUpdate(final String userName)
    {
        AuthenticatedPrincipal principal = getCurrentUser();
        if (principal != null && principal.getName().equals(userName))
        {
            // allow user to update its own data
            return;
        }

        final Operation operation = Operation.UPDATE;
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

    public void authorisePurge(final Queue queue)
    {
        final ObjectProperties properties = new ObjectProperties();
        setQueueProperties(queue, properties);
        if(!checkAllPlugins(new AccessCheck()
        {
            Result allowed(AccessControl plugin)
            {
                return plugin.authorise(PURGE, QUEUE, properties);
            }
        }))
        {
            throw new AccessControlException("Permission denied: queue " + queue.getName());
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
