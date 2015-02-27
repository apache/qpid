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

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.security.auth.database.Base64MD5PasswordFilePrincipalDatabase;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;

@ManagedObject( category = false, managesChildren = true, type = "Base64MD5PasswordFile" )
public class Base64MD5PasswordDatabaseAuthenticationManager
        extends PrincipalDatabaseAuthenticationManager<Base64MD5PasswordDatabaseAuthenticationManager>
{


    public static final String PROVIDER_TYPE = "Base64MD5PasswordFile";

    @ManagedObjectFactoryConstructor
    protected Base64MD5PasswordDatabaseAuthenticationManager(final Map<String, Object> attributes, final Broker broker)
    {
        super(attributes, broker);
    }

    @Override
    protected PrincipalDatabase createDatabase()
    {
        return new Base64MD5PasswordFilePrincipalDatabase();
    }
}
