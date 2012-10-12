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
 *
 */
package org.apache.qpid.server.security.auth.manager;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.common.Closeable;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.group.GroupPrincipalAccessor;

/**
 * A concrete implementation of {@link IAuthenticationManagerRegistry} that registers all {@link AuthenticationManager}
 * instances defined in the configuration, building an optional mapping between port number and AuthenticationManager.
 *
 * <p>The default AuthenticationManager is either the one nominated as default within the configuration with
 * {@link ServerConfiguration#getDefaultAuthenticationManager()}, or if there is only one, it is implicitly
 * the default.</p>
 *
 * <p>It is important to {@link #close()} the registry after use and this allows the AuthenticationManagers
 * to reverse any security registrations they have performed.</p>
 */
public class AuthenticationManagerRegistry implements Closeable, IAuthenticationManagerRegistry
{
    private final Map<String,AuthenticationManager> _classToAuthManagerMap = new HashMap<String,AuthenticationManager>();
    private final SubjectCreator _defaultSubjectCreator;
    private final Map<Integer, SubjectCreator> _portToSubjectCreatorMap;
    private final List<RegistryChangeListener> _listeners =
            Collections.synchronizedList(new ArrayList<RegistryChangeListener>());
    private final QpidServiceLoader<AuthenticationManagerFactory> _authManagerFactoryServiceLoader;

    public AuthenticationManagerRegistry(ServerConfiguration serverConfiguration, GroupPrincipalAccessor groupPrincipalAccessor)
    throws ConfigurationException
    {
        this(serverConfiguration, groupPrincipalAccessor, new QpidServiceLoader<AuthenticationManagerFactory>());
    }

    // Exists as separate constructor for unit testing purposes
    AuthenticationManagerRegistry(ServerConfiguration serverConfiguration, GroupPrincipalAccessor groupPrincipalAccessor, QpidServiceLoader<AuthenticationManagerFactory> authManagerFactoryServiceLoader)
            throws ConfigurationException
    {
        _authManagerFactoryServiceLoader = authManagerFactoryServiceLoader;

        boolean willClose = true;
        try
        {
            createAuthManagers(serverConfiguration.getConfig());

            if(_classToAuthManagerMap.isEmpty())
            {
                throw new ConfigurationException("No authentication managers configured within the configuration file.");
            }

            _defaultSubjectCreator = createDefaultSubectCreator(serverConfiguration, groupPrincipalAccessor);

            _portToSubjectCreatorMap = createPortToSubjectCreatorMap(serverConfiguration, groupPrincipalAccessor);
            willClose = false;
        }
        finally
        {
            // if anyConfigurationExceptionthing went wrong whilst configuring the registry, try to close all the AuthentcationManagers instantiated so far.
            // This is done to allow the AuthenticationManager to undo any security registrations that they have performed.
            if (willClose)
            {
                close();
            }
        }
    }

    @Override
    public SubjectCreator getSubjectCreator(SocketAddress address)
    {
        SubjectCreator subjectCreator =
                address instanceof InetSocketAddress
                        ? _portToSubjectCreatorMap.get(((InetSocketAddress)address).getPort())
                        : null;

        return subjectCreator == null ? _defaultSubjectCreator : subjectCreator;
    }

    @Override
    public void close()
    {
        for (AuthenticationManager authManager : _classToAuthManagerMap.values())
        {
            authManager.close();
        }
    }

    private void createAuthManagers(Configuration config)
    {
        Configuration securityConfiguration = config.subset("security");

        for(AuthenticationManagerFactory factory : _authManagerFactoryServiceLoader.atLeastOneInstanceOf(AuthenticationManagerFactory.class))
        {
            AuthenticationManager plugin = factory.createInstance(securityConfiguration);
            if(plugin != null)
            {
                validateAndInitialiseAuthenticationManager(plugin);
            }
        }
    }

    private void validateAndInitialiseAuthenticationManager(AuthenticationManager authenticationManager)
    {
        // TODO Should be a user-defined name rather than the classname.
        final String authManagerName = authenticationManager.getClass().getSimpleName();
        if (_classToAuthManagerMap.containsKey(authManagerName))
        {
            throw new RuntimeException("Cannot configure more than one authentication manager with name "
                    + authManagerName + ".");
        }

        authenticationManager.initialise();

        _classToAuthManagerMap.put(authManagerName, authenticationManager);
    }

    private SubjectCreator createDefaultSubectCreator(
            ServerConfiguration serverConfiguration, GroupPrincipalAccessor groupAccessor)
            throws ConfigurationException
    {
        final AuthenticationManager defaultAuthenticationManager;
        if(_classToAuthManagerMap.size() == 1)
        {
            defaultAuthenticationManager = _classToAuthManagerMap.values().iterator().next();
        }
        else if(serverConfiguration.getDefaultAuthenticationManager() != null)
        {
            defaultAuthenticationManager = _classToAuthManagerMap.get(serverConfiguration.getDefaultAuthenticationManager());
            if(defaultAuthenticationManager == null)
            {
                throw new ConfigurationException("No authentication managers configured of type "
                                                 + serverConfiguration.getDefaultAuthenticationManager()
                                                 + " which is specified as the default.  Available managers are: "
                                                 + _classToAuthManagerMap.keySet());
            }
        }
        else
        {
            throw new ConfigurationException("If more than one authentication manager is configured a default MUST be specified.");
        }
        return new SubjectCreator(defaultAuthenticationManager, groupAccessor);
    }

    private Map<Integer, SubjectCreator> createPortToSubjectCreatorMap(
            ServerConfiguration serverConfiguration, GroupPrincipalAccessor groupPrincipalAccessor)
            throws ConfigurationException
    {
        Map<Integer,SubjectCreator> portToSubjectCreatorMap = new HashMap<Integer, SubjectCreator>();

        for(Map.Entry<Integer,String> portMapping : serverConfiguration.getPortAuthenticationMappings().entrySet())
        {

            AuthenticationManager authenticationManager = _classToAuthManagerMap.get(portMapping.getValue());
            if(authenticationManager == null)
            {
                throw new ConfigurationException("Unknown authentication manager class " + portMapping.getValue() +
                                                " configured for port " + portMapping.getKey());
            }

            SubjectCreator subjectCreator = new SubjectCreator(authenticationManager, groupPrincipalAccessor);
            portToSubjectCreatorMap.put(portMapping.getKey(), subjectCreator);
        }

        return portToSubjectCreatorMap;
    }

    @Override
    public Map<String, AuthenticationManager> getAvailableAuthenticationManagers()
    {
        return Collections.unmodifiableMap(new HashMap<String, AuthenticationManager>(_classToAuthManagerMap));
    }

    @Override
    public void addRegistryChangeListener(RegistryChangeListener listener)
    {
        _listeners.add(listener);
    }

}
