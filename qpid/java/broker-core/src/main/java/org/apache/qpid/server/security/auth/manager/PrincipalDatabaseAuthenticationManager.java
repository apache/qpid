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
package org.apache.qpid.server.security.auth.manager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.Principal;

import javax.security.auth.login.AccountNotFoundException;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;

public class PrincipalDatabaseAuthenticationManager implements AuthenticationManager
{
    private final PrincipalDatabase _principalDatabase;
    private final String _passwordFile;

    public PrincipalDatabaseAuthenticationManager(PrincipalDatabase pd, String passwordFile)
    {
        _principalDatabase = pd;
        _passwordFile = passwordFile;
    }

    public void initialise()
    {
        try
        {
            _principalDatabase.open(new File(_passwordFile));
        }
        catch (FileNotFoundException e)
        {
            throw new IllegalConfigurationException("Exception opening password database: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new IllegalConfigurationException("Cannot use password database at :" + _passwordFile, e);
        }
    }

    public String getMechanisms()
    {
        return _principalDatabase.getMechanisms();
    }

    public SaslServer createSaslServer(String mechanism, String localFQDN, Principal externalPrincipal) throws SaslException
    {
        return _principalDatabase.createSaslServer(mechanism, localFQDN, externalPrincipal);
    }

    /**
     * @see org.apache.qpid.server.security.auth.manager.AuthenticationManager#authenticate(SaslServer, byte[])
     */
    public AuthenticationResult authenticate(SaslServer server, byte[] response)
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

    /**
     * @see org.apache.qpid.server.security.auth.manager.AuthenticationManager#authenticate(String, String)
     */
    public AuthenticationResult authenticate(final String username, final String password)
    {
        try
        {
            if (_principalDatabase.verifyPassword(username, password.toCharArray()))
            {
                return new AuthenticationResult(new UsernamePrincipal(username));
            }
            else
            {
                return new AuthenticationResult(AuthenticationStatus.CONTINUE);
            }
        }
        catch (AccountNotFoundException e)
        {
            return new AuthenticationResult(AuthenticationStatus.CONTINUE);
        }
    }

    public void close()
    {

    }

    public PrincipalDatabase getPrincipalDatabase()
    {
        return _principalDatabase;
    }

    @Override
    public void onCreate()
    {
        try
        {
            File passwordFile = new File(_passwordFile);
            if (!passwordFile.exists())
            {
                passwordFile.createNewFile();
            }
            else if (!passwordFile.canRead())
            {
                throw new IllegalConfigurationException("Cannot read password file" + _passwordFile + ". Check permissions.");
            }
        }
        catch (IOException e)
        {
            throw new IllegalConfigurationException("Cannot use password database at :" + _passwordFile, e);
        }
    }

    @Override
    public void onDelete()
    {
        File file = new File(_passwordFile);
        if (file.exists() && file.isFile())
        {
            file.delete();
        }
    }
}
