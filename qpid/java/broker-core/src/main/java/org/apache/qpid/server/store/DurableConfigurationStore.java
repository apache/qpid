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

import java.util.UUID;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;

public interface DurableConfigurationStore
{
    /**
     * Initializes and opens the configuration store.
     * @param parent
     * @param overwrite
     * @param initialRecords
     *
     */
    void openConfigurationStore(ConfiguredObject<?> parent,
                                final boolean overwrite,
                                final ConfiguredObjectRecord... initialRecords) throws StoreException;

    /**
     * Requests that the store performs any upgrade work on the store's structure. If there is no
     * upgrade work to be done, this method should return without doing anything.
     *
     * @throws StoreException signals that a problem was encountered trying to upgrade the store.
     * Implementations, on encountering a problem, should endeavour to leave the store in its
     * original state.
     */
    void upgradeStoreStructure() throws StoreException;

    /**
     * Visit all configured object records with given handler.
     *
     * @param handler a handler to invoke on each configured object record
     * @throws StoreException
     */
    void visitConfiguredObjectRecords(ConfiguredObjectRecordHandler handler) throws StoreException;

    /**
     * Makes the specified object persistent.
     *
     * @param object The object to persist.
     *
     * @throws StoreException If the operation fails for any reason.
     */
    void create(ConfiguredObjectRecord object) throws StoreException;

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

    /**
     * Removes the specified persistent configured objects.
     *
     * @param objects The objects to remove.
     *
     * @throws StoreException If the operation fails for any reason.
     */
    public UUID[] remove(ConfiguredObjectRecord... objects) throws StoreException;

    void closeConfigurationStore() throws StoreException;

    /**
     * Deletes the configuration store from its underlying storage.  If the store
     * has not be opened, then this call will be ignored.  The store should be closed
     * before making this call.
     */
    void onDelete(ConfiguredObject<?> parent);
}
