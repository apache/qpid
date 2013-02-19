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
package org.apache.qpid.server.exchange;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.FieldTableFactory;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.message.AMQMessage;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.MessageMetaData;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.IncomingMessage;
import org.apache.qpid.server.queue.MockStoredMessage;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.SimpleAMQQueue;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

public class AbstractHeadersExchangeTestBase extends QpidTestCase
{
    private static final Logger _log = Logger.getLogger(AbstractHeadersExchangeTestBase.class);

    private final HeadersExchange exchange = new HeadersExchange();
    private final Set<TestQueue> queues = new HashSet<TestQueue>();
    private VirtualHost _virtualHost;
    private int count;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _virtualHost = BrokerTestHelper.createVirtualHost(getClass().getName());
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            if (_virtualHost != null)
            {
                _virtualHost.close();
            }
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
        }
    }

    public void testDoNothing()
    {
        // this is here only to make junit under Eclipse happy
    }

    public VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    protected TestQueue bindDefault(String... bindings) throws AMQException
    {
        String queueName = "Queue" + (++count);

        return bind(queueName, queueName, getHeadersMap(bindings));
    }
    
    protected void unbind(TestQueue queue, String... bindings) throws AMQException
    {
        String queueName = queue.getName();
        exchange.onUnbind(new Binding(null, queueName, queue, exchange, getHeadersMap(bindings)));
    }
    
    protected int getCount()
    {
        return count;
    }

    private TestQueue bind(String key, String queueName, Map<String,Object> args) throws AMQException
    {
        TestQueue queue = new TestQueue(new AMQShortString(queueName), _virtualHost);
        queues.add(queue);
        exchange.onBind(new Binding(null, key, queue, exchange, args));
        return queue;
    }
    

    protected int route(Message m) throws AMQException
    {
        m.getIncomingMessage().headersReceived(System.currentTimeMillis());
        m.route(exchange);
        if(m.getIncomingMessage().allContentReceived())
        {
            for(BaseQueue q : m.getIncomingMessage().getDestinationQueues())
            {
                q.enqueue(m);
            }
        }
        return m.getIncomingMessage().getDestinationQueues().size();
    }

    protected void routeAndTest(Message m, TestQueue... expected) throws AMQException
    {
        routeAndTest(m, false, Arrays.asList(expected));
    }

    protected void routeAndTest(Message m, boolean expectReturn, TestQueue... expected) throws AMQException
    {
        routeAndTest(m, expectReturn, Arrays.asList(expected));
    }

    protected void routeAndTest(Message m, List<TestQueue> expected) throws AMQException
    {
        routeAndTest(m, false, expected);
    }

    protected void routeAndTest(Message m, boolean expectReturn, List<TestQueue> expected) throws AMQException
    {
            int queueCount = route(m);

            for (TestQueue q : queues)
            {
                if (expected.contains(q))
                {
                    assertTrue("Expected " + m + " to be delivered to " + q, q.isInQueue(m));
                    //assert m.isInQueue(q) : "Expected " + m + " to be delivered to " + q;
                }
                else
                {
                    assertFalse("Did not expect " + m + " to be delivered to " + q, q.isInQueue(m));
                    //assert !m.isInQueue(q) : "Did not expect " + m + " to be delivered to " + q;
                }
            }

            if(expectReturn)
            {
                assertEquals("Expected "+m+" to be returned due to manadatory flag, and lack of routing",0, queueCount);
            }

    }
    
    static Map<String,Object> getHeadersMap(String... entries)
    {
        if(entries == null)
        {
            return null;
        }
        
        Map<String,Object> headers = new HashMap<String,Object>();

        for (String s : entries)
        {
            String[] parts = s.split("=", 2);
            headers.put(parts[0], parts.length > 1 ? parts[1] : "");
        }
        return headers;
    }

    static FieldTable getHeaders(String... entries)
    {
        FieldTable headers = FieldTableFactory.newFieldTable();
        for (String s : entries)
        {
            String[] parts = s.split("=", 2);
            headers.setObject(parts[0], parts.length > 1 ? parts[1] : "");
        }
        return headers;
    }


    static final class MessagePublishInfoImpl implements MessagePublishInfo
    {
        private AMQShortString _exchange;
        private boolean _immediate;
        private boolean _mandatory;
        private AMQShortString _routingKey;

        public MessagePublishInfoImpl(AMQShortString routingKey)
        {
            _routingKey = routingKey;
        }

        public MessagePublishInfoImpl(AMQShortString exchange, boolean immediate, boolean mandatory, AMQShortString routingKey)
        {
            _exchange = exchange;
            _immediate = immediate;
            _mandatory = mandatory;
            _routingKey = routingKey;
        }

        public AMQShortString getExchange()
        {
            return _exchange;
        }

        public boolean isImmediate()
        {
            return _immediate;

        }

        public boolean isMandatory()
        {
            return _mandatory;
        }

        public AMQShortString getRoutingKey()
        {
            return _routingKey;
        }


        public void setExchange(AMQShortString exchange)
        {
            _exchange = exchange;
        }

        public void setImmediate(boolean immediate)
        {
            _immediate = immediate;
        }

        public void setMandatory(boolean mandatory)
        {
            _mandatory = mandatory;
        }

        public void setRoutingKey(AMQShortString routingKey)
        {
            _routingKey = routingKey;
        }
    }

    static MessagePublishInfo getPublishRequest(final String id)
    {
        return new MessagePublishInfoImpl(null, false, false, new AMQShortString(id));
    }

    static ContentHeaderBody getContentHeader(FieldTable headers)
    {
        ContentHeaderBody header = new ContentHeaderBody();
        header.setProperties(getProperties(headers));
        return header;
    }

    static BasicContentHeaderProperties getProperties(FieldTable headers)
    {
        BasicContentHeaderProperties properties = new BasicContentHeaderProperties();
        properties.setHeaders(headers);
        return properties;
    }

    static class TestQueue extends SimpleAMQQueue
    {
        private final List<HeadersExchangeTest.Message> messages = new ArrayList<HeadersExchangeTest.Message>();

        public String toString()
        {
            return getNameShortString().toString();
        }

        public TestQueue(AMQShortString name, VirtualHost host) throws AMQException
        {
            super(UUIDGenerator.generateRandomUUID(), name, false, new AMQShortString("test"), true, false, host, Collections.EMPTY_MAP);
            host.getQueueRegistry().registerQueue(this);
        }



        /**
         * We override this method so that the default behaviour, which attempts to use a delivery manager, is
         * not invoked. It is unnecessary since for this test we only care to know whether the message was
         * sent to the queue; the queue processing logic is not being tested.
         * @param msg
         * @throws AMQException
         */
        @Override
        public void enqueue(ServerMessage msg, boolean sync, PostEnqueueAction action) throws AMQException
        {
            messages.add( new HeadersExchangeTest.Message((AMQMessage) msg));
            final QueueEntry queueEntry = new QueueEntry()
            {

                public AMQQueue getQueue()
                {
                    return null;
                }

                public AMQMessage getMessage()
                {
                    return null;
                }

                public long getSize()
                {
                    return 0;
                }

                public boolean getDeliveredToConsumer()
                {
                    return false;
                }

                public boolean expired() throws AMQException
                {
                    return false;
                }

                public boolean isAvailable()
                {
                    return false;
                }

                public boolean isAcquired()
                {
                    return false;
                }

                public boolean acquire()
                {
                    return false;
                }

                public boolean acquire(Subscription sub)
                {
                    return false;
                }

                public boolean delete()
                {
                    return false;
                }

                public boolean isDeleted()
                {
                    return false;
                }

                public boolean acquiredBySubscription()
                {
                    return false;
                }

                public boolean isAcquiredBy(Subscription subscription)
                {
                    return false;
                }

                public void release()
                {
                  
                }

                public boolean releaseButRetain()
                {
                    return false;
                }

                public boolean immediateAndNotDelivered()
                {
                    return false;
                }

                public void setRedelivered()
                {
                  
                }

                public AMQMessageHeader getMessageHeader()
                {
                    return null;
                }

                public boolean isPersistent()
                {
                    return false;
                }

                public boolean isRedelivered()
                {
                    return false;
                }

                public Subscription getDeliveredSubscription()
                {
                    return null;
                }

                public void reject()
                {
                  
                }

                public boolean isRejectedBy(long subscriptionId)
                {
                    return false;
                }

                public void dequeue()
                {
                  
                }

                public void dispose()
                {
                  
                }

                public void discard()
                {
                  
                }

                public void routeToAlternate()
                {
                  
                }

                public boolean isQueueDeleted()
                {
                    return false;
                }

                public void addStateChangeListener(StateChangeListener listener)
                {
                  
                }

                public boolean removeStateChangeListener(StateChangeListener listener)
                {
                    return false;
                }

                public int compareTo(final QueueEntry o)
                {
                    return 0;
                }

                public boolean isDequeued()
                {
                    return false;
                }

                public boolean isDispensed()
                {
                    return false;
                }

                public QueueEntry getNextNode()
                {
                    return null;
                }

                public QueueEntry getNextValidEntry()
                {
                    return null;
                }

                public int getDeliveryCount()
                {
                    return 0;
                }

                public void incrementDeliveryCount()
                {
                }

                public void decrementDeliveryCount()
                {
                }
            };

            if(action != null)
            {
                action.onEnqueue(queueEntry);
            }

        }

        boolean isInQueue(Message msg)
        {
            return messages.contains(msg);
        }

    }

    /**
     * Just add some extra utility methods to AMQMessage to aid testing.
     */
    static class Message extends AMQMessage
    {
        private static AtomicLong _messageId = new AtomicLong();

        private class TestIncomingMessage extends IncomingMessage
        {

            public TestIncomingMessage(final long messageId,
                                       final MessagePublishInfo info,
                                       final AMQProtocolSession publisher)
            {
                super(info);
            }


            public ContentHeaderBody getContentHeader()
            {
                try
                {
                    return Message.this.getContentHeaderBody();
                }
                catch (AMQException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }

        private IncomingMessage _incoming;


        Message(AMQProtocolSession protocolSession, String id, String... headers) throws AMQException
        {
            this(protocolSession, id, getHeaders(headers));
        }

        Message(AMQProtocolSession protocolSession, String id, FieldTable headers) throws AMQException
        {
            this(protocolSession, _messageId.incrementAndGet(),getPublishRequest(id), getContentHeader(headers), Collections.EMPTY_LIST);
        }

        public IncomingMessage getIncomingMessage()
        {
            return _incoming;
        }

        private Message(AMQProtocolSession protocolsession, long messageId,
                        MessagePublishInfo publish,
                        ContentHeaderBody header,
                        List<ContentBody> bodies) throws AMQException
        {
            super(new MockStoredMessage(messageId, publish, header));

            StoredMessage<MessageMetaData> storedMessage = getStoredMessage();

            int pos = 0;
            for(ContentBody body : bodies)
            {
                storedMessage.addContent(pos, ByteBuffer.wrap(body.getPayload()));
                pos += body.getPayload().length;
            }

            _incoming = new TestIncomingMessage(getMessageId(),publish, protocolsession);
            _incoming.setContentHeaderBody(header);


        }


        private Message(AMQMessage msg) throws AMQException
        {
            super(msg.getStoredMessage());
        }



        void route(Exchange exchange) throws AMQException
        {
            _incoming.enqueue(exchange.route(_incoming));
        }


        public int hashCode()
        {
            return getKey().hashCode();
        }

        public boolean equals(Object o)
        {
            return o instanceof HeadersExchangeTest.Message && equals((HeadersExchangeTest.Message) o);
        }

        private boolean equals(HeadersExchangeTest.Message m)
        {
            return getKey().equals(m.getKey());
        }

        public String toString()
        {
            return getKey().toString();
        }

        private Object getKey()
        {
            try
            {
                return getMessagePublishInfo().getRoutingKey();
            }
            catch (AMQException e)
            {
                _log.error("Error getting routing key: " + e, e);
                return null;
            }
        }
    }
}
