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

import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.MessagePublishInfoImpl;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.subscription.SubscriptionFactoryImpl;
import org.apache.qpid.server.flow.LimitlessCreditManager;
import org.apache.qpid.server.flow.Pre0_10CreditManager;
import org.apache.qpid.server.ack.UnacknowledgedMessageMap;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.TestMemoryMessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.txn.NonTransactionalContext;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.util.NullApplicationRegistry;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Set;

/**
 * Tests that acknowledgements are handled correctly.
 */
public class AckTest extends TestCase
{
    private static final Logger _log = Logger.getLogger(AckTest.class);

    private Subscription _subscription;

    private MockProtocolSession _protocolSession;

    private TestMemoryMessageStore _messageStore;

    private StoreContext _storeContext = new StoreContext();

    private AMQChannel _channel;

    private AMQQueue _queue;

    private static final AMQShortString DEFAULT_CONSUMER_TAG = new AMQShortString("conTag");

    protected void setUp() throws Exception
    {
        super.setUp();
        ApplicationRegistry.initialise(new NullApplicationRegistry(), 1);

        _messageStore = new TestMemoryMessageStore();
        _protocolSession = new MockProtocolSession(_messageStore);
        _channel = new AMQChannel(_protocolSession,5, _messageStore /*dont need exchange registry*/);

        _protocolSession.addChannel(_channel);

        _queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("myQ"), false, new AMQShortString("guest"), true, ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost("test"),
                                                    null);
    }

    protected void tearDown()
    {
        ApplicationRegistry.remove(1);
    }

    private void publishMessages(int count) throws AMQException
    {
        publishMessages(count, false);
    }

    private void publishMessages(int count, boolean persistent) throws AMQException
    {
        TransactionalContext txnContext = new NonTransactionalContext(_messageStore, _storeContext, null,
                                                                      new LinkedList<RequiredDeliveryException>()
        );
        _queue.registerSubscription(_subscription,false);
        MessageFactory factory = MessageFactory.getInstance();
        for (int i = 1; i <= count; i++)
        {
            // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
            // TODO: Establish some way to determine the version for the test.
            MessagePublishInfo publishBody = new MessagePublishInfoImpl(new AMQShortString("someExchange"), false,
                                                                        false, new AMQShortString("rk"));

            IncomingMessage msg = new IncomingMessage(publishBody, txnContext,_protocolSession, _messageStore);
            //IncomingMessage msg2 = null;
            if (persistent)
            {
                BasicContentHeaderProperties b = new BasicContentHeaderProperties();
                //This is DeliveryMode.PERSISTENT
                b.setDeliveryMode((byte) 2);
                ContentHeaderBody cb = new ContentHeaderBody();
                cb.properties = b;
                msg.setContentHeaderBody(cb);
            }
            else
            {
                msg.setContentHeaderBody(new ContentHeaderBody());
            }
            // we increment the reference here since we are not delivering the messaging to any queues, which is where
            // the reference is normally incremented. The test is easier to construct if we have direct access to the
            // subscription
            ArrayList<AMQQueue> qs = new ArrayList<AMQQueue>();
            qs.add(_queue);
            msg.enqueue(qs);
            msg.routingComplete(_messageStore);
            if(msg.allContentReceived())
            {
                msg.deliverToQueues();
            }
            // we manually send the message to the subscription
            //_subscription.send(new QueueEntry(_queue,msg), _queue);
        }
    }

    /**
     * Tests that the acknowledgements are correctly associated with a channel and
     * order is preserved when acks are enabled
     */
    public void testAckChannelAssociationTest() throws AMQException
    {
        _subscription = SubscriptionFactoryImpl.INSTANCE.createSubscription(5, _protocolSession, DEFAULT_CONSUMER_TAG, true, null, false, new LimitlessCreditManager());
        final int msgCount = 10;
        publishMessages(msgCount, true);

        UnacknowledgedMessageMap map = _channel.getUnacknowledgedMessageMap();
        assertTrue(map.size() == msgCount);
        assertTrue(_messageStore.getMessageMetaDataMap().size() == msgCount);

        Set<Long> deliveryTagSet = map.getDeliveryTags();
        int i = 1;
        for (long deliveryTag : deliveryTagSet)
        {
            assertTrue(deliveryTag == i);
            i++;
            QueueEntry unackedMsg = map.get(deliveryTag);
            assertTrue(unackedMsg.getQueue() == _queue);
        }

        assertTrue(map.size() == msgCount);
        assertTrue(_messageStore.getMessageMetaDataMap().size() == msgCount);
    }

    /**
     * Tests that in no-ack mode no messages are retained
     */
    public void testNoAckMode() throws AMQException
    {
        // false arg means no acks expected
        _subscription = SubscriptionFactoryImpl.INSTANCE.createSubscription(5, _protocolSession, DEFAULT_CONSUMER_TAG, false, null, false, new LimitlessCreditManager());
        final int msgCount = 10;
        publishMessages(msgCount);

        UnacknowledgedMessageMap map = _channel.getUnacknowledgedMessageMap();
        assertTrue(map.size() == 0);
        assertTrue(_messageStore.getMessageMetaDataMap().size() == 0);
        assertTrue(_messageStore.getContentBodyMap().size() == 0);

    }

    /**
     * Tests that in no-ack mode no messages are retained
     */
    public void testPersistentNoAckMode() throws AMQException
    {
        // false arg means no acks expected
        _subscription = SubscriptionFactoryImpl.INSTANCE.createSubscription(5, _protocolSession, DEFAULT_CONSUMER_TAG, false,null,false, new LimitlessCreditManager());
        final int msgCount = 10;
        publishMessages(msgCount, true);

        UnacknowledgedMessageMap map = _channel.getUnacknowledgedMessageMap();
        assertTrue(map.size() == 0);
        assertTrue(_messageStore.getMessageMetaDataMap().size() == 0);
        assertTrue(_messageStore.getContentBodyMap().size() == 0);

    }

    /**
     * Tests that a single acknowledgement is handled correctly (i.e multiple flag not
     * set case)
     */
    public void testSingleAckReceivedTest() throws AMQException
    {
        _subscription = SubscriptionFactoryImpl.INSTANCE.createSubscription(5, _protocolSession, DEFAULT_CONSUMER_TAG, true,null,false, new LimitlessCreditManager());
        final int msgCount = 10;
        publishMessages(msgCount);

        _channel.acknowledgeMessage(5, false);
        UnacknowledgedMessageMap map = _channel.getUnacknowledgedMessageMap();
        assertTrue(map.size() == msgCount - 1);

        Set<Long> deliveryTagSet = map.getDeliveryTags();
        int i = 1;
        for (long deliveryTag : deliveryTagSet)
        {
            assertTrue(deliveryTag == i);
            QueueEntry unackedMsg = map.get(deliveryTag);
            assertTrue(unackedMsg.getQueue() == _queue);
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
    public void testMultiAckReceivedTest() throws AMQException
    {
        _subscription = SubscriptionFactoryImpl.INSTANCE.createSubscription(5, _protocolSession, DEFAULT_CONSUMER_TAG, true,null,false, new LimitlessCreditManager());
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
            QueueEntry unackedMsg = map.get(deliveryTag);
            assertTrue(unackedMsg.getQueue() == _queue);
            ++i;
        }
    }

    /**
     * Tests that a multiple acknowledgement is handled correctly. When ack'ing all pending msgs.
     */
    public void testMultiAckAllReceivedTest() throws AMQException
    {
        _subscription = SubscriptionFactoryImpl.INSTANCE.createSubscription(5, _protocolSession, DEFAULT_CONSUMER_TAG, true,null,false, new LimitlessCreditManager());
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
            QueueEntry unackedMsg = map.get(deliveryTag);
            assertTrue(unackedMsg.getQueue() == _queue);
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

        _subscription = SubscriptionFactoryImpl.INSTANCE.createSubscription(5, _protocolSession,
                                                                            DEFAULT_CONSUMER_TAG, true, null, false, creditManager);
        final int msgCount = 1;
        publishMessages(msgCount);

        _queue.deliverAsync(_subscription);

        _channel.acknowledgeMessage(1, false);

        // Check credit available
        assertTrue("No credit available", creditManager.hasCredit());

    }


/*
    public void testPrefetchHighLow() throws AMQException
    {
        int lowMark = 5;
        int highMark = 10;

        _subscription = SubscriptionFactoryImpl.INSTANCE.createSubscription(5, _protocolSession, DEFAULT_CONSUMER_TAG, true,null,false, new LimitlessCreditManager());
        _channel.setPrefetchLowMarkCount(lowMark);
        _channel.setPrefetchHighMarkCount(highMark);

        assertTrue(_channel.getPrefetchLowMarkCount() == lowMark);
        assertTrue(_channel.getPrefetchHighMarkCount() == highMark);

        publishMessages(highMark);

        // at this point we should have sent out only highMark messages
        // which have not bee received so will be queued up in the channel
        // which should be suspended
        assertTrue(_subscription.isSuspended());
        UnacknowledgedMessageMap map = _channel.getUnacknowledgedMessageMap();
        assertTrue(map.size() == highMark);

        //acknowledge messages so we are just above lowMark
        _channel.acknowledgeMessage(lowMark - 1, true);

        //we should still be suspended
        assertTrue(_subscription.isSuspended());
        assertTrue(map.size() == lowMark + 1);

        //acknowledge one more message
        _channel.acknowledgeMessage(lowMark, true);

        //and suspension should be lifted
        assertTrue(!_subscription.isSuspended());

        //pubilsh more msgs so we are just below the limit
        publishMessages(lowMark - 1);

        //we should not be suspended
        assertTrue(!_subscription.isSuspended());

        //acknowledge all messages
        _channel.acknowledgeMessage(0, true);
        try
        {
            Thread.sleep(3000);
        }
        catch (InterruptedException e)
        {
            _log.error("Error: " + e, e);
        }
        //map will be empty
        assertTrue(map.size() == 0);
    }

*/
/*
    public void testPrefetch() throws AMQException
    {
        _subscription = SubscriptionFactoryImpl.INSTANCE.createSubscription(5, _protocolSession, DEFAULT_CONSUMER_TAG, true,null,false, new LimitlessCreditManager());
        _channel.setMessageCredit(5);

        assertTrue(_channel.getPrefetchCount() == 5);

        final int msgCount = 5;
        publishMessages(msgCount);

        // at this point we should have sent out only 5 messages with a further 5 queued
        // up in the channel which should now be suspended
        assertTrue(_subscription.isSuspended());
        UnacknowledgedMessageMap map = _channel.getUnacknowledgedMessageMap();
        assertTrue(map.size() == 5);
        _channel.acknowledgeMessage(5, true);
        assertTrue(!_subscription.isSuspended());
        try
        {
            Thread.sleep(3000);
        }
        catch (InterruptedException e)
        {
            _log.error("Error: " + e, e);
        }
        assertTrue(map.size() == 0);
    }

*/
    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(AckTest.class);
    }
}
