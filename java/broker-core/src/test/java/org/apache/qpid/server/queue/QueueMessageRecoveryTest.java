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
package org.apache.qpid.server.queue;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.store.MessageEnqueueRecord;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.test.utils.QpidTestCase;

public class QueueMessageRecoveryTest extends QpidTestCase
{
    VirtualHostImpl<?, ?, ?> _vhost;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _vhost = BrokerTestHelper.createVirtualHost("host");
    }

    public void testSimpleRecovery() throws Exception
    {
        // Simple deterministic test to prove that interleaved recovery and new publishing onto a queue is correctly
        // handled
        final List<ServerMessage<?>> messageList = new ArrayList<>();
        TestQueue queue = new TestQueue(Collections.singletonMap(Queue.NAME, (Object)"test"), _vhost, messageList);

        queue.open();

        queue.recover(createMockMessage(0), createEnqueueRecord(0, queue));
        queue.enqueue(createMockMessage(4), null, null);
        queue.enqueue(createMockMessage(5), null, null);
        queue.recover(createMockMessage(1), createEnqueueRecord(1, queue));
        queue.recover(createMockMessage(2), createEnqueueRecord(2, queue));
        queue.enqueue(createMockMessage(6), null, null);
        queue.recover(createMockMessage(3), createEnqueueRecord(3, queue));

        assertEquals(4, messageList.size());
        for(int i = 0; i < 4; i++)
        {
            assertEquals((long)i, messageList.get(i).getMessageNumber());
        }

        queue.completeRecovery();

        queue.enqueue(createMockMessage(7), null, null);

        assertEquals(8, messageList.size());

        for(int i = 0; i < 8; i++)
        {
            assertEquals((long)i, messageList.get(i).getMessageNumber());
        }

    }


    public void testMultiThreadedRecovery() throws Exception
    {
        // Non deterministic test with separate publishing and recovery threads

        performMultiThreadedRecovery(5000);
    }

    public void testRepeatedMultiThreadedRecovery() throws Exception
    {
        // Repeated non deterministic test with separate publishing and recovery threads(but with fewer messages being
        // published/recovered

        for(int i = 0; i < 50; i++)
        {
            performMultiThreadedRecovery(10);
        }
    }

    private void performMultiThreadedRecovery(final int size) throws Exception
    {

        final List<ServerMessage<?>> messageList = new ArrayList<>();
        final TestQueue queue = new TestQueue(Collections.singletonMap(Queue.NAME, (Object) "test"), _vhost, messageList);

        queue.open();


        Thread recoveryThread = new Thread(new Runnable()
            {

                @Override
                public void run()
                {
                    for(int i = 0; i < size; i++)
                    {
                        queue.recover(createMockMessage(i), createEnqueueRecord(i, queue));
                    }
                    queue.completeRecovery();
                }
            }, "recovery thread");

        Thread publishingThread = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                for(int i = 0; i < size; i++)
                {
                    queue.enqueue(createMockMessage(size + i), null, null);
                }
            }
        }, "publishing thread");

        recoveryThread.start();
        publishingThread.start();

        recoveryThread.join(10000);
        publishingThread.join(10000);

        assertEquals(size*2, messageList.size());

        for(int i = 0; i < (size*2); i++)
        {
            assertEquals((long)i, messageList.get(i).getMessageNumber());
        }
    }

    private MessageEnqueueRecord createEnqueueRecord(final int messageNumber, final TestQueue queue)
    {
        return new MessageEnqueueRecord()
        {
            @Override
            public UUID getQueueId()
            {
                return queue.getId();
            }

            @Override
            public long getMessageNumber()
            {
                return messageNumber;
            }
        };
    }


    private ServerMessage createMockMessage(final long i)
    {
        ServerMessage msg = mock(ServerMessage.class);
        when(msg.getMessageNumber()).thenReturn(i);
        MessageReference ref = mock(MessageReference.class);
        when(ref.getMessage()).thenReturn(msg);
        when(msg.newReference()).thenReturn(ref);
        when(msg.newReference(any(TransactionLogResource.class))).thenReturn(ref);
        when(msg.getStoredMessage()).thenReturn(mock(StoredMessage.class));
        return msg;
    }

    private class TestQueue extends AbstractQueue<TestQueue>
    {

        private final List<ServerMessage<?>> _messageList;

        protected TestQueue(final Map<String, Object> attributes,
                            final VirtualHostImpl virtualHost,
                            final List<ServerMessage<?>> messageList)
        {
            super(attributes, virtualHost);
            _messageList = messageList;
        }

        @Override
        QueueEntryList getEntries()
        {
            return null;
        }

        @Override
        protected void doEnqueue(final ServerMessage message, final Action<? super MessageInstance> action, MessageEnqueueRecord record)
        {
            synchronized(_messageList)
            {
                _messageList.add(message);
            }
        }
    }
}
