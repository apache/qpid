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

import static org.mockito.Mockito.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.plugins.Plugin;
import org.apache.qpid.server.plugins.PluginManager;
import org.apache.qpid.server.security.SecurityManager.SecurityConfiguration;
import org.mockito.Mockito;

import junit.framework.TestCase;

public class AuthenticationManagerRegistryTest extends TestCase
{
    private static final Map<String, AuthenticationManagerPluginFactory<? extends Plugin>> EMPTY_PLUGINMAP = Collections.emptyMap();

    private PluginManager _pluginManager = Mockito.mock(PluginManager.class);
    private ServerConfiguration _serverConfiguration = Mockito.mock(ServerConfiguration.class);
    private SecurityConfiguration _securityConfiguration = Mockito.mock(SecurityConfiguration.class);

    private List<AuthenticationManager> _allCreatedAuthManagers = new ArrayList<AuthenticationManager>();

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        // Setup server configuration to return mock security config.
        when(_serverConfiguration.getConfiguration(SecurityConfiguration.class.getName())).thenReturn(_securityConfiguration);
    }

    @Override
    protected void tearDown() throws Exception
    {
        try
        {
            verifyAllCreatedAuthManagersClosed();
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testNoAuthenticationManagerFactoryPluginsFound() throws Exception
    {
        when(_pluginManager.getAuthenticationManagerPlugins()).thenReturn(EMPTY_PLUGINMAP);
        try
        {
            new AuthenticationManagerRegistry(_serverConfiguration, _pluginManager);
            fail("Exception not thrown");
        }
        catch (ConfigurationException ce)
        {
            // PASS
            assertEquals("No authentication manager factory plugins found. Check the desired authentication manager plugin has been placed in the plugins directory.",
                         ce.getMessage());
        }
    }

    public void testSameAuthenticationManagerSpecifiedTwice() throws Exception
    {
        AuthenticationManagerPluginFactory<? extends Plugin> myAuthManagerFactory = newMockFactoryProducingMockAuthManagerImplementing(TestAuthenticationManager1.class);

        Map<String, AuthenticationManagerPluginFactory<? extends Plugin>> pluginMap = createPluginMap(myAuthManagerFactory, myAuthManagerFactory);

        when(_pluginManager.getAuthenticationManagerPlugins()).thenReturn(pluginMap);

        try
        {
            new AuthenticationManagerRegistry(_serverConfiguration, _pluginManager);
            fail("Exception not thrown");
        }
        catch (ConfigurationException ce)
        {
            // PASS
            assertEquals("Cannot configure more than one authentication manager of type " + myAuthManagerFactory.getPluginClass().getSimpleName() + ". Remove configuration for one of the authentication managers.",
                         ce.getMessage());
        }
    }

    public void testMultipleAuthenticationManagersSpecifiedButNoDefaultSpecified() throws Exception
    {
        AuthenticationManagerPluginFactory<? extends Plugin> myAuthManagerFactory1 = newMockFactoryProducingMockAuthManagerImplementing(TestAuthenticationManager1.class);
        AuthenticationManagerPluginFactory<? extends Plugin> myAuthManagerFactory2 = newMockFactoryProducingMockAuthManagerImplementing(TestAuthenticationManager2.class);

        Map<String, AuthenticationManagerPluginFactory<? extends Plugin>> pluginMap = createPluginMap(myAuthManagerFactory1, myAuthManagerFactory2);

        when(_pluginManager.getAuthenticationManagerPlugins()).thenReturn(pluginMap);
        when(_serverConfiguration.getDefaultAuthenticationManager()).thenReturn(null);

        try
        {
            new AuthenticationManagerRegistry(_serverConfiguration, _pluginManager);
            fail("Exception not thrown");
        }
        catch (ConfigurationException ce)
        {
            // PASS
            assertEquals("If more than one authentication manager is configured a default MUST be specified.",
                         ce.getMessage());
        }
    }

    public void testDefaultAuthenticationManagerNotKnown() throws Exception
    {
        String myDefaultAuthManagerSimpleClassName = "UnknownAuthenticationManager";

        AuthenticationManagerPluginFactory<? extends Plugin> myAuthManagerFactory1 = newMockFactoryProducingMockAuthManagerImplementing(TestAuthenticationManager1.class);
        AuthenticationManagerPluginFactory<? extends Plugin> myAuthManagerFactory2 = newMockFactoryProducingMockAuthManagerImplementing(TestAuthenticationManager2.class);

        Map<String, AuthenticationManagerPluginFactory<? extends Plugin>> pluginMap = createPluginMap(myAuthManagerFactory1, myAuthManagerFactory2);

        when(_pluginManager.getAuthenticationManagerPlugins()).thenReturn(pluginMap);
        when(_serverConfiguration.getDefaultAuthenticationManager()).thenReturn(myDefaultAuthManagerSimpleClassName);

        try
        {
            new AuthenticationManagerRegistry(_serverConfiguration, _pluginManager);
            fail("Exception not thrown");
        }
        catch (ConfigurationException ce)
        {
            // PASS
            assertTrue("Unexpected message " + ce.getMessage(),
                     ce.getMessage().startsWith("No authentication managers configured of type " + myDefaultAuthManagerSimpleClassName + " which is specified as the default"));
        }
    }

    public void testPortMappedToUnknownAuthenticationManager() throws Exception
    {
        String myDefaultAuthManagerSimpleClassName = "UnknownAuthenticationManager";
        int portNumber = 1234;

        AuthenticationManagerPluginFactory<? extends Plugin> myAuthManagerFactory1 = newMockFactoryProducingMockAuthManagerImplementing(TestAuthenticationManager1.class);

        Map<String, AuthenticationManagerPluginFactory<? extends Plugin>> pluginMap = createPluginMap(myAuthManagerFactory1);

        when(_pluginManager.getAuthenticationManagerPlugins()).thenReturn(pluginMap);
        when(_serverConfiguration.getPortAuthenticationMappings()).thenReturn(Collections.singletonMap(portNumber, myDefaultAuthManagerSimpleClassName));

        try
        {
            new AuthenticationManagerRegistry(_serverConfiguration, _pluginManager);
            fail("Exception not thrown");
        }
        catch (ConfigurationException ce)
        {
            // PASS
            assertEquals("Unknown authentication manager class " + myDefaultAuthManagerSimpleClassName + " configured for port " + portNumber, ce.getMessage());
        }
    }

    public void testGetAuthenticationManagerForInetSocketAddress() throws Exception
    {
        AuthenticationManagerPluginFactory<? extends Plugin> myAuthManagerFactory1 = newMockFactoryProducingMockAuthManagerImplementing(TestAuthenticationManager1.class);
        Map<String, AuthenticationManagerPluginFactory<? extends Plugin>> pluginMap = createPluginMap(myAuthManagerFactory1);

        when(_pluginManager.getAuthenticationManagerPlugins()).thenReturn(pluginMap);

        AuthenticationManagerRegistry registry = new AuthenticationManagerRegistry(_serverConfiguration, _pluginManager);

        AuthenticationManager authenticationManager = registry.getAuthenticationManagerFor(new InetSocketAddress(1234));
        assertEquals("TestAuthenticationManager1", authenticationManager.getMechanisms());

        registry.close();
    }

    public void testGetAuthenticationManagerForNonInetSocketAddress() throws Exception
    {
        AuthenticationManagerPluginFactory<? extends Plugin> myAuthManagerFactory1 = newMockFactoryProducingMockAuthManagerImplementing(TestAuthenticationManager1.class);
        Map<String, AuthenticationManagerPluginFactory<? extends Plugin>> pluginMap = createPluginMap(myAuthManagerFactory1);

        when(_pluginManager.getAuthenticationManagerPlugins()).thenReturn(pluginMap);

        AuthenticationManagerRegistry registry = new AuthenticationManagerRegistry(_serverConfiguration, _pluginManager);

        AuthenticationManager authenticationManager = registry.getAuthenticationManagerFor(mock(SocketAddress.class));
        assertEquals("TestAuthenticationManager1", authenticationManager.getMechanisms());

        registry.close();
    }

    public void testGetAuthenticationManagerWithMultipleAuthenticationManager() throws Exception
    {
        AuthenticationManagerPluginFactory<? extends Plugin> myAuthManagerFactory1 = newMockFactoryProducingMockAuthManagerImplementing(TestAuthenticationManager1.class);
        AuthenticationManagerPluginFactory<? extends Plugin> myAuthManagerFactory2 = newMockFactoryProducingMockAuthManagerImplementing(TestAuthenticationManager2.class);
        Map<String, AuthenticationManagerPluginFactory<? extends Plugin>> pluginMap = createPluginMap(myAuthManagerFactory1, myAuthManagerFactory2);

        String defaultAuthManger = myAuthManagerFactory1.getPluginName();
        int unmappedPortNumber = 1234;
        int mappedPortNumber = 1235;
        String mappedAuthManager = myAuthManagerFactory2.getPluginName();

        when(_pluginManager.getAuthenticationManagerPlugins()).thenReturn(pluginMap);
        when(_serverConfiguration.getDefaultAuthenticationManager()).thenReturn(defaultAuthManger);
        when(_serverConfiguration.getPortAuthenticationMappings()).thenReturn(Collections.singletonMap(mappedPortNumber, mappedAuthManager));

        AuthenticationManagerRegistry registry = new AuthenticationManagerRegistry(_serverConfiguration, _pluginManager);

        AuthenticationManager authenticationManager1 = registry.getAuthenticationManagerFor(new InetSocketAddress(unmappedPortNumber));
        assertEquals("TestAuthenticationManager1", authenticationManager1.getMechanisms());

        AuthenticationManager authenticationManager2 = registry.getAuthenticationManagerFor(new InetSocketAddress(mappedPortNumber));
        assertEquals("TestAuthenticationManager2", authenticationManager2.getMechanisms());

        registry.close();
    }

    public void testAuthenticationManagersAreClosed() throws Exception
    {
        AuthenticationManagerPluginFactory<? extends Plugin> myAuthManagerFactory1 = newMockFactoryProducingMockAuthManagerImplementing(TestAuthenticationManager1.class);
        AuthenticationManagerPluginFactory<? extends Plugin> myAuthManagerFactory2 = newMockFactoryProducingMockAuthManagerImplementing(TestAuthenticationManager2.class);
        Map<String, AuthenticationManagerPluginFactory<? extends Plugin>> pluginMap = createPluginMap(myAuthManagerFactory1, myAuthManagerFactory2);

        String defaultAuthManger = myAuthManagerFactory1.getPluginName();
        when(_pluginManager.getAuthenticationManagerPlugins()).thenReturn(pluginMap);
        when(_serverConfiguration.getDefaultAuthenticationManager()).thenReturn(defaultAuthManger);

        AuthenticationManagerRegistry registry = new AuthenticationManagerRegistry(_serverConfiguration, _pluginManager);

        registry.close();
    }

    private AuthenticationManagerPluginFactory<? extends Plugin> newMockFactoryProducingMockAuthManagerImplementing(Class<? extends AuthenticationManager> authManagerClazz)
            throws ConfigurationException
    {
        AuthenticationManager myAuthManager = mock(authManagerClazz);
        when(myAuthManager.getMechanisms()).thenReturn(authManagerClazz.getSimpleName());  // used to verify the getAuthenticationManagerFor returns expected impl.

        AuthenticationManagerPluginFactory myAuthManagerFactory = mock(AuthenticationManagerPluginFactory.class);
        when(myAuthManagerFactory.getPluginClass()).thenReturn(myAuthManager.getClass());
        when(myAuthManagerFactory.getPluginName()).thenReturn(myAuthManager.getClass().getSimpleName());
        when(myAuthManagerFactory.newInstance(_securityConfiguration)).thenReturn(myAuthManager);

        _allCreatedAuthManagers.add(myAuthManager);
        return myAuthManagerFactory;
    }

    private Map<String, AuthenticationManagerPluginFactory<? extends Plugin>> createPluginMap(
            AuthenticationManagerPluginFactory<? extends Plugin> myAuthManagerFactory)
    {
        return createPluginMap(myAuthManagerFactory, null);
    }

    private Map<String, AuthenticationManagerPluginFactory<? extends Plugin>> createPluginMap(
            AuthenticationManagerPluginFactory<? extends Plugin> authManagerFactory1,
            AuthenticationManagerPluginFactory<? extends Plugin> authManagerFactory2)
    {
        Map<String, AuthenticationManagerPluginFactory<? extends Plugin>> pluginMap = new HashMap<String, AuthenticationManagerPluginFactory<? extends Plugin>>();
        pluginMap.put("config.path.unused1", authManagerFactory1);
        if (authManagerFactory2 != null)
        {
            pluginMap.put("config.path.unused2", authManagerFactory2);
        }
        return pluginMap;
    }

    private void verifyAllCreatedAuthManagersClosed()
    {
        for (Iterator<AuthenticationManager> iterator = _allCreatedAuthManagers.iterator(); iterator.hasNext();)
        {
            AuthenticationManager authenticationManager = (AuthenticationManager) iterator.next();
            verify(authenticationManager).close();
        }
    }

    private interface TestAuthenticationManager1 extends AuthenticationManager
    {
    }

    private interface TestAuthenticationManager2 extends AuthenticationManager
    {
    }
}
