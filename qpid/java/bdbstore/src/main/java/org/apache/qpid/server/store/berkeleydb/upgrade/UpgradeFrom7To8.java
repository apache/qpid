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

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.store.berkeleydb.tuple.ConfiguredObjectBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.MessageMetaDataBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.UUIDTupleBinding;

import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.je.Sequence;
import com.sleepycat.je.SequenceConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

public class UpgradeFrom7To8 extends AbstractStoreUpgrade
{
    private static final TypeReference<HashMap<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<HashMap<String,Object>>(){};

    @SuppressWarnings("serial")
    private Map<String, String> _defaultExchanges = new HashMap<String, String>()
    {{
        put("amq.direct", "direct");
        put("amq.topic", "topic");
        put("amq.fanout", "fanout");
        put("amq.match", "headers");
    }};

    private static final DatabaseEntry MESSAGE_METADATA_SEQ_KEY = new DatabaseEntry("MESSAGE_METADATA_SEQ_KEY".getBytes(Charset.forName("UTF-8")));
    private static SequenceConfig MESSAGE_METADATA_SEQ_CONFIG = SequenceConfig.DEFAULT.
                setAllowCreate(true).
                setWrap(true).
                setCacheSize(100000);

    @Override
    public void performUpgrade(Environment environment, UpgradeInteractionHandler handler, ConfiguredObject<?> parent)
    {
        reportStarting(environment, 7);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);

        Database hierarchyDb = environment.openDatabase(null, "CONFIGURED_OBJECT_HIERARCHY", dbConfig);
        Database configuredObjectsDb = environment.openDatabase(null, "CONFIGURED_OBJECTS", dbConfig);
        Database configVersionDb = environment.openDatabase(null, "CONFIG_VERSION", dbConfig);
        Database messageMetadataDb = environment.openDatabase(null, "MESSAGE_METADATA", dbConfig);
        Database messageMetadataSeqDb = environment.openDatabase(null, "MESSAGE_METADATA.SEQ", dbConfig);

        long maxMessageId = getMaximumMessageId(messageMetadataDb);
        createMessageMetadataSequence(messageMetadataSeqDb, maxMessageId);

        Cursor objectsCursor = null;

        String stringifiedConfigVersion = BrokerModel.MODEL_VERSION;
        int configVersion = getConfigVersion(configVersionDb);
        if (configVersion > -1)
        {
            stringifiedConfigVersion = "0." + configVersion;
        }
        configVersionDb.close();

        String virtualHostName = parent.getName();
        Map<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put("modelVersion", stringifiedConfigVersion);
        virtualHostAttributes.put("name", virtualHostName);
        UUID virtualHostId = UUIDGenerator.generateVhostUUID(virtualHostName);
        ConfiguredObjectRecord virtualHostRecord = new org.apache.qpid.server.store.ConfiguredObjectRecordImpl(virtualHostId, "VirtualHost", virtualHostAttributes);

        Transaction txn = environment.beginTransaction(null, null);

        try
        {
            objectsCursor = configuredObjectsDb.openCursor(txn, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            ObjectMapper mapper = new ObjectMapper();

            while (objectsCursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                UUID id = UUIDTupleBinding.getInstance().entryToObject(key);
                TupleInput input = TupleBinding.entryToInput(value);
                String type = input.readString();
                String json = input.readString();
                Map<String,Object> attributes = null;
                try
                {
                    attributes = mapper.readValue(json, MAP_TYPE_REFERENCE);
                }
                catch (Exception e)
                {
                    throw new StoreException(e);
                }
                String name = (String)attributes.get("name");

                if (type.equals("Exchange"))
                {
                    _defaultExchanges.remove(name);
                }

                if(!type.endsWith("Binding"))
                {
                    storeVirtualHostHierarchyRecord(hierarchyDb, txn, id, virtualHostId);
                }
                else
                {
                    try
                    {
                        DatabaseEntry hierarchyKey = new DatabaseEntry();
                        DatabaseEntry hierarchyValue = new DatabaseEntry();

                        Object queueIdString = attributes.remove("queue");
                        if(queueIdString instanceof String)
                        {
                            UUID queueId = UUID.fromString(queueIdString.toString());
                            UUIDTupleBinding.getInstance().objectToEntry(queueId,hierarchyValue);
                            TupleOutput tupleOutput = new TupleOutput();
                            tupleOutput.writeLong(id.getMostSignificantBits());
                            tupleOutput.writeLong(id.getLeastSignificantBits());
                            tupleOutput.writeString("Queue");
                            TupleBinding.outputToEntry(tupleOutput, hierarchyKey);
                            hierarchyDb.put(txn, hierarchyKey, hierarchyValue);
                        }
                        Object exchangeIdString = attributes.remove("exchange");
                        if(exchangeIdString instanceof String)
                        {
                            UUID exchangeId = UUID.fromString(exchangeIdString.toString());
                            UUIDTupleBinding.getInstance().objectToEntry(exchangeId,hierarchyValue);
                            TupleOutput tupleOutput = new TupleOutput();
                            tupleOutput.writeLong(id.getMostSignificantBits());
                            tupleOutput.writeLong(id.getLeastSignificantBits());
                            tupleOutput.writeString("Exchange");
                            TupleBinding.outputToEntry(tupleOutput, hierarchyKey);
                            hierarchyDb.put(txn, hierarchyKey, hierarchyValue);
                        }
                        TupleOutput tupleOutput = new TupleOutput();
                        tupleOutput.writeString(type);
                        StringWriter writer = new StringWriter();
                        mapper.writeValue(writer,attributes);
                        tupleOutput.writeString(writer.getBuffer().toString());
                        TupleBinding.outputToEntry(tupleOutput, value);
                        objectsCursor.putCurrent(value);
                    }
                    catch (IOException e)
                    {
                        throw new StoreException(e);
                    }
                }
            }
        }
        finally
        {
            if(objectsCursor != null)
            {
                objectsCursor.close();
            }
        }

        storeConfiguredObjectEntry(configuredObjectsDb, txn, virtualHostRecord);
        for (Map.Entry<String, String> defaultExchangeEntry : _defaultExchanges.entrySet())
        {
            UUID id = UUIDGenerator.generateExchangeUUID(defaultExchangeEntry.getKey(), virtualHostName);
            Map<String, Object> exchangeAttributes = new HashMap<String, Object>();
            exchangeAttributes.put("name", defaultExchangeEntry.getKey());
            exchangeAttributes.put("type", defaultExchangeEntry.getValue());
            exchangeAttributes.put("lifetimePolicy", "PERMANENT");
            ConfiguredObjectRecord exchangeRecord = new org.apache.qpid.server.store.ConfiguredObjectRecordImpl(id, "Exchange", exchangeAttributes);
            storeConfiguredObjectEntry(configuredObjectsDb, txn, exchangeRecord);
            storeVirtualHostHierarchyRecord(hierarchyDb, txn, id, virtualHostId);
        }
        txn.commit();

        hierarchyDb.close();
        configuredObjectsDb.close();
        messageMetadataDb.close();
        messageMetadataSeqDb.close();

        reportFinished(environment, 8);
    }

    void storeVirtualHostHierarchyRecord(Database hierarchyDb, Transaction txn, UUID id, UUID virtualHostId)
    {
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry value = new DatabaseEntry();
        UUIDTupleBinding.getInstance().objectToEntry(virtualHostId, value);
        TupleOutput tupleOutput = new TupleOutput();
        tupleOutput.writeLong(id.getMostSignificantBits());
        tupleOutput.writeLong(id.getLeastSignificantBits());
        tupleOutput.writeString("VirtualHost");
        TupleBinding.outputToEntry(tupleOutput, key);
        hierarchyDb.put(txn, key, value);
    }

    private void createMessageMetadataSequence(final Database messageMetadataSeqDb,
                                               final long maximumMessageId)
    {
        SequenceConfig sequenceConfig = MESSAGE_METADATA_SEQ_CONFIG.setInitialValue(maximumMessageId + 1);

        Sequence messageMetadataSeq = messageMetadataSeqDb.openSequence(null, MESSAGE_METADATA_SEQ_KEY, sequenceConfig);
        messageMetadataSeq.close();
    }

    private int getConfigVersion(Database configVersionDb)
    {
        Cursor cursor = null;
        try
        {
            cursor = configVersionDb.openCursor(null, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                return IntegerBinding.entryToInt(value);
            }
            return -1;
        }
        finally
        {
            cursor.close();
        }
    }

    private void storeConfiguredObjectEntry(Database configuredObjectsDb, final Transaction txn, ConfiguredObjectRecord configuredObject)
    {
        DatabaseEntry key = new DatabaseEntry();
        UUIDTupleBinding uuidBinding = UUIDTupleBinding.getInstance();
        uuidBinding.objectToEntry(configuredObject.getId(), key);

        DatabaseEntry value = new DatabaseEntry();
        ConfiguredObjectBinding configuredObjectBinding = ConfiguredObjectBinding.getInstance();

        configuredObjectBinding.objectToEntry(configuredObject, value);
        OperationStatus status = configuredObjectsDb.put(txn, key, value);
        if (status != OperationStatus.SUCCESS)
        {
            throw new StoreException("Error writing configured object " + configuredObject + " to database: "
                    + status);
        }
    }

    private long getMaximumMessageId(Database messageMetaDataDb)
    {
        Cursor cursor = null;
        long maximumMessageId = 0;  // Our hand-rolled sequences value always began at zero
        try
        {
            cursor = messageMetaDataDb.openCursor(null, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            MessageMetaDataBinding valueBinding = MessageMetaDataBinding.getInstance();

            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                long messageId = LongBinding.entryToLong(key);
                maximumMessageId = Math.max(messageId, maximumMessageId);
            }
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }

        return maximumMessageId;
    }

}
