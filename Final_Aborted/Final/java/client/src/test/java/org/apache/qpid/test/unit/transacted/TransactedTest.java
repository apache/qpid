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
package org.apache.qpid.test.unit.transacted;

import junit.framework.TestCase;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.jms.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.TextMessage;

public class TransactedTest extends TestCase
{
    private AMQQueue queue1;
    private AMQQueue queue2;

    private AMQConnection con;
    private Session session;
    private MessageConsumer consumer1;
    private MessageProducer producer2;

    private AMQConnection prepCon;
    private Session prepSession;
    private MessageProducer prepProducer1;

    private AMQConnection testCon;
    private Session testSession;
    private MessageConsumer testConsumer1;
    private MessageConsumer testConsumer2;
    private static final Logger _logger = LoggerFactory.getLogger(TransactedTest.class);

    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);
        _logger.info("Create Connection");
        con = new AMQConnection("vm://:1", "guest", "guest", "TransactedTest", "test");

        _logger.info("Create Session");
        session = con.createSession(true, Session.SESSION_TRANSACTED);
        _logger.info("Create Q1");
        queue1 =
            new AMQQueue(session.getDefaultQueueExchangeName(), new AMQShortString("Q1"), new AMQShortString("Q1"), false,
                true);
        _logger.info("Create Q2");
        queue2 = new AMQQueue(session.getDefaultQueueExchangeName(), new AMQShortString("Q2"), false);

        _logger.info("Create Consumer of Q1");
        consumer1 = session.createConsumer(queue1);
        // Dummy just to create the queue.
        _logger.info("Create Consumer of Q2");
        MessageConsumer consumer2 = session.createConsumer(queue2);
        _logger.info("Close Consumer of Q2");
        consumer2.close();

        _logger.info("Create producer to Q2");
        producer2 = session.createProducer(queue2);

        _logger.info("Start Connection");
        con.start();

        _logger.info("Create prep connection");
        prepCon = new AMQConnection("vm://:1", "guest", "guest", "PrepConnection", "test");

        _logger.info("Create prep session");
        prepSession = prepCon.createSession(false, AMQSession.NO_ACKNOWLEDGE);

        _logger.info("Create prep producer to Q1");
        prepProducer1 = prepSession.createProducer(queue1);

        _logger.info("Create prep connection start");
        prepCon.start();

        _logger.info("Create test connection");
        testCon = new AMQConnection("vm://:1", "guest", "guest", "TestConnection", "test");
        _logger.info("Create test session");
        testSession = testCon.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        _logger.info("Create test consumer of q2");
        testConsumer2 = testSession.createConsumer(queue2);
    }

    protected void tearDown() throws Exception
    {
        _logger.info("Close connection");
        con.close();
        _logger.info("Close test connection");
        testCon.close();
        _logger.info("Close prep connection");
        prepCon.close();
        _logger.info("Kill broker");
        TransportConnection.killAllVMBrokers();
        super.tearDown();
    }

    public void testCommit() throws Exception
    {
        // add some messages
        _logger.info("Send prep A");
        prepProducer1.send(prepSession.createTextMessage("A"));
        _logger.info("Send prep B");
        prepProducer1.send(prepSession.createTextMessage("B"));
        _logger.info("Send prep C");
        prepProducer1.send(prepSession.createTextMessage("C"));

        // send and receive some messages
        _logger.info("Send X to Q2");
        producer2.send(session.createTextMessage("X"));
        _logger.info("Send Y to Q2");
        producer2.send(session.createTextMessage("Y"));
        _logger.info("Send Z to Q2");
        producer2.send(session.createTextMessage("Z"));

        _logger.info("Read A from Q1");
        expect("A", consumer1.receive(1000));
        _logger.info("Read B from Q1");
        expect("B", consumer1.receive(1000));
        _logger.info("Read C from Q1");
        expect("C", consumer1.receive(1000));

        // commit
        _logger.info("session commit");
        session.commit();
        _logger.info("Start test Connection");
        testCon.start();

        // ensure sent messages can be received and received messages are gone
        _logger.info("Read X from Q2");
        expect("X", testConsumer2.receive(1000));
        _logger.info("Read Y from Q2");
        expect("Y", testConsumer2.receive(1000));
        _logger.info("Read Z from Q2");
        expect("Z", testConsumer2.receive(1000));

        _logger.info("create test session on Q1");
        testConsumer1 = testSession.createConsumer(queue1);
        _logger.info("Read null from Q1");
        assertTrue(null == testConsumer1.receive(1000));
        _logger.info("Read null from Q2");
        assertTrue(null == testConsumer2.receive(1000));
    }

    public void testRollback() throws Exception
    {
        // add some messages
        _logger.info("Send prep A");
        prepProducer1.send(prepSession.createTextMessage("A"));
        _logger.info("Send prep B");
        prepProducer1.send(prepSession.createTextMessage("B"));
        _logger.info("Send prep C");
        prepProducer1.send(prepSession.createTextMessage("C"));

        // Quick sleep to ensure all three get pre-fetched
        Thread.sleep(500);

        _logger.info("Sending X Y Z");
        producer2.send(session.createTextMessage("X"));
        producer2.send(session.createTextMessage("Y"));
        producer2.send(session.createTextMessage("Z"));
        _logger.info("Receiving A B");
        expect("A", consumer1.receive(1000));
        expect("B", consumer1.receive(1000));
        // Don't consume 'C' leave it in the prefetch cache to ensure rollback removes it.

        // rollback
        _logger.info("rollback");
        session.rollback();

        _logger.info("Receiving A B C");
        // ensure sent messages are not visible and received messages are requeued
        expect("A", consumer1.receive(1000), true);
        expect("B", consumer1.receive(1000), true);
        expect("C", consumer1.receive(1000), true);

        _logger.info("Starting new connection");
        testCon.start();
        testConsumer1 = testSession.createConsumer(queue1);
        _logger.info("Testing we have no messages left");
        assertTrue(null == testConsumer1.receive(1000));
        assertTrue(null == testConsumer2.receive(1000));

        session.commit();

        _logger.info("Testing we have no messages left after commit");
        assertTrue(null == testConsumer1.receive(1000));
        assertTrue(null == testConsumer2.receive(1000));
    }

    public void testResendsMsgsAfterSessionClose() throws Exception
    {
        AMQConnection con = new AMQConnection("vm://:1", "guest", "guest", "consumer1", "test");

        Session consumerSession = con.createSession(true, Session.SESSION_TRANSACTED);
        AMQQueue queue3 = new AMQQueue(consumerSession.getDefaultQueueExchangeName(), new AMQShortString("Q3"), false);
        MessageConsumer consumer = consumerSession.createConsumer(queue3);

        AMQConnection con2 = new AMQConnection("vm://:1", "guest", "guest", "producer1", "test");
        Session producerSession = con2.createSession(true, Session.SESSION_TRANSACTED);
        MessageProducer producer = producerSession.createProducer(queue3);

        _logger.info("Sending four messages");
        producer.send(producerSession.createTextMessage("msg1"));
        producer.send(producerSession.createTextMessage("msg2"));
        producer.send(producerSession.createTextMessage("msg3"));
        producer.send(producerSession.createTextMessage("msg4"));

        producerSession.commit();

        _logger.info("Starting connection");
        con.start();
        TextMessage tm = (TextMessage) consumer.receive();
        assertNotNull(tm);
        assertEquals("msg1", tm.getText());

        consumerSession.commit();

        _logger.info("Received and committed first message");
        tm = (TextMessage) consumer.receive(1000);
        assertNotNull(tm);
        assertEquals("msg2", tm.getText());

        tm = (TextMessage) consumer.receive(1000);
        assertNotNull(tm);
        assertEquals("msg3", tm.getText());

        tm = (TextMessage) consumer.receive(1000);
        assertNotNull(tm);
        assertEquals("msg4", tm.getText());

        _logger.info("Received all four messages. Closing connection with three outstanding messages");

        consumerSession.close();

        consumerSession = con.createSession(true, Session.SESSION_TRANSACTED);

        consumer = consumerSession.createConsumer(queue3);

        // no ack for last three messages so when I call recover I expect to get three messages back
        tm = (TextMessage) consumer.receive(3000);
        assertNotNull(tm);
        assertEquals("msg2", tm.getText());
        assertTrue("Message is not redelivered", tm.getJMSRedelivered());

        tm = (TextMessage) consumer.receive(3000);
        assertNotNull(tm);
        assertEquals("msg3", tm.getText());
        assertTrue("Message is not redelivered", tm.getJMSRedelivered());

        tm = (TextMessage) consumer.receive(3000);
        assertNotNull(tm);
        assertEquals("msg4", tm.getText());
        assertTrue("Message is not redelivered", tm.getJMSRedelivered());

        _logger.info("Received redelivery of three messages. Committing");

        consumerSession.commit();

        _logger.info("Called commit");

        tm = (TextMessage) consumer.receive(1000);
        assertNull(tm);

        _logger.info("No messages redelivered as is expected");

        con.close();
        con2.close();
    }

    private void expect(String text, Message msg) throws JMSException
    {
        expect(text, msg, false);
    }

    private void expect(String text, Message msg, boolean requeued) throws JMSException
    {
        assertNotNull("Message should not be null", msg);
        assertTrue("Message should be a text message", msg instanceof TextMessage);
        assertEquals("Message content does not match expected", text, ((TextMessage) msg).getText());
        assertEquals("Message should " + (requeued ? "" : "not") + " be requeued", requeued, msg.getJMSRedelivered());
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(TransactedTest.class);
    }
}
