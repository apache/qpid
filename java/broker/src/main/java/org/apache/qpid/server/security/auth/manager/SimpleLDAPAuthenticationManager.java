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
 */

package org.apache.qpid.server.security.auth.manager;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.plugins.ConfigurationPlugin;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.sasl.UsernamePrincipal;
import org.apache.qpid.server.security.auth.sasl.plain.PlainPasswordCallback;

public class SimpleLDAPAuthenticationManager implements AuthenticationManager
{
    private static final Logger _logger = Logger.getLogger(SimpleLDAPAuthenticationManager.class);

    private static final String PLAIN_MECHANISM = "PLAIN";
    private static final String DEFAULT_LDAP_CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    private String _providerSearchURL;
    private String _searchContext;
    private String _searchFilter;
    private String _providerAuthURL;
    private String _ldapContextFactory;

    public static class SimpleLDAPAuthenticationManagerConfiguration extends ConfigurationPlugin
    {

        public static final ConfigurationPluginFactory FACTORY =
                new ConfigurationPluginFactory()
                {
                    public List<String> getParentPaths()
                    {
                        return Arrays.asList("security.simple-ldap-auth-manager");
                    }

                    public ConfigurationPlugin newInstance(final String path, final Configuration config) throws ConfigurationException
                    {
                        final ConfigurationPlugin instance = new SimpleLDAPAuthenticationManagerConfiguration();

                        instance.setConfiguration(path, config);
                        return instance;
                    }
                };

        private static final String PROVIDER_URL = "provider-url";
        private static final String PROVIDER_SEARCH_URL = "provider-search-url";
        private static final String PROVIDER_AUTH_URL = "provider-auth-url";
        private static final String SEARCH_CONTEXT = "search-context";
        private static final String SEARCH_FILTER = "search-filter";
        private static final String LDAP_CONTEXT_FACTORY = "ldap-context-factory";

        public String[] getElementsProcessed()
        {
            return new String[] {PROVIDER_URL, PROVIDER_SEARCH_URL, PROVIDER_AUTH_URL, SEARCH_CONTEXT, SEARCH_FILTER,
                                 LDAP_CONTEXT_FACTORY};
        }

        public void validateConfiguration() throws ConfigurationException
        {
        }

        public String getLDAPContextFactory()
        {
            return getConfig().getString(LDAP_CONTEXT_FACTORY, DEFAULT_LDAP_CONTEXT_FACTORY);
        }


        public String getProviderURL()
        {
            return getConfig().getString(PROVIDER_URL);
        }

        public String getProviderSearchURL()
        {
            return getConfig().getString(PROVIDER_SEARCH_URL, getProviderURL());
        }

        public String getSearchContext()
        {
            return getConfig().getString(SEARCH_CONTEXT);
        }

        public String getSearchFilter()
        {
            return getConfig().getString(SEARCH_FILTER);
        }

        public String getProviderAuthURL()
        {
            return getConfig().getString(PROVIDER_AUTH_URL, getProviderURL());
        }
    }


    public static final AuthenticationManagerPluginFactory<SimpleLDAPAuthenticationManager> FACTORY = new AuthenticationManagerPluginFactory<SimpleLDAPAuthenticationManager>()
    {
        public SimpleLDAPAuthenticationManager newInstance(final ConfigurationPlugin config) throws ConfigurationException
        {
            SimpleLDAPAuthenticationManagerConfiguration configuration =
                    config == null
                            ? null
                            : (SimpleLDAPAuthenticationManagerConfiguration) config.getConfiguration(SimpleLDAPAuthenticationManagerConfiguration.class.getName());

            // If there is no configuration for this plugin then don't load it.
            if (configuration == null)
            {
                _logger.info("No authentication-manager configuration found for SimpleLDAPAuthenticationManager");
                return null;
            }
            SimpleLDAPAuthenticationManager simpleLDAPAuthenticationManager = new SimpleLDAPAuthenticationManager();
            simpleLDAPAuthenticationManager.configure(configuration);
            return simpleLDAPAuthenticationManager;
        }

        public Class<SimpleLDAPAuthenticationManager> getPluginClass()
        {
            return SimpleLDAPAuthenticationManager.class;
        }

        public String getPluginName()
        {
            return SimpleLDAPAuthenticationManager.class.getName();
        }
    };


    private SimpleLDAPAuthenticationManager()
    {
    }

    @Override
    public void initialise()
    {

    }

    @Override
    public String getMechanisms()
    {
        return PLAIN_MECHANISM;
    }

    @Override
    public SaslServer createSaslServer(String mechanism, String localFQDN) throws SaslException
    {
        if(PLAIN_MECHANISM.equals(mechanism))
        {
            return Sasl.createSaslServer(PLAIN_MECHANISM, "AMQP", localFQDN,
                                     new HashMap<String, Object>(), new PlainCallbackHandler());

        }
        else
        {
            throw new SaslException("Unknown mechanism: " + mechanism);
        }
    }

    @Override
    public AuthenticationResult authenticate(SaslServer server, byte[] response)
    {
        try
        {
            // Process response from the client
            byte[] challenge = server.evaluateResponse(response != null ? response : new byte[0]);

            if (server.isComplete())
            {
                final Subject subject = new Subject();
                _logger.debug("Authenticated as " + server.getAuthorizationID());
                subject.getPrincipals().add(new UsernamePrincipal(server.getAuthorizationID()));
                return new AuthenticationResult(subject);
            }
            else
            {
                return new AuthenticationResult(challenge, AuthenticationResult.AuthenticationStatus.CONTINUE);
            }
        }
        catch (SaslException e)
        {
            return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR, e);
        }
    }

    @Override
    public AuthenticationResult authenticate(String username, String password)
    {

        try
        {
            return doLDAPNameAuthentication(getNameFromId(username), password);
        }
        catch (NamingException e)
        {

            return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR, e);

        }
    }

    private AuthenticationResult doLDAPNameAuthentication(String username, String password) throws NamingException
    {
        Hashtable<Object,Object> env = new Hashtable<Object,Object>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, _ldapContextFactory);
        env.put(Context.PROVIDER_URL, _providerAuthURL);

        env.put(Context.SECURITY_AUTHENTICATION, "simple");

        env.put(Context.SECURITY_PRINCIPAL, username);
        env.put(Context.SECURITY_CREDENTIALS, password);
        DirContext ctx = new InitialDirContext(env);
        ctx.close();
        final Subject subject = new Subject();
        subject.getPrincipals().add(new UsernamePrincipal(username));
        return new AuthenticationResult(subject);
    }

    @Override
    public CallbackHandler getHandler(String mechanism)
    {
        if(PLAIN_MECHANISM.equals(mechanism))
        {
            return new PlainCallbackHandler();
        }
        else
        {
            return null;
        }
    }

    @Override
    public void close()
    {
    }

    @Override
    public void configure(ConfigurationPlugin config) throws ConfigurationException
    {
        SimpleLDAPAuthenticationManagerConfiguration ldapConfig = (SimpleLDAPAuthenticationManagerConfiguration) config;

        _ldapContextFactory = ldapConfig.getLDAPContextFactory();
        _providerSearchURL = ldapConfig.getProviderSearchURL();
        _providerAuthURL = ldapConfig.getProviderAuthURL();
        _searchContext = ldapConfig.getSearchContext();
        _searchFilter = ldapConfig.getSearchFilter();

        Hashtable<String,Object> env = new Hashtable<String, Object>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, _ldapContextFactory);
        env.put(Context.PROVIDER_URL, _providerSearchURL);
        env.put(Context.SECURITY_AUTHENTICATION, "none");

        try
        {
            new InitialDirContext(env);
        }
        catch (NamingException e)
        {
            throw new ConfigurationException("Unable to establish anonymous connection to the ldap server at " + _providerSearchURL, e);
        }
    }

    private class PlainCallbackHandler implements CallbackHandler
    {

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException
        {
            String name = null;
            String password = null;
            AuthenticationResult authenticated = null;
            for(Callback callback : callbacks)
            {
                if (callback instanceof NameCallback)
                {
                    String id = ((NameCallback) callback).getDefaultName();
                    try
                    {
                        name = getNameFromId(id);
                    }
                    catch (NamingException e)
                    {
                        _logger.info("SASL Authentication Error", e);
                    }
                    if(password != null)
                    {
                        try
                        {
                            authenticated = doLDAPNameAuthentication(name, password);

                        }
                        catch (NamingException e)
                        {
                            authenticated = new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR, e);
                        }
                    }
                }
                else if (callback instanceof PlainPasswordCallback)
                {
                    password = ((PlainPasswordCallback)callback).getPlainPassword();
                    if(name != null)
                    {
                        try
                        {
                            authenticated = doLDAPNameAuthentication(name, password);
                            if(authenticated.getStatus()== AuthenticationResult.AuthenticationStatus.SUCCESS)
                            {
                                ((PlainPasswordCallback)callback).setAuthenticated(true);
                            }
                        }
                        catch (NamingException e)
                        {
                            authenticated = new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR, e);
                        }
                    }
                }
                else if (callback instanceof AuthorizeCallback)
                {
                    ((AuthorizeCallback) callback).setAuthorized(authenticated != null && authenticated.getStatus() == AuthenticationResult.AuthenticationStatus.SUCCESS);
                }
                else
                {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        }
    }

    private String getNameFromId(String id) throws NamingException
    {
        Hashtable<Object,Object> env = new Hashtable<Object,Object>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, _ldapContextFactory);
        env.put(Context.PROVIDER_URL, _providerSearchURL);


        env.put(Context.SECURITY_AUTHENTICATION, "none");
        DirContext ctx = null;

        ctx = new InitialDirContext(env);

        try
        {
            SearchControls searchControls = new SearchControls();
            searchControls.setReturningAttributes(new String[] {});
            searchControls.setCountLimit(1l);
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            NamingEnumeration<?> namingEnum = null;
            String name = null;

            namingEnum = ctx.search(_searchContext, _searchFilter, new String[] { id }, searchControls);
            if(namingEnum.hasMore())
            {
                SearchResult result = (SearchResult) namingEnum.next();
                name = result.getNameInNamespace();
            }
            return name;
        }
        finally
        {
            ctx.close();
        }

    }
}
