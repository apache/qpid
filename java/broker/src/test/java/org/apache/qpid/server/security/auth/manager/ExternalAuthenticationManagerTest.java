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
import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;

import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.database.PlainPasswordFilePrincipalDatabase;
import org.apache.qpid.server.util.InternalBrokerBaseCase;

public class ExternalAuthenticationManagerTest extends InternalBrokerBaseCase
{

    private AuthenticationManager _manager = null;

    public void setUp() throws Exception
    {
        _manager = ExternalAuthenticationManager.INSTANCE;
    }


    public void tearDown() throws Exception
    {
        if(_manager != null)
        {
            _manager = null;
        }
    }

    private ConfigurationPlugin getPlainDatabaseConfig() throws ConfigurationException
    {
        final ConfigurationPlugin config = new PrincipalDatabaseAuthenticationManager.PrincipalDatabaseAuthenticationManagerConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();
        xmlconfig.addProperty("pd-auth-manager.principal-database.class", PlainPasswordFilePrincipalDatabase.class.getName());

        // Create a CompositeConfiguration as this is what the broker uses
        CompositeConfiguration composite = new CompositeConfiguration();
        composite.addConfiguration(xmlconfig);
        config.setConfiguration("security", xmlconfig);
        return config;
    }


    public void testConfiguration() throws Exception
    {
        AuthenticationManager authenticationManager =
                ExternalAuthenticationManager.FACTORY.newInstance(getPlainDatabaseConfig());

        assertNull("ExternalAuthenticationManager unexpectedly created when not in config", authenticationManager);
    }

    public void testGetMechanisms() throws Exception
    {
        assertEquals("EXTERNAL", _manager.getMechanisms());
    }

    public void testCreateSaslServer() throws Exception
    {
        SaslServer server = _manager.createSaslServer("EXTERNAL", "example.example.com", null);

        assertEquals("Sasl Server mechanism name is not as expected", "EXTERNAL", server.getMechanismName());

        try
        {
            server = _manager.createSaslServer("PLAIN", "example.example.com", null);
            fail("Expected creating SaslServer with incorrect mechanism to throw an exception");
        }
        catch (SaslException e)
        {
            // pass
        }
    }

    public void testAuthenticate() throws Exception
    {
        X500Principal principal = new X500Principal("CN=person, DC=example, DC=com");
        SaslServer saslServer = _manager.createSaslServer("EXTERNAL", "example.example.com", principal);

        AuthenticationResult result = _manager.authenticate(saslServer, new byte[0]);
        assertNotNull(result);
        assertEquals("Expected authentication to be successful",
                     AuthenticationResult.AuthenticationStatus.SUCCESS,
                     result.getStatus());

        assertOnlyContainsWrapped(principal, result.getPrincipals());

        saslServer = _manager.createSaslServer("EXTERNAL", "example.example.com", null);
        result = _manager.authenticate(saslServer, new byte[0]);

        assertNotNull(result);
                assertEquals("Expected authentication to be unsuccessful",
                             AuthenticationResult.AuthenticationStatus.ERROR,
                             result.getStatus());

    }


}
