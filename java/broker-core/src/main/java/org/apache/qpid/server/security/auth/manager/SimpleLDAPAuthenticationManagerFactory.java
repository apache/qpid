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

public class SimpleLDAPAuthenticationManagerFactory implements AuthenticationManagerFactory
{
    public static final String RESOURCE_BUNDLE = "org.apache.qpid.server.security.auth.manager.SimpleLDAPAuthenticationProviderAttributeDescriptions";
    private static final String DEFAULT_LDAP_CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";

    public static final String PROVIDER_TYPE = "SimpleLDAP";

    public static final String ATTRIBUTE_LDAP_CONTEXT_FACTORY = "ldapContextFactory";
    public static final String ATTRIBUTE_SEARCH_FILTER = "searchFilter";
    public static final String ATTRIBUTE_SEARCH_CONTEXT = "searchContext";
    public static final String ATTRIBUTE_PROVIDER_AUTH_URL = "providerAuthUrl";
    public static final String ATTRIBUTE_PROVIDER_URL = "providerUrl";

    public static final Collection<String> ATTRIBUTES = Collections.<String> unmodifiableList(Arrays.asList(
            ATTRIBUTE_TYPE,
            ATTRIBUTE_PROVIDER_URL,
            ATTRIBUTE_SEARCH_CONTEXT,
            ATTRIBUTE_SEARCH_FILTER,
            ATTRIBUTE_PROVIDER_AUTH_URL,
            ATTRIBUTE_LDAP_CONTEXT_FACTORY
            ));

    @Override
    public AuthenticationManager createInstance(Map<String, Object> attributes)
    {
        if (attributes == null || !PROVIDER_TYPE.equals(attributes.get(ATTRIBUTE_TYPE)))
        {
            return null;
        }
        String providerUrl = (String) attributes.get(ATTRIBUTE_PROVIDER_URL);
        String providerAuthUrl = (String) attributes.get(ATTRIBUTE_PROVIDER_AUTH_URL);
        if (providerAuthUrl == null)
        {
            providerAuthUrl = providerUrl;
        }
        String searchContext = (String) attributes.get(ATTRIBUTE_SEARCH_CONTEXT);
        String searchFilter = (String) attributes.get(ATTRIBUTE_SEARCH_FILTER);
        String ldapContextFactory = (String) attributes.get(ATTRIBUTE_LDAP_CONTEXT_FACTORY);
        if (ldapContextFactory == null)
        {
            ldapContextFactory = DEFAULT_LDAP_CONTEXT_FACTORY;
        }

        return new SimpleLDAPAuthenticationManager(providerUrl, providerAuthUrl, searchContext, searchFilter,
                ldapContextFactory);
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
