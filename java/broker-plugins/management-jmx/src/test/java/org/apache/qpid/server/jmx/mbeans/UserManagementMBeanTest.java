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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import javax.security.auth.login.AccountNotFoundException;

import junit.framework.TestCase;

import org.apache.qpid.management.common.mbeans.UserManagement;
import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.model.PasswordCredentialManagingAuthenticationProvider;

public class UserManagementMBeanTest extends TestCase
{
    private UserManagementMBean _userManagement;
    private ManagedObjectRegistry _mockRegistry;
    private PasswordCredentialManagingAuthenticationProvider _mockProvider;

    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "password";

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        _mockProvider = mock(PasswordCredentialManagingAuthenticationProvider.class);
        _mockRegistry = mock(ManagedObjectRegistry.class);
        _userManagement = new UserManagementMBean(_mockProvider, _mockRegistry);
    }

    public void testMBeanRegistersItself() throws Exception
    {
        UserManagementMBean userManagementMBean = new UserManagementMBean(_mockProvider, _mockRegistry);
        verify(_mockRegistry).registerObject(userManagementMBean);
    }

    public void testDeleteUser() throws Exception
    {
        boolean deleteSuccess = _userManagement.deleteUser(TEST_USERNAME);
        assertTrue("Expected successful delete", deleteSuccess);

        verify(_mockProvider).deleteUser(TEST_USERNAME);
    }

    public void testDeleteUserWhereUserDoesNotExist() throws Exception
    {
        doThrow(AccountNotFoundException.class).when(_mockProvider).deleteUser(TEST_USERNAME);

        boolean deleteSuccess = _userManagement.deleteUser(TEST_USERNAME);
        assertFalse("Expected unsuccessful delete", deleteSuccess);
    }
    
    public void testCreateUser() throws Exception
    {
        when(_mockProvider.createUser(TEST_USERNAME, TEST_PASSWORD, null)).thenReturn(true);

        boolean createSuccess = _userManagement.createUser(TEST_USERNAME, TEST_PASSWORD);
        assertTrue(createSuccess);
    }

    public void testCreateUserWhereUserAlreadyExists()
    {
        when(_mockProvider.createUser(TEST_USERNAME, TEST_PASSWORD, null)).thenReturn(false);

        boolean createSuccess = _userManagement.createUser(TEST_USERNAME, TEST_PASSWORD);
        assertFalse(createSuccess);
    }

    public void testSetPassword() throws Exception
    {
        boolean setPasswordSuccess = _userManagement.setPassword(TEST_USERNAME, TEST_PASSWORD);
        assertTrue(setPasswordSuccess);

        assertTrue("Set password should return true to flag successful change", setPasswordSuccess);

        verify(_mockProvider).setPassword(TEST_USERNAME, TEST_PASSWORD);
    }

    public void testSetPasswordWhereUserDoesNotExist() throws Exception
    {
        doThrow(AccountNotFoundException.class).when(_mockProvider).setPassword(TEST_USERNAME, TEST_PASSWORD);

        boolean setPasswordSuccess = _userManagement.setPassword(TEST_USERNAME, TEST_PASSWORD);

        assertFalse("Set password should return false to flag unsuccessful change", setPasswordSuccess);
    }

    public void testReload() throws Exception
    {
        boolean reloadSuccess = _userManagement.reloadData();

        assertTrue("Reload should return true to flag succesful update", reloadSuccess);

        verify(_mockProvider).reload();
    }

    public void testReloadFails() throws Exception
    {
        doThrow(IOException.class).when(_mockProvider).reload();

        boolean reloadSuccess = _userManagement.reloadData();

        assertFalse("Expected reload to fail", reloadSuccess);
    }

    public void testViewUsers() throws Exception
    {
        Map<String,String> args = Collections.emptyMap();
        when(_mockProvider.getUsers()).thenReturn(Collections.singletonMap(TEST_USERNAME, args));

        TabularData userList = _userManagement.viewUsers();

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
}
