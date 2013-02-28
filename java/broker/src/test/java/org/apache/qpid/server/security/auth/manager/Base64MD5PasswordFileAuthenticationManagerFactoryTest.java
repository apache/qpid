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

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.security.auth.database.Base64MD5PasswordFilePrincipalDatabase;

public class Base64MD5PasswordFileAuthenticationManagerFactoryTest extends  TestCase
{
    AuthenticationManagerFactory _factory = new Base64MD5PasswordFileAuthenticationManagerFactory();
    private Map<String, Object> _configuration = new HashMap<String, Object>();
    private File _emptyPasswordFile;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _emptyPasswordFile = File.createTempFile(getName(), "passwd");
        _emptyPasswordFile.deleteOnExit();
    }

    public void testBase64MD5InstanceCreated() throws Exception
    {
        _configuration.put(AbstractPrincipalDatabaseAuthManagerFactory.ATTRIBUTE_TYPE, Base64MD5PasswordFileAuthenticationManagerFactory.PROVIDER_TYPE);
        _configuration.put(AbstractPrincipalDatabaseAuthManagerFactory.ATTRIBUTE_PATH, _emptyPasswordFile.getAbsolutePath());

        AuthenticationManager manager = _factory.createInstance(_configuration);
        assertNotNull(manager);
        assertTrue(manager instanceof PrincipalDatabaseAuthenticationManager);
        assertTrue(((PrincipalDatabaseAuthenticationManager)manager).getPrincipalDatabase() instanceof Base64MD5PasswordFilePrincipalDatabase);
    }

    public void testPasswordFileNotFound() throws Exception
    {
        //delete the file
        _emptyPasswordFile.delete();

        _configuration.put(AbstractPrincipalDatabaseAuthManagerFactory.ATTRIBUTE_TYPE, Base64MD5PasswordFileAuthenticationManagerFactory.PROVIDER_TYPE);
        _configuration.put(AbstractPrincipalDatabaseAuthManagerFactory.ATTRIBUTE_PATH, _emptyPasswordFile.getAbsolutePath());

        try
        {
            _factory.createInstance(_configuration);
        }
        catch (RuntimeException re)
        {
            assertTrue(re.getCause() instanceof FileNotFoundException);
        }
    }

    public void testReturnsNullWhenNoConfig() throws Exception
    {
        AuthenticationManager manager = _factory.createInstance(_configuration);
        assertNull(manager);
    }

    public void testReturnsNullWhenConfigForOtherAuthManagerType() throws Exception
    {
        _configuration.put(AbstractPrincipalDatabaseAuthManagerFactory.ATTRIBUTE_TYPE, "other-auth-manager");
        AuthenticationManager manager = _factory.createInstance(_configuration);
        assertNull(manager);
    }

    public void testReturnsNullWhenConfigForPlainPDImplementationNoPasswordFileValueSpecified() throws Exception
    {
        _configuration.put(AbstractPrincipalDatabaseAuthManagerFactory.ATTRIBUTE_TYPE, Base64MD5PasswordFileAuthenticationManagerFactory.PROVIDER_TYPE);

        AuthenticationManager manager = _factory.createInstance(_configuration);
        assertNull(manager);
    }

    @Override
    protected void tearDown() throws Exception
    {
        try
        {
            if (_emptyPasswordFile == null && _emptyPasswordFile.exists())
            {
                _emptyPasswordFile.delete();
            }
        }
        finally
        {
            super.tearDown();
        }
    }
}