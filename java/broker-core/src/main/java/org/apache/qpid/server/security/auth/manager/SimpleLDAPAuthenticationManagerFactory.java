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

import org.apache.qpid.server.model.AbstractConfiguredObjectTypeFactory;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.util.ResourceBundleLoader;

public class SimpleLDAPAuthenticationManagerFactory
        extends AbstractConfiguredObjectTypeFactory<SimpleLDAPAuthenticationManagerImpl>
        implements AuthenticationManagerFactory<SimpleLDAPAuthenticationManagerImpl>
{
    public static final String RESOURCE_BUNDLE = "org.apache.qpid.server.security.auth.manager.SimpleLDAPAuthenticationProviderAttributeDescriptions";

    public static final String PROVIDER_TYPE = "SimpleLDAP";

    public static final String ATTRIBUTE_NAME = "name";
    public static final String ATTRIBUTE_LDAP_CONTEXT_FACTORY = "ldapContextFactory";
    public static final String ATTRIBUTE_SEARCH_FILTER = "searchFilter";
    public static final String ATTRIBUTE_SEARCH_CONTEXT = "searchContext";
    public static final String ATTRIBUTE_TRUST_STORE = "trustStore";
    public static final String ATTRIBUTE_PROVIDER_AUTH_URL = "providerAuthUrl";
    public static final String ATTRIBUTE_PROVIDER_URL = "providerUrl";

    public static final Collection<String> ATTRIBUTES = Collections.<String> unmodifiableList(Arrays.asList(
            AuthenticationProvider.TYPE,
            ATTRIBUTE_PROVIDER_URL,
            ATTRIBUTE_SEARCH_CONTEXT,
            ATTRIBUTE_SEARCH_FILTER,
            ATTRIBUTE_TRUST_STORE,
            ATTRIBUTE_PROVIDER_AUTH_URL,
            ATTRIBUTE_LDAP_CONTEXT_FACTORY
            ));

    public SimpleLDAPAuthenticationManagerFactory()
    {
        super(SimpleLDAPAuthenticationManagerImpl.class);
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return ATTRIBUTES;
    }

    @Override
    public Map<String, String> getAttributeDescriptions()
    {
        return ResourceBundleLoader.getResources(RESOURCE_BUNDLE);
    }

    @Override
    public SimpleLDAPAuthenticationManagerImpl createInstance(final Map<String, Object> attributes,
                                                          final ConfiguredObject<?>... parents)
    {
        return new SimpleLDAPAuthenticationManagerImpl(attributes, getParent(Broker.class, parents));
    }

}
