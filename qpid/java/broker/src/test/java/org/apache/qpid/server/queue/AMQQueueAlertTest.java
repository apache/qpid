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

import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.message.AMQMessage;
import org.apache.qpid.server.message.MessageMetaData;
import org.apache.qpid.server.protocol.InternalTestProtocolSession;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.subscription.SubscriptionFactoryImpl;
import org.apache.qpid.server.util.InternalBrokerBaseCase;

import javax.management.Notification;
import java.util.ArrayList;

/** This class tests all the alerts an AMQQueue can throw based on threshold values of different parameters */
public class AMQQueueAlertTest extends InternalBrokerBaseCase
{
    private final static long MAX_MESSAGE_COUNT = 50;
    private final static long MAX_MESSAGE_AGE = 250;   // 0.25 sec
    private final static long MAX_MESSAGE_SIZE = 2000;  // 2 KB
    private final static long MAX_QUEUE_DEPTH = 10000;  // 10 KB
    private AMQQueueMBean _queueMBean;
    private static final SubscriptionFactoryImpl SUBSCRIPTION_FACTORY = SubscriptionFactoryImpl.INSTANCE;

    /**
     * Tests if the alert gets thrown when message count increases the threshold limit
     *
     * @throws Exception
     */
    public void testMessageCountAlert() throws Exception
    {
        setSession(new InternalTestProtocolSession(getVirtualHost()));
        AMQChannel channel = new AMQChannel(getSession(), 2, getMessageStore());
        getSession().addChannel(channel);

        setQueue(AMQQueueFactory.createAMQQueueImpl(new AMQShortString("testQueue1"), false, new AMQShortString("AMQueueAlertTest"),
                              false, false,
                getVirtualHost(), null));
        _queueMBean = (AMQQueueMBean) getQueue().getManagedObject();

        _queueMBean.setMaximumMessageCount(MAX_MESSAGE_COUNT);

        sendMessages(channel, MAX_MESSAGE_COUNT, 256l);
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
        setSession(new InternalTestProtocolSession(getVirtualHost()));
        AMQChannel channel = new AMQChannel(getSession(), 2, getMessageStore());
        getSession().addChannel(channel);

        setQueue(AMQQueueFactory.createAMQQueueImpl(new AMQShortString("testQueue2"), false, new AMQShortString("AMQueueAlertTest"),
                              false, false,
                getVirtualHost(), null));
        _queueMBean = (AMQQueueMBean) getQueue().getManagedObject();
        _queueMBean.setMaximumMessageCount(MAX_MESSAGE_COUNT);
        _queueMBean.setMaximumMessageSize(MAX_MESSAGE_SIZE);

        sendMessages(channel, 1, MAX_MESSAGE_SIZE * 2);
        assertTrue(_queueMBean.getMessageCount() == 1);

        Notification lastNotification = _queueMBean.getLastNotification();
        assertNotNull(lastNotification);

        String notificationMsg = lastNotification.getMessage();
        assertTrue(notificationMsg.startsWith(NotificationCheck.MESSAGE_SIZE_ALERT.name()));
    }

    /**
     * Tests if Queue Depth alert is thrown when queue depth reaches the threshold value
     *
     * Based on FT-402 subbmitted by client
     *
     * @throws Exception
     */
    public void testQueueDepthAlertNoSubscriber() throws Exception
    {
        setSession(new InternalTestProtocolSession(getVirtualHost()));
        AMQChannel channel = new AMQChannel(getSession(), 2, getMessageStore());
        getSession().addChannel(channel);

        setQueue(AMQQueueFactory.createAMQQueueImpl(new AMQShortString("testQueue3"), false, new AMQShortString("AMQueueAlertTest"),
                              false, false,
                getVirtualHost(), null));
        _queueMBean = (AMQQueueMBean) getQueue().getManagedObject();
        _queueMBean.setMaximumMessageCount(MAX_MESSAGE_COUNT);
        _queueMBean.setMaximumQueueDepth(MAX_QUEUE_DEPTH);

        while (getQueue().getQueueDepth() < MAX_QUEUE_DEPTH)
        {
            sendMessages(channel, 1, MAX_MESSAGE_SIZE);
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
     * Alternative test to FT-401 provided by client
     *
     * @throws Exception
     */
    public void testMessageAgeAlert() throws Exception
    {
        setSession(new InternalTestProtocolSession(getVirtualHost()));
        AMQChannel channel = new AMQChannel(getSession(), 2, getMessageStore());
        getSession().addChannel(channel);

        setQueue(AMQQueueFactory.createAMQQueueImpl(new AMQShortString("testQueue4"), false, new AMQShortString("AMQueueAlertTest"),
                              false, false,
                getVirtualHost(), null));
        _queueMBean = (AMQQueueMBean) getQueue().getManagedObject();
        _queueMBean.setMaximumMessageCount(MAX_MESSAGE_COUNT);
        _queueMBean.setMaximumMessageAge(MAX_MESSAGE_AGE);

        sendMessages(channel, 1, MAX_MESSAGE_SIZE);

        // Ensure message sits on queue long enough to age.
        Thread.sleep(MAX_MESSAGE_AGE * 2);

        Notification lastNotification = _queueMBean.getLastNotification();
        assertNotNull("Last notification was null", lastNotification);

        String notificationMsg = lastNotification.getMessage();
        assertTrue(notificationMsg.startsWith(NotificationCheck.MESSAGE_AGE_ALERT.name()));
    }

    /*
     This test sends some messages to the queue with subscribers needing message to be acknowledged.
     The messages will not be acknowledged and will be required twice. Why we are checking this is because
     the bug reported said that the queueDepth keeps increasing when messages are requeued.
     // TODO - queue depth now includes unacknowledged messages so does not go down when messages are delivered

     The QueueDepth should decrease when messages are delivered from the queue (QPID-408)
    */
    public void testQueueDepthAlertWithSubscribers() throws Exception
    {
        AMQChannel channel = new AMQChannel(getSession(), 2, getMessageStore());
        getSession().addChannel(channel);

        // Create queue
        setQueue(getNewQueue());
        Subscription subscription =
                SUBSCRIPTION_FACTORY.createSubscription(channel.getChannelId(), getSession(), new AMQShortString("consumer_tag"), true, null, false, channel.getCreditManager());

        getQueue().registerSubscription(
                subscription, false);

        _queueMBean = (AMQQueueMBean) getQueue().getManagedObject();
        _queueMBean.setMaximumMessageCount(9999l);   // Set a high value, because this is not being tested
        _queueMBean.setMaximumQueueDepth(MAX_QUEUE_DEPTH);

        // Send messages(no of message to be little more than what can cause a Queue_Depth alert)
        int messageCount = Math.round(MAX_QUEUE_DEPTH / MAX_MESSAGE_SIZE) + 10;
        long totalSize = (messageCount * MAX_MESSAGE_SIZE);
        sendMessages(channel, messageCount, MAX_MESSAGE_SIZE);

        // Check queueDepth. There should be no messages on the queue and as the subscriber is listening
        // so there should be no Queue_Deoth alert raised
        assertEquals(new Long(totalSize), new Long(_queueMBean.getQueueDepth()));
        Notification lastNotification = _queueMBean.getLastNotification();
//        assertNull(lastNotification);

        // Kill the subscriber and check for the queue depth values.
        // Messages are unacknowledged, so those should get requeued. All messages should be on the Queue
        getQueue().unregisterSubscription(subscription);
        channel.requeue();

        assertEquals(new Long(totalSize), new Long(_queueMBean.getQueueDepth()));

        lastNotification = _queueMBean.getLastNotification();
        assertNotNull(lastNotification);
        String notificationMsg = lastNotification.getMessage();
        assertTrue(notificationMsg.startsWith(NotificationCheck.QUEUE_DEPTH_ALERT.name()));

        // Connect a consumer again and check QueueDepth values. The queue should get emptied.
        // Messages will get delivered but still are unacknowledged.
        Subscription subscription2 =
                SUBSCRIPTION_FACTORY.createSubscription(channel.getChannelId(), getSession(), new AMQShortString("consumer_tag"), true, null, false, channel.getCreditManager());

        getQueue().registerSubscription(
                subscription2, false);

        while (getQueue().getUndeliveredMessageCount()!= 0)
        {
            Thread.sleep(100);
        }
//        assertEquals(new Long(0), new Long(_queueMBean.getQueueDepth()));

        // Kill the subscriber again. Now those messages should get requeued again. Check if the queue depth
        // value is correct.
        getQueue().unregisterSubscription(subscription2);
        channel.requeue();

        assertEquals(new Long(totalSize), new Long(_queueMBean.getQueueDepth()));
        getSession().closeSession();

        // Check the clear queue
        _queueMBean.clearQueue();
        assertEquals(new Long(0), new Long(_queueMBean.getQueueDepth()));
    }

    protected IncomingMessage message(final boolean immediate, long size) throws AMQException
    {
        MessagePublishInfo publish = new MessagePublishInfo()
        {

            public AMQShortString getExchange()
            {
                return null;
            }

            public void setExchange(AMQShortString exchange)
            {
                //To change body of implemented methods use File | Settings | File Templates.
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
        BasicContentHeaderProperties props = new BasicContentHeaderProperties();
        contentHeaderBody.setProperties(props);
        contentHeaderBody.bodySize = size;   // in bytes
        IncomingMessage message = new IncomingMessage(publish);
        message.setContentHeaderBody(contentHeaderBody);

        return message;
    }

    @Override
    protected void configure()
    {
        // Increase Alert Check period
        getConfiguration().setHousekeepingExpiredMessageCheckPeriod(200);
    }

    private void sendMessages(AMQChannel channel, long messageCount, final long size) throws AMQException
    {
        IncomingMessage[] messages = new IncomingMessage[(int) messageCount];
        MessageMetaData[] metaData = new MessageMetaData[(int) messageCount];
        for (int i = 0; i < messages.length; i++)
        {
            messages[i] = message(false, size);
            ArrayList<AMQQueue> qs = new ArrayList<AMQQueue>();
            qs.add(getQueue());
            metaData[i] = messages[i].headersReceived();
            messages[i].setStoredMessage(getMessageStore().addMessage(metaData[i]));

            messages[i].enqueue(qs);

        }

        for (int i = 0; i < messageCount; i++)
        {
            messages[i].addContentBodyFrame(new ContentChunk(){

                ByteBuffer _data = ByteBuffer.allocate((int)size);

                {
                    _data.limit((int)size);
                }

                public int getSize()
                {
                    return (int) size;
                }

                public ByteBuffer getData()
                {
                    return _data;
                }

                public void reduceToFit()
                {

                }
            });

            getQueue().enqueue(new AMQMessage(messages[i].getStoredMessage()));

        }
    }

    private AMQQueue getNewQueue() throws AMQException
    {
        return AMQQueueFactory.createAMQQueueImpl(new AMQShortString("testQueue" + Math.random()),
                            false,
                            new AMQShortString("AMQueueAlertTest"),
                            false,
                            false, getVirtualHost(), null);
    }
}
