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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.OperationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.store.berkeleydb.BDBConfigurationStore;

public class Upgrader
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Upgrader.class);

    static final String VERSION_DB_NAME = "DB_VERSION";

    private Environment _environment;
    private ConfiguredObject<?> _parent;

    public Upgrader(Environment environment, ConfiguredObject<?> parent)
    {
        _environment = environment;
        _parent = parent;
    }

    public void upgradeIfNecessary()
    {
        boolean isEmpty = _environment.getDatabaseNames().isEmpty();
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);

        Database versionDb = null;
        try
        {
            versionDb = _environment.openDatabase(null, VERSION_DB_NAME, dbConfig);

            if(versionDb.count() == 0L)
            {

                int sourceVersion = isEmpty ? BDBConfigurationStore.VERSION: identifyOldStoreVersion();
                DatabaseEntry key = new DatabaseEntry();
                IntegerBinding.intToEntry(sourceVersion, key);
                DatabaseEntry value = new DatabaseEntry();
                LongBinding.longToEntry(System.currentTimeMillis(), value);

                versionDb.put(null, key, value);
            }

            int version = getSourceVersion(versionDb);

            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Source message store version is " + version);
            }

            if(version > BDBConfigurationStore.VERSION)
            {
                throw new StoreException("Database version " + version
                                            + " is higher than the most recent known version: "
                                            + BDBConfigurationStore.VERSION);
            }
            performUpgradeFromVersion(version, versionDb);
        }
        finally
        {
            if (versionDb != null)
            {
                versionDb.close();
            }
        }
    }

    int getSourceVersion(Database versionDb)
    {
        int version = -1;

        Cursor cursor = null;
        try
        {
            cursor = versionDb.openCursor(null, null);

            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();

            while(cursor.getNext(key, value, null) == OperationStatus.SUCCESS)
            {
                int ver = IntegerBinding.entryToInt(key);
                if(ver > version)
                {
                    version = ver;
                }
            }
        }
        finally
        {
            if(cursor != null)
            {
                cursor.close();
            }
        }


        return version;
    }

    void performUpgradeFromVersion(int sourceVersion, Database versionDb)
            throws StoreException
    {
        while(sourceVersion != BDBConfigurationStore.VERSION)
        {
            upgrade(sourceVersion, ++sourceVersion);
            DatabaseEntry key = new DatabaseEntry();
            IntegerBinding.intToEntry(sourceVersion, key);
            DatabaseEntry value = new DatabaseEntry();
            LongBinding.longToEntry(System.currentTimeMillis(), value);
            versionDb.put(null, key, value);
        }
    }

    void upgrade(final int fromVersion, final int toVersion) throws StoreException
    {
        try
        {
            @SuppressWarnings("unchecked")
            Class<StoreUpgrade> upgradeClass =
                    (Class<StoreUpgrade>) Class.forName("org.apache.qpid.server.store.berkeleydb.upgrade."
                                                        + "UpgradeFrom"+fromVersion+"To"+toVersion);
            Constructor<StoreUpgrade> ctr = upgradeClass.getConstructor();
            StoreUpgrade upgrade = ctr.newInstance();
            upgrade.performUpgrade(_environment, UpgradeInteractionHandler.DEFAULT_HANDLER, _parent);
        }
        catch (ClassNotFoundException e)
        {
            throw new StoreException("Unable to upgrade BDB data store from version " + fromVersion + " to version"
                                        + toVersion, e);
        }
        catch (NoSuchMethodException e)
        {
            throw new StoreException("Unable to upgrade BDB data store from version " + fromVersion + " to version"
                                        + toVersion, e);
        }
        catch (InvocationTargetException e)
        {
            throw new StoreException("Unable to upgrade BDB data store from version " + fromVersion + " to version"
                                        + toVersion, e);
        }
        catch (InstantiationException e)
        {
            throw new StoreException("Unable to upgrade BDB data store from version " + fromVersion + " to version"
                                        + toVersion, e);
        }
        catch (IllegalAccessException e)
        {
            throw new StoreException("Unable to upgrade BDB data store from version " + fromVersion + " to version"
                                        + toVersion, e);
        }
    }

    private int identifyOldStoreVersion() throws DatabaseException
    {
        int version = BDBConfigurationStore.VERSION;
        for (String databaseName : _environment.getDatabaseNames())
        {
            if (databaseName.contains("_v"))
            {
                int versionIndex = databaseName.indexOf("_v");
                if (versionIndex == -1)
                {
                    versionIndex = 1;
                }
                version = Integer.parseInt(databaseName.substring(versionIndex + 2));
                break;
            }
        }
        return version;
    }
}
