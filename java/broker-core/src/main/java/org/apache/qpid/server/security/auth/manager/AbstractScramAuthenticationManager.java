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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.xml.bind.DatatypeConverter;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.PasswordCredentialManagingAuthenticationProvider;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.auth.sasl.plain.PlainAdapterSaslServer;
import org.apache.qpid.server.security.auth.sasl.scram.ScramSaslServer;

public abstract class AbstractScramAuthenticationManager<X extends AbstractScramAuthenticationManager<X>>
        extends ConfigModelPasswordManagingAuthenticationProvider<X>
        implements PasswordCredentialManagingAuthenticationProvider<X>
{

    public static final String PLAIN = "PLAIN";
    private final SecureRandom _random = new SecureRandom();

    private int _iterationCount = 4096;


    protected AbstractScramAuthenticationManager(final Map<String, Object> attributes, final Broker broker)
    {
        super(attributes, broker);
    }

    @Override
    public List<String> getMechanisms()
    {
        return Collections.unmodifiableList(Arrays.asList(getMechanismName(), PLAIN));
    }

    protected abstract String getMechanismName();

    @Override
    public SaslServer createSaslServer(final String mechanism,
                                       final String localFQDN,
                                       final Principal externalPrincipal)
            throws SaslException
    {
        if(getMechanismName().equals(mechanism))
        {
            return new ScramSaslServer(this, getMechanismName(), getHmacName(), getDigestName());
        }
        else if(PLAIN.equals(mechanism))
        {
            return new PlainAdapterSaslServer(this);
        }
        else
        {
            throw new SaslException("Unknown mechanism: " + mechanism);
        }
    }

    protected abstract String getDigestName();

    @Override
    public AuthenticationResult authenticate(final String username, final String password)
    {
        ManagedUser user = getUser(username);
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
            catch (IllegalArgumentException e)
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
        ManagedUser user = getUser(username);

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
        ManagedUser user = getUser(username);
        if(user == null)
        {
            throw new SaslException("Authentication Failed");
        }
        else
        {
            return DatatypeConverter.parseBase64Binary(user.getPassword().split(",")[1]);
        }
    }

    private byte[] createSaltedPassword(byte[] salt, String password)
    {
        Mac mac = createShaHmac(password.getBytes(ASCII));

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

    private Mac createShaHmac(final byte[] keyBytes)
    {
        try
        {
            SecretKeySpec key = new SecretKeySpec(keyBytes, getHmacName());
            Mac mac = Mac.getInstance(getHmacName());
            mac.init(key);
            return mac;
        }
        catch (NoSuchAlgorithmException | InvalidKeyException e)
        {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    protected abstract String getHmacName();

    @Override
    protected String createStoredPassword(final String password)
    {
        byte[] salt = new byte[32];
        _random.nextBytes(salt);
        byte[] passwordBytes = createSaltedPassword(salt, password);
        return DatatypeConverter.printBase64Binary(salt) + "," + DatatypeConverter.printBase64Binary(passwordBytes);
    }

    @Override
    void validateUser(final ManagedUser managedUser)
    {
        if(!ASCII.newEncoder().canEncode(managedUser.getName()))
        {
            throw new IllegalArgumentException("User names are restricted to characters in the ASCII charset");
        }
    }
}
