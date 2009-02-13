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

import junit.framework.TestCase;
import junit.framework.Assert;
import org.apache.qpid.server.queue.*;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.txn.NonTransactionalContext;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.server.protocol.InternalTestProtocolSession;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.MessagePublishInfoImpl;

import java.util.LinkedList;

public class DestWildExchangeTest extends TestCase
{

    TopicExchange _exchange;

    VirtualHost _vhost;
    MessageStore _store;
    StoreContext _context;

    InternalTestProtocolSession _protocolSession;


    public void setUp() throws AMQException
    {
        _exchange = new TopicExchange();
        _vhost = ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHosts().iterator().next();
        _store = new MemoryMessageStore();
        _context = new StoreContext();
        _protocolSession = new InternalTestProtocolSession();
    }

    public void tearDown()
    {
        ApplicationRegistry.remove(1); 
    }


    public void testNoRoute() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("a*#b"), false, null, false, _vhost, null);        
        _exchange.registerQueue(new AMQShortString("a.*.#.b"), queue, null);


        MessagePublishInfo info = new MessagePublishInfoImpl(null, false, false, new AMQShortString("a.b"));

        IncomingMessage message = new IncomingMessage(0L, info, null, _protocolSession);

        _exchange.route(message);            

        Assert.assertEquals(0, queue.getMessageCount());
    }

    public void testDirectMatch() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("ab"), false, null, false, _vhost, null);
        _exchange.registerQueue(new AMQShortString("a.b"), queue, null);


        IncomingMessage message = createMessage("a.b");

        try
        {
            routeMessage(message);
        }
        catch (AMQException nre)
        {
            fail("Message has  route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageId(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageId());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());


        message = createMessage("a.c");

        try
        {
            routeMessage(message);
            fail("Message has no route and should fail to be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());
    }


    public void testStarMatch() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("a*"), false, null, false, _vhost, null);
        _exchange.registerQueue(new AMQShortString("a.*"), queue, null);


        IncomingMessage message = createMessage("a.b");

        try
        {
            routeMessage(message);
        }
        catch (AMQException nre)
        {
            fail("Message has  route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageId(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageId());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());


        message = createMessage("a.c");

        try
        {
            routeMessage(message);
        }
        catch (AMQException nre)
        {
            fail("Message has route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageId(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageId());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());


        message = createMessage("a");

        try
        {
            routeMessage(message);
            fail("Message has no route and should fail to be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());
    }

    public void testHashMatch() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("a#"), false, null, false, _vhost, null);
        _exchange.registerQueue(new AMQShortString("a.#"), queue, null);


        IncomingMessage message = createMessage("a.b.c");

        try
        {
            routeMessage(message);
        }
        catch (AMQException nre)
        {
            fail("Message has route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageId(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageId());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());


        message = createMessage("a.b");

        try
        {
            routeMessage(message);
        }
        catch (AMQException nre)
        {
            fail("Message has route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageId(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageId());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());


        message = createMessage("a.c");

        try
        {
            routeMessage(message);
        }
        catch (AMQException nre)
        {
            fail("Message has route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageId(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageId());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a");

        try
        {
            routeMessage(message);
        }
        catch (AMQException nre)
        {
            fail("Message has route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageId(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageId());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());


        message = createMessage("b");

        try
        {
            routeMessage(message);
            fail("Message has no route and should fail to be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());
    }


    public void testMidHash() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("a"), false, null, false, _vhost, null);
        _exchange.registerQueue(new AMQShortString("a.*.#.b"), queue, null);


        IncomingMessage message = createMessage("a.c.d.b");

        try
        {
            routeMessage(message);
        }
        catch (AMQException nre)
        {
            fail("Message has no route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageId(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageId());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a.c.b");

        try
        {
            routeMessage(message);
        }
        catch (AMQException nre)
        {
            fail("Message has no route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageId(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageId());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

    }

    public void testMatchafterHash() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("a#"), false, null, false, _vhost, null);
        _exchange.registerQueue(new AMQShortString("a.*.#.b.c"), queue, null);


        IncomingMessage message = createMessage("a.c.b.b");

        try
        {
            routeMessage(message);
            fail("Message has route and should not be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());


        message = createMessage("a.a.b.c");

        try
        {
            routeMessage(message);
        }
        catch (AMQException nre)
        {
            fail("Message has no route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageId(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageId());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a.b.c.b");

        try
        {
            routeMessage(message);
            fail("Message has  route and should not be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a.b.c.b.c");

        try
        {
            routeMessage(message);
        }
        catch (AMQException nre)
        {
            fail("Message has no route and should be routed");

        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageId(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageId());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

    }


    public void testHashAfterHash() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("a#"), false, null, false, _vhost, null);
        _exchange.registerQueue(new AMQShortString("a.*.#.b.c.#.d"), queue, null);


        IncomingMessage message = createMessage("a.c.b.b.c");

        try
        {
            routeMessage(message);
            fail("Message has route and should not be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());


        message = createMessage("a.a.b.c.d");

        try
        {
            routeMessage(message);
        }
        catch (AMQException nre)
        {
            fail("Message has no route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageId(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageId());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

    }

    public void testHashHash() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("a#"), false, null, false, _vhost, null);
        _exchange.registerQueue(new AMQShortString("a.#.*.#.d"), queue, null);


        IncomingMessage message = createMessage("a.c.b.b.c");

        try
        {
            routeMessage(message);
            fail("Message has route and should not be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a.a.b.c.d");

        try
        {
            routeMessage(message);
        }
        catch (AMQException nre)
        {
            fail("Message has no route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageId(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageId());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

    }

    public void testSubMatchFails() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("a"), false, null, false, _vhost, null);
        _exchange.registerQueue(new AMQShortString("a.b.c.d"), queue, null);


        IncomingMessage message = createMessage("a.b.c");

        try
        {
            routeMessage(message);
            fail("Message has route and should not be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());

    }

    private void routeMessage(final IncomingMessage message)
            throws AMQException
    {
        _exchange.route(message);
        message.routingComplete(_store, new MessageFactory());
        message.deliverToQueues();
    }

    public void testMoreRouting() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("a"), false, null, false, _vhost, null);
        _exchange.registerQueue(new AMQShortString("a.b"), queue, null);


        IncomingMessage message = createMessage("a.b.c");

        try
        {
            routeMessage(message);
            fail("Message has route and should not be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());

    }

    public void testMoreQueue() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(new AMQShortString("a"), false, null, false, _vhost, null);
        _exchange.registerQueue(new AMQShortString("a.b"), queue, null);


        IncomingMessage message = createMessage("a");

        try
        {
            routeMessage(message);
            fail("Message has route and should not be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());

    }

    private IncomingMessage createMessage(String s) throws AMQException
    {
        MessagePublishInfo info = new MessagePublishInfoImpl(null, false, true, new AMQShortString(s));

        TransactionalContext trancontext = new NonTransactionalContext(_store, _context, null,
                                                                       new LinkedList<RequiredDeliveryException>()
        );

        IncomingMessage message = new IncomingMessage(0L, info, trancontext,_protocolSession);
        message.setContentHeaderBody( new ContentHeaderBody());


        return message;
    }
}
