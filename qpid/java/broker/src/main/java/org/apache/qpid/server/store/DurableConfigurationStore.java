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
package org.apache.qpid.server.store;

import java.util.Map;
import java.util.UUID;
import org.apache.commons.configuration.Configuration;

import org.apache.qpid.AMQStoreException;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.queue.AMQQueue;

public interface DurableConfigurationStore
{

    public static interface Source
    {
        DurableConfigurationStore getDurableConfigurationStore();
    }

    /**
     * Called after instantiation in order to configure the message store. A particular implementation can define
     * whatever parameters it wants.
     *
     *
     *
     * @param name             The name to be used by this store
     * @param recoveryHandler  Handler to be called as the store recovers on start up
     * @param virtualHost
     * @throws Exception If any error occurs that means the store is unable to configure itself.
     */
    void configureConfigStore(String name,
                              ConfigurationRecoveryHandler recoveryHandler,
                              VirtualHost virtualHost) throws Exception;



    /**
     * Makes the specified object persistent.
     *
     * @param id The id of the object to persist.
     * @param type The type of the object to persist
     * @param attributes the attributes of the object to persist
     *
     * @throws AMQStoreException If the operation fails for any reason.
     */
    void create(UUID id, String type, Map<String, Object> attributes) throws AMQStoreException;

    /**
     * Removes the specified persistent configured object.
     *
     * @param id The id of the object to remove.
     * @param type The type of the object to remove
     *
     * @throws AMQStoreException If the operation fails for any reason.
     */
    void remove(UUID id, String type) throws AMQStoreException;


    /**
     * Updates the specified object in the persistent store, IF it is already present. If the object
     * is not present in the store, it will not be added.
     *
     * @param id The id of the object to update.
     * @param type The type of the object to update
     * @param attributes the updated attributes
     *
     * @throws AMQStoreException If the operation fails for any reason.
     */
    void update(UUID id, String type, Map<String, Object> attributes) throws AMQStoreException;


    public void update(ConfiguredObjectRecord... records) throws AMQStoreException;


}
