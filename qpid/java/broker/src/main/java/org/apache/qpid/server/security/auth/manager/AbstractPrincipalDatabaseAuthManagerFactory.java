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

import java.io.IOException;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;

/**
 * Factory for {@link PrincipalDatabaseAuthenticationManager} objects configured
 * with either the Plain or Base64MD5 digest {@link PrincipalDatabase}
 * implementation.
 */
public abstract class AbstractPrincipalDatabaseAuthManagerFactory implements AuthenticationManagerFactory
{
    public static final String ATTRIBUTE_PATH = "path";

    private static final Logger LOGGER = Logger.getLogger(AbstractPrincipalDatabaseAuthManagerFactory.class);

    @Override
    public AuthenticationManager createInstance(Map<String, Object> attributes)
    {
        if (attributes == null || !getType().equals(attributes.get(ATTRIBUTE_TYPE)))
        {
            return null;
        }

        String passwordFile = (String) attributes.get(ATTRIBUTE_PATH);
        if (passwordFile == null)
        {
            LOGGER.warn("Password file path must not be null");
            return null;
        }

        PrincipalDatabase principalDatabase = createPrincipalDatabase();
        try
        {
            principalDatabase.setPasswordFile(passwordFile);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e.getMessage(), e);
        }

        return new PrincipalDatabaseAuthenticationManager(principalDatabase);
    }

    abstract String getType();

    abstract PrincipalDatabase createPrincipalDatabase();
}
