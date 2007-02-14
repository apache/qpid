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

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.MapConfiguration;
import org.apache.qpid.server.management.ManagedObjectRegistry;
import org.apache.qpid.server.management.NoopManagedObjectRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.security.auth.AuthenticationManager;
import org.apache.qpid.server.security.auth.NullAuthenticationManager;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class NullApplicationRegistry extends ApplicationRegistry
{
    private ManagedObjectRegistry _managedObjectRegistry;

    private AuthenticationManager _authenticationManager;

    private VirtualHostRegistry _virtualHostRegistry;


    public NullApplicationRegistry()
    {
        super(new MapConfiguration(new HashMap()));
    }

    public void initialise() throws Exception
    {
        _configuration.addProperty("store.class","org.apache.qpid.server.store.MemoryMessageStore");

        _managedObjectRegistry = new NoopManagedObjectRegistry();
        _virtualHostRegistry = new VirtualHostRegistry();
        VirtualHost dummyHost = new VirtualHost("test",getConfiguration());
        _virtualHostRegistry.registerVirtualHost(dummyHost);
        _virtualHostRegistry.setDefaultVirtualHostName("test");
        _authenticationManager = new NullAuthenticationManager();

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

    public AuthenticationManager getAuthenticationManager()
    {
        return _authenticationManager;
    }

    public Collection<String> getVirtualHostNames()
    {
        String[] hosts = {"test"}; 
        return Arrays.asList( hosts );
    }

    public VirtualHostRegistry getVirtualHostRegistry()
    {
        return _virtualHostRegistry;
    }
}


