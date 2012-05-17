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
 */

package org.apache.qpid.server.registry;

import java.net.InetSocketAddress;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.security.auth.manager.AnonymousAuthenticationManager;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.util.InternalBrokerBaseCase;

public class ApplicationRegistryAuthenticationManagerTest extends InternalBrokerBaseCase
{
    private Runnable _configureTask;

    @Override
    public void tearDown() throws Exception
    {
        _configureTask = null;
        super.tearDown();
    }

    @Override
    protected void createBroker() throws Exception
    {
        // Do nothing - we don't want create broker called in setUp
    }

    @Override
    protected void configure()
    {
        if(_configureTask != null)
        {
            _configureTask.run();
        }
    }

    @Override
    protected IApplicationRegistry createApplicationRegistry() throws ConfigurationException
    {
        return new TestableApplicationRegistry(getConfiguration());
    }

    private void reallyCreateBroker() throws Exception
    {
        super.createBroker();
    }

    public void testNoAuthenticationManagers() throws Exception
    {
        try
        {
            reallyCreateBroker();
            fail("Expected a ConfigurationException when no AuthenticationManagers are defined");
        }
        catch(ConfigurationException e)
        {
            // pass
        }
    }

    public void testSingleAuthenticationManager() throws Exception
    {
        _configureTask =
                new Runnable()
                {
                    @Override
                    public void run()
                    {
                        getConfiguration().getConfig().addProperty("security.anonymous-auth-manager", "");
                    }
                };

        try
        {
            reallyCreateBroker();
        }
        catch(ConfigurationException e)
        {
            fail("Unexpected ConfigurationException when creating the registry with a single AuthenticationManager");
        }
    }

    public void testMultipleAuthenticationManagersNoDefault() throws Exception
    {
        _configureTask =
                new Runnable()
                {
                    @Override
                    public void run()
                    {
                        getConfiguration().getConfig().addProperty("security.anonymous-auth-manager", "");
                        getConfiguration().getConfig().addProperty("security.pd-auth-manager.principal-database.class","org.apache.qpid.server.security.auth.database.PlainPasswordFilePrincipalDatabase");
                    }
                };
        try
        {
            reallyCreateBroker();
            fail("Expected ConfigurationException as two AuthenticationManagers are defined, but there is no default specified");
        }
        catch (ConfigurationException e)
        {
            // pass
        }
    }

    public void testDefaultAuthenticationManager() throws Exception
    {
        _configureTask =
                new Runnable()
                {
                    @Override
                    public void run()
                    {
                        getConfiguration().getConfig().addProperty("security.anonymous-auth-manager", "");
                        getConfiguration().getConfig().addProperty("security.pd-auth-manager.principal-database.class","org.apache.qpid.server.security.auth.database.PlainPasswordFilePrincipalDatabase");
                        getConfiguration().getConfig().addProperty("security.default-auth-manager", "AnonymousAuthenticationManager");
                    }
                };
        try
        {
            reallyCreateBroker();
        }
        catch (ConfigurationException e)
        {
            fail("Unexpected ConfigurationException when two AuthenticationManagers are defined, but there is a default specified");
        }

        AuthenticationManager authMgr =
                ApplicationRegistry.getInstance().getAuthenticationManager(new InetSocketAddress(1));

        assertNotNull("AuthenticationManager should not be null for any socket", authMgr);
        assertEquals("AuthenticationManager not of expected class", AnonymousAuthenticationManager.class, authMgr.getClass());


    }

    public void testMappedAuthenticationManager() throws Exception
    {
        _configureTask =
                new Runnable()
                {
                    @Override
                    public void run()
                    {
                        getConfiguration().getConfig().addProperty("security.anonymous-auth-manager", "");
                        getConfiguration().getConfig().addProperty("security.pd-auth-manager.principal-database.class","org.apache.qpid.server.security.auth.database.PlainPasswordFilePrincipalDatabase");
                        getConfiguration().getConfig().addProperty("security.default-auth-manager", "PrincipalDatabaseAuthenticationManager");
                        getConfiguration().getConfig().addProperty("security.port-mappings.port-mapping.port", "200");
                        getConfiguration().getConfig().addProperty("security.port-mappings.port-mapping.auth-manager", "AnonymousAuthenticationManager");
                    }
                };
        reallyCreateBroker();

        AuthenticationManager authMgr =
                ApplicationRegistry.getInstance().getAuthenticationManager(new InetSocketAddress(200));

        assertNotNull("AuthenticationManager should not be null for any socket", authMgr);
        assertEquals("AuthenticationManager not of expected class", AnonymousAuthenticationManager.class, authMgr.getClass());

        // test the default is still in effect for other ports
        authMgr = ApplicationRegistry.getInstance().getAuthenticationManager(new InetSocketAddress(1));
        assertEquals("AuthenticationManager not of expected class", PrincipalDatabaseAuthenticationManager.class, authMgr.getClass());


    }
}
