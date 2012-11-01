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

import java.util.Map;
import java.util.Properties;

import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.security.auth.database.PropertiesPrincipalDatabase;

public class TestAuthenticationManagerFactory implements AuthenticationManagerFactory
{

    public static final String TEST_AUTH_MANAGER_MARKER = "test-auth-manager";

    @Override
    public AuthenticationManager createInstance(Map<String, Object> attributes)
    {
        if (TEST_AUTH_MANAGER_MARKER.equals(attributes.get(TYPE)))
        {
            final Properties users = new Properties();
            users.put("guest","guest");
            users.put("admin","admin");
            final PropertiesPrincipalDatabase ppd = new PropertiesPrincipalDatabase(users);
            final AuthenticationManager pdam =  new PrincipalDatabaseAuthenticationManager(ppd);
            return pdam;
        }
        return null;
    }

}
