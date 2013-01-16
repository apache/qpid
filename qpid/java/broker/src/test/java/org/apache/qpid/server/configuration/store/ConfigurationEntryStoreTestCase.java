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
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.security.auth.manager.AnonymousAuthenticationManager;
import org.apache.qpid.server.security.auth.manager.ExternalAuthenticationManager;
import org.apache.qpid.test.utils.QpidTestCase;

public abstract class ConfigurationEntryStoreTestCase extends QpidTestCase
{
    private ConfigurationEntryStore _store;

    private UUID _brokerId;
    private UUID _virtualHostId;
    private UUID _authenticationProviderId;

    private Map<String, Object> _brokerAttributes;
    private Map<String, Object> _virtualHostAttributes;
    private Map<String, Object> _authenticationProviderAttributes;

    public void setUp() throws Exception
    {
        super.setUp();

        _brokerId = UUID.randomUUID();
        _brokerAttributes = new HashMap<String, Object>();
        _brokerAttributes.put(Broker.DEFAULT_VIRTUAL_HOST, "test");
        _brokerAttributes.put(Broker.DEFAULT_AUTHENTICATION_PROVIDER, "authenticationProvider1");
        _brokerAttributes.put(Broker.ALERT_THRESHOLD_MESSAGE_AGE, 9);
        _brokerAttributes.put(Broker.ALERT_THRESHOLD_MESSAGE_COUNT, 8);
        _brokerAttributes.put(Broker.ALERT_THRESHOLD_QUEUE_DEPTH, 7);
        _brokerAttributes.put(Broker.ALERT_THRESHOLD_MESSAGE_SIZE, 6);
        _brokerAttributes.put(Broker.ALERT_REPEAT_GAP, 5);
        _brokerAttributes.put(Broker.FLOW_CONTROL_SIZE_BYTES, 5);
        _brokerAttributes.put(Broker.FLOW_CONTROL_RESUME_SIZE_BYTES, 3);
        _brokerAttributes.put(Broker.MAXIMUM_DELIVERY_ATTEMPTS, 2);
        _brokerAttributes.put(Broker.DEAD_LETTER_QUEUE_ENABLED, true);
        _brokerAttributes.put(Broker.HOUSEKEEPING_CHECK_PERIOD, 1);
        _brokerAttributes.put(Broker.ACL_FILE, "/path/to/acl");
        _brokerAttributes.put(Broker.SESSION_COUNT_LIMIT, 1000);
        _brokerAttributes.put(Broker.HEART_BEAT_DELAY, 2000);
        _brokerAttributes.put(Broker.STATISTICS_REPORTING_PERIOD, 4000);
        _brokerAttributes.put(Broker.STATISTICS_REPORTING_RESET_ENABLED, true);

        _virtualHostId = UUID.randomUUID();
        _virtualHostAttributes = new HashMap<String, Object>();
        _virtualHostAttributes.put(VirtualHost.NAME, "test");
        _virtualHostAttributes.put(VirtualHost.CONFIG_PATH, "/path/to/phantom/test");

        _authenticationProviderId = UUID.randomUUID();
        _authenticationProviderAttributes = new HashMap<String, Object>();
        _authenticationProviderAttributes.put(AuthenticationProvider.NAME, "authenticationProvider1");
        _authenticationProviderAttributes.put(AuthenticationManagerFactory.ATTRIBUTE_TYPE, AnonymousAuthenticationManager.class.getSimpleName());

        _store = createStore(_brokerId, _brokerAttributes);
        addConfiguration(_virtualHostId, VirtualHost.class.getSimpleName(), _virtualHostAttributes);
        addConfiguration(_authenticationProviderId, AuthenticationProvider.class.getSimpleName(), _authenticationProviderAttributes);
    }

    // ??? perhaps it should not be abstract
    protected abstract ConfigurationEntryStore createStore(UUID brokerId, Map<String, Object> brokerAttributes) throws Exception;

    protected abstract void addConfiguration(UUID id, String type, Map<String, Object> attributes);

    protected ConfigurationEntryStore getStore()
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
        assertEquals("Unexpected attributes", _brokerAttributes, attributes);
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
        Map<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put(VirtualHost.NAME, getName());
        virtualHostAttributes.put(VirtualHost.CONFIG_PATH, "/path/to/phantom/virtualhost/config");
        UUID virtualHostId = UUID.randomUUID();
        addConfiguration(virtualHostId, VirtualHost.class.getSimpleName(), virtualHostAttributes);

        assertNotNull("Virtual host with id " + virtualHostId + " should exist", _store.getEntry(virtualHostId));

        _store.remove(virtualHostId);
        assertNull("Authentication provider configuration should be removed", _store.getEntry(virtualHostId));
    }

    public void testRemoveMultipleEntries()
    {
        Map<String, Object> virtualHost1Attributes = new HashMap<String, Object>();
        virtualHost1Attributes.put(VirtualHost.NAME, "test1");
        virtualHost1Attributes.put(VirtualHost.CONFIG_PATH, "/path/to/phantom/virtualhost/config1");
        UUID virtualHost1Id = UUID.randomUUID();
        addConfiguration(virtualHost1Id, VirtualHost.class.getSimpleName(), virtualHost1Attributes);

        Map<String, Object> virtualHost2Attributes = new HashMap<String, Object>();
        virtualHost2Attributes.put(VirtualHost.NAME, "test1");
        virtualHost2Attributes.put(VirtualHost.CONFIG_PATH, "/path/to/phantom/virtualhost/config2");
        UUID virtualHost2Id = UUID.randomUUID();
        addConfiguration(virtualHost2Id, VirtualHost.class.getSimpleName(), virtualHost2Attributes);

        assertNotNull("Virtual host with id " + virtualHost1Id + " should exist", _store.getEntry(virtualHost1Id));
        assertNotNull("Virtual host with id " + virtualHost2Id + " should exist", _store.getEntry(virtualHost2Id));

        UUID[] deletedIds = _store.remove(virtualHost1Id, virtualHost2Id);
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
        attributes.put(Broker.DEFAULT_AUTHENTICATION_PROVIDER, "authenticationProvider1");
        attributes.put(Broker.ALERT_THRESHOLD_MESSAGE_AGE, 19);
        attributes.put(Broker.ALERT_THRESHOLD_MESSAGE_COUNT, 18);
        attributes.put(Broker.ALERT_THRESHOLD_QUEUE_DEPTH, 17);
        attributes.put(Broker.ALERT_THRESHOLD_MESSAGE_SIZE, 16);
        attributes.put(Broker.ALERT_REPEAT_GAP, 15);
        attributes.put(Broker.FLOW_CONTROL_SIZE_BYTES, 15);
        attributes.put(Broker.FLOW_CONTROL_RESUME_SIZE_BYTES, 13);
        attributes.put(Broker.MAXIMUM_DELIVERY_ATTEMPTS, 12);
        attributes.put(Broker.DEAD_LETTER_QUEUE_ENABLED, false);
        attributes.put(Broker.HOUSEKEEPING_CHECK_PERIOD, 11);
        attributes.put(Broker.ACL_FILE, "/path/to/acl1");
        attributes.put(Broker.SESSION_COUNT_LIMIT, 11000);
        attributes.put(Broker.HEART_BEAT_DELAY, 12000);
        attributes.put(Broker.STATISTICS_REPORTING_PERIOD, 14000);
        attributes.put(Broker.STATISTICS_REPORTING_RESET_ENABLED, false);
        ConfigurationEntry updatedBrokerEntry = new ConfigurationEntry(_brokerId, Broker.class.getSimpleName(), attributes,
                brokerConfigEntry.getChildrenIds(), _store);

        _store.save(updatedBrokerEntry);

        ConfigurationEntry newBrokerConfigEntry = _store.getRootEntry();
        assertNotNull("Root entry does not exist", newBrokerConfigEntry);
        assertEquals("Unexpected id", _brokerId, newBrokerConfigEntry.getId());
        assertEquals("Unexpected type ", Broker.class.getSimpleName(), newBrokerConfigEntry.getType());
        Map<String, Object> newBrokerattributes = newBrokerConfigEntry.getAttributes();
        assertNotNull("Attributes cannot be null", newBrokerattributes);
        assertEquals("Unexpected attributes", attributes, newBrokerattributes);
    }

    public void testSaveNewVirtualHost()
    {
        Map<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put(VirtualHost.NAME, "test1");
        virtualHostAttributes.put(VirtualHost.CONFIG_PATH, "/path/to/phantom/virtualhost/config1");
        UUID virtualHostId = UUID.randomUUID();
        ConfigurationEntry hostEntry = new ConfigurationEntry(virtualHostId, VirtualHost.class.getSimpleName(), virtualHostAttributes,
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
        virtualHostAttributes.put(VirtualHost.CONFIG_PATH, "/path/to/new/phantom/test/configuration");

        ConfigurationEntry updatedEntry = new ConfigurationEntry(_virtualHostId, VirtualHost.class.getSimpleName(), virtualHostAttributes,
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
        authenticationProviderAttributes.put(AuthenticationManagerFactory.ATTRIBUTE_TYPE, ExternalAuthenticationManager.class.getSimpleName());
        ConfigurationEntry providerEntry = new ConfigurationEntry(authenticationProviderId, AuthenticationProvider.class.getSimpleName(),
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
        authenticationProviderAttributes.put(AuthenticationManagerFactory.ATTRIBUTE_TYPE, ExternalAuthenticationManager.class.getSimpleName());
        ConfigurationEntry updatedEntry = new ConfigurationEntry(_authenticationProviderId, AuthenticationProvider.class.getSimpleName(),
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
        attributes.put(TrustStore.TYPE, "NON-JKS");
        attributes.put(TrustStore.KEY_MANAGER_FACTORY_ALGORITHM, "NON-STANDARD");
        attributes.put(TrustStore.DESCRIPTION, "Description");

        ConfigurationEntry trustStoreEntry = new ConfigurationEntry(trustStoreId, TrustStore.class.getSimpleName(), attributes,
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
        attributes.put(KeyStore.TYPE, "NON-JKS");
        attributes.put(KeyStore.KEY_MANAGER_FACTORY_ALGORITHM, "NON-STANDARD");
        attributes.put(KeyStore.DESCRIPTION, "Description");
        attributes.put(KeyStore.CERTIFICATE_ALIAS, "Alias");

        ConfigurationEntry keyStoreEntry = new ConfigurationEntry(keyStoreId, KeyStore.class.getSimpleName(), attributes, Collections.<UUID> emptySet(),
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

        ConfigurationEntry groupProviderEntry = new ConfigurationEntry(groupProviderId, GroupProvider.class.getSimpleName(), attributes,
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

        ConfigurationEntry portEntry = new ConfigurationEntry(portId, Port.class.getSimpleName(), attributes, Collections.<UUID> emptySet(), _store);

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
        virtualHostAttributes.put(VirtualHost.CONFIG_PATH, "/path/to/phantom/virtualhost/config1");
        ConfigurationEntry hostEntry = new ConfigurationEntry(virtualHostId, VirtualHost.class.getSimpleName(), virtualHostAttributes,
                Collections.<UUID> emptySet(), _store);

        UUID keyStoreId = UUID.randomUUID();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(KeyStore.NAME, getName());
        attributes.put(KeyStore.PATH, "/path/to/truststore");
        attributes.put(KeyStore.PASSWORD, "my-secret-password");
        attributes.put(KeyStore.TYPE, "NON-JKS");
        attributes.put(KeyStore.KEY_MANAGER_FACTORY_ALGORITHM, "NON-STANDARD");
        attributes.put(KeyStore.DESCRIPTION, "Description");
        attributes.put(KeyStore.CERTIFICATE_ALIAS, "Alias");

        ConfigurationEntry keyStoreEntry = new ConfigurationEntry(keyStoreId, KeyStore.class.getSimpleName(), attributes, Collections.<UUID> emptySet(),
                _store);

        _store.save(hostEntry, keyStoreEntry);

        assertNotNull("Virtual host is not found", _store.getEntry(virtualHostId));
        assertNotNull("Key store is not found", _store.getEntry(keyStoreId));
    }
}
