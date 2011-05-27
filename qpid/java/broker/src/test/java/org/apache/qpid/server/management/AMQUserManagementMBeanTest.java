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

package org.apache.qpid.server.management;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;


import org.apache.commons.lang.NotImplementedException;
import org.apache.qpid.management.common.mbeans.UserManagement;
import org.apache.qpid.server.security.auth.database.PlainPasswordFilePrincipalDatabase;
import org.apache.qpid.server.security.auth.management.AMQUserManagementMBean;

import org.apache.qpid.server.util.InternalBrokerBaseCase;

/** 
 * 
 * Tests the AMQUserManagementMBean and its interaction with the PrincipalDatabase.
 *
 */
public class AMQUserManagementMBeanTest extends InternalBrokerBaseCase
{
    private PlainPasswordFilePrincipalDatabase _database;
    private AMQUserManagementMBean _amqumMBean;
    
    private File _passwordFile;

    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "password";

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        _database = new PlainPasswordFilePrincipalDatabase();
        _amqumMBean = new AMQUserManagementMBean();
        loadFreshTestPasswordFile();
    }

    @Override
    public void tearDown() throws Exception
    {
        //clean up test password/access files
        File _oldPasswordFile = new File(_passwordFile.getAbsolutePath() + ".old");
        _oldPasswordFile.delete();
        _passwordFile.delete();

        super.tearDown();
    }

    public void testDeleteUser()
    {
        assertEquals("Unexpected number of users before test", 1,_amqumMBean.viewUsers().size());
        assertTrue("Delete should return true to flag successful delete", _amqumMBean.deleteUser(TEST_USERNAME));
        assertEquals("Unexpected number of users after test", 0,_amqumMBean.viewUsers().size());
    }

    public void testDeleteUserWhereUserDoesNotExist()
    {
        assertEquals("Unexpected number of users before test", 1,_amqumMBean.viewUsers().size());
        assertFalse("Delete should return false to flag unsuccessful delete", _amqumMBean.deleteUser("made.up.username"));
        assertEquals("Unexpected number of users after test", 1,_amqumMBean.viewUsers().size());

    }
    
    public void testCreateUser()
    {
        assertEquals("Unexpected number of users before test", 1,_amqumMBean.viewUsers().size());
        assertTrue("Create should return true to flag successful create", _amqumMBean.createUser("newuser", "mypass"));
        assertEquals("Unexpected number of users before test", 2,_amqumMBean.viewUsers().size());
    }

    public void testCreateUserWhereUserAlreadyExists()
    {
        assertEquals("Unexpected number of users before test", 1,_amqumMBean.viewUsers().size());
        assertFalse("Create should return false to flag unsuccessful create", _amqumMBean.createUser(TEST_USERNAME, "mypass"));
        assertEquals("Unexpected number of users before test", 1,_amqumMBean.viewUsers().size());
    }

    public void testFiveArgCreateUserWithNegativeRightsRemainsSupported()
    {
        assertEquals("Unexpected number of users before test", 1,_amqumMBean.viewUsers().size());
        assertTrue("Create should return true to flag successful create", _amqumMBean.createUser("newuser", "mypass".toCharArray(), false, false, false));
        assertEquals("Unexpected number of users before test", 2,_amqumMBean.viewUsers().size());
    }

    public void testSetPassword()
    {
        assertTrue("Set password should return true to flag successful change", _amqumMBean.setPassword(TEST_USERNAME, "newpassword"));
    }
    
    public void testSetPasswordWhereUserDoesNotExist()
    {
        assertFalse("Set password should return false to flag successful change", _amqumMBean.setPassword("made.up.username", "newpassword"));
    }

    public void testViewUsers()
    {
        TabularData userList = _amqumMBean.viewUsers();

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

    // TEST DEPRECATED METHODS
    public void testFiveArgCreateUserWithPositiveRightsThrowsUnsupportedOperation()
    {
        try 
        {
            _amqumMBean.createUser(TEST_USERNAME, "mypass", true, false, false);
            fail("Exception not thrown");
        }
        catch (UnsupportedOperationException uoe)
        {
            // PASS
        }
    }

    public void testSetRightsThrowsUnsupportedOperation()
    {
        try 
        {
            _amqumMBean.setRights("", false, false, false);
            fail("Exception not thrown");
        }
        catch(UnsupportedOperationException nie)
        {
            // PASS
        }
    }

    // ============================ Utility methods =========================

    private void loadFreshTestPasswordFile()
    {
        try
        {
            if(_passwordFile == null)
            {
                _passwordFile = File.createTempFile(this.getClass().getName(),".password");
            }

            BufferedWriter passwordWriter = new BufferedWriter(new FileWriter(_passwordFile, false));
            passwordWriter.write(TEST_USERNAME + ":" + TEST_PASSWORD);
            passwordWriter.newLine();
            passwordWriter.flush();
            passwordWriter.close();
            _database.setPasswordFile(_passwordFile.toString());
            _amqumMBean.setPrincipalDatabase(_database);
        }
        catch (IOException e)
        {
            fail("Unable to create test password file: " + e.getMessage());
        }
    }
}
