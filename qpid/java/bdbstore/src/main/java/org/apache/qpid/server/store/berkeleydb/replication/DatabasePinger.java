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
package org.apache.qpid.server.store.berkeleydb.replication;

import org.apache.qpid.server.store.berkeleydb.EnvironmentFacade;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import org.apache.log4j.Logger;

public class DatabasePinger
{
    private static final Logger LOGGER = Logger.getLogger(DatabasePinger.class);

    public static final String PING_DATABASE_NAME = "PINGDB";
    private static final DatabaseConfig DATABASE_CONFIG =
            DatabaseConfig.DEFAULT.setAllowCreate(true).setTransactional(true);
    private static final int ID = 0;
    private final TransactionConfig _pingTransactionConfig = new TransactionConfig();

    public DatabasePinger()
    {
        // The ping merely needs to establish that the sufficient remote nodes are available,
        // we don't need to await the data being written/synch'd
        _pingTransactionConfig.setDurability(new Durability(Durability.SyncPolicy.NO_SYNC,
                                                            Durability.SyncPolicy.NO_SYNC,
                                                            Durability.ReplicaAckPolicy.SIMPLE_MAJORITY));
    }

    public void pingDb(EnvironmentFacade facade)
    {
        try
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Beginning ping transaction");
            }

            final Database db = facade.openDatabase(PING_DATABASE_NAME,
                                                    DATABASE_CONFIG);

            DatabaseEntry key = new DatabaseEntry();
            IntegerBinding.intToEntry(ID, key);

            DatabaseEntry value = new DatabaseEntry();
            LongBinding.longToEntry(System.currentTimeMillis(), value);
            Transaction txn = null;
            try
            {
                txn = facade.getEnvironment().beginTransaction(null, _pingTransactionConfig);
                db.put(txn, key, value);
                txn.commit();

                txn = null;
            }
            finally
            {
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Ping transaction completed");
                }

                if (txn != null)
                {
                    txn.abort();
                }
            }
        }
        catch (RuntimeException de)
        {
            facade.handleDatabaseException("DatabaseException from DatabasePinger ", de);
        }
    }
}
