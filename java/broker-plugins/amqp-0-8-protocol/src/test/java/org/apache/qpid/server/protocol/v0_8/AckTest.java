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


import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.MessagePublishInfo;
import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.flow.LimitlessCreditManager;
import org.apache.qpid.server.flow.Pre0_10CreditManager;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.TestMemoryMessageStore;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.test.utils.QpidTestCase;

/**
 * Tests that acknowledgements are handled correctly.
 */
public class AckTest extends QpidTestCase
{
    private ConsumerTarget_0_8 _subscriptionTarget;
    private ConsumerImpl _consumer;

    private AMQProtocolEngine _protocolEngine;

    private TestMemoryMessageStore _messageStore;

    private AMQChannel _channel;

    private AMQQueue _queue;

    private static final AMQShortString DEFAULT_CONSUMER_TAG = new AMQShortString("conTag");
    private VirtualHostImpl _virtualHost;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _channel = BrokerTestHelper_0_8.createChannel(5);
        _protocolEngine = _channel.getConnection();
        _virtualHost = _protocolEngine.getVirtualHost();
        _queue = BrokerTestHelper.createQueue(getTestName(), _virtualHost);
        _messageStore = (TestMemoryMessageStore)_virtualHost.getMessageStore();
    }

    @Override
    protected void tearDown() throws Exception
    {
        BrokerTestHelper.tearDown();
        super.tearDown();
    }

    private void publishMessages(int count) throws AMQException
    {
        publishMessages(count, false);
    }

    private void publishMessages(int count, boolean persistent) throws AMQException
    {
        for (int i = 1; i <= count; i++)
        {
            MessagePublishInfo publishBody = new MessagePublishInfo(new AMQShortString("someExchange"), false, false,
                                                                    new AMQShortString("rk"));
            BasicContentHeaderProperties b = new BasicContentHeaderProperties();
            ContentHeaderBody cb = new ContentHeaderBody(b);

            if (persistent)
            {
                //This is DeliveryMode.PERSISTENT
                b.setDeliveryMode((byte) 2);
            }

            // The test is easier to construct if we have direct access to the subscription
            ArrayList<AMQQueue> qs = new ArrayList<AMQQueue>();
            qs.add(_queue);

            final MessageMetaData mmd = new MessageMetaData(publishBody,cb, System.currentTimeMillis());

            final StoredMessage<MessageMetaData> result =_messageStore.addMessage(mmd);

            final StoredMessage storedMessage = result;
            final AMQMessage message = new AMQMessage(storedMessage);
            ServerTransaction txn = new AutoCommitTransaction(_messageStore);
            txn.enqueue(_queue, message,
                        new ServerTransaction.Action()
                        {
                            public void postCommit()
                            {
                                _queue.enqueue(message,null);
                            }

                            public void onRollback()
                            {
                                //To change body of implemented methods use File | Settings | File Templates.
                            }
                        });

        }
        try
        {
            Thread.sleep(2000L);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }

    }

    /**
     * Tests that the acknowledgements are correctly associated with a channel and
     * order is preserved when acks are enabled
     */
    public void testAckChannelAssociationTest() throws Exception
    {
        _subscriptionTarget = ConsumerTarget_0_8.createAckTarget(_channel,
                                                                 DEFAULT_CONSUMER_TAG,
                                                                 null,
                                                                 new LimitlessCreditManager());
        _consumer = _queue.addConsumer(_subscriptionTarget, null, AMQMessage.class, DEFAULT_CONSUMER_TAG.toString(),
                                       EnumSet.of(ConsumerImpl.Option.SEES_REQUEUES,
                                                  ConsumerImpl.Option.ACQUIRES));
        final int msgCount = 10;
        publishMessages(msgCount, true);
        UnacknowledgedMessageMap map = _channel.getUnacknowledgedMessageMap();
        assertEquals("Unexpected size for unacknowledged message map",msgCount,map.size());

        Set<Long> deliveryTagSet = map.getDeliveryTags();
        int i = 1;
        for (long deliveryTag : deliveryTagSet)
        {
            assertTrue(deliveryTag == i);
            i++;
            MessageInstance unackedMsg = map.get(deliveryTag);
            assertTrue(unackedMsg.getOwningResource() == _queue);
        }

    }

    /**
     * Tests that in no-ack mode no messages are retained
     */
    public void testNoAckMode() throws Exception
    {
        // false arg means no acks expected
        _subscriptionTarget = ConsumerTarget_0_8.createNoAckTarget(_channel,
                                                                   DEFAULT_CONSUMER_TAG,
                                                                   null,
                                                                   new LimitlessCreditManager());
        _consumer = _queue.addConsumer(_subscriptionTarget,
                                       null,
                                       AMQMessage.class,
                                       DEFAULT_CONSUMER_TAG.toString(),
                                       EnumSet.of(ConsumerImpl.Option.SEES_REQUEUES,
                                                  ConsumerImpl.Option.ACQUIRES));
        final int msgCount = 10;
        publishMessages(msgCount);
        UnacknowledgedMessageMap map = _channel.getUnacknowledgedMessageMap();
        assertTrue(map.size() == 0);
        assertTrue(_messageStore.getMessageCount() == 0);


    }

    /**
     * Tests that in no-ack mode no messages are retained
     */
    public void testPersistentNoAckMode() throws Exception
    {
        // false arg means no acks expected

        _subscriptionTarget = ConsumerTarget_0_8.createNoAckTarget(_channel,
                                                                   DEFAULT_CONSUMER_TAG,
                                                                   null,
                                                                   new LimitlessCreditManager());
        _consumer = _queue.addConsumer(_subscriptionTarget, null, AMQMessage.class, DEFAULT_CONSUMER_TAG.toString(),
                                       EnumSet.of(ConsumerImpl.Option.SEES_REQUEUES, ConsumerImpl.Option.ACQUIRES));
        final int msgCount = 10;
        publishMessages(msgCount, true);

        UnacknowledgedMessageMap map = _channel.getUnacknowledgedMessageMap();
        assertTrue(map.size() == 0);
        assertTrue(_messageStore.getMessageCount() == 0);


    }

    /**
     * Tests that a single acknowledgement is handled correctly (i.e multiple flag not
     * set case)
     */
    public void testSingleAckReceivedTest() throws Exception
    {

        _subscriptionTarget = ConsumerTarget_0_8.createAckTarget(_channel,
                                                                 DEFAULT_CONSUMER_TAG,
                                                                 null,
                                                                 new LimitlessCreditManager());
        _consumer = _queue.addConsumer(_subscriptionTarget, null, AMQMessage.class, DEFAULT_CONSUMER_TAG.toString(),
                                       EnumSet.of(ConsumerImpl.Option.SEES_REQUEUES,
                                                  ConsumerImpl.Option.ACQUIRES));

        final int msgCount = 10;
        publishMessages(msgCount);

        _channel.acknowledgeMessage(5, false);
        UnacknowledgedMessageMap map = _channel.getUnacknowledgedMessageMap();
        assertEquals("Map not expected size",msgCount - 1,map.size());

        Set<Long> deliveryTagSet = map.getDeliveryTags();
        int i = 1;
        for (long deliveryTag : deliveryTagSet)
        {
            assertTrue(deliveryTag == i);
            MessageInstance unackedMsg = map.get(deliveryTag);
            assertTrue(unackedMsg.getOwningResource() == _queue);
            // 5 is the delivery tag of the message that *should* be removed
            if (++i == 5)
            {
                ++i;
            }
        }
    }

    /**
     * Tests that a single acknowledgement is handled correctly (i.e multiple flag not
     * set case)
     */
    public void testMultiAckReceivedTest() throws Exception
    {

        _subscriptionTarget = ConsumerTarget_0_8.createAckTarget(_channel,
                                                                 DEFAULT_CONSUMER_TAG,
                                                                 null,
                                                                 new LimitlessCreditManager());
        _consumer = _queue.addConsumer(_subscriptionTarget, null, AMQMessage.class, DEFAULT_CONSUMER_TAG.toString(),
                                       EnumSet.of(ConsumerImpl.Option.SEES_REQUEUES,
                                                  ConsumerImpl.Option.ACQUIRES));

        final int msgCount = 10;
        publishMessages(msgCount);



        _channel.acknowledgeMessage(5, true);
        UnacknowledgedMessageMap map = _channel.getUnacknowledgedMessageMap();
        assertTrue(map.size() == 5);

        Set<Long> deliveryTagSet = map.getDeliveryTags();
        int i = 1;
        for (long deliveryTag : deliveryTagSet)
        {
            assertTrue(deliveryTag == i + 5);
            MessageInstance unackedMsg = map.get(deliveryTag);
            assertTrue(unackedMsg.getOwningResource() == _queue);
            ++i;
        }
    }

    /**
     * Tests that a multiple acknowledgement is handled correctly. When ack'ing all pending msgs.
     */
    public void testMultiAckAllReceivedTest() throws Exception
    {

        _subscriptionTarget = ConsumerTarget_0_8.createAckTarget(_channel,
                                                                 DEFAULT_CONSUMER_TAG,
                                                                 null,
                                                                 new LimitlessCreditManager());
        _consumer = _queue.addConsumer(_subscriptionTarget, null, AMQMessage.class, DEFAULT_CONSUMER_TAG.toString(),
                                       EnumSet.of(ConsumerImpl.Option.SEES_REQUEUES,
                                                  ConsumerImpl.Option.ACQUIRES));

        final int msgCount = 10;
        publishMessages(msgCount);

        _channel.acknowledgeMessage(0, true);
        UnacknowledgedMessageMap map = _channel.getUnacknowledgedMessageMap();
        assertTrue(map.size() == 0);

        Set<Long> deliveryTagSet = map.getDeliveryTags();
        int i = 1;
        for (long deliveryTag : deliveryTagSet)
        {
            assertTrue(deliveryTag == i + 5);
            MessageInstance unackedMsg = map.get(deliveryTag);
            assertTrue(unackedMsg.getOwningResource() == _queue);
            ++i;
        }
    }

            /**
     * A regression fixing QPID-1136 showed this up
     *
     * @throws Exception
     */
    public void testMessageDequeueRestoresCreditTest() throws Exception
    {
        // Send 10 messages
        Pre0_10CreditManager creditManager = new Pre0_10CreditManager(0l, 1);


        _subscriptionTarget = ConsumerTarget_0_8.createAckTarget(_channel, DEFAULT_CONSUMER_TAG, null, creditManager);
        _consumer = _queue.addConsumer(_subscriptionTarget, null, AMQMessage.class, DEFAULT_CONSUMER_TAG.toString(),
                                       EnumSet.of(ConsumerImpl.Option.SEES_REQUEUES,
                                                  ConsumerImpl.Option.ACQUIRES));

        final int msgCount = 1;
        publishMessages(msgCount);

        _consumer.externalStateChange();

        _channel.acknowledgeMessage(1, false);

        // Check credit available
        assertTrue("No credit available", creditManager.hasCredit());

    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(AckTest.class);
    }
}
