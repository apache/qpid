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
package org.apache.qpid.server.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Properties;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.MapConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.management.NoopManagedObjectRegistry;
import org.apache.qpid.server.plugins.PluginManager;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.access.ACLManager;
import org.apache.qpid.server.security.access.plugins.AllowAll;
import org.apache.qpid.server.security.auth.database.PropertiesPrincipalDatabaseManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class NullApplicationRegistry extends ApplicationRegistry
{
    public NullApplicationRegistry() throws ConfigurationException
    {
        super(new ServerConfiguration(new PropertiesConfiguration()));
    }

    public void initialise() throws Exception
    {
        _logger.info("Initialising NullApplicationRegistry");
        
        _configuration.setHousekeepingExpiredMessageCheckPeriod(200);
        
        Properties users = new Properties();

        users.put("guest", "guest");

        _databaseManager = new PropertiesPrincipalDatabaseManager("default", users);

        _accessManager = new ACLManager(_configuration.getSecurityConfiguration(), _pluginManager, AllowAll.FACTORY);

        _authenticationManager = new PrincipalDatabaseAuthenticationManager(null, null);

        _managedObjectRegistry = new NoopManagedObjectRegistry();
        _virtualHostRegistry = new VirtualHostRegistry();
        PropertiesConfiguration vhostProps = new PropertiesConfiguration();
        VirtualHostConfiguration hostConfig = new VirtualHostConfiguration("test", vhostProps);
        VirtualHost dummyHost = new VirtualHost(hostConfig);
        _virtualHostRegistry.registerVirtualHost(dummyHost);
        _virtualHostRegistry.setDefaultVirtualHostName("test");
        _pluginManager = new PluginManager("");

    }

    public Collection<String> getVirtualHostNames()
    {
        String[] hosts = {"test"};
        return Arrays.asList(hosts);
    }
}



