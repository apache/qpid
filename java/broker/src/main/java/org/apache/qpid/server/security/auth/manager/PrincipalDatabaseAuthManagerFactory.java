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

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.security.auth.database.Base64MD5PasswordFilePrincipalDatabase;
import org.apache.qpid.server.security.auth.database.PlainPasswordFilePrincipalDatabase;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;

/**
 * Factory for {@link PrincipalDatabaseAuthenticationManager} objects configured with either the
 * Plain or Base64MD5 digest {@link PrincipalDatabase} implementation.
 */
public class PrincipalDatabaseAuthManagerFactory implements AuthenticationManagerFactory
{
    private static final Logger LOGGER = Logger.getLogger(PrincipalDatabaseAuthManagerFactory.class);

    @Override
    public AuthenticationManager createInstance(Configuration configuration)
    {
        if(configuration.subset("pd-auth-manager").isEmpty())
        {
            return null;
        }

        String clazz = configuration.getString("pd-auth-manager.principal-database.class");
        String passwordArgumentName = configuration.getString("pd-auth-manager.principal-database.attributes.attribute.name");
        String passwordFile = configuration.getString("pd-auth-manager.principal-database.attributes.attribute.value");

        PrincipalDatabase principalDatabase = createKnownImplementation(clazz);
        if (principalDatabase == null)
        {
            LOGGER.warn("Config for pd-auth-manager found but principal-database class specified in config " + clazz +
                    " not recognised.");
            return null;
        }

        if (!"passwordFile".equals(passwordArgumentName) || passwordFile == null)
        {
            LOGGER.warn("Config for pd-auth-manager found but config incomplete - expected attributes not found.");
            return null;
        }

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

    private PrincipalDatabase createKnownImplementation(String clazz)
    {
        if (PlainPasswordFilePrincipalDatabase.class.getName().equals(clazz))
        {
            return new PlainPasswordFilePrincipalDatabase();
        }
        else if (Base64MD5PasswordFilePrincipalDatabase.class.getName().equals(clazz))
        {
            return new Base64MD5PasswordFilePrincipalDatabase();
        }
        else
        {
            return null;
        }
    }
}
