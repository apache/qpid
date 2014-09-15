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

import static org.apache.qpid.server.store.berkeleydb.BDBUtils.DEFAULT_DATABASE_CONFIG;
import static org.apache.qpid.server.store.berkeleydb.BDBUtils.abortTransactionSafely;
import static org.apache.qpid.server.store.berkeleydb.BDBUtils.closeCursorSafely;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import org.apache.log4j.Logger;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.FileBasedSettings;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreProvider;
import org.apache.qpid.server.store.SizeMonitoringSettings;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.store.berkeleydb.entry.HierarchyKey;
import org.apache.qpid.server.store.berkeleydb.tuple.ConfiguredObjectBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.HierarchyKeyBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.UUIDTupleBinding;
import org.apache.qpid.server.store.berkeleydb.upgrade.Upgrader;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;
import org.apache.qpid.util.FileUtils;

/**
 * Implementation of a DurableConfigurationStore backed by BDB JE
 * that also provides a MessageStore.
 */
public class BDBConfigurationStore implements MessageStoreProvider, DurableConfigurationStore
{
    private static final Logger LOGGER = Logger.getLogger(BDBConfigurationStore.class);

    public static final int VERSION = 8;
    private static final String CONFIGURED_OBJECTS_DB_NAME = "CONFIGURED_OBJECTS";
    private static final String CONFIGURED_OBJECT_HIERARCHY_DB_NAME = "CONFIGURED_OBJECT_HIERARCHY";

    private final AtomicBoolean _configurationStoreOpen = new AtomicBoolean();

    private final EnvironmentFacadeFactory _environmentFacadeFactory;

    private final ProvidedBDBMessageStore _providedMessageStore = new ProvidedBDBMessageStore();

    private EnvironmentFacade _environmentFacade;

    private String _storeLocation;
    private ConfiguredObject<?> _parent;
    private final Class<? extends ConfiguredObject> _rootClass;
    private boolean _overwrite;
    private ConfiguredObjectRecord[] _initialRecords;

    public BDBConfigurationStore(final Class<? extends ConfiguredObject> rootClass)
    {
        this(new StandardEnvironmentFacadeFactory(), rootClass);
    }

    public BDBConfigurationStore(EnvironmentFacadeFactory environmentFacadeFactory, Class<? extends ConfiguredObject> rootClass)
    {
        _environmentFacadeFactory = environmentFacadeFactory;
        _rootClass = rootClass;
    }

    @Override
    public void openConfigurationStore(ConfiguredObject<?> parent,
                                       final boolean overwrite,
                                       final ConfiguredObjectRecord... initialRecords)
    {
        if (_configurationStoreOpen.compareAndSet(false,  true))
        {
            _parent = parent;

            if (_environmentFacade == null)
            {
                _environmentFacade = _environmentFacadeFactory.createEnvironmentFacade(parent);
                _storeLocation = _environmentFacade.getStoreLocation();
                _overwrite = overwrite;
                _initialRecords = initialRecords;
            }
            else
            {
                throw new IllegalStateException("The database have been already opened as message store");
            }
        }
    }

    private void clearConfigurationRecords()
    {
        checkConfigurationStoreOpen();

        _environmentFacade.clearDatabase(CONFIGURED_OBJECTS_DB_NAME, DEFAULT_DATABASE_CONFIG);
        _environmentFacade.clearDatabase(CONFIGURED_OBJECT_HIERARCHY_DB_NAME, DEFAULT_DATABASE_CONFIG);
    }

    @Override
    public void upgradeStoreStructure() throws StoreException
    {
        try
        {
            new Upgrader(_environmentFacade.getEnvironment(), _parent).upgradeIfNecessary();
            if(_overwrite)
            {
                clearConfigurationRecords();
                _overwrite = false;
            }
            if(getConfiguredObjectsDb().count() == 0l)
            {
                update(true, _initialRecords);
            }
            _initialRecords = new ConfiguredObjectRecord[0];
        }
        catch(DatabaseException e)
        {
            throw _environmentFacade.handleDatabaseException("Cannot upgrade store", e);
        }
    }
    @Override
    public void visitConfiguredObjectRecords(ConfiguredObjectRecordHandler handler)
    {
        checkConfigurationStoreOpen();

        try
        {
            handler.begin();
            doVisitAllConfiguredObjectRecords(handler);
            handler.end();
        }
        catch (DatabaseException e)
        {
            throw _environmentFacade.handleDatabaseException("Cannot visit configured object records", e);
        }
    }

    private void doVisitAllConfiguredObjectRecords(ConfiguredObjectRecordHandler handler)
    {
        Map<UUID, BDBConfiguredObjectRecord> configuredObjects = new HashMap<UUID, BDBConfiguredObjectRecord>();
        Cursor objectsCursor = null;
        Cursor hierarchyCursor = null;
        try
        {
            objectsCursor = getConfiguredObjectsDb().openCursor(null, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();


            while (objectsCursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                UUID id = UUIDTupleBinding.getInstance().entryToObject(key);

                BDBConfiguredObjectRecord configuredObject =
                        (BDBConfiguredObjectRecord) new ConfiguredObjectBinding(id).entryToObject(value);
                configuredObjects.put(configuredObject.getId(), configuredObject);
            }

            // set parents
            hierarchyCursor = getConfiguredObjectHierarchyDb().openCursor(null, null);
            while (hierarchyCursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                HierarchyKey hk = HierarchyKeyBinding.getInstance().entryToObject(key);
                UUID parentId = UUIDTupleBinding.getInstance().entryToObject(value);
                BDBConfiguredObjectRecord child = configuredObjects.get(hk.getChildId());
                if(child != null)
                {
                    ConfiguredObjectRecord parent = configuredObjects.get(parentId);
                    if(parent != null)
                    {
                        child.addParent(hk.getParentType(), parent);
                    }
                }
            }
        }
        finally
        {
            closeCursorSafely(objectsCursor, _environmentFacade);
            closeCursorSafely(hierarchyCursor, _environmentFacade);
        }

        for (ConfiguredObjectRecord record : configuredObjects.values())
        {
            boolean shouldContinue = handler.handle(record);
            if (!shouldContinue)
            {
                break;
            }
        }

    }

    public EnvironmentFacade getEnvironmentFacade()
    {
        return _environmentFacade;
    }

    @Override
    public void closeConfigurationStore() throws StoreException
    {
        if (_configurationStoreOpen.compareAndSet(true, false))
        {
            if (!_providedMessageStore.isMessageStoreOpen())
            {
                closeEnvironment();
            }
        }
    }

    private void closeEnvironment()
    {
        if (_environmentFacade != null)
        {
            try
            {
                _environmentFacade.close();
                _environmentFacade = null;
            }
            catch(DatabaseException e)
            {
                throw new StoreException("Exception occurred on message store close", e);
            }
        }
    }

    @Override
    public void create(ConfiguredObjectRecord configuredObject) throws StoreException
    {
        checkConfigurationStoreOpen();

        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Create " + configuredObject);
        }

        com.sleepycat.je.Transaction txn = null;
        try
        {
            txn = _environmentFacade.getEnvironment().beginTransaction(null, null);
            storeConfiguredObjectEntry(txn, configuredObject);
            txn.commit();
            txn = null;
        }
        catch (DatabaseException e)
        {
            throw _environmentFacade.handleDatabaseException("Error creating configured object " + configuredObject
                    + " in database: " + e.getMessage(), e);
        }
        finally
        {
            if (txn != null)
            {
                abortTransactionSafely(txn, _environmentFacade);
            }
        }
    }

    @Override
    public UUID[] remove(final ConfiguredObjectRecord... objects) throws StoreException
    {
        checkConfigurationStoreOpen();

        com.sleepycat.je.Transaction txn = null;
        try
        {
            txn = _environmentFacade.getEnvironment().beginTransaction(null, null);

            Collection<UUID> removed = new ArrayList<UUID>(objects.length);
            for(ConfiguredObjectRecord record : objects)
            {
                if(removeConfiguredObject(txn, record) == OperationStatus.SUCCESS)
                {
                    removed.add(record.getId());
                }
            }

            txn.commit();
            txn = null;
            return removed.toArray(new UUID[removed.size()]);
        }
        catch (DatabaseException e)
        {
            throw _environmentFacade.handleDatabaseException("Error deleting configured objects from database", e);
        }
        finally
        {
            if (txn != null)
            {
                abortTransactionSafely(txn, _environmentFacade);
            }
        }
    }

    @Override
    public void update(boolean createIfNecessary, ConfiguredObjectRecord... records) throws StoreException
    {
        checkConfigurationStoreOpen();

        com.sleepycat.je.Transaction txn = null;
        try
        {
            txn = _environmentFacade.getEnvironment().beginTransaction(null, null);
            for(ConfiguredObjectRecord record : records)
            {
                update(createIfNecessary, record, txn);
            }
            txn.commit();
            txn = null;
        }
        catch (DatabaseException e)
        {
            throw _environmentFacade.handleDatabaseException("Error updating configuration details within the store: " + e,e);
        }
        finally
        {
            if (txn != null)
            {
                abortTransactionSafely(txn, _environmentFacade);
            }
        }
    }

    private void update(boolean createIfNecessary, ConfiguredObjectRecord record, com.sleepycat.je.Transaction txn) throws StoreException
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Updating, creating " + createIfNecessary + " : "  + record);
        }

        DatabaseEntry key = new DatabaseEntry();
        UUIDTupleBinding keyBinding = UUIDTupleBinding.getInstance();
        keyBinding.objectToEntry(record.getId(), key);

        DatabaseEntry value = new DatabaseEntry();
        DatabaseEntry newValue = new DatabaseEntry();
        ConfiguredObjectBinding configuredObjectBinding = ConfiguredObjectBinding.getInstance();

        OperationStatus status = getConfiguredObjectsDb().get(txn, key, value, LockMode.DEFAULT);
        final boolean isNewRecord = status == OperationStatus.NOTFOUND;
        if (status == OperationStatus.SUCCESS || (createIfNecessary && isNewRecord))
        {
            // write the updated entry to the store
            configuredObjectBinding.objectToEntry(record, newValue);
            status = getConfiguredObjectsDb().put(txn, key, newValue);
            if (status != OperationStatus.SUCCESS)
            {
                throw new StoreException("Error updating configuration details within the store: " + status);
            }
            if(isNewRecord)
            {
                writeHierarchyRecords(txn, record);
            }
        }
        else if (status != OperationStatus.NOTFOUND)
        {
            throw new StoreException("Error finding configuration details within the store: " + status);
        }
    }


    private void storeConfiguredObjectEntry(final Transaction txn, ConfiguredObjectRecord configuredObject) throws StoreException
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Storing configured object record: " + configuredObject);
        }
        DatabaseEntry key = new DatabaseEntry();
        UUIDTupleBinding uuidBinding = UUIDTupleBinding.getInstance();
        uuidBinding.objectToEntry(configuredObject.getId(), key);
        DatabaseEntry value = new DatabaseEntry();
        ConfiguredObjectBinding queueBinding = ConfiguredObjectBinding.getInstance();

        queueBinding.objectToEntry(configuredObject, value);
        try
        {
            OperationStatus status = getConfiguredObjectsDb().put(txn, key, value);
            if (status != OperationStatus.SUCCESS)
            {
                throw new StoreException("Error writing configured object " + configuredObject + " to database: "
                        + status);
            }
            writeHierarchyRecords(txn, configuredObject);
        }
        catch (DatabaseException e)
        {
            throw _environmentFacade.handleDatabaseException("Error writing configured object " + configuredObject
                    + " to database: " + e.getMessage(), e);
        }
    }

    private void writeHierarchyRecords(final Transaction txn, final ConfiguredObjectRecord configuredObject)
    {
        OperationStatus status;
        HierarchyKeyBinding hierarchyBinding = HierarchyKeyBinding.getInstance();
        DatabaseEntry hierarchyKey = new DatabaseEntry();
        DatabaseEntry hierarchyValue = new DatabaseEntry();

        for(Map.Entry<String, UUID> parent : configuredObject.getParents().entrySet())
        {

            hierarchyBinding.objectToEntry(new HierarchyKey(configuredObject.getId(), parent.getKey()), hierarchyKey);
            UUIDTupleBinding.getInstance().objectToEntry(parent.getValue(), hierarchyValue);
            status = getConfiguredObjectHierarchyDb().put(txn, hierarchyKey, hierarchyValue);
            if (status != OperationStatus.SUCCESS)
            {
                throw new StoreException("Error writing configured object " + configuredObject + " parent record to database: "
                                         + status);
            }
        }
    }

    private OperationStatus removeConfiguredObject(Transaction tx, ConfiguredObjectRecord record) throws StoreException
    {
        UUID id = record.getId();
        Map<String, UUID> parents = record.getParents();

        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Removing configured object: " + id);
        }
        DatabaseEntry key = new DatabaseEntry();

        UUIDTupleBinding uuidBinding = UUIDTupleBinding.getInstance();
        uuidBinding.objectToEntry(id, key);
        OperationStatus status = getConfiguredObjectsDb().delete(tx, key);
        if(status == OperationStatus.SUCCESS)
        {
            for(String parentType : parents.keySet())
            {
                DatabaseEntry hierarchyKey = new DatabaseEntry();
                HierarchyKeyBinding keyBinding = HierarchyKeyBinding.getInstance();
                keyBinding.objectToEntry(new HierarchyKey(record.getId(), parentType), hierarchyKey);
                getConfiguredObjectHierarchyDb().delete(tx, hierarchyKey);
            }
        }
        return status;
    }

    @Override
    public MessageStore getMessageStore()
    {
        return _providedMessageStore;
    }

    private void checkConfigurationStoreOpen()
    {
        if (!isConfigurationStoreOpen())
        {
            throw new IllegalStateException("Configuration store is not open");
        }
    }

    @Override
    public void onDelete(ConfiguredObject<?> parent)
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Deleting store " + _storeLocation);
        }

        FileBasedSettings fileBasedSettings = (FileBasedSettings)parent;
        String storePath = fileBasedSettings.getStorePath();

        if (storePath != null)
        {
            File configFile = new File(storePath);
            if (!FileUtils.delete(configFile, true))
            {
                LOGGER.info("Failed to delete the store at location " + storePath);
            }
        }

        _storeLocation = null;
    }

    private boolean isConfigurationStoreOpen()
    {
        return _configurationStoreOpen.get();
    }

    private Database getConfiguredObjectsDb()
    {
        return _environmentFacade.openDatabase(CONFIGURED_OBJECTS_DB_NAME, DEFAULT_DATABASE_CONFIG);
    }

    private Database getConfiguredObjectHierarchyDb()
    {
        return _environmentFacade.openDatabase(CONFIGURED_OBJECT_HIERARCHY_DB_NAME, DEFAULT_DATABASE_CONFIG);
    }

    class ProvidedBDBMessageStore extends AbstractBDBMessageStore
    {
        private final AtomicBoolean _messageStoreOpen = new AtomicBoolean();

        private long _persistentSizeLowThreshold;
        private long _persistentSizeHighThreshold;

        private ConfiguredObject<?> _parent;

        @Override
        public void openMessageStore(final ConfiguredObject<?> parent)
        {
            if (_messageStoreOpen.compareAndSet(false, true))
            {
                _parent = parent;

                final SizeMonitoringSettings sizeMonitorSettings = (SizeMonitoringSettings) parent;
                _persistentSizeHighThreshold = sizeMonitorSettings.getStoreOverfullSize();
                _persistentSizeLowThreshold = sizeMonitorSettings.getStoreUnderfullSize();

                if (_persistentSizeLowThreshold > _persistentSizeHighThreshold || _persistentSizeLowThreshold < 0l)
                {
                    _persistentSizeLowThreshold = _persistentSizeHighThreshold;
                }
            }
        }

        public boolean isMessageStoreOpen()
        {
            return _messageStoreOpen.get();
        }

        @Override
        public void closeMessageStore()
        {
            _messageStoreOpen.set(false);
        }

        @Override
        public EnvironmentFacade getEnvironmentFacade()
        {
            return _environmentFacade;
        }

        @Override
        public void onDelete(ConfiguredObject<?> parent)
        {
            // Nothing to do, message store will be deleted when configuration store is deleted
        }

        @Override
        public String getStoreLocation()
        {
            return BDBConfigurationStore.this._storeLocation;
        }

        @Override
        protected long getPersistentSizeLowThreshold()
        {
            return _persistentSizeLowThreshold;
        }

        @Override
        protected long getPersistentSizeHighThreshold()
        {
            return _persistentSizeHighThreshold;
        }

        @Override
        protected ConfiguredObject<?> getParent()
        {
            return _parent;
        }

        @Override
        protected void checkMessageStoreOpen()
        {
            if (!_messageStoreOpen.get())
            {
                throw new IllegalStateException("Message store is not open");
            }
        }

        @Override
        protected Logger getLogger()
        {
            return LOGGER;
        }
    }

}
