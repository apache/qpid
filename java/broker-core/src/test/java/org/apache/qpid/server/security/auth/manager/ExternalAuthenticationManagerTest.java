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

    public void testCreateSaslServerUsingFullDN() throws Exception
    {
        createSaslServerTestImpl(_managerUsingFullDN);
    }

    public void createSaslServerTestImpl(AuthenticationManager manager) throws Exception
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

    /**
     * Test behaviour of the authentication when the useFullDN attribute is set true
     * and the username is taken directly as the externally supplied Principal
     */
    public void testAuthenticateWithFullDN() throws Exception
    {
        X500Principal principal = new X500Principal("CN=person, DC=example, DC=com");
        SaslServer saslServer = _managerUsingFullDN.createSaslServer("EXTERNAL", "example.example.com", principal);

        AuthenticationResult result = _managerUsingFullDN.authenticate(saslServer, new byte[0]);
        assertNotNull(result);
        assertEquals("Expected authentication to be successful",
                     AuthenticationResult.AuthenticationStatus.SUCCESS,
                     result.getStatus());

        assertOnlyContainsWrapped(principal, result.getPrincipals());

        saslServer = _managerUsingFullDN.createSaslServer("EXTERNAL", "example.example.com", null);
        result = _managerUsingFullDN.authenticate(saslServer, new byte[0]);

        assertNotNull(result);
        assertEquals("Expected authentication to be unsuccessful",
                     AuthenticationResult.AuthenticationStatus.ERROR,
                     result.getStatus());
    }

    /**
     * Test behaviour of the authentication when parsing the username from
     * the Principals DN as <CN>@<DC1>.<DC2>.<DC3>....<DCN>
     */
    public void testAuthenticateWithUsernameBasedOnCNAndDC() throws Exception
    {
        X500Principal principal;
        SaslServer saslServer;
        AuthenticationResult result;
        UsernamePrincipal expectedPrincipal;

        // DN contains only CN
        principal = new X500Principal("CN=person");
        expectedPrincipal = new UsernamePrincipal("person");
        saslServer = _manager.createSaslServer("EXTERNAL", "example.example.com", principal);

        result = _manager.authenticate(saslServer, new byte[0]);
        assertNotNull(result);
        assertEquals("Expected authentication to be successful",
                     AuthenticationResult.AuthenticationStatus.SUCCESS,
                     result.getStatus());
        assertOnlyContainsWrapped(expectedPrincipal, result.getPrincipals());

        // Null principal
        saslServer = _manager.createSaslServer("EXTERNAL", "example.example.com", null);
        result = _manager.authenticate(saslServer, new byte[0]);

        assertNotNull(result);
        assertEquals("Expected authentication to be unsuccessful",
                AuthenticationResult.AuthenticationStatus.ERROR,
                result.getStatus());

        // DN doesn't contain CN
        principal = new X500Principal("DC=example, DC=com, O=My Company Ltd, L=Newbury, ST=Berkshire, C=GB");
        saslServer = _manager.createSaslServer("EXTERNAL", "example.example.com", principal);
        result = _manager.authenticate(saslServer, new byte[0]);

        assertNotNull(result);
        assertEquals("Expected authentication to be unsuccessful",
                AuthenticationResult.AuthenticationStatus.ERROR,
                result.getStatus());

        // DN contains empty CN
        principal = new X500Principal("CN=, DC=example, DC=com, O=My Company Ltd, L=Newbury, ST=Berkshire, C=GB");
        saslServer = _manager.createSaslServer("EXTERNAL", "example.example.com", principal);
        result = _manager.authenticate(saslServer, new byte[0]);

        assertNotNull(result);
        assertEquals("Expected authentication to be unsuccessful",
                AuthenticationResult.AuthenticationStatus.ERROR,
                result.getStatus());

        // DN contains CN and DC
        principal = new X500Principal("CN=person, DC=example, DC=com");
        expectedPrincipal = new UsernamePrincipal("person@example.com");
        saslServer = _manager.createSaslServer("EXTERNAL", "example.example.com", principal);

        result = _manager.authenticate(saslServer, new byte[0]);
        assertNotNull(result);
        assertEquals("Expected authentication to be successful",
                AuthenticationResult.AuthenticationStatus.SUCCESS,
                result.getStatus());
        assertOnlyContainsWrapped(expectedPrincipal, result.getPrincipals());

        // DN contains CN and DC and other components
        principal = new X500Principal("CN=person, DC=example, DC=com, O=My Company Ltd, L=Newbury, ST=Berkshire, C=GB");
        expectedPrincipal = new UsernamePrincipal("person@example.com");
        saslServer = _manager.createSaslServer("EXTERNAL", "example.example.com", principal);

        result = _manager.authenticate(saslServer, new byte[0]);
        assertNotNull(result);
        assertEquals("Expected authentication to be successful",
                AuthenticationResult.AuthenticationStatus.SUCCESS,
                result.getStatus());
        assertOnlyContainsWrapped(expectedPrincipal, result.getPrincipals());

        // DN contains CN and DC and other components
        principal = new X500Principal("CN=person, O=My Company Ltd, L=Newbury, ST=Berkshire, C=GB");
        expectedPrincipal = new UsernamePrincipal("person");
        saslServer = _manager.createSaslServer("EXTERNAL", "example.example.com", principal);

        result = _manager.authenticate(saslServer, new byte[0]);
        assertNotNull(result);
        assertEquals("Expected authentication to be successful",
                AuthenticationResult.AuthenticationStatus.SUCCESS,
                result.getStatus());
        assertOnlyContainsWrapped(expectedPrincipal, result.getPrincipals());
    }

}
