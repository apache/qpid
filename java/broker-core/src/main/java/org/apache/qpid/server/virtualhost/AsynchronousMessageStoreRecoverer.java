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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;

import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.messages.TransactionLogMessages;
import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.plugin.MessageMetaDataType;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.Transaction;
import org.apache.qpid.server.store.handler.DistributedTransactionHandler;
import org.apache.qpid.server.store.handler.MessageHandler;
import org.apache.qpid.server.store.handler.MessageInstanceHandler;
import org.apache.qpid.server.txn.DtxBranch;
import org.apache.qpid.server.txn.DtxRegistry;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.transport.Xid;
import org.apache.qpid.transport.util.Functions;

public class AsynchronousMessageStoreRecoverer implements MessageStoreRecoverer
{
    private static final Logger _logger = Logger.getLogger(AsynchronousMessageStoreRecoverer.class);

    @Override
    public void recover(final VirtualHostImpl virtualHost)
    {
        AsynchronousRecoverer asynchronousRecoverer = new AsynchronousRecoverer(virtualHost);

        asynchronousRecoverer.recover();
    }

    private static class AsynchronousRecoverer
    {
        private final VirtualHostImpl<?, ?, ?> _virtualHost;
        private final EventLogger _eventLogger;
        private final MessageStore _store;
        private final MessageStoreLogSubject _logSubject;
        private final long _maxMessageId;
        private final Set<AMQQueue<?>> _recoveringQueues = new CopyOnWriteArraySet<>();
        private final AtomicBoolean _recoveryComplete = new AtomicBoolean();
        private final Map<Long, MessageReference<? extends ServerMessage<?>>> _recoveredMessages = new HashMap<>();


        private AsynchronousRecoverer(final VirtualHostImpl<?, ?, ?> virtualHost)
        {
            _virtualHost = virtualHost;
            _eventLogger = virtualHost.getEventLogger();
            _store = virtualHost.getMessageStore();
            _logSubject = new MessageStoreLogSubject(virtualHost.getName(), _store.getClass().getSimpleName());

            _maxMessageId = _store.getNextMessageId();
            _recoveringQueues.addAll(_virtualHost.getQueues());

        }

        public void recover()
        {
            getStore().visitDistributedTransactions(new DistributedTransactionVisitor());

            for(AMQQueue<?> queue : _recoveringQueues)
            {
                Thread queueThread = new Thread(new QueueRecoveringTask(queue), "Queue Recoverer : " + queue.getName() + " (vh: " + getVirtualHost().getName() + ")");
                queueThread.start();
            }
        }

        public VirtualHostImpl<?, ?, ?> getVirtualHost()
        {
            return _virtualHost;
        }

        public EventLogger getEventLogger()
        {
            return _eventLogger;
        }

        public MessageStore getStore()
        {
            return _store;
        }

        public MessageStoreLogSubject getLogSubject()
        {
            return _logSubject;
        }

        private boolean isRecovering(AMQQueue<?> queue)
        {
            return _recoveringQueues.contains(queue);
        }

        private void recoverQueue(AMQQueue<?> queue)
        {
            MessageInstanceVisitor handler = new MessageInstanceVisitor(queue);
            _store.visitMessageInstances(queue, handler);

            getEventLogger().message(getLogSubject(), TransactionLogMessages.RECOVERED(handler.getRecoveredCount(), queue.getName()));
            getEventLogger().message(getLogSubject(), TransactionLogMessages.RECOVERY_COMPLETE(queue.getName(), true));
            queue.completeRecovery();

            _recoveringQueues.remove(queue);
            if (_recoveringQueues.isEmpty() && _recoveryComplete.compareAndSet(false, true))
            {
                completeRecovery();
            }
        }

        private synchronized void completeRecovery()
        {
            // at this point nothing should be writing to the map of recovered messages
            for (Map.Entry<Long,MessageReference<? extends ServerMessage<?>>> entry : _recoveredMessages.entrySet())
            {
                entry.getValue().release();
                entry.setValue(null); // free up any memory associated with the reference object
            }
            getStore().visitMessages(new MessageHandler()
            {
                @Override
                public boolean handle(final StoredMessage<?> storedMessage)
                {

                    long messageNumber = storedMessage.getMessageNumber();
                    if(!_recoveredMessages.containsKey(messageNumber))
                    {
                        _logger.info("Message id " + messageNumber + " in store, but not in any queue - removing....");
                        storedMessage.remove();
                    }
                    return messageNumber <_maxMessageId-1;
                }
            });
            _recoveredMessages.clear();
        }

        private synchronized ServerMessage<?> getRecoveredMessage(final long messageId)
        {
            MessageReference<? extends ServerMessage<?>> ref = _recoveredMessages.get(messageId);
            if (ref == null)
            {
                StoredMessage<?> message = _store.getMessage(messageId);
                if(message != null)
                {
                    StorableMessageMetaData metaData = message.getMetaData();

                    @SuppressWarnings("rawtypes")
                    MessageMetaDataType type = metaData.getType();

                    @SuppressWarnings("unchecked")
                    ServerMessage<?> serverMessage = type.createMessage(message);

                    ref = serverMessage.newReference();
                    _recoveredMessages.put(messageId, ref);
                }
            }
            return ref == null ? null : ref.getMessage();
        }


        private class DistributedTransactionVisitor implements DistributedTransactionHandler
        {



            @Override
            public boolean handle(long format,
                                  byte[] globalId,
                                  byte[] branchId,
                                  Transaction.Record[] enqueues,
                                  Transaction.Record[] dequeues)
            {
                Xid id = new Xid(format, globalId, branchId);
                DtxRegistry dtxRegistry = getVirtualHost().getDtxRegistry();
                DtxBranch branch = dtxRegistry.getBranch(id);
                if (branch == null)
                {
                    branch = new DtxBranch(id, getStore(), getVirtualHost());
                    dtxRegistry.registerBranch(branch);
                }
                for (Transaction.Record record : enqueues)
                {
                    final AMQQueue<?> queue = getVirtualHost().getQueue(record.getResource().getId());
                    if (queue != null)
                    {
                        final long messageId = record.getMessage().getMessageNumber();
                        final ServerMessage<?> message = getRecoveredMessage(messageId);

                        if (message != null)
                        {
                            final MessageReference<?> ref = message.newReference();

                            branch.enqueue(queue, message);

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
                            getEventLogger().message(getLogSubject(),
                                                            TransactionLogMessages.XA_INCOMPLETE_MESSAGE(xidString.toString(),
                                                                                                         Long.toString(
                                                                                                                 messageId)));
                        }
                    }
                    else
                    {
                        StringBuilder xidString = xidAsString(id);
                        getEventLogger().message(getLogSubject(),
                                                        TransactionLogMessages.XA_INCOMPLETE_QUEUE(xidString.toString(),
                                                                                                   record.getResource()
                                                                                                           .getId()
                                                                                                           .toString()));

                    }
                }
                for (Transaction.Record record : dequeues)
                {

                    final AMQQueue<?> queue = getVirtualHost().getQueue(record.getResource().getId());

                    if (queue != null)
                    {
                        // For DTX to work correctly the queues which have uncommitted branches with dequeues
                        // must be synchronously recovered

                        if (isRecovering(queue))
                        {
                            recoverQueue(queue);
                        }

                        final long messageId = record.getMessage().getMessageNumber();
                        final ServerMessage<?> message = getRecoveredMessage(messageId);

                        if (message != null)
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
                            getEventLogger().message(getLogSubject(),
                                                            TransactionLogMessages.XA_INCOMPLETE_MESSAGE(xidString.toString(),
                                                                                                         Long.toString(
                                                                                                                 messageId)));

                        }

                    }
                    else
                    {
                        StringBuilder xidString = xidAsString(id);
                        getEventLogger().message(getLogSubject(),
                                                        TransactionLogMessages.XA_INCOMPLETE_QUEUE(xidString.toString(),
                                                                                                   record.getResource()
                                                                                                           .getId()
                                                                                                           .toString()));
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

        private class QueueRecoveringTask implements Runnable
        {
            private final AMQQueue<?> _queue;

            public QueueRecoveringTask(final AMQQueue<?> queue)
            {
                _queue = queue;
            }

            @Override
            public void run()
            {
                recoverQueue(_queue);
            }
        }

        private class MessageInstanceVisitor implements MessageInstanceHandler
        {
            private final AMQQueue<?> _queue;
            long _recoveredCount;

            private MessageInstanceVisitor(AMQQueue<?> queue)
            {
                _queue = queue;
            }

            @Override
            public boolean handle(final UUID queueId, long messageId)
            {
                String queueName = _queue.getName();

                if(messageId < _maxMessageId)
                {
                    ServerMessage<?> message = getRecoveredMessage(messageId);

                    if (message != null)
                    {
                        if (_logger.isDebugEnabled())
                        {
                            _logger.debug("On recovery, delivering " + message.getMessageNumber() + " to " + queueName);
                        }

                        _queue.recover(message);
                        _recoveredCount++;
                    }
                    else
                    {
                        _logger.warn("Message id "
                                     + messageId
                                     + " referenced in log as enqueued in queue "
                                     + queueName
                                     + " is unknown, entry will be discarded");
                        Transaction txn = getStore().newTransaction();
                        txn.dequeueMessage(_queue, new DummyMessage(messageId));
                        txn.commitTranAsync();
                    }
                    return true;
                }
                else
                {
                    return false;
                }

            }

            public long getRecoveredCount()
            {
                return _recoveredCount;
            }
        }
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
