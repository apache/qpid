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

import static org.apache.qpid.server.security.auth.AuthenticatedPrincipalTestHelper.assertOnlyContainsWrapped;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.auth.database.PlainPasswordFilePrincipalDatabase;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;

/**
 * Tests the public methods of PrincipalDatabaseAuthenticationManager.
 *
 */
public class PrincipalDatabaseAuthenticationManagerTest extends QpidTestCase
{
    private static final String LOCALHOST = "localhost";
    private static final String MOCK_MECH_NAME = "MOCK-MECH-NAME";
    private static final UsernamePrincipal PRINCIPAL = new UsernamePrincipal("guest");

    private PrincipalDatabaseAuthenticationManager _manager = null; // Class under test
    private PrincipalDatabase _principalDatabase;
    private String _passwordFileLocation;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _passwordFileLocation = TMP_FOLDER + File.separator + PrincipalDatabaseAuthenticationManagerTest.class.getSimpleName() + "-" + getName();
        deletePasswordFileIfExists();
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            if (_manager != null)
            {
                _manager.close();
            }
        }
        finally
        {
            deletePasswordFileIfExists();
        }
        super.tearDown();
    }

    private void setupMocks() throws Exception
    {
        setUpPrincipalDatabase();

        setupManager(false);

        _manager.initialise();
    }

    private void setUpPrincipalDatabase() throws SaslException
    {
        _principalDatabase = mock(PrincipalDatabase.class);

        when(_principalDatabase.getMechanisms()).thenReturn(Collections.singletonList(MOCK_MECH_NAME));
        when(_principalDatabase.createSaslServer(MOCK_MECH_NAME, LOCALHOST, null)).thenReturn(new MySaslServer(false, true));
    }

    private void setupManager(final boolean recovering)
    {
        Map<String,Object> attrs = new HashMap<String, Object>();
        attrs.put(ConfiguredObject.ID, UUID.randomUUID());
        attrs.put(ConfiguredObject.NAME, getTestName());
        attrs.put("path", _passwordFileLocation);
        _manager = getPrincipalDatabaseAuthenticationManager(attrs);
        if(recovering)
        {
            _manager.open();
        }
        else
        {
            _manager.create();
        }
    }

    public void testInitialiseWhenPasswordFileNotFound() throws Exception
    {
        _principalDatabase = new PlainPasswordFilePrincipalDatabase();
        setupManager(true);
        try
        {

            _manager.initialise();
            fail("Initialisiation should fail when users file does not exist");
        }
        catch (IllegalConfigurationException e)
        {
            assertTrue(e.getCause() instanceof FileNotFoundException);
        }
    }

    public void testInitialiseWhenPasswordFileExists() throws Exception
    {
        _principalDatabase = new PlainPasswordFilePrincipalDatabase();
        setupManager(true);

        File f = new File(_passwordFileLocation);
        f.createNewFile();
        FileOutputStream fos = null;
        try
        {
            fos = new FileOutputStream(f);
            fos.write("admin:admin".getBytes());
        }
        finally
        {
            if (fos != null)
            {
                fos.close();
            }
        }
        _manager.initialise();
        List<Principal> users = _principalDatabase.getUsers();
        assertEquals("Unexpected uses size", 1, users.size());
        Principal p = _principalDatabase.getUser("admin");
        assertEquals("Unexpected principal name", "admin", p.getName());
    }

    /**
     * Tests that the SASL factory method createSaslServer correctly
     * returns a non-null implementation.
     */
    public void testSaslMechanismCreation() throws Exception
    {
        setupMocks();

        SaslServer server = _manager.createSaslServer(MOCK_MECH_NAME, LOCALHOST, null);
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
        setupMocks();

        SaslServer testServer = createTestSaslServer(true, false);

        AuthenticationResult result = _manager.authenticate(testServer, "12345".getBytes());

        assertOnlyContainsWrapped(PRINCIPAL, result.getPrincipals());
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
        setupMocks();

        SaslServer testServer = createTestSaslServer(false, false);

        AuthenticationResult result = _manager.authenticate(testServer, "12345".getBytes());
        assertEquals("Principals was not expected size", 0, result.getPrincipals().size());

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
        setupMocks();

        SaslServer testServer = createTestSaslServer(false, true);

        AuthenticationResult result = _manager.authenticate(testServer, "12345".getBytes());
        assertEquals("Principals was not expected size", 0, result.getPrincipals().size());
        assertEquals(AuthenticationStatus.ERROR, result.getStatus());
    }

    public void testNonSaslAuthenticationSuccess() throws Exception
    {
        setupMocks();

        when(_principalDatabase.verifyPassword("guest", "guest".toCharArray())).thenReturn(true);

        AuthenticationResult result = _manager.authenticate("guest", "guest");
        assertOnlyContainsWrapped(PRINCIPAL, result.getPrincipals());
        assertEquals(AuthenticationStatus.SUCCESS, result.getStatus());
    }

    public void testNonSaslAuthenticationNotCompleted() throws Exception
    {
        setupMocks();

        when(_principalDatabase.verifyPassword("guest", "wrongpassword".toCharArray())).thenReturn(false);

        AuthenticationResult result = _manager.authenticate("guest", "wrongpassword");
        assertEquals("Principals was not expected size", 0, result.getPrincipals().size());
        assertEquals(AuthenticationStatus.CONTINUE, result.getStatus());
    }

    public void testOnCreate() throws Exception
    {
        setupMocks();

        assertTrue("Password file was not created", new File(_passwordFileLocation).exists());
    }

    public void testOnDelete() throws Exception
    {
        setupMocks();

        assertTrue("Password file was not created", new File(_passwordFileLocation).exists());

        _manager.delete();
        assertFalse("Password file was not deleted", new File(_passwordFileLocation).exists());
    }

    public void testCreateForInvalidPath() throws Exception
    {
        setUpPrincipalDatabase();

        Map<String,Object> attrs = new HashMap<>();
        attrs.put(ConfiguredObject.ID, UUID.randomUUID());
        attrs.put(ConfiguredObject.NAME, getTestName());
        String path = TMP_FOLDER + File.separator + getTestName() + System.nanoTime() + File.separator + "users";
        attrs.put("path", path);

        _manager = getPrincipalDatabaseAuthenticationManager(attrs);
        try
        {
            _manager.create();
            fail("Creation with invalid path should have failed");
        }
        catch(IllegalConfigurationException e)
        {
            assertEquals("Unexpected exception message:" + e.getMessage(), String.format("Cannot create password file at '%s'", path), e.getMessage());
        }
    }

    PrincipalDatabaseAuthenticationManager getPrincipalDatabaseAuthenticationManager(final Map<String, Object> attrs)
    {
        return new PrincipalDatabaseAuthenticationManager(attrs, BrokerTestHelper.createBrokerMock())
        {
            @Override
            protected PrincipalDatabase createDatabase()
            {
                return _principalDatabase;
            }

        };
    }

    private void deletePasswordFileIfExists()
    {
        File passwordFile = new File(_passwordFileLocation);
        if (passwordFile.exists())
        {
            passwordFile.delete();
        }
    }

    /**
     * Test SASL implementation used to test the authenticate() method.
     */
    private SaslServer createTestSaslServer(final boolean complete, final boolean throwSaslException)
    {
        return new MySaslServer(throwSaslException, complete);
    }

    public static final class MySaslServer implements SaslServer
    {
        private final boolean _throwSaslException;
        private final boolean _complete;

        public MySaslServer()
        {
            this(false, true);
        }

        private MySaslServer(boolean throwSaslException, boolean complete)
        {
            _throwSaslException = throwSaslException;
            _complete = complete;
        }

        public String getMechanismName()
        {
            return null;
        }

        public byte[] evaluateResponse(byte[] response) throws SaslException
        {
            if (_throwSaslException)
            {
                throw new SaslException("Mocked exception");
            }
            return null;
        }

        public boolean isComplete()
        {
            return _complete;
        }

        public String getAuthorizationID()
        {
            return _complete ? "guest" : null;
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
    }

    public static class MySaslServerFactory implements SaslServerFactory
    {
        @Override
        public SaslServer createSaslServer(String mechanism, String protocol,
                String serverName, Map<String, ?> props, CallbackHandler cbh)
                throws SaslException
        {
            if (MOCK_MECH_NAME.equals(mechanism))
            {
                return new MySaslServer();
            }
            else
            {
                return null;
            }
        }

        @Override
        public String[] getMechanismNames(Map<String, ?> props)
        {
            return new String[]{MOCK_MECH_NAME};
        }
    }
}
