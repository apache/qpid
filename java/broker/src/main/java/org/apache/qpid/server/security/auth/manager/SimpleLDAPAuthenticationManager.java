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
import java.security.Principal;
import java.util.HashMap;
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import org.apache.log4j.Logger;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.auth.sasl.plain.PlainPasswordCallback;

public class SimpleLDAPAuthenticationManager implements AuthenticationManager
{
    private static final Logger _logger = Logger.getLogger(SimpleLDAPAuthenticationManager.class);

    private static final String PLAIN_MECHANISM = "PLAIN";
    private final String _providerSearchURL;
    private final String _providerAuthURL;
    private final String _searchContext;
    private final String _searchFilter;
    private final String _ldapContextFactory;

    SimpleLDAPAuthenticationManager(String providerSearchUrl, String providerAuthUrl, String searchContext, String searchFilter, String ldapContextFactory)
    {
        _providerSearchURL = providerSearchUrl;
        _providerAuthURL = providerAuthUrl;
        _searchContext = searchContext;
        _searchFilter = searchFilter;
        _ldapContextFactory = ldapContextFactory;
    }

    @Override
    public void initialise()
    {
        validateInitialDirContext();
    }

    @Override
    public String getMechanisms()
    {
        return PLAIN_MECHANISM;
    }

    @Override
    public SaslServer createSaslServer(String mechanism, String localFQDN, Principal externalPrincipal) throws SaslException
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
                String authorizationID = server.getAuthorizationID();
                _logger.debug("Authenticated as " + authorizationID);

                return new AuthenticationResult(new UsernamePrincipal(authorizationID));
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

        return new AuthenticationResult(new UsernamePrincipal(username));
    }

    @Override
    public void close()
    {
    }

    private void validateInitialDirContext()
    {
        Hashtable<String,Object> env = new Hashtable<String, Object>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, _ldapContextFactory);
        env.put(Context.PROVIDER_URL, _providerSearchURL);
        env.put(Context.SECURITY_AUTHENTICATION, "none");

        try
        {
            new InitialDirContext(env).close();
        }
        catch (NamingException e)
        {
            throw new RuntimeException("Unable to establish anonymous connection to the ldap server at " + _providerSearchURL, e);
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
