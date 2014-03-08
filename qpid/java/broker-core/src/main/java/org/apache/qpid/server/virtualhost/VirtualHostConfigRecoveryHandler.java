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

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.messages.TransactionLogMessages;
import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreRecoveryHandler;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.Transaction;
import org.apache.qpid.server.store.TransactionLogRecoveryHandler;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.txn.DtxBranch;
import org.apache.qpid.server.txn.DtxRegistry;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.transport.Xid;
import org.apache.qpid.transport.util.Functions;

public class VirtualHostConfigRecoveryHandler implements
                                                        MessageStoreRecoveryHandler,
                                                        MessageStoreRecoveryHandler.StoredMessageRecoveryHandler,
                                                        TransactionLogRecoveryHandler,
                                                        TransactionLogRecoveryHandler.QueueEntryRecoveryHandler,
                                                        TransactionLogRecoveryHandler.DtxRecordRecoveryHandler
{
    private static final Logger _logger = Logger.getLogger(VirtualHostConfigRecoveryHandler.class);

    private final VirtualHost _virtualHost;

    private final Map<String, Integer> _queueRecoveries = new TreeMap<String, Integer>();
    private final Map<Long, ServerMessage> _recoveredMessages = new HashMap<Long, ServerMessage>();
    private final Map<Long, StoredMessage> _unusedMessages = new HashMap<Long, StoredMessage>();
    private final EventLogger _eventLogger;

    private MessageStoreLogSubject _logSubject;
    private MessageStore _store;

    public VirtualHostConfigRecoveryHandler(VirtualHost virtualHost)
    {
        _virtualHost = virtualHost;
        _eventLogger = virtualHost.getEventLogger();
    }

    public VirtualHostConfigRecoveryHandler begin(MessageStore store)
    {
        _logSubject = new MessageStoreLogSubject(_virtualHost.getName(), store.getClass().getSimpleName());
        _store = store;
        _eventLogger.message(_logSubject, TransactionLogMessages.RECOVERY_START(null, false));
        return this;
    }

    public StoredMessageRecoveryHandler begin()
    {
        return this;
    }

    public void message(StoredMessage message)
    {
        ServerMessage serverMessage  = message.getMetaData().getType().createMessage(message);

        _recoveredMessages.put(message.getMessageNumber(), serverMessage);
        _unusedMessages.put(message.getMessageNumber(), message);
    }

    public void completeMessageRecovery()
    {
    }

    public void dtxRecord(long format, byte[] globalId, byte[] branchId,
                          Transaction.Record[] enqueues,
                          Transaction.Record[] dequeues)
    {
        Xid id = new Xid(format, globalId, branchId);
        DtxRegistry dtxRegistry = _virtualHost.getDtxRegistry();
        DtxBranch branch = dtxRegistry.getBranch(id);
        if(branch == null)
        {
            branch = new DtxBranch(id, _store, _virtualHost);
            dtxRegistry.registerBranch(branch);
        }
        for(Transaction.Record record : enqueues)
        {
            final AMQQueue queue = _virtualHost.getQueue(record.getResource().getId());
            if(queue != null)
            {
                final long messageId = record.getMessage().getMessageNumber();
                final ServerMessage message = _recoveredMessages.get(messageId);
                _unusedMessages.remove(messageId);

                if(message != null)
                {
                    final MessageReference ref = message.newReference();


                    branch.enqueue(queue,message);

                    branch.addPostTransactionAction(new ServerTransaction.Action()
                    {

                        public void postCommit()
                        {
                            queue.enqueue(message, null);
                            ref.release();
                        }

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
        for(Transaction.Record record : dequeues)
        {
            final AMQQueue queue = _virtualHost.getQueue(record.getResource().getId());
            if(queue != null)
            {
                final long messageId = record.getMessage().getMessageNumber();
                final ServerMessage message = _recoveredMessages.get(messageId);
                _unusedMessages.remove(messageId);

                if(message != null)
                {
                    final QueueEntry entry = queue.getMessageOnTheQueue(messageId);

                    entry.acquire();

                    branch.dequeue(queue, message);

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
                                                                             record.getResource().getId().toString()));
            }

        }

        branch.setState(DtxBranch.State.PREPARED);
        branch.prePrepareTransaction();
    }

    private static StringBuilder xidAsString(Xid id)
    {
        return new StringBuilder("(")
                    .append(id.getFormat())
                    .append(',')
                    .append(Functions.str(id.getGlobalId()))
                    .append(',')
                    .append(Functions.str(id.getBranchId()))
                    .append(')');
    }

    public void completeDtxRecordRecovery()
    {
        for(StoredMessage m : _unusedMessages.values())
        {
            _logger.warn("Message id " + m.getMessageNumber() + " in store, but not in any queue - removing....");
            m.remove();
        }
        _eventLogger.message(_logSubject, TransactionLogMessages.RECOVERY_COMPLETE(null, false));
    }

    public void complete()
    {
    }

    public void queueEntry(final UUID queueId, long messageId)
    {
        AMQQueue queue = _virtualHost.getQueue(queueId);
        if(queue != null)
        {
            String queueName = queue.getName();
            ServerMessage message = _recoveredMessages.get(messageId);
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

                queue.enqueue(message,null);

                _queueRecoveries.put(queueName, ++count);
            }
            else
            {
                _logger.warn("Message id " + messageId + " referenced in log as enqueued in queue " + queueName + " is unknown, entry will be discarded");
                Transaction txn = _store.newTransaction();
                txn.dequeueMessage(queue, new DummyMessage(messageId));
                txn.commitTranAsync();
            }
        }
        else
        {
            _logger.warn("Message id " + messageId + " in log references queue with id " + queueId + " which is not in the configuration, entry will be discarded");
            Transaction txn = _store.newTransaction();
            TransactionLogResource mockQueue =
                    new TransactionLogResource()
                    {
                        @Override
                        public String getName()
                        {
                            return "<<UNKNOWN>>";
                        }

                        @Override
                        public UUID getId()
                        {
                            return queueId;
                        }

                        @Override
                        public boolean isDurable()
                        {
                            return false;
                        }
                    };
            txn.dequeueMessage(mockQueue, new DummyMessage(messageId));
            txn.commitTranAsync();
        }
    }

    public DtxRecordRecoveryHandler completeQueueEntryRecovery()
    {

        for(Map.Entry<String,Integer> entry : _queueRecoveries.entrySet())
        {
            _eventLogger.message(_logSubject, TransactionLogMessages.RECOVERED(entry.getValue(), entry.getKey()));

            _eventLogger.message(_logSubject, TransactionLogMessages.RECOVERY_COMPLETE(entry.getKey(), true));
        }



        return this;
    }

    private static class DummyMessage implements EnqueueableMessage
    {


        private final long _messageId;

        public DummyMessage(long messageId)
        {
            _messageId = messageId;
        }

        public long getMessageNumber()
        {
            return _messageId;
        }


        public boolean isPersistent()
        {
            return true;
        }


        public StoredMessage getStoredMessage()
        {
            return null;
        }
    }

}
