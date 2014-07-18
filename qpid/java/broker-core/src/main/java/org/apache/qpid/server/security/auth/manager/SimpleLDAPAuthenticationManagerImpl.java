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
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.Hashtable;
import java.util.Map;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.log4j.Logger;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.auth.manager.ldap.AbstractLDAPSSLSocketFactory;
import org.apache.qpid.server.security.auth.manager.ldap.LDAPSSLSocketFactoryGenerator;
import org.apache.qpid.server.security.auth.sasl.plain.PlainPasswordCallback;
import org.apache.qpid.server.security.auth.sasl.plain.PlainSaslServer;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.server.util.StringUtil;
import org.apache.qpid.ssl.SSLContextFactory;

public class SimpleLDAPAuthenticationManagerImpl extends AbstractAuthenticationManager<SimpleLDAPAuthenticationManagerImpl>
        implements SimpleLDAPAuthenticationManager<SimpleLDAPAuthenticationManagerImpl>
{
    private static final Logger _logger = Logger.getLogger(SimpleLDAPAuthenticationManagerImpl.class);

    /**
     * Environment key to instruct {@link InitialDirContext} to override the socket factory.
     */
    private static final String JAVA_NAMING_LDAP_FACTORY_SOCKET = "java.naming.ldap.factory.socket";

    @ManagedAttributeField
    private String _providerUrl;
    @ManagedAttributeField
    private String _providerAuthUrl;
    @ManagedAttributeField
    private String _searchContext;
    @ManagedAttributeField
    private String _searchFilter;
    @ManagedAttributeField
    private String _ldapContextFactory;


    /**
     * Trust store - typically used when the Directory has been secured with a certificate signed by a
     * private CA (or self-signed certificate).
     */
    @ManagedAttributeField
    private TrustStore _trustStore;

    @ManagedAttributeField
    private boolean _bindWithoutSearch;

    /**
     * Dynamically created SSL Socket Factory implementation used in the case where user has specified a trust store.
     */
    private Class<? extends SocketFactory> _sslSocketFactoryOverrideClass;

    @ManagedObjectFactoryConstructor
    protected SimpleLDAPAuthenticationManagerImpl(final Map<String, Object> attributes, final Broker broker)
    {
        super(attributes, broker);
    }


    @Override
    public void initialise()
    {
        _sslSocketFactoryOverrideClass = createSslSocketFactoryOverrideClass();

        validateInitialDirContext();
    }

    @Override
    public String getProviderUrl()
    {
        return _providerUrl;
    }

    @Override
    public String getProviderAuthUrl()
    {
        return _providerAuthUrl;
    }

    @Override
    public String getSearchContext()
    {
        return _searchContext;
    }

    @Override
    public String getSearchFilter()
    {
        return _searchFilter;
    }

    @Override
    public String getLdapContextFactory()
    {
        return _ldapContextFactory;
    }

    @Override
    public TrustStore getTrustStore()
    {
        return _trustStore;
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
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Authenticated as " + authorizationID);
                }

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

        String providerAuthUrl = _providerAuthUrl == null ? _providerUrl : _providerAuthUrl;
        Hashtable<String, Object> env = createInitialDirContextEnvironment(providerAuthUrl);

        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, name);
        env.put(Context.SECURITY_CREDENTIALS, password);

        InitialDirContext ctx = null;
        try
        {
            ctx = createInitialDirContext(env);

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
                closeSafely(ctx);
            }
        }
    }

    private Hashtable<String, Object> createInitialDirContextEnvironment(String providerUrl)
    {
        Hashtable<String,Object> env = new Hashtable<String,Object>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, _ldapContextFactory);
        env.put(Context.PROVIDER_URL, providerUrl);
        return env;
    }

    private InitialDirContext createInitialDirContext(Hashtable<String, Object> env) throws NamingException
    {
        ClassLoader existingContextClassLoader = null;

        boolean isLdaps = String.valueOf(env.get(Context.PROVIDER_URL)).trim().toLowerCase().startsWith("ldaps:");

        boolean revertContentClassLoader = false;
        try
        {
            if (isLdaps && _sslSocketFactoryOverrideClass != null)
            {
                existingContextClassLoader = Thread.currentThread().getContextClassLoader();
                env.put(JAVA_NAMING_LDAP_FACTORY_SOCKET, _sslSocketFactoryOverrideClass.getName());
                Thread.currentThread().setContextClassLoader(_sslSocketFactoryOverrideClass.getClassLoader());
                revertContentClassLoader = true;
            }
            return new InitialDirContext(env);
        }
        finally
        {
            if (revertContentClassLoader)
            {
                Thread.currentThread().setContextClassLoader(existingContextClassLoader);
            }
        }
    }

    /**
     * If a trust store has been specified, create a {@link SSLContextFactory} class that is
     * associated with the {@link SSLContext} generated from that trust store.
     *
     * @return generated socket factory class
     */
    private Class<? extends SocketFactory> createSslSocketFactoryOverrideClass()
    {
        if (_trustStore != null)
        {
            String clazzName = new StringUtil().createUniqueJavaName(getName());
            SSLContext sslContext = null;
            try
            {
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, _trustStore.getTrustManagers(), null);
            }
            catch (NoSuchAlgorithmException e)
            {
                _logger.error("Exception creating SSLContext", e);
                throw new ServerScopedRuntimeException("Error creating SSLContext for trust store : " + _trustStore.getName() , e);
            }
            catch (KeyManagementException e)
            {
                _logger.error("Exception creating SSLContext", e);
                throw new ServerScopedRuntimeException("Error creating SSLContext for trust store : " + _trustStore.getName() , e);
            }
            catch (GeneralSecurityException e)
            {
                _logger.error("Exception creating SSLContext", e);
                throw new ServerScopedRuntimeException("Error creating SSLContext for trust store : " + _trustStore.getName() , e);
            }

            Class<? extends AbstractLDAPSSLSocketFactory> clazz = LDAPSSLSocketFactoryGenerator.createSubClass(clazzName, sslContext.getSocketFactory());
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Connection to Directory will use custom SSL socket factory : " +  clazz);
            }
            return clazz;
        }

        return null;
    }

    private void validateInitialDirContext()
    {
        Hashtable<String,Object> env = createInitialDirContextEnvironment(_providerUrl);
        env.put(Context.SECURITY_AUTHENTICATION, "none");

        InitialDirContext ctx = null;
        try
        {
            ctx = createInitialDirContext(env);
        }
        catch (NamingException e)
        {
            throw new ServerScopedRuntimeException("Unable to establish anonymous connection to the ldap server at " + _providerUrl, e);
        }
        finally
        {
            closeSafely(ctx);
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
        if(!isBindWithoutSearch())
        {
            Hashtable<String, Object> env = createInitialDirContextEnvironment(_providerUrl);

            env.put(Context.SECURITY_AUTHENTICATION, "none");
            InitialDirContext ctx = createInitialDirContext(env);

            try
            {
                SearchControls searchControls = new SearchControls();
                searchControls.setReturningAttributes(new String[]{});
                searchControls.setCountLimit(1l);
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
                NamingEnumeration<?> namingEnum = null;
                String name = null;

                namingEnum = ctx.search(_searchContext, _searchFilter, new String[]{id}, searchControls);
                if (namingEnum.hasMore())
                {
                    SearchResult result = (SearchResult) namingEnum.next();
                    name = result.getNameInNamespace();
                }
                return name;
            }
            finally
            {
                closeSafely(ctx);
            }
        }
        else
        {
            return id;
        }

    }

    @Override
    public boolean isBindWithoutSearch()
    {
        return _bindWithoutSearch;
    }

    private void closeSafely(InitialDirContext ctx)
    {
        try
        {
            if (ctx != null)
            {
                ctx.close();
                ctx = null;
            }
        }
        catch (Exception e)
        {
            _logger.warn("Exception closing InitialDirContext", e);
        }
    }

}
