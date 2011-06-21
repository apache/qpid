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

import junit.framework.TestCase;
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
import org.apache.qpid.server.binding.BindingFactory;
import org.apache.qpid.server.message.AMQMessage;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.MessageMetaData;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.IncomingMessage;
import org.apache.qpid.server.queue.MockStoredMessage;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.SimpleAMQQueue;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.util.InternalBrokerBaseCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class AbstractHeadersExchangeTestBase extends InternalBrokerBaseCase
{
    private static final Logger _log = Logger.getLogger(AbstractHeadersExchangeTestBase.class);

    private final HeadersExchange exchange = new HeadersExchange();
    protected final Set<TestQueue> queues = new HashSet<TestQueue>();



    /**
     * Not used in this test, just there to stub out the routing calls
     */
    private MessageStore _store = new MemoryMessageStore();


    BindingFactory bindingFactory = new BindingFactory(new DurableConfigurationStore.Source()
                                                        {

                                                            public DurableConfigurationStore getDurableConfigurationStore()
                                                            {
                                                                return _store;
                                                            }
                                                        },
                                                        exchange);

    private int count;

    public void testDoNothing()
    {
        // this is here only to make junit under Eclipse happy
    }

    protected TestQueue bindDefault(String... bindings) throws AMQException
    {
        String queueName = "Queue" + (++count);

        return bind(queueName, queueName, getHeadersMap(bindings));
    }
    
    protected void unbind(TestQueue queue, String... bindings) throws AMQException
    {
        String queueName = queue.getName();
        //TODO - check this
        exchange.onUnbind(new Binding(null,queueName, queue, exchange, getHeadersMap(bindings)));
    }
    
    protected int getCount()
    {
        return count;
    }

    private TestQueue bind(String key, String queueName, Map<String,Object> args) throws AMQException
    {
        TestQueue queue = new TestQueue(new AMQShortString(queueName));
        queues.add(queue);
        exchange.onBind(new Binding(null,key, queue, exchange, args));
        return queue;
    }
    

    protected int route(Message m) throws AMQException
    {
        m.getIncomingMessage().headersReceived();
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
        final List<HeadersExchangeTest.Message> messages = new ArrayList<HeadersExchangeTest.Message>();

        public String toString()
        {
            return getNameShortString().toString();
        }

        public TestQueue(AMQShortString name) throws AMQException
        {
            super(name, false, new AMQShortString("test"), true, false,ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost("test"), Collections.EMPTY_MAP);
            ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost("test").getQueueRegistry().registerQueue(this);
        }



        /**
         * We override this method so that the default behaviour, which attempts to use a delivery manager, is
         * not invoked. It is unnecessary since for this test we only care to know whether the message was
         * sent to the queue; the queue processing logic is not being tested.
         * @param msg
         * @throws AMQException
         */
        @Override
        public void enqueue(ServerMessage msg, PostEnqueueAction action) throws AMQException
        {
            messages.add( new HeadersExchangeTest.Message((AMQMessage) msg));
            final QueueEntry queueEntry = new QueueEntry()
            {

                public AMQQueue getQueue()
                {
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public AMQMessage getMessage()
                {
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public long getSize()
                {
                    return 0;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean getDeliveredToConsumer()
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean expired() throws AMQException
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean isAvailable()
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean isAcquired()
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean acquire()
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean acquire(Subscription sub)
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
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
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean isAcquiredBy(Subscription subscription)
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public void release()
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean releaseButRetain()
                {
                    return false;
                }

                public boolean immediateAndNotDelivered()
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public void setRedelivered()
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public AMQMessageHeader getMessageHeader()
                {
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean isPersistent()
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean isRedelivered()
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public Subscription getDeliveredSubscription()
                {
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public void reject()
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public void reject(Subscription subscription)
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean isRejectedBy(Subscription subscription)
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public void requeue(Subscription subscription) 
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public void dequeue()
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public void dispose()
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public void discard()
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public void routeToAlternate()
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean isQueueDeleted()
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public void addStateChangeListener(StateChangeListener listener)
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean removeStateChangeListener(StateChangeListener listener)
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public int compareTo(final QueueEntry o)
                {
                    return 0;  //To change body of implemented methods use File | Settings | File Templates.
                }

                @Override
                public boolean isDequeued()
                {
                    return false;
                }

                @Override
                public boolean isDispensed()
                {
                    return false;
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


            public AMQMessage getUnderlyingMessage()
            {
                return Message.this;
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
                storedMessage.addContent(pos, body.payload.duplicate().buf());
                pos += body.payload.limit();
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
