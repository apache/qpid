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

import org.apache.commons.configuration.Configuration;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;

public class SimpleLDAPAuthenticationManagerFactory implements AuthenticationManagerFactory
{

    private static final String DEFAULT_LDAP_CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";

    @Override
    public AuthenticationManager createInstance(Configuration configuration)
    {

        final Configuration subset = configuration.subset("simple-ldap-auth-manager");
        if(subset.isEmpty())
        {
            return null;
        }

        String providerUrl = configuration.getString("simple-ldap-auth-manager.provider-url");
        String providerSearchUrl = configuration.getString("simple-ldap-auth-manager.provider-search-url", providerUrl);
        String providerAuthUrl = configuration.getString("simple-ldap-auth-manager.provider-auth-url", providerUrl);
        String searchContext = configuration.getString("simple-ldap-auth-manager.search-context");
        String searchFilter = configuration.getString("simple-ldap-auth-manager.search-filter");
        String ldapContextFactory = configuration.getString("simple-ldap-auth-manager.ldap-context-factory", DEFAULT_LDAP_CONTEXT_FACTORY);

        return new SimpleLDAPAuthenticationManager(providerSearchUrl, providerAuthUrl, searchContext, searchFilter, ldapContextFactory);
    }

}
