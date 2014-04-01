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
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.OperationStatus;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.store.StoreException;

public class UpgradeFrom6To7 extends AbstractStoreUpgrade
{

    private static final int DEFAULT_CONFIG_VERSION = 0;

    @Override
    public void performUpgrade(Environment environment, UpgradeInteractionHandler handler, ConfiguredObject<?> parent)
    {
        reportStarting(environment, 6);
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);

        Database versionDb = environment.openDatabase(null, "CONFIG_VERSION", dbConfig);

        if(versionDb.count() == 0L)
        {
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            IntegerBinding.intToEntry(DEFAULT_CONFIG_VERSION, value);
            ByteBinding.byteToEntry((byte) 0, key);
            OperationStatus status = versionDb.put(null, key, value);
            if (status != OperationStatus.SUCCESS)
            {
                throw new StoreException("Error initialising config version: " + status);
            }
        }

        versionDb.close();

        reportFinished(environment, 7);
    }
}
