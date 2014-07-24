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
package org.apache.qpid.server.security.auth.sasl.plain;

import java.io.IOException;

import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.security.auth.AuthenticationResult;

public class PlainAdapterSaslServer implements SaslServer
{
    public static interface PasswordValidator
    {
        boolean validatePassword(String user, String password);
    }



    public static final String MECHANISM = "PLAIN";
    private final PasswordValidator _passwordValidator;

    private String _authorizationId;

    private boolean _complete = false;

    public PlainAdapterSaslServer(final PasswordValidator passwordValidator)
    {
        _passwordValidator = passwordValidator;
    }

    public PlainAdapterSaslServer(final AuthenticationProvider authProvider)
    {
        this(new PasswordValidator()
            {
                @Override
                public boolean validatePassword(final String user, final String password)
                {
                    AuthenticationResult authenticationResult = authProvider.authenticate(user, password);
                    return authenticationResult != null && authenticationResult.getStatus() == AuthenticationResult.AuthenticationStatus.SUCCESS;
                }
            });
    }

    public String getMechanismName()
    {
        return MECHANISM;
    }

    public byte[] evaluateResponse(byte[] response) throws SaslException
    {
        int authzidNullPosition = findNullPosition(response, 0);
        if (authzidNullPosition < 0)
        {
            throw new SaslException("Invalid PLAIN encoding, authzid null terminator not found");
        }
        int authcidNullPosition = findNullPosition(response, authzidNullPosition + 1);
        if (authcidNullPosition < 0)
        {
            throw new SaslException("Invalid PLAIN encoding, authcid null terminator not found");
        }

        PlainPasswordCallback passwordCb;
        AuthorizeCallback authzCb;

        try
        {
            // we do not currently support authcid in any meaningful way
            String authzid = new String(response, authzidNullPosition + 1, authcidNullPosition - authzidNullPosition - 1, "utf8");

            // TODO: should not get pwd as a String but as a char array...
            int passwordLen = response.length - authcidNullPosition - 1;
            String pwd = new String(response, authcidNullPosition + 1, passwordLen, "utf8");


            if(_passwordValidator.validatePassword(authzid, pwd))
            {
                _authorizationId = authzid;
                _complete = true;
            }
            else
            {
                throw new SaslException("Authentication failed");
            }

            return null;

        }
        catch (IOException e)
        {
            if(e instanceof SaslException)
            {
                throw (SaslException) e;
            }
            throw new SaslException("Error processing data: " + e, e);
        }


    }



    private int findNullPosition(byte[] response, int startPosition)
    {
        int position = startPosition;
        while (position < response.length)
        {
            if (response[position] == (byte) 0)
            {
                return position;
            }
            position++;
        }
        return -1;
    }

    public boolean isComplete()
    {
        return _complete;
    }

    public String getAuthorizationID()
    {
        return _authorizationId;
    }

    public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException
    {
        throw new SaslException("Unsupported operation");
    }

    public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException
    {
        throw new SaslException("Unsupported operation");
    }

    public Object getNegotiatedProperty(String propName)
    {
        return null;
    }

    public void dispose() throws SaslException
    {
    }

}
