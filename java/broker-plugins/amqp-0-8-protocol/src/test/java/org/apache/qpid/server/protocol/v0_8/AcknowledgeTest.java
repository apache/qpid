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

import java.util.Collections;
import java.util.List;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.MessageCounter;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.test.utils.QpidTestCase;

public class AcknowledgeTest extends QpidTestCase
{
    private AMQChannel _channel;
    private AMQQueue _queue;
    private MessageStore _messageStore;
    private String _queueName;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _channel = BrokerTestHelper_0_8.createChannel();
        VirtualHostImpl virtualHost = _channel.getVirtualHost();
        _queueName = getTestName();
        _queue = BrokerTestHelper.createQueue(_queueName, virtualHost);
        _messageStore = virtualHost.getMessageStore();
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            if (_channel != null)
            {
                _channel.getVirtualHost().close();
            }
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
        }
    }

    private AMQChannel getChannel()
    {
        return _channel;
    }

    private InternalTestProtocolSession getSession()
    {
        return (InternalTestProtocolSession)_channel.getProtocolSession();
    }

    private AMQQueue getQueue()
    {
        return _queue;
    }

    public void testTransactionalSingleAck() throws Exception
    {
        getChannel().setLocalTransactional();
        runMessageAck(1, 1, 1, false, 0);
    }

    public void testTransactionalMultiAck() throws Exception
    {
        getChannel().setLocalTransactional();
        runMessageAck(10, 1, 5, true, 5);
    }

    public void testTransactionalAckAll() throws Exception
    {
        getChannel().setLocalTransactional();
        runMessageAck(10, 1, 0, true, 0);
    }

    public void testNonTransactionalSingleAck() throws Exception
    {
        runMessageAck(1, 1, 1, false, 0);
    }

    public void testNonTransactionalMultiAck() throws Exception
    {
        runMessageAck(10, 1, 5, true, 5);
    }

    public void testNonTransactionalAckAll() throws Exception
    {
        runMessageAck(10, 1, 0, true, 0);
    }

    protected void runMessageAck(int sendMessageCount, long firstDeliveryTag, long acknowledgeDeliveryTag, boolean acknowledgeMultiple, int remainingUnackedMessages) throws Exception
    {
        //Check store is empty
        checkStoreContents(0);

        //Send required messages to the queue
        BrokerTestHelper_0_8.publishMessages(getChannel(),
                sendMessageCount,
                _queueName,
                ExchangeDefaults.DEFAULT_EXCHANGE_NAME);

        if (getChannel().isTransactional())
        {
            getChannel().commit();
        }

        //Ensure they are stored
        checkStoreContents(sendMessageCount);

        //Check that there are no unacked messages
        assertEquals("Channel should have no unacked msgs ", 0, getChannel().getUnacknowledgedMessageMap().size());

        //Subscribe to the queue
        AMQShortString subscriber = _channel.consumeFromSource(null,
                                                               Collections.singleton(_queue),
                                                               true, null, true, false);

        getQueue().deliverAsync();

        //Wait for the messages to be delivered
        getSession().awaitDelivery(sendMessageCount);

        //Check that they are all waiting to be acknowledged
        assertEquals("Channel should have unacked msgs", sendMessageCount, getChannel().getUnacknowledgedMessageMap().size());

        List<InternalTestProtocolSession.DeliveryPair> messages = getSession().getDelivers(getChannel().getChannelId(), subscriber, sendMessageCount);

        //Double check we received the right number of messages
        assertEquals(sendMessageCount, messages.size());

        //Check that the first message has the expected deliveryTag
        assertEquals("First message does not have expected deliveryTag", firstDeliveryTag, messages.get(0).getDeliveryTag());

        //Send required Acknowledgement
        getChannel().acknowledgeMessage(acknowledgeDeliveryTag, acknowledgeMultiple);

        if (getChannel().isTransactional())
        {
            getChannel().commit();
        }

        // Check Remaining Acknowledgements
        assertEquals("Channel unacked msgs count incorrect", remainingUnackedMessages, getChannel().getUnacknowledgedMessageMap().size());

        //Check store contents are also correct.
        checkStoreContents(remainingUnackedMessages);
    }

    private void checkStoreContents(int messageCount)
    {
        MessageCounter counter = new MessageCounter();
        _messageStore.visitMessages(counter);
        assertEquals("Message header count incorrect in the MetaDataMap", messageCount, counter.getCount());
    }

}
