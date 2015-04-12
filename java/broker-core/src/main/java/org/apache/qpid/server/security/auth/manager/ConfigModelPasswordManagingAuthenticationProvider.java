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
package org.apache.qpid.server.security.auth.manager;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.security.auth.login.AccountNotFoundException;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.apache.qpid.server.configuration.updater.Task;
import org.apache.qpid.server.configuration.updater.VoidTaskWithException;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.PasswordCredentialManagingAuthenticationProvider;
import org.apache.qpid.server.model.PreferencesSupportingAuthenticationProvider;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.UsernamePrincipal;

public abstract class ConfigModelPasswordManagingAuthenticationProvider<X extends ConfigModelPasswordManagingAuthenticationProvider<X>>
        extends AbstractAuthenticationManager<X>
        implements PasswordCredentialManagingAuthenticationProvider<X>, PreferencesSupportingAuthenticationProvider
{
    static final Charset ASCII = Charset.forName("ASCII");
    protected Map<String, ManagedUser> _users = new ConcurrentHashMap<>();

    protected ConfigModelPasswordManagingAuthenticationProvider(final Map<String, Object> attributes,
                                                                final Broker broker)
    {
        super(attributes, broker);
    }

    ManagedUser getUser(final String username)
    {
        return _users.get(username);
    }

    @Override
    public boolean createUser(final String username, final String password, final Map<String, String> attributes)
    {
        return runTask(new Task<Boolean>()
        {
            @Override
            public Boolean execute()
            {

                Map<String, Object> userAttrs = new HashMap<>();
                userAttrs.put(User.ID, UUID.randomUUID());
                userAttrs.put(User.NAME, username);
                userAttrs.put(User.PASSWORD, password);
                userAttrs.put(User.TYPE, ManagedUser.MANAGED_USER_TYPE);
                User user = createChild(User.class, userAttrs);
                return user != null;

            }
        });
    }

    @Override
    protected SecurityManager getSecurityManager()
    {
        return getBroker().getSecurityManager();
    }

    @Override
    public void deleteUser(final String user) throws AccountNotFoundException
    {
        final ManagedUser authUser = getUser(user);
        if(authUser != null)
        {
            authUser.delete();
        }
        else
        {
            throw new AccountNotFoundException("No such user: '" + user + "'");
        }
    }

    @Override
    public Map<String, Map<String, String>> getUsers()
    {
        return runTask(new Task<Map<String, Map<String, String>>>()
        {
            @Override
            public Map<String, Map<String, String>> execute()
            {

                Map<String, Map<String, String>> users = new HashMap<>();
                for (String user : _users.keySet())
                {
                    users.put(user, Collections.<String, String>emptyMap());
                }
                return users;
            }
        });
    }

    @Override
    public void reload() throws IOException
    {

    }

    @Override
    public void recoverUser(final User user)
    {
        _users.put(user.getName(), (ManagedUser) user);
    }

    @Override
    public void setPassword(final String username, final String password) throws AccountNotFoundException
    {
        runTask(new VoidTaskWithException<AccountNotFoundException>()
        {
            @Override
            public void execute() throws AccountNotFoundException
            {

                final ManagedUser authUser = getUser(username);
                if (authUser != null)
                {
                    authUser.setPassword(password);
                }
                else
                {
                    throw new AccountNotFoundException("No such user: '" + username + "'");
                }
            }
        });

    }

    @Override
    public AuthenticationResult authenticate(final SaslServer server, final byte[] response)
    {
        try
        {
            // Process response from the client
            byte[] challenge = server.evaluateResponse(response != null ? response : new byte[0]);

            if (server.isComplete() && (challenge == null || challenge.length == 0))
            {
                final String userId = server.getAuthorizationID();
                return new AuthenticationResult(new UsernamePrincipal(userId));
            }
            else
            {
                return new AuthenticationResult(challenge, AuthenticationResult.AuthenticationStatus.CONTINUE);
            }
        }
        catch (SaslException e)
        {
            return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR, e);
        }
    }

    protected abstract String createStoredPassword(String password);

    Map<String, ManagedUser> getUserMap()
    {
        return _users;
    }

    @Override
    public <C extends ConfiguredObject> ListenableFuture<C> addChildAsync(final Class<C> childClass,
                                                   final Map<String, Object> attributes,
                                                   final ConfiguredObject... otherParents)
    {
        if(childClass == User.class)
        {
            String username = (String) attributes.get(User.NAME);
            if (_users.containsKey(username))
            {
                throw new IllegalArgumentException("User '" + username + "' already exists");
            }
            attributes.put(User.PASSWORD, createStoredPassword((String) attributes.get(User.PASSWORD)));
            ManagedUser user = new ManagedUser(attributes, ConfigModelPasswordManagingAuthenticationProvider.this);
            user.create();
            return Futures.immediateFuture((C)getUser(username));
        }
        return super.addChildAsync(childClass, attributes, otherParents);
    }

    abstract void validateUser(final ManagedUser managedUser);
}
