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
import java.util.Arrays;
import java.util.List;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;
import org.apache.qpid.server.security.auth.sasl.anonymous.AnonymousInitialiser;
import org.apache.qpid.server.security.auth.sasl.anonymous.AnonymousSaslServer;

public class AnonymousAuthenticationManager implements AuthenticationManager
{
    private static final Logger _logger = Logger.getLogger(AnonymousAuthenticationManager.class);

    private static final AnonymousInitialiser SASL_INITIALISER = new AnonymousInitialiser();

    private static final String ANONYMOUS = SASL_INITIALISER.getMechanismName();

    private static final Principal ANONYMOUS_PRINCIPAL = new UsernamePrincipal("ANONYMOUS");

    private static final Subject ANONYMOUS_SUBJECT = new Subject();
    static
    {
        ANONYMOUS_SUBJECT.getPrincipals().add(ANONYMOUS_PRINCIPAL);
    }

    private static final AuthenticationResult ANONYMOUS_AUTHENTICATION = new AuthenticationResult(ANONYMOUS_SUBJECT);


    private static CallbackHandler _callbackHandler = SASL_INITIALISER.getCallbackHandler();

    static final AnonymousAuthenticationManager INSTANCE = new AnonymousAuthenticationManager();

    public static class AnonymousAuthenticationManagerConfiguration extends ConfigurationPlugin
    {

        public static final ConfigurationPluginFactory FACTORY =
                new ConfigurationPluginFactory()
                {
                    public List<String> getParentPaths()
                    {
                        return Arrays.asList("security.anonymous-auth-manager");
                    }

                    public ConfigurationPlugin newInstance(final String path, final Configuration config) throws ConfigurationException
                    {
                        final ConfigurationPlugin instance = new AnonymousAuthenticationManagerConfiguration();

                        instance.setConfiguration(path, config);
                        return instance;
                    }
                };

        public String[] getElementsProcessed()
        {
            return new String[0];
        }

        public void validateConfiguration() throws ConfigurationException
        {
        }

        }


    public static final AuthenticationManagerPluginFactory<AnonymousAuthenticationManager> FACTORY = new AuthenticationManagerPluginFactory<AnonymousAuthenticationManager>()
    {
        public AnonymousAuthenticationManager newInstance(final ConfigurationPlugin config) throws ConfigurationException
        {
            AnonymousAuthenticationManagerConfiguration configuration =
                    config == null
                            ? null
                            : (AnonymousAuthenticationManagerConfiguration) config.getConfiguration(AnonymousAuthenticationManagerConfiguration.class.getName());

            // If there is no configuration for this plugin then don't load it.
            if (configuration == null)
            {
                _logger.info("No authentication-manager configuration found for AnonymousAuthenticationManager");
                return null;
            }
            return INSTANCE;
        }

        public Class<AnonymousAuthenticationManager> getPluginClass()
        {
            return AnonymousAuthenticationManager.class;
        }

        public String getPluginName()
        {
            return AnonymousAuthenticationManager.class.getName();
        }
    };


    private AnonymousAuthenticationManager()
    {
    }

    @Override
    public void initialise()
    {

    }

    @Override
    public String getMechanisms()
    {
        return ANONYMOUS;
    }

    @Override
    public SaslServer createSaslServer(String mechanism, String localFQDN) throws SaslException
    {
        if(ANONYMOUS.equals(mechanism))
        {
            return new AnonymousSaslServer();
        }
        else
        {
            throw new SaslException("Unknown mechanism: " + mechanism);
        }
    }

    @Override
    public AuthenticationResult authenticate(SaslServer server, byte[] response)
    {
        try
        {
            // Process response from the client
            byte[] challenge = server.evaluateResponse(response != null ? response : new byte[0]);

            if (server.isComplete())
            {
                return ANONYMOUS_AUTHENTICATION;
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
    public AuthenticationResult authenticate(String username, String password)
    {
        return ANONYMOUS_AUTHENTICATION;
    }

    @Override
    public CallbackHandler getHandler(String mechanism)
    {
        if(ANONYMOUS.equals(mechanism))
        {
            return _callbackHandler;
        }
        else
        {
            return null;
        }
    }

    @Override
    public void close()
    {
    }

    @Override
    public void configure(ConfigurationPlugin config) throws ConfigurationException
    {
    }
}
