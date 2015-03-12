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
package org.apache.qpid.server.security;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.IntegrityViolationException;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.security.auth.manager.SimpleLDAPAuthenticationManager;
import org.apache.qpid.server.util.urlstreamhandler.data.Handler;
import org.apache.qpid.transport.network.security.ssl.QpidMultipleTrustManager;
import org.apache.qpid.transport.network.security.ssl.QpidPeersOnlyTrustManager;
import org.apache.qpid.transport.network.security.ssl.SSLUtil;

public class FileTrustStoreImpl extends AbstractConfiguredObject<FileTrustStoreImpl> implements FileTrustStore<FileTrustStoreImpl>
{

    @ManagedAttributeField
    private String _trustStoreType;
    @ManagedAttributeField
    private String _trustManagerFactoryAlgorithm;
    @ManagedAttributeField(afterSet = "postSetStoreUrl")
    private String _storeUrl;
    private String _path;
    @ManagedAttributeField
    private boolean _peersOnly;
    @ManagedAttributeField
    private String _password;

    private final Broker<?> _broker;

    static
    {
        Handler.register();
    }

    @ManagedObjectFactoryConstructor
    public FileTrustStoreImpl(Map<String, Object> attributes, Broker<?> broker)
    {
        super(parentsMap(broker), attributes);
        _broker = broker;
    }

    @Override
    public void onValidate()
    {
        super.onValidate();
        validateTrustStore(this);
        if(!isDurable())
        {
            throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
        }
    }

    @StateTransition(currentState = {State.ACTIVE, State.ERRORED}, desiredState = State.DELETED)
    protected ListenableFuture<Void> doDelete()
    {
        // verify that it is not in use
        String storeName = getName();

        Collection<Port<?>> ports = new ArrayList<Port<?>>(_broker.getPorts());
        for (Port port : ports)
        {
            Collection<TrustStore> trustStores = port.getTrustStores();
            if(trustStores != null)
            {
                for (TrustStore store : trustStores)
                {
                    if(storeName.equals(store.getAttribute(TrustStore.NAME)))
                    {
                        throw new IntegrityViolationException("Trust store '"
                                + storeName
                                + "' can't be deleted as it is in use by a port: "
                                + port.getName());
                    }
                }
            }
        }

        Collection<AuthenticationProvider> authenticationProviders = new ArrayList<AuthenticationProvider>(_broker.getAuthenticationProviders());
        for (AuthenticationProvider authProvider : authenticationProviders)
        {
            if (authProvider instanceof SimpleLDAPAuthenticationManager)
            {
                SimpleLDAPAuthenticationManager simpleLdap = (SimpleLDAPAuthenticationManager) authProvider;
                if (simpleLdap.getTrustStore() == this)
                {
                    throw new IntegrityViolationException("Trust store '"
                            + storeName
                            + "' can't be deleted as it is in use by an authentication manager: "
                            + authProvider.getName());
                }
            }
        }
        deleted();
        setState(State.DELETED);
        return Futures.immediateFuture(null);
    }

    @StateTransition(currentState = {State.UNINITIALIZED, State.ERRORED}, desiredState = State.ACTIVE)
    protected ListenableFuture<Void> doActivate()
    {
        setState(State.ACTIVE);
        return Futures.immediateFuture(null);
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);

        FileTrustStore updated = (FileTrustStore) proxyForValidation;
        if (changedAttributes.contains(TrustStore.DESIRED_STATE) && updated.getDesiredState() == State.DELETED)
        {
            return;
        }
        if(changedAttributes.contains(TrustStore.NAME) && !getName().equals(updated.getName()))
        {
            throw new IllegalConfigurationException("Changing the trust store name is not allowed");
        }
        if(changedAttributes.contains(DURABLE) && !proxyForValidation.isDurable())
        {
            throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
        }
        validateTrustStore(updated);
    }


    private static void validateTrustStore(FileTrustStore trustStore)
    {
        try
        {
            URL trustStoreUrl = getUrlFromString(trustStore.getStoreUrl());
            SSLUtil.getInitializedKeyStore(trustStoreUrl, trustStore.getPassword(), trustStore.getTrustStoreType());
        }
        catch (Exception e)
        {
            final String message;
            if (e instanceof IOException && e.getCause() != null && e.getCause() instanceof UnrecoverableKeyException)
            {
                message = "Check trust store password. Cannot instantiate trust store from '" + trustStore.getStoreUrl() + "'.";
            }
            else
            {
                message = "Cannot instantiate trust store from '" + trustStore.getStoreUrl() + "'.";
            }

            throw new IllegalConfigurationException(message, e);
        }

        try
        {
            TrustManagerFactory.getInstance(trustStore.getTrustManagerFactoryAlgorithm());
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalConfigurationException("Unknown trustManagerFactoryAlgorithm: " + trustStore.getTrustManagerFactoryAlgorithm());
        }
    }

    @Override
    public String getStoreUrl()
    {
        return _storeUrl;
    }

    @Override
    public String getPath()
    {
        return _path;
    }

    @Override
    public String getTrustManagerFactoryAlgorithm()
    {
        return _trustManagerFactoryAlgorithm;
    }

    @Override
    public String getTrustStoreType()
    {
        return _trustStoreType;
    }

    @Override
    public boolean isPeersOnly()
    {
        return _peersOnly;
    }

    @Override
    public String getPassword()
    {
        return _password;
    }

    public void setPassword(String password)
    {
        _password = password;
    }
    public TrustManager[] getTrustManagers() throws GeneralSecurityException
    {
        String trustStorePassword = getPassword();
        String trustStoreType = _trustStoreType;
        String trustManagerFactoryAlgorithm = _trustManagerFactoryAlgorithm;

        try
        {
            URL trustStoreUrl = getUrlFromString(_storeUrl);

            KeyStore ts = SSLUtil.getInitializedKeyStore(trustStoreUrl, trustStorePassword, trustStoreType);
            final TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(trustManagerFactoryAlgorithm);
            tmf.init(ts);
            final Collection<TrustManager> trustManagersCol = new ArrayList<TrustManager>();
            final QpidMultipleTrustManager mulTrustManager = new QpidMultipleTrustManager();
            TrustManager[] delegateManagers = tmf.getTrustManagers();
            for (TrustManager tm : delegateManagers)
            {
                if (tm instanceof X509TrustManager)
                {
                    if (_peersOnly)
                    {
                        // truststore is supposed to trust only clients which peers certificates
                        // are directly in the store. CA signing will not be considered.
                        mulTrustManager.addTrustManager(new QpidPeersOnlyTrustManager(ts, (X509TrustManager) tm));
                    }
                    else
                    {
                        mulTrustManager.addTrustManager((X509TrustManager) tm);
                    }
                }
                else
                {
                    trustManagersCol.add(tm);
                }
            }
            if (! mulTrustManager.isEmpty())
            {
                trustManagersCol.add(mulTrustManager);
            }

            if (trustManagersCol.isEmpty())
            {
                return null;
            }
            else
            {
                return trustManagersCol.toArray(new TrustManager[trustManagersCol.size()]);
            }
        }
        catch (IOException e)
        {
            throw new GeneralSecurityException(e);
        }
    }

    private static URL getUrlFromString(String urlString) throws MalformedURLException
    {
        URL url;
        try
        {
            url = new URL(urlString);
        }
        catch (MalformedURLException e)
        {
            File file = new File(urlString);
            url = file.toURI().toURL();

        }
        return url;
    }

    @SuppressWarnings(value = "unused")
    private void postSetStoreUrl()
    {
        if (_storeUrl != null && !_storeUrl.startsWith("data:"))
        {
            _path = _storeUrl;
        }
        else
        {
            _path = null;
        }
    }
}
