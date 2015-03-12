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

import java.util.List;

import com.sleepycat.je.Database;
import com.sleepycat.je.Environment;
import com.sleepycat.je.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractStoreUpgrade implements StoreUpgrade
{
    private static final Logger _logger = LoggerFactory.getLogger(AbstractStoreUpgrade.class);

    protected void reportFinished(Environment environment, int version)
    {
        _logger.info("Completed upgrade to version " + version);
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Upgraded:");
            reportDatabaseRowCount(environment);
        }
    }

    private void reportDatabaseRowCount(Environment environment)
    {
        List<String> databases = environment.getDatabaseNames();
        for (String database : databases)
        {
            _logger.debug("    " + getRowCount(database, environment)  + " rows in " + database);
        }
    }

    protected void reportStarting(Environment environment, int version)
    {
        _logger.info("Starting store upgrade from version " + version);
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Upgrading:");
            reportDatabaseRowCount(environment);
        }
    }

    private long getRowCount(String databaseName, Environment environment)
    {
        DatabaseCallable<Long> operation = new DatabaseCallable<Long>()
        {
            @Override
            public Long call(Database sourceDatabase, Database targetDatabase, Transaction transaction)
            {
                return sourceDatabase.count();
            }
        };
        return new DatabaseTemplate(environment, databaseName, null).call(operation);
    }

}
