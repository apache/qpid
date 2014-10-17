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

import java.util.Collections;
import java.util.Map;

public class ScramSHA1AuthenticationManagerTest extends ManagedAuthenticationManagerTestBase
{
    @Override
    public void setUp() throws Exception
    {
        super.setUp();
    }

    @Override
    protected ConfigModelPasswordManagingAuthenticationProvider<?> createAuthManager(final Map<String, Object> attributesMap)
    {
        return new ScramSHA1AuthenticationManager(attributesMap, getBroker());
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


    public void testNonASCIIUser()
    {
        try
        {
            getAuthManager().createUser(getTestName() + Character.toString((char) 0xa3),
                                        "password",
                                        Collections.<String, String>emptyMap());
            fail("Expected exception when attempting to create a user with a non ascii name");
        }
        catch(IllegalArgumentException e)
        {
            // pass
        }
    }

}
