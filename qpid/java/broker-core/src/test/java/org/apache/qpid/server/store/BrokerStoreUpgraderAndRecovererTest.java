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
package org.apache.qpid.server.store;

import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.SystemContext;
import org.apache.qpid.server.model.SystemContextImpl;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;
import org.apache.qpid.test.utils.QpidTestCase;


public class BrokerStoreUpgraderAndRecovererTest extends QpidTestCase
{
    private ConfiguredObjectRecord _brokerRecord;
    private CurrentThreadTaskExecutor _taskExecutor;
    private SystemContext<?> _systemContext;

    public void setUp() throws Exception
    {
        super.setUp();
        Map<String, Object> brokerAttributes = new HashMap<>();
        brokerAttributes.put("createdTime", 1401385808828l);
        brokerAttributes.put("defaultVirtualHost", "test");
        brokerAttributes.put("modelVersion", "1.3");
        brokerAttributes.put("name", "Broker");

        _brokerRecord = new ConfiguredObjectRecordImpl(UUID.randomUUID(), "Broker", brokerAttributes);
        _taskExecutor = new CurrentThreadTaskExecutor();
        _taskExecutor.start();
        _systemContext = new SystemContextImpl(_taskExecutor,
                                               mock(EventLogger.class),
                                               mock(LogRecorder.class),
                                               mock(BrokerOptions.class));
    }

    public void testUpgradeVirtualHostWithJDBCStore()
    {
        Map<String, Object> hostAttributes = new HashMap<>();
        hostAttributes.put("name", "test");
        hostAttributes.put("modelVersion", "0.4");
        hostAttributes.put("connectionPool", "BONECP");
        hostAttributes.put("connectionURL", "jdbc:derby://localhost:1527/tmp/vh/test;create=true");
        hostAttributes.put("createdBy", "webadmin");
        hostAttributes.put("createdTime", 1401385905260l);
        hostAttributes.put("maxConnectionsPerPartition", 7);
        hostAttributes.put("minConnectionsPerPartition", 6);
        hostAttributes.put("partitionCount", 2);
        hostAttributes.put("storeType", "jdbc");
        hostAttributes.put("type", "STANDARD");
        hostAttributes.put("jdbcBigIntType", "mybigint");
        hostAttributes.put("jdbcBlobType", "myblob");
        hostAttributes.put("jdbcVarbinaryType", "myvarbinary");
        hostAttributes.put("jdbcBytesForBlob", true);


        ConfiguredObjectRecord virtualHostRecord = new ConfiguredObjectRecordImpl(UUID.randomUUID(), "VirtualHost",
                hostAttributes, Collections.<String,ConfiguredObjectRecord>singletonMap("Broker", _brokerRecord));
        DurableConfigurationStore dcs = new DurableConfigurationStoreStub(_brokerRecord, virtualHostRecord);

        BrokerStoreUpgraderAndRecoverer recoverer = new BrokerStoreUpgraderAndRecoverer(_systemContext);
        List<ConfiguredObjectRecord> records = recoverer.upgrade(dcs);

        ConfiguredObjectRecord upgradedVirtualHostNodeRecord = findRecordById(virtualHostRecord.getId(), records);
        assertEquals("Unexpected type", "VirtualHostNode", upgradedVirtualHostNodeRecord.getType());
        Map<String,Object> expectedAttributes = new HashMap<>();
        expectedAttributes.put("connectionPoolType", "BONECP");
        expectedAttributes.put("connectionUrl", "jdbc:derby://localhost:1527/tmp/vh/test;create=true");
        expectedAttributes.put("createdBy", "webadmin");
        expectedAttributes.put("createdTime", 1401385905260l);
        expectedAttributes.put("name", "test");
        expectedAttributes.put("type", "JDBC");

        final Map<String, Object> context = new HashMap<>();
        context.put("qpid.jdbcstore.bigIntType", "mybigint");
        context.put("qpid.jdbcstore.varBinaryType", "myvarbinary");
        context.put("qpid.jdbcstore.blobType", "myblob");
        context.put("qpid.jdbcstore.useBytesForBlob", true);

        context.put("qpid.jdbcstore.bonecp.maxConnectionsPerPartition", 7);
        context.put("qpid.jdbcstore.bonecp.minConnectionsPerPartition", 6);
        context.put("qpid.jdbcstore.bonecp.partitionCount", 2);
        expectedAttributes.put("context", context);

        assertEquals("Unexpected attributes", expectedAttributes, upgradedVirtualHostNodeRecord.getAttributes());
    }

    public void testUpgradeVirtualHostWithDerbyStore()
    {
        Map<String, Object> hostAttributes = new HashMap<>();
        hostAttributes.put("name", "test");
        hostAttributes.put("modelVersion", "0.4");
        hostAttributes.put("storePath", "/tmp/vh/derby");
        hostAttributes.put("storeType", "derby");
        hostAttributes.put("createdBy", "webadmin");
        hostAttributes.put("createdTime", 1401385905260l);
        hostAttributes.put("type", "STANDARD");

        ConfiguredObjectRecord virtualHostRecord = new ConfiguredObjectRecordImpl(UUID.randomUUID(), "VirtualHost",
                hostAttributes, Collections.<String,ConfiguredObjectRecord>singletonMap("Broker", _brokerRecord));
        DurableConfigurationStore dcs = new DurableConfigurationStoreStub(_brokerRecord, virtualHostRecord);

        BrokerStoreUpgraderAndRecoverer recoverer = new BrokerStoreUpgraderAndRecoverer(_systemContext);
        List<ConfiguredObjectRecord> records = recoverer.upgrade(dcs);

        ConfiguredObjectRecord upgradedVirtualHostNodeRecord = findRecordById(virtualHostRecord.getId(), records);
        assertEquals("Unexpected type", "VirtualHostNode", upgradedVirtualHostNodeRecord.getType());
        Map<String,Object> expectedAttributes = new HashMap<>();
        expectedAttributes.put("storePath", "/tmp/vh/derby");
        expectedAttributes.put("createdBy", "webadmin");
        expectedAttributes.put("createdTime", 1401385905260l);
        expectedAttributes.put("name", "test");
        expectedAttributes.put("type", "DERBY");
        assertEquals("Unexpected attributes", expectedAttributes, upgradedVirtualHostNodeRecord.getAttributes());
    }

    public void testUpgradeVirtualHostWithBDBStore()
    {
        Map<String, Object> hostAttributes = new HashMap<>();
        hostAttributes.put("name", "test");
        hostAttributes.put("modelVersion", "0.4");
        hostAttributes.put("storePath", "/tmp/vh/bdb");
        hostAttributes.put("storeType", "bdb");
        hostAttributes.put("createdBy", "webadmin");
        hostAttributes.put("createdTime", 1401385905260l);
        hostAttributes.put("type", "STANDARD");
        hostAttributes.put("bdbEnvironmentConfig", Collections.singletonMap("je.stats.collect", "false"));


        ConfiguredObjectRecord virtualHostRecord = new ConfiguredObjectRecordImpl(UUID.randomUUID(), "VirtualHost",
                hostAttributes, Collections.<String,ConfiguredObjectRecord>singletonMap("Broker", _brokerRecord));
        DurableConfigurationStore dcs = new DurableConfigurationStoreStub(_brokerRecord, virtualHostRecord);

        BrokerStoreUpgraderAndRecoverer recoverer = new BrokerStoreUpgraderAndRecoverer(_systemContext);
        List<ConfiguredObjectRecord> records = recoverer.upgrade(dcs);

        ConfiguredObjectRecord upgradedVirtualHostNodeRecord = findRecordById(virtualHostRecord.getId(), records);
        assertEquals("Unexpected type", "VirtualHostNode", upgradedVirtualHostNodeRecord.getType());
        Map<String,Object> expectedAttributes = new HashMap<>();
        expectedAttributes.put("storePath", "/tmp/vh/bdb");
        expectedAttributes.put("createdBy", "webadmin");
        expectedAttributes.put("createdTime", 1401385905260l);
        expectedAttributes.put("name", "test");
        expectedAttributes.put("type", "BDB");
        expectedAttributes.put("context", Collections.singletonMap("je.stats.collect", "false"));
        assertEquals("Unexpected attributes", expectedAttributes, upgradedVirtualHostNodeRecord.getAttributes());
    }

    public void testUpgradeVirtualHostWithBDBHAStore()
    {
        Map<String, Object> hostAttributes = new HashMap<>();
        hostAttributes.put("name", "test");
        hostAttributes.put("modelVersion", "0.4");
        hostAttributes.put("createdBy", "webadmin");
        hostAttributes.put("createdTime", 1401385905260l);
        hostAttributes.put("type", "BDB_HA");
        hostAttributes.put("storePath", "/tmp/vh/bdbha");
        hostAttributes.put("haCoalescingSync", "true");
        hostAttributes.put("haDesignatedPrimary", "true");
        hostAttributes.put("haGroupName", "ha");
        hostAttributes.put("haHelperAddress", "localhost:7000");
        hostAttributes.put("haNodeAddress", "localhost:7000");
        hostAttributes.put("haNodeName", "n1");
        hostAttributes.put("haReplicationConfig", Collections.singletonMap("je.stats.collect", "false"));
        hostAttributes.put("bdbEnvironmentConfig", Collections.singletonMap("je.rep.feederTimeout", "1 m"));


        ConfiguredObjectRecord virtualHostRecord = new ConfiguredObjectRecordImpl(UUID.randomUUID(), "VirtualHost",
                hostAttributes, Collections.<String,ConfiguredObjectRecord>singletonMap("Broker", _brokerRecord));
        DurableConfigurationStore dcs = new DurableConfigurationStoreStub(_brokerRecord, virtualHostRecord);

        BrokerStoreUpgraderAndRecoverer recoverer = new BrokerStoreUpgraderAndRecoverer(_systemContext);
        List<ConfiguredObjectRecord> records = recoverer.upgrade(dcs);

        ConfiguredObjectRecord upgradedVirtualHostNodeRecord = findRecordById(virtualHostRecord.getId(), records);
        assertEquals("Unexpected type", "VirtualHostNode", upgradedVirtualHostNodeRecord.getType());
        Map<String,Object> expectedContext = new HashMap<>();
        expectedContext.put("je.stats.collect", "false");
        expectedContext.put("je.rep.feederTimeout", "1 m");

        Map<String,Object> expectedAttributes = new HashMap<>();
        expectedAttributes.put("createdBy", "webadmin");
        expectedAttributes.put("createdTime", 1401385905260l);
        expectedAttributes.put("type", "BDB_HA");
        expectedAttributes.put("storePath", "/tmp/vh/bdbha");
        expectedAttributes.put("designatedPrimary", "true");
        expectedAttributes.put("groupName", "ha");
        expectedAttributes.put("address", "localhost:7000");
        expectedAttributes.put("helperAddress", "localhost:7000");
        expectedAttributes.put("name", "n1");
        expectedAttributes.put("context", expectedContext);

        assertEquals("Unexpected attributes", expectedAttributes, upgradedVirtualHostNodeRecord.getAttributes());
    }

    public void testUpgradeVirtualHostWithMemoryStore()
    {
        Map<String, Object> hostAttributes = new HashMap<>();
        hostAttributes.put("name", "test");
        hostAttributes.put("modelVersion", "0.4");
        hostAttributes.put("storeType", "memory");
        hostAttributes.put("createdBy", "webadmin");
        hostAttributes.put("createdTime", 1401385905260l);
        hostAttributes.put("type", "STANDARD");

        ConfiguredObjectRecord virtualHostRecord = new ConfiguredObjectRecordImpl(UUID.randomUUID(), "VirtualHost",
                hostAttributes, Collections.<String,ConfiguredObjectRecord>singletonMap("Broker", _brokerRecord));
        DurableConfigurationStore dcs = new DurableConfigurationStoreStub(_brokerRecord, virtualHostRecord);

        BrokerStoreUpgraderAndRecoverer recoverer = new BrokerStoreUpgraderAndRecoverer(_systemContext);
        List<ConfiguredObjectRecord> records = recoverer.upgrade(dcs);

        ConfiguredObjectRecord upgradedVirtualHostNodeRecord = findRecordById(virtualHostRecord.getId(), records);
        assertEquals("Unexpected type", "VirtualHostNode", upgradedVirtualHostNodeRecord.getType());
        Map<String,Object> expectedAttributes = new HashMap<>();
        expectedAttributes.put("createdBy", "webadmin");
        expectedAttributes.put("createdTime", 1401385905260l);
        expectedAttributes.put("name", "test");
        expectedAttributes.put("type", "Memory");
        assertEquals("Unexpected attributes", expectedAttributes, upgradedVirtualHostNodeRecord.getAttributes());
    }

    private ConfiguredObjectRecord findRecordById(UUID id, List<ConfiguredObjectRecord> records)
    {
        for (ConfiguredObjectRecord configuredObjectRecord : records)
        {
            if (configuredObjectRecord.getId().equals(id))
            {
                return configuredObjectRecord;
            }
        }
        return null;
    }

    class DurableConfigurationStoreStub implements DurableConfigurationStore
    {
        private ConfiguredObjectRecord[] records;

        public DurableConfigurationStoreStub(ConfiguredObjectRecord... records)
        {
            super();
            this.records = records;
        }

        @Override
        public void openConfigurationStore(ConfiguredObject<?> parent, Map<String, Object> storeSettings) throws StoreException
        {
        }

        @Override
        public void upgradeStoreStructure() throws StoreException
        {

        }

        @Override
        public void create(ConfiguredObjectRecord object) throws StoreException
        {
        }

        @Override
        public UUID[] remove(ConfiguredObjectRecord... objects) throws StoreException
        {
            return null;
        }

        @Override
        public void update(boolean createIfNecessary, ConfiguredObjectRecord... records) throws StoreException
        {
        }

        @Override
        public void closeConfigurationStore() throws StoreException
        {
        }

        @Override
        public void onDelete()
        {
        }

        @Override
        public void visitConfiguredObjectRecords(ConfiguredObjectRecordHandler handler) throws StoreException
        {
            handler.begin();
            for (ConfiguredObjectRecord record : records)
            {
                handler.handle(record);
            }
            handler.end();
        }
    }
}
