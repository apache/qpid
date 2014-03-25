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

import com.sleepycat.bind.tuple.ByteBinding;
import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.*;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.store.berkeleydb.BDBConfiguredObjectRecord;
import org.apache.qpid.server.store.berkeleydb.entry.HierarchyKey;
import org.apache.qpid.server.store.berkeleydb.tuple.ConfiguredObjectBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.HierarchyKeyBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.UUIDTupleBinding;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UpgradeFrom7To8 extends AbstractStoreUpgrade
{

    @Override
    public void performUpgrade(Environment environment, UpgradeInteractionHandler handler, ConfiguredObject<?> parent)
    {
        reportStarting(environment, 7);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);

        Database hierarchyDb = environment.openDatabase(null, "CONFIGURED_OBJECT_HIERARCHY", dbConfig);
        Database configuredObjectsDb = environment.openDatabase(null, "CONFIGURED_OBJECTS", dbConfig);

        Cursor objectsCursor = null;

        Transaction txn = environment.beginTransaction(null, null);

        try
        {
            objectsCursor = configuredObjectsDb.openCursor(txn, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();

            Map<UUID, BDBConfiguredObjectRecord> configuredObjects =
                    new HashMap<UUID, BDBConfiguredObjectRecord>();

            while (objectsCursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                UUID id = UUIDTupleBinding.getInstance().entryToObject(key);
                TupleInput input = TupleBinding.entryToInput(value);
                String type = input.readString();

                if(!type.endsWith("Binding"))
                {
                    UUIDTupleBinding.getInstance().objectToEntry(parent.getId(),value);
                    TupleOutput tupleOutput = new TupleOutput();
                    tupleOutput.writeLong(id.getMostSignificantBits());
                    tupleOutput.writeLong(id.getLeastSignificantBits());
                    tupleOutput.writeString("VirtualHost");
                    TupleBinding.outputToEntry(tupleOutput, key);
                    hierarchyDb.put(txn, key, value);
                }
                else
                {
                    String json = input.readString();
                    ObjectMapper mapper = new ObjectMapper();
                    try
                    {
                        DatabaseEntry hierarchyKey = new DatabaseEntry();
                        DatabaseEntry hierarchyValue = new DatabaseEntry();

                        Map<String,Object> attributes = mapper.readValue(json, Map.class);
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
        txn.commit();

        hierarchyDb.close();
        configuredObjectsDb.close();



        reportFinished(environment, 8);
    }
}
