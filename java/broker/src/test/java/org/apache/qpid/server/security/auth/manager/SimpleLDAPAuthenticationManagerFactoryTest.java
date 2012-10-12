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

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.XMLConfiguration;

import junit.framework.TestCase;

public class SimpleLDAPAuthenticationManagerFactoryTest extends TestCase
{
    private SimpleLDAPAuthenticationManagerFactory _factory = new SimpleLDAPAuthenticationManagerFactory();
    private Configuration _configuration = new XMLConfiguration();

    public void testInstanceCreated() throws Exception
    {
        _configuration.setProperty("simple-ldap-auth-manager.provider-url", "ldaps://example.com:636/");
        _configuration.setProperty("simple-ldap-auth-manager.search-context", "dc=example");

        AuthenticationManager manager = _factory.createInstance(_configuration);
        assertNotNull(manager);
    }

    public void testReturnsNullWhenNoConfig() throws Exception
    {
        AuthenticationManager manager = _factory.createInstance(_configuration);
        assertNull(manager);
    }
}
