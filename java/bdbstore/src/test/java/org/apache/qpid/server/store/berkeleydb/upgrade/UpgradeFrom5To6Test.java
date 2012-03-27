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

import org.apache.log4j.Logger;
import org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.CompoundKey;
import org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.CompoundKeyBinding;
import org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeFrom5To6.NewDataBinding;

import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
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
        upgrade.performUpgrade(LOG_SUBJECT, _environment, UpgradeInteractionHandler.DEFAULT_HANDLER);

        assertDatabaseRecordCounts();
        assertContent();
    }

    public void testPerformUpgradeWithMissingMessageChunkKeepsIncompleteMessage() throws Exception
    {
        corruptDatabase();

        UpgradeFrom5To6 upgrade = new UpgradeFrom5To6();
        upgrade.performUpgrade(LOG_SUBJECT, _environment, new StaticAnswerHandler(UpgradeInteractionResponse.YES));

        assertDatabaseRecordCounts();
    }

    public void testPerformUpgradeWithMissingMessageChunkDiscardsIncompleteMessage() throws Exception
    {
        corruptDatabase();

        UpgradeFrom5To6 upgrade = new UpgradeFrom5To6();

        UpgradeInteractionHandler discardMessageInteractionHandler = new StaticAnswerHandler(UpgradeInteractionResponse.NO);

        upgrade.performUpgrade(LOG_SUBJECT, _environment, discardMessageInteractionHandler);

        assertDatabaseRecordCount("MESSAGE_METADATA", 11);
        assertDatabaseRecordCount("MESSAGE_CONTENT", 11);
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
        new DatabaseTemplate(_environment, "messageContentDb_v5", transaction).run(cursorOperation);
        transaction.commit();
    }

    private void assertDatabaseRecordCounts()
    {
        assertDatabaseRecordCount("EXCHANGES", 5);
        assertDatabaseRecordCount("QUEUES", 3);
        assertDatabaseRecordCount("QUEUE_BINDINGS", 6);
        assertDatabaseRecordCount("DELIVERIES", 12);

        assertDatabaseRecordCount("MESSAGE_METADATA", 12);
        assertDatabaseRecordCount("MESSAGE_CONTENT", 12);
    }

    private void assertContent()
    {
        final NewDataBinding contentBinding = new NewDataBinding();
        CursorOperation contentCursorOperation = new CursorOperation()
        {

            @Override
            public void processEntry(Database sourceDatabase, Database targetDatabase, Transaction transaction, DatabaseEntry key,
                    DatabaseEntry value)
            {
                long id = LongBinding.entryToLong(key);
                assertTrue("Unexpected id", id > 0);
                byte[] content = contentBinding.entryToObject(value);
                assertNotNull("Unexpected content", content);
            }
        };
        new DatabaseTemplate(_environment, "MESSAGE_CONTENT", null).run(contentCursorOperation);
    }
}
