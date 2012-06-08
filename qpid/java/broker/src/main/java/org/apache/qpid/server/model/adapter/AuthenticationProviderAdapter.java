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

import java.security.AccessControlException;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.security.auth.login.AccountNotFoundException;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.PasswordCredentialManagingAuthenticationProvider;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.VirtualHostAlias;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;

public abstract class AuthenticationProviderAdapter<T extends AuthenticationManager> extends AbstractAdapter implements AuthenticationProvider
{
    private final BrokerAdapter _broker;
    private final T _authManager;

    private AuthenticationProviderAdapter(BrokerAdapter brokerAdapter,
                                          final T authManager)
    {
        _broker = brokerAdapter;
        _authManager = authManager;
    }

    static AuthenticationProviderAdapter createAuthenticationProviderAdapter(BrokerAdapter brokerAdapter,
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
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public State getActualState()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
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
        //To change body of implemented methods use File | Settings | File Templates.
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
        return null;  //To change body of implemented methods use File | Settings | File Templates.
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
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public <C extends ConfiguredObject> C createChild(Class<C> childClass,
                                                      Map<String, Object> attributes,
                                                      ConfiguredObject... otherParents)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
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
        public void createUser(String username, String password, Map<String, String> attributes)
        {
            getPrincipalDatabase().createPrincipal(new UsernamePrincipal(username), password.toCharArray());
        }

        @Override
        public void deleteUser(String username)
        {
            try
            {
                getPrincipalDatabase().deletePrincipal(new UsernamePrincipal(username));
            }
            catch (AccountNotFoundException e)
            {
                // TODO
            }
        }

        private PrincipalDatabase getPrincipalDatabase()
        {
            return getAuthManager().getPrincipalDatabase();
        }

        @Override
        public void setPassword(String username, String password)
        {
            try
            {
                getPrincipalDatabase().updatePassword(new UsernamePrincipal(username), password.toCharArray());
            }
            catch (AccountNotFoundException e)
            {
                // TODO
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
    }
}
