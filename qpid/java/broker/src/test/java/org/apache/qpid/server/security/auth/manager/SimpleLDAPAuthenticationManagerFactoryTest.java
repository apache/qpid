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
 *
 */
package org.apache.qpid.server.security.auth.manager;

import java.util.HashMap;
import java.util.Map;


import junit.framework.TestCase;

public class SimpleLDAPAuthenticationManagerFactoryTest extends TestCase
{
    private SimpleLDAPAuthenticationManagerFactory _factory = new SimpleLDAPAuthenticationManagerFactory();
    private Map<String, Object> _configuration = new HashMap<String, Object>();

    public void testInstanceCreated() throws Exception
    {
        _configuration.put(SimpleLDAPAuthenticationManagerFactory.ATTRIBUTE_TYPE, SimpleLDAPAuthenticationManagerFactory.PROVIDER_TYPE);
        _configuration.put("providerUrl", "ldaps://example.com:636/");
        _configuration.put("searchContext", "dc=example");

        AuthenticationManager manager = _factory.createInstance(_configuration);
        assertNotNull(manager);
    }

    public void testReturnsNullWhenNoConfig() throws Exception
    {
        AuthenticationManager manager = _factory.createInstance(_configuration);
        assertNull(manager);
    }
}
