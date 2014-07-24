/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.server.security.auth.database;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.AccountNotFoundException;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.log4j.Logger;

import org.apache.qpid.server.security.auth.sasl.crammd5.CRAMMD5HashedInitialiser;
import org.apache.qpid.server.security.auth.sasl.crammd5.CRAMMD5HashedSaslServer;
import org.apache.qpid.server.security.auth.sasl.crammd5.CRAMMD5HexInitialiser;
import org.apache.qpid.server.security.auth.sasl.crammd5.CRAMMD5HexSaslServer;
import org.apache.qpid.server.security.auth.sasl.plain.PlainAdapterSaslServer;
import org.apache.qpid.server.security.auth.sasl.plain.PlainSaslServer;

/**
 * Represents a user database where the account information is stored in a simple flat file.
 *
 * The file is expected to be in the form: username:password username1:password1 ... usernamen:passwordn
 *
 * where a carriage return separates each username/password pair. Passwords are assumed to be in plain text.
 */
public class Base64MD5PasswordFilePrincipalDatabase extends AbstractPasswordFilePrincipalDatabase<HashedUser>
{
    private final Logger _logger = Logger.getLogger(Base64MD5PasswordFilePrincipalDatabase.class);
    private List<String> _mechanisms = Collections.unmodifiableList(Arrays.asList(CRAMMD5HashedSaslServer.MECHANISM,
                                                                                  CRAMMD5HexSaslServer.MECHANISM,
                                                                                  PlainSaslServer.MECHANISM));
    private final Map<String, CallbackHandler> _callbackHandlerMap = new HashMap<String, CallbackHandler>();

    public Base64MD5PasswordFilePrincipalDatabase()
    {
        CRAMMD5HashedInitialiser crammd5HashedInitialiser = new CRAMMD5HashedInitialiser();
        crammd5HashedInitialiser.initialise(this);
        _callbackHandlerMap.put(CRAMMD5HashedSaslServer.MECHANISM, crammd5HashedInitialiser.getCallbackHandler());

        CRAMMD5HexInitialiser crammd5HexInitialiser = new CRAMMD5HexInitialiser();
        crammd5HexInitialiser.initialise(this);
        _callbackHandlerMap.put(CRAMMD5HexSaslServer.MECHANISM, crammd5HexInitialiser.getCallbackHandler());

    }


    /**
     * Used to verify that the presented Password is correct. Currently only used by Management Console
     *
     * @param principal The principal to authenticate
     * @param password  The password to check
     *
     * @return true if password is correct
     *
     * @throws AccountNotFoundException if the principal cannot be found
     */
    public boolean verifyPassword(String principal, char[] password) throws AccountNotFoundException
    {
        char[] pwd = lookupPassword(principal);
        
        if (pwd == null)
        {
            throw new AccountNotFoundException("Unable to lookup the specified users password");
        }
        
        byte[] byteArray = new byte[password.length];
        int index = 0;
        for (char c : password)
        {
            byteArray[index++] = (byte) c;
        }
        
        byte[] MD5byteArray;
        try
        {
            MD5byteArray = HashedUser.getMD5(byteArray);
        }
        catch (Exception e1)
        {
            getLogger().warn("Unable to hash password for user '" + principal + "' for comparison");
            return false;
        }
        
        char[] hashedPassword = new char[MD5byteArray.length];

        index = 0;
        for (byte c : MD5byteArray)
        {
            hashedPassword[index++] = (char) c;
        }

        return compareCharArray(pwd, hashedPassword);
    }

    protected HashedUser createUserFromPassword(Principal principal, char[] passwd)
    {
        return new HashedUser(principal.getName(), passwd);
    }


    protected HashedUser createUserFromFileData(String[] result)
    {
        return new HashedUser(result);
    }

    protected Logger getLogger()
    {
        return _logger;
    }

    @Override
    public List<String> getMechanisms()
    {
        return _mechanisms;
    }

    @Override
    public SaslServer createSaslServer(String mechanism, String localFQDN, Principal externalPrincipal) throws SaslException
    {
        CallbackHandler callbackHandler = _callbackHandlerMap.get(mechanism);
        if(callbackHandler == null)
        {
            throw new SaslException("Unsupported mechanism: " + mechanism);
        }

        //The SaslServers simply delegate to the built in CRAM-MD5 SaslServer
        if(CRAMMD5HashedSaslServer.MECHANISM.equals(mechanism))
        {
            return new CRAMMD5HashedSaslServer(mechanism, "AMQP", localFQDN, null, callbackHandler);
        }
        else if(CRAMMD5HexSaslServer.MECHANISM.equals(mechanism))
        {
            return new CRAMMD5HexSaslServer(mechanism, "AMQP", localFQDN, null, callbackHandler);
        }
        else if(PlainSaslServer.MECHANISM.equals(mechanism))
        {
            return new PlainAdapterSaslServer(new PlainAdapterSaslServer.PasswordValidator()
            {
                @Override
                public boolean validatePassword(final String user, final String password)
                {
                    try
                    {
                        return verifyPassword(user, password.toCharArray());
                    }
                    catch (AccountNotFoundException e)
                    {
                        return false;
                    }
                }
            });
        }

        throw new SaslException("Unsupported mechanism: " + mechanism);
    }
}
