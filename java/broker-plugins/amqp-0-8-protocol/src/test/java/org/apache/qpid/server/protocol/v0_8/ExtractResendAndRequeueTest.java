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
package org.apache.qpid.server.protocol.v0_8;

import junit.framework.TestCase;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.consumer.Consumer;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * QPID-1385 : Race condition between added to unacked map and resending due to a rollback.
 *
 * In AMQChannel _unackedMap.clear() was done after the visit. This meant that the clear was not in the same
 * synchronized block as as the preparation to resend.
 *
 * This clearing/prep for resend was done as a result of the rollback call. HOWEVER, the delivery thread was still
 * in the process of sending messages to the client. It is therefore possible that a message could block on the
 * _unackedMap lock waiting for the visit to complete so that it can add the new message to the unackedMap....
 * which is then cleared by the resend/rollback thread.
 *
 * This problem was encountered by the testSend2ThenRollback test.
 *
 * To try and increase the chance of the race condition occurring this test will send multiple messages so that the
 * delivery thread will be in progress while the rollback method is called. Hopefully this will cause the
 * deliveryTag to be lost
 */
public class ExtractResendAndRequeueTest extends TestCase
{

    private UnacknowledgedMessageMapImpl _unacknowledgedMessageMap;
    private static final int INITIAL_MSG_COUNT = 10;
    private AMQQueue _queue;
    private LinkedList<QueueEntry> _referenceList = new LinkedList<QueueEntry>();
    private Consumer _consumer;
    private boolean _queueDeleted;

    @Override
    public void setUp() throws AMQException
    {
        _queueDeleted = false;
        _unacknowledgedMessageMap = new UnacknowledgedMessageMapImpl(100);
        _queue = mock(AMQQueue.class);
        when(_queue.getName()).thenReturn(getName());
        when(_queue.isDeleted()).thenReturn(_queueDeleted);
        _consumer = mock(Consumer.class);
        when(_consumer.getId()).thenReturn(Consumer.SUB_ID_GENERATOR.getAndIncrement());


        long id = 0;

        // Add initial messages to QueueEntryList
        for (int count = 0; count < INITIAL_MSG_COUNT; count++)
        {
            ServerMessage msg = mock(ServerMessage.class);
            when(msg.getMessageNumber()).thenReturn(id);
            final QueueEntry entry = mock(QueueEntry.class);
            when(entry.getMessage()).thenReturn(msg);
            when(entry.getQueue()).thenReturn(_queue);
            when(entry.isQueueDeleted()).thenReturn(_queueDeleted);
            doAnswer(new Answer()
            {
                @Override
                public Object answer(final InvocationOnMock invocation) throws Throwable
                {
                    when(entry.isDeleted()).thenReturn(true);
                    return null;
                }
            }).when(entry).delete();

            _unacknowledgedMessageMap.add(id, entry);
            _referenceList.add(entry);
            //Increment ID;
            id++;
        }

        assertEquals("Map does not contain correct setup data", INITIAL_MSG_COUNT, _unacknowledgedMessageMap.size());
    }

    /**
     * Helper method to create a new subscription and acquire the given messages.
     *
     * @param messageList The messages to acquire
     *
     * @return Subscription that performed the acquire
     */
    private void acquireMessages(LinkedList<QueueEntry> messageList)
    {

        // Acquire messages in subscription
        for(QueueEntry entry : messageList)
        {
            when(entry.getDeliveredConsumer()).thenReturn(_consumer);
        }
    }

    /**
     * This is the normal consumer rollback method.
     *
     * An active consumer that has acquired messages expects those messages to be reset when rollback is requested.
     *
     * This test validates that the msgToResend map includes all the messages and none are left behind.
     *
     * @throws AMQException the visit interface throws this
     */
    public void testResend() throws AMQException
    {
        //We don't need the subscription object here.
        acquireMessages(_referenceList);

        final Map<Long, MessageInstance> msgToRequeue = new LinkedHashMap<Long, MessageInstance>();
        final Map<Long, MessageInstance> msgToResend = new LinkedHashMap<Long, MessageInstance>();

        // requeueIfUnableToResend doesn't matter here.
        _unacknowledgedMessageMap.visit(new ExtractResendAndRequeue(_unacknowledgedMessageMap, msgToRequeue,
                                                                    msgToResend));

        assertEquals("Message count for resend not correct.", INITIAL_MSG_COUNT, msgToResend.size());
        assertEquals("Message count for requeue not correct.", 0, msgToRequeue.size());
        assertEquals("Map was not emptied", 0, _unacknowledgedMessageMap.size());
    }

    /**
     * This is the normal consumer close method.
     *
     * When a consumer that has acquired messages expects closes the messages that it has acquired should be removed from
     * the unacknowledgedMap and placed in msgToRequeue
     *
     * This test validates that the msgToRequeue map includes all the messages and none are left behind.
     *
     * @throws AMQException the visit interface throws this
     */
    public void testRequeueDueToSubscriptionClosure() throws AMQException
    {
        acquireMessages(_referenceList);

        // Close subscription
        when(_consumer.isClosed()).thenReturn(true);

        final Map<Long, MessageInstance> msgToRequeue = new LinkedHashMap<Long, MessageInstance>();
        final Map<Long, MessageInstance> msgToResend = new LinkedHashMap<Long, MessageInstance>();

        // requeueIfUnableToResend doesn't matter here.
        _unacknowledgedMessageMap.visit(new ExtractResendAndRequeue(_unacknowledgedMessageMap, msgToRequeue,
                                                                    msgToResend));

        assertEquals("Message count for resend not correct.", 0, msgToResend.size());
        assertEquals("Message count for requeue not correct.", INITIAL_MSG_COUNT, msgToRequeue.size());
        assertEquals("Map was not emptied", 0, _unacknowledgedMessageMap.size());
    }


}
