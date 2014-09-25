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

import java.io.IOException;
import java.security.AccessControlException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

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
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.auth.manager.SimpleLDAPAuthenticationManager;
import org.apache.qpid.transport.network.security.ssl.QpidMultipleTrustManager;
import org.apache.qpid.transport.network.security.ssl.QpidPeersOnlyTrustManager;
import org.apache.qpid.transport.network.security.ssl.SSLUtil;

public class FileTrustStoreImpl extends AbstractConfiguredObject<FileTrustStoreImpl> implements FileTrustStore<FileTrustStoreImpl>
{

    @ManagedAttributeField
    private String _trustStoreType;
    @ManagedAttributeField
    private String _trustManagerFactoryAlgorithm;
    @ManagedAttributeField
    private String _path;
    @ManagedAttributeField
    private boolean _peersOnly;
    @ManagedAttributeField
    private String _password;

    private Broker<?> _broker;

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
    protected void doDelete()
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
            if(authProvider.getAttributeNames().contains(SimpleLDAPAuthenticationManager.TRUST_STORE))
            {
                Object attributeType = authProvider.getAttribute(AuthenticationProvider.TYPE);
                Object attributeValue = authProvider.getAttribute(SimpleLDAPAuthenticationManager.TRUST_STORE);
                if (SimpleLDAPAuthenticationManager.PROVIDER_TYPE.equals(attributeType)
                        && storeName.equals(attributeValue))
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
    }

    @StateTransition(currentState = {State.UNINITIALIZED, State.ERRORED}, desiredState = State.ACTIVE)
    protected void doActivate()
    {
        setState(State.ACTIVE);
    }

    @Override
    protected void authoriseSetDesiredState(State desiredState) throws AccessControlException
    {
        if(desiredState == State.DELETED)
        {
            if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), TrustStore.class, Operation.DELETE))
            {
                throw new AccessControlException("Deletion of key store is denied");
            }
        }
    }

    @Override
    protected void authoriseSetAttributes(ConfiguredObject<?> modified, Set<String> attributes) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), TrustStore.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting key store attributes is denied");
        }
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
            SSLUtil.getInitializedKeyStore(trustStore.getPath(), trustStore.getPassword(), trustStore.getTrustStoreType());
        }
        catch (Exception e)
        {
            throw new IllegalConfigurationException("Cannot instantiate trust store at " + trustStore.getPath(), e);
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
        String trustStorePath = _path;
        String trustStorePassword = getPassword();
        String trustStoreType = _trustStoreType;
        String trustManagerFactoryAlgorithm = _trustManagerFactoryAlgorithm;

        try
        {
            KeyStore ts = SSLUtil.getInitializedKeyStore(trustStorePath, trustStorePassword, trustStoreType);
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
}
