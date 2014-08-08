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

import static org.apache.qpid.server.store.berkeleydb.BDBUtils.DEFAULT_DATABASE_CONFIG;
import static org.apache.qpid.server.store.berkeleydb.BDBUtils.abortTransactionSafely;
import static org.apache.qpid.server.store.berkeleydb.BDBUtils.closeCursorSafely;

import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.sleepycat.bind.tuple.ByteBinding;
import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
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
import org.apache.qpid.server.store.Event;
import org.apache.qpid.server.store.EventListener;
import org.apache.qpid.server.store.EventManager;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.store.Xid;
import org.apache.qpid.server.store.berkeleydb.entry.PreparedTransaction;
import org.apache.qpid.server.store.berkeleydb.entry.QueueEntryKey;
import org.apache.qpid.server.store.berkeleydb.tuple.ContentBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.MessageMetaDataBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.PreparedTransactionBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.QueueEntryBinding;
import org.apache.qpid.server.store.berkeleydb.tuple.XidBinding;
import org.apache.qpid.server.store.berkeleydb.upgrade.Upgrader;
import org.apache.qpid.server.store.handler.DistributedTransactionHandler;
import org.apache.qpid.server.store.handler.MessageHandler;
import org.apache.qpid.server.store.handler.MessageInstanceHandler;


public abstract class AbstractBDBMessageStore implements MessageStore
{

    private static final int LOCK_RETRY_ATTEMPTS = 5;

    private static final String MESSAGE_META_DATA_DB_NAME = "MESSAGE_METADATA";
    private static final String MESSAGE_META_DATA_SEQ_DB_NAME = "MESSAGE_METADATA.SEQ";
    private static final String MESSAGE_CONTENT_DB_NAME = "MESSAGE_CONTENT";
    private static final String DELIVERY_DB_NAME = "QUEUE_ENTRIES";

    //TODO: Add upgrader to remove BRIDGES and LINKS
    private static final String BRIDGEDB_NAME = "BRIDGES";
    private static final String LINKDB_NAME = "LINKS";
    private static final String XID_DB_NAME = "XIDS";

    private final EventManager _eventManager = new EventManager();

    private final DatabaseEntry MESSAGE_METADATA_SEQ_KEY = new DatabaseEntry("MESSAGE_METADATA_SEQ_KEY".getBytes(
            Charset.forName("UTF-8")));

    private final SequenceConfig MESSAGE_METADATA_SEQ_CONFIG = SequenceConfig.DEFAULT.
            setAllowCreate(true).
            setInitialValue(1).
            setWrap(true).
            setCacheSize(100000);

    private boolean _limitBusted;
    private long _totalStoreSize;

    @Override
    public void upgradeStoreStructure() throws StoreException
    {
        try
        {
            new Upgrader(getEnvironmentFacade().getEnvironment(), getParent()).upgradeIfNecessary();
        }
        catch(DatabaseException e)
        {
            throw getEnvironmentFacade().handleDatabaseException("Cannot upgrade store", e);
        }

        // TODO this relies on the fact that the VH will call upgrade just before putting the VH into service.
        _totalStoreSize = getSizeOnDisk();
    }

    @Override
    public <T extends StorableMessageMetaData> StoredMessage<T> addMessage(T metaData)
    {

        long newMessageId = getNextMessageId();

        return new StoredBDBMessage<T>(newMessageId, metaData);
    }

    public long getNextMessageId()
    {
        long newMessageId;
        try
        {
            // The implementations of sequences mean that there is only a transaction
            // after every n sequence values, where n is the MESSAGE_METADATA_SEQ_CONFIG.getCacheSize()

            Sequence mmdSeq = getEnvironmentFacade().openSequence(getMessageMetaDataSeqDb(),
                                                              MESSAGE_METADATA_SEQ_KEY,
                                                              MESSAGE_METADATA_SEQ_CONFIG);
            newMessageId = mmdSeq.get(null, 1);
        }
        catch (DatabaseException de)
        {
            throw getEnvironmentFacade().handleDatabaseException("Cannot get sequence value for new message", de);
        }
        return newMessageId;
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
    public void addEventListener(final EventListener eventListener, final Event... events)
    {
        _eventManager.addEventListener(eventListener, events);
    }

    @Override
    public void visitMessages(final MessageHandler handler) throws StoreException
    {
        checkMessageStoreOpen();
        visitMessagesInternal(handler, getEnvironmentFacade());
    }

    @Override
    public StoredMessage<?> getMessage(final long messageId)
    {
        checkMessageStoreOpen();
        return getMessageInternal(messageId, getEnvironmentFacade());
    }

    @Override
    public void visitMessageInstances(final TransactionLogResource queue, final MessageInstanceHandler handler) throws StoreException
    {
        checkMessageStoreOpen();

        Cursor cursor = null;
        List<QueueEntryKey> entries = new ArrayList<QueueEntryKey>();
        try
        {
            cursor = getDeliveryDb().openCursor(null, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();

            QueueEntryBinding keyBinding = QueueEntryBinding.getInstance();
            keyBinding.objectToEntry(new QueueEntryKey(queue.getId(),0l), key);

            if(cursor.getSearchKeyRange(key,value,LockMode.DEFAULT) == OperationStatus.SUCCESS)
            {
                QueueEntryKey entry = keyBinding.entryToObject(key);
                if(entry.getQueueId().equals(queue.getId()))
                {
                    entries.add(entry);
                }
                while (cursor.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS)
                {
                    entry = keyBinding.entryToObject(key);
                    if(entry.getQueueId().equals(queue.getId()))
                    {
                        entries.add(entry);
                    }
                    else
                    {
                        break;
                    }
                }
            }
        }
        catch (DatabaseException e)
        {
            throw getEnvironmentFacade().handleDatabaseException("Cannot visit message instances", e);
        }
        finally
        {
            closeCursorSafely(cursor, getEnvironmentFacade());
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
            throw getEnvironmentFacade().handleDatabaseException("Cannot visit message instances", e);
        }
        finally
        {
            closeCursorSafely(cursor, getEnvironmentFacade());
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
            throw getEnvironmentFacade().handleDatabaseException("Cannot recover distributed transactions", e);
        }
        finally
        {
            closeCursorSafely(cursor, getEnvironmentFacade());
        }
    }

    /**
     * Retrieves message meta-data.
     *
     * @param messageId The message to get the meta-data for.
     *
     * @return The message meta data.
     *
     * @throws org.apache.qpid.server.store.StoreException If the operation fails for any reason, or if the specified message does not exist.
     */
    StorableMessageMetaData getMessageMetaData(long messageId) throws StoreException
    {
        if (getLogger().isDebugEnabled())
        {
            getLogger().debug("public MessageMetaData getMessageMetaData(Long messageId = "
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
            throw getEnvironmentFacade().handleDatabaseException("Error reading message metadata for message with id "
                                                                 + messageId
                                                                 + ": "
                                                                 + e.getMessage(), e);
        }
    }

    void removeMessage(long messageId, boolean sync) throws StoreException
    {
        boolean complete = false;
        Transaction tx = null;

        Random rand = null;
        int attempts = 0;
        try
        {
            do
            {
                tx = null;
                try
                {
                    tx = getEnvironmentFacade().getEnvironment().beginTransaction(null, null);

                    //remove the message meta data from the store
                    DatabaseEntry key = new DatabaseEntry();
                    LongBinding.longToEntry(messageId, key);

                    if (getLogger().isDebugEnabled())
                    {
                        getLogger().debug("Removing message id " + messageId);
                    }


                    OperationStatus status = getMessageMetaDataDb().delete(tx, key);
                    if (status == OperationStatus.NOTFOUND)
                    {
                        if (getLogger().isDebugEnabled())
                        {
                            getLogger().debug("Message id " + messageId
                                + " not found (attempt to remove failed - probably application initiated rollback)");
                        }
                    }

                    if (getLogger().isDebugEnabled())
                    {
                        getLogger().debug("Deleted metadata for message " + messageId);
                    }

                    //now remove the content data from the store if there is any.
                    DatabaseEntry contentKeyEntry = new DatabaseEntry();
                    LongBinding.longToEntry(messageId, contentKeyEntry);
                    getMessageContentDb().delete(tx, contentKeyEntry);

                    if (getLogger().isDebugEnabled())
                    {
                        getLogger().debug("Deleted content for message " + messageId);
                    }

                    getEnvironmentFacade().commit(tx, sync);

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
                        getLogger().warn(
                                "Unable to abort transaction after LockConflictException on removal of message with id "
                                + messageId,
                                e2);
                        // rethrow the original log conflict exception, the secondary exception should already have
                        // been logged.
                        throw getEnvironmentFacade().handleDatabaseException("Cannot remove message with id "
                                                                             + messageId, e);
                    }


                    getLogger().warn("Lock timeout exception. Retrying (attempt "
                                     + (attempts + 1) + " of " + LOCK_RETRY_ATTEMPTS + ") " + e);

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
                        throw getEnvironmentFacade().handleDatabaseException("Cannot remove messages", e);
                    }
                }
            }
            while(!complete);
        }
        catch (DatabaseException e)
        {
            getLogger().error("Unexpected BDB exception", e);

            try
            {
                abortTransactionSafely(tx,
                                       getEnvironmentFacade());
            }
            finally
            {
                tx = null;
            }

            throw getEnvironmentFacade().handleDatabaseException("Error removing message with id "
                                                                 + messageId
                                                                 + " from database: "
                                                                 + e.getMessage(), e);
        }
        finally
        {
            try
            {
                abortTransactionSafely(tx,
                                       getEnvironmentFacade());
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
     * @throws org.apache.qpid.server.store.StoreException If the operation fails for any reason, or if the specified message does not exist.
     */
    int getContent(long messageId, int offset, ByteBuffer dst) throws StoreException
    {
        DatabaseEntry contentKeyEntry = new DatabaseEntry();
        LongBinding.longToEntry(messageId, contentKeyEntry);
        DatabaseEntry value = new DatabaseEntry();
        ContentBinding contentTupleBinding = ContentBinding.getInstance();


        if (getLogger().isDebugEnabled())
        {
            getLogger().debug("Message Id: " + messageId + " Getting content body from offset: " + offset);
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
            throw getEnvironmentFacade().handleDatabaseException("Error getting AMQMessage with id "
                                                                 + messageId
                                                                 + " to database: "
                                                                 + e.getMessage(), e);
        }
    }

    byte[] getAllContent(long messageId) throws StoreException
    {
        DatabaseEntry contentKeyEntry = new DatabaseEntry();
        LongBinding.longToEntry(messageId, contentKeyEntry);
        DatabaseEntry value = new DatabaseEntry();
        ContentBinding contentTupleBinding = ContentBinding.getInstance();


        if (getLogger().isDebugEnabled())
        {
            getLogger().debug("Message Id: " + messageId + " Getting content body");
        }

        try
        {

            int written = 0;
            OperationStatus status = getMessageContentDb().get(null, contentKeyEntry, value, LockMode.READ_UNCOMMITTED);
            if (status == OperationStatus.SUCCESS)
            {
                return contentTupleBinding.entryToObject(value);
            }
            else
            {
                throw new StoreException("Unable to find message with id " + messageId);
            }

        }
        catch (DatabaseException e)
        {
            throw getEnvironmentFacade().handleDatabaseException("Error getting AMQMessage with id "
                                                                 + messageId
                                                                 + " to database: "
                                                                 + e.getMessage(), e);
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


    private StoredBDBMessage<?> getMessageInternal(long messageId, EnvironmentFacade environmentFacade)
    {
        try
        {
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            MessageMetaDataBinding valueBinding = MessageMetaDataBinding.getInstance();
            LongBinding.longToEntry(messageId, key);
            if(getMessageMetaDataDb().get(null, key, value, LockMode.READ_COMMITTED) == OperationStatus.SUCCESS)
            {
                StorableMessageMetaData metaData = valueBinding.entryToObject(value);
                StoredBDBMessage message = new StoredBDBMessage(messageId, metaData, true);
                return message;
            }
            else
            {
                return null;
            }

        }
        catch (DatabaseException e)
        {
            throw environmentFacade.handleDatabaseException("Cannot visit messages", e);
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
     * @throws org.apache.qpid.server.store.StoreException If the operation fails for any reason, or if the specified message does not exist.
     */
    private void addContent(final Transaction tx, long messageId, int offset,
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

            if (getLogger().isDebugEnabled())
            {
                getLogger().debug("Storing content for message " + messageId + " in transaction " + tx);

            }
        }
        catch (DatabaseException e)
        {
            throw getEnvironmentFacade().handleDatabaseException("Error writing AMQMessage with id "
                                                                 + messageId
                                                                 + " to database: "
                                                                 + e.getMessage(), e);
        }
    }

    /**
     * Stores message meta-data.
     *
     * @param tx         The transaction for the operation.
     * @param messageId       The message to store the data for.
     * @param messageMetaData The message meta data to store.
     *
     * @throws org.apache.qpid.server.store.StoreException If the operation fails for any reason, or if the specified message does not exist.
     */
    private void storeMetaData(final Transaction tx, long messageId,
                               StorableMessageMetaData messageMetaData)
            throws StoreException
    {
        if (getLogger().isDebugEnabled())
        {
            getLogger().debug("storeMetaData called for transaction " + tx
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
            if (getLogger().isDebugEnabled())
            {
                getLogger().debug("Storing message metadata for message id " + messageId + " in transaction " + tx);
            }
        }
        catch (DatabaseException e)
        {
            throw getEnvironmentFacade().handleDatabaseException("Error writing message metadata with id "
                                                                 + messageId
                                                                 + " to database: "
                                                                 + e.getMessage(), e);
        }
    }


    /**
     * Places a message onto a specified queue, in a given transaction.
     *
     * @param tx   The transaction for the operation.
     * @param queue     The the queue to place the message on.
     * @param messageId The message to enqueue.
     *
     * @throws org.apache.qpid.server.store.StoreException If the operation fails for any reason.
     */
    private void enqueueMessage(final Transaction tx, final TransactionLogResource queue,
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
            if (getLogger().isDebugEnabled())
            {
                getLogger().debug("Enqueuing message " + messageId + " on queue "
                                  + queue.getName() + " with id " + queue.getId() + " in transaction " + tx);
            }
            getDeliveryDb().put(tx, key, value);
        }
        catch (DatabaseException e)
        {
            getLogger().error("Failed to enqueue: " + e.getMessage(), e);
            throw getEnvironmentFacade().handleDatabaseException("Error writing enqueued message with id "
                                                                 + messageId
                                                                 + " for queue "
                                                                 + queue.getName()
                                                                 + " with id "
                                                                 + queue.getId()
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
     * @throws org.apache.qpid.server.store.StoreException If the operation fails for any reason, or if the specified message does not exist.
     */
    private void dequeueMessage(final Transaction tx, final TransactionLogResource queue,
                                long messageId) throws StoreException
    {

        DatabaseEntry key = new DatabaseEntry();
        QueueEntryBinding keyBinding = QueueEntryBinding.getInstance();
        QueueEntryKey queueEntryKey = new QueueEntryKey(queue.getId(), messageId);
        UUID id = queue.getId();
        keyBinding.objectToEntry(queueEntryKey, key);
        if (getLogger().isDebugEnabled())
        {
            getLogger().debug("Dequeue message id " + messageId + " from queue "
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

            if (getLogger().isDebugEnabled())
            {
                getLogger().debug("Removed message " + messageId + " on queue "
                                  + queue.getName() + " with id " + id);

            }
        }
        catch (DatabaseException e)
        {

            getLogger().error("Failed to dequeue message " + messageId + " in transaction " + tx, e);

            throw getEnvironmentFacade().handleDatabaseException("Error accessing database while dequeuing message: "
                                                                 + e.getMessage(), e);
        }
    }

    private List<Runnable> recordXid(Transaction txn,
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
        List<Runnable> postActions = new ArrayList<>();
        for(org.apache.qpid.server.store.Transaction.Record enqueue : enqueues)
        {
            StoredMessage storedMessage = enqueue.getMessage().getStoredMessage();
            if(storedMessage instanceof StoredBDBMessage)
            {
                postActions.add(((StoredBDBMessage) storedMessage).store(txn));
            }
        }

        try
        {
            getXidDb().put(txn, key, value);
            return postActions;
        }
        catch (DatabaseException e)
        {
            getLogger().error("Failed to write xid: " + e.getMessage(), e);
            throw getEnvironmentFacade().handleDatabaseException("Error writing xid to database", e);
        }
    }

    private void removeXid(Transaction txn, long format, byte[] globalId, byte[] branchId)
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

            getLogger().error("Failed to remove xid in transaction " + txn, e);

            throw getEnvironmentFacade().handleDatabaseException("Error accessing database while removing xid: "
                                                                 + e.getMessage(), e);
        }
    }

    /**
     * Commits all operations performed within a given transaction.
     *
     * @param tx The transaction to commit all operations for.
     *
     * @throws org.apache.qpid.server.store.StoreException If the operation fails for any reason.
     */
    private StoreFuture commitTranImpl(final Transaction tx, boolean syncCommit) throws StoreException
    {
        if (tx == null)
        {
            throw new StoreException("Fatal internal error: transactional is null at commitTran");
        }

        StoreFuture result = getEnvironmentFacade().commit(tx, syncCommit);

        if (getLogger().isDebugEnabled())
        {
            String transactionType = syncCommit ? "synchronous" : "asynchronous";
            getLogger().debug("commitTranImpl completed " + transactionType + " transaction " + tx);
        }

        return result;
    }

    /**
     * Abandons all operations performed within a given transaction.
     *
     * @param tx The transaction to abandon.
     *
     * @throws org.apache.qpid.server.store.StoreException If the operation fails for any reason.
     */
    private void abortTran(final Transaction tx) throws StoreException
    {
        if (getLogger().isDebugEnabled())
        {
            getLogger().debug("abortTran called for transaction " + tx);
        }

        try
        {
            tx.abort();
        }
        catch (DatabaseException e)
        {
            throw getEnvironmentFacade().handleDatabaseException("Error aborting transaction: " + e.getMessage(), e);
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
            throw getEnvironmentFacade().handleDatabaseException("Stored size change exception", e);
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
        getEnvironmentFacade().getEnvironment().getConfig().setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, "false");
        boolean cleaned = false;
        while (getEnvironmentFacade().getEnvironment().cleanLog() > 0)
        {
            cleaned = true;
        }
        if (cleaned)
        {
            CheckpointConfig force = new CheckpointConfig();
            force.setForce(true);
            getEnvironmentFacade().getEnvironment().checkpoint(force);
        }


        getEnvironmentFacade().getEnvironment().getConfig().setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, "true");
    }

    private long getSizeOnDisk()
    {
        return getEnvironmentFacade().getEnvironment().getStats(null).getTotalLogSize();
    }

    private Database getMessageContentDb()
    {
        return getEnvironmentFacade().openDatabase(MESSAGE_CONTENT_DB_NAME, DEFAULT_DATABASE_CONFIG);
    }

    private Database getMessageMetaDataDb()
    {
        return getEnvironmentFacade().openDatabase(MESSAGE_META_DATA_DB_NAME, DEFAULT_DATABASE_CONFIG);
    }

    private Database getMessageMetaDataSeqDb()
    {
        return getEnvironmentFacade().openDatabase(MESSAGE_META_DATA_SEQ_DB_NAME, DEFAULT_DATABASE_CONFIG);
    }

    private Database getDeliveryDb()
    {
        return getEnvironmentFacade().openDatabase(DELIVERY_DB_NAME, DEFAULT_DATABASE_CONFIG);
    }

    private Database getXidDb()
    {
        return getEnvironmentFacade().openDatabase(XID_DB_NAME, DEFAULT_DATABASE_CONFIG);
    }

    protected abstract void checkMessageStoreOpen();

    protected abstract ConfiguredObject<?> getParent();

    protected abstract EnvironmentFacade getEnvironmentFacade();

    protected abstract long getPersistentSizeLowThreshold();

    protected abstract long getPersistentSizeHighThreshold();

    protected abstract Logger getLogger();

    static interface MessageDataRef<T extends StorableMessageMetaData>
    {
        T getMetaData();
        byte[] getData();
        void setData(byte[] data);
        boolean isHardRef();
    }

    private static final class MessageDataHardRef<T extends StorableMessageMetaData> implements MessageDataRef<T>
    {
        private final T _metaData;
        private byte[] _data;

        private MessageDataHardRef(final T metaData)
        {
            _metaData = metaData;
        }

        @Override
        public T getMetaData()
        {
            return _metaData;
        }

        @Override
        public byte[] getData()
        {
            return _data;
        }

        @Override
        public void setData(final byte[] data)
        {
            _data = data;
        }

        @Override
        public boolean isHardRef()
        {
            return true;
        }
    }

    private static final class MessageData<T extends StorableMessageMetaData>
    {
        private T _metaData;
        private SoftReference<byte[]> _data;

        private MessageData(final T metaData, final byte[] data)
        {
            _metaData = metaData;

            if(data != null)
            {
                _data = new SoftReference<>(data);
            }
        }

        public T getMetaData()
        {
            return _metaData;
        }

        public byte[] getData()
        {
            return _data == null ? null : _data.get();
        }

        public void setData(final byte[] data)
        {
            _data = new SoftReference<>(data);
        }


    }
    private static final class MessageDataSoftRef<T extends StorableMessageMetaData> extends SoftReference<MessageData<T>> implements MessageDataRef<T>
    {

        public MessageDataSoftRef(final T metadata, byte[] data)
        {
            super(new MessageData<T>(metadata, data));
        }

        @Override
        public T getMetaData()
        {
            MessageData<T> ref = get();
            return ref == null ? null : ref.getMetaData();
        }

        @Override
        public byte[] getData()
        {
            MessageData<T> ref = get();

            return ref == null ? null : ref.getData();
        }

        @Override
        public void setData(final byte[] data)
        {
            MessageData<T> ref = get();
            if(ref != null)
            {
                ref.setData(data);
            }
        }

        @Override
        public boolean isHardRef()
        {
            return false;
        }
    }

    final class StoredBDBMessage<T extends StorableMessageMetaData> implements StoredMessage<T>
    {

        private final long _messageId;

        private volatile MessageDataRef<T> _messageDataRef;

        StoredBDBMessage(long messageId, T metaData)
        {
            this(messageId, metaData, false);
        }

        StoredBDBMessage(long messageId, T metaData, boolean isRecovered)
        {
            _messageId = messageId;

            if(!isRecovered)
            {
                _messageDataRef = new MessageDataHardRef<>(metaData);
            }
            else
            {
                _messageDataRef = new MessageDataSoftRef<>(metaData, null);
            }
        }

        @Override
        public T getMetaData()
        {
            T metaData = _messageDataRef.getMetaData();

            if(metaData == null)
            {
                checkMessageStoreOpen();
                metaData = (T) getMessageMetaData(_messageId);
                _messageDataRef = new MessageDataSoftRef<>(metaData,null);
            }
            return metaData;
        }

        @Override
        public long getMessageNumber()
        {
            return _messageId;
        }

        @Override
        public void addContent(int offsetInMessage, ByteBuffer src)
        {
            src = src.slice();
            byte[] data = _messageDataRef.getData();
            if(data == null)
            {
                data = new byte[src.remaining()];
                src.duplicate().get(data);
                _messageDataRef.setData(data);
            }
            else
            {
                byte[] oldData = data;
                data = new byte[oldData.length + src.remaining()];


                System.arraycopy(oldData, 0, data, 0, oldData.length);
                src.duplicate().get(data, oldData.length, src.remaining());

                _messageDataRef.setData(data);
            }

        }

        @Override
        public int getContent(int offsetInMessage, ByteBuffer dst)
        {
            byte[] data = _messageDataRef.getData();
            if(data == null)
            {
                if(stored())
                {
                    checkMessageStoreOpen();
                    data = AbstractBDBMessageStore.this.getAllContent(_messageId);
                    T metaData = _messageDataRef.getMetaData();
                    if (metaData == null)
                    {
                        metaData = (T) getMessageMetaData(_messageId);
                        _messageDataRef = new MessageDataSoftRef<>(metaData, data);
                    }
                    else
                    {
                        _messageDataRef.setData(data);
                    }
                }
                else
                {
                    data = new byte[0];
                }
            }

            int length = Math.min(dst.remaining(), data.length - offsetInMessage);
            dst.put(data, offsetInMessage, length);
            return length;
        }

        @Override
        public ByteBuffer getContent(int offsetInMessage, int size)
        {
            byte[] data = _messageDataRef.getData();
            if(data == null)
            {
                if(stored())
                {
                    checkMessageStoreOpen();
                    data = AbstractBDBMessageStore.this.getAllContent(_messageId);
                    T metaData = _messageDataRef.getMetaData();
                    if (metaData == null)
                    {
                        metaData = (T) getMessageMetaData(_messageId);
                        _messageDataRef = new MessageDataSoftRef<>(metaData, data);
                    }
                    else
                    {
                        _messageDataRef.setData(data);
                    }
                }
                else
                {
                    data = new byte[0];
                }
            }
            return ByteBuffer.wrap(data,offsetInMessage,size);

        }

        synchronized Runnable store(Transaction txn)
        {
            if (!stored())
            {

                AbstractBDBMessageStore.this.storeMetaData(txn, _messageId, _messageDataRef.getMetaData());
                AbstractBDBMessageStore.this.addContent(txn, _messageId, 0,
                                                        _messageDataRef.getData() == null
                                                                ? ByteBuffer.allocate(0)
                                                                : ByteBuffer.wrap(_messageDataRef.getData()));


                MessageDataRef<T> hardRef = _messageDataRef;
                MessageDataSoftRef<T> messageDataSoftRef;
                MessageData<T> ref;
                do
                {
                    messageDataSoftRef = new MessageDataSoftRef<>(hardRef.getMetaData(), hardRef.getData());
                    ref = messageDataSoftRef.get();
                }
                while (ref == null);

                _messageDataRef = messageDataSoftRef;

                class Pointer implements Runnable
                {
                    private MessageData<T> _ref;

                    Pointer(final MessageData<T> ref)
                    {
                        _ref = ref;
                    }

                    @Override
                    public void run()
                    {
                        _ref = null;
                    }
                }
                return new Pointer(ref);
            }
            else
            {
                return new Runnable()
                {

                    @Override
                    public void run()
                    {
                    }
                };
            }
        }

        synchronized StoreFuture flushToStore()
        {
            if(!stored())
            {
                checkMessageStoreOpen();

                Transaction txn;
                try
                {
                    txn = getEnvironmentFacade().getEnvironment().beginTransaction(
                            null, null);
                }
                catch (DatabaseException e)
                {
                    throw getEnvironmentFacade().handleDatabaseException("failed to begin transaction", e);
                }
                store(txn);
                getEnvironmentFacade().commit(txn, true);

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

        @Override
        public boolean isInMemory()
        {
            return _messageDataRef.isHardRef();
        }

        private boolean stored()
        {
            return !_messageDataRef.isHardRef();
        }

        @Override
        public boolean flowToDisk()
        {
            flushToStore();
            return true;
        }

        @Override
        public String toString()
        {
            return this.getClass() + "[messageId=" + _messageId + "]";
        }
    }


    private class BDBTransaction implements org.apache.qpid.server.store.Transaction
    {
        private Transaction _txn;
        private int _storeSizeIncrease;
        private final List<Runnable> _preCommitActions = new ArrayList<>();
        private final List<Runnable> _postCommitActions = new ArrayList<>();

        private BDBTransaction() throws StoreException
        {
            try
            {
                _txn = getEnvironmentFacade().getEnvironment().beginTransaction(null, null);
            }
            catch(DatabaseException e)
            {
                throw getEnvironmentFacade().handleDatabaseException("Cannot create store transaction", e);
            }
        }

        @Override
        public void enqueueMessage(TransactionLogResource queue, EnqueueableMessage message) throws StoreException
        {
            checkMessageStoreOpen();

            if(message.getStoredMessage() instanceof StoredBDBMessage)
            {
                final StoredBDBMessage storedMessage = (StoredBDBMessage) message.getStoredMessage();
                final long contentSize = storedMessage.getMetaData().getContentSize();
                _preCommitActions.add(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        _postCommitActions.add(storedMessage.store(_txn));
                        _storeSizeIncrease += contentSize;
                    }
                });

            }

            AbstractBDBMessageStore.this.enqueueMessage(_txn, queue, message.getMessageNumber());
        }

        @Override
        public void dequeueMessage(TransactionLogResource queue, EnqueueableMessage message) throws StoreException
        {
            checkMessageStoreOpen();

            AbstractBDBMessageStore.this.dequeueMessage(_txn, queue, message.getMessageNumber());
        }

        @Override
        public void commitTran() throws StoreException
        {
            checkMessageStoreOpen();
            doPreCommitActions();
            AbstractBDBMessageStore.this.commitTranImpl(_txn, true);
            doPostCommitActions();
            AbstractBDBMessageStore.this.storedSizeChangeOccurred(_storeSizeIncrease);
        }

        private void doPreCommitActions()
        {
            for(Runnable action : _preCommitActions)
            {
                action.run();
            }
            _preCommitActions.clear();
        }

        private void doPostCommitActions()
        {
            for(Runnable action : _postCommitActions)
            {
                action.run();
            }
            _postCommitActions.clear();
        }

        @Override
        public StoreFuture commitTranAsync() throws StoreException
        {
            checkMessageStoreOpen();
            doPreCommitActions();
            AbstractBDBMessageStore.this.storedSizeChangeOccurred(_storeSizeIncrease);
            StoreFuture storeFuture = AbstractBDBMessageStore.this.commitTranImpl(_txn, false);
            doPostCommitActions();
            return storeFuture;
        }

        @Override
        public void abortTran() throws StoreException
        {
            checkMessageStoreOpen();
            _preCommitActions.clear();
            _postCommitActions.clear();
            AbstractBDBMessageStore.this.abortTran(_txn);
        }

        @Override
        public void removeXid(long format, byte[] globalId, byte[] branchId) throws StoreException
        {
            checkMessageStoreOpen();

            AbstractBDBMessageStore.this.removeXid(_txn, format, globalId, branchId);
        }

        @Override
        public void recordXid(long format, byte[] globalId, byte[] branchId, Record[] enqueues,
                              Record[] dequeues) throws StoreException
        {
            checkMessageStoreOpen();

            _postCommitActions.addAll(AbstractBDBMessageStore.this.recordXid(_txn, format, globalId, branchId, enqueues, dequeues));
        }
    }

}
