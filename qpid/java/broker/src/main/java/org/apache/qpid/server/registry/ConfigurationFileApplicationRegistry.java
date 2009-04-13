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
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.SystemConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.server.management.JMXManagedObjectRegistry;
import org.apache.qpid.server.management.ManagedObjectRegistry;
import org.apache.qpid.server.management.ManagementConfiguration;
import org.apache.qpid.server.management.NoopManagedObjectRegistry;
import org.apache.qpid.server.plugins.PluginManager;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.database.ConfigurationFilePrincipalDatabaseManager;
import org.apache.qpid.server.security.auth.database.PrincipalDatabaseManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.security.access.ACLPlugin;
import org.apache.qpid.server.security.access.ACLManager;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.AMQException;

public class ConfigurationFileApplicationRegistry extends ApplicationRegistry
{

    public ConfigurationFileApplicationRegistry(File configurationURL) throws ConfigurationException
    {
        super(config(configurationURL));
    }

    // Our configuration class needs to make the interpolate method
    // public so it can be called below from the config method.
    private static class MyConfiguration extends CompositeConfiguration
    {
        public String interpolate(String obj)
        {
            return super.interpolate(obj);
        }
    }

    private static final Configuration config(File url) throws ConfigurationException
    {
        // We have to override the interpolate methods so that
        // interpolation takes place accross the entirety of the
        // composite configuration. Without doing this each
        // configuration object only interpolates variables defined
        // inside itself.
        final MyConfiguration conf = new MyConfiguration();
        conf.addConfiguration(new SystemConfiguration()
        {
            protected String interpolate(String o)
            {
                return conf.interpolate(o);
            }
        });
        conf.addConfiguration(new XMLConfiguration(url)
        {
            protected String interpolate(String o)
            {
                return conf.interpolate(o);
            }
        });
        return conf;
    }

    public void initialise() throws Exception
    {
        initialiseManagedObjectRegistry();

        _virtualHostRegistry = new VirtualHostRegistry();

        _pluginManager = new PluginManager(_configuration.getString("plugin-directory"));

        _accessManager = new ACLManager(_configuration, _pluginManager);
        
        _databaseManager = new ConfigurationFilePrincipalDatabaseManager(_configuration);

        _authenticationManager = new PrincipalDatabaseAuthenticationManager(null, null);

        _databaseManager.initialiseManagement(_configuration);

        _managedObjectRegistry.start();

        initialiseVirtualHosts();

    }

    private void initialiseVirtualHosts() throws Exception
    {
        for (String name : getVirtualHostNames())
        {

            _virtualHostRegistry.registerVirtualHost(new VirtualHost(name, getConfiguration().subset("virtualhosts.virtualhost." + name)));
        }
    }

    private void initialiseManagedObjectRegistry() throws AMQException
    {
        ManagementConfiguration config = getConfiguredObject(ManagementConfiguration.class);
        if (config.enabled)
        {
            _managedObjectRegistry = new JMXManagedObjectRegistry();
        }
        else
        {
            _managedObjectRegistry = new NoopManagedObjectRegistry();
        }
    }

    public Collection<String> getVirtualHostNames()
    {
        return getConfiguration().getList("virtualhosts.virtualhost.name");
    }

}
