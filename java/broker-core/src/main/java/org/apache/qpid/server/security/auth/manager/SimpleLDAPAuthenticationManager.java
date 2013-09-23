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
import java.util.Hashtable;

import javax.naming.AuthenticationException;
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
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import org.apache.log4j.Logger;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.auth.sasl.plain.PlainPasswordCallback;
import org.apache.qpid.server.security.auth.sasl.plain.PlainSaslServer;

public class SimpleLDAPAuthenticationManager implements AuthenticationManager
{
    private static final Logger _logger = Logger.getLogger(SimpleLDAPAuthenticationManager.class);

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
        return PlainSaslServer.MECHANISM;
    }

    @Override
    public SaslServer createSaslServer(String mechanism, String localFQDN, Principal externalPrincipal) throws SaslException
    {
        if(PlainSaslServer.MECHANISM.equals(mechanism))
        {
            return new PlainSaslServer(new SimpleLDAPPlainCallbackHandler());
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
            AuthenticationResult result = doLDAPNameAuthentication(getNameFromId(username), password);
            if(result.getStatus() == AuthenticationStatus.SUCCESS)
            {
                //Return a result based on the supplied username rather than the search name
                return new AuthenticationResult(new UsernamePrincipal(username));
            }
            else
            {
                return result;
            }
        }
        catch (NamingException e)
        {
            return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR, e);
        }
    }

    private AuthenticationResult doLDAPNameAuthentication(String name, String password)
    {
        if(name == null)
        {
            //The search didn't return anything, class as not-authenticated before it NPEs below
            return new AuthenticationResult(AuthenticationStatus.CONTINUE);
        }

        Hashtable<Object,Object> env = new Hashtable<Object,Object>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, _ldapContextFactory);
        env.put(Context.PROVIDER_URL, _providerAuthURL);

        env.put(Context.SECURITY_AUTHENTICATION, "simple");

        env.put(Context.SECURITY_PRINCIPAL, name);
        env.put(Context.SECURITY_CREDENTIALS, password);

        DirContext ctx = null;
        try
        {
            ctx = new InitialDirContext(env);

            //Authentication succeeded
            return new AuthenticationResult(new UsernamePrincipal(name));
        }
        catch(AuthenticationException ae)
        {
            //Authentication failed
            return new AuthenticationResult(AuthenticationStatus.CONTINUE);
        }
        catch (NamingException e)
        {
            //Some other failure
            return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR, e);
        }
        finally
        {
            if(ctx != null)
            {
                try
                {
                    ctx.close();
                }
                catch (Exception e)
                {
                    _logger.warn("Exception closing InitialDirContext", e);
                }
            }
        }
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

    private class SimpleLDAPPlainCallbackHandler implements CallbackHandler
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
                        _logger.warn("SASL Authentication Exception", e);
                    }
                    if(password != null)
                    {
                        authenticated = doLDAPNameAuthentication(name, password);
                    }
                }
                else if (callback instanceof PlainPasswordCallback)
                {
                    password = ((PlainPasswordCallback)callback).getPlainPassword();
                    if(name != null)
                    {
                        authenticated = doLDAPNameAuthentication(name, password);
                        if(authenticated.getStatus()== AuthenticationResult.AuthenticationStatus.SUCCESS)
                        {
                            ((PlainPasswordCallback)callback).setAuthenticated(true);
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
            try
            {
                ctx.close();
            }
            catch (Exception e)
            {
                _logger.warn("Exception closing InitialDirContext", e);
            }
        }

    }

    @Override
    public void onCreate()
    {
        // nothing to do, no external resource is required
    }

    @Override
    public void onDelete()
    {
        // nothing to do, no external resource is used
    }
}
