/*
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
package org.apache.qpid.server.model.adapter;

import java.security.AccessControlException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.apache.qpid.server.model.*;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.group.GroupManager;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.util.MapValueConverter;

public class GroupProviderAdapter extends AbstractConfiguredObject<GroupProviderAdapter> implements GroupProvider<GroupProviderAdapter>
{
    private static Logger LOGGER = Logger.getLogger(GroupProviderAdapter.class);

    private final GroupManager _groupManager;
    private final Broker _broker;
    private Collection<String> _supportedAttributes;
    private AtomicReference<State> _state;

    public GroupProviderAdapter(UUID id, Broker broker, GroupManager groupManager, Map<String, Object> attributes, Collection<String> attributeNames)
    {
        super(id, null, null, broker.getTaskExecutor());

        if (groupManager == null)
        {
            throw new IllegalArgumentException("GroupManager must not be null");
        }
        _groupManager = groupManager;
        _broker = broker;
        _supportedAttributes = createSupportedAttributes(attributeNames);
        State state = MapValueConverter.getEnumAttribute(State.class, STATE, attributes, State.INITIALISING);
        _state = new AtomicReference<State>(state);
       addParent(Broker.class, broker);

       // set attributes now after all attribute names are known
       if (attributes != null)
       {
           for (String name : _supportedAttributes)
           {
               if (attributes.containsKey(name))
               {
                   changeAttribute(name, null, attributes.get(name));
               }
           }
       }
    }

    protected Collection<String> createSupportedAttributes(Collection<String> factoryAttributes)
    {
        List<String> attributesNames = new ArrayList<String>(Attribute.getAttributeNames(GroupProvider.class));
        if (factoryAttributes != null)
        {
            attributesNames.addAll(factoryAttributes);
        }

        return Collections.unmodifiableCollection(attributesNames);
    }

    @Override
    public String getName()
    {
        return (String)getAttribute(NAME);
    }

    @Override
    public String setName(String currentName, String desiredName)
            throws IllegalStateException, AccessControlException
    {
        return null;
    }

    @Override
    public State getState()
    {
        return _state.get();
    }

    @Override
    public boolean isDurable()
    {
        return true;
    }

    @Override
    public void setDurable(boolean durable) throws IllegalStateException,
            AccessControlException, IllegalArgumentException
    {
    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    @Override
    public LifetimePolicy setLifetimePolicy(LifetimePolicy expected,
            LifetimePolicy desired) throws IllegalStateException,
            AccessControlException, IllegalArgumentException
    {
        return null;
    }

    @Override
    public long getTimeToLive()
    {
        return 0;
    }

    @Override
    public long setTimeToLive(long expected, long desired)
            throws IllegalStateException, AccessControlException,
            IllegalArgumentException
    {
        return 0;
    }

    @Override
    public Statistics getStatistics()
    {
        return NoStatistics.getInstance();
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return _supportedAttributes;
    }

    @Override
    public Object getAttribute(String name)
    {
        if (CREATED.equals(name))
        {
            // TODO
        }
        else if (DURABLE.equals(name))
        {
            return true;
        }
        else if (ID.equals(name))
        {
            return getId();
        }
        else if (LIFETIME_POLICY.equals(name))
        {
            return LifetimePolicy.PERMANENT;
        }
        else if (STATE.equals(name))
        {
            return getState();
        }
        else if (TIME_TO_LIVE.equals(name))
        {
            // TODO
        }
        else if (UPDATED.equals(name))
        {
            // TODO
        }
        return super.getAttribute(name);
    }

    @Override
    public <C extends ConfiguredObject> C addChild(Class<C> childClass,
            Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        if (childClass == Group.class)
        {
            String groupName = (String) attributes.get(Group.NAME);

            getSecurityManager().authoriseGroupOperation(Operation.CREATE, groupName);
                _groupManager.createGroup(groupName);
                return (C) new GroupAdapter(groupName, getTaskExecutor());

        }

        throw new IllegalArgumentException(
                "This group provider does not support creating children of type: "
                        + childClass);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        if (clazz == Group.class)
        {
            Set<Principal> groups = _groupManager.getGroupPrincipals();
            Collection<Group> principals = new ArrayList<Group>(groups.size());
            for (Principal group : groups)
            {
                principals.add(new GroupAdapter(group.getName(), getTaskExecutor()));
            }
            return (Collection<C>) Collections
                    .unmodifiableCollection(principals);
        }
        else
        {
            return null;
        }
    }

    public GroupManager getGroupManager()
    {
        return _groupManager;
    }

    private SecurityManager getSecurityManager()
    {
        return _broker.getSecurityManager();
    }

    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        State state = _state.get();
        if (desiredState == State.ACTIVE)
        {
            if ((state == State.INITIALISING || state == State.QUIESCED || state == State.STOPPED)
                    && _state.compareAndSet(state, State.ACTIVE))
            {
                try
                {
                    _groupManager.open();
                    return true;
                }
                catch(RuntimeException e)
                {
                    _state.compareAndSet(State.ACTIVE, State.ERRORED);
                    if (_broker.isManagementMode())
                    {
                        LOGGER.warn("Failed to activate group provider: " + getName(), e);
                    }
                    else
                    {
                        throw e;
                    }
                }
            }
            else
            {
                throw new IllegalStateException("Cannot activate group provider in state: " + state);
            }
        }
        else if (desiredState == State.STOPPED)
        {
            if (_state.compareAndSet(state, State.STOPPED))
            {
                _groupManager.close();
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot stop group provider in state: " + state);
            }
        }
        else if (desiredState == State.DELETED)
        {
            if ((state == State.INITIALISING || state == State.ACTIVE || state == State.STOPPED || state == State.QUIESCED || state == State.ERRORED)
                    && _state.compareAndSet(state, State.DELETED))
            {
                _groupManager.close();
                _groupManager.onDelete();
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot delete group provider in state: " + state);
            }
        }
        else if (desiredState == State.QUIESCED)
        {
            if (state == State.INITIALISING && _state.compareAndSet(state, State.QUIESCED))
            {
                return true;
            }
        }
        return false;
    }

    public Set<Principal> getGroupPrincipalsForUser(String username)
    {
        return _groupManager.getGroupPrincipalsForUser(username);
    }

    @Override
    protected void childAdded(ConfiguredObject child)
    {
        // no-op, prevent storing groups in the broker store
    }

    @Override
    protected void childRemoved(ConfiguredObject child)
    {
        // no-op, as per above, groups are not in the store
    }

    @Override
    protected void authoriseSetDesiredState(State currentState, State desiredState) throws AccessControlException
    {
        if(desiredState == State.DELETED)
        {
            if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), GroupProvider.class, Operation.DELETE))
            {
                throw new AccessControlException("Deletion of groups provider is denied");
            }
        }
    }

    @Override
    protected void authoriseSetAttribute(String name, Object expected, Object desired) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), GroupProvider.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of group provider attributes is denied");
        }
    }

    @Override
    protected void authoriseSetAttributes(Map<String, Object> attributes) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), GroupProvider.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of group provider attributes is denied");
        }
    }

    @Override
    protected void changeAttributes(Map<String, Object> attributes)
    {
        throw new UnsupportedOperationException("Changing attributes on group providers is not supported.");
    }

    private class GroupAdapter extends AbstractConfiguredObject<GroupAdapter> implements Group<GroupAdapter>
    {
        private final String _group;

        public GroupAdapter(String group, TaskExecutor taskExecutor)
        {
            super(UUIDGenerator.generateGroupUUID(GroupProviderAdapter.this.getName(), group), taskExecutor);
            _group = group;

        }

        @Override
        public String getName()
        {
            return _group;
        }

        @Override
        public String setName(String currentName, String desiredName)
                throws IllegalStateException, AccessControlException
        {
            throw new IllegalStateException("Names cannot be updated");
        }

        @Override
        public State getState()
        {
            return State.ACTIVE;
        }

        @Override
        public boolean isDurable()
        {
            return true;
        }

        @Override
        public void setDurable(boolean durable) throws IllegalStateException,
                AccessControlException, IllegalArgumentException
        {
            throw new IllegalStateException("Durability cannot be updated");
        }

        @Override
        public LifetimePolicy getLifetimePolicy()
        {
            return LifetimePolicy.PERMANENT;
        }

        @Override
        public LifetimePolicy setLifetimePolicy(LifetimePolicy expected,
                LifetimePolicy desired) throws IllegalStateException,
                AccessControlException, IllegalArgumentException
        {
            throw new IllegalStateException("LifetimePolicy cannot be updated");
        }

        @Override
        public long getTimeToLive()
        {
            return 0;
        }

        @Override
        public long setTimeToLive(long expected, long desired)
                throws IllegalStateException, AccessControlException,
                IllegalArgumentException
        {
            throw new IllegalStateException("ttl cannot be updated");
        }

        @Override
        public Statistics getStatistics()
        {
            return NoStatistics.getInstance();
        }

        @Override
        public <C extends ConfiguredObject> Collection<C> getChildren(
                Class<C> clazz)
        {
            if (clazz == GroupMember.class)
            {
                Set<Principal> usersInGroup = _groupManager
                        .getUserPrincipalsForGroup(_group);
                Collection<GroupMember> members = new ArrayList<GroupMember>();
                for (Principal principal : usersInGroup)
                {
                    members.add(new GroupMemberAdapter(principal.getName(), getTaskExecutor()));
                }
                return (Collection<C>) Collections
                        .unmodifiableCollection(members);
            }
            else
            {
                return null;
            }

        }

        @Override
        public <C extends ConfiguredObject> C addChild(Class<C> childClass,
                Map<String, Object> attributes,
                ConfiguredObject... otherParents)
        {
            if (childClass == GroupMember.class)
            {
                String memberName = (String) attributes.get(GroupMember.NAME);

                getSecurityManager().authoriseGroupOperation(Operation.UPDATE, _group);

                _groupManager.addUserToGroup(memberName, _group);
                return (C) new GroupMemberAdapter(memberName, getTaskExecutor());

            }

            throw new IllegalArgumentException(
                    "This group provider does not support creating children of type: "
                            + childClass);
        }

        @Override
        public Collection<String> getAttributeNames()
        {
            return Attribute.getAttributeNames(Group.class);
        }

        @Override
        public Object getAttribute(String name)
        {
            if (ID.equals(name))
            {
                return getId();
            }
            else if (NAME.equals(name))
            {
                return getName();
            }
            return super.getAttribute(name);
        }

        @Override
        protected boolean setState(State currentState, State desiredState)
                throws IllegalStateTransitionException, AccessControlException
        {
            if (desiredState == State.DELETED)
            {
                getSecurityManager().authoriseGroupOperation(Operation.DELETE, _group);
                _groupManager.removeGroup(_group);
                return true;
            }

            return false;
        }

        @Override
        public Object setAttribute(final String name, final Object expected, final Object desired) throws IllegalStateException,
                AccessControlException, IllegalArgumentException
        {
            throw new UnsupportedOperationException("Changing attributes on group is not supported.");
        }

        @Override
        public void setAttributes(final Map<String, Object> attributes) throws IllegalStateException, AccessControlException,
                IllegalArgumentException
        {
            throw new UnsupportedOperationException("Changing attributes on group is not supported.");
        }

        private class GroupMemberAdapter extends AbstractConfiguredObject<GroupMemberAdapter> implements
                GroupMember<GroupMemberAdapter>
        {
            private String _memberName;

            public GroupMemberAdapter(String memberName, TaskExecutor taskExecutor)
            {
                super(UUIDGenerator.generateGroupMemberUUID(GroupProviderAdapter.this.getName(), _group, memberName), taskExecutor);
                _memberName = memberName;
            }

            @Override
            public Collection<String> getAttributeNames()
            {
                return Attribute.getAttributeNames(GroupMember.class);
            }

            @Override
            public Object getAttribute(String name)
            {
                if (ID.equals(name))
                {
                    return getId();
                }
                else if (NAME.equals(name))
                {
                    return getName();
                }
                return super.getAttribute(name);
            }

            @Override
            public String getName()
            {
                return _memberName;
            }

            @Override
            public String setName(String currentName, String desiredName)
                    throws IllegalStateException, AccessControlException
            {
                return null;
            }

            @Override
            public State getState()
            {
                return null;
            }

            @Override
            public boolean isDurable()
            {
                return false;
            }

            @Override
            public void setDurable(boolean durable)
                    throws IllegalStateException, AccessControlException,
                    IllegalArgumentException
            {
            }

            @Override
            public LifetimePolicy getLifetimePolicy()
            {
                return null;
            }

            @Override
            public LifetimePolicy setLifetimePolicy(LifetimePolicy expected,
                    LifetimePolicy desired) throws IllegalStateException,
                    AccessControlException, IllegalArgumentException
            {
                return null;
            }

            @Override
            public long getTimeToLive()
            {
                return 0;
            }

            @Override
            public long setTimeToLive(long expected, long desired)
                    throws IllegalStateException, AccessControlException,
                    IllegalArgumentException
            {
                return 0;
            }

            @Override
            public Statistics getStatistics()
            {
                return NoStatistics.getInstance();
            }

            @Override
            public <C extends ConfiguredObject> Collection<C> getChildren(
                    Class<C> clazz)
            {
                return null;
            }

            @Override
            public <C extends ConfiguredObject> C createChild(
                    Class<C> childClass, Map<String, Object> attributes,
                    ConfiguredObject... otherParents)
            {
                return null;
            }

            @Override
            protected boolean setState(State currentState, State desiredState)
                    throws IllegalStateTransitionException,
                    AccessControlException
            {
                if (desiredState == State.DELETED)
                {
                    getSecurityManager().authoriseGroupOperation(Operation.UPDATE, _group);

                    _groupManager.removeUserFromGroup(_memberName, _group);
                    return true;

                }
                return false;
            }

            @Override
            public Object setAttribute(final String name, final Object expected, final Object desired) throws IllegalStateException,
                    AccessControlException, IllegalArgumentException
            {
                throw new UnsupportedOperationException("Changing attributes on group member is not supported.");
            }

            @Override
            public void setAttributes(final Map<String, Object> attributes) throws IllegalStateException, AccessControlException,
                    IllegalArgumentException
            {
                throw new UnsupportedOperationException("Changing attributes on group member is not supported.");
            }
        }
    }


}
