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
package org.apache.qpid.server.txn;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;

import java.util.Collections;

import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.store.MessageDurability;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.Transaction;
import org.apache.qpid.server.txn.AsyncAutoCommitTransaction.FutureRecorder;
import org.apache.qpid.server.txn.ServerTransaction.Action;
import org.apache.qpid.test.utils.QpidTestCase;

public class AsyncAutoCommitTransactionTest extends QpidTestCase
{
    private static final String STRICT_ORDER_SYSTEM_PROPERTY = AsyncAutoCommitTransaction.QPID_STRICT_ORDER_WITH_MIXED_DELIVERY_MODE;

    private FutureRecorder _futureRecorder = mock(FutureRecorder.class);
    private EnqueueableMessage _message = mock(EnqueueableMessage.class);
    private BaseQueue _queue = mock(BaseQueue.class);
    private MessageStore _messageStore = mock(MessageStore.class);
    private Transaction _storeTransaction = mock(Transaction.class);
    private Action _postTransactionAction = mock(Action.class);
    private StoreFuture _future = mock(StoreFuture.class);


    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        when(_messageStore.newTransaction()).thenReturn(_storeTransaction);
        when(_storeTransaction.commitTranAsync()).thenReturn(_future);
        when(_queue.isDurable()).thenReturn(true);
        when(_queue.getMessageDurability()).thenReturn(MessageDurability.DEFAULT);
    }

    public void testEnqueuePersistentMessagePostCommitNotCalledWhenFutureAlreadyComplete() throws Exception
    {
        setTestSystemProperty(STRICT_ORDER_SYSTEM_PROPERTY, "false");

        when(_message.isPersistent()).thenReturn(true);
        when(_future.isComplete()).thenReturn(true);

        AsyncAutoCommitTransaction asyncAutoCommitTransaction =
                new AsyncAutoCommitTransaction(_messageStore, _futureRecorder);

        asyncAutoCommitTransaction.enqueue(_queue, _message, _postTransactionAction);

        verify(_storeTransaction).enqueueMessage(_queue, _message);
        verify(_futureRecorder).recordFuture(_future, _postTransactionAction);
        verifyZeroInteractions(_postTransactionAction);
    }

    public void testEnqueuePersistentMessageOnMultipleQueuesPostCommitNotCalled() throws Exception
    {
        setTestSystemProperty(STRICT_ORDER_SYSTEM_PROPERTY, "false");

        when(_message.isPersistent()).thenReturn(true);
        when(_future.isComplete()).thenReturn(true);

        AsyncAutoCommitTransaction asyncAutoCommitTransaction =
                new AsyncAutoCommitTransaction(_messageStore, _futureRecorder);

        asyncAutoCommitTransaction.enqueue(Collections.singletonList(_queue), _message, _postTransactionAction);

        verify(_storeTransaction).enqueueMessage(_queue, _message);
        verify(_futureRecorder).recordFuture(_future, _postTransactionAction);
        verifyZeroInteractions(_postTransactionAction);
    }

    public void testEnqueuePersistentMessagePostCommitNotCalledWhenFutureNotYetComplete() throws Exception
    {
        setTestSystemProperty(STRICT_ORDER_SYSTEM_PROPERTY, "false");

        when(_message.isPersistent()).thenReturn(true);
        when(_future.isComplete()).thenReturn(false);

        AsyncAutoCommitTransaction asyncAutoCommitTransaction =
                new AsyncAutoCommitTransaction(_messageStore, _futureRecorder);

        asyncAutoCommitTransaction.enqueue(_queue, _message, _postTransactionAction);

        verify(_storeTransaction).enqueueMessage(_queue, _message);
        verify(_futureRecorder).recordFuture(_future, _postTransactionAction);
        verifyZeroInteractions(_postTransactionAction);
    }

    public void testEnqueueTransientMessagePostCommitIsCalledWhenNotBehavingStrictly() throws Exception
    {
        setTestSystemProperty(STRICT_ORDER_SYSTEM_PROPERTY, "false");

        when(_message.isPersistent()).thenReturn(false);

        AsyncAutoCommitTransaction asyncAutoCommitTransaction =
                new AsyncAutoCommitTransaction(_messageStore, _futureRecorder);

        asyncAutoCommitTransaction.enqueue(_queue, _message, _postTransactionAction);

        verifyZeroInteractions(_storeTransaction);
        verify(_postTransactionAction).postCommit();
        verifyZeroInteractions(_futureRecorder);
    }

    public void testEnqueueTransientMessagePostCommitIsCalledWhenBehavingStrictly() throws Exception
    {
        setTestSystemProperty(STRICT_ORDER_SYSTEM_PROPERTY, "true");

        when(_message.isPersistent()).thenReturn(false);

        AsyncAutoCommitTransaction asyncAutoCommitTransaction =
                new AsyncAutoCommitTransaction(_messageStore, _futureRecorder);

        asyncAutoCommitTransaction.enqueue(_queue, _message, _postTransactionAction);

        verifyZeroInteractions(_storeTransaction);
        verify(_futureRecorder).recordFuture(StoreFuture.IMMEDIATE_FUTURE, _postTransactionAction);
        verifyZeroInteractions(_postTransactionAction);
    }
}
