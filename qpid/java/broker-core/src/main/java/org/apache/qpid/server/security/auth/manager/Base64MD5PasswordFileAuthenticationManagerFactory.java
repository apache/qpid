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
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.model.AbstractConfiguredObjectTypeFactory;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.ResolvedObject;
import org.apache.qpid.server.store.UnresolvedConfiguredObject;
import org.apache.qpid.server.util.ResourceBundleLoader;

@PluggableService
public class Base64MD5PasswordFileAuthenticationManagerFactory
        extends AbstractConfiguredObjectTypeFactory<Base64MD5PasswordDatabaseAuthenticationManager>
        implements AuthenticationManagerFactory<Base64MD5PasswordDatabaseAuthenticationManager>
{
    public static final String PROVIDER_TYPE = "Base64MD5PasswordFile";
    public static final String RESOURCE_BUNDLE = "org.apache.qpid.server.security.auth.manager.PasswordFileAuthenticationProviderAttributeDescriptions";
    public static final String ATTRIBUTE_PATH = "path";


    public static final Collection<String> ATTRIBUTES = Collections.unmodifiableList(Arrays.asList(
            AuthenticationProvider.TYPE,
            ATTRIBUTE_PATH));

    public Base64MD5PasswordFileAuthenticationManagerFactory()
    {
        super(Base64MD5PasswordDatabaseAuthenticationManager.class);
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

    @Override
    public Base64MD5PasswordDatabaseAuthenticationManager createInstance(final Map<String, Object> attributes,
                                                                         final ConfiguredObject<?>... parents)
    {
        return new Base64MD5PasswordDatabaseAuthenticationManager(getParent(Broker.class, parents), attributes);
    }

    @Override
    public UnresolvedConfiguredObject<Base64MD5PasswordDatabaseAuthenticationManager> recover(final ConfiguredObjectFactory factory,
                                                                                              final ConfiguredObjectRecord record,
                                                                                              final ConfiguredObject<?>... parents)
    {

        Map<String, Object> attributes = new HashMap<String, Object>(record.getAttributes());
        attributes.put(ConfiguredObject.ID, record.getId());
        final Base64MD5PasswordDatabaseAuthenticationManager authenticationManager =
                new Base64MD5PasswordDatabaseAuthenticationManager(getParent(Broker.class, parents),
                                                                   attributes
                );
        return ResolvedObject.newInstance(authenticationManager);
    }
}
