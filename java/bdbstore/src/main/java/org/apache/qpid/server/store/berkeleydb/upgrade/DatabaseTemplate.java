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
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseTemplate
{
    private static final Logger _logger = LoggerFactory.getLogger(DatabaseTemplate.class);

    private Environment _environment;
    private String _sourceDatabaseName;
    private String _targetDatabaseName;
    private Transaction _parentTransaction;

    public DatabaseTemplate(Environment environment, String sourceDatabaseName, Transaction transaction)
    {
        this(environment, sourceDatabaseName, null, transaction);
    }

    public DatabaseTemplate(Environment environment, String sourceDatabaseName, String targetDatabaseName,
            Transaction parentTransaction)
    {
        _environment = environment;
        _sourceDatabaseName = sourceDatabaseName;
        _targetDatabaseName = targetDatabaseName;
        _parentTransaction = parentTransaction;
    }

    public void run(DatabaseRunnable databaseRunnable)
    {
        DatabaseCallable<Void> callable = runnableToCallable(databaseRunnable);
        call(callable);
    }

    public <T> T call(DatabaseCallable<T> databaseCallable)
    {
        Database sourceDatabase = null;
        Database targetDatabase = null;
        try
        {
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(true);

            sourceDatabase = _environment.openDatabase(_parentTransaction, _sourceDatabaseName, dbConfig);

            if (_targetDatabaseName != null)
            {
                targetDatabase = _environment.openDatabase(_parentTransaction, _targetDatabaseName, dbConfig);
            }

            return databaseCallable.call(sourceDatabase, targetDatabase, _parentTransaction);
        }
        finally
        {
            closeDatabase(sourceDatabase);
            closeDatabase(targetDatabase);
        }
    }

    private DatabaseCallable<Void> runnableToCallable(final DatabaseRunnable databaseRunnable)
    {
        return new DatabaseCallable<Void>()
        {

            @Override
            public Void call(Database sourceDatabase, Database targetDatabase, Transaction transaction)
            {
                databaseRunnable.run(sourceDatabase, targetDatabase, transaction);
                return null;
            }
        };
    }

    private void closeDatabase(Database database)
    {
        if (database != null)
        {
            try
            {
                database.close();
            }
            catch (Exception e)
            {
                _logger.error("Unable to close database", e);
            }
        }
    }

}
