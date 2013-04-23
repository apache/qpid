/*
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
package org.apache.qpid.server.security.auth.manager;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.util.ResourceBundleLoader;

public class ExternalAuthenticationManagerFactory implements AuthenticationManagerFactory
{
    public static final String RESOURCE_BUNDLE = "org.apache.qpid.server.security.auth.manager.ExternalAuthenticationProviderAttributeDescriptions";
    public static final String PROVIDER_TYPE = "External";
    public static final String ATTRIBUTE_USE_FULL_DN = "useFullDN";

    public static final Collection<String> ATTRIBUTES = Collections.<String> unmodifiableList(Arrays.asList(
            ATTRIBUTE_TYPE,
            ATTRIBUTE_USE_FULL_DN));

    @Override
    public AuthenticationManager createInstance(Map<String, Object> attributes)
    {
        if (attributes != null && PROVIDER_TYPE.equals(attributes.get(ATTRIBUTE_TYPE)))
        {
            boolean useFullDN = Boolean.valueOf(String.valueOf(attributes.get(ATTRIBUTE_USE_FULL_DN)));

            return new ExternalAuthenticationManager(useFullDN);
        }
        return null;
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return ATTRIBUTES;
    }

    @Override
    public String getType()
    {
        return PROVIDER_TYPE;
    }

    @Override
    public Map<String, String> getAttributeDescriptions()
    {
        return ResourceBundleLoader.getResources(RESOURCE_BUNDLE);
    }

}
