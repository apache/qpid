/*
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
 */

package org.apache.qpid.server.store.berkeleydb;

import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.store.StoreException;

public class BDBUtils
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BDBUtils.class);

    public static final DatabaseConfig DEFAULT_DATABASE_CONFIG = new DatabaseConfig().setTransactional(true).setAllowCreate(true);

    public static void closeCursorSafely(Cursor cursor, final EnvironmentFacade environmentFacade) throws StoreException
    {
        if (cursor != null)
        {
            try
            {
                cursor.close();
            }
            catch (RuntimeException e)
            {
                // We need the possible side effect of the facade restarting the environment but don't care about the exception
                throw environmentFacade.handleDatabaseException("Cannot close cursor", e);
            }
        }
    }

    public static void abortTransactionSafely(Transaction tx, EnvironmentFacade environmentFacade)
    {
        try
        {
            if (tx != null)
            {
                tx.abort();
            }
        }
        catch (RuntimeException e)
        {
            // We need the possible side effect of the facade restarting the environment but don't care about the exception
            environmentFacade.handleDatabaseException("Cannot abort transaction", e);
        }
    }

    public synchronized static void runCleaner(final Environment environment)
    {
        boolean cleanerWasRunning = Boolean.parseBoolean(environment.getConfig().getConfigParam(EnvironmentConfig.ENV_RUN_CLEANER));

        try
        {
            if (cleanerWasRunning)
            {
                environment.getConfig().setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, Boolean.FALSE.toString());
            }

            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Cleaning logs");
            }

            boolean cleaned = false;
            while (environment.cleanLog() > 0)
            {
                cleaned = true;
            }
            if (cleaned)
            {
                LOGGER.debug("Cleaned log");

                CheckpointConfig force = new CheckpointConfig();
                force.setForce(true);
                environment.checkpoint(force);

                LOGGER.debug("Checkpoint force complete");
            }
        }
        finally
        {
            if (cleanerWasRunning)
            {
                environment.getConfig().setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, Boolean.TRUE.toString());
            }
        }

    }
}
