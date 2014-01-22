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
package org.apache.qpid.server.store.berkeleydb;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQStoreException;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

public class StandardEnvironmentFacade implements EnvironmentFacade
{
    private static final Logger LOGGER = Logger.getLogger(StandardEnvironmentFacade.class);
    public static final String TYPE = "BDB";

    private final String _storePath;
    private final Map<String, Database> _databases = new HashMap<String, Database>();

    private Environment _environment;

    public StandardEnvironmentFacade(String storePath, Map<String, String> attributes)
    {
        _storePath = storePath;

        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Creating environment at environment path " + _storePath);
        }

        File environmentPath = new File(storePath);
        if (!environmentPath.exists())
        {
            if (!environmentPath.mkdirs())
            {
                throw new IllegalArgumentException("Environment path " + environmentPath + " could not be read or created. "
                                                   + "Ensure the path is correct and that the permissions are correct.");
            }
        }

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);

        for (Map.Entry<String, String> configItem : attributes.entrySet())
        {
            LOGGER.debug("Setting EnvironmentConfig key " + configItem.getKey() + " to '" + configItem.getValue() + "'");
            envConfig.setConfigParam(configItem.getKey(), configItem.getValue());
        }

        envConfig.setExceptionListener(new LoggingAsyncExceptionListener());

        _environment = new Environment(environmentPath, envConfig);
    }

    @Override
    public void commit(com.sleepycat.je.Transaction tx) throws AMQStoreException
    {
        try
        {
            tx.commitNoSync();
        }
        catch (DatabaseException de)
        {
            LOGGER.error("Got DatabaseException on commit, closing environment", de);

            closeEnvironmentSafely();

            throw handleDatabaseException("Got DatabaseException on commit", de);
        }
    }

    @Override
    public void close()
    {
        closeDatabases();
        closeEnvironment();
    }

    private void closeDatabases()
    {
        RuntimeException firstThrownException = null;
        for (Database database : _databases.values())
        {
            try
            {
                database.close();
            }
            catch(RuntimeException e)
            {
                if (firstThrownException == null)
                {
                    firstThrownException = e;
                }
            }
        }
        if (firstThrownException != null)
        {
            throw firstThrownException;
        }
    }

    private void closeEnvironmentSafely()
    {
        if (_environment != null)
        {
            if (_environment.isValid())
            {
                try
                {
                    closeDatabases();
                }
                catch(Exception e)
                {
                    LOGGER.error("Exception closing environment databases", e);
                }
            }
            try
            {
                _environment.close();
            }
            catch (DatabaseException ex)
            {
                LOGGER.error("Exception closing store environment", ex);
            }
            catch (IllegalStateException ex)
            {
                LOGGER.error("Exception closing store environment", ex);
            }
            finally
            {
                _environment = null;
            }
        }
    }

    @Override
    public Environment getEnvironment()
    {
        return _environment;
    }

    private void closeEnvironment()
    {
        if (_environment != null)
        {
            // Clean the log before closing. This makes sure it doesn't contain
            // redundant data. Closing without doing this means the cleaner may
            // not get a chance to finish.
            try
            {
                _environment.cleanLog();
            }
            finally
            {
                _environment.close();
                _environment = null;
            }
        }
    }

    @Override
    public AMQStoreException handleDatabaseException(String contextMessage, DatabaseException e)
    {
        if (_environment != null && !_environment.isValid())
        {
            closeEnvironmentSafely();
        }
        return new AMQStoreException(contextMessage, e);
    }

    @Override
    public void openDatabases(DatabaseConfig dbConfig, String... databaseNames)
    {
        for (String databaseName : databaseNames)
        {
            Database database = _environment.openDatabase(null, databaseName, dbConfig);
            _databases .put(databaseName, database);
        }
    }

    @Override
    public Database getOpenDatabase(String name)
    {
        Database database = _databases.get(name);
        if (database == null)
        {
            throw new IllegalArgumentException("Database with name '" + name + "' has not been opened");
        }
        return database;
    }

    @Override
    public Committer createCommitter(String name)
    {
        return new CoalescingCommiter(name, this);
    }

    @Override
    public String getStoreLocation()
    {
        return _storePath;
    }

}
