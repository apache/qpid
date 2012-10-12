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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import junit.framework.TestCase;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.group.GroupPrincipalAccessor;

public class AuthenticationManagerRegistryTest extends TestCase
{
    @SuppressWarnings("unchecked")
    private QpidServiceLoader<AuthenticationManagerFactory> _authManagerServiceLoader = mock(QpidServiceLoader.class);

    private ServerConfiguration _serverConfiguration = mock(ServerConfiguration.class);
    private Configuration _serverCommonsConfig = mock(Configuration.class);
    private Configuration _securityCommonsConfig = mock(Configuration.class);

    private GroupPrincipalAccessor _groupPrincipalAccessor = mock(GroupPrincipalAccessor.class);

    private TestAuthenticationManager1 _testAuthManager1 = new TestAuthenticationManager1();
    private TestAuthenticationManager2 _testAuthManager2 = new TestAuthenticationManager2();

    private AuthenticationManagerFactory _testAuthManager1Factory = newMockFactoryProducing(_testAuthManager1);
    private AuthenticationManagerFactory _testAuthManager2Factory = newMockFactoryProducing(_testAuthManager2);

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        when(_serverConfiguration.getConfig()).thenReturn(_serverCommonsConfig);
        when(_serverCommonsConfig.subset("security")).thenReturn(_securityCommonsConfig);
    }

    public void testNoAuthenticationManagerFactoryPluginsFound() throws Exception
    {
        String noServicesMessage = "mock exception - no services found";
        when(_authManagerServiceLoader.atLeastOneInstanceOf(AuthenticationManagerFactory.class)).thenThrow(new RuntimeException(noServicesMessage));
        try
        {
            new AuthenticationManagerRegistry(_serverConfiguration, _groupPrincipalAccessor, _authManagerServiceLoader);
            fail("Exception not thrown");
        }
        catch (RuntimeException ce)
        {
            // PASS
            assertEquals(noServicesMessage, ce.getMessage());
        }
    }

    public void testSameAuthenticationManagerSpecifiedTwice() throws Exception
    {
        List<AuthenticationManagerFactory> duplicateFactories = Arrays.asList(_testAuthManager1Factory, _testAuthManager1Factory);

        when(_authManagerServiceLoader.atLeastOneInstanceOf(AuthenticationManagerFactory.class)).thenReturn(duplicateFactories);

        try
        {
            new AuthenticationManagerRegistry(_serverConfiguration, _groupPrincipalAccessor, _authManagerServiceLoader);
            fail("Exception not thrown");
        }
        catch (RuntimeException e)
        {
            assertTrue(e.getMessage().contains("Cannot configure more than one authentication manager with name"));
            assertTrue(e.getMessage().contains(TestAuthenticationManager1.class.getSimpleName()));
        }
    }

    public void testMultipleAuthenticationManagersSpecifiedButNoDefaultSpecified() throws Exception
    {
        List<AuthenticationManagerFactory> factoryList = Arrays.asList(_testAuthManager1Factory, _testAuthManager2Factory);

        when(_authManagerServiceLoader.atLeastOneInstanceOf(AuthenticationManagerFactory.class)).thenReturn(factoryList);
        when(_serverConfiguration.getDefaultAuthenticationManager()).thenReturn(null);

        try
        {
            new AuthenticationManagerRegistry(_serverConfiguration, _groupPrincipalAccessor, _authManagerServiceLoader);
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

        List<AuthenticationManagerFactory> factoryList = Arrays.asList(_testAuthManager1Factory, _testAuthManager2Factory);

        when(_authManagerServiceLoader.atLeastOneInstanceOf(AuthenticationManagerFactory.class)).thenReturn(factoryList);
        when(_serverConfiguration.getDefaultAuthenticationManager()).thenReturn(myDefaultAuthManagerSimpleClassName);

        try
        {
            new AuthenticationManagerRegistry(_serverConfiguration, _groupPrincipalAccessor, _authManagerServiceLoader);
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

        List<AuthenticationManagerFactory> factoryList = Arrays.asList(_testAuthManager1Factory);

        when(_authManagerServiceLoader.atLeastOneInstanceOf(AuthenticationManagerFactory.class)).thenReturn(factoryList);
        when(_serverConfiguration.getPortAuthenticationMappings()).thenReturn(Collections.singletonMap(portNumber, myDefaultAuthManagerSimpleClassName));

        try
        {
            new AuthenticationManagerRegistry(_serverConfiguration, _groupPrincipalAccessor, _authManagerServiceLoader);
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
        List<AuthenticationManagerFactory> factoryList = Arrays.asList(_testAuthManager1Factory);

        when(_authManagerServiceLoader.atLeastOneInstanceOf(AuthenticationManagerFactory.class)).thenReturn(factoryList);

        AuthenticationManagerRegistry registry = new AuthenticationManagerRegistry(_serverConfiguration, _groupPrincipalAccessor, _authManagerServiceLoader);

        SubjectCreator subjectCreator = registry.getSubjectCreator(new InetSocketAddress(1234));
        assertSubjectCreatorUsingExpectedAuthManager(_testAuthManager1, subjectCreator);
    }

    public void testGetAuthenticationManagerForNonInetSocketAddress() throws Exception
    {
        List<AuthenticationManagerFactory> factoryList = Arrays.asList(_testAuthManager1Factory);

        when(_authManagerServiceLoader.atLeastOneInstanceOf(AuthenticationManagerFactory.class)).thenReturn(factoryList);

        AuthenticationManagerRegistry registry = new AuthenticationManagerRegistry(_serverConfiguration, _groupPrincipalAccessor, _authManagerServiceLoader);

        SubjectCreator subjectCreator = registry.getSubjectCreator(mock(SocketAddress.class));
        assertSubjectCreatorUsingExpectedAuthManager(_testAuthManager1, subjectCreator);
    }

    public void testGetAuthenticationManagerWithMultipleAuthenticationManager() throws Exception
    {
        List<AuthenticationManagerFactory> factoryList = Arrays.asList(_testAuthManager1Factory, _testAuthManager2Factory);

        AuthenticationManager defaultAuthManger = _testAuthManager1;
        AuthenticationManager unmappedAuthManager = _testAuthManager2;

        String defaultAuthMangerName = defaultAuthManger.getClass().getSimpleName();
        String mappedAuthManagerName = unmappedAuthManager.getClass().getSimpleName();

        int unmappedPortNumber = 1234;
        int mappedPortNumber = 1235;

        when(_authManagerServiceLoader.atLeastOneInstanceOf(AuthenticationManagerFactory.class)).thenReturn(factoryList);
        when(_serverConfiguration.getDefaultAuthenticationManager()).thenReturn(defaultAuthMangerName);
        when(_serverConfiguration.getPortAuthenticationMappings()).thenReturn(Collections.singletonMap(mappedPortNumber, mappedAuthManagerName));

        AuthenticationManagerRegistry registry = new AuthenticationManagerRegistry(_serverConfiguration, _groupPrincipalAccessor, _authManagerServiceLoader);

        SubjectCreator subjectCreatorForDefaultAuthManager = registry.getSubjectCreator(new InetSocketAddress(unmappedPortNumber));
        assertSubjectCreatorUsingExpectedAuthManager(defaultAuthManger, subjectCreatorForDefaultAuthManager);

        SubjectCreator subjectCreatorForUnmappedAuthManager = registry.getSubjectCreator(new InetSocketAddress(mappedPortNumber));
        assertSubjectCreatorUsingExpectedAuthManager(unmappedAuthManager, subjectCreatorForUnmappedAuthManager);
    }

    public void testAuthenticationManagersAreClosed() throws Exception
    {
        AuthenticationManager defaultAuthManager = mock(MockAuthenticationManager1.class);
        AuthenticationManagerFactory defaultAuthManagerFactory = newMockFactoryProducing(defaultAuthManager);

        AuthenticationManager authManager2 = mock(MockAuthenticationManager2.class);
        AuthenticationManagerFactory authManagerFactory2 = newMockFactoryProducing(authManager2);

        List<AuthenticationManagerFactory> factoryList = Arrays.asList(defaultAuthManagerFactory, authManagerFactory2);

        String defaultAuthMangerName = defaultAuthManager.getClass().getSimpleName();
        when(_serverConfiguration.getDefaultAuthenticationManager()).thenReturn(defaultAuthMangerName);
        when(_authManagerServiceLoader.atLeastOneInstanceOf(AuthenticationManagerFactory.class)).thenReturn(factoryList);

        AuthenticationManagerRegistry registry = new AuthenticationManagerRegistry(_serverConfiguration, _groupPrincipalAccessor, _authManagerServiceLoader);

        registry.close();

        verify(defaultAuthManager).close();
        verify(authManager2).close();
    }

    public void testAlreadyInitialisedAuthManagersAreClosedWhenAnotherAuthManagerInitFails() throws Exception
    {
        AuthenticationManager authManger1 = mock(MockAuthenticationManager1.class);
        AuthenticationManagerFactory authManager1Factory = newMockFactoryProducing(authManger1);

        AuthenticationManager authManager2 = mock(MockAuthenticationManager2.class);
        AuthenticationManagerFactory authManagerFactory2 = newMockFactoryProducing(authManager2);

        List<AuthenticationManagerFactory> factoryList = Arrays.asList(authManager1Factory, authManagerFactory2);
        when(_authManagerServiceLoader.atLeastOneInstanceOf(AuthenticationManagerFactory.class)).thenReturn(factoryList);

        doThrow(new RuntimeException("mock auth manager 2 init exception")).when(authManager2).initialise();

        try
        {
            new AuthenticationManagerRegistry(_serverConfiguration, _groupPrincipalAccessor, _authManagerServiceLoader);
            fail("Exception not thrown");
        }
        catch (RuntimeException e)
        {
            // PASS
        }

        verify(authManger1).close();
    }


    private AuthenticationManagerFactory newMockFactoryProducing(AuthenticationManager myAuthManager)
    {
        AuthenticationManagerFactory myAuthManagerFactory = mock(AuthenticationManagerFactory.class);
        when(myAuthManagerFactory.createInstance(_securityCommonsConfig)).thenReturn(myAuthManager);
        return myAuthManagerFactory;
    }

    private void assertSubjectCreatorUsingExpectedAuthManager(AuthenticationManager expectedAuthenticationManager, SubjectCreator subjectCreator)
    {
        assertEquals(
                "The subject creator should be using " + expectedAuthenticationManager + " so its mechanisms should match",
                expectedAuthenticationManager.getMechanisms(),
                subjectCreator.getMechanisms());
    }

    /** @see MockAuthenticationManager2 */
    private interface MockAuthenticationManager1 extends AuthenticationManager
    {
    }

    /**
     * I only exist so that mock implementations of me have a different class to {@link MockAuthenticationManager1},
     * as mandated by {@link AuthenticationManagerRegistry}
     */
    private interface MockAuthenticationManager2 extends AuthenticationManager
    {
    }

    /**
     * We use a stub rather than a mock because {@link AuthenticationManagerRegistry} relies on {@link AuthenticationManager} class names,
     * which are hard to predict for mocks.
     */
    private abstract class TestAuthenticationManager implements AuthenticationManager
    {
        @Override
        public void close()
        {
            // no-op
        }

        @Override
        public void initialise()
        {
            // no-op
        }

        @Override
        public SaslServer createSaslServer(String mechanism, String localFQDN, Principal externalPrincipal) throws SaslException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public AuthenticationResult authenticate(SaslServer server, byte[] response)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public AuthenticationResult authenticate(String username, String password)
        {
            throw new UnsupportedOperationException();
        }
    }

    private class TestAuthenticationManager1 extends TestAuthenticationManager
    {
        @Override
        public String getMechanisms()
        {
            return "MECHANISMS1";
        }
    }

    private class TestAuthenticationManager2 extends TestAuthenticationManager
    {
        /**
         * Needs to different from {@link TestAuthenticationManager1#getMechanisms()} to aid our test assertions.
         */
        @Override
        public String getMechanisms()
        {
            return "MECHANISMS2";
        }
    }
}
