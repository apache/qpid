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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sleepycat.bind.tuple.ByteBinding;
import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.bind.tuple.TupleBase;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQFrameDecodingException;
import org.apache.qpid.framing.AMQProtocolVersionException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.MessagePublishInfo;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.protocol.v0_8.MessageMetaData;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.store.berkeleydb.AMQShortStringEncoding;
import org.apache.qpid.server.store.berkeleydb.FieldTableEncoding;

public class UpgradeFrom4To5 extends AbstractStoreUpgrade
{
    private static final String OLD_DELIVERY_DB = "deliveryDb_v4";
    private static final String NEW_DELIVERY_DB = "deliveryDb_v5";
    private static final String EXCHANGE_DB_NAME = "exchangeDb_v4";
    private static final String OLD_BINDINGS_DB_NAME = "queueBindingsDb_v4";
    private static final String NEW_BINDINGS_DB_NAME = "queueBindingsDb_v5";
    private static final String OLD_QUEUE_DB_NAME = "queueDb_v4";
    private static final String NEW_QUEUE_DB_NAME = "queueDb_v5";
    private static final String OLD_METADATA_DB_NAME = "messageMetaDataDb_v4";
    private static final String NEW_METADATA_DB_NAME = "messageMetaDataDb_v5";
    private static final String OLD_CONTENT_DB_NAME = "messageContentDb_v4";
    private static final String NEW_CONTENT_DB_NAME = "messageContentDb_v5";

    private static final byte COLON = (byte) ':';

    private static final Logger _logger = LoggerFactory.getLogger(UpgradeFrom4To5.class);

    public void performUpgrade(final Environment environment, final UpgradeInteractionHandler handler, ConfiguredObject<?> parent)
    {
        Transaction transaction = null;
        reportStarting(environment, 4);

        transaction = environment.beginTransaction(null, null);

        // find all queues which are bound to a topic exchange and which have a colon in their name
        final List<AMQShortString> potentialDurableSubs = findPotentialDurableSubscriptions(environment, transaction);

        Set<String> existingQueues = upgradeQueues(environment, handler, potentialDurableSubs, transaction);
        upgradeQueueBindings(environment, handler, potentialDurableSubs, transaction);
        Set<Long> messagesToDiscard = upgradeDelivery(environment, existingQueues, handler, transaction);
        upgradeContent(environment, handler, messagesToDiscard, transaction);
        upgradeMetaData(environment, handler, messagesToDiscard, transaction);
        renameRemainingDatabases(environment, handler, transaction);
        transaction.commit();

        reportFinished(environment, 5);
    }

    private void upgradeQueueBindings(Environment environment, UpgradeInteractionHandler handler, final List<AMQShortString> potentialDurableSubs,
            Transaction transaction)
    {
        if (environment.getDatabaseNames().contains(OLD_BINDINGS_DB_NAME))
        {
            _logger.info("Queue Bindings");
            final BindingTuple bindingTuple = new BindingTuple();
            CursorOperation databaseOperation = new CursorOperation()
            {

                @Override
                public void processEntry(Database sourceDatabase, Database targetDatabase, Transaction transaction,
                        DatabaseEntry key, DatabaseEntry value)
                {
                    // All the information required in binding entries is actually in the *key* not value.
                    BindingRecord oldBindingRecord = bindingTuple.entryToObject(key);

                    AMQShortString queueName = oldBindingRecord.getQueueName();
                    AMQShortString exchangeName = oldBindingRecord.getExchangeName();
                    AMQShortString routingKey = oldBindingRecord.getRoutingKey();
                    FieldTable arguments = oldBindingRecord.getArguments();

                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug(String.format(
                                "Processing binding for queue %s, exchange %s, routingKey %s arguments %s", queueName,
                                exchangeName, routingKey, arguments));
                    }

                    // if the queue name is in the gathered list then inspect its binding arguments
                    // only topic exchange should have a JMS selector key in binding
                    if (potentialDurableSubs.contains(queueName)
                            && exchangeName.equals(AMQShortString.valueOf(ExchangeDefaults.TOPIC_EXCHANGE_NAME)))
                    {
                        if (arguments == null)
                        {
                            arguments = new FieldTable();
                        }

                        AMQShortString selectorFilterKey = AMQPFilterTypes.JMS_SELECTOR.getValue();
                        if (!arguments.containsKey(selectorFilterKey))
                        {
                            if (_logger.isDebugEnabled())
                            {
                                _logger.info("adding the empty string (i.e. 'no selector') value for " + queueName
                                        + " and exchange " + exchangeName);
                            }
                            arguments.put(selectorFilterKey, "");
                        }
                    }
                    addBindingToDatabase(bindingTuple, targetDatabase, transaction, queueName, exchangeName, routingKey,
                            arguments);
                }
            };
            new DatabaseTemplate(environment, OLD_BINDINGS_DB_NAME, NEW_BINDINGS_DB_NAME, transaction)
                    .run(databaseOperation);
            environment.removeDatabase(transaction, OLD_BINDINGS_DB_NAME);
            _logger.info(databaseOperation.getRowCount() + " Queue Binding entries");
        }
    }

    private Set<String> upgradeQueues(final Environment environment, final UpgradeInteractionHandler handler,
            List<AMQShortString> potentialDurableSubs, Transaction transaction)
    {
        _logger.info("Queues");
        final Set<String> existingQueues = new HashSet<String>();
        if (environment.getDatabaseNames().contains(OLD_QUEUE_DB_NAME))
        {
            final QueueRecordBinding binding = new QueueRecordBinding(potentialDurableSubs);
            CursorOperation databaseOperation = new CursorOperation()
            {
                @Override
                public void processEntry(final Database sourceDatabase, final Database targetDatabase,
                        final Transaction transaction, final DatabaseEntry key, final DatabaseEntry value)
                {
                    QueueRecord record = binding.entryToObject(value);
                    DatabaseEntry newValue = new DatabaseEntry();
                    binding.objectToEntry(record, newValue);
                    targetDatabase.put(transaction, key, newValue);
                    existingQueues.add(record.getNameShortString().asString());
                    sourceDatabase.delete(transaction, key);
                }
            };
            new DatabaseTemplate(environment, OLD_QUEUE_DB_NAME, NEW_QUEUE_DB_NAME, transaction).run(databaseOperation);
            environment.removeDatabase(transaction, OLD_QUEUE_DB_NAME);
            _logger.info(databaseOperation.getRowCount() + " Queue entries");
        }
        return existingQueues;
    }

    private List<AMQShortString> findPotentialDurableSubscriptions(final Environment environment,
            Transaction transaction)
    {
        final List<AMQShortString> exchangeNames = findTopicExchanges(environment);
        final List<AMQShortString> queues = new ArrayList<AMQShortString>();
        final PartialBindingRecordBinding binding = new PartialBindingRecordBinding();

        CursorOperation databaseOperation = new CursorOperation()
        {
            @Override
            public void processEntry(Database sourceDatabase, Database targetDatabase, Transaction transaction,
                    DatabaseEntry key, DatabaseEntry value)
            {
                PartialBindingRecord record = binding.entryToObject(key);
                if (exchangeNames.contains(record.getExchangeName()) && record.getQueueName().contains(COLON))
                {
                    queues.add(record.getQueueName());
                }
            }
        };
        new DatabaseTemplate(environment, OLD_BINDINGS_DB_NAME, transaction).run(databaseOperation);
        return queues;
    }

    private Set<Long> upgradeDelivery(final Environment environment, final Set<String> existingQueues,
            final UpgradeInteractionHandler handler, Transaction transaction)
    {
        final Set<Long> messagesToDiscard = new HashSet<Long>();
        final Set<String> queuesToDiscard = new HashSet<String>();
        final QueueEntryKeyBinding queueEntryKeyBinding = new QueueEntryKeyBinding();
        _logger.info("Delivery Records");

        CursorOperation databaseOperation = new CursorOperation()
        {
            @Override
            public void processEntry(Database sourceDatabase, Database targetDatabase, Transaction transaction,
                    DatabaseEntry key, DatabaseEntry value)
            {
                QueueEntryKey entryKey = queueEntryKeyBinding.entryToObject(key);
                Long messageId = entryKey.getMessageId();
                final String queueName = entryKey.getQueueName().asString();
                if (!existingQueues.contains(queueName))
                {
                    if (queuesToDiscard.contains(queueName))
                    {
                        messagesToDiscard.add(messageId);
                    }
                    else
                    {
                        String lineSeparator = System.getProperty("line.separator");
                        String question = MessageFormat.format("Found persistent messages for non-durable queue ''{1}''. "
                                + " Do you with to create this queue and move all the messages into it?" + lineSeparator
                                + "NOTE: Answering No will result in these messages being discarded!", queueName);
                        UpgradeInteractionResponse response = handler.requireResponse(question.toString(),
                                UpgradeInteractionResponse.YES, UpgradeInteractionResponse.YES,
                                UpgradeInteractionResponse.NO, UpgradeInteractionResponse.ABORT);

                        if (response == UpgradeInteractionResponse.YES)
                        {
                            createQueue(environment, transaction, queueName);
                            existingQueues.add(queueName);
                        }
                        else if (response == UpgradeInteractionResponse.NO)
                        {
                            queuesToDiscard.add(queueName);
                            messagesToDiscard.add(messageId);
                        }
                        else
                        {
                            throw new StoreException("Unable is aborted!");
                        }
                    }
                }

                if (!messagesToDiscard.contains(messageId))
                {
                    DatabaseEntry newKey = new DatabaseEntry();
                    queueEntryKeyBinding.objectToEntry(entryKey, newKey);
                    targetDatabase.put(transaction, newKey, value);

                }
            }
        };
        new DatabaseTemplate(environment, OLD_DELIVERY_DB, NEW_DELIVERY_DB, transaction).run(databaseOperation);

        if (!messagesToDiscard.isEmpty())
        {
            databaseOperation = new CursorOperation()
            {
                @Override
                public void processEntry(Database sourceDatabase, Database targetDatabase, Transaction transaction,
                        DatabaseEntry key, DatabaseEntry value)
                {
                    QueueEntryKey entryKey = queueEntryKeyBinding.entryToObject(key);
                    Long messageId = entryKey.getMessageId();

                    if (messagesToDiscard.contains(messageId))
                    {
                        messagesToDiscard.remove(messageId);
                    }
                }
            };
            new DatabaseTemplate(environment, NEW_DELIVERY_DB, transaction).run(databaseOperation);
        }
        _logger.info(databaseOperation.getRowCount() + " Delivery Records entries ");
        environment.removeDatabase(transaction, OLD_DELIVERY_DB);

        return messagesToDiscard;
    }

    protected void createQueue(final Environment environment, Transaction transaction, final String queueName)
    {

        final QueueRecordBinding binding = new QueueRecordBinding(null);
        final BindingTuple bindingTuple = new BindingTuple();
        DatabaseRunnable queueCreateOperation = new DatabaseRunnable()
        {

            @Override
            public void run(Database newQueueDatabase, Database newBindingsDatabase, Transaction transaction)
            {
                AMQShortString queueNameAMQ = new AMQShortString(queueName);
                QueueRecord record = new QueueRecord(queueNameAMQ, null, false, null);

                DatabaseEntry key = new DatabaseEntry();

                TupleOutput output = new TupleOutput();
                AMQShortStringEncoding.writeShortString(record.getNameShortString(), output);
                TupleBase.outputToEntry(output, key);

                DatabaseEntry newValue = new DatabaseEntry();
                binding.objectToEntry(record, newValue);
                newQueueDatabase.put(transaction, key, newValue);

                FieldTable emptyArguments = new FieldTable();
                addBindingToDatabase(bindingTuple, newBindingsDatabase, transaction, queueNameAMQ,
                        AMQShortString.valueOf(ExchangeDefaults.DIRECT_EXCHANGE_NAME), queueNameAMQ, emptyArguments);

                // TODO QPID-3490 we should not persist a default exchange binding
                addBindingToDatabase(bindingTuple, newBindingsDatabase, transaction, queueNameAMQ,
                        AMQShortString.valueOf(ExchangeDefaults.DEFAULT_EXCHANGE_NAME), queueNameAMQ, emptyArguments);
            }
        };
        new DatabaseTemplate(environment, NEW_QUEUE_DB_NAME, NEW_BINDINGS_DB_NAME, transaction).run(queueCreateOperation);
    }

    private List<AMQShortString> findTopicExchanges(final Environment environment)
    {
        final List<AMQShortString> topicExchanges = new ArrayList<AMQShortString>();
        final ExchangeRecordBinding binding = new ExchangeRecordBinding();
        CursorOperation databaseOperation = new CursorOperation()
        {

            @Override
            public void processEntry(Database sourceDatabase, Database targetDatabase, Transaction transaction,
                    DatabaseEntry key, DatabaseEntry value)
            {
                ExchangeRecord record = binding.entryToObject(value);
                if (AMQShortString.valueOf(ExchangeDefaults.TOPIC_EXCHANGE_CLASS).equals(record.getType()))
                {
                    topicExchanges.add(record.getName());
                }
            }
        };
        new DatabaseTemplate(environment, EXCHANGE_DB_NAME, null).run(databaseOperation);
        return topicExchanges;
    }

    private void upgradeMetaData(final Environment environment, final UpgradeInteractionHandler handler,
            final Set<Long> messagesToDiscard, Transaction transaction)
    {
        _logger.info("Message MetaData");
        if (environment.getDatabaseNames().contains(OLD_METADATA_DB_NAME))
        {
            final MessageMetaDataBinding binding = new MessageMetaDataBinding();
            CursorOperation databaseOperation = new CursorOperation()
            {

                @Override
                public void processEntry(Database sourceDatabase, Database targetDatabase, Transaction transaction,
                        DatabaseEntry key, DatabaseEntry value)
                {
                    StorableMessageMetaData metaData = binding.entryToObject(value);

                    // get message id
                    Long messageId = LongBinding.entryToLong(key);

                    // ONLY copy data if message is delivered to existing queue
                    if (messagesToDiscard.contains(messageId))
                    {
                        return;
                    }
                    DatabaseEntry newValue = new DatabaseEntry();
                    binding.objectToEntry(metaData, newValue);

                    targetDatabase.put(transaction, key, newValue);
                    targetDatabase.put(transaction, key, newValue);
                    deleteCurrent();

                }
            };

            new DatabaseTemplate(environment, OLD_METADATA_DB_NAME, NEW_METADATA_DB_NAME, transaction)
                    .run(databaseOperation);
            environment.removeDatabase(transaction, OLD_METADATA_DB_NAME);
            _logger.info(databaseOperation.getRowCount() + " Message MetaData entries");
        }
    }

    private void upgradeContent(final Environment environment, final UpgradeInteractionHandler handler,
            final Set<Long> messagesToDiscard, Transaction transaction)
    {
        _logger.info("Message Contents");
        if (environment.getDatabaseNames().contains(OLD_CONTENT_DB_NAME))
        {
            final MessageContentKeyBinding keyBinding = new MessageContentKeyBinding();
            final ContentBinding contentBinding = new ContentBinding();
            CursorOperation cursorOperation = new CursorOperation()
            {
                private long _prevMsgId = -1;
                private int _bytesSeenSoFar;

                @Override
                public void processEntry(Database sourceDatabase, Database targetDatabase, Transaction transaction,
                        DatabaseEntry key, DatabaseEntry value)
                {
                    // determine the msgId of the current entry
                    MessageContentKey contentKey = keyBinding.entryToObject(key);
                    long msgId = contentKey.getMessageId();

                    // ONLY copy data if message is delivered to existing queue
                    if (messagesToDiscard.contains(msgId))
                    {
                        return;
                    }
                    // if this is a new message, restart the byte offset count.
                    if (_prevMsgId != msgId)
                    {
                        _bytesSeenSoFar = 0;
                    }

                    // determine the content size
                    ByteBuffer content = contentBinding.entryToObject(value);
                    int contentSize = content.limit();

                    // create the new key: id + previously seen data count
                    MessageContentKey newKey = new MessageContentKey(msgId, _bytesSeenSoFar);
                    DatabaseEntry newKeyEntry = new DatabaseEntry();
                    keyBinding.objectToEntry(newKey, newKeyEntry);

                    DatabaseEntry newValueEntry = new DatabaseEntry();
                    contentBinding.objectToEntry(content, newValueEntry);

                    targetDatabase.put(null, newKeyEntry, newValueEntry);

                    _prevMsgId = msgId;
                    _bytesSeenSoFar += contentSize;
                }
            };
            new DatabaseTemplate(environment, OLD_CONTENT_DB_NAME, NEW_CONTENT_DB_NAME, transaction).run(cursorOperation);
            environment.removeDatabase(transaction, OLD_CONTENT_DB_NAME);
            _logger.info(cursorOperation.getRowCount() + " Message Content entries");
        }
    }

    /**
     * For all databases which haven't been otherwise upgraded, we still need to
     * rename them from _v4 to _v5
     */
    private void renameRemainingDatabases(final Environment environment, final UpgradeInteractionHandler handler,
            Transaction transaction)
    {
        for (String dbName : environment.getDatabaseNames())
        {
            if (dbName.endsWith("_v4"))
            {
                String newName = dbName.substring(0, dbName.length() - 3) + "_v5";
                _logger.info("Renaming " + dbName + " into " + newName);
                environment.renameDatabase(transaction, dbName, newName);
            }
        }

    }

    private void addBindingToDatabase(final BindingTuple bindingTuple, Database targetDatabase, Transaction transaction,
            AMQShortString queueName, AMQShortString exchangeName, AMQShortString routingKey, FieldTable arguments)
    {

        DatabaseEntry newKey = new DatabaseEntry();

        bindingTuple.objectToEntry(new BindingRecord(exchangeName, queueName, routingKey, arguments), newKey);

        DatabaseEntry newValue = new DatabaseEntry();
        ByteBinding.byteToEntry((byte) 0, newValue);

        targetDatabase.put(transaction, newKey, newValue);
    }

    private static final class ExchangeRecord
    {
        private final AMQShortString _name;
        private final AMQShortString _type;

        private ExchangeRecord(final AMQShortString name, final AMQShortString type)
        {
            _name = name;
            _type = type;
        }

        public AMQShortString getName()
        {
            return _name;
        }

        public AMQShortString getType()
        {
            return _type;
        }
    }

    private static final class ExchangeRecordBinding extends TupleBinding<ExchangeRecord>
    {

        @Override
        public ExchangeRecord entryToObject(final TupleInput input)
        {
            return new ExchangeRecord(AMQShortStringEncoding.readShortString(input),
                    AMQShortStringEncoding.readShortString(input));
        }

        @Override
        public void objectToEntry(final ExchangeRecord object, final TupleOutput output)
        {
            AMQShortStringEncoding.writeShortString(object.getName(), output);
            AMQShortStringEncoding.writeShortString(object.getType(), output);
            output.writeBoolean(false);
        }
    }

    private static final class PartialBindingRecord
    {
        private final AMQShortString _exchangeName;
        private final AMQShortString _queueName;

        private PartialBindingRecord(final AMQShortString name, final AMQShortString type)
        {
            _exchangeName = name;
            _queueName = type;
        }

        public AMQShortString getExchangeName()
        {
            return _exchangeName;
        }

        public AMQShortString getQueueName()
        {
            return _queueName;
        }
    }

    private static final class PartialBindingRecordBinding extends TupleBinding<PartialBindingRecord>
    {

        @Override
        public PartialBindingRecord entryToObject(final TupleInput input)
        {
            return new PartialBindingRecord(AMQShortStringEncoding.readShortString(input),
                    AMQShortStringEncoding.readShortString(input));
        }

        @Override
        public void objectToEntry(final PartialBindingRecord object, final TupleOutput output)
        {
            throw new UnsupportedOperationException();
        }
    }

    static final class QueueRecord
    {
        private final AMQShortString _queueName;
        private final AMQShortString _owner;
        private final FieldTable _arguments;
        private final boolean _exclusive;

        public QueueRecord(AMQShortString queueName, AMQShortString owner, boolean exclusive, FieldTable arguments)
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

        public FieldTable getArguments()
        {
            return _arguments;
        }
    }

    static final class QueueRecordBinding extends TupleBinding<QueueRecord>
    {
        private final List<AMQShortString> _durableSubNames;

        QueueRecordBinding(final List<AMQShortString> durableSubNames)
        {
            _durableSubNames = durableSubNames;
        }

        @Override
        public QueueRecord entryToObject(final TupleInput input)
        {
            AMQShortString name = AMQShortStringEncoding.readShortString(input);
            AMQShortString owner = AMQShortStringEncoding.readShortString(input);
            FieldTable arguments = FieldTableEncoding.readFieldTable(input);
            boolean exclusive = input.available() > 0 && input.readBoolean();
            exclusive = exclusive || _durableSubNames.contains(name);

            return new QueueRecord(name, owner, exclusive, arguments);

        }

        @Override
        public void objectToEntry(final QueueRecord record, final TupleOutput output)
        {
            AMQShortStringEncoding.writeShortString(record.getNameShortString(), output);
            AMQShortStringEncoding.writeShortString(record.getOwner(), output);
            FieldTableEncoding.writeFieldTable(record.getArguments(), output);
            output.writeBoolean(record.isExclusive());

        }
    }

    static final class MessageMetaDataBinding extends TupleBinding<StorableMessageMetaData>
    {

        @Override
        public MessageMetaData entryToObject(final TupleInput input)
        {
            try
            {
                final MessagePublishInfo publishBody = readMessagePublishInfo(input);
                final ContentHeaderBody contentHeaderBody = readContentHeaderBody(input);

                return new MessageMetaData(publishBody, contentHeaderBody);
            }
            catch (Exception e)
            {
                _logger.error("Error converting entry to object: " + e, e);
                // annoyingly just have to return null since we cannot throw
                return null;
            }
        }

        private MessagePublishInfo readMessagePublishInfo(TupleInput tupleInput)
        {

            final AMQShortString exchange = AMQShortStringEncoding.readShortString(tupleInput);
            final AMQShortString routingKey = AMQShortStringEncoding.readShortString(tupleInput);
            final boolean mandatory = tupleInput.readBoolean();
            final boolean immediate = tupleInput.readBoolean();

            return new MessagePublishInfo(exchange, immediate, mandatory, routingKey);

        }

        private ContentHeaderBody readContentHeaderBody(TupleInput tupleInput) throws AMQFrameDecodingException,
                AMQProtocolVersionException
        {
            int bodySize = tupleInput.readInt();
            byte[] underlying = new byte[bodySize];
            tupleInput.readFast(underlying);

            try
            {
                return ContentHeaderBody.createFromBuffer(new DataInputStream(new ByteArrayInputStream(underlying)),
                        bodySize);
            }
            catch (IOException e)
            {
                throw new AMQFrameDecodingException(null, e.getMessage(), e);
            }
        }

        @Override
        public void objectToEntry(final StorableMessageMetaData metaData, final TupleOutput output)
        {
            final int bodySize = 1 + metaData.getStorableSize();
            byte[] underlying = new byte[bodySize];
            underlying[0] = (byte) metaData.getType().ordinal();
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(underlying);
            buf.position(1);
            buf = buf.slice();

            metaData.writeToBuffer(buf);
            output.writeInt(bodySize);
            output.writeFast(underlying);
        }
    }

    static final class MessageContentKey
    {
        private long _messageId;
        private int _chunk;

        public MessageContentKey(long messageId, int chunkNo)
        {
            _messageId = messageId;
            _chunk = chunkNo;
        }

        public int getChunk()
        {
            return _chunk;
        }

        public long getMessageId()
        {
            return _messageId;
        }

    }

    static final class MessageContentKeyBinding extends TupleBinding<MessageContentKey>
    {

        public MessageContentKey entryToObject(TupleInput tupleInput)
        {
            long messageId = tupleInput.readLong();
            int chunk = tupleInput.readInt();
            return new MessageContentKey(messageId, chunk);
        }

        public void objectToEntry(MessageContentKey object, TupleOutput tupleOutput)
        {
            final MessageContentKey mk = object;
            tupleOutput.writeLong(mk.getMessageId());
            tupleOutput.writeInt(mk.getChunk());
        }

    }

    static final class ContentBinding extends TupleBinding<ByteBuffer>
    {
        public ByteBuffer entryToObject(TupleInput tupleInput)
        {
            final int size = tupleInput.readInt();
            byte[] underlying = new byte[size];
            tupleInput.readFast(underlying);
            return ByteBuffer.wrap(underlying);
        }

        public void objectToEntry(ByteBuffer src, TupleOutput tupleOutput)
        {
            src = src.slice();

            byte[] chunkData = new byte[src.limit()];
            src.duplicate().get(chunkData);

            tupleOutput.writeInt(chunkData.length);
            tupleOutput.writeFast(chunkData);
        }
    }

    static final class QueueEntryKey
    {
        private AMQShortString _queueName;
        private long _messageId;

        public QueueEntryKey(AMQShortString queueName, long messageId)
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

    static final class QueueEntryKeyBinding extends TupleBinding<QueueEntryKey>
    {
        public QueueEntryKey entryToObject(TupleInput tupleInput)
        {
            AMQShortString queueName = AMQShortStringEncoding.readShortString(tupleInput);
            long messageId = tupleInput.readLong();
            return new QueueEntryKey(queueName, messageId);
        }

        public void objectToEntry(QueueEntryKey mk, TupleOutput tupleOutput)
        {
            AMQShortStringEncoding.writeShortString(mk.getQueueName(), tupleOutput);
            tupleOutput.writeLong(mk.getMessageId());
        }
    }

    static final class BindingRecord extends Object
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

    static final class BindingTuple extends TupleBinding<BindingRecord>
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
}
