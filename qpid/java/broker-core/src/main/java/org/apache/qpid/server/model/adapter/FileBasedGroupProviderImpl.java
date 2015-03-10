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
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.log4j.Logger;

import org.apache.qpid.server.configuration.BrokerProperties;
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
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.group.FileGroupDatabase;
import org.apache.qpid.server.security.group.GroupPrincipal;
import org.apache.qpid.server.util.FileHelper;

public class FileBasedGroupProviderImpl
        extends AbstractConfiguredObject<FileBasedGroupProviderImpl> implements FileBasedGroupProvider<FileBasedGroupProviderImpl>
{
    public static final String GROUP_FILE_PROVIDER_TYPE = "GroupFile";
    private static Logger LOGGER = Logger.getLogger(FileBasedGroupProviderImpl.class);

    private final Broker<?> _broker;

    private FileGroupDatabase _groupDatabase;

    @ManagedAttributeField
    private String _path;

    @ManagedObjectFactoryConstructor
    public FileBasedGroupProviderImpl(Map<String, Object> attributes,
                                      Broker broker)
    {
        super(parentsMap(broker), attributes);


        _broker = broker;
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

    @Override
    protected void onOpen()
    {
        super.onOpen();
        FileGroupDatabase groupDatabase = new FileGroupDatabase();
        try
        {
            groupDatabase.setGroupFile(getPath());
        }
        catch(IOException | RuntimeException e)
        {
            if (e instanceof IllegalConfigurationException)
            {
                throw (IllegalConfigurationException) e;
            }
            throw new IllegalConfigurationException(String.format("Cannot load groups from '%s'", getPath()), e);
        }

        _groupDatabase = groupDatabase;

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
        File file = new File(_path);
        if (!file.exists())
        {
            File parent = file.getParentFile();
            if (!parent.exists() && !file.getParentFile().mkdirs())
            {
                throw new IllegalConfigurationException(String.format("Cannot create groups file at '%s'",_path));
            }

            try
            {
                String posixFileAttributes = getContextValue(String.class, BrokerProperties.POSIX_FILE_PERMISSIONS);
                new FileHelper().createNewFile(file, posixFileAttributes);
            }
            catch (IOException e)
            {
                throw new IllegalConfigurationException(String.format("Cannot create groups file at '%s'", _path), e);
            }
        }
    }

    @Override
    protected void validateOnCreate()
    {
        super.validateOnCreate();
        File groupsFile = new File(_path);
        if (groupsFile.exists())
        {
            if (!groupsFile.canRead())
            {
                throw new IllegalConfigurationException(String.format("Cannot read groups file '%s'. Please check permissions.", _path));
            }

            FileGroupDatabase groupDatabase = new FileGroupDatabase();
            try
            {
                groupDatabase.setGroupFile(_path);
            }
            catch (Exception e)
            {
                throw new IllegalConfigurationException(String.format("Cannot load groups from '%s'", _path), e);
            }
        }
    }

    @Override
    public String getPath()
    {
        return _path;
    }

    @Override
    public <C extends ConfiguredObject> C addChild(Class<C> childClass,
            Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        if (childClass == Group.class)
        {
            String groupName = (String) attributes.get(Group.NAME);

            if (getState() != State.ACTIVE)
            {
                throw new IllegalConfigurationException(String.format("Group provider '%s' is not activated. Cannot create a group.", getName()));
            }

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

    @Override
    protected SecurityManager getSecurityManager()
    {
        return _broker.getSecurityManager();
    }

    @StateTransition( currentState = { State.UNINITIALIZED, State.QUIESCED, State.ERRORED }, desiredState = State.ACTIVE )
    private void activate()
    {
        if (_groupDatabase != null)
        {
            setState(State.ACTIVE);
        }
        else
        {
            if (_broker.isManagementMode())
            {
                        LOGGER.warn("Failed to activate group provider: " + getName());
            }
            else
            {
                throw new IllegalConfigurationException(String.format("Cannot load groups from '%s'", getPath()));
            }
        }
    }

    @StateTransition( currentState = { State.QUIESCED, State.ACTIVE, State.ERRORED}, desiredState = State.DELETED )
    private void doDelete()
    {
        close();
        File file = new File(getPath());
        if (file.exists())
        {
            if (!file.delete())
            {
                throw new IllegalConfigurationException("Cannot delete group file");
            }
        }

        deleted();
        setState(State.DELETED);
    }

    @StateTransition( currentState = State.UNINITIALIZED, desiredState = State.QUIESCED)
    private void startQuiesced()
    {
        setState(State.QUIESCED);
    }

    public Set<Principal> getGroupPrincipalsForUser(String username)
    {
        Set<String> groups = _groupDatabase == null ? Collections.<String>emptySet(): _groupDatabase.getGroupsForUser(username);
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

    private class GroupAdapter extends AbstractConfiguredObject<GroupAdapter> implements Group<GroupAdapter>
    {
        private GroupPrincipal _groupPrincipal;
        public GroupAdapter(Map<String, Object> attributes)
        {
            super(parentsMap(FileBasedGroupProviderImpl.this), attributes);
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
            setState(State.ACTIVE);
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
            _groupPrincipal = new GroupPrincipal(getName());
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
            _groupDatabase.removeGroup(getName());
            deleted();
            setState(State.DELETED);
        }

        @Override
        public GroupPrincipal getGroupPrincipal()
        {
            return _groupPrincipal;
        }

        private class GroupMemberAdapter extends AbstractConfiguredObject<GroupMemberAdapter> implements
                GroupMember<GroupMemberAdapter>
        {

            private Principal _principal;

            public GroupMemberAdapter(Map<String, Object> attrMap)
            {
                // TODO - need to relate to the User object
                super(parentsMap(GroupAdapter.this),attrMap);
            }

            @Override
            protected void onOpen()
            {
                super.onOpen();
                _principal = new UsernamePrincipal(getName());
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
            public <C extends ConfiguredObject> Collection<C> getChildren(
                    Class<C> clazz)
            {
                return Collections.emptySet();
            }

            @StateTransition(currentState = State.UNINITIALIZED, desiredState = State.ACTIVE)
            private void activate()
            {
                setState(State.ACTIVE);
            }

            @StateTransition(currentState = State.ACTIVE, desiredState = State.DELETED)
            private void doDelete()
            {
                _groupDatabase.removeUserFromGroup(getName(), GroupAdapter.this.getName());
                deleted();
                setState(State.DELETED);
            }

            @Override
            public Principal getPrincipal()
            {
                return _principal;
            }
        }
    }


}
