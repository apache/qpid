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
package org.apache.qpid.server.configuration.store;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryImpl;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.FileSystemPreferencesProvider;
import org.apache.qpid.server.security.auth.manager.AnonymousAuthenticationManager;
import org.apache.qpid.server.security.auth.manager.ExternalAuthenticationManager;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.test.utils.QpidTestCase;

public abstract class ConfigurationEntryStoreTestCase extends QpidTestCase
{
    private MemoryConfigurationEntryStore _store;

    private UUID _brokerId;
    private UUID _virtualHostId;
    protected UUID _authenticationProviderId;

    private Map<String, Object> _brokerAttributes;
    private Map<String, Object> _virtualHostAttributes;
    private Map<String, Object> _authenticationProviderAttributes;

    public void setUp() throws Exception
    {
        super.setUp();

        _brokerId = UUID.randomUUID();
        _brokerAttributes = new HashMap<String, Object>();
        _brokerAttributes.put(Broker.DEFAULT_VIRTUAL_HOST, "test");
        _brokerAttributes.put(Broker.CONNECTION_SESSION_COUNT_LIMIT, 1000);
        _brokerAttributes.put(Broker.CONNECTION_HEART_BEAT_DELAY, 2000);
        _brokerAttributes.put(Broker.STATISTICS_REPORTING_PERIOD, 4000);
        _brokerAttributes.put(Broker.STATISTICS_REPORTING_RESET_ENABLED, true);

        _virtualHostId = UUID.randomUUID();
        _virtualHostAttributes = new HashMap<String, Object>();
        _virtualHostAttributes.put(VirtualHost.NAME, "test");
        _virtualHostAttributes.put(VirtualHost.TYPE, "STANDARD");

        _authenticationProviderId = UUID.randomUUID();
        _authenticationProviderAttributes = new HashMap<String, Object>();
        _authenticationProviderAttributes.put(AuthenticationProvider.NAME, "authenticationProvider1");
        _authenticationProviderAttributes.put(AuthenticationProvider.TYPE, AnonymousAuthenticationManager.class.getSimpleName());

        _store = createStore(_brokerId, _brokerAttributes);
        addConfiguration(_virtualHostId, VirtualHost.class.getSimpleName(), _virtualHostAttributes);
        addConfiguration(_authenticationProviderId, AuthenticationProvider.class.getSimpleName(), _authenticationProviderAttributes);
    }

    // ??? perhaps it should not be abstract

    protected abstract MemoryConfigurationEntryStore createStore(UUID brokerId, Map<String, Object> brokerAttributes) throws Exception;

    protected abstract void addConfiguration(UUID id, String type, Map<String, Object> attributes, UUID parentId);

    protected final void addConfiguration(UUID id, String type, Map<String, Object> attributes)
    {
        addConfiguration(id, type, attributes, _brokerId);
    }

    protected MemoryConfigurationEntryStore getStore()
    {
        return _store;
    }

    public void testGetRootEntry()
    {
        ConfigurationEntry brokerConfigEntry = _store.getRootEntry();
        assertNotNull("Root entry does not exist", brokerConfigEntry);
        assertEquals("Unexpected id", _brokerId, brokerConfigEntry.getId());
        assertEquals("Unexpected type ", Broker.class.getSimpleName(), brokerConfigEntry.getType());
        Map<String, Object> attributes = brokerConfigEntry.getAttributes();
        assertNotNull("Attributes cannot be null", attributes);
        for (Map.Entry<String, Object> attribute : _brokerAttributes.entrySet())
        {
            assertEquals("Unexpected attribute " + attribute.getKey(), attribute.getValue(), attributes.get(attribute.getKey()));
        }
    }

    public void testGetEntry()
    {
        ConfigurationEntry authenticationProviderConfigEntry = _store.getEntry(_authenticationProviderId);
        assertNotNull("Provider with id " + _authenticationProviderId + " should exist", authenticationProviderConfigEntry);
        assertEquals("Unexpected id", _authenticationProviderId, authenticationProviderConfigEntry.getId());
        assertEquals("Unexpected type ", AuthenticationProvider.class.getSimpleName(), authenticationProviderConfigEntry.getType());
        Map<String, Object> attributes = authenticationProviderConfigEntry.getAttributes();
        assertNotNull("Attributes cannot be null", attributes);
        assertEquals("Unexpected attributes", _authenticationProviderAttributes, attributes);
    }

    public void testRemove()
    {
        final Map<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put(VirtualHost.NAME, getName());
        virtualHostAttributes.put(VirtualHost.TYPE, "STANDARD");
        final UUID virtualHostId = UUID.randomUUID();
        addConfiguration(virtualHostId, VirtualHost.class.getSimpleName(), virtualHostAttributes);

        assertNotNull("Virtual host with id " + virtualHostId + " should exist", _store.getEntry(virtualHostId));

        _store.remove(createConfiguredObjectRecord(virtualHostId, VirtualHost.class, virtualHostAttributes));
        assertNull("Virtual host configuration should be removed", _store.getEntry(virtualHostId));
    }

    protected ConfiguredObjectRecord createConfiguredObjectRecord(final UUID virtualHostId,
                                                                  final Class<? extends ConfiguredObject> type,
                                                                  final Map<String, Object> virtualHostAttributes)
    {
        return new ConfiguredObjectRecord()
        {
            @Override
            public UUID getId()
            {
                return virtualHostId;
            }

            @Override
            public String getType()
            {
                return type.getSimpleName();
            }

            @Override
            public Map<String, Object> getAttributes()
            {
                return virtualHostAttributes;
            }

            @Override
            public Map<String, ConfiguredObjectRecord> getParents()
            {
                // TODO RG : rectify this
                return null;
            }
        };
    }

    public void testRemoveMultipleEntries()
    {
        Map<String, Object> virtualHost1Attributes = new HashMap<String, Object>();
        virtualHost1Attributes.put(VirtualHost.NAME, "test1");
        virtualHost1Attributes.put(VirtualHost.TYPE, "STANDARD");
        UUID virtualHost1Id = UUID.randomUUID();
        addConfiguration(virtualHost1Id, VirtualHost.class.getSimpleName(), virtualHost1Attributes);

        Map<String, Object> virtualHost2Attributes = new HashMap<String, Object>();
        virtualHost2Attributes.put(VirtualHost.NAME, "test1");
        virtualHost2Attributes.put(VirtualHost.TYPE, "STANDARD");
        UUID virtualHost2Id = UUID.randomUUID();
        addConfiguration(virtualHost2Id, VirtualHost.class.getSimpleName(), virtualHost2Attributes);

        assertNotNull("Virtual host with id " + virtualHost1Id + " should exist", _store.getEntry(virtualHost1Id));
        assertNotNull("Virtual host with id " + virtualHost2Id + " should exist", _store.getEntry(virtualHost2Id));

        UUID[] deletedIds =
                _store.remove(createConfiguredObjectRecord(virtualHost1Id, VirtualHost.class, virtualHost1Attributes),
                              createConfiguredObjectRecord(virtualHost2Id, VirtualHost.class, virtualHost2Attributes));

        assertNotNull("Unexpected deleted ids", deletedIds);
        assertEquals("Unexpected id of first deleted virtual host", virtualHost1Id , deletedIds[0]);
        assertEquals("Unexpected id of second deleted virtual host", virtualHost2Id , deletedIds[1]);
        assertNull("First virtual host configuration should be removed", _store.getEntry(virtualHost1Id));
        assertNull("Second virtual host configuration should be removed", _store.getEntry(virtualHost2Id));
    }

    public void testSaveBroker()
    {
        ConfigurationEntry brokerConfigEntry = _store.getRootEntry();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Broker.DEFAULT_VIRTUAL_HOST, "test");
        attributes.put(Broker.CONNECTION_SESSION_COUNT_LIMIT, 11000);
        attributes.put(Broker.CONNECTION_HEART_BEAT_DELAY, 12000);
        attributes.put(Broker.STATISTICS_REPORTING_PERIOD, 14000);
        attributes.put(Broker.STATISTICS_REPORTING_RESET_ENABLED, false);
        ConfigurationEntry
                updatedBrokerEntry = new ConfigurationEntryImpl(_brokerId, Broker.class.getSimpleName(), attributes,
                brokerConfigEntry.getChildrenIds(), _store);

        _store.save(updatedBrokerEntry);

        ConfigurationEntry newBrokerConfigEntry = _store.getRootEntry();
        assertNotNull("Root entry does not exist", newBrokerConfigEntry);
        assertEquals("Unexpected id", _brokerId, newBrokerConfigEntry.getId());
        assertEquals("Unexpected type ", Broker.class.getSimpleName(), newBrokerConfigEntry.getType());
        Map<String, Object> newBrokerAttributes = newBrokerConfigEntry.getAttributes();
        assertNotNull("Attributes cannot be null", newBrokerAttributes);
        assertEquals("Unexpected attributes", attributes, newBrokerAttributes);
    }

    public void testSaveNewVirtualHost()
    {
        Map<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put(VirtualHost.NAME, "test1");
        virtualHostAttributes.put(VirtualHost.TYPE, "STANDARD");
        UUID virtualHostId = UUID.randomUUID();
        ConfigurationEntry hostEntry = new ConfigurationEntryImpl(virtualHostId, VirtualHost.class.getSimpleName(), virtualHostAttributes,
                Collections.<UUID> emptySet(), _store);

        _store.save(hostEntry);

        ConfigurationEntry configurationEntry = _store.getEntry(virtualHostId);
        assertEquals("Unexpected virtual host configuration", hostEntry, configurationEntry);
        assertEquals("Unexpected type", VirtualHost.class.getSimpleName(), configurationEntry.getType());
        assertEquals("Unexpected virtual host attributes", hostEntry.getAttributes(), configurationEntry.getAttributes());
        assertTrue("Unexpected virtual host children found", hostEntry.getChildrenIds().isEmpty());
    }

    public void testSaveExistingVirtualHost()
    {
        ConfigurationEntry hostEntry = _store.getEntry(_virtualHostId);
        assertNotNull("Host configuration is not found", hostEntry);

        Map<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put(VirtualHost.NAME, "test");
        virtualHostAttributes.put(VirtualHost.TYPE, "STANDARD");

        ConfigurationEntry updatedEntry = new ConfigurationEntryImpl(_virtualHostId, VirtualHost.class.getSimpleName(), virtualHostAttributes,
                hostEntry.getChildrenIds(), _store);
        _store.save(updatedEntry);

        ConfigurationEntry newHostEntry = _store.getEntry(_virtualHostId);
        assertEquals("Unexpected virtual host configuration", updatedEntry, newHostEntry);
        assertEquals("Unexpected type", VirtualHost.class.getSimpleName(), newHostEntry.getType());
        assertEquals("Unexpected virtual host attributes", updatedEntry.getAttributes(), newHostEntry.getAttributes());
        assertEquals("Unexpected virtual host children found", updatedEntry.getChildrenIds(), newHostEntry.getChildrenIds());
    }

    public void testSaveNewAuthenticationProvider()
    {
        UUID authenticationProviderId = UUID.randomUUID();
        Map<String, Object> authenticationProviderAttributes = new HashMap<String, Object>();
        authenticationProviderAttributes.put(AuthenticationProvider.NAME, "authenticationProvider1");
        authenticationProviderAttributes.put(AuthenticationProvider.TYPE, ExternalAuthenticationManager.class.getSimpleName());
        ConfigurationEntry providerEntry = new ConfigurationEntryImpl(authenticationProviderId, AuthenticationProvider.class.getSimpleName(),
                authenticationProviderAttributes, Collections.<UUID> emptySet(), _store);

        _store.save(providerEntry);

        ConfigurationEntry storeEntry = _store.getEntry(authenticationProviderId);
        assertEquals("Unexpected provider configuration", providerEntry, storeEntry);
        assertEquals("Unexpected type", AuthenticationProvider.class.getSimpleName(), storeEntry.getType());
        assertEquals("Unexpected provider attributes", providerEntry.getAttributes(), storeEntry.getAttributes());
        assertTrue("Unexpected provider children found", storeEntry.getChildrenIds().isEmpty());
    }

    public void testSaveExistingAuthenticationProvider()
    {
        ConfigurationEntry providerEntry = _store.getEntry(_authenticationProviderId);
        assertNotNull("provider configuration is not found", providerEntry);

        Map<String, Object> authenticationProviderAttributes = new HashMap<String, Object>();
        authenticationProviderAttributes.put(AuthenticationProvider.NAME, "authenticationProvider1");
        authenticationProviderAttributes.put(AuthenticationProvider.TYPE, ExternalAuthenticationManager.class.getSimpleName());
        ConfigurationEntry updatedEntry = new ConfigurationEntryImpl(_authenticationProviderId, AuthenticationProvider.class.getSimpleName(),
                authenticationProviderAttributes, Collections.<UUID> emptySet(), _store);
        _store.save(updatedEntry);

        ConfigurationEntry storeEntry = _store.getEntry(_authenticationProviderId);
        assertEquals("Unexpected provider configuration", updatedEntry, storeEntry);
        assertEquals("Unexpected type", AuthenticationProvider.class.getSimpleName(), storeEntry.getType());
        assertEquals("Unexpected provider attributes", updatedEntry.getAttributes(), storeEntry.getAttributes());
        assertTrue("Unexpected provider children found", storeEntry.getChildrenIds().isEmpty());
    }

    public void testSaveTrustStore()
    {
        UUID trustStoreId = UUID.randomUUID();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(TrustStore.NAME, getName());
        attributes.put(TrustStore.PATH, "/path/to/truststore");
        attributes.put(TrustStore.PASSWORD, "my-secret-password");
        attributes.put(TrustStore.TRUST_STORE_TYPE, "NON-JKS");
        attributes.put(TrustStore.TRUST_MANAGER_FACTORY_ALGORITHM, "NON-STANDARD");
        attributes.put(TrustStore.DESCRIPTION, "Description");

        ConfigurationEntry
                trustStoreEntry = new ConfigurationEntryImpl(trustStoreId, TrustStore.class.getSimpleName(), attributes,
                Collections.<UUID> emptySet(), _store);

        _store.save(trustStoreEntry);

        ConfigurationEntry storeEntry = _store.getEntry(trustStoreId);
        assertEquals("Unexpected trust store configuration", trustStoreEntry, storeEntry);
        assertEquals("Unexpected type", TrustStore.class.getSimpleName(), storeEntry.getType());
        assertEquals("Unexpected provider attributes", trustStoreEntry.getAttributes(), storeEntry.getAttributes());
        assertTrue("Unexpected provider children found", storeEntry.getChildrenIds().isEmpty());
    }

    public void testSaveKeyStore()
    {
        UUID keyStoreId = UUID.randomUUID();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(KeyStore.NAME, getName());
        attributes.put(KeyStore.PATH, "/path/to/truststore");
        attributes.put(KeyStore.PASSWORD, "my-secret-password");
        attributes.put(KeyStore.KEY_STORE_TYPE, "NON-JKS");
        attributes.put(KeyStore.KEY_MANAGER_FACTORY_ALGORITHM, "NON-STANDARD");
        attributes.put(KeyStore.DESCRIPTION, "Description");
        attributes.put(KeyStore.CERTIFICATE_ALIAS, "Alias");

        ConfigurationEntry keyStoreEntry = new ConfigurationEntryImpl(keyStoreId, KeyStore.class.getSimpleName(), attributes, Collections.<UUID> emptySet(),
                _store);

        _store.save(keyStoreEntry);

        ConfigurationEntry storeEntry = _store.getEntry(keyStoreId);
        assertEquals("Unexpected key store configuration", keyStoreEntry, storeEntry);
        assertEquals("Unexpected type", KeyStore.class.getSimpleName(), storeEntry.getType());
        assertEquals("Unexpected provider attributes", keyStoreEntry.getAttributes(), storeEntry.getAttributes());
        assertTrue("Unexpected provider children found", storeEntry.getChildrenIds().isEmpty());
    }

    public void testSaveGroupProvider()
    {
        UUID groupProviderId = UUID.randomUUID();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(GroupProvider.NAME, getName());

        ConfigurationEntry groupProviderEntry = new ConfigurationEntryImpl(groupProviderId, GroupProvider.class.getSimpleName(), attributes,
                Collections.<UUID> emptySet(), _store);

        _store.save(groupProviderEntry);

        ConfigurationEntry storeEntry = _store.getEntry(groupProviderId);
        assertEquals("Unexpected group provider configuration", groupProviderEntry, storeEntry);
        assertEquals("Unexpected type", GroupProvider.class.getSimpleName(), storeEntry.getType());
        assertEquals("Unexpected group provider attributes", groupProviderEntry.getAttributes(), storeEntry.getAttributes());
        assertTrue("Unexpected provider children found", storeEntry.getChildrenIds().isEmpty());
    }

    public void testSavePort()
    {
        UUID portId = UUID.randomUUID();
        Map<String, Object> attributes = new HashMap<String, Object>();
        Set<String> tcpTransportSet = Collections.singleton(Transport.TCP.name());
        attributes.put(Port.PORT, 9999);
        attributes.put(Port.TRANSPORTS, tcpTransportSet);
        attributes.put(Port.TCP_NO_DELAY, true);
        attributes.put(Port.RECEIVE_BUFFER_SIZE, 1);
        attributes.put(Port.SEND_BUFFER_SIZE, 2);
        attributes.put(Port.NEED_CLIENT_AUTH, true);
        attributes.put(Port.WANT_CLIENT_AUTH, true);

        ConfigurationEntry portEntry = new ConfigurationEntryImpl(portId, Port.class.getSimpleName(), attributes, Collections
                .<UUID> emptySet(), _store);

        _store.save(portEntry);

        ConfigurationEntry storeEntry = _store.getEntry(portId);
        assertEquals("Unexpected port configuration", portEntry, storeEntry);
        assertEquals("Unexpected type", Port.class.getSimpleName(), storeEntry.getType());
        assertEquals("Unexpected port attributes", portEntry.getAttributes(), storeEntry.getAttributes());
        assertTrue("Unexpected port children found", storeEntry.getChildrenIds().isEmpty());
    }

    public void testMultipleSave()
    {
        UUID virtualHostId = UUID.randomUUID();
        Map<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put(VirtualHost.NAME, "test1");
        virtualHostAttributes.put(VirtualHost.TYPE, "STANDARD");
        ConfigurationEntry hostEntry = new ConfigurationEntryImpl(virtualHostId, VirtualHost.class.getSimpleName(), virtualHostAttributes,
                Collections.<UUID> emptySet(), _store);

        UUID keyStoreId = UUID.randomUUID();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(KeyStore.NAME, getName());
        attributes.put(KeyStore.PATH, "/path/to/truststore");
        attributes.put(KeyStore.PASSWORD, "my-secret-password");
        attributes.put(KeyStore.KEY_STORE_TYPE, "NON-JKS");
        attributes.put(KeyStore.KEY_MANAGER_FACTORY_ALGORITHM, "NON-STANDARD");
        attributes.put(KeyStore.DESCRIPTION, "Description");
        attributes.put(KeyStore.CERTIFICATE_ALIAS, "Alias");

        ConfigurationEntry keyStoreEntry = new ConfigurationEntryImpl(keyStoreId, KeyStore.class.getSimpleName(), attributes, Collections.<UUID> emptySet(),
                _store);

        _store.save(hostEntry, keyStoreEntry);

        assertNotNull("Virtual host is not found", _store.getEntry(virtualHostId));
        assertNotNull("Key store is not found", _store.getEntry(keyStoreId));
    }

    public void testAddPreferencesProvider()
    {
        UUID preferencesProviderId = UUID.randomUUID();
        String path = TMP_FOLDER;
        String name = getTestName();

        addPreferencesProvider(preferencesProviderId, name, path);

        assertEquals("Unexpected preference provider ID in authentication provider children set", preferencesProviderId, _store
                .getEntry(_authenticationProviderId).getChildrenIds().iterator().next());
        ConfigurationEntry preferencesProviderEntry = _store.getEntry(preferencesProviderId);
        assertNotNull("Preferences provider is not found", preferencesProviderEntry);
        assertEquals("Unexpected preferences provider id", preferencesProviderId, preferencesProviderEntry.getId());
        Map<String, Object> attributes = preferencesProviderEntry.getAttributes();
        assertEquals("Unexpected preferences provider name", name, attributes.get(PreferencesProvider.NAME));
        assertEquals("Unexpected preferences provider path", path, attributes.get(FileSystemPreferencesProvider.PATH));
        assertEquals("Unexpected preferences provider type", FileSystemPreferencesProvider.PROVIDER_TYPE,
                attributes.get(PreferencesProvider.TYPE));
    }

    protected void addPreferencesProvider(UUID preferencesProviderId, String name, String path)
    {
        ConfigurationEntry authenticationProviderEntry = _store.getEntry(_authenticationProviderId);
        ConfigurationEntry newAuthenticationProviderConfigEntry = new ConfigurationEntryImpl(authenticationProviderEntry.getId(),
                authenticationProviderEntry.getType(), authenticationProviderEntry.getAttributes(),
                Collections.<UUID>singleton(preferencesProviderId), _store);

        Map<String, Object> preferencesProviderAttributes = new HashMap<String, Object>();
        preferencesProviderAttributes.put(PreferencesProvider.TYPE, FileSystemPreferencesProvider.PROVIDER_TYPE);
        preferencesProviderAttributes.put(FileSystemPreferencesProvider.PATH, path);
        preferencesProviderAttributes.put(PreferencesProvider.NAME, name);

        ConfigurationEntry preferencesProviderEntry = new ConfigurationEntryImpl(preferencesProviderId, PreferencesProvider.class.getSimpleName(),
                preferencesProviderAttributes, Collections.<UUID> emptySet(), _store);

        _store.save(newAuthenticationProviderConfigEntry, preferencesProviderEntry);
    }

}
