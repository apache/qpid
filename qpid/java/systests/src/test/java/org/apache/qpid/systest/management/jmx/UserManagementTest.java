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
package org.apache.qpid.systest.management.jmx;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.JMSException;

import org.apache.qpid.management.common.mbeans.UserManagement;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.security.auth.manager.PlainPasswordDatabaseAuthenticationManager;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

/**
 * System test for User Management.
 *
 */
public class UserManagementTest extends QpidBrokerTestCase
{
    private static final String TEST_NEWPASSWORD = "newpassword";
    private static final String TEST_PASSWORD = "password";
    private JMXTestUtils _jmxUtils;
    private String _testUserName;
    private File _passwordFile;
    private UserManagement _userManagement;

    public void setUp() throws Exception
    {
        _passwordFile = createTemporaryPasswordFileWithJmxAdminUser();

        Map<String, Object> newAttributes = new HashMap<String, Object>();
        newAttributes.put(AuthenticationProvider.TYPE, getAuthenticationManagerType());
        newAttributes.put("path", _passwordFile.getAbsolutePath());
        getBrokerConfiguration().setObjectAttributes(AuthenticationProvider.class,TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER, newAttributes);
        getBrokerConfiguration().addJmxManagementConfiguration();

        _jmxUtils = new JMXTestUtils(this);

        super.setUp();
        _jmxUtils.open();

        _testUserName = getTestName() + System.currentTimeMillis();

        _userManagement = _jmxUtils.getUserManagement(TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER);
    }


    public void tearDown() throws Exception
    {
        try
        {
            if (_jmxUtils != null)
            {
                _jmxUtils.close();
            }
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testCreateUser() throws Exception
    {
        final int initialNumberOfUsers = _userManagement.viewUsers().size();
        assertFileDoesNotContainsPasswordForUser(_testUserName);

        boolean success = _userManagement.createUser(_testUserName, TEST_PASSWORD);
        assertTrue("Should have been able to create new user " + _testUserName, success);
        assertEquals("Unexpected number of users after add", initialNumberOfUsers + 1, _userManagement.viewUsers().size());

        assertFileContainsPasswordForUser(_testUserName);
    }

    public void testJmsLoginForNewUser() throws Exception
    {
        assertJmsConnectionFails(_testUserName, TEST_PASSWORD);
        testCreateUser();

        assertJmsConnectionSucceeds(_testUserName, TEST_PASSWORD);
    }

    public void testDeleteUser() throws Exception
    {
        final int initialNumberOfUsers = _userManagement.viewUsers().size();

        testCreateUser();

        boolean success = _userManagement.deleteUser(_testUserName);
        assertTrue("Should have been able to delete new user " + _testUserName, success);
        assertEquals("Unexpected number of users after delete", initialNumberOfUsers, _userManagement.viewUsers().size());
        assertFileDoesNotContainsPasswordForUser(_testUserName);
    }

    public void testJmsLoginNotPossibleForDeletedUser() throws Exception
    {
        testDeleteUser();

        assertJmsConnectionFails(_testUserName, TEST_PASSWORD);
    }

    public void testSetPassword() throws Exception
    {
        testCreateUser();

        _userManagement.setPassword(_testUserName, TEST_NEWPASSWORD);

        assertFileContainsPasswordForUser(_testUserName);
    }

    public void testJmsLoginForPasswordChangedUser() throws Exception
    {
        testSetPassword();

        assertJmsConnectionSucceeds(_testUserName, TEST_NEWPASSWORD);
        assertJmsConnectionFails(_testUserName, TEST_PASSWORD);
    }

    public void testReload() throws Exception
    {
        writePasswordFile(_passwordFile, JMXTestUtils.DEFAULT_USERID, JMXTestUtils.DEFAULT_PASSWORD, _testUserName, TEST_PASSWORD);

        assertJmsConnectionFails(_testUserName, TEST_PASSWORD);

        _userManagement.reloadData();

        assertJmsConnectionSucceeds(_testUserName, TEST_PASSWORD);
    }

    public void testGetAuthenticationProviderType() throws Exception
    {
        String actualType = _userManagement.getAuthenticationProviderType();
        assertEquals("unexpected authentication provider type", getAuthenticationManagerType(), actualType);
    }


    protected String getAuthenticationManagerType()
    {
        return PlainPasswordDatabaseAuthenticationManager.PROVIDER_TYPE;
    }

    private File createTemporaryPasswordFileWithJmxAdminUser() throws Exception
    {
        File passwordFile = File.createTempFile("passwd", "pwd");
        passwordFile.deleteOnExit();
        writePasswordFile(passwordFile, JMXTestUtils.DEFAULT_USERID, JMXTestUtils.DEFAULT_PASSWORD);
        return passwordFile;
    }

    private void writePasswordFile(File passwordFile, String... userNamePasswordPairs) throws Exception
    {
        try(FileWriter writer = new FileWriter(passwordFile))
        {
            for (int i = 0; i < userNamePasswordPairs.length; i=i+2)
            {
                String username = userNamePasswordPairs[i];
                String password = userNamePasswordPairs[i+1];
                writeUsernamePassword(writer, username, password);
            }
        }

    }

    protected void writeUsernamePassword(final FileWriter writer, final String username, final String password)
            throws IOException
    {
        writer.append(username);
        writer.append(':');
        writer.append(password);
        writer.append('\n');
    }


    private void assertFileContainsPasswordForUser(String username) throws IOException
    {
        assertTrue("Could not find password for user " + username + " within " + _passwordFile, passwordFileContainsUser(username));
    }

    private void assertFileDoesNotContainsPasswordForUser(String username) throws IOException
    {
        assertFalse("Could not find password for user " + username + " within " + _passwordFile, passwordFileContainsUser(username));
    }

    private boolean passwordFileContainsUser(String username) throws IOException
    {
        try(BufferedReader reader = new BufferedReader(new FileReader(_passwordFile)))
        {
            String line = reader.readLine();
            while(line != null)
            {
                if (line.startsWith(username))
                {
                    return true;
                }
                line = reader.readLine();
            }

            return false;
        }
    }

    private void assertJmsConnectionSucceeds(String username, String password) throws Exception
    {
        Connection connection = getConnection(username, password);
        assertNotNull(connection);
    }

    private void assertJmsConnectionFails(String username, String password) throws Exception
    {
        try
        {
            getConnection(username, password);
            fail("Exception not thrown");
        }
        catch (JMSException e)
        {
            // PASS
        }
    }
}
