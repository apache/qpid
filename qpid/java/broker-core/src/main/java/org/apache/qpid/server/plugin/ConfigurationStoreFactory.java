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
package org.apache.qpid.server.plugin;

import java.util.Map;

import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.IllegalConfigurationException;


public interface ConfigurationStoreFactory extends Pluggable
{
    /**
     * Returns the type of the store this factory can create
     */
    public String getType();

    /**
     * Creates and opens the store from a given location using initial store if provided.
     * <p>
     * If location does not exist, or the overwrite option is specified, then a new store is created from the initial store if it is provided
     *
     * @param storeLocation store location
     * @param initialStore initial store
     * @param overwrite overwrite existing store with initial store
     * @param configProperties a map of configuration properties the store can use to resolve configuration variables
     * @throws IllegalConfigurationException if store cannot be opened in the given location
     */
    public ConfigurationEntryStore createStore(String storeLocation, ConfigurationEntryStore initialStore, boolean overwrite, Map<String, String> configProperties);
}
