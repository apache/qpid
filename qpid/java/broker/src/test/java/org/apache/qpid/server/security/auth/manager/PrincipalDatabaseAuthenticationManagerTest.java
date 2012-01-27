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

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;

import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.database.PlainPasswordFilePrincipalDatabase;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;
import org.apache.qpid.server.util.InternalBrokerBaseCase;

import javax.security.auth.Subject;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.security.Provider;
import java.security.Security;

/**
 *
 * Tests the public methods of PrincipalDatabaseAuthenticationManager.
 *
 */
public class PrincipalDatabaseAuthenticationManagerTest extends InternalBrokerBaseCase
{
    private AuthenticationManager _manager = null; // Class under test
    private String TEST_USERNAME = "guest";
    private String TEST_PASSWORD = "guest";

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
        
        final String passwdFilename = createPasswordFile().getCanonicalPath();
        final ConfigurationPlugin config = getConfig(PlainPasswordFilePrincipalDatabase.class.getName(),
                "passwordFile", passwdFilename);

        _manager = PrincipalDatabaseAuthenticationManager.FACTORY.newInstance(config);
    }

    /**
     * Tests where the case where the config specifies a PD implementation
     * that is not found.
     */
    public void testPrincipalDatabaseImplementationNotFound() throws Exception
    {
        try
        {
            _manager = PrincipalDatabaseAuthenticationManager.FACTORY.newInstance(getConfig("not.Found", null, null));
            fail("Exception not thrown");
        }
        catch (ConfigurationException ce)
        {
            // PASS
        }
    }

    /**
     * Tests where the case where the config specifies a PD implementation
     * of the wrong type.
     */
    public void testPrincipalDatabaseImplementationWrongType() throws Exception
    {
        try
        {
            _manager = PrincipalDatabaseAuthenticationManager.FACTORY.newInstance(getConfig(String.class.getName(), null, null)); // Not a PrincipalDatabase implementation
            fail("Exception not thrown");
        }
        catch (ConfigurationException ce)
        {
            // PASS
        }
    }

    /**
     * Tests the case where a setter with the desired name cannot be found.
     */
    public void testPrincipalDatabaseSetterNotFound() throws Exception
    {
        try
        {
            _manager = PrincipalDatabaseAuthenticationManager.FACTORY.newInstance(getConfig(PlainPasswordFilePrincipalDatabase.class.getName(), "noMethod", "test")); 
            fail("Exception not thrown");
        }
        catch (ConfigurationException ce)
        {
            // PASS
        }
    }

    /**
     * QPID-1347. Make sure the exception message and stack trace is reasonable for an absent password file.
     */
    public void testPrincipalDatabaseThrowsSetterFileNotFound() throws Exception
    {
        try
        {
            _manager = PrincipalDatabaseAuthenticationManager.FACTORY.newInstance(getConfig(PlainPasswordFilePrincipalDatabase.class.getName(), "passwordFile", "/not/found")); 
            fail("Exception not thrown");
        }
        catch (ConfigurationException ce)
        {
            // PASS
            assertNotNull("Expected an underlying cause", ce.getCause());
            assertEquals(FileNotFoundException.class, ce.getCause().getClass());
        }
    }

    /**
     * Tests that the PDAM registers SASL mechanisms correctly with the runtime.
     */
    public void testRegisteredMechanisms() throws Exception
    {
        assertNotNull(_manager.getMechanisms());
        // relies on those mechanisms attached to PropertiesPrincipalDatabaseManager
        assertEquals("AMQPLAIN PLAIN CRAM-MD5", _manager.getMechanisms());

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
        assertEquals("AMQPLAIN PLAIN CRAM-MD5", _manager.getMechanisms());
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
            public String getMechanismName()
            {
                return null;
            }

            public byte[] evaluateResponse(byte[] response) throws SaslException
            {
                if (throwSaslException)
                {
                    throw new SaslException("Mocked exception");
                }
                return null;
            }

            public boolean isComplete()
            {
                return complete;
            }

            public String getAuthorizationID()
            {
                return complete ? "guest" : null;
            }

            public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException
            {
                return null;
            }

            public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException
            {
                return null;
            }

            public Object getNegotiatedProperty(String propName)
            {
                return null;
            }

            public void dispose() throws SaslException
            {
            }
        };
    }

    private ConfigurationPlugin getConfig(final String clazz, final String argName, final String argValue) throws Exception
    {
        final ConfigurationPlugin config = new PrincipalDatabaseAuthenticationManager.PrincipalDatabaseAuthenticationManagerConfiguration();

        XMLConfiguration xmlconfig = new XMLConfiguration();
        xmlconfig.addProperty("pd-auth-manager.principal-database.class", clazz);

        if (argName != null)
        {
            xmlconfig.addProperty("pd-auth-manager.principal-database.attributes.attribute.name", argName);
            xmlconfig.addProperty("pd-auth-manager.principal-database.attributes.attribute.value", argValue);
        }

        // Create a CompositeConfiguration as this is what the broker uses
        CompositeConfiguration composite = new CompositeConfiguration();
        composite.addConfiguration(xmlconfig);
        config.setConfiguration("security", xmlconfig);
        return config;
    }

    private File createPasswordFile() throws Exception
    {
        BufferedWriter writer = null;
        try
        {
            File testFile = File.createTempFile(this.getClass().getName(),"tmp");
            testFile.deleteOnExit();

            writer = new BufferedWriter(new FileWriter(testFile));
            writer.write(TEST_USERNAME + ":" + TEST_PASSWORD);
            writer.newLine();
 
            return testFile;

        }
        finally
        {
            if (writer != null)
            {
                writer.close();
            }
        }
    }
}
