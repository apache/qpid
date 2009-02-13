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
package org.apache.qpid.server.util;

import junit.framework.TestCase;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.framing.abstraction.MessagePublishInfoImpl;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.protocol.InternalTestProtocolSession;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.ConsumerTagNotUniqueException;
import org.apache.qpid.server.transactionlog.TransactionLog;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.AMQException;
import org.apache.qpid.util.MockChannel;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.exchange.ExchangeDefaults;

public class InternalBrokerBaseCase extends TestCase
{
    protected IApplicationRegistry _registry;
    protected TransactionLog _transactionLog;
    protected MockChannel _channel;
    protected InternalTestProtocolSession _session;
    protected VirtualHost _virtualHost;
    protected StoreContext _storeContext = new StoreContext();
    protected AMQQueue _queue;
    protected AMQShortString QUEUE_NAME;

    public void setUp() throws Exception
    {
        super.setUp();
        _registry = new TestApplicationRegistry();
        ApplicationRegistry.initialise(_registry);
        _virtualHost = _registry.getVirtualHostRegistry().getVirtualHost("test");        

        _transactionLog = _virtualHost.getTransactionLog();

        QUEUE_NAME = new AMQShortString("test");
        _queue = AMQQueueFactory.createAMQQueueImpl(QUEUE_NAME, false, new AMQShortString("testowner"),
                                                    false, _virtualHost, null);

        _virtualHost.getQueueRegistry().registerQueue(_queue);

        Exchange defaultExchange = _virtualHost.getExchangeRegistry().getDefaultExchange();

        _queue.bind(defaultExchange, QUEUE_NAME, null);

        _session = new InternalTestProtocolSession();

        _session.setVirtualHost(_virtualHost);

        _channel = new MockChannel(_session, 1, _transactionLog);

        _session.addChannel(_channel);
    }

    public void tearDown() throws Exception
    {
        ApplicationRegistry.remove(1);
        super.tearDown();
    }

    protected void checkStoreContents(int messageCount)
    {
        assertEquals("Message header count incorrect in the MetaDataMap", messageCount, ((TestableMemoryMessageStore) _transactionLog).getMessageMetaDataMap().size());

        //The above publish message is sufficiently small not to fit in the header so no Body is required.
        //assertEquals("Message body count incorrect in the ContentBodyMap", messageCount, ((TestableMemoryMessageStore) _messageStore).getContentBodyMap().size());
    }

    protected AMQShortString subscribe(InternalTestProtocolSession session, AMQChannel channel, AMQQueue queue)
    {
        try
        {
            return channel.subscribeToQueue(null, queue, true, null, false, true);
        }
        catch (AMQException e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }
        catch (ConsumerTagNotUniqueException e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }
        //Keep the compiler happy
        return null;
    }

    protected AMQShortString browse(AMQChannel channel, AMQQueue queue)
    {
        try
        {
            FieldTable filters = new FieldTable();
            filters.put(AMQPFilterTypes.NO_CONSUME.getValue(), true);

            return channel.subscribeToQueue(null, queue, true, filters, false, true);
        }
        catch (AMQException e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }
        catch (ConsumerTagNotUniqueException e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }
        //Keep the compiler happy
        return null;
    }

    public void publishMessages(InternalTestProtocolSession session, AMQChannel channel, int messages) throws AMQException
    {
        MessagePublishInfo info = new MessagePublishInfoImpl(ExchangeDefaults.DEFAULT_EXCHANGE_NAME, false, false,
                                                             QUEUE_NAME);

        for (int count = 0; count < messages; count++)
        {
            channel.setPublishFrame(info,  _virtualHost.getExchangeRegistry().getExchange(info.getExchange()));

            //Set the body size
            ContentHeaderBody _headerBody = new ContentHeaderBody();
            _headerBody.bodySize = 0;

            //Set Minimum properties
            BasicContentHeaderProperties properties = new BasicContentHeaderProperties();

            properties.setExpiration(0L);
            properties.setTimestamp(System.currentTimeMillis());

            //Make Message Persistent
            properties.setDeliveryMode((byte) 2);

            _headerBody.properties = properties;

            channel.publishContentHeader(_headerBody);
        }

    }

    public void acknowledge(AMQChannel channel, long deliveryTag)
    {
        try
        {
            channel.acknowledgeMessage(deliveryTag, false);
        }
        catch (AMQException e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

}
