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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Sequence;
import com.sleepycat.je.SequenceConfig;
import com.sleepycat.je.Transaction;
import org.apache.log4j.Logger;

import org.apache.qpid.server.store.StoreFuture;

public class StandardEnvironmentFacade implements EnvironmentFacade
{
    private static final Logger LOGGER = Logger.getLogger(StandardEnvironmentFacade.class);

    private final String _storePath;
    private final ConcurrentMap<String, Database> _cachedDatabases = new ConcurrentHashMap<>();
    private final ConcurrentMap<DatabaseEntry, Sequence> _cachedSequences = new ConcurrentHashMap<>();

    private Environment _environment;
    private final Committer _committer;
    private final File _environmentPath;

    public StandardEnvironmentFacade(StandardEnvironmentConfiguration configuration)
    {
        _storePath = configuration.getStorePath();

        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Creating environment at environment path " + _storePath);
        }

        _environmentPath = new File(_storePath);
        if (!_environmentPath.exists())
        {
            if (!_environmentPath.mkdirs())
            {
                throw new IllegalArgumentException("Environment path " + _environmentPath + " could not be read or created. "
                                                   + "Ensure the path is correct and that the permissions are correct.");
            }
        }

        String name = configuration.getName();
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);

        Map<String, String> params = new HashMap<>(EnvironmentFacade.ENVCONFIG_DEFAULTS);
        params.putAll(configuration.getParameters());

        for (Map.Entry<String, String> configItem : params.entrySet())
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Setting EnvironmentConfig key "
                             + configItem.getKey()
                             + " to '"
                             + configItem.getValue()
                             + "'");
            }
            envConfig.setConfigParam(configItem.getKey(), configItem.getValue());
        }

        envConfig.setExceptionListener(new LoggingAsyncExceptionListener());

        EnvHomeRegistry.getInstance().registerHome(_environmentPath);
        boolean success = false;
        try
        {
            _environment = new Environment(_environmentPath, envConfig);
            success = true;
        }
        finally
        {
            if (!success)
            {
                EnvHomeRegistry.getInstance().deregisterHome(_environmentPath);
            }
        }

        _committer =  new CoalescingCommiter(name, this);
        _committer.start();
    }


    @Override
    public Transaction beginTransaction()
    {
        return _environment.beginTransaction(null, null);
    }

    @Override
    public StoreFuture commit(com.sleepycat.je.Transaction tx, boolean syncCommit)
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
        return _committer.commit(tx, syncCommit);
    }

    @Override
    public void close()
    {
        try
        {
            _committer.stop();

            closeSequences();
            closeDatabases();
        }
        finally
        {
            try
            {
                closeEnvironment();
            }
            finally
            {
                EnvHomeRegistry.getInstance().deregisterHome(_environmentPath);
            }
        }
    }

    private void closeSequences()
    {
        RuntimeException firstThrownException = null;
        for (DatabaseEntry  sequenceKey : _cachedSequences.keySet())
        {
            try
            {
                closeSequence(sequenceKey);
            }
            catch(DatabaseException de)
            {
                if (firstThrownException == null)
                {
                    firstThrownException = de;
                }
            }
        }
        if (firstThrownException != null)
        {
            throw firstThrownException;
        }
    }

    private void closeDatabases()
    {
        RuntimeException firstThrownException = null;
        for (String databaseName : _cachedDatabases.keySet())
        {
            try
            {
                closeDatabase(databaseName);
            }
            catch(DatabaseException e)
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
    public DatabaseException handleDatabaseException(String contextMessage, DatabaseException e)
    {
        if (_environment != null && !_environment.isValid())
        {
            closeEnvironmentSafely();
        }
        return e;
    }

    @Override
    public Database openDatabase(String name, DatabaseConfig databaseConfig)
    {
        Database cachedHandle = _cachedDatabases.get(name);
        if (cachedHandle == null)
        {
            Database handle = _environment.openDatabase(null, name, databaseConfig);
            Database existingHandle = _cachedDatabases.putIfAbsent(name, handle);
            if (existingHandle == null)
            {
                cachedHandle = handle;
            }
            else
            {
                cachedHandle = existingHandle;
                handle.close();
            }
        }
        return cachedHandle;
    }


    @Override
    public Database clearDatabase(String name, DatabaseConfig databaseConfig)
    {
        closeDatabase(name);
        _environment.removeDatabase(null, name);
        return openDatabase(name, databaseConfig);
    }

    @Override
    public Sequence openSequence(final Database database,
                                 final DatabaseEntry sequenceKey,
                                 final SequenceConfig sequenceConfig)
    {
        Sequence cachedSequence = _cachedSequences.get(sequenceKey);
        if (cachedSequence == null)
        {
            Sequence handle = database.openSequence(null, sequenceKey, sequenceConfig);
            Sequence existingHandle = _cachedSequences.putIfAbsent(sequenceKey, handle);
            if (existingHandle == null)
            {
                cachedSequence = handle;
            }
            else
            {
                cachedSequence = existingHandle;
                handle.close();
            }
        }
        return cachedSequence;
    }


    private void closeSequence(final DatabaseEntry sequenceKey)
    {
        Sequence cachedHandle = _cachedSequences.remove(sequenceKey);
        if (cachedHandle != null)
        {
            cachedHandle.close();
        }
    }

    @Override
    public void closeDatabase(final String name)
    {
        Database cachedHandle = _cachedDatabases.remove(name);
        if (cachedHandle != null)
        {
            cachedHandle.close();
        }
    }

    @Override
    public String getStoreLocation()
    {
        return _storePath;
    }
}
