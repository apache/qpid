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

import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import static org.apache.qpid.server.queue.NotificationCheck.MESSAGE_AGE_ALERT;
import static org.apache.qpid.server.queue.NotificationCheck.MESSAGE_COUNT_ALERT;
import static org.apache.qpid.server.queue.NotificationCheck.MESSAGE_SIZE_ALERT;
import static org.apache.qpid.server.queue.NotificationCheck.QUEUE_DEPTH_ALERT;


import junit.framework.TestCase;

import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.QueueNotificationListener;

public class NotificationCheckTest extends TestCase
{

    private ServerMessage<?> _message = mock(ServerMessage.class);
    private AMQQueue _queue = mock(AMQQueue.class);
    private QueueNotificationListener _listener = mock(QueueNotificationListener .class);

    public void testMessageCountAlertFires() throws Exception
    {
        when(_queue.getAlertThresholdQueueDepthMessages()).thenReturn(1000l);
        when(_queue.getQueueDepthMessages()).thenReturn(999, 1000, 1001);

        MESSAGE_COUNT_ALERT.notifyIfNecessary(_message, _queue, _listener);
        verifyZeroInteractions(_listener);

        MESSAGE_COUNT_ALERT.notifyIfNecessary(_message, _queue, _listener);
        verify(_listener).notifyClients(eq(MESSAGE_COUNT_ALERT), eq(_queue), eq("1000: Maximum count on queue threshold (1000) breached."));

        MESSAGE_COUNT_ALERT.notifyIfNecessary(_message, _queue, _listener);
        verify(_listener).notifyClients(eq(MESSAGE_COUNT_ALERT), eq(_queue), eq("1001: Maximum count on queue threshold (1000) breached."));
    }

    public void testMessageSizeAlertFires() throws Exception
    {
        when(_queue.getAlertThresholdMessageSize()).thenReturn(1024l);
        when(_message.getSize()).thenReturn(1023l, 1024l, 1025l);

        MESSAGE_SIZE_ALERT.notifyIfNecessary(_message, _queue, _listener);
        verifyZeroInteractions(_listener);

        MESSAGE_SIZE_ALERT.notifyIfNecessary(_message, _queue, _listener);
        verify(_listener).notifyClients(eq(MESSAGE_SIZE_ALERT), eq(_queue), contains("1024b : Maximum message size threshold (1024) breached."));

        MESSAGE_SIZE_ALERT.notifyIfNecessary(_message, _queue, _listener);
        verify(_listener).notifyClients(eq(MESSAGE_SIZE_ALERT), eq(_queue), contains("1025b : Maximum message size threshold (1024) breached."));
    }

    public void testMessageAgeAlertFires() throws Exception
    {
        long now = System.currentTimeMillis();
        when(_queue.getAlertThresholdMessageAge()).thenReturn(1000l);
        when(_queue.getOldestMessageArrivalTime()).thenReturn(now, now - 15000);

        MESSAGE_AGE_ALERT.notifyIfNecessary(_message, _queue, _listener);
        verifyZeroInteractions(_listener);

        MESSAGE_AGE_ALERT.notifyIfNecessary(_message, _queue, _listener);
        // Uses contains as first part of message is non-deterministic
        verify(_listener).notifyClients(eq(MESSAGE_AGE_ALERT), eq(_queue), contains("s : Maximum age on queue threshold (1s) breached."));
    }

    public void testQueueDepthAlertFires() throws Exception
    {
        when(_queue.getAlertThresholdQueueDepthBytes()).thenReturn(1024l);
        when(_queue.getQueueDepthBytes()).thenReturn(1023l, 1024l, 2048l);

        QUEUE_DEPTH_ALERT.notifyIfNecessary(_message, _queue, _listener);
        verifyZeroInteractions(_listener);

        QUEUE_DEPTH_ALERT.notifyIfNecessary(_message, _queue, _listener);
        verify(_listener).notifyClients(eq(QUEUE_DEPTH_ALERT), eq(_queue), eq("1Kb : Maximum queue depth threshold (1Kb) breached."));

        QUEUE_DEPTH_ALERT.notifyIfNecessary(_message, _queue, _listener);
        verify(_listener).notifyClients(eq(QUEUE_DEPTH_ALERT), eq(_queue), eq("2Kb : Maximum queue depth threshold (1Kb) breached."));
    }
}
