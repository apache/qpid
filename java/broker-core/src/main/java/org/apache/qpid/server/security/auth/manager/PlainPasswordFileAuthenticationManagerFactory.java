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
package org.apache.qpid.server.security.auth.manager;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.util.ResourceBundleLoader;

public class PlainPasswordFileAuthenticationManagerFactory implements AuthenticationManagerFactory
{
    public static final String RESOURCE_BUNDLE = "org.apache.qpid.server.security.auth.manager.PasswordFileAuthenticationProviderAttributeDescriptions";
    public static final String ATTRIBUTE_PATH = "path";


    public static final Collection<String> ATTRIBUTES = Collections.unmodifiableList(Arrays.asList(
            AuthenticationProvider.TYPE,
            ATTRIBUTE_PATH));

    public static final String PROVIDER_TYPE = "PlainPasswordFile";

    @Override
    public String getType()
    {
        return PROVIDER_TYPE;
    }

    @Override
    public AbstractAuthenticationManager createInstance(final Broker broker,
                                                        final Map<String, Object> attributes,
                                                        final boolean recovering)
    {
        if (attributes == null || !getType().equals(attributes.get(AuthenticationProvider.TYPE)))
        {
            return null;
        }

        return new PlainPasswordDatabaseAuthenticationManager(broker, Collections.<String,Object>emptyMap(),attributes,recovering);
    }

    @Override
    public Map<String, String> getAttributeDescriptions()
    {
        return ResourceBundleLoader.getResources(RESOURCE_BUNDLE);
    }


    @Override
    public Collection<String> getAttributeNames()
    {
        return ATTRIBUTES;
    }
}
