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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.common.Closeable;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.plugins.Plugin;
import org.apache.qpid.server.plugins.PluginManager;
import org.apache.qpid.server.security.SecurityManager.SecurityConfiguration;

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
    private final AuthenticationManager _defaultAuthenticationManager;
    private final Map<Integer,AuthenticationManager> _portToAuthenticationManagerMap;
    private final List<RegistryChangeListener> _listeners =
            Collections.synchronizedList(new ArrayList<RegistryChangeListener>());

    public AuthenticationManagerRegistry(ServerConfiguration serverConfiguration, PluginManager _pluginManager)
    throws ConfigurationException
    {
        final Collection<AuthenticationManagerPluginFactory<? extends Plugin>> factories = _pluginManager.getAuthenticationManagerPlugins().values();

        if (factories.size() == 0)
        {
            throw new ConfigurationException("No authentication manager factory plugins found. Check the desired authentication" +
                    " manager plugin has been placed in the plugins directory.");
        }

        final SecurityConfiguration securityConfiguration = serverConfiguration.getConfiguration(SecurityConfiguration.class.getName());

        boolean willClose = true;
        try
        {
            createAuthenticationManagersRejectingDuplicates(factories, securityConfiguration);

            if(_classToAuthManagerMap.isEmpty())
            {
                throw new ConfigurationException("No authentication managers configured within the configuration file.");
            }

            _defaultAuthenticationManager = getDefaultAuthenticationManager(serverConfiguration);

            _portToAuthenticationManagerMap = getPortToAuthenticationManagerMap(serverConfiguration);
            willClose = false;
        }
        finally
        {
            // if anything went wrong whilst configuring the registry, try to close all the AuthentcationManagers instantiated so far.
            // This is done to allow the AuthenticationManager to undo any security registrations that they have performed.
            if (willClose)
            {
                close();
            }
        }
    }

    @Override
    public AuthenticationManager getAuthenticationManager(SocketAddress address)
    {
        AuthenticationManager authManager =
                address instanceof InetSocketAddress
                        ? _portToAuthenticationManagerMap.get(((InetSocketAddress)address).getPort())
                        : null;

        return authManager == null ? _defaultAuthenticationManager : authManager;
    }

    @Override
    public void close()
    {
        for (AuthenticationManager authManager : _classToAuthManagerMap.values())
        {
            authManager.close();
        }
    }

    private void createAuthenticationManagersRejectingDuplicates(
            final Collection<AuthenticationManagerPluginFactory<? extends Plugin>> factories,
            final SecurityConfiguration securityConfiguration)
            throws ConfigurationException
    {
        for(AuthenticationManagerPluginFactory<? extends Plugin> factory : factories)
        {
            final AuthenticationManager tmp = factory.newInstance(securityConfiguration);
            if (tmp != null)
            {
                if(_classToAuthManagerMap.containsKey(tmp.getClass().getSimpleName()))
                {
                    throw new ConfigurationException("Cannot configure more than one authentication manager of type "
                                                     + tmp.getClass().getSimpleName() + "."
                                                     + " Remove configuration for one of the authentication managers.");
                }
                _classToAuthManagerMap.put(tmp.getClass().getSimpleName(),tmp);

                for(RegistryChangeListener listener : _listeners)
                {
                    listener.authenticationManagerRegistered(tmp);
                }
            }
        }
    }

    private AuthenticationManager getDefaultAuthenticationManager(
            ServerConfiguration serverConfiguration)
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
        return defaultAuthenticationManager;
    }

    private Map<Integer,AuthenticationManager> getPortToAuthenticationManagerMap(
            ServerConfiguration serverConfiguration)
            throws ConfigurationException
    {
        Map<Integer,AuthenticationManager> portToAuthenticationManagerMap = new HashMap<Integer, AuthenticationManager>();

        for(Map.Entry<Integer,String> portMapping : serverConfiguration.getPortAuthenticationMappings().entrySet())
        {

            AuthenticationManager authenticationManager = _classToAuthManagerMap.get(portMapping.getValue());
            if(authenticationManager == null)
            {
                throw new ConfigurationException("Unknown authentication manager class " + portMapping.getValue() +
                                                " configured for port " + portMapping.getKey());
            }
            portToAuthenticationManagerMap.put(portMapping.getKey(), authenticationManager);
        }

        return portToAuthenticationManagerMap;
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
