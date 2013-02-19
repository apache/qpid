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

import java.security.Principal;
import org.apache.log4j.Logger;

import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.sasl.AuthenticationProviderInitialiser;
import org.apache.qpid.server.security.auth.sasl.JCAProvider;
import org.apache.qpid.server.security.auth.UsernamePrincipal;

import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.AccountNotFoundException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;

import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;


/**
 * Concrete implementation of the AuthenticationManager that determines if supplied
 * user credentials match those appearing in a PrincipalDatabase.   The implementation
 * of the PrincipalDatabase is determined from the configuration.
 */
public class PrincipalDatabaseAuthenticationManager implements AuthenticationManager
{

    private static final Logger _logger = Logger.getLogger(PrincipalDatabaseAuthenticationManager.class);

    /** The list of mechanisms, in the order in which they are configured (i.e. preferred order) */
    private String _mechanisms;

    /** Maps from the mechanism to the callback handler to use for handling those requests */
    private final Map<String, CallbackHandler> _callbackHandlerMap = new HashMap<String, CallbackHandler>();

    /**
     * Maps from the mechanism to the properties used to initialise the server. See the method Sasl.createSaslServer for
     * details of the use of these properties. This map is populated during initialisation of each provider.
     */
    private final Map<String, Map<String, ?>> _serverCreationProperties = new HashMap<String, Map<String, ?>>();

    private final PrincipalDatabase _principalDatabase;

    public PrincipalDatabaseAuthenticationManager(PrincipalDatabase pd)
    {
        _principalDatabase = pd;
    }

    public void initialise()
    {
        final Map<String, Class<? extends SaslServerFactory>> providerMap = new TreeMap<String, Class<? extends SaslServerFactory>>();

        initialiseAuthenticationMechanisms(providerMap, _principalDatabase);

        if (providerMap.size() > 0)
        {
            // Ensure we are used before the defaults
            if (Security.insertProviderAt(new JCAProvider(PROVIDER_NAME, providerMap), 1) == -1)
            {
                _logger.error("Unable to load custom SASL providers. Qpid custom SASL authenticators unavailable.");
            }
            else
            {
                _logger.info("Additional SASL providers successfully registered.");
            }
        }
        else
        {
            _logger.warn("No additional SASL providers registered.");
        }
    }

    private void initialiseAuthenticationMechanisms(Map<String, Class<? extends SaslServerFactory>> providerMap, PrincipalDatabase database)
    {
        if (database == null || database.getMechanisms().size() == 0)
        {
            _logger.warn("No Database or no mechanisms to initialise authentication");
            return;
        }

        for (Map.Entry<String, AuthenticationProviderInitialiser> mechanism : database.getMechanisms().entrySet())
        {
            initialiseAuthenticationMechanism(mechanism.getKey(), mechanism.getValue(), providerMap);
        }
    }

    private void initialiseAuthenticationMechanism(String mechanism, AuthenticationProviderInitialiser initialiser,
                                                   Map<String, Class<? extends SaslServerFactory>> providerMap)
    {
        if (_mechanisms == null)
        {
            _mechanisms = mechanism;
        }
        else
        {
            // simple append should be fine since the number of mechanisms is small and this is a one time initialisation
            _mechanisms = _mechanisms + " " + mechanism;
        }
        _callbackHandlerMap.put(mechanism, initialiser.getCallbackHandler());
        _serverCreationProperties.put(mechanism, initialiser.getProperties());
        Class<? extends SaslServerFactory> factory = initialiser.getServerFactoryClassForJCARegistration();
        if (factory != null)
        {
            providerMap.put(mechanism, factory);
        }
        _logger.info("Initialised " + mechanism + " SASL provider successfully");
    }

    public String getMechanisms()
    {
        return _mechanisms;
    }

    public SaslServer createSaslServer(String mechanism, String localFQDN, Principal externalPrincipal) throws SaslException
    {
        Map<String, ?> properties = _serverCreationProperties.get(mechanism);
        CallbackHandler callbackHandler = _callbackHandlerMap.get(mechanism);

        return Sasl.createSaslServer(mechanism, "AMQP", localFQDN, properties,
                                     callbackHandler);
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
        _mechanisms = null;
        Security.removeProvider(PROVIDER_NAME);
    }

    public PrincipalDatabase getPrincipalDatabase()
    {
        return _principalDatabase;
    }
}
