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
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.IntegrityViolationException;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.transport.network.security.ssl.QpidClientX509KeyManager;
import org.apache.qpid.transport.network.security.ssl.SSLUtil;

@ManagedObject( category = false )
public class FileKeyStoreImpl extends AbstractConfiguredObject<FileKeyStoreImpl> implements FileKeyStore<FileKeyStoreImpl>
{

    @ManagedAttributeField
    private String _type;
    @ManagedAttributeField
    private String _keyStoreType;
    @ManagedAttributeField
    private String _certificateAlias;
    @ManagedAttributeField
    private String _keyManagerFactoryAlgorithm;
    @ManagedAttributeField
    private String _path;
    @ManagedAttributeField
    private String _password;


    private Broker<?> _broker;

    @ManagedObjectFactoryConstructor
    public FileKeyStoreImpl(Map<String, Object> attributes, Broker<?> broker)
    {
        super(parentsMap(broker), attributes);

        _broker = broker;
    }

    @Override
    public void onValidate()
    {
        super.onValidate();
        validateKeyStoreAttributes(this);
    }

    @Override
    public State getState()
    {
        return State.ACTIVE;
    }

    @Override
    public Object getAttribute(String name)
    {
        if(KeyStore.STATE.equals(name))
        {
            return getState();
        }

        return super.getAttribute(name);
    }

    @Override
    protected boolean setState(State desiredState)
    {
        if(desiredState == State.DELETED)
        {
            // verify that it is not in use
            String storeName = getName();

            Collection<Port> ports = new ArrayList<Port>(_broker.getPorts());
            for (Port port : ports)
            {
                if (port.getKeyStore() == this)
                {
                    throw new IntegrityViolationException("Key store '" + storeName + "' can't be deleted as it is in use by a port:" + port.getName());
                }
            }
            deleted();
            return true;
        }

        return false;
    }

    @Override
    protected void authoriseSetDesiredState(State desiredState) throws AccessControlException
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
    protected void authoriseSetAttributes(ConfiguredObject<?> modified, Set<String> attributes) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), KeyStore.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting key store attributes is denied");
        }
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);
        FileKeyStore changedStore = (FileKeyStore) proxyForValidation;
        if(changedAttributes.contains(NAME) && !getName().equals(changedStore.getName()))
        {
            throw new IllegalConfigurationException("Changing the key store name is not allowed");
        }
        validateKeyStoreAttributes(changedStore);
    }

    private void validateKeyStoreAttributes(FileKeyStore<?> fileKeyStore)
    {
        java.security.KeyStore keyStore;
        try
        {
            String path = fileKeyStore.getPath();
            String password = fileKeyStore.getPassword();
            String keyStoreType = fileKeyStore.getKeyStoreType();
            keyStore = SSLUtil.getInitializedKeyStore(path, password, keyStoreType);
        }
        catch (Exception e)
        {
            throw new IllegalConfigurationException("Cannot instantiate key store at " + fileKeyStore.getPath(), e);
        }

        if (fileKeyStore.getCertificateAlias() != null)
        {
            Certificate cert;
            try
            {
                cert = keyStore.getCertificate(fileKeyStore.getCertificateAlias());
            }
            catch (KeyStoreException e)
            {
                // key store should be initialized above
                throw new ServerScopedRuntimeException("Key store has not been initialized", e);
            }
            if (cert == null)
            {
                throw new IllegalConfigurationException("Cannot find a certificate with alias " + fileKeyStore.getCertificateAlias()
                        + "in key store : " + fileKeyStore.getPath());
            }
        }

        try
        {
            KeyManagerFactory.getInstance(fileKeyStore.getKeyManagerFactoryAlgorithm());
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalConfigurationException("Unknown keyManagerFactoryAlgorithm: "
                    + fileKeyStore.getKeyManagerFactoryAlgorithm());
        }

        if(!fileKeyStore.isDurable())
        {
            throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
        }
    }

    @Override
    public String getPath()
    {
        return _path;
    }

    @Override
    public String getCertificateAlias()
    {
        return _certificateAlias;
    }

    @Override
    public String getKeyManagerFactoryAlgorithm()
    {
        return _keyManagerFactoryAlgorithm;
    }

    @Override
    public String getKeyStoreType()
    {
        return _keyStoreType;
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

    public KeyManager[] getKeyManagers() throws GeneralSecurityException
    {

        try
        {
            if (_certificateAlias != null)
            {
                return new KeyManager[] {
                        new QpidClientX509KeyManager( _certificateAlias, _path, _keyStoreType, getPassword(),
                                                      _keyManagerFactoryAlgorithm)
                                        };

            }
            else
            {
                final java.security.KeyStore ks = SSLUtil.getInitializedKeyStore(_path, getPassword(), _keyStoreType);

                char[] keyStoreCharPassword = getPassword() == null ? null : getPassword().toCharArray();

                final KeyManagerFactory kmf = KeyManagerFactory.getInstance(_keyManagerFactoryAlgorithm);

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
