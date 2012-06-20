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

package org.apache.qpid.server.jmx.mbeans;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.management.common.mbeans.UserManagement;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.jmx.NoopManagedObjectRegistry;
import org.apache.qpid.server.model.PasswordCredentialManagingAuthenticationProvider;
import org.apache.qpid.server.model.adapter.AuthenticationProviderAdapter;
import org.apache.qpid.server.security.auth.database.PlainPasswordFilePrincipalDatabase;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.util.InternalBrokerBaseCase;

/** 
 * Tests the UserManagementMBean and its interaction with the PasswordCredentialManagingAuthenticationProvider.
 */
public class UserManagementMBeanTest extends InternalBrokerBaseCase
{
    private UserManagementMBean _umMBean;
    
    private File _passwordFile;
    private PrincipalDatabaseAuthenticationManager _authManager;
    private AuthenticationProviderAdapter<?> _authProvider;

    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "password";

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        _passwordFile = File.createTempFile(this.getClass().getName(),".password");
        
        createFreshTestPasswordFile();

        ConfigurationPlugin config = getConfig(PlainPasswordFilePrincipalDatabase.class.getName(), "passwordFile", _passwordFile.getAbsolutePath());
        _authManager = PrincipalDatabaseAuthenticationManager.FACTORY.newInstance(config);
        _authProvider = AuthenticationProviderAdapter.createAuthenticationProviderAdapter(null, _authManager);
        _umMBean = new UserManagementMBean((PasswordCredentialManagingAuthenticationProvider) _authProvider, new NoopManagedObjectRegistry());
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            //clean up test password files
            File _oldPasswordFile = new File(_passwordFile.getAbsolutePath() + ".old");
            _oldPasswordFile.delete();
            _passwordFile.delete();
        }
    }

    public void testDeleteUser()
    {
        assertEquals("Unexpected number of users before test", 1,_umMBean.viewUsers().size());
        assertTrue("Delete should return true to flag successful delete", _umMBean.deleteUser(TEST_USERNAME));
        assertEquals("Unexpected number of users after test", 0,_umMBean.viewUsers().size());
    }

    public void testDeleteUserWhereUserDoesNotExist()
    {
        assertEquals("Unexpected number of users before test", 1,_umMBean.viewUsers().size());
        assertFalse("Delete should return false to flag unsuccessful delete", _umMBean.deleteUser("made.up.username"));
        assertEquals("Unexpected number of users after test", 1,_umMBean.viewUsers().size());

    }
    
    public void testCreateUser()
    {
        assertEquals("Unexpected number of users before test", 1,_umMBean.viewUsers().size());
        assertTrue("Create should return true to flag successful create", _umMBean.createUser("newuser", "mypass"));
        assertEquals("Unexpected number of users before test", 2,_umMBean.viewUsers().size());
    }

    public void testCreateUserWhereUserAlreadyExists()
    {
        assertEquals("Unexpected number of users before test", 1,_umMBean.viewUsers().size());
        assertFalse("Create should return false to flag unsuccessful create", _umMBean.createUser(TEST_USERNAME, "mypass"));
        assertEquals("Unexpected number of users before test", 1,_umMBean.viewUsers().size());
    }

    public void testSetPassword()
    {
        assertTrue("Set password should return true to flag successful change", _umMBean.setPassword(TEST_USERNAME, "newpassword"));
    }
    
    public void testSetPasswordWhereUserDoesNotExist()
    {
        assertFalse("Set password should return false to flag successful change", _umMBean.setPassword("made.up.username", "newpassword"));
    }

    public void testViewUsers()
    {
        TabularData userList = _umMBean.viewUsers();

        assertNotNull(userList);
        assertEquals("Unexpected number of users in user list", 1, userList.size());
        assertTrue(userList.containsKey(new Object[]{TEST_USERNAME}));
        
        // Check the deprecated read, write and admin items continue to exist but return false.
        CompositeData userRec = userList.get(new Object[]{TEST_USERNAME});
        assertTrue(userRec.containsKey(UserManagement.RIGHTS_READ_ONLY));
        assertEquals(false, userRec.get(UserManagement.RIGHTS_READ_ONLY));
        assertEquals(false, userRec.get(UserManagement.RIGHTS_READ_WRITE));
        assertTrue(userRec.containsKey(UserManagement.RIGHTS_READ_WRITE));
        assertTrue(userRec.containsKey(UserManagement.RIGHTS_ADMIN));
        assertEquals(false, userRec.get(UserManagement.RIGHTS_ADMIN));
    }

    // ============================ Utility methods =========================

    private void createFreshTestPasswordFile()
    {
        try
        {
            BufferedWriter passwordWriter = new BufferedWriter(new FileWriter(_passwordFile, false));
            passwordWriter.write(TEST_USERNAME + ":" + TEST_PASSWORD);
            passwordWriter.newLine();
            passwordWriter.flush();
            passwordWriter.close();
        }
        catch (IOException e)
        {
            fail("Unable to create test password file: " + e.getMessage());
        }
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
}
