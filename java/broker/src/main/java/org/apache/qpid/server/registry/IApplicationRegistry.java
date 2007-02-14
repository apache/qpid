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

import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.management.ManagedObjectRegistry;
import org.apache.qpid.server.security.auth.AuthenticationManager;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.commons.configuration.Configuration;

import java.util.Collection;

public interface IApplicationRegistry
{
    /**
     * Initialise the application registry. All initialisation must be done in this method so that any components
     * that need access to the application registry itself for initialisation are able to use it. Attempting to
     * initialise in the constructor will lead to failures since the registry reference will not have been set.
     */
    void initialise() throws Exception;

    void close() throws Exception;

    /**
     * This gets access to a "configured object". A configured object has fields populated from a the configuration
     * object (Commons Configuration) automatically, where it has the appropriate attributes defined on fields.
     * Application registry implementations can choose the refresh strategy or caching approach.
     * @param instanceType the type of object you want initialised. This must be unique - i.e. you can only
     * have a single object of this type in the system.
     * @return the configured object
     */
    <T> T getConfiguredObject(Class<T> instanceType);

    /**
     * Get the low level configuration. For use cases where the configured object approach is not required
     * you can get the complete configuration information.
     * @return a Commons Configuration instance
     */
    Configuration getConfiguration();

    ManagedObjectRegistry getManagedObjectRegistry();

    AuthenticationManager getAuthenticationManager();

    Collection<String> getVirtualHostNames();

    VirtualHostRegistry getVirtualHostRegistry();
}
