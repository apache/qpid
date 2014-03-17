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

import org.apache.qpid.server.model.VirtualHost;

public interface DurableConfigurationStore
{
    String STORE_TYPE                    = "storeType";
    String STORE_PATH                    = "storePath";

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
     *
     *
     * @param virtualHost
     * @param recoveryHandler  Handler to be called as the store recovers on start up
     */
    void configureConfigStore(VirtualHost virtualHost, ConfigurationRecoveryHandler recoveryHandler);


    /**
     * Makes the specified object persistent.
     *
     * @param id The id of the object to persist.
     * @param type The type of the object to persist
     * @param attributes the attributes of the object to persist
     *
     * @throws StoreException If the operation fails for any reason.
     */
    void create(UUID id, String type, Map<String, Object> attributes) throws StoreException;

    /**
     * Removes the specified persistent configured object.
     *
     * @param id The id of the object to remove.
     * @param type The type of the object to remove
     *
     * @throws StoreException If the operation fails for any reason.
     */
    void remove(UUID id, String type) throws StoreException;

    public UUID[] removeConfiguredObjects(UUID... objects) throws StoreException;


    /**
     * Updates the specified object in the persistent store, IF it is already present. If the object
     * is not present in the store, it will not be added.
     *
     * @param id The id of the object to update.
     * @param type The type of the object to update
     * @param attributes the updated attributes
     *
     * @throws StoreException If the operation fails for any reason.
     */
    void update(UUID id, String type, Map<String, Object> attributes) throws StoreException;


    public void update(ConfiguredObjectRecord... records) throws StoreException;
    public void update(boolean createIfNecessary, ConfiguredObjectRecord... records) throws StoreException;


    void close() throws Exception;
}
