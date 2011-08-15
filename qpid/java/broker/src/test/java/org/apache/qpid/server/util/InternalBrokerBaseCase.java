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

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.AMQException;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.logging.SystemOutMessageLogger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.logging.actors.TestLogActor;
import org.apache.qpid.server.protocol.InternalTestProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.MockChannel;


public class InternalBrokerBaseCase extends QpidTestCase
{
    private IApplicationRegistry _registry;
    private MessageStore _messageStore;
    private MockChannel _channel;
    private InternalTestProtocolSession _session;
    private VirtualHost _virtualHost;
    private AMQQueue _queue;
    private AMQShortString QUEUE_NAME;
    private ServerConfiguration _configuration;
    private XMLConfiguration _configXml = new XMLConfiguration();
    private boolean _started = false;

    public void setUp() throws Exception
    {
        super.setUp();

        _configXml.addProperty("virtualhosts.virtualhost.name", "test");
        _configXml.addProperty("virtualhosts.virtualhost.test.store.class", TestableMemoryMessageStore.class.getName());

        _configXml.addProperty("virtualhosts.virtualhost(-1).name", getName());
        _configXml.addProperty("virtualhosts.virtualhost(-1)."+getName()+".store.class", TestableMemoryMessageStore.class.getName());

        createBroker();
    }

    protected void createBroker() throws Exception
    {
        _started = true;
        CurrentActor.set(new TestLogActor(new SystemOutMessageLogger()));

        _configuration = new ServerConfiguration(_configXml);

        configure();

        _registry = new TestApplicationRegistry(_configuration);
        ApplicationRegistry.initialise(_registry);
        _registry.getVirtualHostRegistry().setDefaultVirtualHostName(getName());
        _virtualHost = _registry.getVirtualHostRegistry().getVirtualHost(getName());

        QUEUE_NAME = new AMQShortString("test");        
        // Create a queue on the test Vhost.. this will aid in diagnosing duff tests
        // as the ExpiredMessage Task will log with the test Name.
        _queue = AMQQueueFactory.createAMQQueueImpl(QUEUE_NAME, false, new AMQShortString("testowner"),
                                                    false, false, _virtualHost, null);

        Exchange defaultExchange = _virtualHost.getExchangeRegistry().getDefaultExchange();
        _virtualHost.getBindingFactory().addBinding(QUEUE_NAME.toString(), _queue, defaultExchange, null);

        _virtualHost = _registry.getVirtualHostRegistry().getVirtualHost("test");
        _messageStore = _virtualHost.getMessageStore();

        _queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString(getName()), false, new AMQShortString("testowner"),
                                                    false, false, _virtualHost, null);

        _virtualHost.getQueueRegistry().registerQueue(_queue);

        defaultExchange = _virtualHost.getExchangeRegistry().getDefaultExchange();

        _virtualHost.getBindingFactory().addBinding(getName(), _queue, defaultExchange, null);

        _session = new InternalTestProtocolSession(_virtualHost);
        CurrentActor.set(_session.getLogActor());

        _channel = new MockChannel(_session, 1, _messageStore);

        _session.addChannel(_channel);
    }

    protected void configure()
    {
        // Allow other tests to override configuration
    }

    protected void stopBroker()
    {
        try
        {
            //Remove the ProtocolSession Actor added during createBroker
            CurrentActor.remove();
        }
        finally
        {
            ApplicationRegistry.remove();
            _started = false;
        }
    }


    public void tearDown() throws Exception
    {
        try
        {
            if (_started)
            {
                stopBroker();
            }
        }
        finally
        {
            super.tearDown();
            // Purge Any erroneously added actors
            CurrentActor.removeAll();
        }
    }

    protected void checkStoreContents(int messageCount)
    {
        assertEquals("Message header count incorrect in the MetaDataMap", messageCount, ((TestableMemoryMessageStore) _messageStore).getMessageCount());

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
                return new AMQShortString(getName());
            }
        };

        for (int count = 0; count < messages; count++)
        {
            channel.setPublishFrame(info, _virtualHost.getExchangeRegistry().getExchange(info.getExchange()));

            //Set the body size
            ContentHeaderBody _headerBody = new ContentHeaderBody();
            _headerBody.bodySize = 0;

            //Set Minimum properties
            BasicContentHeaderProperties properties = new BasicContentHeaderProperties();

            properties.setExpiration(0L);
            properties.setTimestamp(System.currentTimeMillis());

            //Make Message Persistent
            properties.setDeliveryMode((byte) 2);

            _headerBody.setProperties(properties);

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

    public IApplicationRegistry getRegistry()
    {
        return _registry;
    }

    public void setRegistry(IApplicationRegistry registry)
    {
        _registry = registry;
    }

    public MessageStore getMessageStore()
    {
        return _messageStore;
    }

    public void setMessageStore(MessageStore messageStore)
    {
        _messageStore = messageStore;
    }

    public MockChannel getChannel()
    {
        return _channel;
    }

    public void setChannel(MockChannel channel)
    {
        _channel = channel;
    }

    public InternalTestProtocolSession getSession()
    {
        return _session;
    }

    public void setSession(InternalTestProtocolSession session)
    {
        _session = session;
    }

    public VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    public void setVirtualHost(VirtualHost virtualHost)
    {
        _virtualHost = virtualHost;
    }

    public AMQQueue getQueue()
    {
        return _queue;
    }

    public void setQueue(AMQQueue queue)
    {
        _queue = queue;
    }

    public AMQShortString getQUEUE_NAME()
    {
        return QUEUE_NAME;
    }

    public void setQUEUE_NAME(AMQShortString QUEUE_NAME)
    {
        this.QUEUE_NAME = QUEUE_NAME;
    }

    public ServerConfiguration getConfiguration()
    {
        return _configuration;
    }

    public void setConfiguration(ServerConfiguration configuration)
    {
        _configuration = configuration;
    }

    public XMLConfiguration getConfigXml()
    {
        return _configXml;
    }

    public void setConfigXml(XMLConfiguration configXml)
    {
        _configXml = configXml;
    }

    public boolean isStarted()
    {
        return _started;
    }

    public void setStarted(boolean started)
    {
        _started = started;
    }
}
