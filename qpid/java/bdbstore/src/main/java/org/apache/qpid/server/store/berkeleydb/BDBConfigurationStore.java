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
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sleepycat.bind.tuple.ByteBinding;
import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockConflictException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Sequence;
import com.sleepycat.je.SequenceConfig;
import com.sleepycat.je.Transaction;
import org.apache.log4j.Logger;

import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.Event;
import org.apache.qpid.server.store.EventListener;
import org.apache.qpid.server.store.EventManager;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreProvider;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.StoredMemoryMessage;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.store.Xid;
import org.apache.qpid.server.store.berkeleydb.entry.HierarchyKey;
import org.apache.qpid.server.store.berkeleydb.entry.PreparedTransaction;
import org.apache.qpid.server.store.berkeleydb.entry.QueueEntryKey;
import org.apache.qpid.server.store.berkeleydb.tuple.ConfiguredObjectBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.ContentBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.HierarchyKeyBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.MessageMetaDataBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.PreparedTransactionBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.QueueEntryBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.UUIDTupleBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.XidBinding;
import org.apache.qpid.server.store.berkeleydb.upgrade.Upgrader;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;
import org.apache.qpid.server.store.handler.DistributedTransactionHandler;
import org.apache.qpid.server.store.handler.MessageHandler;
import org.apache.qpid.server.store.handler.MessageInstanceHandler;
import org.apache.qpid.util.FileUtils;

/**
 * BDBMessageStore implements a persistent {@link MessageStore} using the BDB high performance log.
 *
 * <p/><table id="crc"><caption>CRC Card</caption> <tr><th> Responsibilities <th> Collaborations <tr><td> Accept
 * transaction boundary demarcations: Begin, Commit, Abort. <tr><td> Store and remove queues. <tr><td> Store and remove
 * exchanges. <tr><td> Store and remove messages. <tr><td> Bind and unbind queues to exchanges. <tr><td> Enqueue and
 * dequeue messages to queues. <tr><td> Generate message identifiers. </table>
 */
public class BDBConfigurationStore implements MessageStoreProvider, DurableConfigurationStore
{
    public static final DatabaseConfig DEFAULT_DATABASE_CONFIG = new DatabaseConfig().setTransactional(true).setAllowCreate(true);

    private static final Logger LOGGER = Logger.getLogger(BDBConfigurationStore.class);

    public static final int VERSION = 8;
    private static String CONFIGURED_OBJECTS_DB_NAME = "CONFIGURED_OBJECTS";
    private static String CONFIGURED_OBJECT_HIERARCHY_DB_NAME = "CONFIGURED_OBJECT_HIERARCHY";

    private static String MESSAGE_META_DATA_DB_NAME = "MESSAGE_METADATA";
    private static String MESSAGE_META_DATA_SEQ_DB_NAME = "MESSAGE_METADATA.SEQ";
    private static String MESSAGE_CONTENT_DB_NAME = "MESSAGE_CONTENT";
    private static String DELIVERY_DB_NAME = "QUEUE_ENTRIES";

    //TODO: Add upgrader to remove BRIDGES and LINKS
    private static String BRIDGEDB_NAME = "BRIDGES";
    private static String LINKDB_NAME = "LINKS";
    private static String XID_DB_NAME = "XIDS";
    private EnvironmentFacade _environmentFacade;

    private static final DatabaseEntry MESSAGE_METADATA_SEQ_KEY = new DatabaseEntry("MESSAGE_METADATA_SEQ_KEY".getBytes(
            Charset.forName("UTF-8")));

    private static final SequenceConfig MESSAGE_METADATA_SEQ_CONFIG = SequenceConfig.DEFAULT.
            setAllowCreate(true).
            setInitialValue(1).
            setWrap(true).
            setCacheSize(100000);

    private final AtomicBoolean _messageStoreOpen = new AtomicBoolean();
    private final AtomicBoolean _configurationStoreOpen = new AtomicBoolean();

    private final EnvironmentFacadeFactory _environmentFacadeFactory;

    private volatile Committer _committer;

    private boolean _isMessageStoreProvider;

    private String _storeLocation;
    private final BDBMessageStore _messageStoreFacade = new BDBMessageStore();
    private ConfiguredObject<?> _parent;

    public BDBConfigurationStore()
    {
        this(new StandardEnvironmentFacadeFactory());
    }

    public BDBConfigurationStore(EnvironmentFacadeFactory environmentFacadeFactory)
    {
        _environmentFacadeFactory = environmentFacadeFactory;
    }

    @Override
    public void openConfigurationStore(ConfiguredObject<?> parent, Map<String, Object> storeSettings)
    {
        if (_configurationStoreOpen.compareAndSet(false,  true))
        {
            _parent = parent;

            if (_environmentFacade == null)
            {
                _environmentFacade = _environmentFacadeFactory.createEnvironmentFacade(storeSettings);
                _storeLocation = _environmentFacade.getStoreLocation();
            }
            else
            {
                throw new IllegalStateException("The database have been already opened as message store");
            }
        }
    }

    @Override
    public void upgradeStoreStructure() throws StoreException
    {
        try
        {
            new Upgrader(_environmentFacade.getEnvironment(), _parent).upgradeIfNecessary();
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
            closeCursorSafely(objectsCursor);
            closeCursorSafely(hierarchyCursor);
        }

        for (ConfiguredObjectRecord record : configuredObjects.values())
        {
            boolean shoudlContinue = handler.handle(record);
            if (!shoudlContinue)
            {
                break;
            }
        }

    }

    public String getStoreLocation()
    {
        return _storeLocation;
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
            if (!_messageStoreOpen.get())
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

    private void closeCursorSafely(Cursor cursor) throws StoreException
    {
        if (cursor != null)
        {
            try
            {
                cursor.close();
            }
            catch(DatabaseException e)
            {
                throw _environmentFacade.handleDatabaseException("Cannot close cursor", e);
            }
        }
    }

    private void abortTransactionIgnoringException(String errorMessage, com.sleepycat.je.Transaction tx)
    {
        try
        {
            if (tx != null)
            {
                tx.abort();
            }
        }
        catch (DatabaseException e1)
        {
            // We need the possible side effect of the handler restarting the environment but don't care about the exception
            _environmentFacade.handleDatabaseException(null, e1);
            LOGGER.warn(errorMessage, e1);
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
                abortTransactionIgnoringException("Error creating configured object", txn);
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
                abortTransactionIgnoringException("Error deleting configured objects", txn);
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
                abortTransactionIgnoringException("Error updating configuration details within the store", txn);
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

        for(Map.Entry<String, ConfiguredObjectRecord> parent : configuredObject.getParents().entrySet())
        {

            hierarchyBinding.objectToEntry(new HierarchyKey(configuredObject.getId(), parent.getKey()), hierarchyKey);
            UUIDTupleBinding.getInstance().objectToEntry(parent.getValue().getId(), hierarchyValue);
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
        Map<String, ConfiguredObjectRecord> parents = record.getParents();

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
        return _messageStoreFacade;
    }

    private void checkConfigurationStoreOpen()
    {
        if (!_configurationStoreOpen.get())
        {
            throw new IllegalStateException("Configuration store is not open");
        }
    }

    public void onDelete()
    {
        if (!_configurationStoreOpen.get() && !_messageStoreOpen.get())
        {
            if (_storeLocation != null)
            {
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Deleting store " + _storeLocation);
                }

                File location = new File(_storeLocation);
                if (location.exists())
                {
                    if (!FileUtils.delete(location, true))
                    {
                        LOGGER.error("Cannot delete " + _storeLocation);
                    }
                }
            }
        }
    }

    private Database getConfiguredObjectsDb()
    {
        return _environmentFacade.openDatabase(CONFIGURED_OBJECTS_DB_NAME, DEFAULT_DATABASE_CONFIG);
    }

    private Database getConfiguredObjectHierarchyDb()
    {
        return _environmentFacade.openDatabase(CONFIGURED_OBJECT_HIERARCHY_DB_NAME, DEFAULT_DATABASE_CONFIG);
    }

    private Database getMessageContentDb()
    {
        return _environmentFacade.openDatabase(MESSAGE_CONTENT_DB_NAME, DEFAULT_DATABASE_CONFIG);
    }

    private Database getMessageMetaDataDb()
    {
        return _environmentFacade.openDatabase(MESSAGE_META_DATA_DB_NAME, DEFAULT_DATABASE_CONFIG);
    }

    private Database getMessageMetaDataSeqDb()
    {
        return _environmentFacade.openDatabase(MESSAGE_META_DATA_SEQ_DB_NAME, DEFAULT_DATABASE_CONFIG);
    }

    private Database getDeliveryDb()
    {
        return _environmentFacade.openDatabase(DELIVERY_DB_NAME, DEFAULT_DATABASE_CONFIG);
    }

    private Database getXidDb()
    {
        return _environmentFacade.openDatabase(XID_DB_NAME, DEFAULT_DATABASE_CONFIG);
    }

    class BDBMessageStore implements MessageStore
    {
        private static final int LOCK_RETRY_ATTEMPTS = 5;

        private final EventManager _eventManager = new EventManager();

        private boolean _limitBusted;
        private long _persistentSizeLowThreshold;
        private long _persistentSizeHighThreshold;
        private long _totalStoreSize;

        private ConfiguredObject<?> _parent;

        @Override
        public void openMessageStore(final ConfiguredObject<?> parent, final Map<String, Object> messageStoreSettings)
        {
            if (_messageStoreOpen.compareAndSet(false, true))
            {
                _parent = parent;

                Object overfullAttr = messageStoreSettings.get(OVERFULL_SIZE);
                Object underfullAttr = messageStoreSettings.get(UNDERFULL_SIZE);

                _persistentSizeHighThreshold = overfullAttr == null ? -1l :
                                               overfullAttr instanceof Number ? ((Number) overfullAttr).longValue() : Long.parseLong(overfullAttr.toString());
                _persistentSizeLowThreshold = underfullAttr == null ? _persistentSizeHighThreshold :
                                               underfullAttr instanceof Number ? ((Number) underfullAttr).longValue() : Long.parseLong(underfullAttr.toString());

                if(_persistentSizeLowThreshold > _persistentSizeHighThreshold || _persistentSizeLowThreshold < 0l)
                {
                    _persistentSizeLowThreshold = _persistentSizeHighThreshold;
                }

                if (_environmentFacade == null)
                {
                    _environmentFacade = _environmentFacadeFactory.createEnvironmentFacade(messageStoreSettings
                                                                                          );
                    _storeLocation = _environmentFacade.getStoreLocation();
                }

                _committer = _environmentFacade.createCommitter(parent.getName());
                _committer.start();
            }
        }

        @Override
        public void upgradeStoreStructure() throws StoreException
        {
            try
            {
                new Upgrader(_environmentFacade.getEnvironment(), _parent).upgradeIfNecessary();
            }
            catch(DatabaseException e)
            {
                throw _environmentFacade.handleDatabaseException("Cannot upgrade store", e);
            }

            // TODO this relies on the fact that the VH will call upgrade just before putting the VH into service.
            _totalStoreSize = getSizeOnDisk();
        }

        @Override
        public <T extends StorableMessageMetaData> StoredMessage<T> addMessage(T metaData)
        {

            Sequence mmdSeq = null;
            try
            {
                mmdSeq = getMessageMetaDataSeqDb().openSequence(null, MESSAGE_METADATA_SEQ_KEY, MESSAGE_METADATA_SEQ_CONFIG);
                long newMessageId = mmdSeq.get(null, 1);

                if (metaData.isPersistent())
                {
                    return (StoredMessage<T>) new StoredBDBMessage(newMessageId, metaData);
                }
                else
                {
                    return new StoredMemoryMessage<T>(newMessageId, metaData);
                }
            }
            finally
            {
                if (mmdSeq != null)
                {
                    mmdSeq.close();
                }
            }
        }

        @Override
        public boolean isPersistent()
        {
            return true;
        }

        @Override
        public org.apache.qpid.server.store.Transaction newTransaction()
        {
            checkMessageStoreOpen();

            return new BDBTransaction();
        }

        @Override
        public void closeMessageStore()
        {
            if (_messageStoreOpen.compareAndSet(true, false))
            {
                try
                {
                    if (_committer != null)
                    {
                        _committer.close();
                    }
                }
                finally
                {
                    if (!_configurationStoreOpen.get())
                    {
                        closeEnvironment();
                    }
                }
            }
        }

        @Override
        public void addEventListener(final EventListener eventListener, final Event... events)
        {
            _eventManager.addEventListener(eventListener, events);
        }

        @Override
        public void onDelete()
        {
            BDBConfigurationStore.this.onDelete();
        }

        @Override
        public String getStoreLocation()
        {
            return BDBConfigurationStore.this.getStoreLocation();
        }

        @Override
        public void visitMessages(final MessageHandler handler) throws StoreException
        {
            checkMessageStoreOpen();
            visitMessagesInternal(handler, _environmentFacade);
        }

        @Override
        public void visitMessageInstances(final MessageInstanceHandler handler) throws StoreException
        {
            checkMessageStoreOpen();

            Cursor cursor = null;
            List<QueueEntryKey> entries = new ArrayList<QueueEntryKey>();
            try
            {
                cursor = getDeliveryDb().openCursor(null, null);
                DatabaseEntry key = new DatabaseEntry();
                QueueEntryBinding keyBinding = QueueEntryBinding.getInstance();

                DatabaseEntry value = new DatabaseEntry();
                while (cursor.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS)
                {
                    QueueEntryKey entry = keyBinding.entryToObject(key);
                    entries.add(entry);
                }
            }
            catch (DatabaseException e)
            {
                throw _environmentFacade.handleDatabaseException("Cannot visit message instances", e);
            }
            finally
            {
                closeCursorSafely(cursor);
            }

            for(QueueEntryKey entry : entries)
            {
                UUID queueId = entry.getQueueId();
                long messageId = entry.getMessageId();
                if (!handler.handle(queueId, messageId))
                {
                    break;
                }
            }

        }

        @Override
        public void visitDistributedTransactions(final DistributedTransactionHandler handler) throws StoreException
        {
            checkMessageStoreOpen();

            Cursor cursor = null;
            try
            {
                cursor = getXidDb().openCursor(null, null);
                DatabaseEntry key = new DatabaseEntry();
                XidBinding keyBinding = XidBinding.getInstance();
                PreparedTransactionBinding valueBinding = new PreparedTransactionBinding();
                DatabaseEntry value = new DatabaseEntry();

                while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
                {
                    Xid xid = keyBinding.entryToObject(key);
                    PreparedTransaction preparedTransaction = valueBinding.entryToObject(value);
                    if (!handler.handle(xid.getFormat(), xid.getGlobalId(), xid.getBranchId(),
                                        preparedTransaction.getEnqueues(), preparedTransaction.getDequeues()))
                    {
                        break;
                    }
                }

            }
            catch (DatabaseException e)
            {
                throw _environmentFacade.handleDatabaseException("Cannot recover distributed transactions", e);
            }
            finally
            {
                closeCursorSafely(cursor);
            }
        }

        /**
         * Retrieves message meta-data.
         *
         * @param messageId The message to get the meta-data for.
         *
         * @return The message meta data.
         *
         * @throws StoreException If the operation fails for any reason, or if the specified message does not exist.
         */
        StorableMessageMetaData getMessageMetaData(long messageId) throws StoreException
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("public MessageMetaData getMessageMetaData(Long messageId = "
                             + messageId + "): called");
            }

            DatabaseEntry key = new DatabaseEntry();
            LongBinding.longToEntry(messageId, key);
            DatabaseEntry value = new DatabaseEntry();
            MessageMetaDataBinding messageBinding = MessageMetaDataBinding.getInstance();

            try
            {
                OperationStatus status = getMessageMetaDataDb().get(null, key, value, LockMode.READ_UNCOMMITTED);
                if (status != OperationStatus.SUCCESS)
                {
                    throw new StoreException("Metadata not found for message with id " + messageId);
                }

                StorableMessageMetaData mdd = messageBinding.entryToObject(value);

                return mdd;
            }
            catch (DatabaseException e)
            {
                throw _environmentFacade.handleDatabaseException("Error reading message metadata for message with id " + messageId + ": " + e.getMessage(), e);
            }
        }

        void removeMessage(long messageId, boolean sync) throws StoreException
        {
            boolean complete = false;
            com.sleepycat.je.Transaction tx = null;

            Random rand = null;
            int attempts = 0;
            try
            {
                do
                {
                    tx = null;
                    try
                    {
                        tx = _environmentFacade.getEnvironment().beginTransaction(null, null);

                        //remove the message meta data from the store
                        DatabaseEntry key = new DatabaseEntry();
                        LongBinding.longToEntry(messageId, key);

                        if (LOGGER.isDebugEnabled())
                        {
                            LOGGER.debug("Removing message id " + messageId);
                        }


                        OperationStatus status = getMessageMetaDataDb().delete(tx, key);
                        if (status == OperationStatus.NOTFOUND)
                        {
                            LOGGER.info("Message not found (attempt to remove failed - probably application initiated rollback) " +
                                        messageId);
                        }

                        if (LOGGER.isDebugEnabled())
                        {
                            LOGGER.debug("Deleted metadata for message " + messageId);
                        }

                        //now remove the content data from the store if there is any.
                        DatabaseEntry contentKeyEntry = new DatabaseEntry();
                        LongBinding.longToEntry(messageId, contentKeyEntry);
                        getMessageContentDb().delete(tx, contentKeyEntry);

                        if (LOGGER.isDebugEnabled())
                        {
                            LOGGER.debug("Deleted content for message " + messageId);
                        }

                        _environmentFacade.commit(tx);
                        _committer.commit(tx, sync);

                        complete = true;
                        tx = null;
                    }
                    catch (LockConflictException e)
                    {
                        try
                        {
                            if(tx != null)
                            {
                                tx.abort();
                            }
                        }
                        catch(DatabaseException e2)
                        {
                            LOGGER.warn("Unable to abort transaction after LockConflictExcption on removal of message with id " + messageId, e2);
                            // rethrow the original log conflict exception, the secondary exception should already have
                            // been logged.
                            throw _environmentFacade.handleDatabaseException("Cannot remove message with id " + messageId, e);
                        }


                        LOGGER.warn("Lock timeout exception. Retrying (attempt "
                                    + (attempts+1) + " of "+ LOCK_RETRY_ATTEMPTS +") " + e);

                        if(++attempts < LOCK_RETRY_ATTEMPTS)
                        {
                            if(rand == null)
                            {
                                rand = new Random();
                            }

                            try
                            {
                                Thread.sleep(500l + (long)(500l * rand.nextDouble()));
                            }
                            catch (InterruptedException e1)
                            {

                            }
                        }
                        else
                        {
                            // rethrow the lock conflict exception since we could not solve by retrying
                            throw _environmentFacade.handleDatabaseException("Cannot remove messages", e);
                        }
                    }
                }
                while(!complete);
            }
            catch (DatabaseException e)
            {
                LOGGER.error("Unexpected BDB exception", e);

                try
                {
                    abortTransactionIgnoringException("Error aborting transaction on removal of message with id " + messageId, tx);
                }
                finally
                {
                    tx = null;
                }

                throw _environmentFacade.handleDatabaseException("Error removing message with id " + messageId + " from database: " + e.getMessage(), e);
            }
            finally
            {
                try
                {
                    abortTransactionIgnoringException("Error aborting transaction on removal of message with id " + messageId, tx);
                }
                finally
                {
                    tx = null;
                }
            }
        }


        /**
         * Fills the provided ByteBuffer with as much content for the specified message as possible, starting
         * from the specified offset in the message.
         *
         * @param messageId The message to get the data for.
         * @param offset    The offset of the data within the message.
         * @param dst       The destination of the content read back
         *
         * @return The number of bytes inserted into the destination
         *
         * @throws StoreException If the operation fails for any reason, or if the specified message does not exist.
         */
        int getContent(long messageId, int offset, ByteBuffer dst) throws StoreException
        {
            DatabaseEntry contentKeyEntry = new DatabaseEntry();
            LongBinding.longToEntry(messageId, contentKeyEntry);
            DatabaseEntry value = new DatabaseEntry();
            ContentBinding contentTupleBinding = ContentBinding.getInstance();


            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Message Id: " + messageId + " Getting content body from offset: " + offset);
            }

            try
            {

                int written = 0;
                OperationStatus status = getMessageContentDb().get(null, contentKeyEntry, value, LockMode.READ_UNCOMMITTED);
                if (status == OperationStatus.SUCCESS)
                {
                    byte[] dataAsBytes = contentTupleBinding.entryToObject(value);
                    int size = dataAsBytes.length;
                    if (offset > size)
                    {
                        throw new RuntimeException("Offset " + offset + " is greater than message size " + size
                                                   + " for message id " + messageId + "!");

                    }

                    written = size - offset;
                    if(written > dst.remaining())
                    {
                        written = dst.remaining();
                    }

                    dst.put(dataAsBytes, offset, written);
                }
                return written;
            }
            catch (DatabaseException e)
            {
                throw _environmentFacade.handleDatabaseException("Error getting AMQMessage with id " + messageId + " to database: " + e.getMessage(), e);
            }
        }

        private void visitMessagesInternal(MessageHandler handler, EnvironmentFacade environmentFacade)
        {
            Cursor cursor = null;
            try
            {
                cursor = getMessageMetaDataDb().openCursor(null, null);
                DatabaseEntry key = new DatabaseEntry();
                DatabaseEntry value = new DatabaseEntry();
                MessageMetaDataBinding valueBinding = MessageMetaDataBinding.getInstance();

                while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
                {
                    long messageId = LongBinding.entryToLong(key);
                    StorableMessageMetaData metaData = valueBinding.entryToObject(value);
                    StoredBDBMessage message = new StoredBDBMessage(messageId, metaData, true);

                    if (!handler.handle(message))
                    {
                        break;
                    }
                }
            }
            catch (DatabaseException e)
            {
                throw environmentFacade.handleDatabaseException("Cannot visit messages", e);
            }
            finally
            {
                if (cursor != null)
                {
                    try
                    {
                        cursor.close();
                    }
                    catch(DatabaseException e)
                    {
                        throw environmentFacade.handleDatabaseException("Cannot close cursor", e);
                    }
                }
            }
        }

        /**
         * Stores a chunk of message data.
         *
         * @param tx         The transaction for the operation.
         * @param messageId       The message to store the data for.
         * @param offset          The offset of the data chunk in the message.
         * @param contentBody     The content of the data chunk.
         *
         * @throws StoreException If the operation fails for any reason, or if the specified message does not exist.
         */
        private void addContent(final com.sleepycat.je.Transaction tx, long messageId, int offset,
                                ByteBuffer contentBody) throws StoreException
        {
            DatabaseEntry key = new DatabaseEntry();
            LongBinding.longToEntry(messageId, key);
            DatabaseEntry value = new DatabaseEntry();
            ContentBinding messageBinding = ContentBinding.getInstance();
            messageBinding.objectToEntry(contentBody.array(), value);
            try
            {
                OperationStatus status = getMessageContentDb().put(tx, key, value);
                if (status != OperationStatus.SUCCESS)
                {
                    throw new StoreException("Error adding content for message id " + messageId + ": " + status);
                }

                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Storing content for message " + messageId + " in transaction " + tx);

                }
            }
            catch (DatabaseException e)
            {
                throw _environmentFacade.handleDatabaseException("Error writing AMQMessage with id " + messageId + " to database: " + e.getMessage(), e);
            }
        }

        /**
         * Stores message meta-data.
         *
         * @param tx         The transaction for the operation.
         * @param messageId       The message to store the data for.
         * @param messageMetaData The message meta data to store.
         *
         * @throws StoreException If the operation fails for any reason, or if the specified message does not exist.
         */
        private void storeMetaData(final com.sleepycat.je.Transaction tx, long messageId,
                                   StorableMessageMetaData messageMetaData)
                throws StoreException
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("storeMetaData called for transaction " + tx
                             + ", messageId " + messageId
                             + ", messageMetaData " + messageMetaData);
            }

            DatabaseEntry key = new DatabaseEntry();
            LongBinding.longToEntry(messageId, key);
            DatabaseEntry value = new DatabaseEntry();

            MessageMetaDataBinding messageBinding = MessageMetaDataBinding.getInstance();
            messageBinding.objectToEntry(messageMetaData, value);
            try
            {
                getMessageMetaDataDb().put(tx, key, value);
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Storing message metadata for message id " + messageId + " in transaction " + tx);
                }
            }
            catch (DatabaseException e)
            {
                throw _environmentFacade.handleDatabaseException("Error writing message metadata with id " + messageId + " to database: " + e.getMessage(), e);
            }
        }


        /**
         * Places a message onto a specified queue, in a given transaction.
         *
         * @param tx   The transaction for the operation.
         * @param queue     The the queue to place the message on.
         * @param messageId The message to enqueue.
         *
         * @throws StoreException If the operation fails for any reason.
         */
        private void enqueueMessage(final com.sleepycat.je.Transaction tx, final TransactionLogResource queue,
                                    long messageId) throws StoreException
        {

            DatabaseEntry key = new DatabaseEntry();
            QueueEntryBinding keyBinding = QueueEntryBinding.getInstance();
            QueueEntryKey dd = new QueueEntryKey(queue.getId(), messageId);
            keyBinding.objectToEntry(dd, key);
            DatabaseEntry value = new DatabaseEntry();
            ByteBinding.byteToEntry((byte) 0, value);

            try
            {
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Enqueuing message " + messageId + " on queue "
                                 + queue.getName() + " with id " + queue.getId() + " in transaction " + tx);
                }
                getDeliveryDb().put(tx, key, value);
            }
            catch (DatabaseException e)
            {
                LOGGER.error("Failed to enqueue: " + e.getMessage(), e);
                throw _environmentFacade.handleDatabaseException("Error writing enqueued message with id " + messageId + " for queue "
                                                                 + queue.getName() + " with id " + queue.getId() + " to database", e);
            }
        }

        /**
         * Extracts a message from a specified queue, in a given transaction.
         *
         * @param tx   The transaction for the operation.
         * @param queue     The queue to take the message from.
         * @param messageId The message to dequeue.
         *
         * @throws StoreException If the operation fails for any reason, or if the specified message does not exist.
         */
        private void dequeueMessage(final com.sleepycat.je.Transaction tx, final TransactionLogResource queue,
                                    long messageId) throws StoreException
        {

            DatabaseEntry key = new DatabaseEntry();
            QueueEntryBinding keyBinding = QueueEntryBinding.getInstance();
            QueueEntryKey queueEntryKey = new QueueEntryKey(queue.getId(), messageId);
            UUID id = queue.getId();
            keyBinding.objectToEntry(queueEntryKey, key);
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Dequeue message id " + messageId + " from queue "
                             + queue.getName() + " with id " + id);
            }

            try
            {

                OperationStatus status = getDeliveryDb().delete(tx, key);
                if (status == OperationStatus.NOTFOUND)
                {
                    throw new StoreException("Unable to find message with id " + messageId + " on queue "
                                             + queue.getName() + " with id "  + id);
                }
                else if (status != OperationStatus.SUCCESS)
                {
                    throw new StoreException("Unable to remove message with id " + messageId + " on queue"
                                             + queue.getName() + " with id " + id);
                }

                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Removed message " + messageId + " on queue "
                                 + queue.getName() + " with id " + id);

                }
            }
            catch (DatabaseException e)
            {

                LOGGER.error("Failed to dequeue message " + messageId + " in transaction " + tx , e);

                throw _environmentFacade.handleDatabaseException("Error accessing database while dequeuing message: " + e.getMessage(), e);
            }
        }

        private void recordXid(com.sleepycat.je.Transaction txn,
                               long format,
                               byte[] globalId,
                               byte[] branchId,
                               org.apache.qpid.server.store.Transaction.Record[] enqueues,
                               org.apache.qpid.server.store.Transaction.Record[] dequeues) throws StoreException
        {
            DatabaseEntry key = new DatabaseEntry();
            Xid xid = new Xid(format, globalId, branchId);
            XidBinding keyBinding = XidBinding.getInstance();
            keyBinding.objectToEntry(xid,key);

            DatabaseEntry value = new DatabaseEntry();
            PreparedTransaction preparedTransaction = new PreparedTransaction(enqueues, dequeues);
            PreparedTransactionBinding valueBinding = new PreparedTransactionBinding();
            valueBinding.objectToEntry(preparedTransaction, value);

            try
            {
                getXidDb().put(txn, key, value);
            }
            catch (DatabaseException e)
            {
                LOGGER.error("Failed to write xid: " + e.getMessage(), e);
                throw _environmentFacade.handleDatabaseException("Error writing xid to database", e);
            }
        }

        private void removeXid(com.sleepycat.je.Transaction txn, long format, byte[] globalId, byte[] branchId)
                throws StoreException
        {
            DatabaseEntry key = new DatabaseEntry();
            Xid xid = new Xid(format, globalId, branchId);
            XidBinding keyBinding = XidBinding.getInstance();

            keyBinding.objectToEntry(xid, key);


            try
            {

                OperationStatus status = getXidDb().delete(txn, key);
                if (status == OperationStatus.NOTFOUND)
                {
                    throw new StoreException("Unable to find xid");
                }
                else if (status != OperationStatus.SUCCESS)
                {
                    throw new StoreException("Unable to remove xid");
                }

            }
            catch (DatabaseException e)
            {

                LOGGER.error("Failed to remove xid in transaction " + txn, e);

                throw _environmentFacade.handleDatabaseException("Error accessing database while removing xid: " + e.getMessage(), e);
            }
        }

        /**
         * Commits all operations performed within a given transaction.
         *
         * @param tx The transaction to commit all operations for.
         *
         * @throws StoreException If the operation fails for any reason.
         */
        private StoreFuture commitTranImpl(final com.sleepycat.je.Transaction tx, boolean syncCommit) throws StoreException
        {
            if (tx == null)
            {
                throw new StoreException("Fatal internal error: transactional is null at commitTran");
            }

            _environmentFacade.commit(tx);
            StoreFuture result =  _committer.commit(tx, syncCommit);

            if (LOGGER.isDebugEnabled())
            {
                String transactionType = syncCommit ? "synchronous" : "asynchronous";
                LOGGER.debug("commitTranImpl completed " + transactionType + " transaction " + tx);
            }

            return result;
        }

        /**
         * Abandons all operations performed within a given transaction.
         *
         * @param tx The transaction to abandon.
         *
         * @throws StoreException If the operation fails for any reason.
         */
        private void abortTran(final com.sleepycat.je.Transaction tx) throws StoreException
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("abortTran called for transaction " + tx);
            }

            try
            {
                tx.abort();
            }
            catch (DatabaseException e)
            {
                throw _environmentFacade.handleDatabaseException("Error aborting transaction: " + e.getMessage(), e);
            }
        }

        private void checkMessageStoreOpen()
        {
            if (!_messageStoreOpen.get())
            {
                throw new IllegalStateException("Message store is not open");
            }
        }

        private void storedSizeChangeOccurred(final int delta) throws StoreException
        {
            try
            {
                storedSizeChange(delta);
            }
            catch(DatabaseException e)
            {
                throw _environmentFacade.handleDatabaseException("Stored size change exception", e);
            }
        }

        private void storedSizeChange(final int delta)
        {
            if(getPersistentSizeHighThreshold() > 0)
            {
                synchronized (this)
                {
                    // the delta supplied is an approximation of a store size change. we don;t want to check the statistic every
                    // time, so we do so only when there's been enough change that it is worth looking again. We do this by
                    // assuming the total size will change by less than twice the amount of the message data change.
                    long newSize = _totalStoreSize += 2*delta;

                    if(!_limitBusted &&  newSize > getPersistentSizeHighThreshold())
                    {
                        _totalStoreSize = getSizeOnDisk();

                        if(_totalStoreSize > getPersistentSizeHighThreshold())
                        {
                            _limitBusted = true;
                            _eventManager.notifyEvent(Event.PERSISTENT_MESSAGE_SIZE_OVERFULL);
                        }
                    }
                    else if(_limitBusted && newSize < getPersistentSizeLowThreshold())
                    {
                        long oldSize = _totalStoreSize;
                        _totalStoreSize = getSizeOnDisk();

                        if(oldSize <= _totalStoreSize)
                        {

                            reduceSizeOnDisk();

                            _totalStoreSize = getSizeOnDisk();

                        }

                        if(_totalStoreSize < getPersistentSizeLowThreshold())
                        {
                            _limitBusted = false;
                            _eventManager.notifyEvent(Event.PERSISTENT_MESSAGE_SIZE_UNDERFULL);
                        }


                    }
                }
            }
        }

        private long getPersistentSizeLowThreshold()
        {
            return _persistentSizeLowThreshold;
        }

        private long getPersistentSizeHighThreshold()
        {
            return _persistentSizeHighThreshold;
        }

        private void reduceSizeOnDisk()
        {
            _environmentFacade.getEnvironment().getConfig().setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, "false");
            boolean cleaned = false;
            while (_environmentFacade.getEnvironment().cleanLog() > 0)
            {
                cleaned = true;
            }
            if (cleaned)
            {
                CheckpointConfig force = new CheckpointConfig();
                force.setForce(true);
                _environmentFacade.getEnvironment().checkpoint(force);
            }


            _environmentFacade.getEnvironment().getConfig().setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, "true");
        }

        private long getSizeOnDisk()
        {
            return _environmentFacade.getEnvironment().getStats(null).getTotalLogSize();
        }

        private class StoredBDBMessage<T extends StorableMessageMetaData> implements StoredMessage<T>
        {

            private final long _messageId;
            private final boolean _isRecovered;

            private T _metaData;
            private volatile SoftReference<T> _metaDataRef;

            private byte[] _data;
            private volatile SoftReference<byte[]> _dataRef;

            StoredBDBMessage(long messageId, T metaData)
            {
                this(messageId, metaData, false);
            }

            StoredBDBMessage(long messageId, T metaData, boolean isRecovered)
            {
                _messageId = messageId;
                _isRecovered = isRecovered;

                if(!_isRecovered)
                {
                    _metaData = metaData;
                }
                _metaDataRef = new SoftReference<T>(metaData);
            }

            @Override
            public T getMetaData()
            {
                T metaData = _metaDataRef.get();
                if(metaData == null)
                {
                    checkMessageStoreOpen();

                    metaData = (T) getMessageMetaData(_messageId);
                    _metaDataRef = new SoftReference<T>(metaData);
                }

                return metaData;
            }

            @Override
            public long getMessageNumber()
            {
                return _messageId;
            }

            @Override
            public void addContent(int offsetInMessage, java.nio.ByteBuffer src)
            {
                src = src.slice();

                if(_data == null)
                {
                    _data = new byte[src.remaining()];
                    _dataRef = new SoftReference<byte[]>(_data);
                    src.duplicate().get(_data);
                }
                else
                {
                    byte[] oldData = _data;
                    _data = new byte[oldData.length + src.remaining()];
                    _dataRef = new SoftReference<byte[]>(_data);

                    System.arraycopy(oldData, 0, _data, 0, oldData.length);
                    src.duplicate().get(_data, oldData.length, src.remaining());
                }

            }

            @Override
            public int getContent(int offsetInMessage, java.nio.ByteBuffer dst)
            {
                byte[] data = _dataRef == null ? null : _dataRef.get();
                if(data != null)
                {
                    int length = Math.min(dst.remaining(), data.length - offsetInMessage);
                    dst.put(data, offsetInMessage, length);
                    return length;
                }
                else
                {
                    checkMessageStoreOpen();

                    return BDBMessageStore.this.getContent(_messageId, offsetInMessage, dst);
                }
            }

            @Override
            public ByteBuffer getContent(int offsetInMessage, int size)
            {
                byte[] data = _dataRef == null ? null : _dataRef.get();
                if(data != null)
                {
                    return ByteBuffer.wrap(data,offsetInMessage,size);
                }
                else
                {
                    ByteBuffer buf = ByteBuffer.allocate(size);
                    int length = getContent(offsetInMessage, buf);
                    buf.limit(length);
                    buf.position(0);
                    return  buf;
                }
            }

            synchronized void store(com.sleepycat.je.Transaction txn)
            {
                if (!stored())
                {
                    try
                    {
                        _dataRef = new SoftReference<byte[]>(_data);
                        BDBMessageStore.this.storeMetaData(txn, _messageId, _metaData);
                        BDBMessageStore.this.addContent(txn, _messageId, 0,
                                                        _data == null ? ByteBuffer.allocate(0) : ByteBuffer.wrap(_data));
                    }
                    finally
                    {
                        _metaData = null;
                        _data = null;
                    }
                }
            }

            @Override
            public synchronized StoreFuture flushToStore()
            {
                if(!stored())
                {
                    checkMessageStoreOpen();

                    com.sleepycat.je.Transaction txn;
                    try
                    {
                        txn = _environmentFacade.getEnvironment().beginTransaction(
                                null, null);
                    }
                    catch (DatabaseException e)
                    {
                        throw _environmentFacade.handleDatabaseException("failed to begin transaction", e);
                    }
                    store(txn);
                    _environmentFacade.commit(txn);
                    _committer.commit(txn, true);

                    storedSizeChangeOccurred(getMetaData().getContentSize());
                }
                return StoreFuture.IMMEDIATE_FUTURE;
            }

            @Override
            public void remove()
            {
                checkMessageStoreOpen();

                int delta = getMetaData().getContentSize();
                removeMessage(_messageId, false);
                storedSizeChangeOccurred(-delta);
            }

            private boolean stored()
            {
                return _metaData == null || _isRecovered;
            }

            @Override
            public String toString()
            {
                return this.getClass() + "[messageId=" + _messageId + "]";
            }
        }


        private class BDBTransaction implements org.apache.qpid.server.store.Transaction
        {
            private com.sleepycat.je.Transaction _txn;
            private int _storeSizeIncrease;

            private BDBTransaction() throws StoreException
            {
                try
                {
                    _txn = _environmentFacade.getEnvironment().beginTransaction(null, null);
                }
                catch(DatabaseException e)
                {
                    throw _environmentFacade.handleDatabaseException("Cannot create store transaction", e);
                }
            }

            @Override
            public void enqueueMessage(TransactionLogResource queue, EnqueueableMessage message) throws StoreException
            {
                checkMessageStoreOpen();

                if(message.getStoredMessage() instanceof StoredBDBMessage)
                {
                    final StoredBDBMessage storedMessage = (StoredBDBMessage) message.getStoredMessage();
                    storedMessage.store(_txn);
                    _storeSizeIncrease += storedMessage.getMetaData().getContentSize();
                }

                BDBMessageStore.this.enqueueMessage(_txn, queue, message.getMessageNumber());
            }

            @Override
            public void dequeueMessage(TransactionLogResource queue, EnqueueableMessage message) throws StoreException
            {
                checkMessageStoreOpen();

                BDBMessageStore.this.dequeueMessage(_txn, queue, message.getMessageNumber());
            }

            @Override
            public void commitTran() throws StoreException
            {
                checkMessageStoreOpen();

                BDBMessageStore.this.commitTranImpl(_txn, true);
                BDBMessageStore.this.storedSizeChangeOccurred(_storeSizeIncrease);
            }

            @Override
            public StoreFuture commitTranAsync() throws StoreException
            {
                checkMessageStoreOpen();

                BDBMessageStore.this.storedSizeChangeOccurred(_storeSizeIncrease);
                return BDBMessageStore.this.commitTranImpl(_txn, false);
            }

            @Override
            public void abortTran() throws StoreException
            {
                checkMessageStoreOpen();

                BDBMessageStore.this.abortTran(_txn);
            }

            @Override
            public void removeXid(long format, byte[] globalId, byte[] branchId) throws StoreException
            {
                checkMessageStoreOpen();

                BDBMessageStore.this.removeXid(_txn, format, globalId, branchId);
            }

            @Override
            public void recordXid(long format, byte[] globalId, byte[] branchId, Record[] enqueues,
                                  Record[] dequeues) throws StoreException
            {
                checkMessageStoreOpen();

                BDBMessageStore.this.recordXid(_txn, format, globalId, branchId, enqueues, dequeues);
            }
        }

    }

}
