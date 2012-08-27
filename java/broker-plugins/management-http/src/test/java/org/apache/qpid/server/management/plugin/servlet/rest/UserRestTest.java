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
package org.apache.qpid.server.management.plugin.servlet.rest;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.model.User;

public class UserRestTest extends QpidRestTestCase
{
    public void testGet() throws Exception
    {
        List<Map<String, Object>> users = getJsonAsList("/rest/user");
        assertNotNull("Users cannot be null", users);
        assertTrue("Unexpected number of users", users.size() > 1);
        for (Map<String, Object> user : users)
        {
            assertUser(user);
        }
    }

    public void testGetUserByName() throws Exception
    {
        List<Map<String, Object>> users = getJsonAsList("/rest/user");
        assertNotNull("Users cannot be null", users);
        assertTrue("Unexpected number of users", users.size() > 1);
        for (Map<String, Object> user : users)
        {
            assertNotNull("Attribute " + User.ID, user.get(User.ID));
            String userName = (String) user.get(User.NAME);
            assertNotNull("Attribute " + User.NAME, userName);
            Map<String, Object> userDetails = getJsonAsSingletonList("/rest/user/PrincipalDatabaseAuthenticationManager/"
                    + userName);
            assertUser(userDetails);
            assertEquals("Unexpected user name", userName, userDetails.get(User.NAME));
        }
    }

    public void testPut() throws Exception
    {
        String userName = getTestName();
        HttpURLConnection connection = openManagementConection("/rest/user/PrincipalDatabaseAuthenticationManager/"
                + userName, "PUT");

        Map<String, Object> userData = new HashMap<String, Object>();
        userData.put(User.NAME, userName);
        userData.put(User.PASSWORD, userName);

        writeJsonRequest(connection, userData);
        assertEquals("Unexpected response code", 201, connection.getResponseCode());

        connection.disconnect();

        Map<String, Object> userDetails = getJsonAsSingletonList("/rest/user/PrincipalDatabaseAuthenticationManager/"
                + userName);
        assertUser(userDetails);
        assertEquals("Unexpected user name", userName, userDetails.get(User.NAME));
    }

    public void testDelete() throws Exception
    {
        // add user
        String userName = getTestName();
        HttpURLConnection connection = openManagementConection("/rest/user/PrincipalDatabaseAuthenticationManager/"
                + userName, "PUT");

        Map<String, Object> userData = new HashMap<String, Object>();
        userData.put(User.NAME, userName);
        userData.put(User.PASSWORD, userName);

        writeJsonRequest(connection, userData);
        assertEquals("Unexpected response code", 201, connection.getResponseCode());
        connection.disconnect();

        Map<String, Object> userDetails = getJsonAsSingletonList("/rest/user/PrincipalDatabaseAuthenticationManager/"
                + userName);
        String id = (String) userDetails.get(User.ID);

        connection = openManagementConection("/rest/user/PrincipalDatabaseAuthenticationManager?id=" + id, "DELETE");
        connection.connect();
        assertEquals("Unexpected response code", 200, connection.getResponseCode());
        List<Map<String, Object>> users = getJsonAsList("/rest/user/PrincipalDatabaseAuthenticationManager/" + userName);
        assertEquals("User should be deleted", 0, users.size());
    }

    private void assertUser(Map<String, Object> user)
    {
        assertNotNull("Attribute " + User.ID, user.get(User.ID));
        assertNotNull("Attribute " + User.NAME, user.get(User.NAME));
    }
}
