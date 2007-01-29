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
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.MessageHandleFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.SkeletonMessageStore;
import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.txn.NonTransactionalContext;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.RequiredDeliveryException;
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

    private MessageHandleFactory _handleFactory = new MessageHandleFactory();

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
        m.routingComplete(_store, _storeContext, _handleFactory);
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
                    assertTrue("Expected " + m + " to be delivered to " + q, m.isInQueue(q));
                    //assert m.isInQueue(q) : "Expected " + m + " to be delivered to " + q;
                }
                else
                {
                    assertFalse("Did not expect " + m + " to be delivered to " + q, m.isInQueue(q));
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

    static BasicPublishBody getPublishRequest(String id)
    {
        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Establish some way to determine the version for the test.
        BasicPublishBody request = new BasicPublishBody((byte)8, (byte)0,
                                                       BasicPublishBody.getClazz((byte)8,(byte)0),
                                                       BasicPublishBody.getMethod((byte)8,(byte)0),
                                                       null,false,false,new AMQShortString(id),0);
        
        return request;
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

    static class TestQueue extends AMQQueue
    {
        final List<HeadersExchangeTest.Message> messages = new ArrayList<HeadersExchangeTest.Message>();

        public TestQueue(AMQShortString name) throws AMQException
        {
            super(name, false, new AMQShortString("test"), true, ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost("test"));
        }

        /**
         * We override this method so that the default behaviour, which attempts to use a delivery manager, is
         * not invoked. It is unnecessary since for this test we only care to know whether the message was
         * sent to the queue; the queue processing logic is not being tested.
         * @param msg
         * @throws AMQException
         */
        public void process(StoreContext context, AMQMessage msg) throws AMQException
        {
            messages.add(new HeadersExchangeTest.Message(msg));
        }
    }

    /**
     * Just add some extra utility methods to AMQMessage to aid testing.
     */
    static class Message extends AMQMessage
    {
        private static MessageStore _messageStore = new SkeletonMessageStore();

        private static StoreContext _storeContext = new StoreContext();

        private static TransactionalContext _txnContext = new NonTransactionalContext(_messageStore, _storeContext,
                                                                                      null,
                                                                         new LinkedList<RequiredDeliveryException>(),
                                                                         new HashSet<Long>());

        Message(String id, String... headers) throws AMQException
        {
            this(id, getHeaders(headers));
        }

        Message(String id, FieldTable headers) throws AMQException
        {
            this(getPublishRequest(id), getContentHeader(headers), null);
        }

        private Message(BasicPublishBody publish, ContentHeaderBody header, List<ContentBody> bodies) throws AMQException
        {
            super(_messageStore.getNewMessageId(), publish, _txnContext, header);
        }

        private Message(AMQMessage msg) throws AMQException
        {
            super(msg);
        }

        void route(Exchange exchange) throws AMQException
        {
            exchange.route(this);
        }

        boolean isInQueue(TestQueue queue)
        {
            return queue.messages.contains(this);
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
                return getPublishBody().routingKey;
            }
            catch (AMQException e)
            {
                _log.error("Error getting routing key: " + e, e);
                return null;
            }
        }
    }
}
