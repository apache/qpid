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

import java.io.File;
import java.io.IOException;
import java.security.AccessControlException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Group;
import org.apache.qpid.server.model.GroupMember;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.group.FileGroupDatabase;
import org.apache.qpid.server.security.group.GroupPrincipal;
import org.apache.qpid.server.util.MapValueConverter;

public class FileBasedGroupProviderImpl
        extends AbstractConfiguredObject<FileBasedGroupProviderImpl> implements FileBasedGroupProvider<FileBasedGroupProviderImpl>
{
    public static final String RESOURCE_BUNDLE = "org.apache.qpid.server.security.group.FileGroupProviderAttributeDescriptions";
    public static final String GROUP_FILE_PROVIDER_TYPE = "GroupFile";
    private static Logger LOGGER = Logger.getLogger(FileBasedGroupProviderImpl.class);

    private final Broker<?> _broker;
    private AtomicReference<State> _state;

    private FileGroupDatabase _groupDatabase;

    @ManagedAttributeField
    private String _path;

    @ManagedObjectFactoryConstructor
    public FileBasedGroupProviderImpl(Map<String, Object> attributes,
                                      Broker broker)
    {
        super(parentsMap(broker), attributes);


        _broker = broker;

        State state = MapValueConverter.getEnumAttribute(State.class, STATE, attributes, State.UNINITIALIZED);
        _state = new AtomicReference<State>(state);
    }

    public void onValidate()
    {
        Collection<GroupProvider<?>> groupProviders = _broker.getGroupProviders();
        for(GroupProvider<?> provider : groupProviders)
        {
            if(provider instanceof FileBasedGroupProvider && provider != this)
            {
                try
                {
                    if(new File(getPath()).getCanonicalPath().equals(new File(((FileBasedGroupProvider)provider).getPath()).getCanonicalPath()))
                    {
                        throw new IllegalConfigurationException("Cannot have two group providers using the same file: " + getPath());
                    }
                }
                catch (IOException e)
                {
                    throw new IllegalArgumentException("Invalid path", e);
                }
            }
        }
        if(!isDurable())
        {
            throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
        }
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);
        if(changedAttributes.contains(DURABLE) && !proxyForValidation.isDurable())
        {
            throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
        }
        if(changedAttributes.contains(PATH))
        {
            throw new IllegalArgumentException("Cannot change the path");
        }
    }
    protected void onOpen()
    {
        super.onOpen();
        if(_groupDatabase == null)
        {
            _groupDatabase = new FileGroupDatabase();
            try
            {
                _groupDatabase.setGroupFile(getPath());
            }
            catch (IOException e)
            {
                setState(State.ERRORED);
                LOGGER.warn(("Unable to open preferences file at " + _path));
            }
        }
        Set<Principal> groups = getGroupPrincipals();
        Collection<Group> principals = new ArrayList<Group>(groups.size());
        for (Principal group : groups)
        {
            Map<String,Object> attrMap = new HashMap<String, Object>();
            UUID id = UUID.randomUUID();
            attrMap.put(Group.ID, id);
            attrMap.put(Group.NAME, group.getName());
            GroupAdapter groupAdapter = new GroupAdapter(attrMap);
            principals.add(groupAdapter);
            groupAdapter.registerWithParents();
            groupAdapter.open();
        }

    }

    @Override
    protected void onCreate()
    {
        super.onCreate();
        _groupDatabase = new FileGroupDatabase();

        File file = new File(_path);
        if (!file.exists())
        {
            File parent = file.getParentFile();
            if (!parent.exists())
            {
                parent.mkdirs();
            }
            if (parent.exists())
            {
                try
                {
                    file.createNewFile();
                }
                catch (IOException e)
                {
                    throw new IllegalConfigurationException("Cannot create group file");
                }
            }
            else
            {
                throw new IllegalConfigurationException("Cannot create group file");
            }
        }
        try
        {
            _groupDatabase.setGroupFile(getPath());
        }
        catch (IOException e)
        {
            setState(State.ERRORED);
            LOGGER.warn(("Unable to open preferences file at " + _path));
        }


    }

    @Override
    public String getPath()
    {
        return _path;
    }

    @Override
    public State getState()
    {
        return _state.get();
    }


    @Override
    public Object getAttribute(String name)
    {
        if (STATE.equals(name))
        {
            return getState();
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

            _groupDatabase.createGroup(groupName);

            Map<String,Object> attrMap = new HashMap<String, Object>();
            UUID id = UUID.randomUUID();
            attrMap.put(Group.ID, id);
            attrMap.put(Group.NAME, groupName);
            GroupAdapter groupAdapter = new GroupAdapter(attrMap);
            groupAdapter.create();
            return (C) groupAdapter;

        }

        throw new IllegalArgumentException(
                "This group provider does not support creating children of type: "
                        + childClass);
    }

    private Set<Principal> getGroupPrincipals()
    {

        Set<String> groups = _groupDatabase == null ? Collections.<String>emptySet() : _groupDatabase.getAllGroups();
        if (groups.isEmpty())
        {
            return Collections.emptySet();
        }
        else
        {
            Set<Principal> principals = new HashSet<Principal>();
            for (String groupName : groups)
            {
                principals.add(new GroupPrincipal(groupName));
            }
            return principals;
        }
    }


    private SecurityManager getSecurityManager()
    {
        return _broker.getSecurityManager();
    }

    @StateTransition( currentState = { State.UNINITIALIZED, State.QUIESCED }, desiredState = State.ACTIVE )
    private void activate()
    {
        try
        {
            _groupDatabase.setGroupFile(getPath());
            _state.set(State.ACTIVE);
        }
        catch(IOException | RuntimeException e)
        {
            _state.set(State.ERRORED);
            if (_broker.isManagementMode())
            {
                LOGGER.warn("Failed to activate group provider: " + getName(), e);
            }
        }
    }

    @StateTransition( currentState = { State.QUIESCED, State.ACTIVE, State.ERRORED}, desiredState = State.DELETED )
    private void doDelete()
    {
        File file = new File(getPath());
        if (file.exists())
        {
            if (!file.delete())
            {
                throw new IllegalConfigurationException("Cannot delete group file");
            }
        }

        deleted();
        _state.set(State.DELETED);
    }

    @StateTransition( currentState = State.UNINITIALIZED, desiredState = State.QUIESCED)
    private void startQuiesced()
    {
        _state.set(State.QUIESCED);
    }

    public Set<Principal> getGroupPrincipalsForUser(String username)
    {
        Set<String> groups = _groupDatabase.getGroupsForUser(username);
        if (groups.isEmpty())
        {
            return Collections.emptySet();
        }
        else
        {
            Set<Principal> principals = new HashSet<Principal>();
            for (String groupName : groups)
            {
                principals.add(new GroupPrincipal(groupName));
            }
            return principals;
        }
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
    protected void authoriseSetDesiredState(State desiredState) throws AccessControlException
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
    protected void authoriseSetAttributes(ConfiguredObject<?> modified, Set<String> attributes) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), GroupProvider.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of group provider attributes is denied");
        }
    }

    private class GroupAdapter extends AbstractConfiguredObject<GroupAdapter> implements Group<GroupAdapter>
    {
        private State _state = State.UNINITIALIZED;

        public GroupAdapter(Map<String, Object> attributes)
        {
            super(parentsMap(FileBasedGroupProviderImpl.this), attributes);
        }


        @Override
        public State getState()
        {
            return _state;
        }


        @Override
        public void onValidate()
        {
            super.onValidate();
            if(!isDurable())
            {
                throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
            }
        }

        @StateTransition( currentState = State.UNINITIALIZED, desiredState = State.ACTIVE )
        private void activate()
        {
            _state = State.ACTIVE;
        }

        @Override
        protected void onOpen()
        {
            super.onOpen();
            Set<Principal> usersInGroup = getUserPrincipalsForGroup(getName());
            Collection<GroupMember> members = new ArrayList<GroupMember>();
            for (Principal principal : usersInGroup)
            {
                UUID id = UUID.randomUUID();
                Map<String,Object> attrMap = new HashMap<String, Object>();
                attrMap.put(GroupMember.ID,id);
                attrMap.put(GroupMember.NAME, principal.getName());
                GroupMemberAdapter groupMemberAdapter = new GroupMemberAdapter(attrMap);
                groupMemberAdapter.registerWithParents();
                groupMemberAdapter.open();
                members.add(groupMemberAdapter);
            }
        }

        @Override
        protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
        {
            super.validateChange(proxyForValidation, changedAttributes);
            if(changedAttributes.contains(DURABLE) && !proxyForValidation.isDurable())
            {
                throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
            }
        }

        private Set<Principal> getUserPrincipalsForGroup(String group)
        {
            Set<String> users = _groupDatabase.getUsersInGroup(group);
            if (users.isEmpty())
            {
                return Collections.emptySet();
            }
            else
            {
                Set<Principal> principals = new HashSet<Principal>();
                for (String user : users)
                {
                    principals.add(new UsernamePrincipal(user));
                }
                return principals;
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

                getSecurityManager().authoriseGroupOperation(Operation.UPDATE, getName());

                _groupDatabase.addUserToGroup(memberName, getName());
                UUID id = UUID.randomUUID();
                Map<String,Object> attrMap = new HashMap<String, Object>();
                attrMap.put(GroupMember.ID,id);
                attrMap.put(GroupMember.NAME, memberName);
                GroupMemberAdapter groupMemberAdapter = new GroupMemberAdapter(attrMap);
                groupMemberAdapter.create();
                return (C) groupMemberAdapter;

            }

            throw new IllegalArgumentException(
                    "This group provider does not support creating children of type: "
                            + childClass);
        }

        @StateTransition( currentState = State.ACTIVE, desiredState = State.DELETED )
        private void doDelete()
        {
            getSecurityManager().authoriseGroupOperation(Operation.DELETE, getName());
            _groupDatabase.removeGroup(getName());
            deleted();
            _state = State.DELETED;
        }

        private class GroupMemberAdapter extends AbstractConfiguredObject<GroupMemberAdapter> implements
                GroupMember<GroupMemberAdapter>
        {

            private State _state = State.UNINITIALIZED;

            public GroupMemberAdapter(Map<String, Object> attrMap)
            {
                // TODO - need to relate to the User object
                super(parentsMap(GroupAdapter.this),attrMap);
            }


            @Override
            public void onValidate()
            {
                super.onValidate();
                if(!isDurable())
                {
                    throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
                }
            }

            @Override
            protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
            {
                super.validateChange(proxyForValidation, changedAttributes);
                if(changedAttributes.contains(DURABLE) && !proxyForValidation.isDurable())
                {
                    throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
                }
            }

            @Override
            public State getState()
            {
                return _state;
            }

            @Override
            public <C extends ConfiguredObject> Collection<C> getChildren(
                    Class<C> clazz)
            {
                return Collections.emptySet();
            }

            @StateTransition(currentState = State.UNINITIALIZED, desiredState = State.ACTIVE)
            private void activate()
            {
                _state = State.ACTIVE;
            }

            @StateTransition(currentState = State.ACTIVE, desiredState = State.DELETED)
            private void doDelete()
            {
                getSecurityManager().authoriseGroupOperation(Operation.UPDATE, GroupAdapter.this.getName());

                _groupDatabase.removeUserFromGroup(getName(), GroupAdapter.this.getName());
                deleted();
                _state = State.DELETED;
            }

        }
    }


}
