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
package org.apache.qpid.systest.rest;

import java.util.List;
import java.util.Map;

import org.apache.qpid.server.model.User;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class UserRestTest extends QpidRestTestCase
{
    @Override
    public void setUp() throws Exception
    {
        getRestTestHelper().configureTemporaryPasswordFile(this, "user1", "user2");

        super.setUp(); // do this last because it starts the broker, using the modified config
    }

    public void testGet() throws Exception
    {
        List<Map<String, Object>> users = getRestTestHelper().getJsonAsList("/rest/user");
        assertNotNull("Users cannot be null", users);
        assertTrue("Unexpected number of users", users.size() > 1);
        for (Map<String, Object> user : users)
        {
            assertUser(user);
        }
    }

    public void testGetUserByName() throws Exception
    {
        List<Map<String, Object>> users = getRestTestHelper().getJsonAsList("/rest/user");
        assertNotNull("Users cannot be null", users);
        assertTrue("Unexpected number of users", users.size() > 1);
        for (Map<String, Object> user : users)
        {
            assertNotNull("Attribute " + User.ID, user.get(User.ID));
            String userName = (String) user.get(User.NAME);
            assertNotNull("Attribute " + User.NAME, userName);
            Map<String, Object> userDetails = getRestTestHelper().getJsonAsSingletonList("/rest/user/"
                    + TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/" + userName);
            assertUser(userDetails);
            assertEquals("Unexpected user name", userName, userDetails.get(User.NAME));
        }
    }

    public void testPut() throws Exception
    {
        String userName = getTestName();
        getRestTestHelper().createOrUpdateUser(userName, "newPassword");

        Map<String, Object> userDetails = getRestTestHelper().getJsonAsSingletonList("/rest/user/"
                + TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/" + userName);
        assertUser(userDetails);
        assertEquals("Unexpected user name", userName, userDetails.get(User.NAME));
    }

    public void testDelete() throws Exception
    {
        String userName = getTestName();
        getRestTestHelper().createOrUpdateUser(userName, "newPassword");

        Map<String, Object> userDetails = getRestTestHelper().getJsonAsSingletonList("/rest/user/"
                + TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/" + userName);
        String id = (String) userDetails.get(User.ID);

        getRestTestHelper().removeUserById(id);

        List<Map<String, Object>> users = getRestTestHelper().getJsonAsList("/rest/user/"
                + TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER + "/" + userName);
        assertEquals("User should be deleted", 0, users.size());
    }

    private void assertUser(Map<String, Object> user)
    {
        assertNotNull("Attribute " + User.ID, user.get(User.ID));
        assertNotNull("Attribute " + User.NAME, user.get(User.NAME));
    }
}
