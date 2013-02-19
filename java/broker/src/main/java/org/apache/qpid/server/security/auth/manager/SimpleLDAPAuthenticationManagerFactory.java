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

import java.util.Map;

import org.apache.qpid.server.plugin.AuthenticationManagerFactory;

public class SimpleLDAPAuthenticationManagerFactory implements AuthenticationManagerFactory
{
    private static final String DEFAULT_LDAP_CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";

    public static final String PROVIDER_TYPE = SimpleLDAPAuthenticationManager.class.getSimpleName();

    public static final String ATTRIBUTE_LDAP_CONTEXT_FACTORY = "ldapContextFactory";
    public static final String ATTRIBUTE_SEARCH_FILTER = "searchFilter";
    public static final String ATTRIBUTE_SEARCH_CONTEXT = "searchContext";
    public static final String ATTRIBUTE_PROVIDER_AUTH_URL = "providerAuthUrl";
    public static final String ATTRIBUTE_PROVIDER_SEARCH_URL = "providerSearchUrl";
    public static final String ATTRIBUTE_PROVIDER_URL = "providerUrl";

    @Override
    public AuthenticationManager createInstance(Map<String, Object> attributes)
    {
        if (attributes == null || !PROVIDER_TYPE.equals(attributes.get(ATTRIBUTE_TYPE)))
        {
            return null;
        }
        String providerUrl = (String) attributes.get(ATTRIBUTE_PROVIDER_URL);
        String providerSearchUrl = (String) attributes.get(ATTRIBUTE_PROVIDER_SEARCH_URL);
        if (providerSearchUrl == null)
        {
            providerSearchUrl = providerUrl;
        }
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

        return new SimpleLDAPAuthenticationManager(providerSearchUrl, providerAuthUrl, searchContext, searchFilter,
                ldapContextFactory);
    }

}
