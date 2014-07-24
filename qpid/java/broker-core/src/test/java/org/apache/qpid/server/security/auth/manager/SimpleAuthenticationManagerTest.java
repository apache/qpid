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

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.sasl.SaslUtil;
import org.apache.qpid.server.security.auth.sasl.plain.PlainSaslServer;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;

public class SimpleAuthenticationManagerTest extends QpidTestCase
{
    private static final String TEST_USER = "testUser";
    private static final String TEST_PASSWORD = "testPassword";
    private AuthenticationProvider _authenticationManager;

    public void setUp() throws Exception
    {
        super.setUp();
        Map<String,Object> authManagerAttrs = new HashMap<String, Object>();
        authManagerAttrs.put(AuthenticationProvider.NAME,"MANAGEMENT_MODE_AUTHENTICATION");
        authManagerAttrs.put(AuthenticationProvider.ID, UUID.randomUUID());
        final SimpleAuthenticationManager authManager = new SimpleAuthenticationManager(authManagerAttrs,
                                                                                        BrokerTestHelper.createBrokerMock());
        authManager.addUser(TEST_USER, TEST_PASSWORD);
        _authenticationManager = authManager;

    }

    public void testGetMechanisms()
    {
        List<String> mechanisms = _authenticationManager.getMechanisms();
        assertEquals("Unexpected number of mechanisms", 2, mechanisms.size());
        assertTrue("PLAIN was not present", mechanisms.contains("PLAIN"));
        assertTrue("CRAM-MD5 was not present", mechanisms.contains("CRAM-MD5"));
    }

    public void testCreateSaslServerForUnsupportedMechanisms() throws Exception
    {
        String[] unsupported = new String[] { "EXTERNAL", "CRAM-MD5-HEX", "CRAM-MD5-HASHED", "ANONYMOUS", "GSSAPI"};
        for (int i = 0; i < unsupported.length; i++)
        {
            String mechanism = unsupported[i];
            try
            {
                _authenticationManager.createSaslServer(mechanism, "test", null);
                fail("Mechanism " + mechanism + " should not be supported by SimpleAuthenticationManager");
            }
            catch (SaslException e)
            {
                // pass
            }
        }
    }

    public void testAuthenticateWithPlainSaslServer() throws Exception
    {
        AuthenticationResult result = authenticatePlain(TEST_USER, TEST_PASSWORD);
        assertAuthenticated(result);
    }

    public void testAuthenticateWithPlainSaslServerInvalidPassword() throws Exception
    {
        AuthenticationResult result = authenticatePlain(TEST_USER, "wrong-password");
        assertUnauthenticated(result);
    }

    public void testAuthenticateWithPlainSaslServerInvalidUsername() throws Exception
    {
        AuthenticationResult result = authenticatePlain("wrong-user", TEST_PASSWORD);
        assertUnauthenticated(result);
    }

    public void testAuthenticateWithCramMd5SaslServer() throws Exception
    {
        AuthenticationResult result = authenticateCramMd5(TEST_USER, TEST_PASSWORD);
        assertAuthenticated(result);
    }

    public void testAuthenticateWithCramMd5SaslServerInvalidPassword() throws Exception
    {
        AuthenticationResult result = authenticateCramMd5(TEST_USER, "wrong-password");
        assertUnauthenticated(result);
    }

    public void testAuthenticateWithCramMd5SaslServerInvalidUsername() throws Exception
    {
        AuthenticationResult result = authenticateCramMd5("wrong-user", TEST_PASSWORD);
        assertUnauthenticated(result);
    }

    public void testAuthenticateValidCredentials()
    {
        AuthenticationResult result = _authenticationManager.authenticate(TEST_USER, TEST_PASSWORD);
        assertEquals("Unexpected authentication result", AuthenticationStatus.SUCCESS, result.getStatus());
        assertAuthenticated(result);
    }

    public void testAuthenticateInvalidPassword()
    {
        AuthenticationResult result = _authenticationManager.authenticate(TEST_USER, "invalid");
        assertUnauthenticated(result);
    }

    public void testAuthenticateInvalidUserName()
    {
        AuthenticationResult result = _authenticationManager.authenticate("invalid", TEST_PASSWORD);
        assertUnauthenticated(result);
    }

    private void assertAuthenticated(AuthenticationResult result)
    {
        assertEquals("Unexpected authentication result", AuthenticationStatus.SUCCESS, result.getStatus());
        Principal principal = result.getMainPrincipal();
        assertEquals("Unexpected principal name", TEST_USER, principal.getName());
        Set<Principal> principals = result.getPrincipals();
        assertEquals("Unexpected principals size", 1, principals.size());
        assertEquals("Unexpected principal name", TEST_USER, principals.iterator().next().getName());
    }

    private void assertUnauthenticated(AuthenticationResult result)
    {
        assertEquals("Unexpected authentication result", AuthenticationStatus.ERROR, result.getStatus());
        assertNull("Unexpected principal", result.getMainPrincipal());
        Set<Principal> principals = result.getPrincipals();
        assertEquals("Unexpected principals size", 0, principals.size());
    }

    private AuthenticationResult authenticatePlain(String userName, String userPassword) throws SaslException, Exception
    {
        PlainSaslServer ss = (PlainSaslServer) _authenticationManager.createSaslServer("PLAIN", "test", null);
        byte[] response = SaslUtil.generatePlainClientResponse(userName, userPassword);

        return _authenticationManager.authenticate(ss, response);
    }

    private AuthenticationResult authenticateCramMd5(String userName, String userPassword) throws SaslException, Exception
    {
        SaslServer ss = _authenticationManager.createSaslServer("CRAM-MD5", "test", null);
        byte[] challenge = ss.evaluateResponse(new byte[0]);
        byte[] response = SaslUtil.generateCramMD5ClientResponse(userName, userPassword, challenge);

        AuthenticationResult result = _authenticationManager.authenticate(ss, response);
        return result;
    }
}
