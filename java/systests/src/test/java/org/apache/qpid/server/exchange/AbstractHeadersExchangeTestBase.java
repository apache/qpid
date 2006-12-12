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
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.FieldTableFactory;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.SkeletonMessageStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AbstractHeadersExchangeTestBase extends TestCase
{
    private final HeadersExchange exchange = new HeadersExchange();
    protected final Set<TestQueue> queues = new HashSet<TestQueue>();
    private int count;

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
        return bind(new TestQueue(queue), bindings);
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
    }

    protected void routeAndTest(Message m, TestQueue... expected) throws AMQException
    {
        routeAndTest(m, Arrays.asList(expected));
    }

    protected void routeAndTest(Message m, List<TestQueue> expected) throws AMQException
    {
        route(m);
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

    static FieldTable getHeaders(String... entries)
    {
        FieldTable headers = FieldTableFactory.newFieldTable();
        for (String s : entries)
        {
            String[] parts = s.split("=", 2);
            headers.put(parts[0], parts.length > 1 ? parts[1] : "");
        }
        return headers;
    }

    static BasicPublishBody getPublishRequest(String id)
    {
        BasicPublishBody request = new BasicPublishBody();
        request.routingKey = id;
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

        public TestQueue(String name) throws AMQException
        {
            super(name, false, "test", true, ApplicationRegistry.getInstance().getQueueRegistry());
        }

        public void deliver(AMQMessage msg) throws AMQException
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
            super(_messageStore, publish, header, bodies);
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
            return getPublishBody().routingKey;
        }
    }
}
