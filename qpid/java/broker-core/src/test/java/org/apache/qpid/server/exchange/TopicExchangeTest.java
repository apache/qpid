/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.server.exchange;

import static org.apache.qpid.common.AMQPFilterTypes.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Assert;

import org.apache.qpid.server.binding.BindingImpl;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.QueueExistsException;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.test.utils.QpidTestCase;

public class TopicExchangeTest extends QpidTestCase
{

    private TopicExchange _exchange;
    private VirtualHostImpl _vhost;


    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _vhost = BrokerTestHelper.createVirtualHost(getName());
        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(Exchange.ID, UUID.randomUUID());
        attributes.put(Exchange.NAME, "test");
        attributes.put(Exchange.DURABLE, false);

        _exchange = new TopicExchange(attributes, _vhost);
        _exchange.open();
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            if (_vhost != null)
            {
                _vhost.close();
            }
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
        }
    }

    private AMQQueue<?> createQueue(String name) throws QueueExistsException
    {
        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.ID, UUIDGenerator.generateRandomUUID());
        attributes.put(Queue.NAME, name);
        return _vhost.createQueue(attributes);
    }

    public void testNoRoute() throws Exception
    {
        AMQQueue<?> queue = createQueue("a*#b");
        createBinding(UUID.randomUUID(), "a.*.#.b", queue, _exchange, null);


        routeMessage("a.b", 0l);

        Assert.assertEquals(0, queue.getQueueDepthMessages());
    }

    public void testDirectMatch() throws Exception
    {
        AMQQueue<?> queue = createQueue("ab");
        createBinding(UUID.randomUUID(), "a.b", queue, _exchange, null);


        routeMessage("a.b",0l);

        Assert.assertEquals(1, queue.getQueueDepthMessages());

        Assert.assertEquals("Wrong message received", 0l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.clearQueue();
        Assert.assertEquals(0, queue.getQueueDepthMessages());

        int queueCount = routeMessage("a.c",1l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getQueueDepthMessages());
    }


    public void testStarMatch() throws Exception
    {
        AMQQueue<?> queue = createQueue("a*");
        createBinding(UUID.randomUUID(), "a.*", queue, _exchange, null);


        routeMessage("a.b",0l);

        Assert.assertEquals(1, queue.getQueueDepthMessages());

        Assert.assertEquals("Wrong message received", 0l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.clearQueue();
        Assert.assertEquals(0, queue.getQueueDepthMessages());


        routeMessage("a.c",1l);

        Assert.assertEquals(1, queue.getQueueDepthMessages());

        Assert.assertEquals("Wrong message received", 1l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.clearQueue();
        Assert.assertEquals(0, queue.getQueueDepthMessages());

        int queueCount = routeMessage("a",2l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getQueueDepthMessages());
    }

    public void testHashMatch() throws Exception
    {
        AMQQueue<?> queue = createQueue("a#");
        createBinding(UUID.randomUUID(), "a.#", queue, _exchange, null);


        routeMessage("a.b.c",0l);

        Assert.assertEquals(1, queue.getQueueDepthMessages());

        Assert.assertEquals("Wrong message received", 0l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.clearQueue();
        Assert.assertEquals(0, queue.getQueueDepthMessages());

        routeMessage("a.b",1l);

        Assert.assertEquals(1, queue.getQueueDepthMessages());

        Assert.assertEquals("Wrong message received", 1l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.clearQueue();
        Assert.assertEquals(0, queue.getQueueDepthMessages());


        routeMessage("a.c",2l);

        Assert.assertEquals(1, queue.getQueueDepthMessages());

        Assert.assertEquals("Wrong message received", 2l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.clearQueue();
        Assert.assertEquals(0, queue.getQueueDepthMessages());

        routeMessage("a",3l);

        Assert.assertEquals(1, queue.getQueueDepthMessages());

        Assert.assertEquals("Wrong message received", 3l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.clearQueue();
        Assert.assertEquals(0, queue.getQueueDepthMessages());


        int queueCount = routeMessage("b", 4l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getQueueDepthMessages());
    }


    public void testMidHash() throws Exception
    {
        AMQQueue<?> queue = createQueue("a");
        createBinding(UUID.randomUUID(), "a.*.#.b", queue, _exchange, null);

        routeMessage("a.c.d.b",0l);

        Assert.assertEquals(1, queue.getQueueDepthMessages());

        Assert.assertEquals("Wrong message received", 0l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.clearQueue();
        Assert.assertEquals(0, queue.getQueueDepthMessages());

        routeMessage("a.c.b",1l);

        Assert.assertEquals(1, queue.getQueueDepthMessages());

        Assert.assertEquals("Wrong message received", 1l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.clearQueue();
        Assert.assertEquals(0, queue.getQueueDepthMessages());

    }

    public void testMatchAfterHash() throws Exception
    {
        AMQQueue<?> queue = createQueue("a#");
        createBinding(UUID.randomUUID(), "a.*.#.b.c", queue, _exchange, null);


        int queueCount = routeMessage("a.c.b.b",0l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getQueueDepthMessages());


        routeMessage("a.a.b.c",1l);

        Assert.assertEquals(1, queue.getQueueDepthMessages());

        Assert.assertEquals("Wrong message received", 1l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.clearQueue();
        Assert.assertEquals(0, queue.getQueueDepthMessages());

        queueCount = routeMessage("a.b.c.b",2l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getQueueDepthMessages());

        routeMessage("a.b.c.b.c",3l);

        Assert.assertEquals(1, queue.getQueueDepthMessages());

        Assert.assertEquals("Wrong message received", 3l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.clearQueue();
        Assert.assertEquals(0, queue.getQueueDepthMessages());

    }


    public void testHashAfterHash() throws Exception
    {
        AMQQueue<?> queue = createQueue("a#");
        createBinding(UUID.randomUUID(),
                      "a.*.#.b.c.#.d",
                      queue,
                      _exchange,
                      null);

        int queueCount = routeMessage("a.c.b.b.c",0l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getQueueDepthMessages());

        routeMessage("a.a.b.c.d",1l);

        Assert.assertEquals(1, queue.getQueueDepthMessages());

        Assert.assertEquals("Wrong message received", 1l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.clearQueue();
        Assert.assertEquals(0, queue.getQueueDepthMessages());

    }

    public void testHashHash() throws Exception
    {
        AMQQueue<?> queue = createQueue("a#");
        createBinding(UUID.randomUUID(), "a.#.*.#.d", queue, _exchange, null);

        int queueCount = routeMessage("a.c.b.b.c",0l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getQueueDepthMessages());

        routeMessage("a.a.b.c.d",1l);

        Assert.assertEquals(1, queue.getQueueDepthMessages());

        Assert.assertEquals("Wrong message received", 1l, queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.clearQueue();
        Assert.assertEquals(0, queue.getQueueDepthMessages());

    }

    public void testSubMatchFails() throws Exception
    {
        AMQQueue<?> queue = createQueue("a");
        createBinding(UUID.randomUUID(), "a.b.c.d", queue, _exchange, null);

        int queueCount = routeMessage("a.b.c",0l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getQueueDepthMessages());

    }

    public void testMoreRouting() throws Exception
    {
        AMQQueue<?> queue = createQueue("a");
       createBinding(UUID.randomUUID(), "a.b", queue, _exchange, null);


        int queueCount = routeMessage("a.b.c",0l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getQueueDepthMessages());

    }

    public void testMoreQueue() throws Exception
    {
        AMQQueue<?> queue = createQueue("a");
        createBinding(UUID.randomUUID(), "a.b", queue, _exchange, null);


        int queueCount = routeMessage("a",0l);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getQueueDepthMessages());

    }

    public void testRouteWithJMSSelector() throws Exception
    {
        AMQQueue<?> queue = createQueue("queue1");
        final String bindingKey = "bindingKey";

        Map<String, Object> bindArgs = Collections.<String, Object>singletonMap(JMS_SELECTOR.toString(), "arg > 5");
        createBinding(UUID.randomUUID(), bindingKey, queue, _exchange, bindArgs);

        ServerMessage matchMsg1 = mock(ServerMessage.class);
        AMQMessageHeader msgHeader1 = createMessageHeader(Collections.<String, Object>singletonMap("arg", 6));
        when(matchMsg1.getMessageHeader()).thenReturn(msgHeader1);
        routeMessage(matchMsg1, bindingKey, 1);
        Assert.assertEquals("First message should be routed to queue", 1, queue.getQueueDepthMessages());

        ServerMessage nonmatchMsg2 = mock(ServerMessage.class);
        AMQMessageHeader msgHeader2 = createMessageHeader(Collections.<String, Object>singletonMap("arg", 5));
        when(nonmatchMsg2.getMessageHeader()).thenReturn(msgHeader2);
        routeMessage(nonmatchMsg2, bindingKey, 2);
        Assert.assertEquals("Second message should not be routed to queue", 1, queue.getQueueDepthMessages());

        ServerMessage nonmatchMsg3 = mock(ServerMessage.class);
        AMQMessageHeader msgHeader3 = createMessageHeader(Collections.<String, Object>emptyMap());
        when(nonmatchMsg3.getMessageHeader()).thenReturn(msgHeader3);
        routeMessage(nonmatchMsg3, bindingKey, 3);
        Assert.assertEquals("Third message should not be routed to queue", 1, queue.getQueueDepthMessages());

        ServerMessage matchMsg4 = mock(ServerMessage.class);
        AMQMessageHeader msgHeader4 = createMessageHeader(Collections.<String, Object>singletonMap("arg", 7));
        when(matchMsg4.getMessageHeader()).thenReturn(msgHeader4);
        routeMessage(matchMsg4, bindingKey, 4);
        Assert.assertEquals("First message should be routed to queue", 2, queue.getQueueDepthMessages());

    }

    public void testUpdateBindingReplacingSelector() throws Exception
    {
        AMQQueue<?> queue = createQueue("queue1");
        final String bindingKey = "a";

        Map<String, Object> originalArgs = Collections.<String, Object>singletonMap(JMS_SELECTOR.toString(), "arg > 5");
        createBinding(UUID.randomUUID(), bindingKey, queue, _exchange, originalArgs);

        AMQMessageHeader mgsHeader1 = createMessageHeader(Collections.<String, Object>singletonMap("arg", 6));
        ServerMessage msg1 = mock(ServerMessage.class);
        when(msg1.getMessageHeader()).thenReturn(mgsHeader1);

        routeMessage(msg1, bindingKey, 1);
        Assert.assertEquals(1, queue.getQueueDepthMessages());

        // Update the binding
        Map<String, Object> newArgs = Collections.<String, Object>singletonMap(JMS_SELECTOR.toString(), "arg > 6");
        _exchange.replaceBinding(bindingKey, queue, newArgs);

        // Message that would have matched the original selector but not the new
        AMQMessageHeader mgsHeader2 = createMessageHeader(Collections.<String, Object>singletonMap("arg", 6));
        ServerMessage msg2 = mock(ServerMessage.class);
        when(msg2.getMessageHeader()).thenReturn(mgsHeader2);

        routeMessage(msg2, bindingKey, 2);
        Assert.assertEquals(1, queue.getQueueDepthMessages());

        // Message that matches only the second
        AMQMessageHeader mgsHeader3 = createMessageHeader(Collections.<String, Object>singletonMap("arg", 7));
        ServerMessage msg3 = mock(ServerMessage.class);
        when(msg3.getMessageHeader()).thenReturn(mgsHeader3);

        routeMessage(msg3, bindingKey, 2);
        Assert.assertEquals(2, queue.getQueueDepthMessages());

    }

    // This demonstrates QPID-5785.  Deleting the exchange after this combination of binding
    // updates generated a NPE
    public void testUpdateBindingAddingSelector() throws Exception
    {
        AMQQueue<?> queue = createQueue("queue1");
        final String bindingKey = "a";

        BindingImpl binding = createBinding(UUID.randomUUID(), bindingKey, queue, _exchange, null);

        ServerMessage msg1 = mock(ServerMessage.class);

        routeMessage(msg1, bindingKey, 1);
        Assert.assertEquals(1, queue.getQueueDepthMessages());

        // Update the binding adding selector
        Map<String, Object> newArgs = Collections.<String, Object>singletonMap(JMS_SELECTOR.toString(), "arg > 6");
        _exchange.replaceBinding(bindingKey, queue, newArgs);

        // Message that does not match the new selector
        AMQMessageHeader mgsHeader2 = createMessageHeader(Collections.<String, Object>singletonMap("arg", 6));
        ServerMessage msg2 = mock(ServerMessage.class);
        when(msg2.getMessageHeader()).thenReturn(mgsHeader2);

        routeMessage(msg2, bindingKey, 2);
        Assert.assertEquals(1, queue.getQueueDepthMessages());

        // Message that matches the selector
        AMQMessageHeader mgsHeader3 = createMessageHeader(Collections.<String, Object>singletonMap("arg", 7));
        ServerMessage msg3 = mock(ServerMessage.class);
        when(msg3.getMessageHeader()).thenReturn(mgsHeader3);

        routeMessage(msg3, bindingKey, 2);
        Assert.assertEquals(2, queue.getQueueDepthMessages());

        _exchange.delete();
    }

    private BindingImpl createBinding(UUID id,
                                      String bindingKey,
                                      AMQQueue queue,
                                      ExchangeImpl exchange,
                                      Map<String, Object> arguments)
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Binding.NAME, bindingKey);
        if(arguments != null)
        {
            attributes.put(Binding.ARGUMENTS, arguments);
        }
        attributes.put(Binding.ID, id);

        BindingImpl binding = (BindingImpl) _vhost.getObjectFactory().create(Binding.class, attributes, queue, exchange);
        binding.open();
        return binding;
    }

    private int routeMessage(String routingKey, long messageNumber)
    {
        ServerMessage message = mock(ServerMessage.class);
        return routeMessage(message, routingKey, messageNumber);
    }

    private int routeMessage(ServerMessage message, String routingKey, long messageNumber)
    {
        when(message.getInitialRoutingAddress()).thenReturn(routingKey);
        List<? extends BaseQueue> queues = _exchange.route(message, routingKey, InstanceProperties.EMPTY);
        MessageReference ref = mock(MessageReference.class);
        when(ref.getMessage()).thenReturn(message);
        when(message.newReference()).thenReturn(ref);
        when(message.newReference(any(TransactionLogResource.class))).thenReturn(ref);
        when(message.getMessageNumber()).thenReturn(messageNumber);
        for(BaseQueue q : queues)
        {
            q.enqueue(message, null);
        }

        return queues.size();
    }

    private AMQMessageHeader createMessageHeader(Map<String, Object> headers)
    {
        AMQMessageHeader messageHeader = mock(AMQMessageHeader.class);
        for(Map.Entry<String, Object> entry : headers.entrySet())
        {
            String key = entry.getKey();
            Object value = entry.getValue();

            when(messageHeader.containsHeader(key)).thenReturn(true);
            when(messageHeader.getHeader(key)).thenReturn(value);
        }
        return messageHeader;
    }


}
