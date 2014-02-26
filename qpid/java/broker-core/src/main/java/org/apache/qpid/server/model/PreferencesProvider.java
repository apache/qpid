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

package org.apache.qpid.server.model;

import java.util.Map;
import java.util.Set;

public interface PreferencesProvider<X extends PreferencesProvider<X>> extends ConfiguredObject<X>
{
    String DURABLE         = "durable";
    String LIFETIME_POLICY = "lifetimePolicy";

    /**
     * Returns preferences {@link Map} for a given user ID
     * @param userId user ID to retrieve preferences for
     * @return preferences {@link Map}
     */
    Map<String, Object> getPreferences(String userId);

    /**
     * Set user preferences as specified in a given {@link Map}
     * @param userId user ID to set preferences for
     * @param preferences new preferences
     * @return existing user preferences
     */
    Map<String, Object> setPreferences(String userId, Map<String, Object> preferences);

    /**
     * Delete preferences for given user IDs
     * @param userIDs user IDs to delete preferences for
     * @return user an array with user IDs having preferences deleted
     */
    String[] deletePreferences(String... userIDs);

    /**
     * Returns set of the user IDs having preferences set
     * @return user IDs
     */
    Set<String> listUserIDs();

    /**
     * Returns authentication provider associated with this preferences provider
     * @return authentication provider
     */
    AuthenticationProvider getAuthenticationProvider();
}
