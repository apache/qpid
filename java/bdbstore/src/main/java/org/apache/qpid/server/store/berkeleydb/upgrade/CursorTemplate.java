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

import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

public class CursorTemplate
{
    private Database _database;
    private Transaction _transaction;
    private DatabaseEntryCallback _databaseEntryCallback;
    private Cursor _cursor;
    private boolean _iterating;

    public CursorTemplate(Database database, Transaction transaction, DatabaseEntryCallback databaseEntryCallback)
    {
        _database = database;
        _transaction = transaction;
        _databaseEntryCallback = databaseEntryCallback;
    }

    public void processEntries()
    {
        _cursor = _database.openCursor(_transaction, CursorConfig.READ_COMMITTED);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry value = new DatabaseEntry();

        try
        {
            _iterating = true;
            while (_iterating && _cursor.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS)
            {
                _databaseEntryCallback.processEntry(_database, _transaction, key, value);
            }
        }
        finally
        {
            _cursor.close();
        }
    }

    public boolean deleteCurrent()
    {
        return _cursor.delete() == OperationStatus.SUCCESS;
    }

    public void abort()
    {
        _iterating = false;
    }
}
