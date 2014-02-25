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
package org.apache.qpid.server.model.adapter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.security.AccessControlException;
import java.security.GeneralSecurityException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.security.auth.Subject;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.IntegrityViolationException;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.transport.network.security.ssl.QpidClientX509KeyManager;
import org.apache.qpid.transport.network.security.ssl.SSLUtil;

public class KeyStoreAdapter extends AbstractKeyStoreAdapter<KeyStoreAdapter> implements KeyStore<KeyStoreAdapter>
{
    @SuppressWarnings("serial")
    public static final Map<String, Type> ATTRIBUTE_TYPES = Collections.unmodifiableMap(new HashMap<String, Type>(){{
        put(NAME, String.class);
        put(PATH, String.class);
        put(PASSWORD, String.class);
        put(KEY_STORE_TYPE, String.class);
        put(CERTIFICATE_ALIAS, String.class);
        put(KEY_MANAGER_FACTORY_ALGORITHM, String.class);
    }});

    @SuppressWarnings("serial")
    public static final Map<String, Object> DEFAULTS = Collections.unmodifiableMap(new HashMap<String, Object>(){{
        put(KeyStore.KEY_STORE_TYPE, DEFAULT_KEYSTORE_TYPE);
        put(KeyStore.KEY_MANAGER_FACTORY_ALGORITHM, KeyManagerFactory.getDefaultAlgorithm());
    }});

    private Broker<?> _broker;

    public KeyStoreAdapter(UUID id, Broker<?> broker, Map<String, Object> attributes)
    {
        super(id, broker, DEFAULTS, MapValueConverter.convert(attributes, ATTRIBUTE_TYPES));
        _broker = broker;

        String keyStorePath = (String)getAttribute(KeyStore.PATH);
        String keyStorePassword = Subject.doAs(SecurityManager.SYSTEM, new PrivilegedAction<String>()
        {
            @Override
            public String run()
            {
                return getPassword();
            }
        });
        String keyStoreType = (String)getAttribute(KeyStore.KEY_STORE_TYPE);
        String keyManagerFactoryAlgorithm = (String)getAttribute(KeyStore.KEY_MANAGER_FACTORY_ALGORITHM);
        String certAlias = (String)getAttribute(KeyStore.CERTIFICATE_ALIAS);

        validateKeyStoreAttributes(keyStoreType, keyStorePath, keyStorePassword,
                                   certAlias, keyManagerFactoryAlgorithm);
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return getAttributeNames(KeyStore.class);
    }

    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        if(desiredState == State.DELETED)
        {
            // verify that it is not in use
            String storeName = getName();

            Collection<Port> ports = new ArrayList<Port>(_broker.getPorts());
            for (Port port : ports)
            {
                if (storeName.equals(port.getAttribute(Port.KEY_STORE)))
                {
                    throw new IntegrityViolationException("Key store '" + storeName + "' can't be deleted as it is in use by a port:" + port.getName());
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
            if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), KeyStore.class, Operation.DELETE))
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
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), KeyStore.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting key store attributes is denied");
        }
    }

    @Override
    protected void changeAttributes(Map<String, Object> attributes)
    {
        Map<String, Object> changedValues = MapValueConverter.convert(attributes, ATTRIBUTE_TYPES);
        if(changedValues.containsKey(KeyStore.NAME))
        {
            String newName = (String) changedValues.get(KeyStore.NAME);
            if(!getName().equals(newName))
            {
                throw new IllegalConfigurationException("Changing the key store name is not allowed");
            }
        }

        Map<String, Object> merged = generateEffectiveAttributes(changedValues);

        String keyStorePath = (String)merged.get(KeyStore.PATH);
        String keyStorePassword = (String) merged.get(KeyStore.PASSWORD);
        String keyStoreType = (String)merged.get(KeyStore.KEY_STORE_TYPE);
        String keyManagerFactoryAlgorithm = (String)merged.get(KeyStore.KEY_MANAGER_FACTORY_ALGORITHM);
        String certAlias = (String)merged.get(KeyStore.CERTIFICATE_ALIAS);

        validateKeyStoreAttributes(keyStoreType, keyStorePath, keyStorePassword,
                                   certAlias, keyManagerFactoryAlgorithm);

        super.changeAttributes(changedValues);
    }

    private void validateKeyStoreAttributes(String type, String keyStorePath,
                                            String keyStorePassword, String alias,
                                            String keyManagerFactoryAlgorithm)
    {
        java.security.KeyStore keyStore;
        try
        {
            keyStore = SSLUtil.getInitializedKeyStore(keyStorePath, keyStorePassword, type);
        }
        catch (Exception e)
        {
            throw new IllegalConfigurationException("Cannot instantiate key store at " + keyStorePath, e);
        }

        if (alias != null)
        {
            Certificate cert;
            try
            {
                cert = keyStore.getCertificate(alias);
            }
            catch (KeyStoreException e)
            {
                // key store should be initialized above
                throw new ServerScopedRuntimeException("Key store has not been initialized", e);
            }
            if (cert == null)
            {
                throw new IllegalConfigurationException("Cannot find a certificate with alias " + alias
                        + "in key store : " + keyStorePath);
            }
        }

        try
        {
            KeyManagerFactory.getInstance(keyManagerFactoryAlgorithm);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalConfigurationException("Unknown keyManagerFactoryAlgorithm: "
                    + keyManagerFactoryAlgorithm);
        }
    }

    @Override
    public String getPath()
    {
        return (String) getAttribute(PATH);
    }

    @Override
    public String getCertificateAlias()
    {
        return (String) getAttribute(CERTIFICATE_ALIAS);
    }

    @Override
    public String getKeyManagerFactoryAlgorithm()
    {
        return (String) getAttribute(KEY_MANAGER_FACTORY_ALGORITHM);
    }

    @Override
    public String getKeyStoreType()
    {
        return (String) getAttribute(KEY_STORE_TYPE);
    }

    public KeyManager[] getKeyManagers() throws GeneralSecurityException
    {
        String keyStorePath = (String)getAttribute(KeyStore.PATH);
        String keyStorePassword = Subject.doAs(SecurityManager.SYSTEM, new PrivilegedAction<String>()
                                    {
                                        @Override
                                        public String run()
                                        {
                                            return getPassword();
                                        }
                                    });
        String keyStoreType = (String)getAttribute(KeyStore.KEY_STORE_TYPE);
        String keyManagerFactoryAlgorithm = (String)getAttribute(KeyStore.KEY_MANAGER_FACTORY_ALGORITHM);
        String certAlias = (String)getAttribute(KeyStore.CERTIFICATE_ALIAS);

        try
        {
            if (certAlias != null)
            {
                return new KeyManager[] {
                        new QpidClientX509KeyManager( certAlias, keyStorePath, keyStoreType, keyStorePassword,
                                                      keyManagerFactoryAlgorithm)
                                        };

            }
            else
            {
                final java.security.KeyStore ks = SSLUtil.getInitializedKeyStore(keyStorePath, keyStorePassword, keyStoreType);

                char[] keyStoreCharPassword = keyStorePassword == null ? null : keyStorePassword.toCharArray();

                final KeyManagerFactory kmf = KeyManagerFactory.getInstance(keyManagerFactoryAlgorithm);

                kmf.init(ks, keyStoreCharPassword);

                return kmf.getKeyManagers();
            }
        }
        catch (IOException e)
        {
            throw new GeneralSecurityException(e);
        }
    }
}
