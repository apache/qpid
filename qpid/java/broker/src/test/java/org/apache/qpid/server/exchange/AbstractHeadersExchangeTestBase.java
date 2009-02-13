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
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.*;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.MessagePublishInfoImpl;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.server.queue.*;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.SkeletonMessageStore;
import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.txn.NonTransactionalContext;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.log4j.Logger;

import java.util.*;

public class AbstractHeadersExchangeTestBase extends TestCase
{
    private static final Logger _log = Logger.getLogger(AbstractHeadersExchangeTestBase.class);

    private final HeadersExchange exchange = new HeadersExchange();
    protected final Set<TestQueue> queues = new HashSet<TestQueue>();

    /**
     * Not used in this test, just there to stub out the routing calls
     */
    private MessageStore _store = new MemoryMessageStore();

    private StoreContext _storeContext = new StoreContext();

    private MessageFactory _handleFactory = new MessageFactory();

    private int count;

    public void testDoNothing()
    {
        // this is here only to make junit under Eclipse happy
    }

    protected TestQueue bindDefault(String... bindings) throws AMQException
    {
        return bind("Queue" + (++count), bindings);
    }

    protected TestQueue bind(String queueName, String... bindings) throws AMQException
    {
        return bind(queueName, getHeaders(bindings));
    }

    protected TestQueue bind(String queue, FieldTable bindings) throws AMQException
    {
        return bind(new TestQueue(new AMQShortString(queue)), bindings);
    }

    protected TestQueue bind(TestQueue queue, String... bindings) throws AMQException
    {
        return bind(queue, getHeaders(bindings));
    }

    protected TestQueue bind(TestQueue queue, FieldTable bindings) throws AMQException
    {
        queues.add(queue);
        exchange.registerQueue(null, queue, bindings);
        return queue;
    }


    protected void route(Message m) throws AMQException
    {
        m.route(exchange);
        m.getIncomingMessage().routingComplete(_store, _handleFactory);
        if(m.getIncomingMessage().allContentReceived())
        {
            m.getIncomingMessage().deliverToQueues();
        }
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
        try
        {
            route(m);
            assertFalse("Expected "+m+" to be returned due to manadatory flag, and lack of routing",expectReturn);
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
        }

        catch (NoRouteException ex)
        {
            assertTrue("Expected "+m+" not to be returned",expectReturn);
        }

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

    static MessagePublishInfo getPublishRequest(final String id)
    {
        return new MessagePublishInfoImpl(null, false, false, new AMQShortString(id));
    }

    static ContentHeaderBody getContentHeader(FieldTable headers)
    {
        ContentHeaderBody header = new ContentHeaderBody();
        header.properties = getProperties(headers);
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

        public TestQueue(AMQShortString name) throws AMQException
        {
            super(name, false, new AMQShortString("test"), true, ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost("test"));
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
        public QueueEntry enqueue(StoreContext context, AMQMessage msg) throws AMQException
        {
            messages.add( new HeadersExchangeTest.Message(msg));
            return new QueueEntry()
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

                public void setDeliveredToSubscription()
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public void release()
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public String debugIdentity()
                {
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean immediateAndNotDelivered()
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public void setRedelivered(boolean b)
                {
                    //To change body of implemented methods use File | Settings | File Templates.
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

                public void requeue(StoreContext storeContext) throws AMQException
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public void dequeue(final StoreContext storeContext) throws FailedDequeueException
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public void dispose(final StoreContext storeContext) throws MessageCleanupException
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public void restoreCredit()
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public void discard(StoreContext storeContext) throws FailedDequeueException, MessageCleanupException
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

                public ContentHeaderBody getContentHeaderBody() throws AMQException
                {
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean isPersistent() throws AMQException
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean isRedelivered()
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }
            };
        }

        boolean isInQueue(Message msg)
        {
            return messages.contains(msg);
        }

    }

    /**
     * Just add some extra utility methods to AMQMessage to aid testing.
     */
    static class Message extends PersistentAMQMessage
    {
        private class TestIncomingMessage extends IncomingMessage
        {

            public TestIncomingMessage(final long messageId,
                                       final MessagePublishInfo info,
                                       final TransactionalContext txnContext,
                                       final AMQProtocolSession publisher)
            {
                super(messageId, info, txnContext, publisher);
            }


            public AMQMessage getUnderlyingMessage()
            {
                return Message.this;
            }


            public ContentHeaderBody getContentHeaderBody()
            {
                return Message.this.getContentHeaderBody();
            }
        }

        private IncomingMessage _incoming;

        private static MessageStore _messageStore = new SkeletonMessageStore();

        private static TransactionalContext _txnContext = new NonTransactionalContext(_messageStore, new StoreContext(),
                                                                                      null,
                                                                         new LinkedList<RequiredDeliveryException>()
        );

        Message(String id, String... headers) throws AMQException
        {
            this(id, getHeaders(headers));
        }

        Message(String id, FieldTable headers) throws AMQException
        {
            this(_messageStore.getNewMessageId(),getPublishRequest(id), getContentHeader(headers));
        }

        public IncomingMessage getIncomingMessage()
        {
            return _incoming;
        }

        private Message(long messageId,
                        MessagePublishInfo publish,
                        ContentHeaderBody header) throws AMQException
        {
            super(messageId, _messageStore);

            try
            {
                setPublishAndContentHeaderBody(_txnContext.getStoreContext(), publish,header);
            }
            catch (AMQException e)
            {

            }

            _incoming = new TestIncomingMessage(getMessageId(),publish,_txnContext,new MockProtocolSession(_messageStore));
            _incoming.setContentHeaderBody(header);
        }

        private Message(AMQMessage msg) throws AMQException
        {
            super(msg.getMessageId(), _messageStore);

            this.setPublishAndContentHeaderBody(_txnContext.getStoreContext(), msg.getMessagePublishInfo(), msg.getContentHeaderBody());

            Iterator<ContentChunk> iterator = msg.getContentBodyIterator();

            while(iterator.hasNext())
            {
                this.addContentBodyFrame(_txnContext.getStoreContext(), iterator.next(),iterator.hasNext());
            }
        }



        void route(Exchange exchange) throws AMQException
        {
            exchange.route(_incoming);
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
            return getMessagePublishInfo().getRoutingKey();
        }
    }
}
