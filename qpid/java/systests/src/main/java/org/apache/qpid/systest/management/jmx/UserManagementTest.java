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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.JMSException;

import org.apache.qpid.management.common.mbeans.UserManagement;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.security.auth.manager.AbstractPrincipalDatabaseAuthManagerFactory;
import org.apache.qpid.server.security.auth.manager.PlainPasswordFileAuthenticationManagerFactory;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.tools.security.Passwd;

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
    private Passwd _passwd;

    public void setUp() throws Exception
    {
        _passwd = createPasswordEncodingUtility();
        _passwordFile = createTemporaryPasswordFileWithJmxAdminUser();

        Map<String, Object> newAttributes = new HashMap<String, Object>();
        newAttributes.put(AuthenticationManagerFactory.ATTRIBUTE_TYPE, getAuthenticationManagerType());
        newAttributes.put(AbstractPrincipalDatabaseAuthManagerFactory.ATTRIBUTE_PATH, _passwordFile.getAbsolutePath());
        getBrokerConfiguration().setObjectAttributes(TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER, newAttributes);

        _jmxUtils = new JMXTestUtils(this);
        _jmxUtils.setUp();

        super.setUp();
        _jmxUtils.open();

        _testUserName = getTestName() + System.currentTimeMillis();

        _userManagement = _jmxUtils.getUserManagement();
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

    protected Passwd createPasswordEncodingUtility()
    {
        return new Passwd()
        {
            @Override
            public String getOutput(String username, String password)
            {
                return username + ":" + password;
            }
        };
    }

    protected String getAuthenticationManagerType()
    {
        return PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE;
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
        FileWriter writer = null;
        try
        {
            writer = new FileWriter(passwordFile);
            for (int i = 0; i < userNamePasswordPairs.length; i=i+2)
            {
                String username = userNamePasswordPairs[i];
                String password = userNamePasswordPairs[i+1];
                writer.append(_passwd.getOutput(username, password) + "\n");
            }
        }
        finally
        {
            writer.close();
        }
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
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(_passwordFile));
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
        finally
        {
            reader.close();
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
