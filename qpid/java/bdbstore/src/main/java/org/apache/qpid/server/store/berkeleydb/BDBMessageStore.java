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

import com.sleepycat.bind.tuple.ByteBinding;
import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.je.*;
import com.sleepycat.je.Transaction;

import java.io.File;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.*;
import org.apache.qpid.server.store.MessageStoreRecoveryHandler.StoredMessageRecoveryHandler;
import org.apache.qpid.server.store.TransactionLogRecoveryHandler.QueueEntryRecoveryHandler;
import org.apache.qpid.server.store.berkeleydb.entry.PreparedTransaction;
import org.apache.qpid.server.store.berkeleydb.entry.QueueEntryKey;
import org.apache.qpid.server.store.berkeleydb.entry.Xid;
import org.apache.qpid.server.store.berkeleydb.tuple.ConfiguredObjectBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.ContentBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.MessageMetaDataBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.PreparedTransactionBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.QueueEntryBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.UUIDTupleBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.XidBinding;
import org.apache.qpid.server.store.berkeleydb.upgrade.Upgrader;
import org.apache.qpid.util.FileUtils;

/**
 * BDBMessageStore implements a persistent {@link MessageStore} using the BDB high performance log.
 *
 * <p/><table id="crc"><caption>CRC Card</caption> <tr><th> Responsibilities <th> Collaborations <tr><td> Accept
 * transaction boundary demarcations: Begin, Commit, Abort. <tr><td> Store and remove queues. <tr><td> Store and remove
 * exchanges. <tr><td> Store and remove messages. <tr><td> Bind and unbind queues to exchanges. <tr><td> Enqueue and
 * dequeue messages to queues. <tr><td> Generate message identifiers. </table>
 */
public class BDBMessageStore implements MessageStore, DurableConfigurationStore
{
    private static final Logger LOGGER = Logger.getLogger(BDBMessageStore.class);

    public static final int VERSION = 7;
    public static final String ENVIRONMENT_CONFIGURATION = "bdbEnvironmentConfig";

    private static final int LOCK_RETRY_ATTEMPTS = 5;
    private static String CONFIGURED_OBJECTS_DB_NAME = "CONFIGURED_OBJECTS";
    private static String MESSAGE_META_DATA_DB_NAME = "MESSAGE_METADATA";
    private static String MESSAGE_CONTENT_DB_NAME = "MESSAGE_CONTENT";
    private static String DELIVERY_DB_NAME = "QUEUE_ENTRIES";
    private static String BRIDGEDB_NAME = "BRIDGES";
    private static String LINKDB_NAME = "LINKS";
    private static String XID_DB_NAME = "XIDS";
    private static String CONFIG_VERSION_DB_NAME = "CONFIG_VERSION";
    private static final String[] DATABASE_NAMES = new String[] { CONFIGURED_OBJECTS_DB_NAME, MESSAGE_META_DATA_DB_NAME,
            MESSAGE_CONTENT_DB_NAME, DELIVERY_DB_NAME, BRIDGEDB_NAME, LINKDB_NAME, XID_DB_NAME, CONFIG_VERSION_DB_NAME };

    private final AtomicBoolean _closed = new AtomicBoolean(false);

    private EnvironmentFacade _environmentFacade;
    private final AtomicLong _messageId = new AtomicLong(0);

    protected final StateManager _stateManager;

    private MessageStoreRecoveryHandler _messageRecoveryHandler;

    private TransactionLogRecoveryHandler _tlogRecoveryHandler;

    private ConfigurationRecoveryHandler _configRecoveryHandler;

    private long _totalStoreSize;
    private boolean _limitBusted;
    private long _persistentSizeLowThreshold;
    private long _persistentSizeHighThreshold;

    private final EventManager _eventManager = new EventManager();
    private final String _type;
    private VirtualHost _virtualHost;

    private final EnvironmentFacadeFactory _environmentFacadeFactory;

    private volatile Committer _committer;

    public BDBMessageStore()
    {
        this(new StandardEnvironmentFacadeFactory());
    }

    public BDBMessageStore(EnvironmentFacadeFactory environmentFacadeFactory)
    {
        _type = environmentFacadeFactory.getType();;
        _environmentFacadeFactory = environmentFacadeFactory;
        _stateManager = new StateManager(_eventManager);
    }

    @Override
    public void addEventListener(EventListener eventListener, Event... events)
    {
        _eventManager.addEventListener(eventListener, events);
    }

    @Override
    public void configureConfigStore(VirtualHost virtualHost, ConfigurationRecoveryHandler recoveryHandler)
    {
        _stateManager.attainState(State.INITIALISING);

        _configRecoveryHandler = recoveryHandler;
        _virtualHost = virtualHost;
    }

    @Override
    public void configureMessageStore(VirtualHost virtualHost, MessageStoreRecoveryHandler messageRecoveryHandler,
                                      TransactionLogRecoveryHandler tlogRecoveryHandler) throws AMQStoreException
    {
        if(_stateManager.isInState(State.INITIAL))
        {
            // Is acting as a message store, but not a durable config store
            _stateManager.attainState(State.INITIALISING);
        }

        _messageRecoveryHandler = messageRecoveryHandler;
        _tlogRecoveryHandler = tlogRecoveryHandler;
        _virtualHost = virtualHost;


        completeInitialisation();
    }

    private void completeInitialisation() throws AMQStoreException
    {
        configure(_virtualHost, _messageRecoveryHandler != null);

        _stateManager.attainState(State.INITIALISED);
    }

    private void startActivation() throws AMQStoreException
    {
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        try
        {
            new Upgrader(_environmentFacade.getEnvironment(), _virtualHost.getName()).upgradeIfNecessary();
            _environmentFacade.openDatabases(dbConfig, DATABASE_NAMES);
            _totalStoreSize = getSizeOnDisk();
        }
        catch(DatabaseException e)
        {
            throw _environmentFacade.handleDatabaseException("Cannot configure store", e);
        }

    }

    @Override
    public synchronized void activate() throws AMQStoreException
    {
        // check if acting as a durable config store, but not a message store
        if(_stateManager.isInState(State.INITIALISING))
        {
            completeInitialisation();
        }

        _stateManager.attainState(State.ACTIVATING);
        startActivation();

        if(_configRecoveryHandler != null)
        {
            recoverConfig(_configRecoveryHandler);
        }
        if(_messageRecoveryHandler != null)
        {
            recoverMessages(_messageRecoveryHandler);
        }
        if(_tlogRecoveryHandler != null)
        {
            recoverQueueEntries(_tlogRecoveryHandler);
        }

        _stateManager.attainState(State.ACTIVE);
    }

    @Override
    public org.apache.qpid.server.store.Transaction newTransaction() throws AMQStoreException
    {
        return new BDBTransaction();
    }

    private void configure(VirtualHost virtualHost, boolean isMessageStore) throws AMQStoreException
    {
        Object overfullAttr = virtualHost.getAttribute(MessageStoreConstants.OVERFULL_SIZE_ATTRIBUTE);
        Object underfullAttr = virtualHost.getAttribute(MessageStoreConstants.UNDERFULL_SIZE_ATTRIBUTE);

        _persistentSizeHighThreshold = overfullAttr == null ? -1l :
                                       overfullAttr instanceof Number ? ((Number) overfullAttr).longValue() : Long.parseLong(overfullAttr.toString());
        _persistentSizeLowThreshold = underfullAttr == null ? _persistentSizeHighThreshold :
                                       underfullAttr instanceof Number ? ((Number) underfullAttr).longValue() : Long.parseLong(underfullAttr.toString());


        if(_persistentSizeLowThreshold > _persistentSizeHighThreshold || _persistentSizeLowThreshold < 0l)
        {
            _persistentSizeLowThreshold = _persistentSizeHighThreshold;
        }

        _environmentFacade = _environmentFacadeFactory.createEnvironmentFacade(virtualHost, isMessageStore);

        _committer = _environmentFacade.createCommitter(virtualHost.getName());
        _committer.start();
    }

    @Override
    public String getStoreLocation()
    {
        if (_environmentFacade == null)
        {
            return null;
        }
        return _environmentFacade.getStoreLocation();
    }

    public EnvironmentFacade getEnvironmentFacade()
    {
        return _environmentFacade;
    }

    /**
     * Called to close and cleanup any resources used by the message store.
     *
     * @throws Exception If the close fails.
     */
    @Override
    public void close() throws AMQStoreException
    {
        if (_closed.compareAndSet(false, true))
        {
            _stateManager.attainState(State.CLOSING);
            try
            {
                try
                {
                    _committer.stop();
                }
                finally
                {
                    closeEnvironment();
                }
            }
            catch(DatabaseException e)
            {
                throw new AMQStoreException("Exception occured on message store close", e);
            }
            _stateManager.attainState(State.CLOSED);
        }
    }

    private void closeEnvironment()
    {
        if (_environmentFacade != null)
        {
            _environmentFacade.close();
        }
    }

    private void recoverConfig(ConfigurationRecoveryHandler recoveryHandler) throws AMQStoreException
    {
        try
        {
            final int configVersion = getConfigVersion();
            recoveryHandler.beginConfigurationRecovery(this, configVersion);
            loadConfiguredObjects(recoveryHandler);

            final int newConfigVersion = recoveryHandler.completeConfigurationRecovery();
            if(newConfigVersion != configVersion)
            {
                updateConfigVersion(newConfigVersion);
            }
        }
        catch (DatabaseException e)
        {
            throw _environmentFacade.handleDatabaseException("Error recovering persistent state: " + e.getMessage(), e);
        }

    }

    @SuppressWarnings("resource")
    private void updateConfigVersion(int newConfigVersion) throws AMQStoreException
    {
        Cursor cursor = null;
        try
        {
            Transaction txn = _environmentFacade.getEnvironment().beginTransaction(null, null);
            cursor = getConfigVersionDb().openCursor(txn, null);
            DatabaseEntry key = new DatabaseEntry();
            ByteBinding.byteToEntry((byte) 0,key);
            DatabaseEntry value = new DatabaseEntry();

            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                IntegerBinding.intToEntry(newConfigVersion, value);
                OperationStatus status = cursor.put(key, value);
                if (status != OperationStatus.SUCCESS)
                {
                    throw new AMQStoreException("Error setting config version: " + status);
                }
            }
            cursor.close();
            cursor = null;
            txn.commit();
        }
        finally
        {
            closeCursorSafely(cursor);
        }

    }

    private int getConfigVersion() throws AMQStoreException
    {
        Cursor cursor = null;
        try
        {
            cursor = getConfigVersionDb().openCursor(null, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                return IntegerBinding.entryToInt(value);
            }

            // Insert 0 as the default config version
            IntegerBinding.intToEntry(0,value);
            ByteBinding.byteToEntry((byte) 0,key);
            OperationStatus status = getConfigVersionDb().put(null, key, value);
            if (status != OperationStatus.SUCCESS)
            {
                throw new AMQStoreException("Error initialising config version: " + status);
            }
            return 0;
        }
        finally
        {
            closeCursorSafely(cursor);
        }
    }

    private void loadConfiguredObjects(ConfigurationRecoveryHandler crh) throws DatabaseException, AMQStoreException
    {
        Cursor cursor = null;
        try
        {
            cursor = getConfiguredObjectsDb().openCursor(null, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                UUID id = UUIDTupleBinding.getInstance().entryToObject(key);

                ConfiguredObjectRecord configuredObject = new ConfiguredObjectBinding(id).entryToObject(value);
                LOGGER.debug("Recovering configuredObject : " + configuredObject);// TODO: remove this
                crh.configuredObject(configuredObject.getId(),configuredObject.getType(),configuredObject.getAttributes());
            }

        }
        finally
        {
            closeCursorSafely(cursor);
        }
    }

    private void closeCursorSafely(Cursor cursor) throws AMQStoreException
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


    private void recoverMessages(MessageStoreRecoveryHandler msrh) throws AMQStoreException
    {
        StoredMessageRecoveryHandler mrh = msrh.begin();

        Cursor cursor = null;
        try
        {
            cursor = getMessageMetaDataDb().openCursor(null, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            MessageMetaDataBinding valueBinding = MessageMetaDataBinding.getInstance();

            long maxId = 0;

            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                long messageId = LongBinding.entryToLong(key);
                StorableMessageMetaData metaData = valueBinding.entryToObject(value);

                StoredBDBMessage message = new StoredBDBMessage(messageId, metaData, true);

                mrh.message(message);

                maxId = Math.max(maxId, messageId);
            }

            _messageId.set(maxId);
            mrh.completeMessageRecovery();
        }
        catch (DatabaseException e)
        {
            throw _environmentFacade.handleDatabaseException("Cannot recover messages", e);
        }
        finally
        {
            closeCursorSafely(cursor);
        }
    }

    private void recoverQueueEntries(TransactionLogRecoveryHandler recoveryHandler)
    throws AMQStoreException
    {
        QueueEntryRecoveryHandler qerh = recoveryHandler.begin(this);

        ArrayList<QueueEntryKey> entries = new ArrayList<QueueEntryKey>();

        Cursor cursor = null;
        try
        {
            cursor = getDeliveryDb().openCursor(null, null);
            DatabaseEntry key = new DatabaseEntry();
            QueueEntryBinding keyBinding = QueueEntryBinding.getInstance();

            DatabaseEntry value = new DatabaseEntry();
            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                QueueEntryKey qek = keyBinding.entryToObject(key);

                entries.add(qek);
            }

            try
            {
                cursor.close();
            }
            finally
            {
                cursor = null;
            }

            for(QueueEntryKey entry : entries)
            {
                UUID queueId = entry.getQueueId();
                long messageId = entry.getMessageId();
                qerh.queueEntry(queueId, messageId);
            }
        }
        catch (DatabaseException e)
        {
            throw _environmentFacade.handleDatabaseException("Cannot recover queue entries", e);
        }
        finally
        {
            closeCursorSafely(cursor);
        }

        TransactionLogRecoveryHandler.DtxRecordRecoveryHandler dtxrh = qerh.completeQueueEntryRecovery();

        cursor = null;
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
                dtxrh.dtxRecord(xid.getFormat(),xid.getGlobalId(),xid.getBranchId(),
                                preparedTransaction.getEnqueues(),preparedTransaction.getDequeues());
            }

        }
        catch (DatabaseException e)
        {
            throw _environmentFacade.handleDatabaseException("Cannot recover transactions", e);
        }
        finally
        {
            closeCursorSafely(cursor);
        }


        dtxrh.completeDtxRecordRecovery();
    }

    public void removeMessage(long messageId, boolean sync) throws AMQStoreException
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
    public void create(UUID id, String type, Map<String, Object> attributes) throws AMQStoreException
    {
        if (_stateManager.isInState(State.ACTIVE))
        {
            ConfiguredObjectRecord configuredObject = new ConfiguredObjectRecord(id, type, attributes);
            storeConfiguredObjectEntry(configuredObject);
        }
    }

    @Override
    public void remove(UUID id, String type) throws AMQStoreException
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("public void remove(id = " + id + ", type="+type+"): called");
        }
        OperationStatus status = removeConfiguredObject(null, id);
        if (status == OperationStatus.NOTFOUND)
        {
            throw new AMQStoreException("Configured object of type " + type + " with id " + id + " not found");
        }
    }

    @Override
    public UUID[] removeConfiguredObjects(final UUID... objects) throws AMQStoreException
    {
        com.sleepycat.je.Transaction txn = _environmentFacade.getEnvironment().beginTransaction(null, null);
        Collection<UUID> removed = new ArrayList<UUID>(objects.length);
        for(UUID id : objects)
        {
            if(removeConfiguredObject(txn, id) == OperationStatus.SUCCESS)
            {
                removed.add(id);
            }
        }
        commitTransaction(txn);
        return removed.toArray(new UUID[removed.size()]);
    }

    private void commitTransaction(com.sleepycat.je.Transaction txn) throws AMQStoreException
    {
        try
        {
            txn.commit();
        }
        catch(DatabaseException e)
        {
            throw _environmentFacade.handleDatabaseException("Cannot commit transaction on configured objects removal", e);
        }
    }

    @Override
    public void update(UUID id, String type, Map<String, Object> attributes) throws AMQStoreException
    {
        update(false, id, type, attributes, null);
    }

    @Override
    public void update(ConfiguredObjectRecord... records) throws AMQStoreException
    {
        update(false, records);
    }

    @Override
    public void update(boolean createIfNecessary, ConfiguredObjectRecord... records) throws AMQStoreException
    {
        com.sleepycat.je.Transaction txn = _environmentFacade.getEnvironment().beginTransaction(null, null);
        for(ConfiguredObjectRecord record : records)
        {
            update(createIfNecessary, record.getId(), record.getType(), record.getAttributes(), txn);
        }
        commitTransaction(txn);
    }

    private void update(boolean createIfNecessary, UUID id, String type, Map<String, Object> attributes, com.sleepycat.je.Transaction txn) throws AMQStoreException
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Updating " +type + ", id: " + id);
        }

        try
        {
            DatabaseEntry key = new DatabaseEntry();
            UUIDTupleBinding keyBinding = UUIDTupleBinding.getInstance();
            keyBinding.objectToEntry(id, key);

            DatabaseEntry value = new DatabaseEntry();
            DatabaseEntry newValue = new DatabaseEntry();
            ConfiguredObjectBinding configuredObjectBinding = ConfiguredObjectBinding.getInstance();

            OperationStatus status = getConfiguredObjectsDb().get(txn, key, value, LockMode.DEFAULT);
            if (status == OperationStatus.SUCCESS || (createIfNecessary && status == OperationStatus.NOTFOUND))
            {
                ConfiguredObjectRecord newQueueRecord = new ConfiguredObjectRecord(id, type, attributes);

                // write the updated entry to the store
                configuredObjectBinding.objectToEntry(newQueueRecord, newValue);
                status = getConfiguredObjectsDb().put(txn, key, newValue);
                if (status != OperationStatus.SUCCESS)
                {
                    throw new AMQStoreException("Error updating queue details within the store: " + status);
                }
            }
            else if (status != OperationStatus.NOTFOUND)
            {
                throw new AMQStoreException("Error finding queue details within the store: " + status);
            }
        }
        catch (DatabaseException e)
        {
            throw _environmentFacade.handleDatabaseException("Error updating queue details within the store: " + e,e);
        }
    }

    /**
     * Places a message onto a specified queue, in a given transaction.
     *
     * @param tx   The transaction for the operation.
     * @param queue     The the queue to place the message on.
     * @param messageId The message to enqueue.
     *
     * @throws AMQStoreException If the operation fails for any reason.
     */
    public void enqueueMessage(final com.sleepycat.je.Transaction tx, final TransactionLogResource queue,
                               long messageId) throws AMQStoreException
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
                        + (queue instanceof AMQQueue ? ((AMQQueue) queue).getName() + " with id " : "") + queue.getId()
                        + " in transaction " + tx);
            }
            getDeliveryDb().put(tx, key, value);
        }
        catch (DatabaseException e)
        {
            LOGGER.error("Failed to enqueue: " + e.getMessage(), e);
            throw _environmentFacade.handleDatabaseException("Error writing enqueued message with id " + messageId + " for queue "
                    + (queue instanceof AMQQueue ? ((AMQQueue) queue).getName() + " with id " : "") + queue.getId()
                    + " to database", e);
        }
    }

    /**
     * Extracts a message from a specified queue, in a given transaction.
     *
     * @param tx   The transaction for the operation.
     * @param queue     The queue to take the message from.
     * @param messageId The message to dequeue.
     *
     * @throws AMQStoreException If the operation fails for any reason, or if the specified message does not exist.
     */
    public void dequeueMessage(final com.sleepycat.je.Transaction tx, final TransactionLogResource queue,
                               long messageId) throws AMQStoreException
    {

        DatabaseEntry key = new DatabaseEntry();
        QueueEntryBinding keyBinding = QueueEntryBinding.getInstance();
        QueueEntryKey queueEntryKey = new QueueEntryKey(queue.getId(), messageId);
        UUID id = queue.getId();
        keyBinding.objectToEntry(queueEntryKey, key);
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Dequeue message id " + messageId + " from queue "
                    + (queue instanceof AMQQueue ? ((AMQQueue) queue).getName() + " with id " : "") + id);
        }

        try
        {

            OperationStatus status = getDeliveryDb().delete(tx, key);
            if (status == OperationStatus.NOTFOUND)
            {
                throw new AMQStoreException("Unable to find message with id " + messageId + " on queue "
                        + (queue instanceof AMQQueue ? ((AMQQueue) queue).getName() + " with id " : "") + id);
            }
            else if (status != OperationStatus.SUCCESS)
            {
                throw new AMQStoreException("Unable to remove message with id " + messageId + " on queue"
                        + (queue instanceof AMQQueue ? ((AMQQueue) queue).getName() + " with id " : "") + id);
            }

            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Removed message " + messageId + " on queue "
                        + (queue instanceof AMQQueue ? ((AMQQueue) queue).getName() + " with id " : "") + id
                        + " from delivery db");

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
                           org.apache.qpid.server.store.Transaction.Record[] dequeues) throws AMQStoreException
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
            throws AMQStoreException
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
                throw new AMQStoreException("Unable to find xid");
            }
            else if (status != OperationStatus.SUCCESS)
            {
                throw new AMQStoreException("Unable to remove xid");
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
     * @throws AMQStoreException If the operation fails for any reason.
     */
    private StoreFuture commitTranImpl(final com.sleepycat.je.Transaction tx, boolean syncCommit) throws AMQStoreException
    {
        if (tx == null)
        {
            throw new AMQStoreException("Fatal internal error: transactional is null at commitTran");
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
     * @throws AMQStoreException If the operation fails for any reason.
     */
    public void abortTran(final com.sleepycat.je.Transaction tx) throws AMQStoreException
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

    /**
     * Primarily for testing purposes.
     *
     * @param queueId
     *
     * @return a list of message ids for messages enqueued for a particular queue
     */
    List<Long> getEnqueuedMessages(UUID queueId) throws AMQStoreException
    {
        Cursor cursor = null;
        try
        {
            cursor = getDeliveryDb().openCursor(null, null);

            DatabaseEntry key = new DatabaseEntry();

            QueueEntryKey dd = new QueueEntryKey(queueId, 0);

            QueueEntryBinding keyBinding = QueueEntryBinding.getInstance();
            keyBinding.objectToEntry(dd, key);

            DatabaseEntry value = new DatabaseEntry();

            LinkedList<Long> messageIds = new LinkedList<Long>();

            OperationStatus status = cursor.getSearchKeyRange(key, value, LockMode.DEFAULT);
            dd = keyBinding.entryToObject(key);

            while ((status == OperationStatus.SUCCESS) && dd.getQueueId().equals(queueId))
            {

                messageIds.add(dd.getMessageId());
                status = cursor.getNext(key, value, LockMode.DEFAULT);
                if (status == OperationStatus.SUCCESS)
                {
                    dd = keyBinding.entryToObject(key);
                }
            }

            return messageIds;
        }
        catch (DatabaseException e)
        {
            throw new AMQStoreException("Database error: " + e.getMessage(), e);
        }
        finally
        {
            closeCursorSafely(cursor);
        }
    }

    /**
     * Return a valid, currently unused message id.
     *
     * @return A fresh message id.
     */
    public long getNewMessageId()
    {
        return _messageId.incrementAndGet();
    }

    /**
     * Stores a chunk of message data.
     *
     * @param tx         The transaction for the operation.
     * @param messageId       The message to store the data for.
     * @param offset          The offset of the data chunk in the message.
     * @param contentBody     The content of the data chunk.
     *
     * @throws AMQStoreException If the operation fails for any reason, or if the specified message does not exist.
     */
    protected void addContent(final com.sleepycat.je.Transaction tx, long messageId, int offset,
                                      ByteBuffer contentBody) throws AMQStoreException
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
                throw new AMQStoreException("Error adding content for message id " + messageId + ": " + status);
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
     * @throws AMQStoreException If the operation fails for any reason, or if the specified message does not exist.
     */
    private void storeMetaData(final com.sleepycat.je.Transaction tx, long messageId,
                               StorableMessageMetaData messageMetaData)
            throws AMQStoreException
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
     * Retrieves message meta-data.
     *
     * @param messageId The message to get the meta-data for.
     *
     * @return The message meta data.
     *
     * @throws AMQStoreException If the operation fails for any reason, or if the specified message does not exist.
     */
    public StorableMessageMetaData getMessageMetaData(long messageId) throws AMQStoreException
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
                throw new AMQStoreException("Metadata not found for message with id " + messageId);
            }

            StorableMessageMetaData mdd = messageBinding.entryToObject(value);

            return mdd;
        }
        catch (DatabaseException e)
        {
            throw _environmentFacade.handleDatabaseException("Error reading message metadata for message with id " + messageId + ": " + e.getMessage(), e);
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
     * @throws AMQStoreException If the operation fails for any reason, or if the specified message does not exist.
     */
    public int getContent(long messageId, int offset, ByteBuffer dst) throws AMQStoreException
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

    @Override
    public boolean isPersistent()
    {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends StorableMessageMetaData> StoredMessage<T> addMessage(T metaData)
    {
        if(metaData.isPersistent())
        {
            return (StoredMessage<T>) new StoredBDBMessage(getNewMessageId(), metaData);
        }
        else
        {
            return new StoredMemoryMessage(getNewMessageId(), metaData);
        }
    }

    /**
     * Makes the specified configured object persistent.
     *
     * @param configuredObject     Details of the configured object to store.
     * @throws AMQStoreException If the operation fails for any reason.
     */
    private void storeConfiguredObjectEntry(ConfiguredObjectRecord configuredObject) throws AMQStoreException
    {
        if (_stateManager.isInState(State.ACTIVE))
        {
            LOGGER.debug("Storing configured object: " + configuredObject);
            DatabaseEntry key = new DatabaseEntry();
            UUIDTupleBinding keyBinding = UUIDTupleBinding.getInstance();
            keyBinding.objectToEntry(configuredObject.getId(), key);

            DatabaseEntry value = new DatabaseEntry();
            ConfiguredObjectBinding queueBinding = ConfiguredObjectBinding.getInstance();

            queueBinding.objectToEntry(configuredObject, value);
            try
            {
                OperationStatus status = getConfiguredObjectsDb().put(null, key, value);
                if (status != OperationStatus.SUCCESS)
                {
                    throw new AMQStoreException("Error writing configured object " + configuredObject + " to database: "
                            + status);
                }
            }
            catch (DatabaseException e)
            {
                throw _environmentFacade.handleDatabaseException("Error writing configured object " + configuredObject
                        + " to database: " + e.getMessage(), e);
            }
        }
    }

    private OperationStatus removeConfiguredObject(Transaction tx, UUID id) throws AMQStoreException
    {

        LOGGER.debug("Removing configured object: " + id);
        DatabaseEntry key = new DatabaseEntry();
        UUIDTupleBinding uuidBinding = UUIDTupleBinding.getInstance();
        uuidBinding.objectToEntry(id, key);
        try
        {
            return getConfiguredObjectsDb().delete(tx, key);
        }
        catch (DatabaseException e)
        {
            throw _environmentFacade.handleDatabaseException("Error deleting of configured object with id " + id + " from database", e);
        }
    }



    private class StoredBDBMessage implements StoredMessage<StorableMessageMetaData>
    {

        private final long _messageId;
        private final boolean _isRecovered;

        private StorableMessageMetaData _metaData;
        private volatile SoftReference<StorableMessageMetaData> _metaDataRef;

        private byte[] _data;
        private volatile SoftReference<byte[]> _dataRef;

        StoredBDBMessage(long messageId, StorableMessageMetaData metaData)
        {
            this(messageId, metaData, false);
        }

        StoredBDBMessage(long messageId, StorableMessageMetaData metaData, boolean isRecovered)
        {
            _messageId = messageId;
            _isRecovered = isRecovered;

            if(!_isRecovered)
            {
                _metaData = metaData;
            }
            _metaDataRef = new SoftReference<StorableMessageMetaData>(metaData);
        }

        public StorableMessageMetaData getMetaData()
        {
            StorableMessageMetaData metaData = _metaDataRef.get();
            if(metaData == null)
            {
                try
                {
                    metaData = BDBMessageStore.this.getMessageMetaData(_messageId);
                }
                catch (AMQStoreException e)
                {
                    throw new RuntimeException(e);
                }
                _metaDataRef = new SoftReference<StorableMessageMetaData>(metaData);
            }

            return metaData;
        }

        public long getMessageNumber()
        {
            return _messageId;
        }

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

                System.arraycopy(oldData,0,_data,0,oldData.length);
                src.duplicate().get(_data, oldData.length, src.remaining());
            }

        }

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
                try
                {
                    return BDBMessageStore.this.getContent(_messageId, offsetInMessage, dst);
                }
                catch (AMQStoreException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }

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
                catch (AMQStoreException e)
                {
                    throw new RuntimeException(e);
                }
                catch (RuntimeException e)
                {
                    LOGGER.error("RuntimeException during store", e);
                    throw e;
                }
                finally
                {
                    _metaData = null;
                    _data = null;
                }
            }
        }

        public synchronized StoreFuture flushToStore()
        {
            if(!stored())
            {
                try
                {
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

                    storedSizeChangeOccured(getMetaData().getContentSize());
                }
                catch (AMQStoreException e)
                {
                    throw new RuntimeException(e);
                }
            }
            return StoreFuture.IMMEDIATE_FUTURE;
        }

        public void remove()
        {
            try
            {
                int delta = getMetaData().getContentSize();
                BDBMessageStore.this.removeMessage(_messageId, false);
                storedSizeChangeOccured(-delta);

            }
            catch (AMQStoreException e)
            {
                throw new RuntimeException(e);
            }
        }

        private boolean stored()
        {
            return _metaData == null || _isRecovered;
        }
    }

    private class BDBTransaction implements org.apache.qpid.server.store.Transaction
    {
        private com.sleepycat.je.Transaction _txn;
        private int _storeSizeIncrease;

        private BDBTransaction() throws AMQStoreException
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

        public void enqueueMessage(TransactionLogResource queue, EnqueueableMessage message) throws AMQStoreException
        {
            if(message.getStoredMessage() instanceof StoredBDBMessage)
            {
                final StoredBDBMessage storedMessage = (StoredBDBMessage) message.getStoredMessage();
                storedMessage.store(_txn);
                _storeSizeIncrease += storedMessage.getMetaData().getContentSize();
            }

            BDBMessageStore.this.enqueueMessage(_txn, queue, message.getMessageNumber());
        }

        public void dequeueMessage(TransactionLogResource queue, EnqueueableMessage message) throws AMQStoreException
        {
            BDBMessageStore.this.dequeueMessage(_txn, queue, message.getMessageNumber());
        }

        public void commitTran() throws AMQStoreException
        {
            BDBMessageStore.this.commitTranImpl(_txn, true);
            BDBMessageStore.this.storedSizeChangeOccured(_storeSizeIncrease);
        }

        public StoreFuture commitTranAsync() throws AMQStoreException
        {
            BDBMessageStore.this.storedSizeChangeOccured(_storeSizeIncrease);
            return BDBMessageStore.this.commitTranImpl(_txn, false);
        }

        public void abortTran() throws AMQStoreException
        {
            BDBMessageStore.this.abortTran(_txn);
        }

        public void removeXid(long format, byte[] globalId, byte[] branchId) throws AMQStoreException
        {
            BDBMessageStore.this.removeXid(_txn, format, globalId, branchId);
        }

        public void recordXid(long format, byte[] globalId, byte[] branchId, Record[] enqueues,
                              Record[] dequeues) throws AMQStoreException
        {
            BDBMessageStore.this.recordXid(_txn, format, globalId, branchId, enqueues, dequeues);
        }
    }

    private void storedSizeChangeOccured(final int delta) throws AMQStoreException
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

    private long getPersistentSizeLowThreshold()
    {
        return _persistentSizeLowThreshold;
    }

    private long getPersistentSizeHighThreshold()
    {
        return _persistentSizeHighThreshold;
    }


    @Override
    public void onDelete()
    {
        String storeLocation = getStoreLocation();

        if (storeLocation != null)
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Deleting store " + storeLocation);
            }

            File location = new File(storeLocation);
            if (location.exists())
            {
                if (!FileUtils.delete(location, true))
                {
                    LOGGER.error("Cannot delete " + storeLocation);
                }
            }
        }
    }

    @Override
    public String getStoreType()
    {
        return _type;
    }

    private Database getMessageContentDb()
    {
        return _environmentFacade.getOpenDatabase(MESSAGE_CONTENT_DB_NAME);
    }

    private Database getConfiguredObjectsDb()
    {
        return _environmentFacade.getOpenDatabase(CONFIGURED_OBJECTS_DB_NAME);
    }

    private Database getConfigVersionDb()
    {
        return _environmentFacade.getOpenDatabase(CONFIG_VERSION_DB_NAME);
    }

    private Database getMessageMetaDataDb()
    {
        return _environmentFacade.getOpenDatabase(MESSAGE_META_DATA_DB_NAME);
    }

    private Database getDeliveryDb()
    {
        return _environmentFacade.getOpenDatabase(DELIVERY_DB_NAME);
    }

    private Database getXidDb()
    {
        return _environmentFacade.getOpenDatabase(XID_DB_NAME);
    }

}
