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

import static org.apache.qpid.server.security.auth.AuthenticatedPrincipalTestHelper.assertOnlyContainsWrapped;

import javax.security.auth.x500.X500Principal;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.test.utils.QpidTestCase;

public class ExternalAuthenticationManagerTest extends QpidTestCase
{
    private AuthenticationManager _manager = new ExternalAuthenticationManager(false);
    private AuthenticationManager _managerUsingFullDN = new ExternalAuthenticationManager(true);

    public void testGetMechanisms() throws Exception
    {
        assertEquals("EXTERNAL", _manager.getMechanisms());
    }

    public void testCreateSaslServer() throws Exception
    {
        createSaslServerTestImpl(_manager);
    }

    public void testAuthenticatePrincipalNull_CausesAuthError() throws Exception
    {
        SaslServer saslServer = _manager.createSaslServer("EXTERNAL", "example.example.com", null);
        AuthenticationResult result = _manager.authenticate(saslServer, new byte[0]);

        assertNotNull(result);
        assertEquals("Expected authentication to be unsuccessful",
                AuthenticationResult.AuthenticationStatus.ERROR,
                result.getStatus());
        assertNull(saslServer.getAuthorizationID());
    }

    public void testAuthenticatePrincipalNoCn_CausesAuthError() throws Exception
    {
        X500Principal principal = new X500Principal("DC=example, DC=com, O=My Company Ltd, L=Newbury, ST=Berkshire, C=GB");
        SaslServer saslServer = _manager.createSaslServer("EXTERNAL", "example.example.com", principal);
        AuthenticationResult result = _manager.authenticate(saslServer, new byte[0]);

        assertNotNull(result);
        assertEquals("Expected authentication to be unsuccessful",
                AuthenticationResult.AuthenticationStatus.ERROR,
                result.getStatus());
        assertNull(saslServer.getAuthorizationID());
    }

    public void testAuthenticatePrincipalEmptyCn_CausesAuthError() throws Exception
    {
        X500Principal principal = new X500Principal("CN=, DC=example, DC=com, O=My Company Ltd, L=Newbury, ST=Berkshire, C=GB");
        SaslServer saslServer = _manager.createSaslServer("EXTERNAL", "example.example.com", principal);
        AuthenticationResult result = _manager.authenticate(saslServer, new byte[0]);

        assertNotNull(result);
        assertEquals("Expected authentication to be unsuccessful",
                AuthenticationResult.AuthenticationStatus.ERROR,
                result.getStatus());
        assertNull(saslServer.getAuthorizationID());
    }

    public void testAuthenticatePrincipalCnOnly() throws Exception
    {
        X500Principal principal = new X500Principal("CN=person");
        UsernamePrincipal expectedPrincipal = new UsernamePrincipal("person");
        SaslServer saslServer = _manager.createSaslServer("EXTERNAL", "example.example.com", principal);

        AuthenticationResult result = _manager.authenticate(saslServer, new byte[0]);
        assertNotNull(result);
        assertEquals("Expected authentication to be successful",
                     AuthenticationResult.AuthenticationStatus.SUCCESS,
                     result.getStatus());
        assertOnlyContainsWrapped(expectedPrincipal, result.getPrincipals());
        assertEquals("person", saslServer.getAuthorizationID());
    }

    public void testAuthenticatePrincipalCnAndDc() throws Exception
    {
        X500Principal principal = new X500Principal("CN=person, DC=example, DC=com");
        UsernamePrincipal expectedPrincipal = new UsernamePrincipal("person@example.com");
        SaslServer saslServer = _manager.createSaslServer("EXTERNAL", "example.example.com", principal);

        AuthenticationResult result = _manager.authenticate(saslServer, new byte[0]);
        assertNotNull(result);
        assertEquals("Expected authentication to be successful",
                AuthenticationResult.AuthenticationStatus.SUCCESS,
                result.getStatus());
        assertOnlyContainsWrapped(expectedPrincipal, result.getPrincipals());
        assertEquals("person@example.com", saslServer.getAuthorizationID());
    }

    public void testAuthenticatePrincipalCnDc_OtherComponentsIgnored() throws Exception
    {
        X500Principal principal = new X500Principal("CN=person, DC=example, DC=com, O=My Company Ltd, L=Newbury, ST=Berkshire, C=GB");
        UsernamePrincipal expectedPrincipal = new UsernamePrincipal("person@example.com");
        SaslServer saslServer = _manager.createSaslServer("EXTERNAL", "example.example.com", principal);

        AuthenticationResult result = _manager.authenticate(saslServer, new byte[0]);
        assertNotNull(result);
        assertEquals("Expected authentication to be successful",
                AuthenticationResult.AuthenticationStatus.SUCCESS,
                result.getStatus());
        assertOnlyContainsWrapped(expectedPrincipal, result.getPrincipals());
        assertEquals("person@example.com", saslServer.getAuthorizationID());
    }

    public void testAuthenticatePrincipalCn_OtherComponentsIgnored() throws Exception
    {
        X500Principal principal = new X500Principal("CN=person, O=My Company Ltd, L=Newbury, ST=Berkshire, C=GB");
        UsernamePrincipal expectedPrincipal = new UsernamePrincipal("person");
        SaslServer saslServer = _manager.createSaslServer("EXTERNAL", "example.example.com", principal);

        AuthenticationResult result = _manager.authenticate(saslServer, new byte[0]);
        assertNotNull(result);
        assertEquals("Expected authentication to be successful",
                AuthenticationResult.AuthenticationStatus.SUCCESS,
                result.getStatus());
        assertOnlyContainsWrapped(expectedPrincipal, result.getPrincipals());
        assertEquals("person", saslServer.getAuthorizationID());
    }

    public void testFullDNMode_CreateSaslServer() throws Exception
    {
        createSaslServerTestImpl(_managerUsingFullDN);
    }

    public void testFullDNMode_Authenticate() throws Exception
    {
        X500Principal principal = new X500Principal("CN=person, DC=example, DC=com");
        SaslServer saslServer = _managerUsingFullDN.createSaslServer("EXTERNAL", "example.example.com", principal);

        AuthenticationResult result = _managerUsingFullDN.authenticate(saslServer, new byte[0]);
        assertNotNull(result);
        assertEquals("Expected authentication to be successful",
                     AuthenticationResult.AuthenticationStatus.SUCCESS,
                     result.getStatus());

        assertOnlyContainsWrapped(principal, result.getPrincipals());
        assertEquals("CN=person,DC=example,DC=com", saslServer.getAuthorizationID());
    }

    public void testFullDNMode_AuthenticatePrincipalNull_CausesAuthError() throws Exception
    {
        SaslServer saslServer = _managerUsingFullDN.createSaslServer("EXTERNAL", "example.example.com", null);
        AuthenticationResult result = _managerUsingFullDN.authenticate(saslServer, new byte[0]);

        assertNotNull(result);
        assertEquals("Expected authentication to be unsuccessful",
                     AuthenticationResult.AuthenticationStatus.ERROR,
                     result.getStatus());
        assertNull(saslServer.getAuthorizationID());
    }

    private void createSaslServerTestImpl(AuthenticationManager manager) throws Exception
    {
        SaslServer server = manager.createSaslServer("EXTERNAL", "example.example.com", null);

        assertEquals("Sasl Server mechanism name is not as expected", "EXTERNAL", server.getMechanismName());

        try
        {
            server = manager.createSaslServer("PLAIN", "example.example.com", null);
            fail("Expected creating SaslServer with incorrect mechanism to throw an exception");
        }
        catch (SaslException e)
        {
            // pass
        }
    }

}
