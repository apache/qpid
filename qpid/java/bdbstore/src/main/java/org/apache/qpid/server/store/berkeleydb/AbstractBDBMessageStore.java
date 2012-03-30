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
import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockConflictException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.TransactionConfig;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;

import org.apache.qpid.AMQStoreException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.federation.Bridge;
import org.apache.qpid.server.federation.BrokerLink;
import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler.BindingRecoveryHandler;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler.ExchangeRecoveryHandler;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler.QueueRecoveryHandler;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.Event;
import org.apache.qpid.server.store.EventListener;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreRecoveryHandler;
import org.apache.qpid.server.store.MessageStoreRecoveryHandler.StoredMessageRecoveryHandler;
import org.apache.qpid.server.store.State;
import org.apache.qpid.server.store.StateManager;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.StoredMemoryMessage;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.TransactionLogRecoveryHandler;
import org.apache.qpid.server.store.TransactionLogRecoveryHandler.QueueEntryRecoveryHandler;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.store.berkeleydb.entry.BindingRecord;
import org.apache.qpid.server.store.berkeleydb.entry.ExchangeRecord;
import org.apache.qpid.server.store.berkeleydb.entry.PreparedTransaction;
import org.apache.qpid.server.store.berkeleydb.entry.QueueEntryKey;
import org.apache.qpid.server.store.berkeleydb.entry.QueueRecord;
import org.apache.qpid.server.store.berkeleydb.entry.Xid;
import org.apache.qpid.server.store.berkeleydb.tuple.AMQShortStringBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.ContentBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.ExchangeBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.MessageMetaDataBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.PreparedTransactionBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.QueueBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.QueueBindingTupleBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.QueueEntryBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.StringMapBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.UUIDTupleBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.XidBinding;
import org.apache.qpid.server.store.berkeleydb.upgrade.Upgrader;

import java.io.File;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractBDBMessageStore implements MessageStore
{
    private static final Logger LOGGER = Logger.getLogger(AbstractBDBMessageStore.class);

    private static final int LOCK_RETRY_ATTEMPTS = 5;

    public static final int VERSION = 6;

    public static final String ENVIRONMENT_PATH_PROPERTY = "environment-path";

    private Environment _environment;

    private String MESSAGEMETADATADB_NAME = "MESSAGE_METADATA";
    private String MESSAGECONTENTDB_NAME = "MESSAGE_CONTENT";
    private String QUEUEBINDINGSDB_NAME = "QUEUE_BINDINGS";
    private String DELIVERYDB_NAME = "DELIVERIES";
    private String EXCHANGEDB_NAME = "EXCHANGES";
    private String QUEUEDB_NAME = "QUEUES";
    private String BRIDGEDB_NAME = "BRIDGES";
    private String LINKDB_NAME = "LINKS";
    private String XIDDB_NAME = "XIDS";


    private Database _messageMetaDataDb;
    private Database _messageContentDb;
    private Database _queueBindingsDb;
    private Database _deliveryDb;
    private Database _exchangeDb;
    private Database _queueDb;
    private Database _bridgeDb;
    private Database _linkDb;
    private Database _xidDb;

    /* =======
     * Schema:
     * =======
     *
     * Queue:
     * name(AMQShortString) - name(AMQShortString), owner(AMQShortString),
     *                        arguments(FieldTable encoded as binary), exclusive (boolean)
     *
     * Exchange:
     * name(AMQShortString) - name(AMQShortString), typeName(AMQShortString), autodelete (boolean)
     *
     * Binding:
     * exchangeName(AMQShortString), queueName(AMQShortString), routingKey(AMQShortString),
     *                                            arguments (FieldTable encoded as binary) - 0 (zero)
     *
     * QueueEntry:
     * queueName(AMQShortString), messageId (long) - 0 (zero)
     *
     * Message (MetaData):
     * messageId (long) - bodySize (integer), metaData (MessageMetaData encoded as binary)
     *
     * Message (Content):
     * messageId (long), byteOffset (integer) - dataLength(integer), data(binary)
     */

    private final AtomicLong _messageId = new AtomicLong(0);

    protected final StateManager _stateManager = new StateManager();

    protected TransactionConfig _transactionConfig = new TransactionConfig();

    private boolean _readOnly = false;

    private MessageStoreRecoveryHandler _messageRecoveryHandler;

    private TransactionLogRecoveryHandler _tlogRecoveryHandler;

    private ConfigurationRecoveryHandler _configRecoveryHandler;

    public AbstractBDBMessageStore()
    {
    }

    public void configureConfigStore(String name,
                                     ConfigurationRecoveryHandler recoveryHandler,
                                     Configuration storeConfiguration) throws Exception
    {
        _stateManager.stateTransition(State.INITIAL, State.CONFIGURING);

        _configRecoveryHandler = recoveryHandler;

        configure(name,storeConfiguration);
    }

    public void configureMessageStore(String name,
                                      MessageStoreRecoveryHandler messageRecoveryHandler,
                                      TransactionLogRecoveryHandler tlogRecoveryHandler,
                                      Configuration storeConfiguration) throws Exception
    {
        _messageRecoveryHandler = messageRecoveryHandler;
        _tlogRecoveryHandler = tlogRecoveryHandler;
    }

    public void activate() throws Exception
    {
        _stateManager.stateTransition(State.CONFIGURING, State.RECOVERING);

        recoverConfig(_configRecoveryHandler);
        recoverMessages(_messageRecoveryHandler);
        recoverQueueEntries(_tlogRecoveryHandler);
        _stateManager.stateTransition(State.RECOVERING, State.ACTIVE);
    }

    public org.apache.qpid.server.store.Transaction newTransaction()
    {
        return new BDBTransaction();
    }


    /**
     * Called after instantiation in order to configure the message store.
     *
     * @param name The name of the virtual host using this store
     * @return whether a new store environment was created or not (to indicate whether recovery is necessary)
     *
     * @throws Exception If any error occurs that means the store is unable to configure itself.
     */
    public void configure(String name, Configuration storeConfig) throws Exception
    {
        File environmentPath = new File(storeConfig.getString(ENVIRONMENT_PATH_PROPERTY,
                                System.getProperty("QPID_WORK") + File.separator + "bdbstore" + File.separator + name));
        if (!environmentPath.exists())
        {
            if (!environmentPath.mkdirs())
            {
                throw new IllegalArgumentException("Environment path " + environmentPath + " could not be read or created. "
                                                   + "Ensure the path is correct and that the permissions are correct.");
            }
        }

        configure(environmentPath, false);
    }

    /**
     * @param environmentPath location for the store to be created in/recovered from
     * @param readonly if true then don't allow modifications to an existing store, and don't create a new store if none exists
     * @return whether or not a new store environment was created
     * @throws AMQStoreException
     * @throws DatabaseException
     */
    protected void configure(File environmentPath, boolean readonly) throws AMQStoreException, DatabaseException
    {
        if (_stateManager.isInState(State.INITIAL))
        {
            // TODO - currently required for BDBUpgrade and BDBMessageStoreTest
            _stateManager.stateTransition(State.INITIAL, State.CONFIGURING);
        }

        _readOnly = readonly;

        LOGGER.info("Configuring BDB message store");

        setupStore(environmentPath, readonly);
    }

    /**
     * Move the store state from CONFIGURING to ACTIVE.
     *
     * This is required if you do not want to perform recovery of the store data
     *
     * @throws AMQStoreException if the store is not in the correct state
     */
    public void start() throws AMQStoreException
    {
        _stateManager.stateTransition(State.CONFIGURING, State.ACTIVE);
    }

    protected void setupStore(File storePath, boolean readonly) throws DatabaseException, AMQStoreException
    {
        _environment = createEnvironment(storePath, readonly);

        new Upgrader(_environment).upgradeIfNecessary();

        openDatabases(readonly);
    }

    protected Environment createEnvironment(File environmentPath, boolean readonly) throws DatabaseException
    {
        LOGGER.info("BDB message store using environment path " + environmentPath.getAbsolutePath());
        EnvironmentConfig envConfig = new EnvironmentConfig();
        // This is what allows the creation of the store if it does not already exist.
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        envConfig.setConfigParam("je.lock.nLockTables", "7");

        // Added to help diagnosis of Deadlock issue
        // http://www.oracle.com/technology/products/berkeley-db/faq/je_faq.html#23
        if (Boolean.getBoolean("qpid.bdb.lock.debug"))
        {
            envConfig.setConfigParam("je.txn.deadlockStackTrace", "true");
            envConfig.setConfigParam("je.txn.dumpLocks", "true");
        }

        // Set transaction mode
        _transactionConfig.setReadCommitted(true);

        //This prevents background threads running which will potentially update the store.
        envConfig.setReadOnly(readonly);
        try
        {
            return new Environment(environmentPath, envConfig);
        }
        catch (DatabaseException de)
        {
            if (de.getMessage().contains("Environment.setAllowCreate is false"))
            {
                //Allow the creation this time
                envConfig.setAllowCreate(true);
                if (_environment != null )
                {
                    _environment.cleanLog();
                    _environment.close();
                }
                return new Environment(environmentPath, envConfig);
            }
            else
            {
                throw de;
            }
        }
    }

    public Environment getEnvironment()
    {
        return _environment;
    }

    private void openDatabases(boolean readonly) throws DatabaseException
    {
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);

        //This is required if we are wanting read only access.
        dbConfig.setReadOnly(readonly);

        _messageMetaDataDb = openDatabase(MESSAGEMETADATADB_NAME, dbConfig);
        _queueDb = openDatabase(QUEUEDB_NAME, dbConfig);
        _exchangeDb = openDatabase(EXCHANGEDB_NAME, dbConfig);
        _queueBindingsDb = openDatabase(QUEUEBINDINGSDB_NAME, dbConfig);
        _messageContentDb = openDatabase(MESSAGECONTENTDB_NAME, dbConfig);
        _deliveryDb = openDatabase(DELIVERYDB_NAME, dbConfig);
        _linkDb = openDatabase(LINKDB_NAME, dbConfig);
        _bridgeDb = openDatabase(BRIDGEDB_NAME, dbConfig);
        _xidDb = openDatabase(XIDDB_NAME, dbConfig);
    }

    private Database openDatabase(final String dbName, final DatabaseConfig dbConfig)
    {
        // if opening read-only and the database doesn't exist, then you can't create it
        return dbConfig.getReadOnly() && !_environment.getDatabaseNames().contains(dbName)
               ? null
               : _environment.openDatabase(null, dbName, dbConfig);
    }

    /**
     * Called to close and cleanup any resources used by the message store.
     *
     * @throws Exception If the close fails.
     */
    public void close() throws Exception
    {
        if (_stateManager.isInState(State.ACTIVE))
        {
            _stateManager.stateTransition(State.ACTIVE, State.CLOSING);

            closeInternal();

            _stateManager.stateTransition(State.CLOSING, State.CLOSED);
        }
    }

    protected void closeInternal() throws Exception
    {
        if (_messageMetaDataDb != null)
        {
            LOGGER.info("Closing message metadata database");
            _messageMetaDataDb.close();
        }

        if (_messageContentDb != null)
        {
            LOGGER.info("Closing message content database");
            _messageContentDb.close();
        }

        if (_exchangeDb != null)
        {
            LOGGER.info("Closing exchange database");
            _exchangeDb.close();
        }

        if (_queueBindingsDb != null)
        {
            LOGGER.info("Closing bindings database");
            _queueBindingsDb.close();
        }

        if (_queueDb != null)
        {
            LOGGER.info("Closing queue database");
            _queueDb.close();
        }

        if (_deliveryDb != null)
        {
            LOGGER.info("Close delivery database");
            _deliveryDb.close();
        }

        if (_bridgeDb != null)
        {
            LOGGER.info("Close bridge database");
            _bridgeDb.close();
        }

        if (_linkDb != null)
        {
            LOGGER.info("Close link database");
            _linkDb.close();
        }


        if (_xidDb != null)
        {
            LOGGER.info("Close xid database");
            _xidDb.close();
        }

        closeEnvironment();

    }

    private void closeEnvironment() throws DatabaseException
    {
        if (_environment != null)
        {
            if(!_readOnly)
            {
                // Clean the log before closing. This makes sure it doesn't contain
                // redundant data. Closing without doing this means the cleaner may not
                // get a chance to finish.
                _environment.cleanLog();
            }
            _environment.close();
        }
    }


    private void recoverConfig(ConfigurationRecoveryHandler recoveryHandler) throws AMQStoreException
    {
        try
        {
            QueueRecoveryHandler qrh = recoveryHandler.begin(this);
            loadQueues(qrh);

            ExchangeRecoveryHandler erh = qrh.completeQueueRecovery();
            loadExchanges(erh);

            BindingRecoveryHandler brh = erh.completeExchangeRecovery();
            recoverBindings(brh);

            ConfigurationRecoveryHandler.BrokerLinkRecoveryHandler lrh = brh.completeBindingRecovery();
            recoverBrokerLinks(lrh);
        }
        catch (DatabaseException e)
        {
            throw new AMQStoreException("Error recovering persistent state: " + e.getMessage(), e);
        }

    }

    private void loadQueues(QueueRecoveryHandler qrh) throws DatabaseException
    {
        Cursor cursor = null;

        try
        {
            cursor = _queueDb.openCursor(null, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            QueueBinding binding = QueueBinding.getInstance();
            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                QueueRecord queueRecord = binding.entryToObject(value);

                String queueName = queueRecord.getNameShortString() == null ? null :
                                        queueRecord.getNameShortString().asString();
                String owner = queueRecord.getOwner() == null ? null :
                                        queueRecord.getOwner().asString();
                boolean exclusive = queueRecord.isExclusive();

                FieldTable arguments = queueRecord.getArguments();

                qrh.queue(queueName, owner, exclusive, arguments);
            }

        }
        finally
        {
            closeCursorSafely(cursor);
        }
    }

    private void closeCursorSafely(Cursor cursor)
    {
        if (cursor != null)
        {
            cursor.close();
        }
    }


    private void loadExchanges(ExchangeRecoveryHandler erh) throws DatabaseException
    {
        Cursor cursor = null;

        try
        {
            cursor = _exchangeDb.openCursor(null, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            ExchangeBinding binding = ExchangeBinding.getInstance();

            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                ExchangeRecord exchangeRec = binding.entryToObject(value);

                String exchangeName = exchangeRec.getNameShortString() == null ? null :
                                      exchangeRec.getNameShortString().asString();
                String type = exchangeRec.getType() == null ? null :
                              exchangeRec.getType().asString();
                boolean autoDelete = exchangeRec.isAutoDelete();

                erh.exchange(exchangeName, type, autoDelete);
            }
        }
        finally
        {
            closeCursorSafely(cursor);
        }

    }

    private void recoverBindings(BindingRecoveryHandler brh) throws DatabaseException
    {
        Cursor cursor = null;
        try
        {
            cursor = _queueBindingsDb.openCursor(null, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            QueueBindingTupleBinding binding = QueueBindingTupleBinding.getInstance();

            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                //yes, this is retrieving all the useful information from the key only.
                //For table compatibility it shall currently be left as is
                BindingRecord bindingRecord = binding.entryToObject(key);

                String exchangeName = bindingRecord.getExchangeName() == null ? null :
                                      bindingRecord.getExchangeName().asString();
                String queueName = bindingRecord.getQueueName() == null ? null :
                                   bindingRecord.getQueueName().asString();
                String routingKey = bindingRecord.getRoutingKey() == null ? null :
                                    bindingRecord.getRoutingKey().asString();
                ByteBuffer argumentsBB = (bindingRecord.getArguments() == null ? null :
                    java.nio.ByteBuffer.wrap(bindingRecord.getArguments().getDataAsBytes()));

                brh.binding(exchangeName, queueName, routingKey, argumentsBB);
            }
        }
        finally
        {
            closeCursorSafely(cursor);
        }

    }


    private void recoverBrokerLinks(final ConfigurationRecoveryHandler.BrokerLinkRecoveryHandler lrh)
    {
        Cursor cursor = null;

        try
        {
            cursor = _linkDb.openCursor(null, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();

            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                UUID id = UUIDTupleBinding.getInstance().entryToObject(key);
                long createTime = LongBinding.entryToLong(value);
                Map<String,String> arguments = StringMapBinding.getInstance().entryToObject(value);

                ConfigurationRecoveryHandler.BridgeRecoveryHandler brh = lrh.brokerLink(id, createTime, arguments);

                recoverBridges(brh, id);
            }
        }
        finally
        {
            closeCursorSafely(cursor);
        }

    }

    private void recoverBridges(final ConfigurationRecoveryHandler.BridgeRecoveryHandler brh, final UUID linkId)
    {
        Cursor cursor = null;

        try
        {
            cursor = _bridgeDb.openCursor(null, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();

            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                UUID id = UUIDTupleBinding.getInstance().entryToObject(key);

                UUID parentId = UUIDTupleBinding.getInstance().entryToObject(value);
                if(parentId.equals(linkId))
                {

                    long createTime = LongBinding.entryToLong(value);
                    Map<String,String> arguments = StringMapBinding.getInstance().entryToObject(value);
                    brh.bridge(id,createTime,arguments);
                }
            }
            brh.completeBridgeRecoveryForLink();
        }
        finally
        {
            closeCursorSafely(cursor);
        }

    }


    private void recoverMessages(MessageStoreRecoveryHandler msrh) throws DatabaseException
    {
        StoredMessageRecoveryHandler mrh = msrh.begin();

        Cursor cursor = null;
        try
        {
            cursor = _messageMetaDataDb.openCursor(null, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            MessageMetaDataBinding valueBinding = MessageMetaDataBinding.getInstance();

            long maxId = 0;

            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                long messageId = LongBinding.entryToLong(key);
                StorableMessageMetaData metaData = valueBinding.entryToObject(value);

                StoredBDBMessage message = new StoredBDBMessage(messageId, metaData, false);
                mrh.message(message);

                maxId = Math.max(maxId, messageId);
            }

            _messageId.set(maxId);
        }
        catch (DatabaseException e)
        {
            LOGGER.error("Database Error: " + e.getMessage(), e);
            throw e;
        }
        finally
        {
            closeCursorSafely(cursor);
        }
    }

    private void recoverQueueEntries(TransactionLogRecoveryHandler recoveryHandler)
    throws DatabaseException
    {
        QueueEntryRecoveryHandler qerh = recoveryHandler.begin(this);

        ArrayList<QueueEntryKey> entries = new ArrayList<QueueEntryKey>();

        Cursor cursor = null;
        try
        {
            cursor = _deliveryDb.openCursor(null, null);
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
                AMQShortString queueName = entry.getQueueName();
                long messageId = entry.getMessageId();

                qerh.queueEntry(queueName.asString(),messageId);
            }
        }
        catch (DatabaseException e)
        {
            LOGGER.error("Database Error: " + e.getMessage(), e);
            throw e;
        }
        finally
        {
            closeCursorSafely(cursor);
        }

        TransactionLogRecoveryHandler.DtxRecordRecoveryHandler dtxrh = qerh.completeQueueEntryRecovery();

        cursor = null;
        try
        {
            cursor = _xidDb.openCursor(null, null);
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
            LOGGER.error("Database Error: " + e.getMessage(), e);
            throw e;
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
                    tx = _environment.beginTransaction(null, null);

                    //remove the message meta data from the store
                    DatabaseEntry key = new DatabaseEntry();
                    LongBinding.longToEntry(messageId, key);

                    if (LOGGER.isDebugEnabled())
                    {
                        LOGGER.debug("Removing message id " + messageId);
                    }


                    OperationStatus status = _messageMetaDataDb.delete(tx, key);
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
                    _messageContentDb.delete(tx, contentKeyEntry);

                    if (LOGGER.isDebugEnabled())
                    {
                        LOGGER.debug("Deleted content for message " + messageId);
                    }

                    commit(tx, sync);
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
                        LOGGER.warn("Unable to abort transaction after LockConflictExcption", e2);
                        // rethrow the original log conflict exception, the secondary exception should already have
                        // been logged.
                        throw e;
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
                        throw e;
                    }
                }
            }
            while(!complete);
        }
        catch (DatabaseException e)
        {
            LOGGER.error("Unexpected BDB exception", e);

            if (tx != null)
            {
                try
                {
                    tx.abort();
                    tx = null;
                }
                catch (DatabaseException e1)
                {
                    throw new AMQStoreException("Error aborting transaction " + e1, e1);
                }
            }

            throw new AMQStoreException("Error removing message with id " + messageId + " from database: " + e.getMessage(), e);
        }
        finally
        {
            if (tx != null)
            {
                try
                {
                    tx.abort();
                    tx = null;
                }
                catch (DatabaseException e1)
                {
                    throw new AMQStoreException("Error aborting transaction " + e1, e1);
                }
            }
        }
    }

    /**
     * @see DurableConfigurationStore#createExchange(Exchange)
     */
    public void createExchange(Exchange exchange) throws AMQStoreException
    {
        if (_stateManager.isInState(State.ACTIVE))
        {
            ExchangeRecord exchangeRec = new ExchangeRecord(exchange.getNameShortString(),
                                             exchange.getTypeShortString(), exchange.isAutoDelete());

            DatabaseEntry key = new DatabaseEntry();
            AMQShortStringBinding keyBinding = AMQShortStringBinding.getInstance();
            keyBinding.objectToEntry(exchange.getNameShortString(), key);

            DatabaseEntry value = new DatabaseEntry();
            ExchangeBinding exchangeBinding = ExchangeBinding.getInstance();
            exchangeBinding.objectToEntry(exchangeRec, value);

            try
            {
                _exchangeDb.put(null, key, value);
            }
            catch (DatabaseException e)
            {
                throw new AMQStoreException("Error writing Exchange with name " + exchange.getName() + " to database: " + e.getMessage(), e);
            }
        }
    }

    /**
     * @see DurableConfigurationStore#removeExchange(Exchange)
     */
    public void removeExchange(Exchange exchange) throws AMQStoreException
    {
        DatabaseEntry key = new DatabaseEntry();
        AMQShortStringBinding keyBinding = AMQShortStringBinding.getInstance();
        keyBinding.objectToEntry(exchange.getNameShortString(), key);
        try
        {
            OperationStatus status = _exchangeDb.delete(null, key);
            if (status == OperationStatus.NOTFOUND)
            {
                throw new AMQStoreException("Exchange " + exchange.getName() + " not found");
            }
        }
        catch (DatabaseException e)
        {
            throw new AMQStoreException("Error writing deleting with name " + exchange.getName() + " from database: " + e.getMessage(), e);
        }
    }


    /**
     * @see DurableConfigurationStore#bindQueue(Exchange, AMQShortString, AMQQueue, FieldTable)
     */
    public void bindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQStoreException
    {
        bindQueue(new BindingRecord(exchange.getNameShortString(), queue.getNameShortString(), routingKey, args));
    }

    protected void bindQueue(final BindingRecord bindingRecord) throws AMQStoreException
    {
        if (_stateManager.isInState(State.ACTIVE))
        {
            DatabaseEntry key = new DatabaseEntry();
            QueueBindingTupleBinding keyBinding = QueueBindingTupleBinding.getInstance();

            keyBinding.objectToEntry(bindingRecord, key);

            //yes, this is writing out 0 as a value and putting all the
            //useful info into the key, don't ask me why. For table
            //compatibility it shall currently be left as is
            DatabaseEntry value = new DatabaseEntry();
            ByteBinding.byteToEntry((byte) 0, value);

            try
            {
                _queueBindingsDb.put(null, key, value);
            }
            catch (DatabaseException e)
            {
                throw new AMQStoreException("Error writing binding for AMQQueue with name " + bindingRecord.getQueueName() + " to exchange "
                                       + bindingRecord.getExchangeName() + " to database: " + e.getMessage(), e);
            }
        }
    }

    /**
     * @see DurableConfigurationStore#unbindQueue(Exchange, AMQShortString, AMQQueue, FieldTable)
     */
    public void unbindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args)
            throws AMQStoreException
    {
        DatabaseEntry key = new DatabaseEntry();
        QueueBindingTupleBinding keyBinding = QueueBindingTupleBinding.getInstance();
        keyBinding.objectToEntry(new BindingRecord(exchange.getNameShortString(), queue.getNameShortString(), routingKey, args), key);

        try
        {
            OperationStatus status = _queueBindingsDb.delete(null, key);
            if (status == OperationStatus.NOTFOUND)
            {
                throw new AMQStoreException("Queue binding for queue with name " + queue.getName() + " to exchange "
                                       + exchange.getName() + "  not found");
            }
        }
        catch (DatabaseException e)
        {
            throw new AMQStoreException("Error deleting queue binding for queue with name " + queue.getName() + " to exchange "
                                   + exchange.getName() + " from database: " + e.getMessage(), e);
        }
    }

    /**
     * @see DurableConfigurationStore#createQueue(AMQQueue)
     */
    public void createQueue(AMQQueue queue) throws AMQStoreException
    {
        createQueue(queue, null);
    }

    /**
     * @see DurableConfigurationStore#createQueue(AMQQueue, FieldTable)
     */
    public void createQueue(AMQQueue queue, FieldTable arguments) throws AMQStoreException
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("public void createQueue(AMQQueue queue(" + queue.getName() + ") = " + queue + "): called");
        }

        QueueRecord queueRecord= new QueueRecord(queue.getNameShortString(),
                                                queue.getOwner(), queue.isExclusive(), arguments);

        createQueue(queueRecord);
    }

    /**
     * Makes the specified queue persistent.
     *
     * Only intended for direct use during store upgrades.
     *
     * @param queueRecord     Details of the queue to store.
     *
     * @throws AMQStoreException If the operation fails for any reason.
     */
    protected void createQueue(QueueRecord queueRecord) throws AMQStoreException
    {
        if (_stateManager.isInState(State.ACTIVE))
        {
            DatabaseEntry key = new DatabaseEntry();
            AMQShortStringBinding keyBinding = AMQShortStringBinding.getInstance();
            keyBinding.objectToEntry(queueRecord.getNameShortString(), key);

            DatabaseEntry value = new DatabaseEntry();
            QueueBinding queueBinding = QueueBinding.getInstance();

            queueBinding.objectToEntry(queueRecord, value);
            try
            {
                _queueDb.put(null, key, value);
            }
            catch (DatabaseException e)
            {
                throw new AMQStoreException("Error writing AMQQueue with name " + queueRecord.getNameShortString().asString()
                        + " to database: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Updates the specified queue in the persistent store, IF it is already present. If the queue
     * is not present in the store, it will not be added.
     *
     * NOTE: Currently only updates the exclusivity.
     *
     * @param queue The queue to update the entry for.
     * @throws AMQStoreException If the operation fails for any reason.
     */
    public void updateQueue(final AMQQueue queue) throws AMQStoreException
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Updating queue: " + queue.getName());
        }

        try
        {
            DatabaseEntry key = new DatabaseEntry();
            AMQShortStringBinding keyBinding = AMQShortStringBinding.getInstance();
            keyBinding.objectToEntry(queue.getNameShortString(), key);

            DatabaseEntry value = new DatabaseEntry();
            DatabaseEntry newValue = new DatabaseEntry();
            QueueBinding queueBinding = QueueBinding.getInstance();

            OperationStatus status = _queueDb.get(null, key, value, LockMode.DEFAULT);
            if(status == OperationStatus.SUCCESS)
            {
                //read the existing record and apply the new exclusivity setting
                QueueRecord queueRecord = queueBinding.entryToObject(value);
                queueRecord.setExclusive(queue.isExclusive());

                //write the updated entry to the store
                queueBinding.objectToEntry(queueRecord, newValue);

                _queueDb.put(null, key, newValue);
            }
            else if(status != OperationStatus.NOTFOUND)
            {
                throw new AMQStoreException("Error updating queue details within the store: " + status);
            }
        }
        catch (DatabaseException e)
        {
            throw new AMQStoreException("Error updating queue details within the store: " + e,e);
        }
    }

    /**
     * Removes the specified queue from the persistent store.
     *
     * @param queue The queue to remove.
     *
     * @throws AMQStoreException If the operation fails for any reason.
     */
    public void removeQueue(final AMQQueue queue) throws AMQStoreException
    {
        AMQShortString name = queue.getNameShortString();

        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("public void removeQueue(AMQShortString name = " + name + "): called");
        }

        DatabaseEntry key = new DatabaseEntry();
        AMQShortStringBinding keyBinding = AMQShortStringBinding.getInstance();
        keyBinding.objectToEntry(name, key);
        try
        {
            OperationStatus status = _queueDb.delete(null, key);
            if (status == OperationStatus.NOTFOUND)
            {
                throw new AMQStoreException("Queue " + name + " not found");
            }
        }
        catch (DatabaseException e)
        {
            throw new AMQStoreException("Error writing deleting with name " + name + " from database: " + e.getMessage(), e);
        }
    }

    public void createBrokerLink(final BrokerLink link) throws AMQStoreException
    {
        if (_stateManager.isInState(State.ACTIVE))
        {
            DatabaseEntry key = new DatabaseEntry();
            UUIDTupleBinding.getInstance().objectToEntry(link.getId(), key);

            DatabaseEntry value = new DatabaseEntry();
            LongBinding.longToEntry(link.getCreateTime(),value);
            StringMapBinding.getInstance().objectToEntry(link.getArguments(), value);

            try
            {
                _linkDb.put(null, key, value);
            }
            catch (DatabaseException e)
            {
                throw new AMQStoreException("Error writing Link  " + link
                                            + " to database: " + e.getMessage(), e);
            }
        }
    }

    public void deleteBrokerLink(final BrokerLink link) throws AMQStoreException
    {
        DatabaseEntry key = new DatabaseEntry();
        UUIDTupleBinding.getInstance().objectToEntry(link.getId(), key);
        try
        {
            OperationStatus status = _linkDb.delete(null, key);
            if (status == OperationStatus.NOTFOUND)
            {
                throw new AMQStoreException("Link " + link + " not found");
            }
        }
        catch (DatabaseException e)
        {
            throw new AMQStoreException("Error deleting the Link " + link + " from database: " + e.getMessage(), e);
        }
    }

    public void createBridge(final Bridge bridge) throws AMQStoreException
    {
        if (_stateManager.isInState(State.ACTIVE))
        {
            DatabaseEntry key = new DatabaseEntry();
            UUIDTupleBinding.getInstance().objectToEntry(bridge.getId(), key);

            DatabaseEntry value = new DatabaseEntry();
            UUIDTupleBinding.getInstance().objectToEntry(bridge.getLink().getId(),value);
            LongBinding.longToEntry(bridge.getCreateTime(),value);
            StringMapBinding.getInstance().objectToEntry(bridge.getArguments(), value);

            try
            {
                _bridgeDb.put(null, key, value);
            }
            catch (DatabaseException e)
            {
                throw new AMQStoreException("Error writing Bridge  " + bridge
                                            + " to database: " + e.getMessage(), e);
            }

        }
    }

    public void deleteBridge(final Bridge bridge) throws AMQStoreException
    {
        DatabaseEntry key = new DatabaseEntry();
        UUIDTupleBinding.getInstance().objectToEntry(bridge.getId(), key);
        try
        {
            OperationStatus status = _bridgeDb.delete(null, key);
            if (status == OperationStatus.NOTFOUND)
            {
                throw new AMQStoreException("Bridge " + bridge + " not found");
            }
        }
        catch (DatabaseException e)
        {
            throw new AMQStoreException("Error deleting the Bridge " + bridge + " from database: " + e.getMessage(), e);
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
        AMQShortString name = AMQShortString.valueOf(queue.getResourceName());

        DatabaseEntry key = new DatabaseEntry();
        QueueEntryBinding keyBinding = QueueEntryBinding.getInstance();
        QueueEntryKey dd = new QueueEntryKey(name, messageId);
        keyBinding.objectToEntry(dd, key);
        DatabaseEntry value = new DatabaseEntry();
        ByteBinding.byteToEntry((byte) 0, value);

        try
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Enqueuing message " + messageId + " on queue " + name + " [Transaction" + tx + "]");
            }
            _deliveryDb.put(tx, key, value);
        }
        catch (DatabaseException e)
        {
            LOGGER.error("Failed to enqueue: " + e.getMessage(), e);
            throw new AMQStoreException("Error writing enqueued message with id " + messageId + " for queue " + name
                                   + " to database", e);
        }
    }

    /**
     * Extracts a message from a specified queue, in a given transaction.
     *
     * @param tx   The transaction for the operation.
     * @param queue     The name queue to take the message from.
     * @param messageId The message to dequeue.
     *
     * @throws AMQStoreException If the operation fails for any reason, or if the specified message does not exist.
     */
    public void dequeueMessage(final com.sleepycat.je.Transaction tx, final TransactionLogResource queue,
                               long messageId) throws AMQStoreException
    {
        AMQShortString name = new AMQShortString(queue.getResourceName());

        DatabaseEntry key = new DatabaseEntry();
        QueueEntryBinding keyBinding = QueueEntryBinding.getInstance();
        QueueEntryKey queueEntryKey = new QueueEntryKey(name, messageId);

        keyBinding.objectToEntry(queueEntryKey, key);

        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Dequeue message id " + messageId);
        }

        try
        {

            OperationStatus status = _deliveryDb.delete(tx, key);
            if (status == OperationStatus.NOTFOUND)
            {
                throw new AMQStoreException("Unable to find message with id " + messageId + " on queue " + name);
            }
            else if (status != OperationStatus.SUCCESS)
            {
                throw new AMQStoreException("Unable to remove message with id " + messageId + " on queue " + name);
            }

            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Removed message " + messageId + ", " + name + " from delivery db");

            }
        }
        catch (DatabaseException e)
        {

            LOGGER.error("Failed to dequeue message " + messageId + ": " + e.getMessage(), e);
            LOGGER.error(tx);

            throw new AMQStoreException("Error accessing database while dequeuing message: " + e.getMessage(), e);
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
            _xidDb.put(txn, key, value);
        }
        catch (DatabaseException e)
        {
            LOGGER.error("Failed to write xid: " + e.getMessage(), e);
            throw new AMQStoreException("Error writing xid to database", e);
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

            OperationStatus status = _xidDb.delete(txn, key);
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

            LOGGER.error("Failed to remove xid ", e);
            LOGGER.error(txn);

            throw new AMQStoreException("Error accessing database while removing xid: " + e.getMessage(), e);
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

        StoreFuture result;
        try
        {
            result = commit(tx, syncCommit);

            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("commitTranImpl completed for [Transaction:" + tx + "]");
            }
        }
        catch (DatabaseException e)
        {
            throw new AMQStoreException("Error commit tx: " + e.getMessage(), e);
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
            LOGGER.debug("abortTran called for [Transaction:" + tx + "]");
        }

        try
        {
            tx.abort();
        }
        catch (DatabaseException e)
        {
            throw new AMQStoreException("Error aborting transaction: " + e.getMessage(), e);
        }
    }

    /**
     * Primarily for testing purposes.
     *
     * @param queueName
     *
     * @return a list of message ids for messages enqueued for a particular queue
     */
    List<Long> getEnqueuedMessages(AMQShortString queueName) throws AMQStoreException
    {
        Cursor cursor = null;
        try
        {
            cursor = _deliveryDb.openCursor(null, null);

            DatabaseEntry key = new DatabaseEntry();

            QueueEntryKey dd = new QueueEntryKey(queueName, 0);

            QueueEntryBinding keyBinding = QueueEntryBinding.getInstance();
            keyBinding.objectToEntry(dd, key);

            DatabaseEntry value = new DatabaseEntry();

            LinkedList<Long> messageIds = new LinkedList<Long>();

            OperationStatus status = cursor.getSearchKeyRange(key, value, LockMode.DEFAULT);
            dd = keyBinding.entryToObject(key);

            while ((status == OperationStatus.SUCCESS) && dd.getQueueName().equals(queueName))
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
            if (cursor != null)
            {
                try
                {
                    cursor.close();
                }
                catch (DatabaseException e)
                {
                    throw new AMQStoreException("Error closing cursor: " + e.getMessage(), e);
                }
            }
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
            OperationStatus status = _messageContentDb.put(tx, key, value);
            if (status != OperationStatus.SUCCESS)
            {
                throw new AMQStoreException("Error adding content for message id " + messageId + ": " + status);
            }

            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Storing content for message " + messageId + "[Transaction" + tx + "]");

            }
        }
        catch (DatabaseException e)
        {
            throw new AMQStoreException("Error writing AMQMessage with id " + messageId + " to database: " + e.getMessage(), e);
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
            LOGGER.debug("public void storeMetaData(Txn tx = " + tx + ", Long messageId = "
                       + messageId + ", MessageMetaData messageMetaData = " + messageMetaData + "): called");
        }

        DatabaseEntry key = new DatabaseEntry();
        LongBinding.longToEntry(messageId, key);
        DatabaseEntry value = new DatabaseEntry();

        MessageMetaDataBinding messageBinding = MessageMetaDataBinding.getInstance();
        messageBinding.objectToEntry(messageMetaData, value);
        try
        {
            _messageMetaDataDb.put(tx, key, value);
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Storing message metadata for message id " + messageId + "[Transaction" + tx + "]");
            }
        }
        catch (DatabaseException e)
        {
            throw new AMQStoreException("Error writing message metadata with id " + messageId + " to database: " + e.getMessage(), e);
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
            OperationStatus status = _messageMetaDataDb.get(null, key, value, LockMode.READ_UNCOMMITTED);
            if (status != OperationStatus.SUCCESS)
            {
                throw new AMQStoreException("Metadata not found for message with id " + messageId);
            }

            StorableMessageMetaData mdd = messageBinding.entryToObject(value);

            return mdd;
        }
        catch (DatabaseException e)
        {
            throw new AMQStoreException("Error reading message metadata for message with id " + messageId + ": " + e.getMessage(), e);
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

        Cursor cursor = null;
        try
        {

            int written = 0;
            OperationStatus status = _messageContentDb.get(null, contentKeyEntry, value, LockMode.READ_UNCOMMITTED);
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
            throw new AMQStoreException("Error getting AMQMessage with id " + messageId + " to database: " + e.getMessage(), e);
        }
    }

    public boolean isPersistent()
    {
        return true;
    }

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

    //Package getters for the various databases used by the Store

    Database getMetaDataDb()
    {
        return _messageMetaDataDb;
    }

    Database getContentDb()
    {
        return _messageContentDb;
    }

    Database getQueuesDb()
    {
        return _queueDb;
    }

    Database getDeliveryDb()
    {
        return _deliveryDb;
    }

    Database getExchangesDb()
    {
        return _exchangeDb;
    }

    Database getBindingsDb()
    {
        return _queueBindingsDb;
    }

    protected abstract StoreFuture commit(com.sleepycat.je.Transaction tx, boolean syncCommit) throws DatabaseException;


    private class StoredBDBMessage implements StoredMessage<StorableMessageMetaData>
    {

        private final long _messageId;
        private volatile SoftReference<StorableMessageMetaData> _metaDataRef;

        private StorableMessageMetaData _metaData;
        private volatile SoftReference<byte[]> _dataRef;
        private byte[] _data;

        StoredBDBMessage(long messageId, StorableMessageMetaData metaData)
        {
            this(messageId, metaData, true);
        }


        StoredBDBMessage(long messageId,
                           StorableMessageMetaData metaData, boolean persist)
        {
            try
            {
                _messageId = messageId;
                _metaData = metaData;

                _metaDataRef = new SoftReference<StorableMessageMetaData>(metaData);

            }
            catch (DatabaseException e)
            {
                throw new RuntimeException(e);
            }

        }

        public StorableMessageMetaData getMetaData()
        {
            StorableMessageMetaData metaData = _metaDataRef.get();
            if(metaData == null)
            {
                try
                {
                    metaData = AbstractBDBMessageStore.this.getMessageMetaData(_messageId);
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
                    return AbstractBDBMessageStore.this.getContent(_messageId, offsetInMessage, dst);
                }
                catch (AMQStoreException e)
                {
                    // TODO maybe should throw a checked exception, or at least log before throwing
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
                getContent(offsetInMessage, buf);
                buf.position(0);
                return  buf;
            }
        }

        synchronized void store(com.sleepycat.je.Transaction txn)
        {

            if(_metaData != null)
            {
                try
                {
                    _dataRef = new SoftReference<byte[]>(_data);
                    AbstractBDBMessageStore.this.storeMetaData(txn, _messageId, _metaData);
                    AbstractBDBMessageStore.this.addContent(txn, _messageId, 0,
                                                    _data == null ? ByteBuffer.allocate(0) : ByteBuffer.wrap(_data));
                }
                catch(DatabaseException e)
                {
                    throw new RuntimeException(e);
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
            if(_metaData != null)
            {
                com.sleepycat.je.Transaction txn = _environment.beginTransaction(null, null);
                store(txn);
                AbstractBDBMessageStore.this.commit(txn,true);

            }
            return StoreFuture.IMMEDIATE_FUTURE;
        }

        public void remove()
        {
            try
            {
                AbstractBDBMessageStore.this.removeMessage(_messageId, false);
            }
            catch (AMQStoreException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    private class BDBTransaction implements org.apache.qpid.server.store.Transaction
    {
        private com.sleepycat.je.Transaction _txn;

        private BDBTransaction()
        {
            try
            {
                _txn = _environment.beginTransaction(null, null);
            }
            catch (DatabaseException e)
            {
                throw new RuntimeException(e);
            }
        }

        public void enqueueMessage(TransactionLogResource queue, EnqueableMessage message) throws AMQStoreException
        {
            if(message.getStoredMessage() instanceof StoredBDBMessage)
            {
                ((StoredBDBMessage)message.getStoredMessage()).store(_txn);
            }

            AbstractBDBMessageStore.this.enqueueMessage(_txn, queue, message.getMessageNumber());
        }

        public void dequeueMessage(TransactionLogResource queue, EnqueableMessage message) throws AMQStoreException
        {
            AbstractBDBMessageStore.this.dequeueMessage(_txn, queue, message.getMessageNumber());
        }

        public void commitTran() throws AMQStoreException
        {
            AbstractBDBMessageStore.this.commitTranImpl(_txn, true);
        }

        public StoreFuture commitTranAsync() throws AMQStoreException
        {
            return AbstractBDBMessageStore.this.commitTranImpl(_txn, false);
        }

        public void abortTran() throws AMQStoreException
        {
            AbstractBDBMessageStore.this.abortTran(_txn);
        }

        public void removeXid(long format, byte[] globalId, byte[] branchId) throws AMQStoreException
        {
            AbstractBDBMessageStore.this.removeXid(_txn, format, globalId, branchId);
        }

        public void recordXid(long format, byte[] globalId, byte[] branchId, Record[] enqueues,
                              Record[] dequeues) throws AMQStoreException
        {
            AbstractBDBMessageStore.this.recordXid(_txn, format, globalId, branchId, enqueues, dequeues);
        }
    }

    @Override
    public void addEventListener(EventListener eventListener, Event event)
    {
        throw new UnsupportedOperationException();
    }

    public MessageStore getUnderlyingStore()
    {
        return this;
    }
}
