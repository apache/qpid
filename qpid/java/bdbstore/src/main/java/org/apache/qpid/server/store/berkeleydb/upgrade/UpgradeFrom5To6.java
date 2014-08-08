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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.queue.QueueArgumentsConverter;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.store.berkeleydb.AMQShortStringEncoding;
import org.apache.qpid.server.store.berkeleydb.FieldTableEncoding;
import org.apache.qpid.server.util.MapJsonSerializer;

import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

public class UpgradeFrom5To6 extends AbstractStoreUpgrade
{

    private static final Logger _logger = Logger.getLogger(UpgradeFrom5To6.class);

    static final String OLD_CONTENT_DB_NAME = "messageContentDb_v5";
    static final String NEW_CONTENT_DB_NAME = "MESSAGE_CONTENT";
    static final String NEW_METADATA_DB_NAME = "MESSAGE_METADATA";
    static final String OLD_META_DATA_DB_NAME = "messageMetaDataDb_v5";
    static final String OLD_EXCHANGE_DB_NAME = "exchangeDb_v5";
    static final String OLD_QUEUE_DB_NAME = "queueDb_v5";
    static final String OLD_DELIVERY_DB_NAME = "deliveryDb_v5";
    static final String OLD_QUEUE_BINDINGS_DB_NAME = "queueBindingsDb_v5";
    static final String OLD_XID_DB_NAME = "xids_v5";
    static final String NEW_XID_DB_NAME = "XIDS";
    static final String CONFIGURED_OBJECTS_DB_NAME = "CONFIGURED_OBJECTS";
    static final String NEW_DELIVERY_DB_NAME = "QUEUE_ENTRIES";
    static final String NEW_BRIDGES_DB_NAME = "BRIDGES";
    static final String NEW_LINKS_DB_NAME = "LINKS";
    static final String OLD_BRIDGES_DB_NAME = "bridges_v5";
    static final String OLD_LINKS_DB_NAME = "links_v5";

    private static final Set<String> DEFAULT_EXCHANGES_SET =
            new HashSet<String>(Arrays.asList(
                    ExchangeDefaults.DEFAULT_EXCHANGE_NAME,
                    ExchangeDefaults.FANOUT_EXCHANGE_NAME,
                    ExchangeDefaults.HEADERS_EXCHANGE_NAME,
                    ExchangeDefaults.TOPIC_EXCHANGE_NAME,
                    ExchangeDefaults.DIRECT_EXCHANGE_NAME));

    private static final String ARGUMENTS = "arguments";

    private MapJsonSerializer _serializer = new MapJsonSerializer();

    private static final boolean _moveNonExclusiveQueueOwnerToDescription = Boolean.parseBoolean(System.getProperty("qpid.move_non_exclusive_queue_owner_to_description", Boolean.TRUE.toString()));

    /**
     * Upgrades from a v5 database to a v6 database
     *
     * v6 is the first "new style" schema where we don't version every table,
     * and the upgrade is re-runnable
     *
     * Change in this version:
     *
     * Message content is moved from the database messageContentDb_v5 to
     * MESSAGE_CONTENT. The structure of the database changes from ( message-id:
     * long, chunk-id: int ) {@literal ->} ( size: int, byte[] data ) to ( message-id:
     * long) {@literal ->} ( byte[] data )
     *
     * That is we keep only one record per message, which contains all the
     * message content
     *
     * Queue, Exchange, Bindings entries are stored now as configurable objects
     * in "CONFIGURED_OBJECTS" table.
     */
    public void performUpgrade(final Environment environment, final UpgradeInteractionHandler handler, ConfiguredObject<?> parent)
    {
        reportStarting(environment, 5);
        upgradeMessages(environment, handler);
        upgradeConfiguredObjectsAndDependencies(environment, handler, parent.getName());
        renameDatabases(environment, null);
        reportFinished(environment, 6);
    }

    private void upgradeConfiguredObjectsAndDependencies(Environment environment, UpgradeInteractionHandler handler, String virtualHostName)
    {
        Transaction transaction = null;
        transaction = environment.beginTransaction(null, null);
        upgradeConfiguredObjects(environment, handler, transaction, virtualHostName);
        upgradeQueueEntries(environment, transaction, virtualHostName);
        upgradeXidEntries(environment, transaction, virtualHostName);
        transaction.commit();
    }

    private void upgradeMessages(final Environment environment, final UpgradeInteractionHandler handler)
    {
        Transaction transaction = null;
        transaction = environment.beginTransaction(null, null);
        upgradeMessages(environment, handler, transaction);
        transaction.commit();
    }

    private void renameDatabases(Environment environment, Transaction transaction)
    {
        List<String> databases = environment.getDatabaseNames();
        String[] oldDatabases = { OLD_META_DATA_DB_NAME, OLD_BRIDGES_DB_NAME, OLD_LINKS_DB_NAME };
        String[] newDatabases = { NEW_METADATA_DB_NAME, NEW_BRIDGES_DB_NAME, NEW_LINKS_DB_NAME };

        for (int i = 0; i < oldDatabases.length; i++)
        {
            String oldName = oldDatabases[i];
            String newName = newDatabases[i];
            if (databases.contains(oldName))
            {
                _logger.info("Renaming " + oldName + " into " + newName);
                environment.renameDatabase(transaction, oldName, newName);
            }
        }
    }

    private void upgradeMessages(final Environment environment, final UpgradeInteractionHandler handler,
            final Transaction transaction)
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
                    new DatabaseTemplate(environment, OLD_META_DATA_DB_NAME, contentTransaction)
                            .run(metaDataDatabaseOperation);
                    _logger.info(metaDataDatabaseOperation.getRowCount() + " Message Content Entries");
                }
            };
            new DatabaseTemplate(environment, OLD_CONTENT_DB_NAME, NEW_CONTENT_DB_NAME, transaction).run(contentOperation);
            environment.removeDatabase(transaction, OLD_CONTENT_DB_NAME);
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
                    throw new StoreException("Unable to upgrade message " + messageId);
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

        put(newDatabase, txn, key, value);
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

    private void upgradeConfiguredObjects(Environment environment, UpgradeInteractionHandler handler, Transaction transaction, String virtualHostName)
    {
        upgradeQueues(environment, transaction, virtualHostName);
        upgradeExchanges(environment, transaction, virtualHostName);
        upgradeQueueBindings(environment, transaction, handler, virtualHostName);
    }

    private void upgradeXidEntries(Environment environment, Transaction transaction, final String virtualHostName)
    {
        if (environment.getDatabaseNames().contains(OLD_XID_DB_NAME))
        {
            _logger.info("Xid Records");
            final OldPreparedTransactionBinding oldTransactionBinding = new OldPreparedTransactionBinding();
            final NewPreparedTransactionBinding newTransactionBinding = new NewPreparedTransactionBinding();
            CursorOperation xidEntriesCursor = new CursorOperation()
            {
                @Override
                public void processEntry(Database oldXidDatabase, Database newXidDatabase, Transaction transaction,
                        DatabaseEntry key, DatabaseEntry value)
                {
                    OldPreparedTransaction oldPreparedTransaction = oldTransactionBinding.entryToObject(value);
                    OldRecordImpl[] oldDequeues = oldPreparedTransaction.getDequeues();
                    OldRecordImpl[] oldEnqueues = oldPreparedTransaction.getEnqueues();

                    NewRecordImpl[] newEnqueues = null;
                    NewRecordImpl[] newDequeues = null;
                    if (oldDequeues != null)
                    {
                        newDequeues = new NewRecordImpl[oldDequeues.length];
                        for (int i = 0; i < newDequeues.length; i++)
                        {
                            OldRecordImpl dequeue = oldDequeues[i];
                            UUID id = UUIDGenerator.generateQueueUUID(dequeue.getQueueName(), virtualHostName);
                            newDequeues[i] = new NewRecordImpl(id, dequeue.getMessageNumber());
                        }
                    }
                    if (oldEnqueues != null)
                    {
                        newEnqueues = new NewRecordImpl[oldEnqueues.length];
                        for (int i = 0; i < newEnqueues.length; i++)
                        {
                            OldRecordImpl enqueue = oldEnqueues[i];
                            UUID id = UUIDGenerator.generateQueueUUID(enqueue.getQueueName(), virtualHostName);
                            newEnqueues[i] = new NewRecordImpl(id, enqueue.getMessageNumber());
                        }
                    }
                    NewPreparedTransaction newPreparedTransaction = new NewPreparedTransaction(newEnqueues, newDequeues);
                    DatabaseEntry newValue = new DatabaseEntry();
                    newTransactionBinding.objectToEntry(newPreparedTransaction, newValue);
                    put(newXidDatabase, transaction, key, newValue);
                }
            };
            new DatabaseTemplate(environment, OLD_XID_DB_NAME, NEW_XID_DB_NAME, transaction).run(xidEntriesCursor);
            environment.removeDatabase(transaction, OLD_XID_DB_NAME);
            _logger.info(xidEntriesCursor.getRowCount() + " Xid Entries");
        }
    }

    private void upgradeQueueEntries(Environment environment, Transaction transaction, final String virtualHostName)
    {
        _logger.info("Queue Delivery Records");
        if (environment.getDatabaseNames().contains(OLD_DELIVERY_DB_NAME))
        {
            final OldQueueEntryBinding oldBinding = new OldQueueEntryBinding();
            final NewQueueEntryBinding newBinding = new NewQueueEntryBinding();
            CursorOperation queueEntriesCursor = new CursorOperation()
            {
                @Override
                public void processEntry(Database oldDeliveryDatabase, Database newDeliveryDatabase,
                        Transaction transaction, DatabaseEntry key, DatabaseEntry value)
                {
                    OldQueueEntryKey oldEntryRecord = oldBinding.entryToObject(key);
                    UUID queueId = UUIDGenerator.generateQueueUUID(oldEntryRecord.getQueueName().asString(), virtualHostName);

                    NewQueueEntryKey newEntryRecord = new NewQueueEntryKey(queueId, oldEntryRecord.getMessageId());
                    DatabaseEntry newKey = new DatabaseEntry();
                    newBinding.objectToEntry(newEntryRecord, newKey);
                    put(newDeliveryDatabase, transaction, newKey, value);
                }
            };
            new DatabaseTemplate(environment, OLD_DELIVERY_DB_NAME, NEW_DELIVERY_DB_NAME, transaction)
                    .run(queueEntriesCursor);
            environment.removeDatabase(transaction, OLD_DELIVERY_DB_NAME);
            _logger.info(queueEntriesCursor.getRowCount() + " Queue Delivery Record Entries");
        }
    }

    private void upgradeQueueBindings(Environment environment, Transaction transaction, final UpgradeInteractionHandler handler, final String virtualHostName)
    {
        _logger.info("Queue Bindings");
        if (environment.getDatabaseNames().contains(OLD_QUEUE_BINDINGS_DB_NAME))
        {
            final QueueBindingBinding binding = new QueueBindingBinding();
            CursorOperation bindingCursor = new CursorOperation()
            {
                @Override
                public void processEntry(Database exchangeDatabase, Database configuredObjectsDatabase,
                        Transaction transaction, DatabaseEntry key, DatabaseEntry value)
                {
                    // TODO: check and remove orphaned bindings
                    BindingRecord bindingRecord = binding.entryToObject(key);
                    String exchangeName = bindingRecord.getExchangeName() == null ? ExchangeDefaults.DEFAULT_EXCHANGE_NAME : bindingRecord.getExchangeName().asString();
                    String queueName = bindingRecord.getQueueName().asString();
                    String routingKey = bindingRecord.getRoutingKey().asString();
                    FieldTable arguments = bindingRecord.getArguments();

                    UUID bindingId = UUIDGenerator.generateBindingUUID(exchangeName, queueName, routingKey, virtualHostName);
                    UpgradeConfiguredObjectRecord configuredObject = createBindingConfiguredObjectRecord(exchangeName, queueName,
                            routingKey, arguments, virtualHostName);
                    storeConfiguredObjectEntry(configuredObjectsDatabase, bindingId, configuredObject, transaction);
                }

            };
            new DatabaseTemplate(environment, OLD_QUEUE_BINDINGS_DB_NAME, CONFIGURED_OBJECTS_DB_NAME, transaction)
                    .run(bindingCursor);
            environment.removeDatabase(transaction, OLD_QUEUE_BINDINGS_DB_NAME);
            _logger.info(bindingCursor.getRowCount() + " Queue Binding Entries");
        }
    }

    private List<String> upgradeExchanges(Environment environment, Transaction transaction, final String virtualHostName)
    {
        final List<String> exchangeNames = new ArrayList<String>();
        _logger.info("Exchanges");
        if (environment.getDatabaseNames().contains(OLD_EXCHANGE_DB_NAME))
        {
            final ExchangeBinding exchangeBinding = new ExchangeBinding();
            CursorOperation exchangeCursor = new CursorOperation()
            {
                @Override
                public void processEntry(Database exchangeDatabase, Database configuredObjectsDatabase,
                        Transaction transaction, DatabaseEntry key, DatabaseEntry value)
                {
                    ExchangeRecord exchangeRecord = exchangeBinding.entryToObject(value);
                    String exchangeName = exchangeRecord.getNameShortString().asString();
                    if (!DEFAULT_EXCHANGES_SET.contains(exchangeName) && !"<<default>>".equals(exchangeName))
                    {
                        String exchangeType = exchangeRecord.getType().asString();
                        boolean autoDelete = exchangeRecord.isAutoDelete();

                        UUID exchangeId = UUIDGenerator.generateExchangeUUID(exchangeName, virtualHostName);

                        UpgradeConfiguredObjectRecord configuredObject = createExchangeConfiguredObjectRecord(exchangeName,
                                exchangeType, autoDelete);
                        storeConfiguredObjectEntry(configuredObjectsDatabase, exchangeId, configuredObject, transaction);
                        exchangeNames.add(exchangeName);
                    }
                }
            };
            new DatabaseTemplate(environment, OLD_EXCHANGE_DB_NAME, CONFIGURED_OBJECTS_DB_NAME, transaction)
                    .run(exchangeCursor);
            environment.removeDatabase(transaction, OLD_EXCHANGE_DB_NAME);
            _logger.info(exchangeCursor.getRowCount() + " Exchange Entries");
        }
        return exchangeNames;
    }

    private List<String> upgradeQueues(Environment environment, Transaction transaction, final String virtualHostName)
    {
        final List<String> queueNames = new ArrayList<String>();
        _logger.info("Queues");
        if (environment.getDatabaseNames().contains(OLD_QUEUE_DB_NAME))
        {
            final UpgradeQueueBinding queueBinding = new UpgradeQueueBinding();
            CursorOperation queueCursor = new CursorOperation()
            {
                @Override
                public void processEntry(Database queueDatabase, Database configuredObjectsDatabase,
                        Transaction transaction, DatabaseEntry key, DatabaseEntry value)
                {
                    OldQueueRecord queueRecord = queueBinding.entryToObject(value);
                    String queueName = queueRecord.getNameShortString().asString();
                    queueNames.add(queueName);
                    String owner = queueRecord.getOwner() == null ? null : queueRecord.getOwner().asString();
                    boolean exclusive = queueRecord.isExclusive();
                    FieldTable arguments = queueRecord.getArguments();

                    UUID queueId = UUIDGenerator.generateQueueUUID(queueName, virtualHostName);
                    UpgradeConfiguredObjectRecord configuredObject = createQueueConfiguredObjectRecord(queueName, owner, exclusive,
                            arguments);
                    storeConfiguredObjectEntry(configuredObjectsDatabase, queueId, configuredObject, transaction);
                }
            };
            new DatabaseTemplate(environment, OLD_QUEUE_DB_NAME, CONFIGURED_OBJECTS_DB_NAME, transaction).run(queueCursor);
            environment.removeDatabase(transaction, OLD_QUEUE_DB_NAME);
            _logger.info(queueCursor.getRowCount() + " Queue Entries");
        }
        return queueNames;
    }

    private void storeConfiguredObjectEntry(Database configuredObjectsDatabase, UUID id,
            UpgradeConfiguredObjectRecord configuredObject, Transaction transaction)
    {
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry value = new DatabaseEntry();
        UpgradeUUIDBinding uuidBinding = new UpgradeUUIDBinding();
        uuidBinding.objectToEntry(id, key);
        ConfiguredObjectBinding configuredBinding = new ConfiguredObjectBinding();
        configuredBinding.objectToEntry(configuredObject, value);
        put(configuredObjectsDatabase, transaction, key, value);
    }

    private UpgradeConfiguredObjectRecord createQueueConfiguredObjectRecord(String queueName, String owner, boolean exclusive,
            FieldTable arguments)
    {
        Map<String, Object> attributesMap = buildQueueArgumentMap(queueName,
                owner, exclusive, arguments);
        String json = _serializer.serialize(attributesMap);
        UpgradeConfiguredObjectRecord configuredObject = new UpgradeConfiguredObjectRecord(Queue.class.getName(), json);
        return configuredObject;
    }

    private Map<String, Object> buildQueueArgumentMap(String queueName,
            String owner, boolean exclusive, FieldTable arguments)
    {

        Map<String, Object> attributesMap = new HashMap<String, Object>();
        attributesMap.put(Queue.NAME, queueName);
        attributesMap.put(Queue.EXCLUSIVE, exclusive);

        FieldTable argumentsCopy = new FieldTable();
        if (arguments != null)
        {
            argumentsCopy.addAll(arguments);
        }

        if (moveNonExclusiveOwnerToDescription(owner, exclusive))
        {
            _logger.info("Non-exclusive owner " + owner + " for queue " + queueName + " moved to " + QueueArgumentsConverter.X_QPID_DESCRIPTION);

            attributesMap.put(Queue.OWNER, null);
            argumentsCopy.put(AMQShortString.valueOf(QueueArgumentsConverter.X_QPID_DESCRIPTION), owner);
        }
        else
        {
            attributesMap.put(Queue.OWNER, owner);
        }
        if (!argumentsCopy.isEmpty())
        {
            attributesMap.put(ARGUMENTS, FieldTable.convertToMap(argumentsCopy));
        }
        return attributesMap;
    }

    private boolean moveNonExclusiveOwnerToDescription(String owner,
            boolean exclusive)
    {
        return exclusive == false && owner != null && _moveNonExclusiveQueueOwnerToDescription;
    }

    private UpgradeConfiguredObjectRecord createExchangeConfiguredObjectRecord(String exchangeName, String exchangeType,
            boolean autoDelete)
    {
        Map<String, Object> attributesMap = new HashMap<String, Object>();
        attributesMap.put(Exchange.NAME, exchangeName);
        attributesMap.put(Exchange.TYPE, exchangeType);
        attributesMap.put(Exchange.LIFETIME_POLICY, autoDelete ? "AUTO_DELETE"
                : LifetimePolicy.PERMANENT.name());
        String json = _serializer.serialize(attributesMap);
        UpgradeConfiguredObjectRecord configuredObject = new UpgradeConfiguredObjectRecord(Exchange.class.getName(), json);
        return configuredObject;
    }

    private UpgradeConfiguredObjectRecord createBindingConfiguredObjectRecord(String exchangeName, String queueName,
            String routingKey, FieldTable arguments, String virtualHostName)
    {
        Map<String, Object> attributesMap = new HashMap<String, Object>();
        attributesMap.put(Binding.NAME, routingKey);
        attributesMap.put(Binding.EXCHANGE, UUIDGenerator.generateExchangeUUID(exchangeName, virtualHostName));
        attributesMap.put(Binding.QUEUE, UUIDGenerator.generateQueueUUID(queueName, virtualHostName));
        if (arguments != null)
        {
            attributesMap.put(Binding.ARGUMENTS, FieldTable.convertToMap(arguments));
        }
        String json = _serializer.serialize(attributesMap);
        UpgradeConfiguredObjectRecord configuredObject = new UpgradeConfiguredObjectRecord(Binding.class.getName(), json);
        return configuredObject;
    }

    private void put(final Database database, Transaction txn, DatabaseEntry key, DatabaseEntry value)
    {
        OperationStatus status = database.put(txn, key, value);
        if (status != OperationStatus.SUCCESS)
        {
            throw new StoreException("Cannot add record into " + database.getDatabaseName() + ":" + status);
        }
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

    static class OldQueueRecord extends Object
    {
        private final AMQShortString _queueName;
        private final AMQShortString _owner;
        private final FieldTable _arguments;
        private boolean _exclusive;

        public OldQueueRecord(AMQShortString queueName, AMQShortString owner, boolean exclusive, FieldTable arguments)
        {
            _queueName = queueName;
            _owner = owner;
            _exclusive = exclusive;
            _arguments = arguments;
        }

        public AMQShortString getNameShortString()
        {
            return _queueName;
        }

        public AMQShortString getOwner()
        {
            return _owner;
        }

        public boolean isExclusive()
        {
            return _exclusive;
        }

        public void setExclusive(boolean exclusive)
        {
            _exclusive = exclusive;
        }

        public FieldTable getArguments()
        {
            return _arguments;
        }

    }

    static class UpgradeConfiguredObjectRecord
    {
        private String _attributes;
        private String _type;

        public UpgradeConfiguredObjectRecord(String type, String attributes)
        {
            super();
            _attributes = attributes;
            _type = type;
        }

        public String getAttributes()
        {
            return _attributes;
        }

        public String getType()
        {
            return _type;
        }

    }

    static class UpgradeQueueBinding extends TupleBinding<OldQueueRecord>
    {
        public OldQueueRecord entryToObject(TupleInput tupleInput)
        {
            AMQShortString name = AMQShortStringEncoding.readShortString(tupleInput);
            AMQShortString owner = AMQShortStringEncoding.readShortString(tupleInput);
            FieldTable arguments = FieldTableEncoding.readFieldTable(tupleInput);
            boolean exclusive = tupleInput.readBoolean();
            return new OldQueueRecord(name, owner, exclusive, arguments);
        }

        public void objectToEntry(OldQueueRecord queue, TupleOutput tupleOutput)
        {
            AMQShortStringEncoding.writeShortString(queue.getNameShortString(), tupleOutput);
            AMQShortStringEncoding.writeShortString(queue.getOwner(), tupleOutput);
            FieldTableEncoding.writeFieldTable(queue.getArguments(), tupleOutput);
            tupleOutput.writeBoolean(queue.isExclusive());
        }
    }

    static class UpgradeUUIDBinding extends TupleBinding<UUID>
    {
        public UUID entryToObject(final TupleInput tupleInput)
        {
            return new UUID(tupleInput.readLong(), tupleInput.readLong());
        }

        public void objectToEntry(final UUID uuid, final TupleOutput tupleOutput)
        {
            tupleOutput.writeLong(uuid.getMostSignificantBits());
            tupleOutput.writeLong(uuid.getLeastSignificantBits());
        }
    }

    static class ConfiguredObjectBinding extends TupleBinding<UpgradeConfiguredObjectRecord>
    {

        public UpgradeConfiguredObjectRecord entryToObject(TupleInput tupleInput)
        {
            String type = tupleInput.readString();
            String json = tupleInput.readString();
            UpgradeConfiguredObjectRecord configuredObject = new UpgradeConfiguredObjectRecord(type, json);
            return configuredObject;
        }

        public void objectToEntry(UpgradeConfiguredObjectRecord object, TupleOutput tupleOutput)
        {
            tupleOutput.writeString(object.getType());
            tupleOutput.writeString(object.getAttributes());
        }

    }

    static class ExchangeRecord extends Object
    {
        private final AMQShortString _exchangeName;
        private final AMQShortString _exchangeType;
        private final boolean _autoDelete;

        public ExchangeRecord(AMQShortString exchangeName, AMQShortString exchangeType, boolean autoDelete)
        {
            _exchangeName = exchangeName;
            _exchangeType = exchangeType;
            _autoDelete = autoDelete;
        }

        public AMQShortString getNameShortString()
        {
            return _exchangeName;
        }

        public AMQShortString getType()
        {
            return _exchangeType;
        }

        public boolean isAutoDelete()
        {
            return _autoDelete;
        }

    }

    static class ExchangeBinding extends TupleBinding<ExchangeRecord>
    {

        public ExchangeRecord entryToObject(TupleInput tupleInput)
        {
            AMQShortString name = AMQShortStringEncoding.readShortString(tupleInput);
            AMQShortString typeName = AMQShortStringEncoding.readShortString(tupleInput);

            boolean autoDelete = tupleInput.readBoolean();

            return new ExchangeRecord(name, typeName, autoDelete);
        }

        public void objectToEntry(ExchangeRecord exchange, TupleOutput tupleOutput)
        {
            AMQShortStringEncoding.writeShortString(exchange.getNameShortString(), tupleOutput);
            AMQShortStringEncoding.writeShortString(exchange.getType(), tupleOutput);

            tupleOutput.writeBoolean(exchange.isAutoDelete());
        }
    }

    static class BindingRecord extends Object
    {
        private final AMQShortString _exchangeName;
        private final AMQShortString _queueName;
        private final AMQShortString _routingKey;
        private final FieldTable _arguments;

        public BindingRecord(AMQShortString exchangeName, AMQShortString queueName, AMQShortString routingKey,
                FieldTable arguments)
        {
            _exchangeName = exchangeName;
            _queueName = queueName;
            _routingKey = routingKey;
            _arguments = arguments;
        }

        public AMQShortString getExchangeName()
        {
            return _exchangeName;
        }

        public AMQShortString getQueueName()
        {
            return _queueName;
        }

        public AMQShortString getRoutingKey()
        {
            return _routingKey;
        }

        public FieldTable getArguments()
        {
            return _arguments;
        }

    }

    static class QueueBindingBinding extends TupleBinding<BindingRecord>
    {

        public BindingRecord entryToObject(TupleInput tupleInput)
        {
            AMQShortString exchangeName = AMQShortStringEncoding.readShortString(tupleInput);
            AMQShortString queueName = AMQShortStringEncoding.readShortString(tupleInput);
            AMQShortString routingKey = AMQShortStringEncoding.readShortString(tupleInput);

            FieldTable arguments = FieldTableEncoding.readFieldTable(tupleInput);

            return new BindingRecord(exchangeName, queueName, routingKey, arguments);
        }

        public void objectToEntry(BindingRecord binding, TupleOutput tupleOutput)
        {
            AMQShortStringEncoding.writeShortString(binding.getExchangeName(), tupleOutput);
            AMQShortStringEncoding.writeShortString(binding.getQueueName(), tupleOutput);
            AMQShortStringEncoding.writeShortString(binding.getRoutingKey(), tupleOutput);

            FieldTableEncoding.writeFieldTable(binding.getArguments(), tupleOutput);
        }
    }

    static class OldQueueEntryKey
    {
        private AMQShortString _queueName;
        private long _messageId;

        public OldQueueEntryKey(AMQShortString queueName, long messageId)
        {
            _queueName = queueName;
            _messageId = messageId;
        }

        public AMQShortString getQueueName()
        {
            return _queueName;
        }

        public long getMessageId()
        {
            return _messageId;
        }
    }

    static class OldQueueEntryBinding extends TupleBinding<OldQueueEntryKey>
    {

        public OldQueueEntryKey entryToObject(TupleInput tupleInput)
        {
            AMQShortString queueName = AMQShortStringEncoding.readShortString(tupleInput);
            long messageId = tupleInput.readLong();

            return new OldQueueEntryKey(queueName, messageId);
        }

        public void objectToEntry(OldQueueEntryKey mk, TupleOutput tupleOutput)
        {
            AMQShortStringEncoding.writeShortString(mk.getQueueName(), tupleOutput);
            tupleOutput.writeLong(mk.getMessageId());
        }
    }

    static class NewQueueEntryKey
    {
        private UUID _queueId;
        private long _messageId;

        public NewQueueEntryKey(UUID queueId, long messageId)
        {
            _queueId = queueId;
            _messageId = messageId;
        }

        public UUID getQueueId()
        {
            return _queueId;
        }

        public long getMessageId()
        {
            return _messageId;
        }
    }

    static class NewQueueEntryBinding extends TupleBinding<NewQueueEntryKey>
    {

        public NewQueueEntryKey entryToObject(TupleInput tupleInput)
        {
            UUID queueId = new UUID(tupleInput.readLong(), tupleInput.readLong());
            long messageId = tupleInput.readLong();

            return new NewQueueEntryKey(queueId, messageId);
        }

        public void objectToEntry(NewQueueEntryKey mk, TupleOutput tupleOutput)
        {
            UUID uuid = mk.getQueueId();
            tupleOutput.writeLong(uuid.getMostSignificantBits());
            tupleOutput.writeLong(uuid.getLeastSignificantBits());
            tupleOutput.writeLong(mk.getMessageId());
        }
    }

    static class NewPreparedTransaction
    {
        private final NewRecordImpl[] _enqueues;
        private final NewRecordImpl[] _dequeues;

        public NewPreparedTransaction(NewRecordImpl[] enqueues, NewRecordImpl[] dequeues)
        {
            _enqueues = enqueues;
            _dequeues = dequeues;
        }

        public NewRecordImpl[] getEnqueues()
        {
            return _enqueues;
        }

        public NewRecordImpl[] getDequeues()
        {
            return _dequeues;
        }
    }

    static class NewRecordImpl
    {

        private long _messageNumber;
        private UUID _queueId;

        public NewRecordImpl(UUID queueId, long messageNumber)
        {
            _messageNumber = messageNumber;
            _queueId = queueId;
        }

        public long getMessageNumber()
        {
            return _messageNumber;
        }

        public UUID getId()
        {
            return _queueId;
        }
    }

    static class NewPreparedTransactionBinding extends TupleBinding<NewPreparedTransaction>
    {
        @Override
        public NewPreparedTransaction entryToObject(TupleInput input)
        {
            NewRecordImpl[] enqueues = readRecords(input);

            NewRecordImpl[] dequeues = readRecords(input);

            return new NewPreparedTransaction(enqueues, dequeues);
        }

        private NewRecordImpl[] readRecords(TupleInput input)
        {
            NewRecordImpl[] records = new NewRecordImpl[input.readInt()];
            for (int i = 0; i < records.length; i++)
            {
                records[i] = new NewRecordImpl(new UUID(input.readLong(), input.readLong()), input.readLong());
            }
            return records;
        }

        @Override
        public void objectToEntry(NewPreparedTransaction preparedTransaction, TupleOutput output)
        {
            writeRecords(preparedTransaction.getEnqueues(), output);
            writeRecords(preparedTransaction.getDequeues(), output);
        }

        private void writeRecords(NewRecordImpl[] records, TupleOutput output)
        {
            if (records == null)
            {
                output.writeInt(0);
            }
            else
            {
                output.writeInt(records.length);
                for (NewRecordImpl record : records)
                {
                    UUID id = record.getId();
                    output.writeLong(id.getMostSignificantBits());
                    output.writeLong(id.getLeastSignificantBits());
                    output.writeLong(record.getMessageNumber());
                }
            }
        }
    }

    static class OldRecordImpl
    {

        private long _messageNumber;
        private String _queueName;

        public OldRecordImpl(String queueName, long messageNumber)
        {
            _messageNumber = messageNumber;
            _queueName = queueName;
        }

        public long getMessageNumber()
        {
            return _messageNumber;
        }

        public String getQueueName()
        {
            return _queueName;
        }
    }

    static class OldPreparedTransaction
    {
        private final OldRecordImpl[] _enqueues;
        private final OldRecordImpl[] _dequeues;

        public OldPreparedTransaction(OldRecordImpl[] enqueues, OldRecordImpl[] dequeues)
        {
            _enqueues = enqueues;
            _dequeues = dequeues;
        }

        public OldRecordImpl[] getEnqueues()
        {
            return _enqueues;
        }

        public OldRecordImpl[] getDequeues()
        {
            return _dequeues;
        }
    }

    static class OldPreparedTransactionBinding extends TupleBinding<OldPreparedTransaction>
    {
        @Override
        public OldPreparedTransaction entryToObject(TupleInput input)
        {
            OldRecordImpl[] enqueues = readRecords(input);

            OldRecordImpl[] dequeues = readRecords(input);

            return new OldPreparedTransaction(enqueues, dequeues);
        }

        private OldRecordImpl[] readRecords(TupleInput input)
        {
            OldRecordImpl[] records = new OldRecordImpl[input.readInt()];
            for (int i = 0; i < records.length; i++)
            {
                records[i] = new OldRecordImpl(input.readString(), input.readLong());
            }
            return records;
        }

        @Override
        public void objectToEntry(OldPreparedTransaction preparedTransaction, TupleOutput output)
        {
            writeRecords(preparedTransaction.getEnqueues(), output);
            writeRecords(preparedTransaction.getDequeues(), output);
        }

        private void writeRecords(OldRecordImpl[] records, TupleOutput output)
        {
            if (records == null)
            {
                output.writeInt(0);
            }
            else
            {
                output.writeInt(records.length);
                for (OldRecordImpl record : records)
                {
                    output.writeString(record.getQueueName());
                    output.writeLong(record.getMessageNumber());
                }
            }
        }
    }
}
