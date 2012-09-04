/*
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
 */
package org.apache.qpid.server.security.auth.manager;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.auth.sasl.external.ExternalSaslServer;

public class ExternalAuthenticationManager implements AuthenticationManager
{
    private static final Logger _logger = Logger.getLogger(ExternalAuthenticationManager.class);

    private static final String EXTERNAL = "EXTERNAL";

    static final ExternalAuthenticationManager INSTANCE = new ExternalAuthenticationManager();

    public static class ExternalAuthenticationManagerConfiguration extends ConfigurationPlugin
    {

        public static final ConfigurationPluginFactory FACTORY =
                new ConfigurationPluginFactory()
                {
                    public List<String> getParentPaths()
                    {
                        return Arrays.asList("security.external-auth-manager");
                    }

                    public ConfigurationPlugin newInstance(final String path, final Configuration config) throws ConfigurationException
                    {
                        final ConfigurationPlugin instance = new ExternalAuthenticationManagerConfiguration();

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


    public static final AuthenticationManagerPluginFactory<ExternalAuthenticationManager> FACTORY = new AuthenticationManagerPluginFactory<ExternalAuthenticationManager>()
    {
        public ExternalAuthenticationManager newInstance(final ConfigurationPlugin config) throws ConfigurationException
        {
            ExternalAuthenticationManagerConfiguration configuration =
                    config == null
                            ? null
                            : (ExternalAuthenticationManagerConfiguration) config.getConfiguration(ExternalAuthenticationManagerConfiguration.class.getName());

            // If there is no configuration for this plugin then don't load it.
            if (configuration == null)
            {
                _logger.info("No authentication-manager configuration found for ExternalAuthenticationManager");
                return null;
            }
            return INSTANCE;
        }

        public Class<ExternalAuthenticationManager> getPluginClass()
        {
            return ExternalAuthenticationManager.class;
        }

        public String getPluginName()
        {
            return ExternalAuthenticationManager.class.getName();
        }
    };


    private ExternalAuthenticationManager()
    {
    }

    @Override
    public void initialise()
    {

    }

    @Override
    public String getMechanisms()
    {
        return EXTERNAL;
    }

    @Override
    public SaslServer createSaslServer(String mechanism, String localFQDN, Principal externalPrincipal) throws SaslException
    {
        if(EXTERNAL.equals(mechanism))
        {
            return new ExternalSaslServer(externalPrincipal);
        }
        else
        {
            throw new SaslException("Unknown mechanism: " + mechanism);
        }
    }

    @Override
    public AuthenticationResult authenticate(SaslServer server, byte[] response)
    {
        // Process response from the client
        try
        {
            server.evaluateResponse(response != null ? response : new byte[0]);

            Principal principal = ((ExternalSaslServer)server).getAuthenticatedPrincipal();

            if(principal != null)
            {
                return new AuthenticationResult(principal);
            }
            else
            {
                return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR);
            }
        }
        catch (SaslException e)
        {
            return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR,e);
        }

    }

    @Override
    public AuthenticationResult authenticate(String username, String password)
    {
        return new AuthenticationResult(new UsernamePrincipal(username));
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
