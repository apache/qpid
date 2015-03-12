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


import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CursorOperation implements DatabaseRunnable
{
    private static final Logger _logger = LoggerFactory.getLogger(CursorOperation.class);

    private CursorTemplate _template;
    private long _rowCount;
    private long _processedRowCount;

    @Override
    public void run(final Database sourceDatabase, final Database targetDatabase, final Transaction transaction)
    {
        _rowCount = sourceDatabase.count();
        _template = new CursorTemplate(sourceDatabase, transaction, new DatabaseEntryCallback()
        {
            @Override
            public void processEntry(final Database database, final Transaction transaction, final DatabaseEntry key,
                    final DatabaseEntry value)
            {
                _processedRowCount++;
                CursorOperation.this.processEntry(database, targetDatabase, transaction, key, value);
                if (getProcessedCount() % 1000 == 0)
                {
                    _logger.info("Processed " + getProcessedCount() + " records of " + getRowCount() + ".");
                }
            }

        });
        _template.processEntries();
    }

    public void abort()
    {
        if (_template != null)
        {
            _template.abort();
        }
    }

    public boolean deleteCurrent()
    {
        if (_template != null)
        {
            return _template.deleteCurrent();
        }
        return false;
    }

    public long getRowCount()
    {
        return _rowCount;
    }

    public long getProcessedCount()
    {
        return _processedRowCount;
    }

    public abstract void processEntry(Database sourceDatabase, Database targetDatabase, Transaction transaction,
            DatabaseEntry key, DatabaseEntry value);

}
