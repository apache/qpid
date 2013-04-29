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
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.AccountNotFoundException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.log4j.Logger;
import org.apache.qpid.server.security.auth.sasl.amqplain.AmqPlainInitialiser;
import org.apache.qpid.server.security.auth.sasl.amqplain.AmqPlainSaslServer;
import org.apache.qpid.server.security.auth.sasl.crammd5.CRAMMD5Initialiser;
import org.apache.qpid.server.security.auth.sasl.plain.PlainInitialiser;
import org.apache.qpid.server.security.auth.sasl.plain.PlainSaslServer;

/**
 * Represents a user database where the account information is stored in a simple flat file.
 *
 * The file is expected to be in the form: username:password username1:password1 ... usernamen:passwordn
 *
 * where a carriage return separates each username/password pair. Passwords are assumed to be in plain text.
 */
public class PlainPasswordFilePrincipalDatabase extends AbstractPasswordFilePrincipalDatabase<PlainUser>
{

    private final Logger _logger = Logger.getLogger(PlainPasswordFilePrincipalDatabase.class);
    private final Map<String, CallbackHandler> _callbackHandlerMap = new HashMap<String, CallbackHandler>();
    private String _mechanismsString;

    public PlainPasswordFilePrincipalDatabase()
    {
        AmqPlainInitialiser amqPlainInitialiser = new AmqPlainInitialiser();
        amqPlainInitialiser.initialise(this);
        _callbackHandlerMap.put(AmqPlainSaslServer.MECHANISM, amqPlainInitialiser.getCallbackHandler());

        PlainInitialiser plainInitialiser = new PlainInitialiser();
        plainInitialiser.initialise(this);
        _callbackHandlerMap.put(PlainSaslServer.MECHANISM, plainInitialiser.getCallbackHandler());

        CRAMMD5Initialiser crammd5Initialiser = new CRAMMD5Initialiser();
        crammd5Initialiser.initialise(this);
        _callbackHandlerMap.put(CRAMMD5Initialiser.MECHANISM, crammd5Initialiser.getCallbackHandler());

        _mechanismsString = AmqPlainSaslServer.MECHANISM + " " + PlainSaslServer.MECHANISM + " " + CRAMMD5Initialiser.MECHANISM;
    }


    /**
     * Used to verify that the presented Password is correct. Currently only used by Management Console
     *
     * @param principal The principal to authenticate
     * @param password  The plaintext password to check
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
            throw new AccountNotFoundException("Unable to lookup the specfied users password");
        }

        return compareCharArray(pwd, password);

    }

    protected PlainUser createUserFromPassword(Principal principal, char[] passwd)
    {
        return new PlainUser(principal.getName(), passwd);
    }


    @Override
    protected PlainUser createUserFromFileData(String[] result)
    {
        return new PlainUser(result);
    }


    protected Logger getLogger()
    {
        return _logger;
    }


    @Override
    public String getMechanisms()
    {
        return _mechanismsString;
    }

    @Override
    public SaslServer createSaslServer(String mechanism, String localFQDN, Principal externalPrincipal) throws SaslException
    {
        CallbackHandler callbackHandler = _callbackHandlerMap.get(mechanism);
        if(callbackHandler == null)
        {
            throw new SaslException("Unsupported mechanism: " + mechanism);
        }

        if(CRAMMD5Initialiser.MECHANISM.equals(mechanism))
        {
            //simply delegate to the built in CRAM-MD5 SaslServer
            return Sasl.createSaslServer(mechanism, "AMQP", localFQDN, null, callbackHandler);
        }
        else if(PlainSaslServer.MECHANISM.equals(mechanism))
        {
            return new PlainSaslServer(callbackHandler);
        }
        else if(AmqPlainSaslServer.MECHANISM.equals(mechanism))
        {
            return new AmqPlainSaslServer(callbackHandler);
        }

        throw new SaslException("Unsupported mechanism: " + mechanism);
    }
}
