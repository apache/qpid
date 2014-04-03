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

import org.apache.qpid.server.model.ConfiguredObject;

import java.util.Map;
import java.util.UUID;

public interface DurableConfigurationStore
{
    String STORE_TYPE                    = "storeType";
    String STORE_PATH                    = "storePath";
    String IS_MESSAGE_STORE_TOO          = "isMessageStoreToo";


    public static interface Source
    {
        DurableConfigurationStore getDurableConfigurationStore();
    }

    /**
     * Called after instantiation in order to configure the message store. A particular implementation can define
     * whatever parameters it wants.
     *
     * @param parent
     * @param storeSettings store settings
     */
    void openConfigurationStore(ConfiguredObject<?> parent, Map<String, Object> storeSettings) throws StoreException;

    /**
     * Recovers configuration from the store using given recovery handler
     * @param recoveryHandler recovery handler
     */
    void recoverConfigurationStore(ConfigurationRecoveryHandler recoveryHandler) throws StoreException;

    /**
     * Makes the specified object persistent.
     *
     * @param object The object to persist.
     *
     * @throws StoreException If the operation fails for any reason.
     */
    void create(ConfiguredObjectRecord object) throws StoreException;

    /**
     * Removes the specified persistent configured objects.
     *
     * @param objects The objects to remove.
     *
     * @throws StoreException If the operation fails for any reason.
     */
    public UUID[] remove(ConfiguredObjectRecord... objects) throws StoreException;


    /**
     * Updates the specified objects in the persistent store, IF it is already present. If the object
     * is not present in the store, it will only be added if createIfNecessary is set to true, otherwise an exception
     * will be thrown.
     *
     * @param createIfNecessary if false then will fail if the object does not exist.
     * @param records the records to update
     *
     * @throws StoreException If the operation fails for any reason.
     */
    void update(boolean createIfNecessary, ConfiguredObjectRecord... records) throws StoreException;

    void closeConfigurationStore() throws StoreException;

}
