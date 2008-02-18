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

import java.util.HashSet;
import java.util.LinkedList;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.MessageHandleFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.txn.NonTransactionalContext;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class DestWildExchangeTest extends TestCase
{

    DestWildExchange _exchange;

    VirtualHost _vhost;
    MessageStore _store;
    StoreContext _context;

    public void setUp() throws AMQException
    {
        _exchange = new DestWildExchange();
        _vhost = ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHosts().iterator().next();
        _store = new MemoryMessageStore();
        _context = new StoreContext();
    }

    public void testNoRoute() throws AMQException
    {
        AMQQueue queue = new AMQQueue(new AMQShortString("a*#b"), false, null, false, _vhost);
        _exchange.registerQueue(new AMQShortString("a.*.#.b"), queue, null);

        MessagePublishInfo info = new PublishInfo(new AMQShortString("a.b"));

        AMQMessage message = new AMQMessage(0L, info, null);

        try
        {
            _exchange.route(message);
            fail("Message has no route and shouldn't be routed");
        }
        catch (NoRouteException nre)
        {
            // normal
        }

        Assert.assertEquals(0, queue.getMessageCount());
    }

    public void testDirectMatch() throws AMQException
    {
        AMQQueue queue = new AMQQueue(new AMQShortString("ab"), false, null, false, _vhost);
        _exchange.registerQueue(new AMQShortString("a.b"), queue, null);

        AMQMessage message = createMessage("a.b");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
        }
        catch (AMQException nre)
        {
            fail("Message has  route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", message, queue.getMessagesOnTheQueue().get(0).getMessage());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a.c");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
            fail("Message has no route and should fail to be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());
    }

    public void testStarMatch() throws AMQException
    {
        AMQQueue queue = new AMQQueue(new AMQShortString("a*"), false, null, false, _vhost);
        _exchange.registerQueue(new AMQShortString("a.*"), queue, null);

        AMQMessage message = createMessage("a.b");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
        }
        catch (AMQException nre)
        {
            fail("Message has  route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", message, queue.getMessagesOnTheQueue().get(0).getMessage());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a.c");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
        }
        catch (AMQException nre)
        {
            fail("Message has route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", message, queue.getMessagesOnTheQueue().get(0).getMessage());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
            fail("Message has no route and should fail to be routed");
        }
        catch (AMQException nre)
        { }
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());
    }

    public void testHashMatch() throws AMQException
    {
        AMQQueue queue = new AMQQueue(new AMQShortString("a#"), false, null, false, _vhost);
        _exchange.registerQueue(new AMQShortString("a.#"), queue, null);

        AMQMessage message = createMessage("a.b.c");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
        }
        catch (AMQException nre)
        {
            fail("Message has route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", message, queue.getMessagesOnTheQueue().get(0).getMessage());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a.b");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
        }
        catch (AMQException nre)
        {
            fail("Message has route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", message, queue.getMessagesOnTheQueue().get(0).getMessage());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a.c");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
        }
        catch (AMQException nre)
        {
            fail("Message has route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", message, queue.getMessagesOnTheQueue().get(0).getMessage());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
        }
        catch (AMQException nre)
        {
            fail("Message has route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", message, queue.getMessagesOnTheQueue().get(0).getMessage());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("b");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
            fail("Message has no route and should fail to be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());
    }

    public void testMidHash() throws AMQException
    {
        AMQQueue queue = new AMQQueue(new AMQShortString("a"), false, null, false, _vhost);
        _exchange.registerQueue(new AMQShortString("a.*.#.b"), queue, null);

        AMQMessage message = createMessage("a.c.d.b");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
        }
        catch (AMQException nre)
        {
            fail("Message has no route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", message, queue.getMessagesOnTheQueue().get(0).getMessage());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a.c.b");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
        }
        catch (AMQException nre)
        {
            fail("Message has no route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", message, queue.getMessagesOnTheQueue().get(0).getMessage());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

    }

    public void testMatchafterHash() throws AMQException
    {
        AMQQueue queue = new AMQQueue(new AMQShortString("a#"), false, null, false, _vhost);
        _exchange.registerQueue(new AMQShortString("a.*.#.b.c"), queue, null);

        AMQMessage message = createMessage("a.c.b.b");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
            fail("Message has route and should not be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a.a.b.c");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
        }
        catch (AMQException nre)
        {
            fail("Message has no route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", message, queue.getMessagesOnTheQueue().get(0).getMessage());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a.b.c.b");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
            fail("Message has  route and should not be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a.b.c.b.c");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
        }
        catch (AMQException nre)
        {
            fail("Message has no route and should be routed");

        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", message, queue.getMessagesOnTheQueue().get(0).getMessage());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

    }

    public void testHashAfterHash() throws AMQException
    {
        AMQQueue queue = new AMQQueue(new AMQShortString("a#"), false, null, false, _vhost);
        _exchange.registerQueue(new AMQShortString("a.*.#.b.c.#.d"), queue, null);

        AMQMessage message = createMessage("a.c.b.b.c");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
            fail("Message has route and should not be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a.a.b.c.d");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
        }
        catch (AMQException nre)
        {
            fail("Message has no route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", message, queue.getMessagesOnTheQueue().get(0).getMessage());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

    }

    public void testHashHash() throws AMQException
    {
        AMQQueue queue = new AMQQueue(new AMQShortString("a#"), false, null, false, _vhost);
        _exchange.registerQueue(new AMQShortString("a.#.*.#.d"), queue, null);

        AMQMessage message = createMessage("a.c.b.b.c");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
            fail("Message has route and should not be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a.a.b.c.d");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
        }
        catch (AMQException nre)
        {
            fail("Message has no route and should be routed");
        }

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", message, queue.getMessagesOnTheQueue().get(0).getMessage());

        queue.deleteMessageFromTop(_context);
        Assert.assertEquals(0, queue.getMessageCount());

    }

    public void testSubMatchFails() throws AMQException
    {
        AMQQueue queue = new AMQQueue(new AMQShortString("a"), false, null, false, _vhost);
        _exchange.registerQueue(new AMQShortString("a.b.c.d"), queue, null);

        AMQMessage message = createMessage("a.b.c");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
            fail("Message has route and should not be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());

    }

    public void testMoreRouting() throws AMQException
    {
        AMQQueue queue = new AMQQueue(new AMQShortString("a"), false, null, false, _vhost);
        _exchange.registerQueue(new AMQShortString("a.b"), queue, null);

        AMQMessage message = createMessage("a.b.c");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
            fail("Message has route and should not be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());

    }

    public void testMoreQueue() throws AMQException
    {
        AMQQueue queue = new AMQQueue(new AMQShortString("a"), false, null, false, _vhost);
        _exchange.registerQueue(new AMQShortString("a.b"), queue, null);

        AMQMessage message = createMessage("a");

        try
        {
            _exchange.route(message);
            message.routingComplete(_store, _context, new MessageHandleFactory());
            fail("Message has route and should not be routed");
        }
        catch (AMQException nre)
        {
        }

        Assert.assertEquals(0, queue.getMessageCount());

    }

    private AMQMessage createMessage(String s) throws AMQException
    {
        MessagePublishInfo info = new PublishInfo(new AMQShortString(s));

        TransactionalContext trancontext = new NonTransactionalContext(_store, _context, null,
                                                                       new LinkedList<RequiredDeliveryException>(),
                                                                       new HashSet<Long>());

        AMQMessage message = new AMQMessage(0L, info, trancontext);
        message.setContentHeaderBody(new ContentHeaderBody());

        return message;
    }

    class PublishInfo implements MessagePublishInfo
    {
        AMQShortString _routingkey;

        PublishInfo(AMQShortString routingkey)
        {
            _routingkey = routingkey;
        }

        public AMQShortString getExchange()
        {
            return null;
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
            return true;
        }

        public AMQShortString getRoutingKey()
        {
            return _routingkey;
        }
    }
}
