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

import junit.framework.JUnit4TestAdapter;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.ack.UnacknowledgedMessage;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.util.TestApplicationRegistry;
import org.apache.log4j.Logger;

import java.util.Iterator;
import java.util.Map;

/**
 * Tests that acknowledgements are handled correctly.
 */
public class AckTest
{
    private static final Logger _log = Logger.getLogger(AckTest.class);

    private SubscriptionImpl _subscription;

    private MockProtocolSession _protocolSession;

    private TestableMemoryMessageStore _messageStore;

    private AMQChannel _channel;

    private SubscriptionSet _subscriptionManager;

    private AMQQueue _queue;

    public AckTest() throws Exception
    {
        ApplicationRegistry.initialise(new TestApplicationRegistry());
    }

    @Before
    public void setup() throws Exception
    {
        _messageStore = new TestableMemoryMessageStore();
        _channel = new AMQChannel(5, _messageStore, null/*dont need exchange registry*/);
        _protocolSession = new MockProtocolSession(_messageStore);
        _protocolSession.addChannel(_channel);
        _subscriptionManager = new SubscriptionSet();
        _queue = new AMQQueue("myQ", false, "guest", true, new DefaultQueueRegistry(), _subscriptionManager);
    }

    private void publishMessages(int count) throws AMQException
    {
        for (int i = 1; i <= count; i++)
        {
            BasicPublishBody publishBody = new BasicPublishBody();
            publishBody.routingKey = "rk";
            publishBody.exchange = "someExchange";
            AMQMessage msg = new AMQMessage(_messageStore, publishBody);
            msg.setContentHeaderBody(new ContentHeaderBody());
            _subscription.send(msg, _queue);
        }
    }

    /**
     * Tests that the acknowledgements are correctly associated with a channel and
     * order is preserved when acks are enabled
     */
    @Test @Ignore /* FIXME: broken at the moment */
    public void ackChannelAssociationTest() throws AMQException
    {
        _subscription = new SubscriptionImpl(5, _protocolSession, "conTag", true);
        final int msgCount = 10;
        publishMessages(msgCount);

        Map<Long, UnacknowledgedMessage> map = _channel.getUnacknowledgedMessageMap();
        assertTrue(map.size() == msgCount);

        Iterator<Map.Entry<Long, UnacknowledgedMessage>> it = map.entrySet().iterator();
        for (int i = 1; i <= map.size(); i++)
        {
            Map.Entry<Long, UnacknowledgedMessage> entry = it.next();
            assertTrue(entry.getKey() == i);
            UnacknowledgedMessage unackedMsg = entry.getValue();
            assertTrue(unackedMsg.queue == _queue);
        }
        assertTrue(_messageStore.getMessageMap().size() == msgCount);
    }

    /**
     * Tests that in no-ack mode no messages are retained
     */
    @Test
    public void testNoAckMode() throws AMQException
    {
        // false arg means no acks expected
        _subscription = new SubscriptionImpl(5, _protocolSession, "conTag", false);
        final int msgCount = 10;
        publishMessages(msgCount);

        Map<Long, UnacknowledgedMessage> map = _channel.getUnacknowledgedMessageMap();
        assertTrue(map.size() == 0);
        assertTrue(_messageStore.getMessageMap().size() == 0);
    }

    /**
     * Tests that a single acknowledgement is handled correctly (i.e multiple flag not
     * set case)
     */
    @Test
    public void singleAckReceivedTest() throws AMQException
    {
        _subscription = new SubscriptionImpl(5, _protocolSession, "conTag", true);
        final int msgCount = 10;
        publishMessages(msgCount);

        _channel.acknowledgeMessage(5, false);
        Map<Long, UnacknowledgedMessage> map = _channel.getUnacknowledgedMessageMap();
        assertTrue(map.size() == msgCount - 1);

        Iterator<Map.Entry<Long, UnacknowledgedMessage>> it = map.entrySet().iterator();
        int i = 1;
        while (i <= map.size())
        {
            Map.Entry<Long, UnacknowledgedMessage> entry = it.next();
            assertTrue(entry.getKey() == i);
            UnacknowledgedMessage unackedMsg = entry.getValue();
            assertTrue(unackedMsg.queue == _queue);
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
    @Test
    public void multiAckReceivedTest() throws AMQException
    {
        _subscription = new SubscriptionImpl(5, _protocolSession, "conTag", true);
        final int msgCount = 10;
        publishMessages(msgCount);

        _channel.acknowledgeMessage(5, true);
        Map<Long, UnacknowledgedMessage> map = _channel.getUnacknowledgedMessageMap();
        assertTrue(map.size() == 5);

        Iterator<Map.Entry<Long, UnacknowledgedMessage>> it = map.entrySet().iterator();
        int i = 1;
        while (i <= map.size())
        {
            Map.Entry<Long, UnacknowledgedMessage> entry = it.next();
            assertTrue(entry.getKey() == i + 5);
            UnacknowledgedMessage unackedMsg = entry.getValue();
            assertTrue(unackedMsg.queue == _queue);
            ++i;
        }
    }

     /**
     * Tests that a multiple acknowledgement is handled correctly. When ack'ing all pending msgs.
     *
     */
    @Test
    public void multiAckAllReceivedTest() throws AMQException
    {
        _subscription = new SubscriptionImpl(5, _protocolSession, "conTag", true);
        final int msgCount = 10;
        publishMessages(msgCount);

        _channel.acknowledgeMessage(0, true);
        Map<Long, UnacknowledgedMessage> map = _channel.getUnacknowledgedMessageMap();
        assertTrue(map.size() == 0);

        Iterator<Map.Entry<Long, UnacknowledgedMessage>> it = map.entrySet().iterator();
        int i = 1;
        while (i <= map.size())
        {
            Map.Entry<Long, UnacknowledgedMessage> entry = it.next();
            assertTrue(entry.getKey() == i + 5);
            UnacknowledgedMessage unackedMsg = entry.getValue();
            assertTrue(unackedMsg.queue == _queue);
            ++i;
        }
    }



    @Test
    public void testPrefetch() throws AMQException
    {
        _subscription = new SubscriptionImpl(5, _protocolSession, "conTag", true);
        _channel.setPrefetchCount(5);
        final int msgCount = 5;
        publishMessages(msgCount);

        // at this point we should have sent out only 5 messages with a further 5 queued
        // up in the channel which should be suspended
        assertTrue(_subscription.isSuspended());
        Map<Long, UnacknowledgedMessage> map = _channel.getUnacknowledgedMessageMap();
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
        return new JUnit4TestAdapter(AckTest.class);
    }
}
