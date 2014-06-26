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
package org.apache.qpid.server.store;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.Transaction.Record;
import org.apache.qpid.server.store.handler.DistributedTransactionHandler;
import org.apache.qpid.server.store.handler.MessageHandler;
import org.apache.qpid.server.store.handler.MessageInstanceHandler;
import org.apache.qpid.test.utils.QpidTestCase;

import org.hamcrest.Description;
import org.mockito.ArgumentMatcher;

public abstract class MessageStoreTestCase extends QpidTestCase
{
    private MessageStore _store;
    private ConfiguredObject<?> _parent;

    public void setUp() throws Exception
    {
        super.setUp();

        _parent = createVirtualHost();

        _store = createMessageStore();

        _store.openMessageStore(_parent);
    }

    protected abstract VirtualHost createVirtualHost();

    protected abstract MessageStore createMessageStore();

    protected MessageStore getStore()
    {
        return _store;
    }

    protected void reopenStore() throws Exception
    {
        _store.closeMessageStore();

        _store = createMessageStore();
        _store.openMessageStore(_parent);
    }

    public void testAddAndRemoveRecordXid() throws Exception
    {
        long format = 1l;
        Record enqueueRecord = getTestRecord(1);
        Record dequeueRecord = getTestRecord(2);
        Record[] enqueues = { enqueueRecord };
        Record[] dequeues = { dequeueRecord };
        byte[] globalId = new byte[] { 1 };
        byte[] branchId = new byte[] { 2 };

        Transaction transaction = _store.newTransaction();
        transaction.recordXid(format, globalId, branchId, enqueues, dequeues);
        transaction.commitTran();

        reopenStore();

        DistributedTransactionHandler handler = mock(DistributedTransactionHandler.class);
        _store.visitDistributedTransactions(handler);
        verify(handler, times(1)).handle(format,globalId, branchId, enqueues, dequeues);

        transaction = _store.newTransaction();
        transaction.removeXid(1l, globalId, branchId);
        transaction.commitTran();

        reopenStore();

        handler = mock(DistributedTransactionHandler.class);
        _store.visitDistributedTransactions(handler);
        verify(handler, never()).handle(format,globalId, branchId, enqueues, dequeues);
    }

    public void testVisitMessages() throws Exception
    {
        long messageId = 1;
        int contentSize = 0;
        final StoredMessage<TestMessageMetaData> message = _store.addMessage(new TestMessageMetaData(messageId, contentSize));
        StoreFuture flushFuture = message.flushToStore();
        flushFuture.waitForCompletion();

        MessageHandler handler = mock(MessageHandler.class);
        _store.visitMessages(handler);

        verify(handler, times(1)).handle(argThat(new MessageMetaDataMatcher(messageId)));

    }

    public void testVisitMessagesAborted() throws Exception
    {
        int contentSize = 0;
        for (int i = 0; i < 3; i++)
        {
            final StoredMessage<TestMessageMetaData> message = _store.addMessage(new TestMessageMetaData(i + 1, contentSize));
            StoreFuture flushFuture = message.flushToStore();
            flushFuture.waitForCompletion();
        }

        MessageHandler handler = mock(MessageHandler.class);
        when(handler.handle(any(StoredMessage.class))).thenReturn(true, false);

        _store.visitMessages(handler);

        verify(handler, times(2)).handle(any(StoredMessage.class));
    }

    public void testReopenedMessageStoreUsesLastMessageId() throws Exception
    {
        int contentSize = 0;
        for (int i = 0; i < 3; i++)
        {
            final StoredMessage<TestMessageMetaData> message = _store.addMessage(new TestMessageMetaData(i + 1, contentSize));
            StoreFuture flushFuture = message.flushToStore();
            flushFuture.waitForCompletion();
        }

        reopenStore();

        final StoredMessage<TestMessageMetaData> message = _store.addMessage(new TestMessageMetaData(4, contentSize));

        StoreFuture flushFuture = message.flushToStore();
        flushFuture.waitForCompletion();

        assertTrue("Unexpected message id " + message.getMessageNumber(), message.getMessageNumber() >= 4);
    }

    public void testVisitMessageInstances() throws Exception
    {
        long messageId = 1;
        int contentSize = 0;
        final StoredMessage<TestMessageMetaData> message = _store.addMessage(new TestMessageMetaData(messageId, contentSize));
        StoreFuture flushFuture = message.flushToStore();
        flushFuture.waitForCompletion();

        EnqueueableMessage enqueueableMessage = createMockEnqueueableMessage(messageId, message);

        UUID queueId = UUID.randomUUID();
        TransactionLogResource queue = createTransactionLogResource(queueId);

        Transaction transaction = _store.newTransaction();
        transaction.enqueueMessage(queue, enqueueableMessage);
        transaction.commitTran();

        MessageInstanceHandler handler = mock(MessageInstanceHandler.class);
        _store.visitMessageInstances(handler);

        verify(handler, times(1)).handle(queueId, messageId);
    }

    public void testVisitDistributedTransactions() throws Exception
    {
        long format = 1l;
        byte[] branchId = new byte[] { 2 };
        byte[] globalId = new byte[] { 1 };
        Record enqueueRecord = getTestRecord(1);
        Record dequeueRecord = getTestRecord(2);
        Record[] enqueues = { enqueueRecord };
        Record[] dequeues = { dequeueRecord };

        Transaction transaction = _store.newTransaction();
        transaction.recordXid(format, globalId, branchId, enqueues, dequeues);
        transaction.commitTran();

        DistributedTransactionHandler handler = mock(DistributedTransactionHandler.class);
        _store.visitDistributedTransactions(handler);

        verify(handler, times(1)).handle(format,globalId, branchId, enqueues, dequeues);

    }

    public void testCommitTransaction() throws Exception
    {
        final UUID mockQueueId = UUIDGenerator.generateRandomUUID();
        TransactionLogResource mockQueue = createTransactionLogResource(mockQueueId);

        Transaction txn = getStore().newTransaction();

        long messageId1 = 1L;
        long messageId2 = 5L;
        final EnqueueableMessage enqueueableMessage1 = createEnqueueableMessage(messageId1);
        final EnqueueableMessage enqueueableMessage2 = createEnqueueableMessage(messageId2);

        txn.enqueueMessage(mockQueue, enqueueableMessage1);
        txn.enqueueMessage(mockQueue, enqueueableMessage2);
        txn.commitTran();

        QueueFilteringMessageInstanceHandler filter = new QueueFilteringMessageInstanceHandler(mockQueueId);
        getStore().visitMessageInstances(filter);
        Set<Long> enqueuedIds = filter.getEnqueuedIds();

        assertEquals("Number of enqueued messages is incorrect", 2, enqueuedIds.size());
        assertTrue("Message with id " + messageId1 + " is not found", enqueuedIds.contains(messageId1));
        assertTrue("Message with id " + messageId2 + " is not found", enqueuedIds.contains(messageId2));
    }

    public void testRollbackTransactionBeforeCommit() throws Exception
    {
        final UUID mockQueueId = UUIDGenerator.generateRandomUUID();
        TransactionLogResource mockQueue = createTransactionLogResource(mockQueueId);

        long messageId1 = 21L;
        long messageId2 = 22L;
        long messageId3 = 23L;
        final EnqueueableMessage enqueueableMessage1 = createEnqueueableMessage(messageId1);
        final EnqueueableMessage enqueueableMessage2 = createEnqueueableMessage(messageId2);
        final EnqueueableMessage enqueueableMessage3 = createEnqueueableMessage(messageId3);

        Transaction txn = getStore().newTransaction();

        txn.enqueueMessage(mockQueue, enqueueableMessage1);
        txn.abortTran();

        txn = getStore().newTransaction();
        txn.enqueueMessage(mockQueue, enqueueableMessage2);
        txn.enqueueMessage(mockQueue, enqueueableMessage3);
        txn.commitTran();

        QueueFilteringMessageInstanceHandler filter = new QueueFilteringMessageInstanceHandler(mockQueueId);
        getStore().visitMessageInstances(filter);
        Set<Long> enqueuedIds = filter.getEnqueuedIds();

        assertEquals("Number of enqueued messages is incorrect", 2, enqueuedIds.size());
        assertTrue("Message with id " + messageId2 + " is not found", enqueuedIds.contains(messageId2));
        assertTrue("Message with id " + messageId3 + " is not found", enqueuedIds.contains(messageId3));
    }

    public void testRollbackTransactionAfterCommit() throws Exception
    {
        final UUID mockQueueId = UUIDGenerator.generateRandomUUID();
        TransactionLogResource mockQueue = createTransactionLogResource(mockQueueId);

        long messageId1 = 30L;
        long messageId2 = 31L;
        long messageId3 = 32L;

        final EnqueueableMessage enqueueableMessage1 = createEnqueueableMessage(messageId1);
        final EnqueueableMessage enqueueableMessage2 = createEnqueueableMessage(messageId2);
        final EnqueueableMessage enqueueableMessage3 = createEnqueueableMessage(messageId3);

        Transaction txn = getStore().newTransaction();

        txn.enqueueMessage(mockQueue, enqueueableMessage1);
        txn.commitTran();

        txn = getStore().newTransaction();
        txn.enqueueMessage(mockQueue, enqueueableMessage2);
        txn.abortTran();

        txn = getStore().newTransaction();
        txn.enqueueMessage(mockQueue, enqueueableMessage3);
        txn.commitTran();

        QueueFilteringMessageInstanceHandler filter = new QueueFilteringMessageInstanceHandler(mockQueueId);
        getStore().visitMessageInstances(filter);
        Set<Long> enqueuedIds = filter.getEnqueuedIds();

        assertEquals("Number of enqueued messages is incorrect", 2, enqueuedIds.size());
        assertTrue("Message with id " + messageId1 + " is not found", enqueuedIds.contains(messageId1));
        assertTrue("Message with id " + messageId3 + " is not found", enqueuedIds.contains(messageId3));
    }

    public void testStoreIgnoresTransientMessage() throws Exception
    {
        long messageId = 1;
        int contentSize = 0;
        final StoredMessage<TestMessageMetaData> message = _store.addMessage(new TestMessageMetaData(messageId, contentSize, false));
        StoreFuture flushFuture = message.flushToStore();
        flushFuture.waitForCompletion();

        MessageHandler handler = mock(MessageHandler.class);
        _store.visitMessages(handler);

        verify(handler, times(0)).handle(argThat(new MessageMetaDataMatcher(messageId)));
    }

    public void testAddAndRemoveMessageWithoutContent() throws Exception
    {
        long messageId = 1;
        int contentSize = 0;
        final StoredMessage<TestMessageMetaData> message = _store.addMessage(new TestMessageMetaData(messageId, contentSize));
        StoreFuture flushFuture = message.flushToStore();
        flushFuture.waitForCompletion();

        final AtomicReference<StoredMessage<?>> retrievedMessageRef = new AtomicReference<StoredMessage<?>>();
        _store.visitMessages(new MessageHandler()
        {

            @Override
            public boolean handle(StoredMessage<?> storedMessage)
            {
                retrievedMessageRef.set(storedMessage);
                return true;
            }
        });

        StoredMessage<?> retrievedMessage = retrievedMessageRef.get();
        assertNotNull("Message was not found", retrievedMessageRef);
        assertEquals("Unexpected retreived message", message.getMessageNumber(), retrievedMessage.getMessageNumber());

        retrievedMessage.remove();

        retrievedMessageRef.set(null);
        _store.visitMessages(new MessageHandler()
        {

            @Override
            public boolean handle(StoredMessage<?> storedMessage)
            {
                retrievedMessageRef.set(storedMessage);
                return true;
            }
        });
        assertNull(retrievedMessageRef.get());
    }


    private TransactionLogResource createTransactionLogResource(UUID queueId)
    {
        TransactionLogResource queue = mock(TransactionLogResource.class);
        when(queue.getId()).thenReturn(queueId);
        when(queue.getName()).thenReturn("testQueue");
        when(queue.isDurable()).thenReturn(true);
        return queue;
    }

    private EnqueueableMessage createMockEnqueueableMessage(long messageId, final StoredMessage<TestMessageMetaData> message)
    {
        EnqueueableMessage enqueueableMessage = mock(EnqueueableMessage.class);
        when(enqueueableMessage.isPersistent()).thenReturn(true);
        when(enqueueableMessage.getMessageNumber()).thenReturn(messageId);
        when(enqueueableMessage.getStoredMessage()).thenReturn(message);
        return enqueueableMessage;
    }

    private Record getTestRecord(long messageNumber)
    {
        UUID queueId1 = UUIDGenerator.generateRandomUUID();
        TransactionLogResource queue1 = mock(TransactionLogResource.class);
        when(queue1.getId()).thenReturn(queueId1);
        EnqueueableMessage message1 = mock(EnqueueableMessage.class);
        when(message1.isPersistent()).thenReturn(true);
        when(message1.getMessageNumber()).thenReturn(messageNumber);
        final StoredMessage<?> storedMessage = mock(StoredMessage.class);
        when(storedMessage.getMessageNumber()).thenReturn(messageNumber);
        when(message1.getStoredMessage()).thenReturn(storedMessage);
        Record enqueueRecord = new TestRecord(queue1, message1);
        return enqueueRecord;
    }

    private EnqueueableMessage createEnqueueableMessage(long messageId1)
    {
        final StoredMessage<TestMessageMetaData> message1 = _store.addMessage(new TestMessageMetaData(messageId1, 0));
        StoreFuture flushFuture = message1.flushToStore();
        flushFuture.waitForCompletion();
        EnqueueableMessage enqueueableMessage1 = createMockEnqueueableMessage(messageId1, message1);
        return enqueueableMessage1;
    }

    private class MessageMetaDataMatcher extends ArgumentMatcher<StoredMessage<?>>
    {
        private long _messageNumber;

        public MessageMetaDataMatcher(long messageNumber)
        {
            super();
            _messageNumber = messageNumber;
        }

        public boolean matches(Object obj)
        {
            return obj instanceof StoredMessage && ((StoredMessage<?>)obj).getMessageNumber() == _messageNumber;
        }

        @Override
        public void describeTo(final Description description)
        {
            description.appendText("Expected messageNumber:");
            description.appendValue(_messageNumber);
        }

    }

    private class QueueFilteringMessageInstanceHandler implements MessageInstanceHandler
    {
        private final UUID _queueId;
        private final Set<Long> _enqueuedIds = new HashSet<Long>();

        public QueueFilteringMessageInstanceHandler(UUID queueId)
        {
            _queueId = queueId;
        }

        @Override
        public boolean handle(UUID queueId, long messageId)
        {
            if (queueId.equals(_queueId))
            {
                if (_enqueuedIds.contains(messageId))
                {
                    fail("Queue with id " + _queueId + " contains duplicate message ids");
                }
                _enqueuedIds.add(messageId);
            }
            return true;
        }

        public Set<Long> getEnqueuedIds()
        {
            return _enqueuedIds;
        }
    }

}
