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
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.*;
import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.federation.Bridge;
import org.apache.qpid.server.federation.BrokerLink;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ConfigStoreMessages;
import org.apache.qpid.server.logging.messages.MessageStoreMessages;
import org.apache.qpid.server.logging.messages.TransactionLogMessages;
import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreRecoveryHandler;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.StoredMemoryMessage;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.TransactionLogRecoveryHandler;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler.BindingRecoveryHandler;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler.ExchangeRecoveryHandler;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler.QueueRecoveryHandler;
import org.apache.qpid.server.store.MessageStoreRecoveryHandler.StoredMessageRecoveryHandler;
import org.apache.qpid.server.store.TransactionLogRecoveryHandler.QueueEntryRecoveryHandler;
import org.apache.qpid.server.store.berkeleydb.keys.MessageContentKey_5;
import org.apache.qpid.server.store.berkeleydb.records.ExchangeRecord;
import org.apache.qpid.server.store.berkeleydb.records.QueueRecord;
import org.apache.qpid.server.store.berkeleydb.tuples.BindingTupleBindingFactory;
import org.apache.qpid.server.store.berkeleydb.tuples.MessageContentKeyTB_5;
import org.apache.qpid.server.store.berkeleydb.tuples.MessageMetaDataTupleBindingFactory;
import org.apache.qpid.server.store.berkeleydb.tuples.QueueEntryTB;
import org.apache.qpid.server.store.berkeleydb.tuples.QueueTupleBindingFactory;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.bind.tuple.ByteBinding;
import com.sleepycat.bind.tuple.TupleBinding;

/**
 * BDBMessageStore implements a persistent {@link MessageStore} using the BDB high performance log.
 *
 * <p/><table id="crc"><caption>CRC Card</caption> <tr><th> Responsibilities <th> Collaborations <tr><td> Accept
 * transaction boundary demarcations: Begin, Commit, Abort. <tr><td> Store and remove queues. <tr><td> Store and remove
 * exchanges. <tr><td> Store and remove messages. <tr><td> Bind and unbind queues to exchanges. <tr><td> Enqueue and
 * dequeue messages to queues. <tr><td> Generate message identifiers. </table>
 */
@SuppressWarnings({"unchecked"})
public class BDBMessageStore implements MessageStore, DurableConfigurationStore
{
    private static final Logger _log = Logger.getLogger(BDBMessageStore.class);

    static final int DATABASE_FORMAT_VERSION = 5;
    private static final String DATABASE_FORMAT_VERSION_PROPERTY = "version";
    public static final String ENVIRONMENT_PATH_PROPERTY = "environment-path";

    private Environment _environment;

    private String MESSAGEMETADATADB_NAME = "messageMetaDataDb";
    private String MESSAGECONTENTDB_NAME = "messageContentDb";
    private String QUEUEBINDINGSDB_NAME = "queueBindingsDb";
    private String DELIVERYDB_NAME = "deliveryDb";
    private String EXCHANGEDB_NAME = "exchangeDb";
    private String QUEUEDB_NAME = "queueDb";
    private String BRIDGEDB_NAME = "bridges";
    private String LINKDB_NAME = "links";

    private Database _messageMetaDataDb;
    private Database _messageContentDb;
    private Database _queueBindingsDb;
    private Database _deliveryDb;
    private Database _exchangeDb;
    private Database _queueDb;
    private Database _bridgeDb;
    private Database _linkDb;

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
     * messageId (long), byteOffset (integer) - dataLength(integer), data(binary);
     */

    private LogSubject _logSubject;

    private final AtomicLong _messageId = new AtomicLong(0);

    private final CommitThread _commitThread = new CommitThread("Commit-Thread");

    // Factory Classes to create the TupleBinding objects that reflect the version instance of this BDBStore
    private MessageMetaDataTupleBindingFactory _metaDataTupleBindingFactory;
    private QueueTupleBindingFactory _queueTupleBindingFactory;
    private BindingTupleBindingFactory _bindingTupleBindingFactory;

    /** The data version this store should run with */
    private int _version;
    private enum State
    {
        INITIAL,
        CONFIGURING,
        CONFIGURED,
        RECOVERING,
        STARTED,
        CLOSING,
        CLOSED
    }

    private State _state = State.INITIAL;

    private TransactionConfig _transactionConfig = new TransactionConfig();

    private boolean _readOnly = false;

    private boolean _configured;

    
    public BDBMessageStore()
    {
        this(DATABASE_FORMAT_VERSION);
    }

    public BDBMessageStore(int version)
    {
        _version = version;
    }

    private void setDatabaseNames(int version)
    {
        if (version > 1)
        {
            MESSAGEMETADATADB_NAME += "_v" + version;

            MESSAGECONTENTDB_NAME += "_v" + version;

            QUEUEDB_NAME += "_v" + version;

            DELIVERYDB_NAME += "_v" + version;

            EXCHANGEDB_NAME += "_v" + version;

            QUEUEBINDINGSDB_NAME += "_v" + version;

            LINKDB_NAME += "_v" + version;

            BRIDGEDB_NAME += "_v" + version;
        }
    }
 
    public void configureConfigStore(String name, 
                                     ConfigurationRecoveryHandler recoveryHandler, 
                                     Configuration storeConfiguration,
                                     LogSubject logSubject) throws Exception
    {
        CurrentActor.get().message(logSubject, ConfigStoreMessages.CREATED(this.getClass().getName()));

        if(!_configured)
        {
            _logSubject = logSubject;
            configure(name,storeConfiguration);
            _configured = true;
            stateTransition(State.CONFIGURING, State.CONFIGURED);
        }
        
        recover(recoveryHandler);
        stateTransition(State.RECOVERING, State.STARTED);
    }

    public void configureMessageStore(String name,
                                      MessageStoreRecoveryHandler recoveryHandler,
                                      Configuration storeConfiguration,
                                      LogSubject logSubject) throws Exception
    {
        CurrentActor.get().message(logSubject, MessageStoreMessages.CREATED(this.getClass().getName()));

        if(!_configured)
        {
            _logSubject = logSubject;
            configure(name,storeConfiguration);
            _configured = true;
            stateTransition(State.CONFIGURING, State.CONFIGURED);
        }
        
        recoverMessages(recoveryHandler);
    }

    public void configureTransactionLog(String name, TransactionLogRecoveryHandler recoveryHandler,
            Configuration storeConfiguration, LogSubject logSubject) throws Exception
    {
        CurrentActor.get().message(logSubject, TransactionLogMessages.CREATED(this.getClass().getName()));


        if(!_configured)
        {
            _logSubject = logSubject;
            configure(name,storeConfiguration);
            _configured = true;
            stateTransition(State.CONFIGURING, State.CONFIGURED);
        }

        recoverQueueEntries(recoveryHandler);

        
    }

    public org.apache.qpid.server.store.MessageStore.Transaction newTransaction()
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
    public boolean configure(String name, Configuration storeConfig) throws Exception
    {
        File environmentPath = new File(storeConfig.getString(ENVIRONMENT_PATH_PROPERTY, 
                                System.getProperty("QPID_WORK") + "/bdbstore/" + name));        
        if (!environmentPath.exists())
        {
            if (!environmentPath.mkdirs())
            {
                throw new IllegalArgumentException("Environment path " + environmentPath + " could not be read or created. "
                                                   + "Ensure the path is correct and that the permissions are correct.");
            }
        }

        CurrentActor.get().message(_logSubject, MessageStoreMessages.STORE_LOCATION(environmentPath.getAbsolutePath()));

        _version = storeConfig.getInt(DATABASE_FORMAT_VERSION_PROPERTY, DATABASE_FORMAT_VERSION);

        return configure(environmentPath, false);
    }

    /**
     * @param environmentPath location for the store to be created in/recovered from
     * @param readonly if true then don't allow modifications to an existing store, and don't create a new store if none exists 
     * @return whether or not a new store environment was created
     * @throws AMQStoreException
     * @throws DatabaseException
     */
    protected boolean configure(File environmentPath, boolean readonly) throws AMQStoreException, DatabaseException
    {
        _readOnly = readonly;
        stateTransition(State.INITIAL, State.CONFIGURING);

        _log.info("Configuring BDB message store");

        createTupleBindingFactories(_version);
        
        setDatabaseNames(_version);

        return setupStore(environmentPath, readonly);
    }
    
    private void createTupleBindingFactories(int version)
    {
            _bindingTupleBindingFactory = new BindingTupleBindingFactory(version);
            _queueTupleBindingFactory = new QueueTupleBindingFactory(version);
            _metaDataTupleBindingFactory = new MessageMetaDataTupleBindingFactory(version);
    }

    /**
     * Move the store state from CONFIGURING to STARTED.
     *
     * This is required if you do not want to perform recovery of the store data
     *
     * @throws AMQStoreException if the store is not in the correct state
     */
    public void start() throws AMQStoreException
    {
        stateTransition(State.CONFIGURING, State.STARTED);
    }

    private boolean setupStore(File storePath, boolean readonly) throws DatabaseException, AMQStoreException
    {
        checkState(State.CONFIGURING);

        boolean newEnvironment = createEnvironment(storePath, readonly);

        verifyVersionByTables();

        openDatabases(readonly);

        if (!readonly)
        {
            _commitThread.start();
        }

        return newEnvironment;
    }

    private void verifyVersionByTables() throws DatabaseException
    {
        for (String s : _environment.getDatabaseNames())
        {
            int versionIndex = s.indexOf("_v");

            // lack of _v index suggests DB is v1
            // so if _version is not v1 then error
            if (versionIndex == -1)
            {
                if (_version != 1)
                {
                    closeEnvironment();
                    throw new IllegalArgumentException("Error: Unable to load BDBStore as version " + _version
                                            + ". Store on disk contains version 1 data.");
                }
                else // DB is v1 and _version is v1
                {
                    continue;
                }
            }

            // Otherwise Check Versions
            int version = Integer.parseInt(s.substring(versionIndex + 2));

            if (version != _version)
            {
                closeEnvironment();
                throw new IllegalArgumentException("Error: Unable to load BDBStore as version " + _version
                                            + ". Store on disk contains version " + version + " data.");
            }
        }
    }

    private synchronized void stateTransition(State requiredState, State newState) throws AMQStoreException
    {
        if (_state != requiredState)
        {
            throw new AMQStoreException("Cannot transition to the state: " + newState + "; need to be in state: " + requiredState
                                   + "; currently in state: " + _state);
        }

        _state = newState;
    }

    private void checkState(State requiredState) throws AMQStoreException
    {
        if (_state != requiredState)
        {
            throw new AMQStoreException("Unexpected state: " + _state + "; required state: " + requiredState);
        }
    }

    private boolean createEnvironment(File environmentPath, boolean readonly) throws DatabaseException
    {
        _log.info("BDB message store using environment path " + environmentPath.getAbsolutePath());
        EnvironmentConfig envConfig = new EnvironmentConfig();
        // This is what allows the creation of the store if it does not already exist.
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        envConfig.setConfigParam("je.lock.nLockTables", "7");

        // Restore 500,000 default timeout.	
        //envConfig.setLockTimeout(15000);

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
            _environment = new Environment(environmentPath, envConfig);
            return false;
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
                _environment = new Environment(environmentPath, envConfig);

                return true;
            }
            else
            {
                throw de;
            }
        }
    }

    private void openDatabases(boolean readonly) throws DatabaseException
    {
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);

        //This is required if we are wanting read only access.
        dbConfig.setReadOnly(readonly);

        _messageMetaDataDb = _environment.openDatabase(null, MESSAGEMETADATADB_NAME, dbConfig);
        _queueDb = _environment.openDatabase(null, QUEUEDB_NAME, dbConfig);
        _exchangeDb = _environment.openDatabase(null, EXCHANGEDB_NAME, dbConfig);
        _queueBindingsDb = _environment.openDatabase(null, QUEUEBINDINGSDB_NAME, dbConfig);
        _messageContentDb = _environment.openDatabase(null, MESSAGECONTENTDB_NAME, dbConfig);
        _deliveryDb = _environment.openDatabase(null, DELIVERYDB_NAME, dbConfig);
        _linkDb = _environment.openDatabase(null, LINKDB_NAME, dbConfig);
        _bridgeDb = _environment.openDatabase(null, BRIDGEDB_NAME, dbConfig);


    }

    /**
     * Called to close and cleanup any resources used by the message store.
     *
     * @throws Exception If the close fails.
     */
    public void close() throws Exception
    {
        if (_state != State.STARTED)
        {
            return;
        }

        _state = State.CLOSING;

        _commitThread.close();
        _commitThread.join();

        if (_messageMetaDataDb != null)
        {
            _log.info("Closing message metadata database");
            _messageMetaDataDb.close();
        }

        if (_messageContentDb != null)
        {
            _log.info("Closing message content database");
            _messageContentDb.close();
        }

        if (_exchangeDb != null)
        {
            _log.info("Closing exchange database");
            _exchangeDb.close();
        }

        if (_queueBindingsDb != null)
        {
            _log.info("Closing bindings database");
            _queueBindingsDb.close();
        }

        if (_queueDb != null)
        {
            _log.info("Closing queue database");
            _queueDb.close();
        }

        if (_deliveryDb != null)
        {
            _log.info("Close delivery database");
            _deliveryDb.close();
        }

        if (_bridgeDb != null)
        {
            _log.info("Close bridge database");
            _bridgeDb.close();
        }

        if (_linkDb != null)
        {
            _log.info("Close link database");
            _linkDb.close();
        }

        closeEnvironment();

        _state = State.CLOSED;
        
        CurrentActor.get().message(_logSubject,MessageStoreMessages.CLOSED());
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

    
    public void recover(ConfigurationRecoveryHandler recoveryHandler) throws AMQStoreException
    {
        stateTransition(State.CONFIGURED, State.RECOVERING);

        CurrentActor.get().message(_logSubject,MessageStoreMessages.RECOVERY_START());

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
            TupleBinding binding = _queueTupleBindingFactory.getInstance();
            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                QueueRecord queueRecord = (QueueRecord) binding.entryToObject(value);
                
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
            if (cursor != null)
            {
                cursor.close();
            }
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
            TupleBinding binding = new ExchangeTB();
            
            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                ExchangeRecord exchangeRec = (ExchangeRecord) binding.entryToObject(value);

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
            if (cursor != null)
            {
                cursor.close();
            }
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
            TupleBinding binding = _bindingTupleBindingFactory.getInstance();
            
            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                //yes, this is retrieving all the useful information from the key only.
                //For table compatibility it shall currently be left as is
                BindingKey bindingRecord = (BindingKey) binding.entryToObject(key);
                
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
            if (cursor != null)
            {
                cursor.close();
            }
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
            if (cursor != null)
            {
                cursor.close();
            }
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
            if (cursor != null)
            {
                cursor.close();
            }
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
            EntryBinding valueBinding = _metaDataTupleBindingFactory.getInstance();

            long maxId = 0;

            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                long messageId = LongBinding.entryToLong(key);
                StorableMessageMetaData metaData = (StorableMessageMetaData) valueBinding.entryToObject(value);

                StoredBDBMessage message = new StoredBDBMessage(messageId, metaData, false);
                mrh.message(message);
                
                maxId = Math.max(maxId, messageId);
            }

            _messageId.set(maxId);
        }
        catch (DatabaseException e)
        {
            _log.error("Database Error: " + e.getMessage(), e);
            throw e;
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
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
            EntryBinding keyBinding = new QueueEntryTB();

            DatabaseEntry value = new DatabaseEntry();

            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                QueueEntryKey qek = (QueueEntryKey) keyBinding.entryToObject(key);

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
            _log.error("Database Error: " + e.getMessage(), e);
            throw e;
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }

        qerh.completeQueueEntryRecovery();
    }

    /**
     * Removes the specified message from the store.
     *
     * @param messageId Identifies the message to remove.
     *
     * @throws AMQStoreException If the operation fails for any reason.
     */
    public void removeMessage(long messageId) throws AMQStoreException
    {
        removeMessage(messageId, true);
    }
    public void removeMessage(long messageId, boolean sync) throws AMQStoreException
    {

        // _log.debug("public void removeMessage(Long messageId = " + messageId): called");

        com.sleepycat.je.Transaction tx = null;
        
        Cursor cursor = null;
        try
        {
            tx = _environment.beginTransaction(null, null);
            
            //remove the message meta data from the store
            DatabaseEntry key = new DatabaseEntry();
            LongBinding.longToEntry(messageId, key);

            if (_log.isDebugEnabled())
            {
                _log.debug("Removing message id " + messageId);
            }

            
            OperationStatus status = _messageMetaDataDb.delete(tx, key);
            if (status == OperationStatus.NOTFOUND)
            {
                _log.info("Message not found (attempt to remove failed - probably application initiated rollback) " +
                messageId);
            }

            if (_log.isDebugEnabled())
            {
                _log.debug("Deleted metadata for message " + messageId);
            }

            //now remove the content data from the store if there is any.

            DatabaseEntry contentKeyEntry = new DatabaseEntry();
            MessageContentKey_5 mck = new MessageContentKey_5(messageId,0);

            TupleBinding<MessageContentKey> contentKeyTupleBinding = new MessageContentKeyTB_5();
            contentKeyTupleBinding.objectToEntry(mck, contentKeyEntry);

            //Use a partial record for the value to prevent retrieving the 
            //data itself as we only need the key to identify what to remove.
            DatabaseEntry value = new DatabaseEntry();
            value.setPartial(0, 0, true);

            cursor = _messageContentDb.openCursor(tx, null);

            status = cursor.getSearchKeyRange(contentKeyEntry, value, LockMode.RMW);
            while (status == OperationStatus.SUCCESS)
            {
                mck = (MessageContentKey_5) contentKeyTupleBinding.entryToObject(contentKeyEntry);
                
                if(mck.getMessageId() != messageId)
                {
                    //we have exhausted all chunks for this message id, break
                    break;
                }
                else
                {
                    status = cursor.delete();
                    
                    if(status == OperationStatus.NOTFOUND)
                    {
                        cursor.close();
                        cursor = null;
                        
                        tx.abort();
                        throw new AMQStoreException("Content chunk offset" + mck.getOffset() + " not found for message " + messageId);
                    }
                    
                    if (_log.isDebugEnabled())
                    {
                        _log.debug("Deleted content chunk offset " + mck.getOffset() + " for message " + messageId);
                    }
                }
                
                status = cursor.getNext(contentKeyEntry, value, LockMode.RMW);
            }

            cursor.close();
            cursor = null;
            
            commit(tx, sync);
        }
        catch (DatabaseException e)
        {
            e.printStackTrace();
            
            if (tx != null)
            {
                try
                {
                    if(cursor != null)
                    {
                        cursor.close();
                        cursor = null;
                    }
                    
                    tx.abort();
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
            if(cursor != null)
            {
                try
                {
                    cursor.close();
                }
                catch (DatabaseException e)
                {
                    throw new AMQStoreException("Error closing database connection: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * @see DurableConfigurationStore#createExchange(Exchange)
     */
    public void createExchange(Exchange exchange) throws AMQStoreException
    {
        if (_state != State.RECOVERING)
        {
            ExchangeRecord exchangeRec = new ExchangeRecord(exchange.getNameShortString(), 
                                             exchange.getTypeShortString(), exchange.isAutoDelete());

            DatabaseEntry key = new DatabaseEntry();
            EntryBinding keyBinding = new AMQShortStringTB();
            keyBinding.objectToEntry(exchange.getNameShortString(), key);

            DatabaseEntry value = new DatabaseEntry();
            TupleBinding exchangeBinding = new ExchangeTB();
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
        EntryBinding keyBinding = new AMQShortStringTB();
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
        // _log.debug("public void bindQueue(Exchange exchange = " + exchange + ", AMQShortString routingKey = " + routingKey
        // + ", AMQQueue queue = " + queue + ", FieldTable args = " + args + "): called");

        if (_state != State.RECOVERING)
        {
            BindingKey bindingRecord = new BindingKey(exchange.getNameShortString(), 
                                                queue.getNameShortString(), routingKey, args);

            DatabaseEntry key = new DatabaseEntry();
            EntryBinding keyBinding = _bindingTupleBindingFactory.getInstance();
            
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
                throw new AMQStoreException("Error writing binding for AMQQueue with name " + queue.getName() + " to exchange "
                                       + exchange.getName() + " to database: " + e.getMessage(), e);
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
        EntryBinding keyBinding = _bindingTupleBindingFactory.getInstance();
        keyBinding.objectToEntry(new BindingKey(exchange.getNameShortString(), queue.getNameShortString(), routingKey, args), key);

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
        if (_log.isDebugEnabled())
        {
            _log.debug("public void createQueue(AMQQueue queue(" + queue.getName() + ") = " + queue + "): called");
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
        if (_state != State.RECOVERING)
        {
            DatabaseEntry key = new DatabaseEntry();
            EntryBinding keyBinding = new AMQShortStringTB();
            keyBinding.objectToEntry(queueRecord.getNameShortString(), key);

            DatabaseEntry value = new DatabaseEntry();
            TupleBinding queueBinding = _queueTupleBindingFactory.getInstance();

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
        if (_log.isDebugEnabled())
        {
            _log.debug("Updating queue: " + queue.getName());
        }

        try
        {
            DatabaseEntry key = new DatabaseEntry();
            EntryBinding keyBinding = new AMQShortStringTB();
            keyBinding.objectToEntry(queue.getNameShortString(), key);

            DatabaseEntry value = new DatabaseEntry();
            DatabaseEntry newValue = new DatabaseEntry();
            TupleBinding queueBinding = _queueTupleBindingFactory.getInstance();

            OperationStatus status = _queueDb.get(null, key, value, LockMode.DEFAULT);
            if(status == OperationStatus.SUCCESS)
            {
                //read the existing record and apply the new exclusivity setting 
                QueueRecord queueRecord = (QueueRecord) queueBinding.entryToObject(value);
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

        if (_log.isDebugEnabled())
        {
            _log.debug("public void removeQueue(AMQShortString name = " + name + "): called");
        }
            
        DatabaseEntry key = new DatabaseEntry();
        EntryBinding keyBinding = new AMQShortStringTB();
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
        if (_state != State.RECOVERING)
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
        if (_state != State.RECOVERING)
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
        // _log.debug("public void enqueueMessage(Transaction tx = " + tx + ", AMQShortString name = " + name + ", Long messageId): called");

        AMQShortString name = AMQShortString.valueOf(queue.getResourceName());
        
        DatabaseEntry key = new DatabaseEntry();
        EntryBinding keyBinding = new QueueEntryTB();
        QueueEntryKey dd = new QueueEntryKey(name, messageId);
        keyBinding.objectToEntry(dd, key);
        DatabaseEntry value = new DatabaseEntry();
        ByteBinding.byteToEntry((byte) 0, value);

        try
        {
            if (_log.isDebugEnabled())
            {
                _log.debug("Enqueuing message " + messageId + " on queue " + name + " [Transaction" + tx + "]");
            }
            _deliveryDb.put(tx, key, value);
        }
        catch (DatabaseException e)
        {
            _log.error("Failed to enqueue: " + e.getMessage(), e);
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
        EntryBinding keyBinding = new QueueEntryTB();
        QueueEntryKey dd = new QueueEntryKey(name, messageId);

        keyBinding.objectToEntry(dd, key);

        if (_log.isDebugEnabled())
        {
            _log.debug("Dequeue message id " + messageId);
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

            if (_log.isDebugEnabled())
            {
                _log.debug("Removed message " + messageId + ", " + name + " from delivery db");

            }
        }
        catch (DatabaseException e)
        {

            _log.error("Failed to dequeue message " + messageId + ": " + e.getMessage(), e);
            _log.error(tx);

            throw new AMQStoreException("Error accessing database while dequeuing message: " + e.getMessage(), e);
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
        //if (_log.isDebugEnabled())
        //{
        //    _log.debug("public void commitTranImpl() called with (Transaction=" + tx + ", syncCommit= "+ syncCommit + ")");
        //}
        
        if (tx == null)
        {
            throw new AMQStoreException("Fatal internal error: transactional is null at commitTran");
        }
        
        StoreFuture result;
        try
        {
            result = commit(tx, syncCommit);

            if (_log.isDebugEnabled())
            {
                _log.debug("commitTranImpl completed for [Transaction:" + tx + "]");
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
        if (_log.isDebugEnabled())
        {
            _log.debug("abortTran called for [Transaction:" + tx + "]");
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

            EntryBinding keyBinding = new QueueEntryTB();
            keyBinding.objectToEntry(dd, key);

            DatabaseEntry value = new DatabaseEntry();

            LinkedList<Long> messageIds = new LinkedList<Long>();

            OperationStatus status = cursor.getSearchKeyRange(key, value, LockMode.DEFAULT);
            dd = (QueueEntryKey) keyBinding.entryToObject(key);

            while ((status == OperationStatus.SUCCESS) && dd.getQueueName().equals(queueName))
            {

                messageIds.add(dd.getMessageId());
                status = cursor.getNext(key, value, LockMode.DEFAULT);
                if (status == OperationStatus.SUCCESS)
                {
                    dd = (QueueEntryKey) keyBinding.entryToObject(key);
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
        TupleBinding<MessageContentKey> keyBinding = new MessageContentKeyTB_5();
        keyBinding.objectToEntry(new MessageContentKey_5(messageId, offset), key);
        DatabaseEntry value = new DatabaseEntry();
        TupleBinding<ByteBuffer> messageBinding = new ContentTB();
        messageBinding.objectToEntry(contentBody, value);
        try
        {
            OperationStatus status = _messageContentDb.put(tx, key, value);
            if (status != OperationStatus.SUCCESS)
            {
                throw new AMQStoreException("Error adding content chunk offset" + offset + " for message id " + messageId + ": "
                                       + status);
            }

            if (_log.isDebugEnabled())
            {
                _log.debug("Storing content chunk offset" + offset + " for message " + messageId + "[Transaction" + tx + "]");
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
        if (_log.isDebugEnabled())
        {
            _log.debug("public void storeMetaData(Txn tx = " + tx + ", Long messageId = "
                       + messageId + ", MessageMetaData messageMetaData = " + messageMetaData + "): called");
        }

        DatabaseEntry key = new DatabaseEntry();
        LongBinding.longToEntry(messageId, key);
        DatabaseEntry value = new DatabaseEntry();
        
        TupleBinding messageBinding = _metaDataTupleBindingFactory.getInstance();
        messageBinding.objectToEntry(messageMetaData, value);
        try
        {
            _messageMetaDataDb.put(tx, key, value);
            if (_log.isDebugEnabled())
            {
                _log.debug("Storing message metadata for message id " + messageId + "[Transaction" + tx + "]");
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
        if (_log.isDebugEnabled())
        {
            _log.debug("public MessageMetaData getMessageMetaData(Long messageId = "
                       + messageId + "): called");
        }

        DatabaseEntry key = new DatabaseEntry();
        LongBinding.longToEntry(messageId, key);
        DatabaseEntry value = new DatabaseEntry();
        TupleBinding messageBinding = _metaDataTupleBindingFactory.getInstance();

        try
        {
            OperationStatus status = _messageMetaDataDb.get(null, key, value, LockMode.READ_UNCOMMITTED);
            if (status != OperationStatus.SUCCESS)
            {
                throw new AMQStoreException("Metadata not found for message with id " + messageId);
            }

            StorableMessageMetaData mdd = (StorableMessageMetaData) messageBinding.entryToObject(value);

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
        
        //Start from 0 offset and search for the starting chunk. 
        MessageContentKey_5 mck = new MessageContentKey_5(messageId, 0);
        TupleBinding<MessageContentKey> contentKeyTupleBinding = new MessageContentKeyTB_5();
        contentKeyTupleBinding.objectToEntry(mck, contentKeyEntry);
        DatabaseEntry value = new DatabaseEntry();
        TupleBinding<ByteBuffer> contentTupleBinding = new ContentTB();
        
        if (_log.isDebugEnabled())
        {
            _log.debug("Message Id: " + messageId + " Getting content body from offset: " + offset);
        }

        int written = 0;
        int seenSoFar = 0;
        
        Cursor cursor = null;
        try
        {
            cursor = _messageContentDb.openCursor(null, null);
            
            OperationStatus status = cursor.getSearchKeyRange(contentKeyEntry, value, LockMode.READ_UNCOMMITTED);

            while (status == OperationStatus.SUCCESS)
            {
                mck = (MessageContentKey_5) contentKeyTupleBinding.entryToObject(contentKeyEntry);
                long id = mck.getMessageId();
                
                if(id != messageId)
                {
                    //we have exhausted all chunks for this message id, break
                    break;
                }
                
                int offsetInMessage = mck.getOffset();
                ByteBuffer buf = (ByteBuffer) contentTupleBinding.entryToObject(value);
                
                final int size = (int) buf.limit();
                
                seenSoFar += size;
                
                if(seenSoFar >= offset)
                {
                    byte[] dataAsBytes = buf.array();

                    int posInArray = offset + written - offsetInMessage;
                    int count = size - posInArray;
                    if(count > dst.remaining())
                    {
                        count = dst.remaining();
                    }
                    dst.put(dataAsBytes,posInArray,count);
                    written+=count;

                    if(dst.remaining() == 0)
                    {
                        break;
                    }
                }
                
                status = cursor.getNext(contentKeyEntry, value, LockMode.RMW);
            }

            return written;
        }
        catch (DatabaseException e)
        {
            throw new AMQStoreException("Error writing AMQMessage with id " + messageId + " to database: " + e.getMessage(), e);
        }
        finally
        {
            if(cursor != null)
            {
                try
                {
                    cursor.close();
                }
                catch (DatabaseException e)
                {
		            throw new AMQStoreException("Error writing AMQMessage with id " + messageId + " to database: " + e.getMessage(), e);
                }
            }
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
            return new StoredBDBMessage(getNewMessageId(), metaData);
        }
        else
        {
            return new StoredMemoryMessage(getNewMessageId(), metaData);
        }
    }


    //protected getters for the TupleBindingFactories

    protected QueueTupleBindingFactory getQueueTupleBindingFactory()
    {
        return _queueTupleBindingFactory;
    }

    protected BindingTupleBindingFactory getBindingTupleBindingFactory()
    {
        return _bindingTupleBindingFactory;
    }
    
    protected MessageMetaDataTupleBindingFactory getMetaDataTupleBindingFactory()
    {
        return _metaDataTupleBindingFactory;
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

    void visitMetaDataDb(DatabaseVisitor visitor) throws DatabaseException, AMQStoreException
    {
        visitDatabase(_messageMetaDataDb, visitor);
    }

    void visitContentDb(DatabaseVisitor visitor) throws DatabaseException, AMQStoreException
    {
        visitDatabase(_messageContentDb, visitor);
    }

    void visitQueues(DatabaseVisitor visitor) throws DatabaseException, AMQStoreException
    {
        visitDatabase(_queueDb, visitor);
    }

    void visitDelivery(DatabaseVisitor visitor) throws DatabaseException, AMQStoreException
    {
        visitDatabase(_deliveryDb, visitor);
    }

    void visitExchanges(DatabaseVisitor visitor) throws DatabaseException, AMQStoreException
    {
        visitDatabase(_exchangeDb, visitor);
    }

    void visitBindings(DatabaseVisitor visitor) throws DatabaseException, AMQStoreException
    {
        visitDatabase(_queueBindingsDb, visitor);
    }

    /**
     * Generic visitDatabase allows iteration through the specified database.
     *
     * @param database The database to visit
     * @param visitor  The visitor to give each entry to.
     *
     * @throws DatabaseException If there is a problem with the Database structure
     * @throws AMQStoreException If there is a problem with the Database contents
     */
    void visitDatabase(Database database, DatabaseVisitor visitor) throws DatabaseException, AMQStoreException
    {
        Cursor cursor = database.openCursor(null, null);

        try
        {
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            while (cursor.getNext(key, value, LockMode.RMW) == OperationStatus.SUCCESS)
            {
                visitor.visit(key, value);
            }
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
    }

    private StoreFuture commit(com.sleepycat.je.Transaction tx, boolean syncCommit) throws DatabaseException
    {
        // _log.debug("void commit(Transaction tx = " + tx + ", sync = " + syncCommit + "): called");

        tx.commitNoSync();

        BDBCommitFuture commitFuture = new BDBCommitFuture(_commitThread, tx, syncCommit);
        commitFuture.commit();
        
        return commitFuture;
    }

    public void startCommitThread()
    {
        _commitThread.start();
    }

    private static final class BDBCommitFuture implements StoreFuture
    {
        // private static final Logger _log = Logger.getLogger(BDBCommitFuture.class);

        private final CommitThread _commitThread;
        private final com.sleepycat.je.Transaction _tx;
        private DatabaseException _databaseException;
        private boolean _complete;
        private boolean _syncCommit;

        public BDBCommitFuture(CommitThread commitThread, com.sleepycat.je.Transaction tx, boolean syncCommit)
        {
            // _log.debug("public Commit(CommitThread commitThread = " + commitThread + ", Transaction tx = " + tx
            // + "): called");

            _commitThread = commitThread;
            _tx = tx;
            _syncCommit = syncCommit;
        }

        public synchronized void complete()
        {
            if (_log.isDebugEnabled())
            {
                _log.debug("public synchronized void complete(): called (Transaction = " + _tx + ")");
            }
            _complete = true;

            notifyAll();
        }

        public synchronized void abort(DatabaseException databaseException)
        {
            // _log.debug("public synchronized void abort(DatabaseException databaseException = " + databaseException
            // + "): called");

            _complete = true;
            _databaseException = databaseException;

            notifyAll();
        }

        public void commit() throws DatabaseException
        {
            //_log.debug("public void commit(): called");

            _commitThread.addJob(this, _syncCommit);
            
            if(!_syncCommit)
            {
                _log.debug("CommitAsync was requested, returning immediately.");
                return;
            }
            
            waitForCompletion();
            // _log.debug("Commit completed, _databaseException = " + _databaseException);

            if (_databaseException != null)
            {
                throw _databaseException;
            }

        }

        public synchronized boolean isComplete()
        {
            return _complete;
        }

        public synchronized void waitForCompletion()
        {
            while (!isComplete())
            {
                _commitThread.explicitNotify();
                try
                {
                    wait(250);
                }
                catch (InterruptedException e)
                {
                    //TODO Should we ignore, or throw a 'StoreException'?
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Implements a thread which batches and commits a queue of {@link BDBCommitFuture} operations. The commit operations
     * themselves are responsible for adding themselves to the queue and waiting for the commit to happen before
     * continuing, but it is the responsibility of this thread to tell the commit operations when they have been
     * completed by calling back on their {@link BDBCommitFuture#complete()} and {@link BDBCommitFuture#abort} methods.
     *
     * <p/><table id="crc"><caption>CRC Card</caption> <tr><th> Responsibilities <th> Collarations </table>
     */
    private class CommitThread extends Thread
    {
        // private final Logger _log = Logger.getLogger(CommitThread.class);

        private final AtomicBoolean _stopped = new AtomicBoolean(false);
        private final Queue<BDBCommitFuture> _jobQueue = new ConcurrentLinkedQueue<BDBCommitFuture>();
        private final CheckpointConfig _config = new CheckpointConfig();
        private final Object _lock = new Object();

        public CommitThread(String name)
        {
            super(name);
            _config.setForce(true);

        }

        public void explicitNotify()
        {
            synchronized (_lock)
            {
                _lock.notify();
            }
        }

        public void run()
        {
            while (!_stopped.get())
            {
                synchronized (_lock)
                {
                    while (!_stopped.get() && !hasJobs())
                    {
                        try
                        {
                            // RHM-7 Periodically wake up and check, just in case we 
                            // missed a notification. Don't want to lock the broker hard.
                            _lock.wait(250);
                        }
                        catch (InterruptedException e)
                        {
                            // _log.info(getName() + " interrupted. ");
                        }
                    }
                }
                processJobs();
            }
        }

        private void processJobs()
        {
            // _log.debug("private void processJobs(): called");

            int size = _jobQueue.size();

            try
            {
                //TODO - upgrade to BDB 5.0, then use: _environment.flushLog(true);
                _environment.sync();

                for(int i = 0; i < size; i++)
                {
                    BDBCommitFuture commit = _jobQueue.poll();
                    commit.complete();
                }

            }
            catch (DatabaseException e)
            {
                for(int i = 0; i < size; i++)
                {
                    BDBCommitFuture commit = _jobQueue.poll();
                    commit.abort(e);
                }
            }

        }

        private boolean hasJobs()
        {
            return !_jobQueue.isEmpty();
        }

        public void addJob(BDBCommitFuture commit, final boolean sync)
        {

            _jobQueue.add(commit);
            if(sync)
            {
                synchronized (_lock)
                {
                    _lock.notifyAll();
                }
            }
        }

        public void close()
        {
            synchronized (_lock)
            {
                _stopped.set(true);
                _lock.notifyAll();
            }
        }
    }
    
    
    private class StoredBDBMessage implements StoredMessage
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
                    BDBMessageStore.this.storeMetaData(txn, _messageId, _metaData);
                    BDBMessageStore.this.addContent(txn, _messageId, 0,
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
                    e.printStackTrace();
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
                BDBMessageStore.this.commit(txn,true);

            }
            return IMMEDIATE_FUTURE;
        }

        public void remove()
        {
            try
            {
                BDBMessageStore.this.removeMessage(_messageId, false);
            }
            catch (AMQStoreException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    private class BDBTransaction implements Transaction
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

            BDBMessageStore.this.enqueueMessage(_txn, queue, message.getMessageNumber());
        }

        public void dequeueMessage(TransactionLogResource queue, EnqueableMessage message) throws AMQStoreException
        {
            BDBMessageStore.this.dequeueMessage(_txn, queue, message.getMessageNumber());
        }

        public void enqueueMessage(TransactionLogResource queue, long messageId) throws AMQStoreException
        {
            BDBMessageStore.this.enqueueMessage(_txn, queue, messageId);
        }

        public void dequeueMessage(TransactionLogResource queue, long messageId) throws AMQStoreException
        {
            BDBMessageStore.this.dequeueMessage(_txn, queue, messageId);

        }

        public void commitTran() throws AMQStoreException
        {
            BDBMessageStore.this.commitTranImpl(_txn, true);
        }

        public StoreFuture commitTranAsync() throws AMQStoreException
        {
            return BDBMessageStore.this.commitTranImpl(_txn, false);
        }

        public void abortTran() throws AMQStoreException
        {
            BDBMessageStore.this.abortTran(_txn);
        }
    }

}
