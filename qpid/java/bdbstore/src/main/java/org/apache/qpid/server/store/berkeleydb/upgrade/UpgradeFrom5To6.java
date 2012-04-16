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

import static org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeInteractionResponse.ABORT;
import static org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeInteractionResponse.NO;
import static org.apache.qpid.server.store.berkeleydb.upgrade.UpgradeInteractionResponse.YES;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQStoreException;

import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

public class UpgradeFrom5To6 extends AbstractStoreUpgrade
{

    private static final Logger _logger = Logger.getLogger(UpgradeFrom5To6.class);

    private static final String OLD_CONTENT_DB_NAME = "messageContentDb_v5";
    private static final String NEW_CONTENT_DB_NAME = "MESSAGE_CONTENT";
    private static final String META_DATA_DB_NAME = "messageMetaDataDb_v5";

    private static final String NEW_DB_NAMES[] = { "EXCHANGES", "QUEUES", "QUEUE_BINDINGS", "DELIVERIES",
            "MESSAGE_METADATA", NEW_CONTENT_DB_NAME, "BRIDGES", "LINKS", "XIDS" };
    private static final String OLD_DB_NAMES[] = { "exchangeDb_v5", "queueDb_v5", "queueBindingsDb_v5", "deliveryDb_v5",
            META_DATA_DB_NAME, OLD_CONTENT_DB_NAME, "bridges_v5", "links_v5", "xids_v5" };

    public void performUpgrade(final Environment environment, final UpgradeInteractionHandler handler) throws DatabaseException, AMQStoreException
    {
        _logger.info("Starting store upgrade from version 5");
        Transaction transaction = null;
        try
        {
            reportStarting(environment, OLD_DB_NAMES, USER_FRIENDLY_NAMES);
            transaction = environment.beginTransaction(null, null);
            performUpgradeInternal(environment, handler, transaction);
            transaction.commit();
            reportFinished(environment, NEW_DB_NAMES, USER_FRIENDLY_NAMES);
        }
        catch (Exception e)
        {
            transaction.abort();
            if (e instanceof DatabaseException)
            {
                throw (DatabaseException) e;
            }
            else if (e instanceof AMQStoreException)
            {
                throw (AMQStoreException) e;
            }
            else
            {
                throw new AMQStoreException("Unexpected exception", e);
            }
        }
    }

    /**
     * Upgrades from a v5 database to a v6 database
     *
     * v6 is the first "new style" schema where we don't version every table, and the upgrade is re-runnable
     *
     * Change in this version:
     *
     * Message content is moved from the database messageContentDb_v5 to MESSAGE_CONTENT.
     * The structure of the database changes from
     *    ( message-id: long, chunk-id: int ) -> ( size: int, byte[] data )
     * to
     *    ( message-id: long) -> ( byte[] data )
     *
     * That is we keep only one record per message, which contains all the message content
     */
    public void performUpgradeInternal(final Environment environment, final UpgradeInteractionHandler handler,
            final Transaction transaction) throws AMQStoreException
    {
        _logger.info("Message Contents");
        if (environment.getDatabaseNames().contains(OLD_CONTENT_DB_NAME))
        {
            DatabaseRunnable contentOperation = new DatabaseRunnable()
            {
                @Override
                public void run(final Database oldContentDatabase, final Database newContentDatabase,
                        Transaction contentTransaction)
                {
                    CursorOperation metaDataDatabaseOperation = new CursorOperation()
                    {

                        @Override
                        public void processEntry(Database metadataDatabase, Database notUsed,
                                Transaction metaDataTransaction, DatabaseEntry key, DatabaseEntry value)
                        {
                            long messageId = LongBinding.entryToLong(key);
                            upgradeMessage(messageId, oldContentDatabase, newContentDatabase, handler, metaDataTransaction,
                                    metadataDatabase);
                        }
                    };
                    new DatabaseTemplate(environment, META_DATA_DB_NAME, contentTransaction).run(metaDataDatabaseOperation);
                    _logger.info(metaDataDatabaseOperation.getRowCount() + " Message Content Entries");
                }
            };
            new DatabaseTemplate(environment, OLD_CONTENT_DB_NAME, NEW_CONTENT_DB_NAME, transaction).run(contentOperation);
        }
        renameDatabases(environment, transaction);
    }

    private void renameDatabases(Environment environment, Transaction transaction)
    {
        List<String> databases = environment.getDatabaseNames();
        for (int i = 0; i < OLD_DB_NAMES.length; i++)
        {
            String oldName = OLD_DB_NAMES[i];
            String newName = NEW_DB_NAMES[i];
            if (databases.contains(oldName))
            {
                _logger.info("Renaming " + oldName + " into " + newName);
                environment.renameDatabase(transaction, oldName, newName);
            }
        }
    }

    /**
     * Upgrade an individual message, that is read all the data from the old
     * database, consolidate it into a single byte[] and then (in a transaction)
     * remove the record from the old database and add the corresponding record
     * to the new database
     */
    private void upgradeMessage(final long messageId, final Database oldDatabase, final Database newDatabase,
            final UpgradeInteractionHandler handler, Transaction txn, Database oldMetadataDatabase)
    {
        SortedMap<Integer, byte[]> messageData = getMessageData(messageId, oldDatabase);
        byte[] consolidatedData = new byte[0];
        for (Map.Entry<Integer, byte[]> entry : messageData.entrySet())
        {
            int offset = entry.getKey();
            if (offset != consolidatedData.length)
            {
                String message;
                if (offset < consolidatedData.length)
                {
                    message = "Missing data in message id " + messageId + " between offset " + consolidatedData.length
                            + " and " + offset + ". ";
                }
                else
                {
                    message = "Duplicate data in message id " + messageId + " between offset " + offset + " and "
                            + consolidatedData.length + ". ";
                }
                UpgradeInteractionResponse action = handler.requireResponse(message
                        + "Do you wish do recover as much of this message as "
                        + "possible (answering NO will delete the message)?", ABORT, YES, NO, ABORT);

                switch (action)
                {
                case YES:
                    byte[] oldData = consolidatedData;
                    consolidatedData = new byte[offset];
                    System.arraycopy(oldData, 0, consolidatedData, 0, Math.min(oldData.length, consolidatedData.length));
                    break;
                case NO:
                    DatabaseEntry key = new DatabaseEntry();
                    LongBinding.longToEntry(messageId, key);
                    oldMetadataDatabase.delete(txn, key);
                    return;
                case ABORT:
                    _logger.error(message);
                    throw new RuntimeException("Unable to upgrade message " + messageId);
                }

            }
            byte[] data = new byte[consolidatedData.length + entry.getValue().length];
            System.arraycopy(consolidatedData, 0, data, 0, consolidatedData.length);
            System.arraycopy(entry.getValue(), 0, data, offset, entry.getValue().length);
            consolidatedData = data;
        }

        CompoundKeyBinding binding = new CompoundKeyBinding();
        for (int offset : messageData.keySet())
        {
            DatabaseEntry key = new DatabaseEntry();
            binding.objectToEntry(new CompoundKey(messageId, offset), key);
            oldDatabase.delete(txn, key);
        }
        DatabaseEntry key = new DatabaseEntry();
        LongBinding.longToEntry(messageId, key);
        NewDataBinding dataBinding = new NewDataBinding();
        DatabaseEntry value = new DatabaseEntry();
        dataBinding.objectToEntry(consolidatedData, value);

        newDatabase.put(txn, key, value);
    }

    /**
     * @return a (sorted) map of offset -> data for the given message id
     */
    private SortedMap<Integer, byte[]> getMessageData(final long messageId, final Database oldDatabase)
    {
        TreeMap<Integer, byte[]> data = new TreeMap<Integer, byte[]>();

        Cursor cursor = oldDatabase.openCursor(null, CursorConfig.READ_COMMITTED);
        try
        {
            DatabaseEntry contentKeyEntry = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            CompoundKeyBinding binding = new CompoundKeyBinding();
            binding.objectToEntry(new CompoundKey(messageId, 0), contentKeyEntry);

            OperationStatus status = cursor.getSearchKeyRange(contentKeyEntry, value, LockMode.DEFAULT);
            OldDataBinding dataBinding = new OldDataBinding();

            while (status == OperationStatus.SUCCESS)
            {
                CompoundKey compoundKey = binding.entryToObject(contentKeyEntry);
                long id = compoundKey.getMessageId();

                if (id != messageId)
                {
                    // we have exhausted all chunks for this message id, break
                    break;
                }

                int offsetInMessage = compoundKey.getOffset();
                OldDataValue dataValue = dataBinding.entryToObject(value);
                data.put(offsetInMessage, dataValue.getData());

                status = cursor.getNext(contentKeyEntry, value, LockMode.DEFAULT);
            }
        }
        finally
        {
            cursor.close();
        }

        return data;
    }

    static final class CompoundKey
    {
        public final long _messageId;
        public final int _offset;

        public CompoundKey(final long messageId, final int offset)
        {
            _messageId = messageId;
            _offset = offset;
        }

        public long getMessageId()
        {
            return _messageId;
        }

        public int getOffset()
        {
            return _offset;
        }
    }

    static final class CompoundKeyBinding extends TupleBinding<CompoundKey>
    {

        @Override
        public CompoundKey entryToObject(final TupleInput input)
        {
            return new CompoundKey(input.readLong(), input.readInt());
        }

        @Override
        public void objectToEntry(final CompoundKey object, final TupleOutput output)
        {
            output.writeLong(object._messageId);
            output.writeInt(object._offset);
        }
    }

    static final class OldDataValue
    {
        private final int _size;
        private final byte[] _data;

        private OldDataValue(final int size, final byte[] data)
        {
            _size = size;
            _data = data;
        }

        public int getSize()
        {
            return _size;
        }

        public byte[] getData()
        {
            return _data;
        }
    }

    static final class OldDataBinding extends TupleBinding<OldDataValue>
    {

        @Override
        public OldDataValue entryToObject(final TupleInput input)
        {
            int size = input.readInt();
            byte[] data = new byte[size];
            input.read(data);
            return new OldDataValue(size, data);
        }

        @Override
        public void objectToEntry(OldDataValue value, final TupleOutput output)
        {
            output.writeInt(value.getSize());
            output.write(value.getData());
        }
    }

    static final class NewDataBinding extends TupleBinding<byte[]>
    {

        @Override
        public byte[] entryToObject(final TupleInput input)
        {
            byte[] data = new byte[input.available()];
            input.read(data);
            return data;
        }

        @Override
        public void objectToEntry(final byte[] data, final TupleOutput output)
        {
            output.write(data);
        }
    }

}