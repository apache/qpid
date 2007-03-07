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
import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.txn.NonTransactionalContext;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;

import javax.management.Notification;
import java.util.LinkedList;
import java.util.HashSet;

/** This class tests all the alerts an AMQQueue can throw based on threshold values of different parameters */
public class AMQQueueAlertTest extends TestCase
{
    private final static int MAX_MESSAGE_COUNT = 50;
    private final static long MAX_MESSAGE_AGE = 250;   // 0.25 sec
    private final static long MAX_MESSAGE_SIZE = 2000;  // 2 KB
    private final static long MAX_QUEUE_DEPTH = 10000;  // 10 KB
    private AMQQueue _queue;
    private AMQQueueMBean _queueMBean;
    private VirtualHost _virtualHost;
    private MessageStore _messageStore = new MemoryMessageStore();
    private StoreContext _storeContext = new StoreContext();
    private TransactionalContext _transactionalContext = new NonTransactionalContext(_messageStore, _storeContext,
                                                                                     null,
                                                                                     new LinkedList<RequiredDeliveryException>(),
                                                                                     new HashSet<Long>());

    /**
     * Tests if the alert gets thrown when message count increases the threshold limit
     *
     * @throws Exception
     */
    public void testMessageCountAlert() throws Exception
    {
        _queue = new AMQQueue(new AMQShortString("testQueue1"), false,  new AMQShortString("AMQueueAlertTest"),
                              false, _virtualHost);
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
        _queue = new AMQQueue(new AMQShortString("testQueue2"), false,  new AMQShortString("AMQueueAlertTest"),
                              false, _virtualHost);
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
        _queue = new AMQQueue(new AMQShortString("testQueue3"), false,  new AMQShortString("AMQueueAlertTest"),
                              false, _virtualHost);
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
        _queue = new AMQQueue(new AMQShortString("testQueue4"), false,  new AMQShortString("AMQueueAlertTest"),
                              false, _virtualHost);
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

    protected AMQMessage message(final boolean immediate, long size) throws AMQException
    {
        MessagePublishInfo publish = new MessagePublishInfo()
        {

            public AMQShortString getExchange()
            {
                return null;
            }

            public boolean isImmediate()
            {
                return immediate;
            }

            public boolean isMandatory()
            {
                return false;
            }

            public AMQShortString getRoutingKey()
            {
                return null;
            }
        };

        ContentHeaderBody contentHeaderBody = new ContentHeaderBody();
        contentHeaderBody.bodySize = size;   // in bytes
        AMQMessage message = new AMQMessage(_messageStore.getNewMessageId(), publish, _transactionalContext);
        message.setContentHeaderBody(contentHeaderBody);
        return message;
    }

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        IApplicationRegistry applicationRegistry = ApplicationRegistry.getInstance();
        _virtualHost = applicationRegistry.getVirtualHostRegistry().getVirtualHost("test");
    }

    private void sendMessages(int messageCount, long size) throws AMQException
    {
        AMQMessage[] messages = new AMQMessage[messageCount];
        for (int i = 0; i < messages.length; i++)
        {
            messages[i] = message(false, size);
            messages[i].enqueue(_queue);
            messages[i].routingComplete(_messageStore, _storeContext, new MessageHandleFactory());
        }

        for (int i = 0; i < messageCount; i++)
        {
            _queue.process(_storeContext, messages[i], false);
        }
    }
}
