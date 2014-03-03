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

import static org.mockito.Mockito.mock;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import junit.framework.TestCase;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.security.auth.database.PlainPasswordFilePrincipalDatabase;

public class PlainPasswordFileAuthenticationManagerFactoryTest extends  TestCase
{
    AuthenticationManagerFactory _factory = new PlainPasswordFileAuthenticationManagerFactory();
    private Map<String, Object> _configuration = new HashMap<String, Object>();
    private File _emptyPasswordFile;
    private Broker _broker = mock(Broker.class);

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _emptyPasswordFile = File.createTempFile(getName(), "passwd");
        _emptyPasswordFile.deleteOnExit();
        _configuration.put(AuthenticationProvider.ID, UUID.randomUUID());
        _configuration.put(AuthenticationProvider.NAME, getName());
    }

    public void testPlainInstanceCreated() throws Exception
    {
        _configuration.put(AuthenticationProvider.TYPE, PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE);
        _configuration.put("path", _emptyPasswordFile.getAbsolutePath());

        AuthenticationManager manager = _factory.createInstance(_broker, _configuration, false);
        assertNotNull(manager);
        assertTrue(manager instanceof PrincipalDatabaseAuthenticationManager);
        assertTrue(((PrincipalDatabaseAuthenticationManager)manager).getPrincipalDatabase() instanceof PlainPasswordFilePrincipalDatabase);
    }

    public void testPasswordFileNotFound() throws Exception
    {
        //delete the file
        _emptyPasswordFile.delete();

        _configuration.put(AuthenticationProvider.TYPE, PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE);
        _configuration.put("path", _emptyPasswordFile.getAbsolutePath());


        AuthenticationManager manager = _factory.createInstance(_broker, _configuration, false);

        assertNotNull(manager);
        assertTrue(manager instanceof PrincipalDatabaseAuthenticationManager);
        assertTrue(((PrincipalDatabaseAuthenticationManager)manager).getPrincipalDatabase() instanceof PlainPasswordFilePrincipalDatabase);
    }

    public void testReturnsNullWhenNoConfig() throws Exception
    {
        AuthenticationManager manager = _factory.createInstance(_broker, _configuration, false);
        assertNull(manager);
    }

    public void testReturnsNullWhenConfigForOtherAuthManagerType() throws Exception
    {
        _configuration.put(AuthenticationProvider.TYPE, "other-auth-manager");
        AuthenticationManager manager = _factory.createInstance(_broker, _configuration, false);
        assertNull(manager);
    }

    public void testThrowsExceptionWhenConfigForPlainPDImplementationNoPasswordFileValueSpecified() throws Exception
    {
        _configuration.put(AuthenticationProvider.TYPE, PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE);

        try
        {
            AuthenticationManager manager = _factory.createInstance(_broker, _configuration, false);
            fail("No authentication manager should be created");
        }
        catch (IllegalArgumentException e)
        {
            // pass;
        }
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
