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

import static org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.CONFIGURED_OBJECTS_DB_NAME;
import static org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.NEW_CONTENT_DB_NAME;
import static org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.NEW_DELIVERY_DB_NAME;
import static org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.NEW_METADATA_DB_NAME;
import static org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.NEW_XID_DB_NAME;
import static org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.OLD_CONTENT_DB_NAME;
import static org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.OLD_XID_DB_NAME;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.store.berkeleydb.entry.Xid;
import org.apache.qpid.server.store.berkeleydb.tuple.XidBinding;
import org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.CompoundKey;
import org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.CompoundKeyBinding;
import org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.ConfiguredObjectBinding;
import org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.UpgradeConfiguredObjectRecord;
import org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.NewDataBinding;
import org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.NewPreparedTransaction;
import org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.NewPreparedTransactionBinding;
import org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.NewQueueEntryBinding;
import org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.NewQueueEntryKey;
import org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.NewRecordImpl;
import org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.OldPreparedTransaction;
import org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.OldPreparedTransactionBinding;
import org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.OldRecordImpl;
import org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.UpgradeUUIDBinding;
import org.apache.qpid.server.util.MapJsonSerializer;

import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.Transaction;

public class UpgradeFrom5To6Test extends AbstractUpgradeTestCase
{
    private static final Logger _logger = Logger.getLogger(UpgradeFrom5To6Test.class);

    @Override
    protected String getStoreDirectoryName()
    {
        return "bdbstore-v5";
    }

    public void testPerformUpgrade() throws Exception
    {
        UpgradeFrom5To6 upgrade = new UpgradeFrom5To6();
        upgrade.performUpgrade(_environment, UpgradeInteractionHandler.DEFAULT_HANDLER, getVirtualHostName());

        assertDatabaseRecordCounts();
        assertContent();

        assertConfiguredObjects();
        assertQueueEntries();
    }

    public void testPerformUpgradeWithMissingMessageChunkKeepsIncompleteMessage() throws Exception
    {
        corruptDatabase();

        UpgradeFrom5To6 upgrade = new UpgradeFrom5To6();
        upgrade.performUpgrade(_environment, new StaticAnswerHandler(UpgradeInteractionResponse.YES), getVirtualHostName());

        assertDatabaseRecordCounts();

        assertConfiguredObjects();
        assertQueueEntries();
    }

    public void testPerformUpgradeWithMissingMessageChunkDiscardsIncompleteMessage() throws Exception
    {
        corruptDatabase();

        UpgradeFrom5To6 upgrade = new UpgradeFrom5To6();

        UpgradeInteractionHandler discardMessageInteractionHandler = new StaticAnswerHandler(UpgradeInteractionResponse.NO);

        upgrade.performUpgrade(_environment, discardMessageInteractionHandler, getVirtualHostName());

        assertDatabaseRecordCount(NEW_METADATA_DB_NAME, 11);
        assertDatabaseRecordCount(NEW_CONTENT_DB_NAME, 11);

        assertConfiguredObjects();
        assertQueueEntries();
    }

    public void testPerformXidUpgrade() throws Exception
    {
        File storeLocation = new File(TMP_FOLDER, getName());
        storeLocation.mkdirs();
        Environment environment = createEnvironment(storeLocation);
        try
        {
            populateOldXidEntries(environment);
            UpgradeFrom5To6 upgrade = new UpgradeFrom5To6();
            upgrade.performUpgrade(environment, UpgradeInteractionHandler.DEFAULT_HANDLER, getVirtualHostName());
            assertXidEntries(environment);
        }
        finally
        {
            try
            {
                environment.close();
            }
            finally
            {
                deleteDirectoryIfExists(storeLocation);
            }

        }
    }

    private void assertXidEntries(Environment environment)
    {
        final DatabaseEntry value = new DatabaseEntry();
        final DatabaseEntry key = getXidKey();
        new DatabaseTemplate(environment, NEW_XID_DB_NAME, null).run(new DatabaseRunnable()
        {

            @Override
            public void run(Database xidDatabase, Database nullDatabase, Transaction transaction)
            {
                xidDatabase.get(null, key, value, LockMode.DEFAULT);
            }
        });
        NewPreparedTransactionBinding newBinding = new NewPreparedTransactionBinding();
        NewPreparedTransaction newTransaction = newBinding.entryToObject(value);
        NewRecordImpl[] newEnqueues = newTransaction.getEnqueues();
        NewRecordImpl[] newDequeues = newTransaction.getDequeues();
        assertEquals("Unxpected new enqueus number", 1, newEnqueues.length);
        NewRecordImpl enqueue = newEnqueues[0];
        assertEquals("Unxpected queue id", UUIDGenerator.generateUUID("TEST1", getVirtualHostName()), enqueue.getId());
        assertEquals("Unxpected message id", 1, enqueue.getMessageNumber());
        assertEquals("Unxpected new dequeues number", 1, newDequeues.length);
        NewRecordImpl dequeue = newDequeues[0];
        assertEquals("Unxpected queue id", UUIDGenerator.generateUUID("TEST2", getVirtualHostName()), dequeue.getId());
        assertEquals("Unxpected message id", 2, dequeue.getMessageNumber());
    }

    private void populateOldXidEntries(Environment environment)
    {

        final DatabaseEntry value = new DatabaseEntry();
        OldRecordImpl[] enqueues = { new OldRecordImpl("TEST1", 1) };
        OldRecordImpl[] dequeues = { new OldRecordImpl("TEST2", 2) };
        OldPreparedTransaction oldPreparedTransaction = new OldPreparedTransaction(enqueues, dequeues);
        OldPreparedTransactionBinding oldPreparedTransactionBinding = new OldPreparedTransactionBinding();
        oldPreparedTransactionBinding.objectToEntry(oldPreparedTransaction, value);

        final DatabaseEntry key = getXidKey();
        new DatabaseTemplate(environment, OLD_XID_DB_NAME, null).run(new DatabaseRunnable()
        {

            @Override
            public void run(Database xidDatabase, Database nullDatabase, Transaction transaction)
            {
                xidDatabase.put(null, key, value);
            }
        });
    }

    protected DatabaseEntry getXidKey()
    {
        final DatabaseEntry value = new DatabaseEntry();
        byte[] globalId = { 1 };
        byte[] branchId = { 2 };
        Xid xid = new Xid(1l, globalId, branchId);
        XidBinding xidBinding = XidBinding.getInstance();
        xidBinding.objectToEntry(xid, value);
        return value;
    }

    private void assertQueueEntries()
    {
        final Map<UUID, UpgradeConfiguredObjectRecord> configuredObjects = loadConfiguredObjects();
        final NewQueueEntryBinding newBinding = new NewQueueEntryBinding();
        CursorOperation cursorOperation = new CursorOperation()
        {

            @Override
            public void processEntry(Database sourceDatabase, Database targetDatabase, Transaction transaction,
                    DatabaseEntry key, DatabaseEntry value)
            {
                NewQueueEntryKey newEntryRecord = newBinding.entryToObject(key);
                assertTrue("Unexpected queue id", configuredObjects.containsKey(newEntryRecord.getQueueId()));
            }
        };
        new DatabaseTemplate(_environment, NEW_DELIVERY_DB_NAME, null).run(cursorOperation);
    }

    /**
     * modify the chunk offset of a message to be wrong, so we can test logic
     * that preserves incomplete messages
     */
    private void corruptDatabase()
    {
        CursorOperation cursorOperation = new CursorOperation()
        {

            @Override
            public void processEntry(Database sourceDatabase, Database targetDatabase, Transaction transaction,
                    DatabaseEntry key, DatabaseEntry value)
            {
                CompoundKeyBinding binding = new CompoundKeyBinding();
                CompoundKey originalCompoundKey = binding.entryToObject(key);
                int corruptedOffset = originalCompoundKey.getOffset() + 2;
                CompoundKey corruptedCompoundKey = new CompoundKey(originalCompoundKey.getMessageId(), corruptedOffset);
                DatabaseEntry newKey = new DatabaseEntry();
                binding.objectToEntry(corruptedCompoundKey, newKey);

                _logger.info("Deliberately corrupted message id " + originalCompoundKey.getMessageId()
                        + ", changed offset from " + originalCompoundKey.getOffset() + " to "
                        + corruptedCompoundKey.getOffset());

                deleteCurrent();
                sourceDatabase.put(transaction, newKey, value);

                abort();
            }
        };

        Transaction transaction = _environment.beginTransaction(null, null);
        new DatabaseTemplate(_environment, OLD_CONTENT_DB_NAME, transaction).run(cursorOperation);
        transaction.commit();
    }

    private void assertDatabaseRecordCounts()
    {
        assertDatabaseRecordCount(CONFIGURED_OBJECTS_DB_NAME, 9);
        assertDatabaseRecordCount(NEW_DELIVERY_DB_NAME, 12);

        assertDatabaseRecordCount(NEW_METADATA_DB_NAME, 12);
        assertDatabaseRecordCount(NEW_CONTENT_DB_NAME, 12);
    }

    private void assertConfiguredObjects()
    {
        Map<UUID, UpgradeConfiguredObjectRecord> configuredObjects = loadConfiguredObjects();
        assertEquals("Unexpected number of configured objects", 9, configuredObjects.size());

        Set<Map<String, Object>> expected = new HashSet<Map<String, Object>>(9);
        Map<String, Object> queue1 = new HashMap<String, Object>();
        queue1.put("exclusive", Boolean.FALSE);
        queue1.put("name", "myUpgradeQueue");
        queue1.put("owner", null);
        expected.add(queue1);
        Map<String, Object> queue2 = new HashMap<String, Object>();
        queue2.put("exclusive", Boolean.TRUE);
        queue2.put("name", "clientid:mySelectorDurSubName");
        queue2.put("owner", "clientid");
        expected.add(queue2);
        Map<String, Object> queue3 = new HashMap<String, Object>();
        queue3.put("exclusive", Boolean.TRUE);
        queue3.put("name", "clientid:myDurSubName");
        queue3.put("owner", "clientid");
        expected.add(queue3);

        Map<String, Object> queueBinding1 = new HashMap<String, Object>();
        queueBinding1.put("queue", UUIDGenerator.generateUUID("myUpgradeQueue", getVirtualHostName()).toString());
        queueBinding1.put("name", "myUpgradeQueue");
        queueBinding1.put("exchange", UUIDGenerator.generateUUID("<<default>>", getVirtualHostName()).toString());
        expected.add(queueBinding1);
        Map<String, Object> queueBinding2 = new HashMap<String, Object>();
        queueBinding2.put("queue", UUIDGenerator.generateUUID("myUpgradeQueue", getVirtualHostName()).toString());
        queueBinding2.put("name", "myUpgradeQueue");
        queueBinding2.put("exchange", UUIDGenerator.generateUUID("amq.direct", getVirtualHostName()).toString());
        Map<String, Object> arguments2 = new HashMap<String, Object>();
        arguments2.put("x-filter-jms-selector", "");
        queueBinding2.put("arguments", arguments2);
        expected.add(queueBinding2);
        Map<String, Object> queueBinding3 = new HashMap<String, Object>();
        queueBinding3.put("queue", UUIDGenerator.generateUUID("clientid:myDurSubName", getVirtualHostName()).toString());
        queueBinding3.put("name", "myUpgradeTopic");
        queueBinding3.put("exchange", UUIDGenerator.generateUUID("amq.topic", getVirtualHostName()).toString());
        Map<String, Object> arguments3 = new HashMap<String, Object>();
        arguments3.put("x-filter-jms-selector", "");
        queueBinding3.put("arguments", arguments3);
        expected.add(queueBinding3);
        Map<String, Object> queueBinding4 = new HashMap<String, Object>();
        queueBinding4.put("queue", UUIDGenerator.generateUUID("clientid:mySelectorDurSubName", getVirtualHostName()).toString());
        queueBinding4.put("name", "mySelectorUpgradeTopic");
        queueBinding4.put("exchange", UUIDGenerator.generateUUID("amq.topic", getVirtualHostName()).toString());
        Map<String, Object> arguments4 = new HashMap<String, Object>();
        arguments4.put("x-filter-jms-selector", "testprop='true'");
        queueBinding4.put("arguments", arguments4);
        expected.add(queueBinding4);
        Map<String, Object> queueBinding5 = new HashMap<String, Object>();
        queueBinding5.put("queue", UUIDGenerator.generateUUID("clientid:myDurSubName", getVirtualHostName()).toString());
        queueBinding5.put("name", "clientid:myDurSubName");
        queueBinding5.put("exchange", UUIDGenerator.generateUUID("<<default>>", getVirtualHostName()).toString());
        expected.add(queueBinding5);
        Map<String, Object> queueBinding6 = new HashMap<String, Object>();
        queueBinding6.put("queue", UUIDGenerator.generateUUID("clientid:mySelectorDurSubName", getVirtualHostName()).toString());
        queueBinding6.put("name", "clientid:mySelectorDurSubName");
        queueBinding6.put("exchange", UUIDGenerator.generateUUID("<<default>>", getVirtualHostName()).toString());
        expected.add(queueBinding6);

        Set<String> expectedTypes = new HashSet<String>();
        expectedTypes.add(Queue.class.getName());
        expectedTypes.add(Exchange.class.getName());
        expectedTypes.add(Binding.class.getName());
        MapJsonSerializer jsonSerializer = new MapJsonSerializer();
        for (Entry<UUID, UpgradeConfiguredObjectRecord> entry : configuredObjects.entrySet())
        {
            UpgradeConfiguredObjectRecord object = entry.getValue();
            UUID key = entry.getKey();
            Map<String, Object> deserialized = jsonSerializer.deserialize(object.getAttributes());
            assertTrue("Unexpected entry:" + object.getAttributes(), expected.remove(deserialized));
            String type = object.getType();
            assertTrue("Unexpected type:" + type, expectedTypes.contains(type));
            if (type.equals(Exchange.class.getName()) || type.equals(Queue.class.getName()))
            {
                assertEquals("Unexpected key", key, UUIDGenerator.generateUUID(((String) deserialized.get("name")), getVirtualHostName()));
            }
            else
            {
                assertNotNull("Key cannot be null", key);
            }
        }
        assertTrue("Not all expected configured objects found:" + expected, expected.isEmpty());
    }

    private Map<UUID, UpgradeConfiguredObjectRecord> loadConfiguredObjects()
    {
        final Map<UUID, UpgradeConfiguredObjectRecord> configuredObjectsRecords = new HashMap<UUID, UpgradeConfiguredObjectRecord>();
        final ConfiguredObjectBinding binding = new ConfiguredObjectBinding();
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

    private void assertContent()
    {
        final NewDataBinding contentBinding = new NewDataBinding();
        CursorOperation contentCursorOperation = new CursorOperation()
        {

            @Override
            public void processEntry(Database sourceDatabase, Database targetDatabase, Transaction transaction,
                    DatabaseEntry key, DatabaseEntry value)
            {
                long id = LongBinding.entryToLong(key);
                assertTrue("Unexpected id", id > 0);
                byte[] content = contentBinding.entryToObject(value);
                assertNotNull("Unexpected content", content);
            }
        };
        new DatabaseTemplate(_environment, NEW_CONTENT_DB_NAME, null).run(contentCursorOperation);
    }
}
