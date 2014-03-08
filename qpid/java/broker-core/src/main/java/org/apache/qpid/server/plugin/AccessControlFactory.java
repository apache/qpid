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

import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.EventLoggerProvider;
import org.apache.qpid.server.model.AccessControlProvider;
import org.apache.qpid.server.security.AccessControl;

public interface AccessControlFactory extends Pluggable
{
    public static final String ATTRIBUTE_TYPE = AccessControlProvider.TYPE;

    AccessControl createInstance(Map<String, Object> attributes, final EventLoggerProvider eventLogger);

    /**
     * Returns the access control provider type
     * @return authentication provider type
     */
    String getType();

    /**
     * Get the names of attributes of the access control which can be passed into
     * {@link #createInstance(java.util.Map, org.apache.qpid.server.logging.EventLogger)} to create the group manager
     *
     * @return the collection of attribute names
     */
    Collection<String> getAttributeNames();

    /**
     * @return returns human readable descriptions for the attributes
     */
    Map<String, String> getAttributeDescriptions();
}
