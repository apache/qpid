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
import org.apache.commons.configuration.MapConfiguration;
import org.apache.qpid.server.management.ManagedObjectRegistry;
import org.apache.qpid.server.management.NoopManagedObjectRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.security.auth.database.PrincipalDatabaseManager;
import org.apache.qpid.server.security.auth.database.PropertiesPrincipalDatabaseManager;
import org.apache.qpid.server.security.access.AccessManager;
import org.apache.qpid.server.security.access.AllowAll;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class NullApplicationRegistry extends ApplicationRegistry
{
    private ManagedObjectRegistry _managedObjectRegistry;

    private AuthenticationManager _authenticationManager;

    private VirtualHostRegistry _virtualHostRegistry;

    private AccessManager _accessManager;

    private PrincipalDatabaseManager _databaseManager;


    public NullApplicationRegistry()
    {
        super(new MapConfiguration(new HashMap()));
    }

    public void initialise()
            throws
            Exception
    {
        _configuration.addProperty("store.class", "org.apache.qpid.server.messageStore.MemoryMessageStore");
        _configuration.addProperty("txn.class", "org.apache.qpid.server.txn.MemoryTransactionManager");
       // _configuration.addProperty("store.class", "org.apache.qpid.server.messageStore.JDBCStore");
       // _configuration.addProperty("txn.class", "org.apache.qpid.server.txn.JDBCTransactionManager");

        Properties users = new Properties();

        users.put("guest", "guest");

        _databaseManager = new PropertiesPrincipalDatabaseManager("default", users);

        _accessManager = new AllowAll();

        _authenticationManager = new PrincipalDatabaseAuthenticationManager(null, null);

        _managedObjectRegistry = new NoopManagedObjectRegistry();
        _virtualHostRegistry = new VirtualHostRegistry();
        VirtualHost dummyHost = new VirtualHost("test", getConfiguration());
        _virtualHostRegistry.registerVirtualHost(dummyHost);
        _virtualHostRegistry.setDefaultVirtualHostName("test");

        _configuration.addProperty("heartbeat.delay", 10 * 60); // 10 minutes

    }

    public Configuration getConfiguration()
    {
        return _configuration;
    }


    public ManagedObjectRegistry getManagedObjectRegistry()
    {
        return _managedObjectRegistry;
    }

    public PrincipalDatabaseManager getDatabaseManager()
    {
        return _databaseManager;
    }

    public AuthenticationManager getAuthenticationManager()
    {
        return _authenticationManager;
    }

    public Collection<String> getVirtualHostNames()
    {
        String[] hosts = {"test"};
        return Arrays.asList(hosts);
    }

    public VirtualHostRegistry getVirtualHostRegistry()
    {
        return _virtualHostRegistry;
    }

    public AccessManager getAccessManager()
    {
        return _accessManager;
    }
}



