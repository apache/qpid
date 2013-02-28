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
package org.apache.qpid.server.configuration;

import java.util.UUID;

public interface ConfigurationEntryStore
{
    /**
     * Opens the store from a given location.
     * <p>
     * If location does not exists than a new empty store is created with a single root entry
     *
     * @param storeLocation store location
     * @throws IllegalConfigurationException if store cannot be opened in the given location
     */
    void open(String storeLocation);

    /**
     * Opens the store from a given location.
     * <p>
     * If location does not exists than a new store is created either empty or from the initial store location if it is provided
     *
     * @param storeLocation store location
     * @param initialStoreLocation initial store location
     * @throws IllegalConfigurationException if store cannot be opened in the given location or initial store location does not
     *             exists or corrupted.
     */
    void open(String storeLocation, String initialStoreLocation);

    /**
     * Opens the store from a given location.
     * <p>
     * If location does not exists than a new store is created either empty or from the initial store if it is provided
     *
     * @param storeLocation store location
     * @param initialStore initial store
     * @throws IllegalConfigurationException if store cannot be opened in the given location
     */
    void open(String storeLocation, ConfigurationEntryStore initialStore);

    /**
     * Returns stored root configuration entry
     *
     * @return root entry
     */
    ConfigurationEntry getRootEntry();

    /**
     * Returns the configuration entry with a given id.
     *
     * @return entry with a given id or null if entry does not exists
     */
    ConfigurationEntry getEntry(UUID id);

    /**
     * Saves given entries in the store.
     *
     * @param entries entries to store
     * @throws IllegalConfigurationException if save operation fails
     */
    void save(ConfigurationEntry... entries);

    /**
     * Removes the entries with given IDs and all their children
     *
     * @param entryIds IDs of entries to remove
     * @return IDs of removed entries
     * @throws IllegalConfigurationException if remove operation fails
     */
    UUID[] remove(UUID... entryIds);

    /**
     * Copies the store into the given location
     *
     * @param target location to copy store into
     * @throws IllegalConfigurationException if store cannot be copied into given location
     */
    public void copyTo(String copyLocation);
}
