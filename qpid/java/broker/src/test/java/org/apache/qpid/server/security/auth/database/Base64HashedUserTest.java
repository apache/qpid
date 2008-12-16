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
package org.apache.qpid.server.security.auth.database;

import junit.framework.TestCase;

import java.io.UnsupportedEncodingException;

/*
    Note User is mainly tested by Base64MD5PFPDTest this is just to catch the extra methods
 */
public class Base64HashedUserTest extends TestCase
{

    String USERNAME = "username";
    String PASSWORD = "password";
    String HASHED_PASSWORD = "cGFzc3dvcmQ=";

    public void testToLongArrayConstructor()
    {
        try
        {
            Base64HashedUser user = new Base64HashedUser(new String[]{USERNAME, PASSWORD, USERNAME});
            fail("Error expected");
        }
        catch (IllegalArgumentException e)
        {
            assertEquals("User Data should be length 2, username, password", e.getMessage());
        }
        catch (UnsupportedEncodingException e)
        {
            fail(e.getMessage());
        }
    }

    public void testArrayConstructor()
    {
        try
        {
            Base64HashedUser user = new Base64HashedUser(new String[]{USERNAME, HASHED_PASSWORD});
            assertEquals("Username incorrect", USERNAME, user.getName());
            int index = 0;

            char[] hash = HASHED_PASSWORD.toCharArray();

            try
            {
                for (byte c : user.getEncodedPassword())
                {
                    assertEquals("Password incorrect", hash[index], (char) c);
                    index++;
                }
            }
            catch (Exception e)
            {
                fail(e.getMessage());
            }

            hash = PASSWORD.toCharArray();

            index=0;
            for (char c : user.getPassword())
            {
                assertEquals("Password incorrect", hash[index], c);
                index++;
            }

        }
        catch (UnsupportedEncodingException e)
        {
            fail(e.getMessage());
        }
    }
}

