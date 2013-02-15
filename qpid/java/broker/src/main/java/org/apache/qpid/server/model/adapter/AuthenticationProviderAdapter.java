/*
 *
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

import java.io.IOException;
import java.security.AccessControlException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.security.auth.login.AccountNotFoundException;

import org.apache.log4j.Logger;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.IllegalStateTransitionException;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.PasswordCredentialManagingAuthenticationProvider;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.model.VirtualHostAlias;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.security.group.GroupPrincipalAccessor;
import org.apache.qpid.server.security.SecurityManager;

public abstract class AuthenticationProviderAdapter<T extends AuthenticationManager> extends AbstractAdapter implements AuthenticationProvider
{
    private static final Logger LOGGER = Logger.getLogger(AuthenticationProviderAdapter.class);

    private final T _authManager;
    protected final Broker _broker;

    private GroupPrincipalAccessor _groupAccessor;

    private Object _type;

    private AuthenticationProviderAdapter(UUID id, Broker broker, final T authManager, Map<String, Object> attributes)
    {
        super(id, null, attributes, broker.getTaskExecutor());
        _authManager = authManager;
        _broker = broker;
        _type = authManager instanceof PrincipalDatabaseAuthenticationManager? PrincipalDatabaseAuthenticationManager.class.getSimpleName() : AuthenticationManager.class.getSimpleName() ;
        addParent(Broker.class, broker);
    }

    T getAuthManager()
    {
        return _authManager;
    }

    @Override
    public Collection<VirtualHostAlias> getVirtualHostPortBindings()
    {
        return Collections.emptyList();
    }

    @Override
    public String getName()
    {
        return (String)getAttribute(AuthenticationProvider.NAME);
    }

    @Override
    public String setName(String currentName, String desiredName) throws IllegalStateException, AccessControlException
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
    public void setDurable(boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    @Override
    public LifetimePolicy setLifetimePolicy(LifetimePolicy expected, LifetimePolicy desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
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
            throws IllegalStateException, AccessControlException, IllegalArgumentException
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
        return AuthenticationProvider.AVAILABLE_ATTRIBUTES;
    }

    @Override
    public Object getAttribute(String name)
    {
        if(TYPE.equals(name))
        {
            return _type;
        }
        else if(CREATED.equals(name))
        {
            // TODO
        }
        else if(DURABLE.equals(name))
        {
            return true;
        }
        else if(ID.equals(name))
        {
            return getId();
        }
        else if(LIFETIME_POLICY.equals(name))
        {
            return LifetimePolicy.PERMANENT;
        }
        else if(STATE.equals(name))
        {
            return State.ACTIVE; // TODO
        }
        else if(TIME_TO_LIVE.equals(name))
        {
            // TODO
        }
        else if(UPDATED.equals(name))
        {
            // TODO
        }
        return super.getAttribute(name);
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        return null;
    }

    @Override
    public boolean setState(State currentState, State desiredState)
            throws IllegalStateTransitionException, AccessControlException
    {
        if(desiredState == State.DELETED)
        {
            return true;
        }
        else if(desiredState == State.ACTIVE)
        {
            if (_groupAccessor == null)
            {
                throw new IllegalStateTransitionException("Cannot transit into ACTIVE state with null group accessor!");
            }
            _authManager.initialise();
            return true;
        }
        else if(desiredState == State.STOPPED)
        {
            _authManager.close();
            return true;
        }
        return false;
    }

    @Override
    public SubjectCreator getSubjectCreator()
    {
        return new SubjectCreator(_authManager, _groupAccessor);
    }

    public void setGroupAccessor(GroupPrincipalAccessor groupAccessor)
    {
        _groupAccessor = groupAccessor;
    }

    public static class SimpleAuthenticationProviderAdapter extends AuthenticationProviderAdapter<AuthenticationManager>
    {

        public SimpleAuthenticationProviderAdapter(
                UUID id, Broker broker, AuthenticationManager authManager, Map<String, Object> attributes)
        {
            super(id, broker,authManager, attributes);
        }

        @Override
        public <C extends ConfiguredObject> C createChild(Class<C> childClass,
                                                          Map<String, Object> attributes,
                                                          ConfiguredObject... otherParents)
        {
            throw new UnsupportedOperationException();
        }
    }

    public static class PrincipalDatabaseAuthenticationManagerAdapter
            extends AuthenticationProviderAdapter<PrincipalDatabaseAuthenticationManager>
            implements PasswordCredentialManagingAuthenticationProvider
    {
        public PrincipalDatabaseAuthenticationManagerAdapter(
                UUID id, Broker broker, PrincipalDatabaseAuthenticationManager authManager, Map<String, Object> attributes)
        {
            super(id, broker, authManager, attributes);
        }

        @Override
        public boolean createUser(String username, String password, Map<String, String> attributes)
        {
            if(getSecurityManager().authoriseUserOperation(Operation.CREATE, username))
            {
                return getPrincipalDatabase().createPrincipal(new UsernamePrincipal(username), password.toCharArray());
            }
            else
            {
                throw new AccessControlException("Do not have permission to create new user");
            }
        }

        @Override
        public void deleteUser(String username) throws AccountNotFoundException
        {
            if(getSecurityManager().authoriseUserOperation(Operation.DELETE, username))
            {

                getPrincipalDatabase().deletePrincipal(new UsernamePrincipal(username));
            }
            else
            {
                throw new AccessControlException("Cannot delete user " + username);
            }
        }

        private SecurityManager getSecurityManager()
        {
            return _broker.getSecurityManager();
        }

        private PrincipalDatabase getPrincipalDatabase()
        {
            return getAuthManager().getPrincipalDatabase();
        }

        @Override
        public void setPassword(String username, String password) throws AccountNotFoundException
        {
            if(getSecurityManager().authoriseUserOperation(Operation.UPDATE, username))
            {
                getPrincipalDatabase().updatePassword(new UsernamePrincipal(username), password.toCharArray());
            }
            else
            {
                throw new AccessControlException("Do not have permission to set password");
            }
        }

        @Override
        public Map<String, Map<String, String>> getUsers()
        {

            Map<String, Map<String,String>> users = new HashMap<String, Map<String, String>>();
            for(Principal principal : getPrincipalDatabase().getUsers())
            {
                users.put(principal.getName(), Collections.<String, String>emptyMap());
            }
            return users;
        }

        public void reload() throws IOException
        {
            getPrincipalDatabase().reload();
        }

        @Override
        public <C extends ConfiguredObject> C addChild(Class<C> childClass,
                                                          Map<String, Object> attributes,
                                                          ConfiguredObject... otherParents)
        {
            if(childClass == User.class)
            {
                String username = (String) attributes.get("name");
                String password = (String) attributes.get("password");
                Principal p = new UsernamePrincipal(username);

                if(createUser(username, password,null))
                {
                    @SuppressWarnings("unchecked")
                    C pricipalAdapter = (C) new PrincipalAdapter(p, getTaskExecutor());
                    return pricipalAdapter;
                }
                else
                {
                    //TODO? Silly interface on the PrincipalDatabase at fault
                    throw new RuntimeException("Failed to create user");
                }
            }

            return super.addChild(childClass, attributes, otherParents);
        }

        @Override
        public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
        {
            if(clazz == User.class)
            {
                List<Principal> users = getPrincipalDatabase().getUsers();
                Collection<User> principals = new ArrayList<User>(users.size());
                for(Principal user : users)
                {
                    principals.add(new PrincipalAdapter(user, getTaskExecutor()));
                }
                @SuppressWarnings("unchecked")
                Collection<C> unmodifiablePrincipals = (Collection<C>) Collections.unmodifiableCollection(principals);
                return unmodifiablePrincipals;
            }
            else
            {
                return super.getChildren(clazz);
            }
        }

        private class PrincipalAdapter extends AbstractAdapter implements User
        {
            private final Principal _user;


            public PrincipalAdapter(Principal user, TaskExecutor taskExecutor)
            {
                super(UUIDGenerator.generateUserUUID(PrincipalDatabaseAuthenticationManagerAdapter.this.getName(), user.getName()), taskExecutor);
                _user = user;

            }

            @Override
            public void setPassword(String password)
            {
                try
                {
                    PrincipalDatabaseAuthenticationManagerAdapter.this.setPassword(_user.getName(), password);
                }
                catch (AccountNotFoundException e)
                {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public String getName()
            {
                return _user.getName();
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
            public void setDurable(boolean durable)
                    throws IllegalStateException, AccessControlException, IllegalArgumentException
            {
                throw new IllegalStateException("Durability cannot be updated");
            }

            @Override
            public LifetimePolicy getLifetimePolicy()
            {
                return LifetimePolicy.PERMANENT;
            }

            @Override
            public LifetimePolicy setLifetimePolicy(LifetimePolicy expected, LifetimePolicy desired)
                    throws IllegalStateException, AccessControlException, IllegalArgumentException
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
                    throws IllegalStateException, AccessControlException, IllegalArgumentException
            {
                throw new IllegalStateException("ttl cannot be updated");
            }

            @Override
            public Statistics getStatistics()
            {
                return NoStatistics.getInstance();
            }

            @Override
            public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
            {
                return null;
            }

            @Override
            public <C extends ConfiguredObject> C createChild(Class<C> childClass,
                                                              Map<String, Object> attributes,
                                                              ConfiguredObject... otherParents)
            {
                return null;
            }

            @Override
            public Collection<String> getAttributeNames()
            {
                return User.AVAILABLE_ATTRIBUTES;
            }

            @Override
            public Object getAttribute(String name)
            {
                if(ID.equals(name))
                {
                    return getId();
                }
                else if(PASSWORD.equals(name))
                {
                    return null; // for security reasons we don't expose the password
                }
                else if(NAME.equals(name))
                {
                    return getName();
                }
                return super.getAttribute(name);
            }

            @Override
            public boolean changeAttribute(String name, Object expected, Object desired)
                    throws IllegalStateException, AccessControlException, IllegalArgumentException
            {
                if(name.equals(PASSWORD))
                {
                    setPassword((String)desired);
                    return true;
                }
                return super.changeAttribute(name, expected, desired);
            }

            @Override
            protected boolean setState(State currentState, State desiredState)
                    throws IllegalStateTransitionException, AccessControlException
            {
                if(desiredState == State.DELETED)
                {
                    try
                    {
                        deleteUser(_user.getName());
                    }
                    catch (AccountNotFoundException e)
                    {
                        LOGGER.warn("Failed to delete user " + _user, e);
                    }
                    return true;
                }
                return false;
            }
        }
    }
}
