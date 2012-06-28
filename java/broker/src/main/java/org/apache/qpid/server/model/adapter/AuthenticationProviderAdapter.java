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
import javax.security.auth.login.AccountNotFoundException;

import org.apache.log4j.Logger;
import org.apache.qpid.server.model.*;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;

public abstract class AuthenticationProviderAdapter<T extends AuthenticationManager> extends AbstractAdapter implements AuthenticationProvider
{
    private static final Logger LOGGER = Logger.getLogger(AuthenticationProviderAdapter.class);

    private final BrokerAdapter _broker;
    private final T _authManager;

    private AuthenticationProviderAdapter(BrokerAdapter brokerAdapter,
                                          final T authManager)
    {
        _broker = brokerAdapter;
        _authManager = authManager;
    }

    public static AuthenticationProviderAdapter createAuthenticationProviderAdapter(BrokerAdapter brokerAdapter,
                                                                             final AuthenticationManager authManager)
    {
        return authManager instanceof PrincipalDatabaseAuthenticationManager
                ? new PrincipalDatabaseAuthenticationManagerAdapter(brokerAdapter, (PrincipalDatabaseAuthenticationManager) authManager)
                : new SimpleAuthenticationProviderAdapter(brokerAdapter, authManager);
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
        return _authManager.getClass().getSimpleName();
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
            return _authManager.getClass().getSimpleName();
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
        else if(NAME.equals(name))
        {
            return getName();
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
    public <C extends ConfiguredObject> C createChild(Class<C> childClass,
                                                      Map<String, Object> attributes,
                                                      ConfiguredObject... otherParents)
    {
        return null;
    }

    private static class SimpleAuthenticationProviderAdapter extends AuthenticationProviderAdapter<AuthenticationManager>
    {
        public SimpleAuthenticationProviderAdapter(
                BrokerAdapter brokerAdapter, AuthenticationManager authManager)
        {
            super(brokerAdapter,authManager);
        }
    }

    private static class PrincipalDatabaseAuthenticationManagerAdapter
            extends AuthenticationProviderAdapter<PrincipalDatabaseAuthenticationManager>
            implements PasswordCredentialManagingAuthenticationProvider
    {
        public PrincipalDatabaseAuthenticationManagerAdapter(
                BrokerAdapter brokerAdapter, PrincipalDatabaseAuthenticationManager authManager)
        {
            super(brokerAdapter, authManager);
        }

        @Override
        public boolean createUser(String username, String password, Map<String, String> attributes)
        {
            return getPrincipalDatabase().createPrincipal(new UsernamePrincipal(username), password.toCharArray());
        }

        @Override
        public void deleteUser(String username) throws AccountNotFoundException
        {
            if(getSecurityManager().authoriseMethod(Operation.DELETE,
                                                    "UserManagement",
                                                    "deleteUser"))
            {

                getPrincipalDatabase().deletePrincipal(new UsernamePrincipal(username));
            }
            else
            {
                throw new AccessControlException("Cannot delete user " + username);
            }
        }

        private org.apache.qpid.server.security.SecurityManager getSecurityManager()
        {
            return ApplicationRegistry.getInstance().getSecurityManager();
        }

        private PrincipalDatabase getPrincipalDatabase()
        {
            return getAuthManager().getPrincipalDatabase();
        }

        @Override
        public void setPassword(String username, String password) throws AccountNotFoundException
        {
            getPrincipalDatabase().updatePassword(new UsernamePrincipal(username), password.toCharArray());
        }

        public void reload() throws IOException
        {
            if(getSecurityManager().authoriseMethod(Operation.UPDATE, "UserManagement", "reload"))
            {
                getPrincipalDatabase().reload();
            }
            else
            {
                throw new AccessControlException("Do not have permission to reload principal database");
            }
        }

        @Override
        public Map<String, Map<String, String>> getUsers()
        {

            Map<String, Map<String,String>> users = new HashMap<String, Map<String, String>>();
            for(Principal principal : getPrincipalDatabase().getUsers())
            {
                users.put(principal.getName(), Collections.EMPTY_MAP);
            }
            return users;
        }

        @Override
        public <C extends ConfiguredObject> C createChild(Class<C> childClass,
                                                          Map<String, Object> attributes,
                                                          ConfiguredObject... otherParents)
        {
            if(childClass == User.class)
            {
                Principal p = new UsernamePrincipal((String) attributes.get("name"));
                if(getSecurityManager().authoriseMethod(Operation.UPDATE, "UserManagement", "createUser"))
                {
                    if(getPrincipalDatabase().createPrincipal(p, ((String)attributes.get("password")).toCharArray()))
                    {
                        return (C) new PrincipalAdapter(p);
                    }
                }
                else
                {
                    throw new AccessControlException("Do not have permission to create a new user");
                }

            }

            return super.createChild(childClass, attributes, otherParents);
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
                    principals.add(new PrincipalAdapter(user));
                }
                return (Collection<C>) Collections.unmodifiableCollection(principals);
            }
            else
            {
                return super.getChildren(clazz);
            }
        }

        private class PrincipalAdapter extends AbstractAdapter implements User
        {
            private final Principal _user;


            public PrincipalAdapter(Principal user)
            {
                super(PrincipalDatabaseAuthenticationManagerAdapter.this.getName(), user.getName());
                _user = user;

            }

            @Override
            public String getPassword()
            {
                return null;
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
                else if(NAME.equals(name))
                {
                    return getName();
                }
                return super.getAttribute(name);
            }

            @Override
            public Object setAttribute(String name, Object expected, Object desired)
                    throws IllegalStateException, AccessControlException, IllegalArgumentException
            {
                if(name.equals(PASSWORD))
                {
                    setPassword((String)desired);
                }
                return super.setAttribute(name,
                                          expected,
                                          desired);
            }

            @Override
            public State setDesiredState(State currentState, State desiredState)
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
                    return State.DELETED;
                }
                return super.setDesiredState(currentState, desiredState);
            }
        }
    }
}
