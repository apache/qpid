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
package org.apache.qpid.server.subscription;

import org.apache.qpid.AMQException;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.protocol.InternalTestProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.SimpleAMQQueue;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

import java.util.List;

public class QueueBrowserUsesNoAckTest extends QpidTestCase
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

    public void testQueueBrowserUsesNoAck() throws AMQException
    {
        int sendMessageCount = 2;
        int prefetch = 1;

        //Check store is empty
        checkStoreContents(0);

        //Send required messsages to the queue
        BrokerTestHelper.publishMessages(getChannel(), sendMessageCount, _queueName, ExchangeDefaults.DEFAULT_EXCHANGE_NAME.asString());

        //Ensure they are stored
        checkStoreContents(sendMessageCount);

        //Check that there are no unacked messages
        assertEquals("Channel should have no unacked msgs ", 0,
                     getChannel().getUnacknowledgedMessageMap().size());

        //Set the prefetch on the session to be less than the sent messages
        getChannel().setCredit(0, prefetch);

        //browse the queue
        AMQShortString browser = browse(getChannel(), getQueue());

        getQueue().deliverAsync();

        //Wait for messages to fill the prefetch
        getSession().awaitDelivery(prefetch);

        //Get those messages
        List<InternalTestProtocolSession.DeliveryPair> messages =
                getSession().getDelivers(getChannel().getChannelId(), browser,
                                     prefetch);

        //Ensure we recevied the prefetched messages
        assertEquals(prefetch, messages.size());

        //Check the process didn't suspend the subscription as this would
        // indicate we are using the prefetch credit. i.e. using acks not No-Ack
        assertTrue("The subscription has been suspended",
                   !getChannel().getSubscription(browser).getState()
                           .equals(Subscription.State.SUSPENDED));       
    }

    private void checkStoreContents(int messageCount)
    {
        assertEquals("Message header count incorrect in the MetaDataMap", messageCount, ((TestableMemoryMessageStore) _messageStore).getMessageCount());
    }

    private AMQShortString browse(AMQChannel channel, AMQQueue queue) throws AMQException
    {
        FieldTable filters = new FieldTable();
        filters.put(AMQPFilterTypes.NO_CONSUME.getValue(), true);

        return channel.subscribeToQueue(null, queue, true, filters, false, true);
    }
}
