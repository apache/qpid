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
package org.apache.qpid.server.store.berkeleydb.upgrade;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.util.MapJsonSerializer;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Transaction;

public class UpgradeFrom7To8Test extends AbstractUpgradeTestCase
{
    private static final String ARGUMENTS = "arguments";

    private static final String CONFIGURED_OBJECTS_DB_NAME = "CONFIGURED_OBJECTS";
    private static final String CONFIGURED_OBJECT_HIERARCHY_DB_NAME = "CONFIGURED_OBJECT_HIERARCHY";

    @Override
    public VirtualHost<?,?,?> getVirtualHost()
    {
        VirtualHost<?,?,?> virtualHost = mock(VirtualHost.class);
        when(virtualHost.getName()).thenReturn("test");
        return virtualHost;
    }

    @Override
    protected String getStoreDirectoryName()
    {
        return "bdbstore-v7";
    }

    public void testPerformUpgrade() throws Exception
    {
        UpgradeFrom7To8 upgrade = new UpgradeFrom7To8();
        upgrade.performUpgrade(_environment, UpgradeInteractionHandler.DEFAULT_HANDLER, getVirtualHost());

        assertDatabaseRecordCount(CONFIGURED_OBJECTS_DB_NAME, 7);
        assertDatabaseRecordCount(CONFIGURED_OBJECT_HIERARCHY_DB_NAME, 9);

        assertConfiguredObjects();
        assertConfiguredObjectHierarchy();
    }


    private void assertConfiguredObjectHierarchy()
    {
        Map<UpgradeHierarchyKey, UUID> hierarchy = loadConfiguredObjectHierarchy();
        assertEquals("Unexpected number of configured objects", 9, hierarchy.size());

        UUID vhUuid = UUIDGenerator.generateVhostUUID(getVirtualHost().getName());
        UUID myExchUuid = UUIDGenerator.generateExchangeUUID("myexch", getVirtualHost().getName());
        UUID amqDirectUuid = UUIDGenerator.generateExchangeUUID("amq.direct", getVirtualHost().getName());
        UUID queue1Uuid = UUIDGenerator.generateQueueUUID("queue1", getVirtualHost().getName());
        UUID queue1ToAmqDirectBindingUuid = UUIDGenerator.generateBindingUUID("amq.direct", "queue1", "queue1", getVirtualHost().getName());

        // myexch -> virtualhost
        UpgradeHierarchyKey myExchToVhParent = new UpgradeHierarchyKey(myExchUuid, VirtualHost.class.getSimpleName());
        assertExpectedHierarchyEntry(hierarchy, myExchToVhParent, vhUuid);

        // queue1 -> virtualhost
        UpgradeHierarchyKey queue1ToVhParent = new UpgradeHierarchyKey(queue1Uuid, VirtualHost.class.getSimpleName());
        assertExpectedHierarchyEntry(hierarchy, queue1ToVhParent, vhUuid);

        // ! amq.direct -> virtualhost  (This will change when the upgrader is changed to create the default exchanges)
        UpgradeHierarchyKey amqDirectToVhParent = new UpgradeHierarchyKey(amqDirectUuid, VirtualHost.class.getSimpleName());
        assertFalse("amq.direct should not have a binding to virtualhost", hierarchy.containsKey(amqDirectToVhParent));

        // queue1binding -> amq.direct
        // queue1binding -> queue1
        UpgradeHierarchyKey queue1BindingToAmqDirect = new UpgradeHierarchyKey(queue1ToAmqDirectBindingUuid, Exchange.class.getSimpleName());
        UpgradeHierarchyKey queue1BindingToQueue1 = new UpgradeHierarchyKey(queue1ToAmqDirectBindingUuid, Queue.class.getSimpleName());
        assertExpectedHierarchyEntry(hierarchy, queue1BindingToAmqDirect, amqDirectUuid);
        assertExpectedHierarchyEntry(hierarchy, queue1BindingToQueue1, queue1Uuid);
    }

    private void assertExpectedHierarchyEntry(
            Map<UpgradeHierarchyKey, UUID> hierarchy,
            UpgradeHierarchyKey childHierarchyKey, UUID parentUUID)
    {
        assertTrue("Expected hierarchy entry does not exist", hierarchy.containsKey(childHierarchyKey));
        assertEquals("Expected hierarchy entry does not exist", parentUUID, hierarchy.get(childHierarchyKey));
    }


    private void assertConfiguredObjects()
    {
        Map<UUID, UpgradeConfiguredObjectRecord> configuredObjects = loadConfiguredObjects();
        assertEquals("Unexpected number of configured objects", 7, configuredObjects.size());

        Map<UUID, Map<String, Object>> expected = new HashMap<UUID, Map<String, Object>>();

        String configVersion = "0.3";
        expected.putAll(createExpectedVirtualHost(configVersion));

        expected.putAll(createExpectedQueue("queue1", Boolean.FALSE, null, null));
        expected.putAll(createExpectedQueue("queue2", Boolean.FALSE, null, null));

        expected.putAll(createExpectedExchangeMap("myexch", "direct"));

        expected.putAll(createExpectedBindingMap("queue1", "queue1", "amq.direct", null));
        expected.putAll(createExpectedBindingMap("queue1", "queue1", "myexch", null));
        expected.putAll(createExpectedBindingMap("queue2", "queue2", "amq.fanout", null));

        MapJsonSerializer jsonSerializer = new MapJsonSerializer();
        for (Entry<UUID, UpgradeConfiguredObjectRecord> entry : configuredObjects.entrySet())
        {
            UpgradeConfiguredObjectRecord object = entry.getValue();

            UUID actualKey = entry.getKey();
            String actualType = object.getType();
            String actualJson = object.getAttributes();
            Map<String, Object> actualDeserializedAttributes = jsonSerializer.deserialize(actualJson);

            assertTrue("Entry UUID " + actualKey + " of type " +  actualType + " is unexpected", expected.containsKey(actualKey));

            Map<String, Object> expectedDeserializedAttributes = expected.get(actualKey);

            assertEquals("Entry UUID " + actualKey + " of type " +  actualType + " has uenxpected deserialised value, json was: " + actualJson,
                         expectedDeserializedAttributes, actualDeserializedAttributes);
        }
    }

    private Map<UUID, Map<String, Object>> createExpectedVirtualHost(String modelVersion)
    {
        Map<String, Object> expectedVirtualHostEntry = new HashMap<String, Object>();
        expectedVirtualHostEntry.put("modelVersion", modelVersion);
        expectedVirtualHostEntry.put(VirtualHost.NAME, getVirtualHost().getName());

        UUID expectedUUID = UUIDGenerator.generateVhostUUID(getVirtualHost().getName());
        return Collections.singletonMap(expectedUUID, expectedVirtualHostEntry);
    }

    private Map<UUID, Map<String, Object>> createExpectedQueue(String queueName, boolean exclusiveFlag, String owner, Map<String, Object> argumentMap)
    {
        Map<String, Object> expectedQueueEntry = new HashMap<String, Object>();
        expectedQueueEntry.put(Queue.NAME, queueName);
        expectedQueueEntry.put(Queue.EXCLUSIVE, exclusiveFlag);
        expectedQueueEntry.put(Queue.OWNER, owner);
        expectedQueueEntry.put(Queue.TYPE, "standard");

        if (argumentMap != null)
        {
            expectedQueueEntry.put(ARGUMENTS, argumentMap);
        }
        UUID expectedUUID = UUIDGenerator.generateQueueUUID(queueName, getVirtualHost().getName());
        return Collections.singletonMap(expectedUUID, expectedQueueEntry);
    }

    private Map<UUID, Map<String, Object>> createExpectedExchangeMap(String exchangeName, String type)
    {
        Map<String, Object> expectedExchangeMap = new HashMap<String, Object>();
        expectedExchangeMap.put(Exchange.NAME, exchangeName);
        expectedExchangeMap.put(Exchange.TYPE, type);
        expectedExchangeMap.put(Exchange.LIFETIME_POLICY, LifetimePolicy.PERMANENT.name());
        UUID expectedUUID = UUIDGenerator.generateExchangeUUID(exchangeName, getVirtualHost().getName());
        return Collections.singletonMap(expectedUUID, expectedExchangeMap);
    }

    private Map<UUID, Map<String, Object>> createExpectedBindingMap(String queueName, String bindingName, String exchangeName, Map<String, String> argumentMap)
    {
        Map<String, Object> expectedBinding = new HashMap<String, Object>();
        expectedBinding.put(Binding.NAME, bindingName);
        expectedBinding.put(Binding.ARGUMENTS, argumentMap == null ? Collections.emptyMap() : argumentMap);

        UUID expectedUUID = UUIDGenerator.generateBindingUUID(exchangeName, queueName, bindingName, getVirtualHost().getName());
        return Collections.singletonMap(expectedUUID, expectedBinding);
    }

    private Map<UUID, UpgradeConfiguredObjectRecord> loadConfiguredObjects()
    {
        final Map<UUID, UpgradeConfiguredObjectRecord> configuredObjectsRecords = new HashMap<UUID, UpgradeConfiguredObjectRecord>();
        final UpgradeConfiguredObjectBinding binding = new UpgradeConfiguredObjectBinding();
        final UpgradeUUIDBinding uuidBinding = new UpgradeUUIDBinding();
        CursorOperation configuredObjectsCursor = new CursorOperation()
        {
            @Override
            public void processEntry(Database sourceDatabase, Database targetDatabase, Transaction transaction,
                    DatabaseEntry key, DatabaseEntry value)
            {
                UUID id = uuidBinding.entryToObject(key);
                UpgradeConfiguredObjectRecord object = binding.entryToObject(value);
                configuredObjectsRecords.put(id, object);
            }
        };
        new DatabaseTemplate(_environment, CONFIGURED_OBJECTS_DB_NAME, null).run(configuredObjectsCursor);
        return configuredObjectsRecords;
    }


    private Map<UpgradeHierarchyKey, UUID> loadConfiguredObjectHierarchy()
    {
        final Map<UpgradeHierarchyKey, UUID> hierarchyRecords = new HashMap<UpgradeHierarchyKey, UUID>();
        final UpgradeHierarchyKeyBinding hierarchyKeyBinding = new UpgradeHierarchyKeyBinding();
        final UpgradeUUIDBinding uuidParentBinding = new UpgradeUUIDBinding();
        CursorOperation hierarchyCursor = new CursorOperation()
        {
            @Override
            public void processEntry(Database sourceDatabase, Database targetDatabase, Transaction transaction,
                    DatabaseEntry key, DatabaseEntry value)
            {
                UpgradeHierarchyKey hierarchyKey = hierarchyKeyBinding.entryToObject(key);
                UUID parentId = uuidParentBinding.entryToObject(value);
                hierarchyRecords.put(hierarchyKey, parentId);
            }
        };
        new DatabaseTemplate(_environment, CONFIGURED_OBJECT_HIERARCHY_DB_NAME, null).run(hierarchyCursor);
        return hierarchyRecords;
    }

    private static class UpgradeConfiguredObjectBinding extends TupleBinding<UpgradeConfiguredObjectRecord>
    {
        @Override
        public UpgradeConfiguredObjectRecord entryToObject(TupleInput tupleInput)
        {
            String type = tupleInput.readString();
            String json = tupleInput.readString();
            UpgradeConfiguredObjectRecord configuredObject = new UpgradeConfiguredObjectRecord(type, json);
            return configuredObject;
        }

        @Override
        public void objectToEntry(UpgradeConfiguredObjectRecord object, TupleOutput tupleOutput)
        {
            throw new UnsupportedOperationException();
        }
    }

    private static class UpgradeConfiguredObjectRecord
    {
        private final String _attributes;
        private final String _type;

        public UpgradeConfiguredObjectRecord(String type, String attributes)
        {
            super();
            _attributes = attributes;
            _type = type;
        }

        public String getAttributes()
        {
            return _attributes;
        }

        public String getType()
        {
            return _type;
        }

    }

    private static class UpgradeUUIDBinding extends TupleBinding<UUID>
    {
        @Override
        public UUID entryToObject(final TupleInput tupleInput)
        {
            return new UUID(tupleInput.readLong(), tupleInput.readLong());
        }

        @Override
        public void objectToEntry(final UUID uuid, final TupleOutput tupleOutput)
        {
            throw new UnsupportedOperationException();
        }
    }

    private static class UpgradeHierarchyKeyBinding extends TupleBinding<UpgradeHierarchyKey>
    {
        @Override
        public UpgradeHierarchyKey entryToObject(TupleInput tupleInput)
        {
            UUID childId = new UUID(tupleInput.readLong(), tupleInput.readLong());
            String parentType = tupleInput.readString();

            return new UpgradeHierarchyKey(childId, parentType);
        }

        @Override
        public void objectToEntry(UpgradeHierarchyKey hk, TupleOutput tupleOutput)
        {
            throw new UnsupportedOperationException();
        }
    }

    private static class UpgradeHierarchyKey
    {
        private final UUID _childId;
        private final String _parentType;

        public UpgradeHierarchyKey(final UUID childId, final String parentType)
        {
            _childId = childId;
            _parentType = parentType;
        }

        @Override
        public boolean equals(final Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o == null || getClass() != o.getClass())
            {
                return false;
            }

            final UpgradeHierarchyKey that = (UpgradeHierarchyKey) o;

            if (!_childId.equals(that._childId))
            {
                return false;
            }
            if (!_parentType.equals(that._parentType))
            {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = _childId.hashCode();
            result = 31 * result + _parentType.hashCode();
            return result;
        }

    }

}
