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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.qpid.server.store.berkeleydb.BDBMessageStore;
import org.apache.qpid.server.store.berkeleydb.tuple.ContentBinding;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

public class UpgraderTest extends AbstractUpgradeTestCase
{
    private Upgrader _upgrader;

    @Override
    protected String getStoreDirectoryName()
    {
        return "bdbstore-v4";
    }

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _upgrader = new Upgrader(_environment);
    }

    private int getStoreVersion()
    {
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        int storeVersion = -1;
        Database versionDb = null;
        Cursor cursor = null;
        try
        {
            versionDb = _environment.openDatabase(null, Upgrader.VERSION_DB_NAME, dbConfig);
            cursor = versionDb.openCursor(null, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            while (cursor.getNext(key, value, null) == OperationStatus.SUCCESS)
            {
                int version = IntegerBinding.entryToInt(key);
                if (storeVersion < version)
                {
                    storeVersion = version;
                }
            }
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
            if (versionDb != null)
            {
                versionDb.close();
            }
        }
        return storeVersion;
    }

    public void testUpgrade() throws Exception
    {
        assertEquals("Unexpected store version", -1, getStoreVersion());
        _upgrader.upgradeIfNecessary();
        assertEquals("Unexpected store version", BDBMessageStore.VERSION, getStoreVersion());
        assertContent();
    }

    public void testEmptyDatabaseUpgradeDoesNothing() throws Exception
    {
        File nonExistentStoreLocation = new File(TMP_FOLDER, getName());
        deleteDirectoryIfExists(nonExistentStoreLocation);

        nonExistentStoreLocation.mkdir();
        _environment = createEnvironment(nonExistentStoreLocation);
        _upgrader = new Upgrader(_environment);
        _upgrader.upgradeIfNecessary();

        List<String> databaseNames = _environment.getDatabaseNames();
        List<String> expectedDatabases = new ArrayList<String>();
        expectedDatabases.add("VERSION");
        assertEquals("Expectedonly VERSION table in initially empty store after upgrade: ", expectedDatabases, databaseNames);
        assertEquals("Unexpected store version", BDBMessageStore.VERSION, getStoreVersion());

        nonExistentStoreLocation.delete();
    }

    private void assertContent()
    {
        final ContentBinding contentBinding = ContentBinding.getInstance();
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
