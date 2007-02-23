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
package org.apache.qpid.server.queue;

import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;
import org.apache.qpid.server.protocol.AMQPFastProtocolHandler;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.protocol.AMQMinaProtocolSession;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

public class MessageRequeueTest extends TestCase
{
    private static final Logger _logger = Logger.getLogger(MessageRequeueTest.class);

    protected static AtomicInteger consumerIds = new AtomicInteger(0);
    protected final Integer numTestMessages = 1000;

    protected final int consumeTimeout = 3000;

    protected final String queue = "direct://amq.direct//queue";
    protected String payload = "Message:";

    protected AMQPFastProtocolHandler _server;

    protected AMQQueue _queue;

    protected Subscription _sub1, _sub2, _sub3;

    protected void setUp() throws Exception
    {
        super.setUp();

//        _server = new AMQPFastProtocolHandler(1);
//
//        IApplicationRegistry registry = ApplicationRegistry.getInstance();
//            assertNotNull(registry)
//        _queue = new AMQQueue("testQueue", false, "MRT", false, registry.getQueueRegistry());
//
//        AMQProtocolSession amqProtocolSession = new AMQMinaProtocolSession(null, registry.getQueueRegistry(), registry.getExchangeRegistry(), null);
//
//        _queue.registerProtocolSession(amqProtocolSession, 1, "consumer1", true, null);
//        _queue.registerProtocolSession(amqProtocolSession, 2, "consumer2", true, null);
//        _queue.registerProtocolSession(amqProtocolSession, 3, "consumer3", true, null);
//
//        SubscriptionManager subMan = _queue.getSubscribers();
//
//        List<Subscription> subs = subMan.getSubscriptions();
//
//        assertEquals("Subscriptions not correcty created", 3, subs.size());
//
//        _sub1 = subs.get(0);
//        _sub2 = subs.get(1);
//        _sub3 = subs.get(2);


    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
        _server = null;
    }


    public void testMessageRequeue()
    {


    }
}
