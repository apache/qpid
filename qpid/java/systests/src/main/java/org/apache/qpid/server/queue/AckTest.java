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
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.server.ack.UnacknowledgedMessage;
import org.apache.qpid.server.ack.UnacknowledgedMessageMap;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.txn.MemoryTransactionManager;
import org.apache.qpid.server.txn.NonTransactionalContext;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.util.NullApplicationRegistry;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * Tests that acknowledgements are handled correctly.
 */
public class AckTest extends TestCase
{
    private static final Logger _log = Logger.getLogger(AckTest.class);

    private SubscriptionImpl _subscription;

    private MockProtocolSession _protocolSession;

    private TestableMemoryMessageStore _messageStore;

    private MemoryTransactionManager _txm;

    private StoreContext _storeContext = new StoreContext();

    private AMQChannel _channel;

    private SubscriptionSet _subscriptionManager;

    private AMQQueue _queue;

    private static final AMQShortString DEFAULT_CONSUMER_TAG = new AMQShortString("conTag");

    public AckTest() throws Exception
    {
        ApplicationRegistry.initialise(new NullApplicationRegistry());
    }

    protected void setUp() throws Exception
    {
        super.setUp();
        _messageStore = new TestableMemoryMessageStore();
        _txm = new MemoryTransactionManager();
        _protocolSession = new MockProtocolSession(_messageStore);
        _channel = new AMQChannel(_protocolSession, 5, _txm, _messageStore);

        _protocolSession.addChannel(_channel);
        _subscriptionManager = new SubscriptionSet();
        _queue = new AMQQueue(new AMQShortString("myQ"), false, new AMQShortString("guest"), true, ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost("test"), _subscriptionManager);
    }

    private void publishMessages(int count) throws AMQException
    {
        publishMessages(count, false);
    }

    private void publishMessages(int count, boolean persistent) throws AMQException
    {
        TransactionalContext txnContext = new NonTransactionalContext(_messageStore, _storeContext, null,
                                                                      new LinkedList<RequiredDeliveryException>(),
                                                                      new HashSet<Long>());
        MessageHandleFactory factory = new MessageHandleFactory();
        for (int i = 1; i <= count; i++)
        {
            // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
            // TODO: Establish some way to determine the version for the test.
            MessagePublishInfo publishBody = new MessagePublishInfo()
            {

                public AMQShortString getExchange()
                {
                    return new AMQShortString("someExchange");
                }

                public void setExchange(AMQShortString exchange)
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean isImmediate()
                {
                    return false;
                }

                public boolean isMandatory()
                {
                    return false;
                }

                public AMQShortString getRoutingKey()
                {
                    return new AMQShortString("rk");
                }
            };
            AMQMessage msg = new AMQMessage(_messageStore.getNewMessageId(), publishBody, txnContext);
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
            msg.incrementReference();
            msg.routingComplete(_messageStore, _storeContext, factory);
            // we manually send the message to the subscription
            _subscription.send(new QueueEntry(_queue,msg), _queue);
        }
    }

    /**
     * Tests that the acknowledgements are correctly associated with a channel and
     * order is preserved when acks are enabled
     */
    public void testAckChannelAssociationTest() throws AMQException
    {
        _subscription = new SubscriptionImpl(5, _protocolSession, DEFAULT_CONSUMER_TAG, true);
        final int msgCount = 10;
        publishMessages(msgCount, true);

        UnacknowledgedMessageMap map = _channel.getUnacknowledgedMessageMap();
        assertTrue(map.size() == msgCount);
        assertTrue(_messageStore.getMessageMetaDataMap().size() == msgCount);
        
        //DTX
        //       assertTrue(_messageStore.getNumberStoredMessages() == msgCount);

        Set<Long> deliveryTagSet = map.getDeliveryTags();
        int i = 1;
        for (long deliveryTag : deliveryTagSet)
        {
            assertTrue(deliveryTag == i);
            i++;
            UnacknowledgedMessage unackedMsg = map.get(deliveryTag);
            assertTrue(unackedMsg.getQueue() == _queue);
        }

        assertTrue(map.size() == msgCount);
        assertTrue(_messageStore.getMessageMetaDataMap().size() == msgCount);
        
        //DTX
//        assertTrue(_messageStore.getNumberStoredMessages() == msgCount);
    }

    /**
     * Tests that in no-ack mode no messages are retained
     */
    public void testNoAckMode() throws AMQException
    {
        // false arg means no acks expected
        _subscription = new SubscriptionImpl(5, _protocolSession, DEFAULT_CONSUMER_TAG, false);
        final int msgCount = 10;
        publishMessages(msgCount);

        UnacknowledgedMessageMap map = _channel.getUnacknowledgedMessageMap();
        assertTrue(map.size() == 0);
        assertTrue(_messageStore.getMessageMetaDataMap().size() == 0);
        //DTX MessageStore
//        assertTrue(_messageStore.getNumberStoredMessages() == 0);
    }

    /**
     * Tests that a single acknowledgement is handled correctly (i.e multiple flag not
     * set case)
     */
    public void testSingleAckReceivedTest() throws AMQException
    {
        _subscription = new SubscriptionImpl(5, _protocolSession, DEFAULT_CONSUMER_TAG, true);
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
            UnacknowledgedMessage unackedMsg = map.get(deliveryTag);
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
        _subscription = new SubscriptionImpl(5, _protocolSession, DEFAULT_CONSUMER_TAG, true);
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
            UnacknowledgedMessage unackedMsg = map.get(deliveryTag);
            assertTrue(unackedMsg.getQueue() == _queue);
            ++i;
        }
    }

    /**
     * Tests that a multiple acknowledgement is handled correctly. When ack'ing all pending msgs.
     */
    public void testMultiAckAllReceivedTest() throws AMQException
    {
        _subscription = new SubscriptionImpl(5, _protocolSession, DEFAULT_CONSUMER_TAG, true);
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
            UnacknowledgedMessage unackedMsg = map.get(deliveryTag);
            assertTrue(unackedMsg.getQueue() == _queue);
            ++i;
        }
    }

    public void testPrefetchHighLow() throws AMQException
    {
        int lowMark = 5;
        int highMark = 10;

        _subscription = new SubscriptionImpl(5, _protocolSession, DEFAULT_CONSUMER_TAG, true);
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

    public void testPrefetch() throws AMQException
    {
        _subscription = new SubscriptionImpl(5, _protocolSession, DEFAULT_CONSUMER_TAG, true);
        _channel.setPrefetchCount(5);

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

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(AckTest.class);
    }
}
