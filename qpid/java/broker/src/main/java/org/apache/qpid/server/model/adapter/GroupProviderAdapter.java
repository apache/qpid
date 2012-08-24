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
import java.util.Map;
import java.util.Set;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Group;
import org.apache.qpid.server.model.GroupMember;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.IllegalStateTransitionException;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.group.GroupManager;

public class GroupProviderAdapter extends AbstractAdapter implements
        GroupProvider
{
    private final GroupManager _groupManager;

    protected GroupProviderAdapter(GroupManager groupManager)
    {
        super(UUIDGenerator.generateRandomUUID());

        if (groupManager == null)
        {
            throw new IllegalArgumentException("GroupManager must not be null");
        }
        _groupManager = groupManager;
    }

    public static GroupProviderAdapter createGroupProviderAdapter(
            BrokerAdapter brokerAdapter, GroupManager groupManager)
    {
        final GroupProviderAdapter groupProviderAdapter = new GroupProviderAdapter(
                groupManager);
        groupProviderAdapter.addParent(Broker.class, brokerAdapter);
        return groupProviderAdapter;
    }

    @Override
    public String getName()
    {
        return _groupManager.getClass().getSimpleName();
    }

    @Override
    public String setName(String currentName, String desiredName)
            throws IllegalStateException, AccessControlException
    {
        return null;
    }

    @Override
    public State getActualState()
    {
        return null;
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
        return GroupProvider.AVAILABLE_ATTRIBUTES;
    }

    @Override
    public Object getAttribute(String name)
    {
        if (TYPE.equals(name))
        {
            return getName();
        }
        else if (CREATED.equals(name))
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
        else if (NAME.equals(name))
        {
            return getName();
        }
        else if (STATE.equals(name))
        {
            return State.ACTIVE; // TODO
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
    public <C extends ConfiguredObject> C createChild(Class<C> childClass,
            Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        if (childClass == Group.class)
        {
            String groupName = (String) attributes.get(Group.NAME);

            if (getSecurityManager().authoriseGroupOperation(Operation.CREATE, groupName))
            {
                _groupManager.createGroup(groupName);
                return (C) new GroupAdapter(groupName);
            }
            else
            {
                throw new AccessControlException("Do not have permission" +
                		" to create new group");
            }
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
                principals.add(new GroupAdapter(group.getName()));
            }
            return (Collection<C>) Collections
                    .unmodifiableCollection(principals);
        }
        else
        {
            return null;
        }
    }

    private org.apache.qpid.server.security.SecurityManager getSecurityManager()
    {
        return ApplicationRegistry.getInstance().getSecurityManager();
    }

    private class GroupAdapter extends AbstractAdapter implements Group
    {
        private final String _group;

        public GroupAdapter(String group)
        {
            super(UUIDGenerator.generateGroupUUID(
                    GroupProviderAdapter.this.getName(), group));
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
        public State getActualState()
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
                    members.add(new GroupMemberAdapter(principal.getName()));
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
        public <C extends ConfiguredObject> C createChild(Class<C> childClass,
                Map<String, Object> attributes,
                ConfiguredObject... otherParents)
        {
            if (childClass == GroupMember.class)
            {
                String memberName = (String) attributes.get(GroupMember.NAME);

                if (getSecurityManager().authoriseGroupOperation(Operation.UPDATE, _group))
                {
                    _groupManager.addUserToGroup(memberName, _group);
                    return (C) new GroupMemberAdapter(memberName);
                }
                else
                {
                    throw new AccessControlException("Do not have permission" +
                    		" to add new group member");
                }
            }

            throw new IllegalArgumentException(
                    "This group provider does not support creating children of type: "
                            + childClass);
        }

        @Override
        public Collection<String> getAttributeNames()
        {
            return Group.AVAILABLE_ATTRIBUTES;
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
        public Object setAttribute(String name, Object expected, Object desired)
                throws IllegalStateException, AccessControlException,
                IllegalArgumentException
        {
            return super.setAttribute(name, expected, desired);
        }

        @Override
        public State setDesiredState(State currentState, State desiredState)
                throws IllegalStateTransitionException, AccessControlException
        {
            if (desiredState == State.DELETED)
            {
                if (getSecurityManager().authoriseGroupOperation(Operation.DELETE, _group))
                {
                    _groupManager.removeGroup(_group);
                    return State.DELETED;
                }
                else
                {
                    throw new AccessControlException("Do not have permission" +
                    " to delete group");
                }
            }

            return super.setDesiredState(currentState, desiredState);
        }

        private class GroupMemberAdapter extends AbstractAdapter implements
                GroupMember
        {
            private String _memberName;

            public GroupMemberAdapter(String memberName)
            {
                super(UUIDGenerator
                        .generateGroupMemberUUID(
                                GroupProviderAdapter.this.getName(), _group,
                                memberName));
                _memberName = memberName;
            }

            @Override
            public Collection<String> getAttributeNames()
            {
                return GroupMember.AVAILABLE_ATTRIBUTES;
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
            public State getActualState()
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
            public State setDesiredState(State currentState, State desiredState)
                    throws IllegalStateTransitionException,
                    AccessControlException
            {
                if (desiredState == State.DELETED)
                {
                    if (getSecurityManager().authoriseGroupOperation(Operation.UPDATE, _group))
                    {
                        _groupManager.removeUserFromGroup(_memberName, _group);
                        return State.DELETED;
                    }
                    else
                    {
                        throw new AccessControlException("Do not have permission" +
                        " to remove group member");
                    }
                }
                
                return super.setDesiredState(currentState, desiredState);
            }

        }
    }
}
