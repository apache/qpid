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
import java.security.AccessControlException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.login.AccountNotFoundException;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.xml.bind.DatatypeConverter;

import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.PasswordCredentialManagingAuthenticationProvider;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.auth.sasl.scram.ScramSHA1SaslServer;

@ManagedObject( category = false, type = "SCRAM-SHA-1" )
public class ScramSHA1AuthenticationManager
        extends AbstractAuthenticationManager<ScramSHA1AuthenticationManager>
    implements PasswordCredentialManagingAuthenticationProvider<ScramSHA1AuthenticationManager>,
               RecovererProvider
{
    public static final String SCRAM_USER_TYPE = "scram";
    private static final Charset ASCII = Charset.forName("ASCII");
    public static final String HMAC_SHA_1 = "HmacSHA1";
    private final SecureRandom _random = new SecureRandom();
    private int _iterationCount = 4096;
    private Map<String, ScramAuthUser> _users = new ConcurrentHashMap<String, ScramAuthUser>();


    protected ScramSHA1AuthenticationManager(final Map<String, Object> attributes, final Broker broker)
    {
        super(attributes, broker);
    }

    @Override
    public void initialise()
    {

    }

    @Override
    public String getMechanisms()
    {
        return ScramSHA1SaslServer.MECHANISM;
    }

    @Override
    public SaslServer createSaslServer(final String mechanism,
                                       final String localFQDN,
                                       final Principal externalPrincipal)
            throws SaslException
    {
        return new ScramSHA1SaslServer(this);
    }

    @Override
    public AuthenticationResult authenticate(final SaslServer server, final byte[] response)
    {
        try
        {
            // Process response from the client
            byte[] challenge = server.evaluateResponse(response != null ? response : new byte[0]);

            if (server.isComplete())
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

    @Override
    public AuthenticationResult authenticate(final String username, final String password)
    {
        ScramAuthUser user = getUser(username);
        if(user != null)
        {
            final String[] usernamePassword = user.getPassword().split(",");
            byte[] salt = DatatypeConverter.parseBase64Binary(usernamePassword[0]);
            try
            {
                if(Arrays.equals(DatatypeConverter.parseBase64Binary(usernamePassword[1]),createSaltedPassword(salt, password)))
                {
                    return new AuthenticationResult(new UsernamePrincipal(username));
                }
            }
            catch (SaslException e)
            {
                return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR,e);
            }

        }

        return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR);


    }

    @Override
    public void delete()
    {

    }

    @Override
    public void close()
    {

    }

    public int getIterationCount()
    {
        return _iterationCount;
    }

    public byte[] getSalt(final String username)
    {
        ScramAuthUser user = getUser(username);

        if(user == null)
        {
            // don't disclose that the user doesn't exist, just generate random data so the failure is indistinguishable
            // from the "wrong password" case

            byte[] salt = new byte[32];
            _random.nextBytes(salt);
            return salt;
        }
        else
        {
            return DatatypeConverter.parseBase64Binary(user.getPassword().split(",")[0]);
        }
    }

    private static final byte[] INT_1 = new byte[]{0, 0, 0, 1};

    public byte[] getSaltedPassword(final String username) throws SaslException
    {
        ScramAuthUser user = getUser(username);
        if(user == null)
        {
            throw new SaslException("Authentication Failed");
        }
        else
        {
            return DatatypeConverter.parseBase64Binary(user.getPassword().split(",")[1]);
        }
    }

    private ScramAuthUser getUser(final String username)
    {
        return _users.get(username);
    }

    private byte[] createSaltedPassword(byte[] salt, String password) throws SaslException
    {
        Mac mac = createSha1Hmac(password.getBytes(ASCII));

        mac.update(salt);
        mac.update(INT_1);
        byte[] result = mac.doFinal();

        byte[] previous = null;
        for(int i = 1; i < getIterationCount(); i++)
        {
            mac.update(previous != null? previous: result);
            previous = mac.doFinal();
            for(int x = 0; x < result.length; x++)
            {
                result[x] ^= previous[x];
            }
        }

        return result;

    }

    private Mac createSha1Hmac(final byte[] keyBytes)
            throws SaslException
    {
        try
        {
            SecretKeySpec key = new SecretKeySpec(keyBytes, HMAC_SHA_1);
            Mac mac = Mac.getInstance(HMAC_SHA_1);
            mac.init(key);
            return mac;
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new SaslException(e.getMessage(), e);
        }
        catch (InvalidKeyException e)
        {
            throw new SaslException(e.getMessage(), e);
        }
    }

    @Override
    public boolean createUser(final String username, final String password, final Map<String, String> attributes)
    {
        return runTask(new TaskExecutor.Task<Boolean>()
        {
            @Override
            public Boolean execute()
            {
                getSecurityManager().authoriseUserOperation(Operation.CREATE, username);
                if (_users.containsKey(username))
                {
                    throw new IllegalArgumentException("User '" + username + "' already exists");
                }
                try
                {
                    Map<String, Object> userAttrs = new HashMap<String, Object>();
                    userAttrs.put(User.ID, UUID.randomUUID());
                    userAttrs.put(User.NAME, username);
                    userAttrs.put(User.PASSWORD, createStoredPassword(password));
                    userAttrs.put(User.TYPE, SCRAM_USER_TYPE);
                    ScramAuthUser user = new ScramAuthUser(userAttrs, ScramSHA1AuthenticationManager.this);
                    user.create();

                    return true;
                }
                catch (SaslException e)
                {
                    throw new IllegalArgumentException(e);
                }
            }
        });
    }

    private SecurityManager getSecurityManager()
    {
        return getBroker().getSecurityManager();
    }

    @Override
    public void deleteUser(final String user) throws AccountNotFoundException
    {
        runTask(new TaskExecutor.VoidTaskWithException<AccountNotFoundException>()
        {
            @Override
            public void execute() throws AccountNotFoundException
            {
                final ScramAuthUser authUser = getUser(user);
                if(authUser != null)
                {
                    authUser.setState(State.ACTIVE, State.DELETED);
                }
                else
                {
                    throw new AccountNotFoundException("No such user: '" + user + "'");
                }
            }
        });
    }

    @Override
    public void setPassword(final String username, final String password) throws AccountNotFoundException
    {
        runTask(new TaskExecutor.VoidTaskWithException<AccountNotFoundException>()
        {
            @Override
            public void execute() throws AccountNotFoundException
            {

                final ScramAuthUser authUser = getUser(username);
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
    public Map<String, Map<String, String>> getUsers()
    {
        return runTask(new TaskExecutor.Task<Map<String, Map<String, String>>>()
        {
            @Override
            public Map<String, Map<String, String>> execute()
            {

                Map<String, Map<String, String>> users = new HashMap<String, Map<String, String>>();
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
    public ConfiguredObjectRecoverer<? extends ConfiguredObject> getRecoverer(final String type)
    {
        return null;
    }

    @ManagedObject( category = false, type = "scram")
    static class ScramAuthUser extends AbstractConfiguredObject<ScramAuthUser> implements User<ScramAuthUser>
    {

        private ScramSHA1AuthenticationManager _authenticationManager;
        @ManagedAttributeField
        private String _password;

        protected ScramAuthUser(final Map<String, Object> attributes, ScramSHA1AuthenticationManager parent)
        {
            super(parentsMap(parent),
                  attributes, parent.getTaskExecutor());
            _authenticationManager = parent;
            if(!ASCII.newEncoder().canEncode(getName()))
            {
                throw new IllegalArgumentException("Scram SHA1 user names are restricted to characters in the ASCII charset");
            }

        }

        @Override
        protected void onOpen()
        {
            super.onOpen();
            _authenticationManager._users.put(getName(), this);
        }

        @Override
        public void validate()
        {
            super.validate();
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
        protected boolean setState(final State currentState, final State desiredState)
        {
            if(desiredState == State.DELETED)
            {
                _authenticationManager.getSecurityManager().authoriseUserOperation(Operation.DELETE, getName());
                _authenticationManager._users.remove(getName());
                _authenticationManager.deleted();
                deleted();
                return true;
            }
            else
            {
                return false;
            }
        }

        @Override
        public void setAttributes(final Map<String, Object> attributes)
                throws IllegalStateException, AccessControlException, IllegalArgumentException
        {
            runTask(new TaskExecutor.VoidTask()
            {

                @Override
                public void execute()
                {
                    Map<String, Object> modifiedAttributes = new HashMap<String, Object>(attributes);
                    final String newPassword = (String) attributes.get(User.PASSWORD);
                    if (attributes.containsKey(User.PASSWORD)
                        && !newPassword.equals(getActualAttributes().get(User.PASSWORD)))
                    {
                        try
                        {
                            modifiedAttributes.put(User.PASSWORD,
                                                   _authenticationManager.createStoredPassword(newPassword));
                        }
                        catch (SaslException e)
                        {
                            throw new IllegalArgumentException(e);
                        }
                    }
                    ScramSHA1AuthenticationManager.ScramAuthUser.super.setAttributes(modifiedAttributes);
                }
            });


        }

        @Override
        public Object getAttribute(final String name)
        {
            return super.getAttribute(name);
        }

        @Override
        public String getPassword()
        {
            return _password;
        }

        @Override
        public void setPassword(final String password)
        {
            _authenticationManager.getSecurityManager().authoriseUserOperation(Operation.UPDATE, getName());

            try
            {
                changeAttribute(User.PASSWORD, getAttribute(User.PASSWORD), _authenticationManager.createStoredPassword(
                        password));
            }
            catch (SaslException e)
            {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        public State getState()
        {
            return State.ACTIVE;
        }

        @Override
        public <C extends ConfiguredObject> Collection<C> getChildren(final Class<C> clazz)
        {
            return Collections.emptySet();
        }

        @Override
        public Map<String, Object> getPreferences()
        {
            PreferencesProvider preferencesProvider = _authenticationManager.getPreferencesProvider();
            if (preferencesProvider == null)
            {
                return null;
            }
            return preferencesProvider.getPreferences(this.getName());
        }

        @Override
        public Object getPreference(String name)
        {
            Map<String, Object> preferences = getPreferences();
            if (preferences == null)
            {
                return null;
            }
            return preferences.get(name);
        }

        @Override
        public Map<String, Object> setPreferences(Map<String, Object> preferences)
        {
            PreferencesProvider preferencesProvider = _authenticationManager.getPreferencesProvider();
            if (preferencesProvider == null)
            {
                return null;
            }
            return preferencesProvider.setPreferences(this.getName(), preferences);
        }

        @Override
        public boolean deletePreferences()
        {
            PreferencesProvider preferencesProvider = _authenticationManager.getPreferencesProvider();
            if (preferencesProvider == null)
            {
                return false;
            }
            String[] deleted = preferencesProvider.deletePreferences(this.getName());
            return deleted.length == 1;
        }

    }

    @Override
    public void recoverUser(final User user)
    {
        _users.put(user.getName(), (ScramAuthUser) user);
    }

    protected String createStoredPassword(final String password) throws SaslException
    {
        byte[] salt = new byte[32];
        _random.nextBytes(salt);
        byte[] passwordBytes = createSaltedPassword(salt, password);
        return DatatypeConverter.printBase64Binary(salt) + "," + DatatypeConverter.printBase64Binary(passwordBytes);
    }

    @Override
    public <C extends ConfiguredObject> C addChild(final Class<C> childClass,
                                                   final Map<String, Object> attributes,
                                                   final ConfiguredObject... otherParents)
    {
        if(childClass == User.class)
        {
            String username = (String) attributes.get("name");
            String password = (String) attributes.get("password");

            if(createUser(username, password,null))
            {
                @SuppressWarnings("unchecked")
                C user = (C) _users.get(username);
                return user;
            }
            else
            {
                return null;

            }
        }
        return super.addChild(childClass, attributes, otherParents);
    }

}
