/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.queue;

import junit.framework.TestCase;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.SkeletonMessageStore;
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.ContentHeaderBody;

import javax.management.Notification;

/** This class tests all the alerts an AMQQueue can throw based on threshold values of different parameters */
public class AMQQueueAlertTest extends TestCase
{
    private final static int MAX_MESSAGE_COUNT = 50;
    private final static long MAX_MESSAGE_AGE = 250;   // 0.25 sec
    private final static long MAX_MESSAGE_SIZE = 2000;  // 2 KB
    private final static long MAX_QUEUE_DEPTH = 10000;  // 10 KB
    private AMQQueue _queue;
    private AMQQueueMBean _queueMBean;
    private QueueRegistry _queueRegistry;
    private MessageStore _messageStore = new SkeletonMessageStore();

    /**
     * Tests if the alert gets thrown when message count increases the threshold limit
     *
     * @throws Exception
     */
    public void testMessageCountAlert() throws Exception
    {
        _queue = new AMQQueue("testQueue1", false, "AMQueueAlertTest", false, _queueRegistry);
        _queueMBean = (AMQQueueMBean) _queue.getManagedObject();

        _queueMBean.setMaximumMessageCount(MAX_MESSAGE_COUNT);

        sendMessages(MAX_MESSAGE_COUNT, 256l);
        assertTrue(_queueMBean.getMessageCount() == MAX_MESSAGE_COUNT);

        Notification lastNotification = _queueMBean.getLastNotification();
        assertNotNull(lastNotification);

        String notificationMsg = lastNotification.getMessage();
        assertTrue(notificationMsg.startsWith(NotificationCheck.MESSAGE_COUNT_ALERT.name()));
    }

    /**
     * Tests if the Message Size alert gets thrown when message of higher than threshold limit is sent
     *
     * @throws Exception
     */
    public void testMessageSizeAlert() throws Exception
    {
        _queue = new AMQQueue("testQueue2", false, "AMQueueAlertTest", false, _queueRegistry);
        _queueMBean = (AMQQueueMBean) _queue.getManagedObject();
        _queueMBean.setMaximumMessageCount(MAX_MESSAGE_COUNT);
        _queueMBean.setMaximumMessageSize(MAX_MESSAGE_SIZE);

        sendMessages(1, MAX_MESSAGE_SIZE * 2);
        assertTrue(_queueMBean.getMessageCount() == 1);

        Notification lastNotification = _queueMBean.getLastNotification();
        assertNotNull(lastNotification);

        String notificationMsg = lastNotification.getMessage();
        assertTrue(notificationMsg.startsWith(NotificationCheck.MESSAGE_SIZE_ALERT.name()));
    }

    /**
     * Tests if Queue Depth alert is thrown when queue depth reaches the threshold value
     *
     * @throws Exception
     */
    public void testQueueDepthAlert() throws Exception
    {
        _queue = new AMQQueue("testQueue3", false, "AMQueueAlertTest", false, _queueRegistry);
        _queueMBean = (AMQQueueMBean) _queue.getManagedObject();
        _queueMBean.setMaximumMessageCount(MAX_MESSAGE_COUNT);
        _queueMBean.setMaximumQueueDepth(MAX_QUEUE_DEPTH);

        while (_queue.getQueueDepth() < MAX_QUEUE_DEPTH)
        {
            sendMessages(1, MAX_MESSAGE_SIZE);
        }

        Notification lastNotification = _queueMBean.getLastNotification();
        assertNotNull(lastNotification);

        String notificationMsg = lastNotification.getMessage();
        assertTrue(notificationMsg.startsWith(NotificationCheck.QUEUE_DEPTH_ALERT.name()));
    }

    /**
     * Tests if MESSAGE AGE alert is thrown, when a message is in the queue for time higher than threshold value of
     * message age
     *
     * @throws Exception
     */
    public void testMessageAgeAlert() throws Exception
    {
        _queue = new AMQQueue("testQueue4", false, "AMQueueAlertTest", false, _queueRegistry);
        _queueMBean = (AMQQueueMBean) _queue.getManagedObject();
        _queueMBean.setMaximumMessageCount(MAX_MESSAGE_COUNT);
        _queueMBean.setMaximumMessageAge(MAX_MESSAGE_AGE);

        sendMessages(1, MAX_MESSAGE_SIZE);

        // Ensure message sits on queue long enough to age.
        Thread.sleep(MAX_MESSAGE_AGE * 2);

        sendMessages(1, MAX_MESSAGE_SIZE);
        assertTrue(_queueMBean.getMessageCount() == 2);

        Notification lastNotification = _queueMBean.getLastNotification();
        assertNotNull(lastNotification);

        String notificationMsg = lastNotification.getMessage();
        assertTrue(notificationMsg.startsWith(NotificationCheck.MESSAGE_AGE_ALERT.name()));
    }

    private AMQMessage message(boolean immediate, long size) throws AMQException
    {
        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Establish some way to determine the version for the test.
        BasicPublishBody publish = new BasicPublishBody((byte) 8, (byte) 0);
        publish.immediate = immediate;
        ContentHeaderBody contentHeaderBody = new ContentHeaderBody();
        contentHeaderBody.bodySize = size;   // in bytes
        AMQMessage message = new AMQMessage(_messageStore, publish);//, contentHeaderBody, null);
        message.setContentHeaderBody(contentHeaderBody);
        return message;
    }

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _queueRegistry = new DefaultQueueRegistry();
    }

    private void sendMessages(int messageCount, long size) throws AMQException
    {
        AMQMessage[] messages = new AMQMessage[messageCount];
        for (int i = 0; i < messages.length; i++)
        {
            messages[i] = message(false, size);
        }
        for (int i = 0; i < messageCount; i++)
        {
            _queue.deliver(messages[i]);
        }
    }
}
