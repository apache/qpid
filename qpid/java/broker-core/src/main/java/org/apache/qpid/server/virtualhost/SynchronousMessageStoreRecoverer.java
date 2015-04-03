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
package org.apache.qpid.server.virtualhost;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.messages.MessageStoreMessages;
import org.apache.qpid.server.logging.messages.TransactionLogMessages;
import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.plugin.MessageMetaDataType;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.store.MessageEnqueueRecord;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.Transaction;
import org.apache.qpid.server.store.Transaction.EnqueueRecord;
import org.apache.qpid.server.store.handler.DistributedTransactionHandler;
import org.apache.qpid.server.store.handler.MessageHandler;
import org.apache.qpid.server.store.handler.MessageInstanceHandler;
import org.apache.qpid.server.txn.DtxBranch;
import org.apache.qpid.server.txn.DtxRegistry;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.transport.Xid;
import org.apache.qpid.transport.util.Functions;

public class SynchronousMessageStoreRecoverer implements MessageStoreRecoverer
{
    private static final Logger _logger = LoggerFactory.getLogger(SynchronousMessageStoreRecoverer.class);

    @Override
    public void recover(VirtualHostImpl virtualHost)
    {
        EventLogger eventLogger = virtualHost.getEventLogger();
        MessageStore store = virtualHost.getMessageStore();
        MessageStore.MessageStoreReader storeReader = store.newMessageStoreReader();
        MessageStoreLogSubject logSubject = new MessageStoreLogSubject(virtualHost.getName(), store.getClass().getSimpleName());

        Map<String, Integer> queueRecoveries = new TreeMap<>();
        Map<Long, ServerMessage<?>> recoveredMessages = new HashMap<>();
        Map<Long, StoredMessage<?>> unusedMessages = new HashMap<>();


        eventLogger.message(logSubject, MessageStoreMessages.RECOVERY_START());

        storeReader.visitMessages(new MessageVisitor(recoveredMessages, unusedMessages));

        eventLogger.message(logSubject, TransactionLogMessages.RECOVERY_START(null, false));
        storeReader.visitMessageInstances(new MessageInstanceVisitor(virtualHost, store, queueRecoveries,
                                                               recoveredMessages, unusedMessages));
        for(Map.Entry<String,Integer> entry : queueRecoveries.entrySet())
        {
            eventLogger.message(logSubject, TransactionLogMessages.RECOVERED(entry.getValue(), entry.getKey()));
            eventLogger.message(logSubject, TransactionLogMessages.RECOVERY_COMPLETE(entry.getKey(), true));
            virtualHost.getQueue(entry.getKey()).completeRecovery();
        }

        Collection<AMQQueue> allQueues = virtualHost.getQueues();

        for(AMQQueue q : allQueues)
        {
            if(!queueRecoveries.containsKey(q.getName()))
            {
                q.completeRecovery();
            }
        }

        storeReader.visitDistributedTransactions(new DistributedTransactionVisitor(virtualHost, store, eventLogger,
                                                                             logSubject, recoveredMessages, unusedMessages));



        for(StoredMessage<?> m : unusedMessages.values())
        {
            _logger.warn("Message id " + m.getMessageNumber() + " in store, but not in any queue - removing....");
            m.remove();
        }
        eventLogger.message(logSubject, TransactionLogMessages.RECOVERY_COMPLETE(null, false));

        eventLogger.message(logSubject,
                             MessageStoreMessages.RECOVERED(recoveredMessages.size() - unusedMessages.size()));
        eventLogger.message(logSubject, MessageStoreMessages.RECOVERY_COMPLETE());


    }

    @Override
    public void cancel()
    {
        // No-op
    }

    private static class MessageVisitor implements MessageHandler
    {

        private final Map<Long, ServerMessage<?>> _recoveredMessages;
        private final Map<Long, StoredMessage<?>> _unusedMessages;

        public MessageVisitor(final Map<Long, ServerMessage<?>> recoveredMessages,
                              final Map<Long, StoredMessage<?>> unusedMessages)
        {
            _recoveredMessages = recoveredMessages;
            _unusedMessages = unusedMessages;
        }

        @Override
        public boolean handle(StoredMessage<?> message)
        {
            StorableMessageMetaData metaData = message.getMetaData();

            @SuppressWarnings("rawtypes")
            MessageMetaDataType type = metaData.getType();

            @SuppressWarnings("unchecked")
            ServerMessage<?> serverMessage = type.createMessage(message);

            _recoveredMessages.put(message.getMessageNumber(), serverMessage);
            _unusedMessages.put(message.getMessageNumber(), message);
            return true;
        }

    }

    private static class MessageInstanceVisitor implements MessageInstanceHandler
    {
        private final VirtualHostImpl _virtualHost;
        private final MessageStore _store;

        private final Map<String, Integer> _queueRecoveries;
        private final Map<Long, ServerMessage<?>> _recoveredMessages;
        private final Map<Long, StoredMessage<?>> _unusedMessages;

        private MessageInstanceVisitor(final VirtualHostImpl virtualHost,
                                       final MessageStore store,
                                       final Map<String, Integer> queueRecoveries,
                                       final Map<Long, ServerMessage<?>> recoveredMessages,
                                       final Map<Long, StoredMessage<?>> unusedMessages)
        {
            _virtualHost = virtualHost;
            _store = store;
            _queueRecoveries = queueRecoveries;
            _recoveredMessages = recoveredMessages;
            _unusedMessages = unusedMessages;
        }

        @Override
        public boolean handle(final MessageEnqueueRecord record)
        {
            final UUID queueId = record.getQueueId();
            long messageId = record.getMessageNumber();
            AMQQueue<?> queue = _virtualHost.getQueue(queueId);
            if(queue != null)
            {
                String queueName = queue.getName();
                ServerMessage<?> message = _recoveredMessages.get(messageId);
                _unusedMessages.remove(messageId);

                if(message != null)
                {
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("On recovery, delivering " + message.getMessageNumber() + " to " + queueName);
                    }

                    Integer count = _queueRecoveries.get(queueName);
                    if (count == null)
                    {
                        count = 0;
                    }

                    queue.recover(message, record);

                    _queueRecoveries.put(queueName, ++count);
                }
                else
                {
                    _logger.warn("Message id " + messageId + " referenced in log as enqueued in queue " + queueName + " is unknown, entry will be discarded");
                    Transaction txn = _store.newTransaction();
                    txn.dequeueMessage(record);
                    txn.commitTranAsync();
                }
            }
            else
            {
                _logger.warn("Message id " + messageId + " in log references queue with id " + queueId + " which is not in the configuration, entry will be discarded");
                Transaction txn = _store.newTransaction();
                txn.dequeueMessage(record);
                txn.commitTranAsync();
            }
            return true;
        }
    }

    private static class DistributedTransactionVisitor implements DistributedTransactionHandler
    {

        private final VirtualHostImpl _virtualHost;
        private final MessageStore _store;
        private final EventLogger _eventLogger;
        private final MessageStoreLogSubject _logSubject;

        private final Map<Long, ServerMessage<?>> _recoveredMessages;
        private final Map<Long, StoredMessage<?>> _unusedMessages;

        private DistributedTransactionVisitor(final VirtualHostImpl virtualHost,
                                              final MessageStore store,
                                              final EventLogger eventLogger,
                                              final MessageStoreLogSubject logSubject,
                                              final Map<Long, ServerMessage<?>> recoveredMessages,
                                              final Map<Long, StoredMessage<?>> unusedMessages)
        {
            _virtualHost = virtualHost;
            _store = store;
            _eventLogger = eventLogger;
            _logSubject = logSubject;
            _recoveredMessages = recoveredMessages;
            _unusedMessages = unusedMessages;
        }

        @Override
        public boolean handle(final Transaction.StoredXidRecord storedXid,
                              final Transaction.EnqueueRecord[] enqueues,
                              final Transaction.DequeueRecord[] dequeues)
        {
            Xid id = new Xid(storedXid.getFormat(), storedXid.getGlobalId(), storedXid.getBranchId());
            DtxRegistry dtxRegistry = _virtualHost.getDtxRegistry();
            DtxBranch branch = dtxRegistry.getBranch(id);
            if(branch == null)
            {
                branch = new DtxBranch(storedXid, _store, _virtualHost);
                dtxRegistry.registerBranch(branch);
            }
            for(EnqueueRecord record : enqueues)
            {
                final AMQQueue<?> queue = _virtualHost.getQueue(record.getResource().getId());
                if(queue != null)
                {
                    final long messageId = record.getMessage().getMessageNumber();
                    final ServerMessage<?> message = _recoveredMessages.get(messageId);
                    _unusedMessages.remove(messageId);

                    if(message != null)
                    {
                        final MessageReference<?> ref = message.newReference();
                        final MessageEnqueueRecord[] records = new MessageEnqueueRecord[1];

                        branch.enqueue(queue, message, new Action<MessageEnqueueRecord>()
                        {
                            @Override
                            public void performAction(final MessageEnqueueRecord record)
                            {
                                records[0] = record;
                            }
                        });
                        branch.addPostTransactionAction(new ServerTransaction.Action()
                        {
                            @Override
                            public void postCommit()
                            {
                                queue.enqueue(message, null, records[0]);
                                ref.release();
                            }

                            @Override
                            public void onRollback()
                            {
                                ref.release();
                            }
                        });

                    }
                    else
                    {
                        StringBuilder xidString = xidAsString(id);
                        _eventLogger.message(_logSubject,
                                          TransactionLogMessages.XA_INCOMPLETE_MESSAGE(xidString.toString(),
                                                                                       Long.toString(messageId)));
                    }
                }
                else
                {
                    StringBuilder xidString = xidAsString(id);
                    _eventLogger.message(_logSubject,
                                      TransactionLogMessages.XA_INCOMPLETE_QUEUE(xidString.toString(),
                                                                                 record.getResource().getId().toString()));

                }
            }
            for(Transaction.DequeueRecord record : dequeues)
            {
                final AMQQueue<?> queue = _virtualHost.getQueue(record.getEnqueueRecord().getQueueId());
                if(queue != null)
                {
                    final long messageId = record.getEnqueueRecord().getMessageNumber();
                    final ServerMessage<?> message = _recoveredMessages.get(messageId);
                    _unusedMessages.remove(messageId);

                    if(message != null)
                    {
                        final QueueEntry entry = queue.getMessageOnTheQueue(messageId);

                        entry.acquire();

                        branch.dequeue(entry.getEnqueueRecord());

                        branch.addPostTransactionAction(new ServerTransaction.Action()
                        {

                            public void postCommit()
                            {
                                entry.delete();
                            }

                            public void onRollback()
                            {
                                entry.release();
                            }
                        });
                    }
                    else
                    {
                        StringBuilder xidString = xidAsString(id);
                        _eventLogger.message(_logSubject,
                                          TransactionLogMessages.XA_INCOMPLETE_MESSAGE(xidString.toString(),
                                                                                       Long.toString(messageId)));

                    }

                }
                else
                {
                    StringBuilder xidString = xidAsString(id);
                    _eventLogger.message(_logSubject,
                                      TransactionLogMessages.XA_INCOMPLETE_QUEUE(xidString.toString(),
                                                                                 record.getEnqueueRecord().getQueueId().toString()));
                }

            }

            branch.setState(DtxBranch.State.PREPARED);
            branch.prePrepareTransaction();
            return true;
        }

        private StringBuilder xidAsString(Xid id)
        {
            return new StringBuilder("(")
                        .append(id.getFormat())
                        .append(',')
                        .append(Functions.str(id.getGlobalId()))
                        .append(',')
                        .append(Functions.str(id.getBranchId()))
                        .append(')');
        }


    }


}
