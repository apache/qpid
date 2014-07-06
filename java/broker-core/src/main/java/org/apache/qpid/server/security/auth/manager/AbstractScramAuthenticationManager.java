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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.login.AccountNotFoundException;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.xml.bind.DatatypeConverter;

import org.apache.qpid.server.configuration.updater.Task;
import org.apache.qpid.server.configuration.updater.VoidTaskWithException;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.PasswordCredentialManagingAuthenticationProvider;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.auth.sasl.scram.ScramSaslServer;

public abstract class AbstractScramAuthenticationManager<X extends AbstractScramAuthenticationManager<X>>
        extends AbstractAuthenticationManager<X>
        implements PasswordCredentialManagingAuthenticationProvider<X>
{
    public static final String SCRAM_USER_TYPE = "scram";

    static final Charset ASCII = Charset.forName("ASCII");
    private final SecureRandom _random = new SecureRandom();

    private int _iterationCount = 4096;

    private Map<String, ScramAuthUser> _users = new ConcurrentHashMap<String, ScramAuthUser>();


    protected AbstractScramAuthenticationManager(final Map<String, Object> attributes, final Broker broker)
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
        return getMechanismName();
    }

    protected abstract String getMechanismName();

    @Override
    public SaslServer createSaslServer(final String mechanism,
                                       final String localFQDN,
                                       final Principal externalPrincipal)
            throws SaslException
    {
        return new ScramSaslServer(this, getMechanismName(), getHmacName(), getDigestName());
    }

    protected abstract String getDigestName();

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
                if(Arrays.equals(DatatypeConverter.parseBase64Binary(usernamePassword[1]),
                                 createSaltedPassword(salt, password)))
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
            SecretKeySpec key = new SecretKeySpec(keyBytes, getHmacName());
            Mac mac = Mac.getInstance(getHmacName());
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

    protected abstract String getHmacName();

    @Override
    public boolean createUser(final String username, final String password, final Map<String, String> attributes)
    {
        return runTask(new Task<Boolean>()
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
                    ScramAuthUser user = new ScramAuthUser(userAttrs, AbstractScramAuthenticationManager.this);
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

    org.apache.qpid.server.security.SecurityManager getSecurityManager()
    {
        return getBroker().getSecurityManager();
    }

    @Override
    public void deleteUser(final String user) throws AccountNotFoundException
    {
        runTask(new VoidTaskWithException<AccountNotFoundException>()
        {
            @Override
            public void execute() throws AccountNotFoundException
            {
                final ScramAuthUser authUser = getUser(user);
                if(authUser != null)
                {
                    authUser.setState(State.DELETED);
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
        runTask(new VoidTaskWithException<AccountNotFoundException>()
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
        return runTask(new Task<Map<String, Map<String, String>>>()
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

    void doDeleted()
    {
        deleted();
    }

    Map<String, ScramAuthUser> getUserMap()
    {
        return _users;
    }

}
