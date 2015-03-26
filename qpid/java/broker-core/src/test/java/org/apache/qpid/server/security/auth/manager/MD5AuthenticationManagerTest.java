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

import javax.security.sasl.SaslServer;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.model.User;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.sasl.SaslUtil;

public class MD5AuthenticationManagerTest extends ManagedAuthenticationManagerTestBase
{

    public static final String USER_NAME = "test";
    public static final String USER_PASSWORD = "password";

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
    }

    @Override
    protected ConfigModelPasswordManagingAuthenticationProvider<?> createAuthManager(final Map<String, Object> attributesMap)
    {
        return new MD5AuthenticationProvider(attributesMap, getBroker());
    }

    @Override
    protected boolean isPlain()
    {
        return false;
    }

    @Override
    public void tearDown() throws Exception
    {
        super.tearDown();
    }

    public void testMD5HexAuthenticationWithValidCredentials() throws Exception
    {
        createUser(USER_NAME, USER_PASSWORD);
        AuthenticationResult result = authenticate("CRAM-MD5-HEX", USER_NAME, USER_PASSWORD);
        assertEquals("Unexpected authentication result", AuthenticationResult.AuthenticationStatus.SUCCESS, result.getStatus());
    }

    public void testMD5HexAuthenticationWithInvalidPassword() throws Exception
    {
        createUser(USER_NAME, USER_PASSWORD);
        AuthenticationResult result = authenticate("CRAM-MD5-HEX", USER_NAME, "invalid");
        assertEquals("Unexpected authentication result", AuthenticationResult.AuthenticationStatus.ERROR, result.getStatus());
    }

    public void testMD5HexAuthenticationWithInvalidUsername() throws Exception
    {
        createUser(USER_NAME, USER_PASSWORD);
        AuthenticationResult result = authenticate("CRAM-MD5-HEX", "invalid", USER_PASSWORD);
        assertEquals("Unexpected authentication result", AuthenticationResult.AuthenticationStatus.ERROR, result.getStatus());
    }

    private AuthenticationResult authenticate(String mechanism, String userName, String userPassword) throws Exception
    {
        SaslServer ss = getAuthManager().createSaslServer(mechanism, "test", null);
        byte[] challenge = ss.evaluateResponse(new byte[0]);

        byte[] response = SaslUtil.generateCramMD5HexClientResponse(userName, userPassword, challenge);;

        return  getAuthManager().authenticate(ss, response);
    }

    private User createUser(String userName, String userPassword)
    {
        final Map<String, Object> childAttrs = new HashMap<String, Object>();

        childAttrs.put(User.NAME, userName);
        childAttrs.put(User.PASSWORD, userPassword);
        User user = getAuthManager().addChild(User.class, childAttrs);
        assertNotNull("User should be created but addChild returned null", user);
        assertEquals(userName, user.getName());
        return user;
    }
}
