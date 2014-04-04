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
import java.lang.reflect.Type;
import java.security.AccessControlException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import javax.net.ssl.X509TrustManager;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.*;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.auth.manager.SimpleLDAPAuthenticationManagerFactory;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.transport.network.security.ssl.QpidMultipleTrustManager;
import org.apache.qpid.transport.network.security.ssl.QpidPeersOnlyTrustManager;
import org.apache.qpid.transport.network.security.ssl.SSLUtil;

@ManagedObject( category = false )
public class FileTrustStore extends AbstractKeyStoreAdapter<FileTrustStore> implements TrustStore<FileTrustStore>
{
    @SuppressWarnings("serial")
    public static final Map<String, Type> ATTRIBUTE_TYPES = Collections.unmodifiableMap(new HashMap<String, Type>(){{
        put(NAME, String.class);
        put(PATH, String.class);
        put(PASSWORD, String.class);
        put(TRUST_STORE_TYPE, String.class);
        put(PEERS_ONLY, Boolean.class);
        put(TRUST_MANAGER_FACTORY_ALGORITHM, String.class);
    }});

    @SuppressWarnings("serial")
    public static final Map<String, Object> DEFAULTS = Collections.unmodifiableMap(new HashMap<String, Object>(){{
        put(TrustStore.TRUST_STORE_TYPE, DEFAULT_KEYSTORE_TYPE);
        put(TrustStore.PEERS_ONLY, Boolean.FALSE);
        put(TrustStore.TRUST_MANAGER_FACTORY_ALGORITHM, TrustManagerFactory.getDefaultAlgorithm());
    }});

    @ManagedAttributeField
    private String _trustStoreType;
    @ManagedAttributeField
    private String _trustManagerFactoryAlgorithm;
    @ManagedAttributeField
    private String _path;
    @ManagedAttributeField
    private boolean _peersOnly;


    private Broker<?> _broker;

    public FileTrustStore(UUID id, Broker<?> broker, Map<String, Object> attributes)
    {
        super(id, broker, DEFAULTS, attributes);
        _broker = broker;

        validateTrustStoreAttributes(_trustStoreType, _path, getPassword(), _trustManagerFactoryAlgorithm);
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return getAttributeNames(TrustStore.class);
    }

    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        if(desiredState == State.DELETED)
        {
            // verify that it is not in use
            String storeName = getName();

            Collection<Port<?>> ports = new ArrayList<Port<?>>(_broker.getPorts());
            for (Port port : ports)
            {
                Collection<TrustStore> trustStores = port.getTrustStores();
                for(TrustStore store : trustStores)
                {
                    if (storeName.equals(store.getAttribute(TrustStore.NAME)))
                    {
                        throw new IntegrityViolationException("Trust store '" + storeName + "' can't be deleted as it is in use by a port: " + port.getName());
                    }
                }
            }

            Collection<AuthenticationProvider> authenticationProviders = new ArrayList<AuthenticationProvider>(_broker.getAuthenticationProviders());
            for (AuthenticationProvider authProvider : authenticationProviders)
            {
                Object attributeType = authProvider.getAttribute(AuthenticationProvider.TYPE);
                Object attributeValue = authProvider.getAttribute(SimpleLDAPAuthenticationManagerFactory.ATTRIBUTE_TRUST_STORE);
                if (SimpleLDAPAuthenticationManagerFactory.PROVIDER_TYPE.equals(attributeType)
                    && storeName.equals(attributeValue))
                {
                    throw new IntegrityViolationException("Trust store '" + storeName + "' can't be deleted as it is in use by an authentication manager: " + authProvider.getName());
                }
            }

            return true;
        }
        return false;
    }

    @Override
    protected void authoriseSetDesiredState(State currentState, State desiredState) throws AccessControlException
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
    protected void authoriseSetAttribute(String name, Object expected, Object desired) throws AccessControlException
    {
        authoriseSetAttribute();
    }

    @Override
    protected void authoriseSetAttributes(Map<String, Object> attributes) throws AccessControlException
    {
        authoriseSetAttribute();
    }

    private void authoriseSetAttribute()
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), TrustStore.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting key store attributes is denied");
        }
    }

    @Override
    protected void changeAttributes(Map<String, Object> attributes)
    {
        Map<String, Object> changedValues = MapValueConverter.convert(attributes, ATTRIBUTE_TYPES);
        if(changedValues.containsKey(TrustStore.NAME))
        {
            String newName = (String) changedValues.get(TrustStore.NAME);
            if(!getName().equals(newName))
            {
                throw new IllegalConfigurationException("Changing the trust store name is not allowed");
            }
        }

        Map<String, Object> merged = generateEffectiveAttributes(changedValues);

        String trustStorePath = (String)merged.get(TrustStore.PATH);
        String trustStorePassword = (String) merged.get(TrustStore.PASSWORD);
        String trustStoreType = (String)merged.get(TrustStore.TRUST_STORE_TYPE);
        String trustManagerFactoryAlgorithm = (String)merged.get(TrustStore.TRUST_MANAGER_FACTORY_ALGORITHM);

        validateTrustStoreAttributes(trustStoreType, trustStorePath,
                                     trustStorePassword, trustManagerFactoryAlgorithm);

        super.changeAttributes(changedValues);
    }

    private void validateTrustStoreAttributes(String type, String trustStorePath,
                                              String password, String trustManagerFactoryAlgorithm)
    {
        try
        {
            SSLUtil.getInitializedKeyStore(trustStorePath, password, type);
        }
        catch (Exception e)
        {
            throw new IllegalConfigurationException("Cannot instantiate trust store at " + trustStorePath, e);
        }

        try
        {
            TrustManagerFactory.getInstance(trustManagerFactoryAlgorithm);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalConfigurationException("Unknown trustManagerFactoryAlgorithm: " + trustManagerFactoryAlgorithm);
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
