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
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.protocol.InternalTestProtocolSession;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.ConsumerTagNotUniqueException;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;

public class InternalBrokerBaseCase extends TestCase
{
    protected IApplicationRegistry _registry;
    protected MessageStore _messageStore;
    protected AMQChannel _channel;
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
        _messageStore = _virtualHost.getMessageStore();

        QUEUE_NAME = new AMQShortString("test");
        _queue = new AMQQueue(QUEUE_NAME, false, new AMQShortString("testowner"), false, _virtualHost);

        _virtualHost.getQueueRegistry().registerQueue(_queue);

        Exchange defaultExchange = _virtualHost.getExchangeRegistry().getDefaultExchange();

        _queue.bind(QUEUE_NAME, null, defaultExchange);

        _session = new InternalTestProtocolSession();

        _channel = new AMQChannel(_session, 1, _messageStore);

        _session.addChannel(_channel);
    }

    public void tearDown() throws Exception
    {
        ApplicationRegistry.removeAll();
        super.tearDown();
    }

    protected void checkStoreContents(int messageCount)
    {
        assertEquals("Message header count incorrect in the MetaDataMap", messageCount, ((TestableMemoryMessageStore) _messageStore).getMessageMetaDataMap().size());

        //The above publish message is sufficiently small not to fit in the header so no Body is required.
        //assertEquals("Message body count incorrect in the ContentBodyMap", messageCount, ((TestableMemoryMessageStore) _messageStore).getContentBodyMap().size());
    }

    protected AMQShortString subscribe(InternalTestProtocolSession session, AMQChannel channel, AMQQueue queue)
    {
        try
        {
            return channel.subscribeToQueue(null, queue, session, true, null, false, true);
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
        catch (ConsumerTagNotUniqueException e)
        {
            fail(e.getMessage());
        }
        //Keep the compiler happy
        return null;
    }

    public void publishMessages(InternalTestProtocolSession session, AMQChannel channel, int messages) throws AMQException
    {
        MessagePublishInfo info = new MessagePublishInfo()
        {
            public AMQShortString getExchange()
            {
                return ExchangeDefaults.DEFAULT_EXCHANGE_NAME;
            }

            public void setExchange(AMQShortString exchange)
            {

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
                return new AMQShortString("test");
            }
        };

        for (int count = 0; count < messages; count++)
        {
            channel.setPublishFrame(info, session, _virtualHost.getExchangeRegistry().getExchange(info.getExchange()));

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

            channel.publishContentHeader(_headerBody, session);
        }

    }
}
