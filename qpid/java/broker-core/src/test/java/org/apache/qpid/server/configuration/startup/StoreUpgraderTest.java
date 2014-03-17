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
package org.apache.qpid.server.configuration.startup;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import junit.framework.TestCase;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.VirtualHost;

public class StoreUpgraderTest extends TestCase
{

    private final UUID _brokerId = UUID.randomUUID();
    private final UUID _virtualHostId = UUID.randomUUID();
    private ConfigurationEntryStore _store = mock(ConfigurationEntryStore.class);

    public void testUpgrade13To14_RejectsConfigPath() throws Exception
    {
        HashMap<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put("name", "test");
        virtualHostAttributes.put("type", "STANDARD");
        virtualHostAttributes.put("configPath", "/mypath");
        try
        {
            doTest(_store, virtualHostAttributes);
            fail("Upgrade of virtual host with configuration XML is unsupported at the moment");
        }
        catch(IllegalConfigurationException e)
        {
            // pass
        }
    }

    public void testUpgrade13To14_Derby() throws Exception
    {
        HashMap<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put("name", "test");
        virtualHostAttributes.put("type", "STANDARD");
        virtualHostAttributes.put("storeType", "DERBy");
        virtualHostAttributes.put("storePath", "/mystorepath");
        virtualHostAttributes.put("storeUnderfullSize", 1000);
        virtualHostAttributes.put("storeOverfullSize", 2000);

        doTest(_store, virtualHostAttributes);

        ConfigurationEntry expectNewRoot = new ConfigurationEntry(_brokerId, Broker.class.getSimpleName(), Collections.<String, Object>singletonMap(Broker.MODEL_VERSION, "1.4"), Collections.singleton(_virtualHostId), _store);
        ConfigurationEntry expectedNewVirtualHost;
        {
            Map<String, Object> expectedNewVirtualHostMessageSettings = new HashMap<String, Object>();
            expectedNewVirtualHostMessageSettings.put("storeType", "DERBY");
            expectedNewVirtualHostMessageSettings.put("storePath", "/mystorepath");
            expectedNewVirtualHostMessageSettings.put("storeUnderfullSize", 1000);
            expectedNewVirtualHostMessageSettings.put("storeOverfullSize", 2000);

            Map<String, Object> expectedNewVirtualHostAttributes = new HashMap<String, Object>();
            expectedNewVirtualHostAttributes.put(VirtualHost.NAME, "test");
            expectedNewVirtualHostAttributes.put(VirtualHost.TYPE, "STANDARD");
            expectedNewVirtualHostAttributes.put(VirtualHost.MESSAGE_STORE_SETTINGS, expectedNewVirtualHostMessageSettings);

            expectedNewVirtualHost =  new ConfigurationEntry(_virtualHostId, VirtualHost.class.getSimpleName(), expectedNewVirtualHostAttributes, Collections.<UUID>emptySet(), _store);
        }
        verify(_store).save(expectedNewVirtualHost, expectNewRoot);
    }

    public void testUpgrade13To14_DerbyConfigurationStore() throws Exception
    {
        HashMap<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put("name", "test");
        virtualHostAttributes.put("type", "STANDARD");
        virtualHostAttributes.put("configStoreType", "DERBy");
        virtualHostAttributes.put("configStorePath", "/mystorepath");

        doTest(_store, virtualHostAttributes);

        ConfigurationEntry expectNewRoot = new ConfigurationEntry(_brokerId, Broker.class.getSimpleName(), Collections.<String, Object>singletonMap(Broker.MODEL_VERSION, "1.4"), Collections.singleton(_virtualHostId), _store);
        ConfigurationEntry expectedNewVirtualHost;
        {
            Map<String, Object> expectedNewVirtualHostConfigurationStoreSettings = new HashMap<String, Object>();
            expectedNewVirtualHostConfigurationStoreSettings.put("storeType", "DERBY");
            expectedNewVirtualHostConfigurationStoreSettings.put("storePath", "/mystorepath");

            Map<String, Object> expectedNewVirtualHostAttributes = new HashMap<String, Object>();
            expectedNewVirtualHostAttributes.put(VirtualHost.NAME, "test");
            expectedNewVirtualHostAttributes.put(VirtualHost.TYPE, "STANDARD");
            expectedNewVirtualHostAttributes.put(VirtualHost.CONFIGURATION_STORE_SETTINGS, expectedNewVirtualHostConfigurationStoreSettings);

            expectedNewVirtualHost =  new ConfigurationEntry(_virtualHostId, VirtualHost.class.getSimpleName(), expectedNewVirtualHostAttributes, Collections.<UUID>emptySet(), _store);
        }
        verify(_store).save(expectedNewVirtualHost, expectNewRoot);
    }

    public void testUpgrade13To14_JsonConfigurationStore() throws Exception
    {
        HashMap<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put("name", "test");
        virtualHostAttributes.put("type", "STANDARD");
        virtualHostAttributes.put("configStoreType", "JsoN");
        virtualHostAttributes.put("configStorePath", "/mystorepath");

        doTest(_store, virtualHostAttributes);

        ConfigurationEntry expectNewRoot = new ConfigurationEntry(_brokerId, Broker.class.getSimpleName(), Collections.<String, Object>singletonMap(Broker.MODEL_VERSION, "1.4"), Collections.singleton(_virtualHostId), _store);
        ConfigurationEntry expectedNewVirtualHost;
        {
            Map<String, Object> expectedNewVirtualHostConfigurationStoreSettings = new HashMap<String, Object>();
            expectedNewVirtualHostConfigurationStoreSettings.put("storeType", "JSON");
            expectedNewVirtualHostConfigurationStoreSettings.put("storePath", "/mystorepath");

            Map<String, Object> expectedNewVirtualHostAttributes = new HashMap<String, Object>();
            expectedNewVirtualHostAttributes.put(VirtualHost.NAME, "test");
            expectedNewVirtualHostAttributes.put(VirtualHost.TYPE, "STANDARD");
            expectedNewVirtualHostAttributes.put(VirtualHost.CONFIGURATION_STORE_SETTINGS, expectedNewVirtualHostConfigurationStoreSettings);

            expectedNewVirtualHost =  new ConfigurationEntry(_virtualHostId, VirtualHost.class.getSimpleName(), expectedNewVirtualHostAttributes, Collections.<UUID>emptySet(), _store);
        }
        verify(_store).save(expectedNewVirtualHost, expectNewRoot);
    }

    public void testUpgrade13To14_BdbHa() throws Exception
    {
        HashMap<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put("name", "test");
        virtualHostAttributes.put("type", "BDB_HA");
        virtualHostAttributes.put("storeType", "BdB-HA");
        virtualHostAttributes.put("storePath", "/mystorepath");
        virtualHostAttributes.put("storeUnderfullSize", 1000);
        virtualHostAttributes.put("storeOverfullSize", 2000);
        virtualHostAttributes.put("haNodeName", "node1");
        virtualHostAttributes.put("haGroupName", "group1");
        virtualHostAttributes.put("haHelperAddress", "helper:1000");
        virtualHostAttributes.put("haCoalescingSync", true);
        virtualHostAttributes.put("haNodeAddress", "nodeaddr:1000");
        virtualHostAttributes.put("haDurability", "sync,sync,all");
        virtualHostAttributes.put("haDesignatedPrimary", true);
        virtualHostAttributes.put("haReplicationConfig", Collections.singletonMap("hasettings", "havalue"));
        virtualHostAttributes.put("bdbEnvironmentConfig", Collections.singletonMap("envsettings", "envvalue"));

        doTest(_store, virtualHostAttributes);

        ConfigurationEntry expectNewRoot = new ConfigurationEntry(_brokerId, Broker.class.getSimpleName(), Collections.<String, Object>singletonMap(Broker.MODEL_VERSION, "1.4"), Collections.singleton(_virtualHostId), _store);
        ConfigurationEntry expectedNewVirtualHost;
        {
            Map<String, Object> expectedNewVirtualHostMessageSettings = new HashMap<String, Object>();
            expectedNewVirtualHostMessageSettings.put("storePath", "/mystorepath");
            expectedNewVirtualHostMessageSettings.put("storeUnderfullSize", 1000);
            expectedNewVirtualHostMessageSettings.put("storeOverfullSize", 2000);
            expectedNewVirtualHostMessageSettings.put("haNodeName", "node1");
            expectedNewVirtualHostMessageSettings.put("haGroupName", "group1");
            expectedNewVirtualHostMessageSettings.put("haHelperAddress", "helper:1000");
            expectedNewVirtualHostMessageSettings.put("haCoalescingSync", true);
            expectedNewVirtualHostMessageSettings.put("haNodeAddress", "nodeaddr:1000");
            expectedNewVirtualHostMessageSettings.put("haDurability", "sync,sync,all");
            expectedNewVirtualHostMessageSettings.put("haDesignatedPrimary", true);
            expectedNewVirtualHostMessageSettings.put("haReplicationConfig", Collections.singletonMap("hasettings", "havalue"));
            expectedNewVirtualHostMessageSettings.put("bdbEnvironmentConfig", Collections.singletonMap("envsettings", "envvalue"));

            Map<String, Object> expectedNewVirtualHostAttributes = new HashMap<String, Object>();
            expectedNewVirtualHostAttributes.put(VirtualHost.NAME, "test");
            expectedNewVirtualHostAttributes.put(VirtualHost.TYPE, "BDB_HA");
            expectedNewVirtualHostAttributes.put(VirtualHost.MESSAGE_STORE_SETTINGS, expectedNewVirtualHostMessageSettings);

            expectedNewVirtualHost =  new ConfigurationEntry(_virtualHostId, VirtualHost.class.getSimpleName(), expectedNewVirtualHostAttributes, Collections.<UUID>emptySet(), _store);
        }
        verify(_store).save(expectedNewVirtualHost, expectNewRoot);
    }

    public void testUpgrade13To14_Bdb() throws Exception
    {
        HashMap<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put("name", "test");
        virtualHostAttributes.put("type", "STANDARD");
        virtualHostAttributes.put("storeType", "BdB");
        virtualHostAttributes.put("storePath", "/mystorepath");
        virtualHostAttributes.put("storeUnderfullSize", 1000);
        virtualHostAttributes.put("storeOverfullSize", 2000);
        virtualHostAttributes.put("bdbEnvironmentConfig", Collections.singletonMap("envsettings", "envvalue"));

        doTest(_store, virtualHostAttributes);

        ConfigurationEntry expectNewRoot = new ConfigurationEntry(_brokerId, Broker.class.getSimpleName(), Collections.<String, Object>singletonMap(Broker.MODEL_VERSION, "1.4"), Collections.singleton(_virtualHostId), _store);
        ConfigurationEntry expectedNewVirtualHost;
        {
            Map<String, Object> expectedNewVirtualHostMessageSettings = new HashMap<String, Object>();
            expectedNewVirtualHostMessageSettings.put("storeType", "BDB");
            expectedNewVirtualHostMessageSettings.put("storePath", "/mystorepath");
            expectedNewVirtualHostMessageSettings.put("storeUnderfullSize", 1000);
            expectedNewVirtualHostMessageSettings.put("storeOverfullSize", 2000);
            expectedNewVirtualHostMessageSettings.put("bdbEnvironmentConfig", Collections.singletonMap("envsettings", "envvalue"));

            Map<String, Object> expectedNewVirtualHostAttributes = new HashMap<String, Object>();
            expectedNewVirtualHostAttributes.put(VirtualHost.NAME, "test");
            expectedNewVirtualHostAttributes.put(VirtualHost.TYPE, "STANDARD");
            expectedNewVirtualHostAttributes.put(VirtualHost.MESSAGE_STORE_SETTINGS, expectedNewVirtualHostMessageSettings);

            expectedNewVirtualHost =  new ConfigurationEntry(_virtualHostId, VirtualHost.class.getSimpleName(), expectedNewVirtualHostAttributes, Collections.<UUID>emptySet(), _store);
        }
        verify(_store).save(expectedNewVirtualHost, expectNewRoot);
    }

    public void testUpgrade13To14_BdbMessageStoreAndConfigurationStore() throws Exception
    {
        HashMap<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put("name", "test");
        virtualHostAttributes.put("type", "STANDARD");
        virtualHostAttributes.put("storeType", "BdB");
        virtualHostAttributes.put("storePath", "/mystorepath");
        virtualHostAttributes.put("storeUnderfullSize", 1000);
        virtualHostAttributes.put("storeOverfullSize", 2000);
        virtualHostAttributes.put("bdbEnvironmentConfig", Collections.singletonMap("envsettings", "envvalue"));
        virtualHostAttributes.put("configStoreType", "BdB");
        virtualHostAttributes.put("configStorePath", "/mystorepath2");

        doTest(_store, virtualHostAttributes);

        ConfigurationEntry expectNewRoot = new ConfigurationEntry(_brokerId, Broker.class.getSimpleName(), Collections.<String, Object>singletonMap(Broker.MODEL_VERSION, "1.4"), Collections.singleton(_virtualHostId), _store);
        ConfigurationEntry expectedNewVirtualHost;
        {
            Map<String, Object> expectedNewVirtualHostMessageSettings = new HashMap<String, Object>();
            expectedNewVirtualHostMessageSettings.put("storeType", "BDB");
            expectedNewVirtualHostMessageSettings.put("storePath", "/mystorepath");
            expectedNewVirtualHostMessageSettings.put("storeUnderfullSize", 1000);
            expectedNewVirtualHostMessageSettings.put("storeOverfullSize", 2000);
            expectedNewVirtualHostMessageSettings.put("bdbEnvironmentConfig", Collections.singletonMap("envsettings", "envvalue"));

            Map<String, Object> expectedNewVirtualHostConfigurationSettings = new HashMap<String, Object>();
            expectedNewVirtualHostConfigurationSettings.put("storeType", "BDB");
            expectedNewVirtualHostConfigurationSettings.put("storePath", "/mystorepath2");
            expectedNewVirtualHostConfigurationSettings.put("bdbEnvironmentConfig", Collections.singletonMap("envsettings", "envvalue"));

            Map<String, Object> expectedNewVirtualHostAttributes = new HashMap<String, Object>();
            expectedNewVirtualHostAttributes.put(VirtualHost.NAME, "test");
            expectedNewVirtualHostAttributes.put(VirtualHost.TYPE, "STANDARD");
            expectedNewVirtualHostAttributes.put(VirtualHost.MESSAGE_STORE_SETTINGS, expectedNewVirtualHostMessageSettings);
            expectedNewVirtualHostAttributes.put(VirtualHost.CONFIGURATION_STORE_SETTINGS, expectedNewVirtualHostConfigurationSettings);

            expectedNewVirtualHost =  new ConfigurationEntry(_virtualHostId, VirtualHost.class.getSimpleName(), expectedNewVirtualHostAttributes, Collections.<UUID>emptySet(), _store);
        }
        verify(_store).save(expectedNewVirtualHost, expectNewRoot);
    }

    public void testUpgrade13To14_JDBC() throws Exception
    {
        HashMap<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put("name", "test");
        virtualHostAttributes.put("type", "STANDARD");
        virtualHostAttributes.put("storeType", "JdBC");
        virtualHostAttributes.put("connectionURL", "jdbc:test");
        virtualHostAttributes.put("connectionPool", "BONECP");
        virtualHostAttributes.put("jdbcBigIntType", "NUMBER");
        virtualHostAttributes.put("jdbcBytesForBlob", true);
        virtualHostAttributes.put("jdbcVarbinaryType", "TEST");
        virtualHostAttributes.put("jdbcBlobType", "BLOB");
        virtualHostAttributes.put("partitionCount", 10);
        virtualHostAttributes.put("maxConnectionsPerPartition", 8);
        virtualHostAttributes.put("minConnectionsPerPartition", 2);

        doTest(_store, virtualHostAttributes);

        ConfigurationEntry expectNewRoot = new ConfigurationEntry(_brokerId, Broker.class.getSimpleName(), Collections.<String, Object>singletonMap(Broker.MODEL_VERSION, "1.4"), Collections.singleton(_virtualHostId), _store);
        ConfigurationEntry expectedNewVirtualHost;
        {
            Map<String, Object> expectedNewVirtualHostMessageSettings = new HashMap<String, Object>();
            expectedNewVirtualHostMessageSettings.put("storeType", "JDBC");
            expectedNewVirtualHostMessageSettings.put("connectionURL", "jdbc:test");
            expectedNewVirtualHostMessageSettings.put("connectionPool", "BONECP");
            expectedNewVirtualHostMessageSettings.put("jdbcBigIntType", "NUMBER");
            expectedNewVirtualHostMessageSettings.put("jdbcBytesForBlob", true);
            expectedNewVirtualHostMessageSettings.put("jdbcVarbinaryType", "TEST");
            expectedNewVirtualHostMessageSettings.put("jdbcBlobType", "BLOB");
            expectedNewVirtualHostMessageSettings.put("partitionCount", 10);
            expectedNewVirtualHostMessageSettings.put("maxConnectionsPerPartition", 8);
            expectedNewVirtualHostMessageSettings.put("minConnectionsPerPartition", 2);

            Map<String, Object> expectedNewVirtualHostAttributes = new HashMap<String, Object>();
            expectedNewVirtualHostAttributes.put(VirtualHost.NAME, "test");
            expectedNewVirtualHostAttributes.put(VirtualHost.TYPE, "STANDARD");
            expectedNewVirtualHostAttributes.put(VirtualHost.MESSAGE_STORE_SETTINGS, expectedNewVirtualHostMessageSettings);

            expectedNewVirtualHost =  new ConfigurationEntry(_virtualHostId, VirtualHost.class.getSimpleName(), expectedNewVirtualHostAttributes, Collections.<UUID>emptySet(), _store);
        }
        verify(_store).save(expectedNewVirtualHost, expectNewRoot);
    }

    public void testUpgrade13To14_JDBC_withStorePath() throws Exception
    {
        HashMap<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put("name", "test");
        virtualHostAttributes.put("type", "STANDARD");
        virtualHostAttributes.put("storeType", "JdBC");
        virtualHostAttributes.put("storePath", "jdbc:test");

        doTest(_store, virtualHostAttributes);

        ConfigurationEntry expectNewRoot = new ConfigurationEntry(_brokerId, Broker.class.getSimpleName(), Collections.<String, Object>singletonMap(Broker.MODEL_VERSION, "1.4"), Collections.singleton(_virtualHostId), _store);
        ConfigurationEntry expectedNewVirtualHost;
        {
            Map<String, Object> expectedNewVirtualHostMessageSettings = new HashMap<String, Object>();
            expectedNewVirtualHostMessageSettings.put("storeType", "JDBC");
            expectedNewVirtualHostMessageSettings.put("connectionURL", "jdbc:test");

            Map<String, Object> expectedNewVirtualHostAttributes = new HashMap<String, Object>();
            expectedNewVirtualHostAttributes.put(VirtualHost.NAME, "test");
            expectedNewVirtualHostAttributes.put(VirtualHost.TYPE, "STANDARD");
            expectedNewVirtualHostAttributes.put(VirtualHost.MESSAGE_STORE_SETTINGS, expectedNewVirtualHostMessageSettings);

            expectedNewVirtualHost =  new ConfigurationEntry(_virtualHostId, VirtualHost.class.getSimpleName(), expectedNewVirtualHostAttributes, Collections.<UUID>emptySet(), _store);
        }
        verify(_store).save(expectedNewVirtualHost, expectNewRoot);
    }

    public void testUpgrade13To14_JDBCConfigurationStoreAndMessageStore() throws Exception
    {
        HashMap<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put("name", "test");
        virtualHostAttributes.put("type", "STANDARD");
        virtualHostAttributes.put("storeType", "JdBC");
        virtualHostAttributes.put("connectionURL", "jdbc:test");
        virtualHostAttributes.put("connectionPool", "BONECP");
        virtualHostAttributes.put("jdbcBigIntType", "NUMBER");
        virtualHostAttributes.put("jdbcBytesForBlob", true);
        virtualHostAttributes.put("jdbcVarbinaryType", "TEST");
        virtualHostAttributes.put("jdbcBlobType", "BLOB");
        virtualHostAttributes.put("partitionCount", 10);
        virtualHostAttributes.put("maxConnectionsPerPartition", 8);
        virtualHostAttributes.put("minConnectionsPerPartition", 2);
        virtualHostAttributes.put("configStoreType", "JdBC");
        virtualHostAttributes.put("configConnectionURL", "jdbc:test2");

        doTest(_store, virtualHostAttributes);

        ConfigurationEntry expectNewRoot = new ConfigurationEntry(_brokerId, Broker.class.getSimpleName(), Collections.<String, Object>singletonMap(Broker.MODEL_VERSION, "1.4"), Collections.singleton(_virtualHostId), _store);
        ConfigurationEntry expectedNewVirtualHost;
        {
            Map<String, Object> expectedNewVirtualHostMessageSettings = new HashMap<String, Object>();
            expectedNewVirtualHostMessageSettings.put("storeType", "JDBC");
            expectedNewVirtualHostMessageSettings.put("connectionURL", "jdbc:test");
            expectedNewVirtualHostMessageSettings.put("connectionPool", "BONECP");
            expectedNewVirtualHostMessageSettings.put("jdbcBigIntType", "NUMBER");
            expectedNewVirtualHostMessageSettings.put("jdbcBytesForBlob", true);
            expectedNewVirtualHostMessageSettings.put("jdbcVarbinaryType", "TEST");
            expectedNewVirtualHostMessageSettings.put("jdbcBlobType", "BLOB");
            expectedNewVirtualHostMessageSettings.put("partitionCount", 10);
            expectedNewVirtualHostMessageSettings.put("maxConnectionsPerPartition", 8);
            expectedNewVirtualHostMessageSettings.put("minConnectionsPerPartition", 2);

            Map<String, Object> expectedNewVirtualHostConfigurationSettings = new HashMap<String, Object>();
            expectedNewVirtualHostConfigurationSettings.put("storeType", "JDBC");
            expectedNewVirtualHostConfigurationSettings.put("connectionURL", "jdbc:test2");
            expectedNewVirtualHostConfigurationSettings.put("connectionPool", "BONECP");
            expectedNewVirtualHostConfigurationSettings.put("jdbcBigIntType", "NUMBER");
            expectedNewVirtualHostConfigurationSettings.put("jdbcBytesForBlob", true);
            expectedNewVirtualHostConfigurationSettings.put("jdbcVarbinaryType", "TEST");
            expectedNewVirtualHostConfigurationSettings.put("jdbcBlobType", "BLOB");
            expectedNewVirtualHostConfigurationSettings.put("partitionCount", 10);
            expectedNewVirtualHostConfigurationSettings.put("maxConnectionsPerPartition", 8);
            expectedNewVirtualHostConfigurationSettings.put("minConnectionsPerPartition", 2);

            Map<String, Object> expectedNewVirtualHostAttributes = new HashMap<String, Object>();
            expectedNewVirtualHostAttributes.put(VirtualHost.NAME, "test");
            expectedNewVirtualHostAttributes.put(VirtualHost.TYPE, "STANDARD");
            expectedNewVirtualHostAttributes.put(VirtualHost.MESSAGE_STORE_SETTINGS, expectedNewVirtualHostMessageSettings);
            expectedNewVirtualHostAttributes.put(VirtualHost.CONFIGURATION_STORE_SETTINGS, expectedNewVirtualHostConfigurationSettings);

            expectedNewVirtualHost =  new ConfigurationEntry(_virtualHostId, VirtualHost.class.getSimpleName(), expectedNewVirtualHostAttributes, Collections.<UUID>emptySet(), _store);
        }
        verify(_store).save(expectedNewVirtualHost, expectNewRoot);
    }

    private void doTest(ConfigurationEntryStore store, Map<String,Object> virtualHostAttributes)
    {
        final ConfigurationEntry virtualHostEntry = new ConfigurationEntry(_virtualHostId, VirtualHost.class.getSimpleName(), virtualHostAttributes, Collections.<UUID>emptySet(), store);

        final ConfigurationEntry rootEntry;
        {
            Map<String, Object> rootEntryAttributes = Collections.<String, Object>singletonMap(Broker.MODEL_VERSION, "1.3");
            rootEntry = new ConfigurationEntry(_brokerId, Broker.class.getSimpleName(), rootEntryAttributes, Collections.singleton(_virtualHostId), store);
        }

        when(store.getRootEntry()).thenReturn(rootEntry);
        when(store.getEntry(_virtualHostId)).thenReturn(virtualHostEntry);

        StoreUpgrader.UPGRADE_1_3.doUpgrade(store);
    }

}
