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
package org.apache.qpid.server.ack;


import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.protocol.InternalTestProtocolSession;
import org.apache.qpid.server.queue.SimpleAMQQueue;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

import java.util.List;

public class AcknowledgeTest extends QpidTestCase
{
    private AMQChannel _channel;
    private SimpleAMQQueue _queue;
    private MessageStore _messageStore;
    private String _queueName;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _channel = BrokerTestHelper.createChannel();
        VirtualHost virtualHost = _channel.getVirtualHost();
        _queueName = getTestName();
        _queue = BrokerTestHelper.createQueue(_queueName, virtualHost);
        _messageStore = virtualHost.getMessageStore();
        Exchange defaultExchange = virtualHost.getExchangeRegistry().getDefaultExchange();
        virtualHost.getBindingFactory().addBinding(_queueName, _queue, defaultExchange, null);
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

    private SimpleAMQQueue getQueue()
    {
        return _queue;
    }

    public void testTransactionalSingleAck() throws AMQException
    {
        getChannel().setLocalTransactional();
        runMessageAck(1, 1, 1, false, 0);
    }

    public void testTransactionalMultiAck() throws AMQException
    {
        getChannel().setLocalTransactional();
        runMessageAck(10, 1, 5, true, 5);
    }

    public void testTransactionalAckAll() throws AMQException
    {
        getChannel().setLocalTransactional();
        runMessageAck(10, 1, 0, true, 0);
    }

    public void testNonTransactionalSingleAck() throws AMQException
    {
        runMessageAck(1, 1, 1, false, 0);
    }

    public void testNonTransactionalMultiAck() throws AMQException
    {
        runMessageAck(10, 1, 5, true, 5);
    }

    public void testNonTransactionalAckAll() throws AMQException
    {
        runMessageAck(10, 1, 0, true, 0);
    }

    protected void runMessageAck(int sendMessageCount, long firstDeliveryTag, long acknowledgeDeliveryTag, boolean acknowldegeMultiple, int remainingUnackedMessages) throws AMQException
    {
        //Check store is empty
        checkStoreContents(0);

        //Send required messsages to the queue
        BrokerTestHelper.publishMessages(getChannel(), sendMessageCount, _queueName, ExchangeDefaults.DEFAULT_EXCHANGE_NAME.asString());

        if (getChannel().isTransactional())
        {
            getChannel().commit();
        }

        //Ensure they are stored
        checkStoreContents(sendMessageCount);

        //Check that there are no unacked messages
        assertEquals("Channel should have no unacked msgs ", 0, getChannel().getUnacknowledgedMessageMap().size());

        //Subscribe to the queue
        AMQShortString subscriber = _channel.subscribeToQueue(null, _queue, true, null, false, true);

        getQueue().deliverAsync();

        //Wait for the messages to be delivered
        getSession().awaitDelivery(sendMessageCount);

        //Check that they are all waiting to be acknoledged
        assertEquals("Channel should have unacked msgs", sendMessageCount, getChannel().getUnacknowledgedMessageMap().size());

        List<InternalTestProtocolSession.DeliveryPair> messages = getSession().getDelivers(getChannel().getChannelId(), subscriber, sendMessageCount);

        //Double check we received the right number of messages
        assertEquals(sendMessageCount, messages.size());

        //Check that the first message has the expected deliveryTag
        assertEquals("First message does not have expected deliveryTag", firstDeliveryTag, messages.get(0).getDeliveryTag());

        //Send required Acknowledgement
        getChannel().acknowledgeMessage(acknowledgeDeliveryTag, acknowldegeMultiple);

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
        assertEquals("Message header count incorrect in the MetaDataMap", messageCount, ((TestableMemoryMessageStore) _messageStore).getMessageCount());
    }

}
