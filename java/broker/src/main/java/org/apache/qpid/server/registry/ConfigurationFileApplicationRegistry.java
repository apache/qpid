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
package org.apache.qpid.server.registry;

import java.io.File;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.management.JMXManagedObjectRegistry;
import org.apache.qpid.server.management.NoopManagedObjectRegistry;
import org.apache.qpid.server.plugins.PluginManager;
import org.apache.qpid.server.security.access.ACLManager;
import org.apache.qpid.server.security.auth.database.ConfigurationFilePrincipalDatabaseManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class ConfigurationFileApplicationRegistry extends ApplicationRegistry
{

    public ConfigurationFileApplicationRegistry(File configurationURL) throws ConfigurationException
    {
        super(new ServerConfiguration(configurationURL));
    }

    public void initialise() throws Exception
    {
        initialiseManagedObjectRegistry();

        _virtualHostRegistry = new VirtualHostRegistry();

        _pluginManager = new PluginManager(_configuration.getPluginDirectory());

        _accessManager = new ACLManager(_configuration.getSecurityConfiguration(), _pluginManager);
        
        _databaseManager = new ConfigurationFilePrincipalDatabaseManager(_configuration);

        _authenticationManager = new PrincipalDatabaseAuthenticationManager(null, null);

        _databaseManager.initialiseManagement(_configuration);

        _managedObjectRegistry.start();

        initialiseVirtualHosts();

    }

    private void initialiseVirtualHosts() throws Exception
    {        
        for (String name : _configuration.getVirtualHosts())
        {
            _virtualHostRegistry.registerVirtualHost(new VirtualHost(_configuration.getVirtualHostConfig(name)));
        }
        getVirtualHostRegistry().setDefaultVirtualHostName(_configuration.getDefaultVirtualHost());
    }

    private void initialiseManagedObjectRegistry() throws AMQException
    {
        if (_configuration.getManagementEnabled())
        {
            _managedObjectRegistry = new JMXManagedObjectRegistry();
        }
        else
        {
            _managedObjectRegistry = new NoopManagedObjectRegistry();
        }
    }
}
