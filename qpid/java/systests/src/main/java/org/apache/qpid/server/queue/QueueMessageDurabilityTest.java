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
package org.apache.qpid.server.queue;

import java.util.HashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.store.MessageDurability;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class QueueMessageDurabilityTest extends QpidBrokerTestCase
{

    private static final String QPID_MESSAGE_DURABILITY = "qpid.message_durability";
    private static final String DURABLE_ALWAYS_PERSIST_NAME = "DURABLE_QUEUE_ALWAYS_PERSIST";
    private static final String DURABLE_NEVER_PERSIST_NAME = "DURABLE_QUEUE_NEVER_PERSIST";
    private static final String DURABLE_DEFAULT_PERSIST_NAME = "DURABLE_QUEUE_DEFAULT_PERSIST";
    private static final String NONDURABLE_ALWAYS_PERSIST_NAME = "NONDURABLE_QUEUE_ALWAYS_PERSIST";

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        Connection conn = getConnection();
        Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        AMQSession amqSession = (AMQSession) session;

        Map<String,Object> arguments = new HashMap<>();
        arguments.put(QPID_MESSAGE_DURABILITY, MessageDurability.ALWAYS.name());
        amqSession.createQueue(new AMQShortString(DURABLE_ALWAYS_PERSIST_NAME), false, true, false, arguments);

        arguments = new HashMap<>();
        arguments.put(QPID_MESSAGE_DURABILITY, MessageDurability.NEVER.name());
        amqSession.createQueue(new AMQShortString(DURABLE_NEVER_PERSIST_NAME), false, true, false, arguments);

        arguments = new HashMap<>();
        arguments.put(QPID_MESSAGE_DURABILITY, MessageDurability.DEFAULT.name());
        amqSession.createQueue(new AMQShortString(DURABLE_DEFAULT_PERSIST_NAME), false, true, false, arguments);

        arguments = new HashMap<>();
        arguments.put(QPID_MESSAGE_DURABILITY,MessageDurability.ALWAYS.name());
        amqSession.createQueue(new AMQShortString(NONDURABLE_ALWAYS_PERSIST_NAME), false, false, false, arguments);

        amqSession.bindQueue(AMQShortString.valueOf(DURABLE_ALWAYS_PERSIST_NAME),
                             AMQShortString.valueOf("Y.*.*.*"),
                             null,
                             AMQShortString.valueOf(ExchangeDefaults.TOPIC_EXCHANGE_NAME),
                             null);

        amqSession.bindQueue(AMQShortString.valueOf(DURABLE_NEVER_PERSIST_NAME),
                             AMQShortString.valueOf("*.Y.*.*"),
                             null,
                             AMQShortString.valueOf(ExchangeDefaults.TOPIC_EXCHANGE_NAME),
                             null);

        amqSession.bindQueue(AMQShortString.valueOf(DURABLE_DEFAULT_PERSIST_NAME),
                             AMQShortString.valueOf("*.*.Y.*"),
                             null,
                             AMQShortString.valueOf(ExchangeDefaults.TOPIC_EXCHANGE_NAME),
                             null);

        amqSession.bindQueue(AMQShortString.valueOf(NONDURABLE_ALWAYS_PERSIST_NAME),
                             AMQShortString.valueOf("*.*.*.Y"),
                             null,
                             AMQShortString.valueOf(ExchangeDefaults.TOPIC_EXCHANGE_NAME),
                             null);
    }

    public void testSendPersistentMessageToAll() throws Exception
    {
        Connection conn = getConnection();
        Session session = conn.createSession(true, Session.SESSION_TRANSACTED);
        MessageProducer producer = session.createProducer(null);
        conn.start();
        producer.send(session.createTopic("Y.Y.Y.Y"), session.createTextMessage("test"));
        session.commit();

        AMQSession amqSession = (AMQSession) session;
        assertEquals(1,amqSession.getQueueDepth((AMQDestination)session.createQueue(DURABLE_ALWAYS_PERSIST_NAME)));
        assertEquals(1,amqSession.getQueueDepth((AMQDestination)session.createQueue(DURABLE_NEVER_PERSIST_NAME)));
        assertEquals(1,amqSession.getQueueDepth((AMQDestination)session.createQueue(DURABLE_DEFAULT_PERSIST_NAME)));
        assertEquals(1,amqSession.getQueueDepth((AMQDestination)session.createQueue(NONDURABLE_ALWAYS_PERSIST_NAME)));

        restartBroker();

        conn = getConnection();
        session = conn.createSession(true, Session.SESSION_TRANSACTED);
        amqSession = (AMQSession) session;
        assertEquals(1,amqSession.getQueueDepth((AMQDestination)session.createQueue(DURABLE_ALWAYS_PERSIST_NAME)));
        assertEquals(0,amqSession.getQueueDepth((AMQDestination)session.createQueue(DURABLE_NEVER_PERSIST_NAME)));
        assertEquals(1,amqSession.getQueueDepth((AMQDestination)session.createQueue(DURABLE_DEFAULT_PERSIST_NAME)));

        assertFalse(amqSession.isQueueBound((AMQDestination) session.createQueue(NONDURABLE_ALWAYS_PERSIST_NAME)));

    }


    public void testSendNonPersistentMessageToAll() throws Exception
    {
        Connection conn = getConnection();
        Session session = conn.createSession(true, Session.SESSION_TRANSACTED);
        MessageProducer producer = session.createProducer(null);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        conn.start();
        producer.send(session.createTopic("Y.Y.Y.Y"), session.createTextMessage("test"));
        session.commit();

        AMQSession amqSession = (AMQSession) session;
        assertEquals(1,amqSession.getQueueDepth((AMQDestination)session.createQueue(DURABLE_ALWAYS_PERSIST_NAME)));
        assertEquals(1,amqSession.getQueueDepth((AMQDestination)session.createQueue(DURABLE_NEVER_PERSIST_NAME)));
        assertEquals(1,amqSession.getQueueDepth((AMQDestination)session.createQueue(DURABLE_DEFAULT_PERSIST_NAME)));
        assertEquals(1,amqSession.getQueueDepth((AMQDestination)session.createQueue(NONDURABLE_ALWAYS_PERSIST_NAME)));

        restartBroker();

        conn = getConnection();
        session = conn.createSession(true, Session.SESSION_TRANSACTED);
        amqSession = (AMQSession) session;
        assertEquals(1,amqSession.getQueueDepth((AMQDestination)session.createQueue(DURABLE_ALWAYS_PERSIST_NAME)));
        assertEquals(0,amqSession.getQueueDepth((AMQDestination)session.createQueue(DURABLE_NEVER_PERSIST_NAME)));
        assertEquals(0,amqSession.getQueueDepth((AMQDestination)session.createQueue(DURABLE_DEFAULT_PERSIST_NAME)));

        assertFalse(amqSession.isQueueBound((AMQDestination)session.createQueue(NONDURABLE_ALWAYS_PERSIST_NAME)));

    }

    public void testNonPersistentContentRetained() throws Exception
    {
        Connection conn = getConnection();
        Session session = conn.createSession(true, Session.SESSION_TRANSACTED);
        MessageProducer producer = session.createProducer(null);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        conn.start();
        producer.send(session.createTopic("N.N.Y.Y"), session.createTextMessage("test1"));
        producer.send(session.createTopic("Y.N.Y.Y"), session.createTextMessage("test2"));
        session.commit();
        MessageConsumer consumer = session.createConsumer(session.createQueue(DURABLE_ALWAYS_PERSIST_NAME));
        Message msg = consumer.receive(1000l);
        assertNotNull(msg);
        assertTrue(msg instanceof TextMessage);
        assertEquals("test2", ((TextMessage) msg).getText());
        session.rollback();
        restartBroker();
        conn = getConnection();
        conn.start();
        session = conn.createSession(true, Session.SESSION_TRANSACTED);
        AMQSession amqSession = (AMQSession) session;
        assertEquals(1, amqSession.getQueueDepth((AMQDestination) session.createQueue(DURABLE_ALWAYS_PERSIST_NAME)));
        assertEquals(0,amqSession.getQueueDepth((AMQDestination)session.createQueue(DURABLE_NEVER_PERSIST_NAME)));
        assertEquals(0,amqSession.getQueueDepth((AMQDestination)session.createQueue(DURABLE_DEFAULT_PERSIST_NAME)));
        consumer = session.createConsumer(session.createQueue(DURABLE_ALWAYS_PERSIST_NAME));
        msg = consumer.receive(1000l);
        assertNotNull(msg);
        assertTrue(msg instanceof TextMessage);
        assertEquals("test2", ((TextMessage)msg).getText());
        session.commit();
    }

    public void testPersistentContentRetainedOnTransientQueue() throws Exception
    {
        setTestClientSystemProperty(ClientProperties.QPID_DECLARE_QUEUES_PROP_NAME, "false");
        Connection conn = getConnection();
        Session session = conn.createSession(true, Session.SESSION_TRANSACTED);
        MessageProducer producer = session.createProducer(null);
        producer.setDeliveryMode(DeliveryMode.PERSISTENT);
        conn.start();
        producer.send(session.createTopic("N.N.Y.Y"), session.createTextMessage("test1"));
        session.commit();
        MessageConsumer consumer = session.createConsumer(session.createQueue(DURABLE_DEFAULT_PERSIST_NAME));
        Message msg = consumer.receive(1000l);
        assertNotNull(msg);
        assertTrue(msg instanceof TextMessage);
        assertEquals("test1", ((TextMessage)msg).getText());
        session.commit();
        System.gc();
        consumer = session.createConsumer(session.createQueue(NONDURABLE_ALWAYS_PERSIST_NAME));
        msg = consumer.receive(1000l);
        assertNotNull(msg);
        assertTrue(msg instanceof TextMessage);
        assertEquals("test1", ((TextMessage)msg).getText());
        session.commit();
    }


}
