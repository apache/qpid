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

import org.apache.log4j.Logger;

import com.sleepycat.je.Database;
import com.sleepycat.je.Environment;
import com.sleepycat.je.Transaction;

public abstract class AbstractStoreUpgrade implements StoreUpgrade
{
    private static final Logger _logger = Logger.getLogger(AbstractStoreUpgrade.class);
    protected static final String[] USER_FRIENDLY_NAMES = new String[] { "Exchanges", "Queues", "Queue bindings",
            "Message deliveries", "Message metadata", "Message content", "Bridges", "Links", "Distributed transactions" };

    protected void reportFinished(Environment environment, String[] databaseNames, String[] userFriendlyNames)
    {
        if (_logger.isInfoEnabled())
        {
            _logger.info("Upgraded:");
            List<String> databases = environment.getDatabaseNames();
            for (int i = 0; i < databaseNames.length; i++)
            {
                if (databases.contains(databaseNames[i]))
                {
                    _logger.info("    " + getRowCount(databaseNames[i], environment)  + " rows in " + userFriendlyNames[i]);
                }
            }
        }
    }


    protected void reportStarting(Environment environment, String[] databaseNames, String[] userFriendlyNames)
    {
        if (_logger.isInfoEnabled())
        {
            _logger.info("Upgrading:");
            List<String> databases = environment.getDatabaseNames();
            for (int i = 0; i < databaseNames.length; i++)
            {
                if (databases.contains(databaseNames[i]))
                {
                    _logger.info("    " + getRowCount(databaseNames[i], environment) + " rows from " + userFriendlyNames[i]);
                }
            }
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
