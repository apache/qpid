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
package org.apache.qpid.server.security.auth.manager;

import java.security.Provider;
import java.security.Security;

import javax.security.auth.Subject;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;
import org.apache.qpid.server.util.InternalBrokerBaseCase;

/**
 *
 * Tests the public methods of PrincipalDatabaseAuthenticationManager.
 *
 */
public class PrincipalDatabaseAuthenticationManagerTest extends InternalBrokerBaseCase
{
    private PrincipalDatabaseAuthenticationManager _manager = null;
    
    /**
     * @see org.apache.qpid.server.util.InternalBrokerBaseCase#tearDown()
     */
    @Override
    public void tearDown() throws Exception
    {
        super.tearDown();
        if (_manager != null)
        {
            _manager.close();
        }
    }

    /**
     * @see org.apache.qpid.server.util.InternalBrokerBaseCase#setUp()
     */
    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        
        _manager = new PrincipalDatabaseAuthenticationManager();
    }

    /**
     * Tests that the PDAM registers SASL mechanisms correctly with the runtime.
     */
    public void testRegisteredMechanisms() throws Exception
    {
        assertNotNull(_manager.getMechanisms());
        // relies on those mechanisms attached to PropertiesPrincipalDatabaseManager
        assertEquals("PLAIN CRAM-MD5", _manager.getMechanisms());
    
        Provider qpidProvider = Security.getProvider(PrincipalDatabaseAuthenticationManager.PROVIDER_NAME);
        assertNotNull(qpidProvider);
    }

    /**
     * Tests that the SASL factory method createSaslServer correctly
     * returns a non-null implementation.
     */
    public void testSaslMechanismCreation() throws Exception
    {
        SaslServer server = _manager.createSaslServer("CRAM-MD5", "localhost");
        assertNotNull(server);
        // Merely tests the creation of the mechanism. Mechanisms themselves are tested
        // by their own tests.
    }
    
    /**
     * Tests that the authenticate method correctly interprets an
     * authentication success.
     * 
     */
    public void testSaslAuthenticationSuccess() throws Exception
    {
        SaslServer testServer = createTestSaslServer(true, false);
        
        AuthenticationResult result = _manager.authenticate(testServer, "12345".getBytes());
        final Subject subject = result.getSubject();
        assertTrue(subject.getPrincipals().contains(new UsernamePrincipal("guest")));
        assertEquals(AuthenticationStatus.SUCCESS, result.getStatus());
    }

    /**
     * 
     * Tests that the authenticate method correctly interprets an
     * authentication not complete.
     * 
     */
    public void testSaslAuthenticationNotCompleted() throws Exception
    {
        SaslServer testServer = createTestSaslServer(false, false);
        
        AuthenticationResult result = _manager.authenticate(testServer, "12345".getBytes());
        assertNull(result.getSubject());
        assertEquals(AuthenticationStatus.CONTINUE, result.getStatus());
    }

    /**
     * 
     * Tests that the authenticate method correctly interprets an
     * authentication error.
     * 
     */
    public void testSaslAuthenticationError() throws Exception
    {
        SaslServer testServer = createTestSaslServer(false, true);
        
        AuthenticationResult result = _manager.authenticate(testServer, "12345".getBytes());
        assertNull(result.getSubject());
        assertEquals(AuthenticationStatus.ERROR, result.getStatus());
    }

    /**
     * Tests that the authenticate method correctly interprets an
     * authentication success.
     *
     */
    public void testNonSaslAuthenticationSuccess() throws Exception
    {
        AuthenticationResult result = _manager.authenticate("guest", "guest");
        final Subject subject = result.getSubject();
        assertFalse("Subject should not be set read-only", subject.isReadOnly());
        assertTrue(subject.getPrincipals().contains(new UsernamePrincipal("guest")));
        assertEquals(AuthenticationStatus.SUCCESS, result.getStatus());
    }

    /**
     * Tests that the authenticate method correctly interprets an
     * authentication success.
     *
     */
    public void testNonSaslAuthenticationNotCompleted() throws Exception
    {
        AuthenticationResult result = _manager.authenticate("guest", "wrongpassword");
        assertNull(result.getSubject());
        assertEquals(AuthenticationStatus.CONTINUE, result.getStatus());
    }
    
    /**
     * Tests the ability to de-register the provider.
     */
    public void testClose() throws Exception
    {
        assertEquals("PLAIN CRAM-MD5", _manager.getMechanisms());
        assertNotNull(Security.getProvider(PrincipalDatabaseAuthenticationManager.PROVIDER_NAME));
        
        _manager.close();
        
        // Check provider has been removed.
        assertNull(_manager.getMechanisms());
        assertNull(Security.getProvider(PrincipalDatabaseAuthenticationManager.PROVIDER_NAME));
        _manager = null;
    }

    /**
     * Test SASL implementation used to test the authenticate() method.
     */
    private SaslServer createTestSaslServer(final boolean complete, final boolean throwSaslException)
    {
        return new SaslServer()
        {

            @Override
            public String getMechanismName()
            {
                return null;
            }

            @Override
            public byte[] evaluateResponse(byte[] response) throws SaslException
            {
                if (throwSaslException)
                {
                    throw new SaslException("Mocked exception");
                }
                return null;
            }

            @Override
            public boolean isComplete()
            {
                return complete;
            }

            @Override
            public String getAuthorizationID()
            {
                return complete ? "guest" : null;
            }

            @Override
            public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException
            {
                return null;
            }

            @Override
            public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException
            {
                return null;
            }

            @Override
            public Object getNegotiatedProperty(String propName)
            {
                return null;
            }

            @Override
            public void dispose() throws SaslException
            {
            }
        };
    }
}
