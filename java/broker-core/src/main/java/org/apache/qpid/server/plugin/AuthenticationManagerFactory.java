/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.qpid.server.plugin;

import java.util.Collection;
import java.util.Map;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;

public interface AuthenticationManagerFactory extends Pluggable
{
    public static final String ATTRIBUTE_TYPE = AuthenticationProvider.TYPE;

    /**
     * Returns the authentication provider type
     * @return authentication provider type
     */
    String getType();

    /**
     * Creates authentication manager from the provided attributes
     *
     * @param attributes
     *            attributes to create authentication manager
     * @return authentication manager instance
     */
    AuthenticationManager createInstance(Map<String, Object> attributes);

    /**
     * Get the names of attributes the authentication manager which can be passed into {@link #createInstance(Map)} to create the
     * authentication manager
     *
     * @return the collection of attribute names
     */
    Collection<String> getAttributeNames();

    /**
     * @return returns human readable descriptions for the attributes
     */
    Map<String, String> getAttributeDescriptions();
}
