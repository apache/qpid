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

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import junit.framework.TestCase;

import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;

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
    private static final Logger _logger = Logger.getLogger(TransactedTest.class);

    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);
        queue1 = new AMQQueue(new AMQShortString("Q1"), new AMQShortString("Q1"), false, true);
        queue2 = new AMQQueue("Q2", false);

        con = new AMQConnection("vm://:1", "guest", "guest", "TransactedTest", "test");
        session = con.createSession(true, 0);
        consumer1 = session.createConsumer(queue1);
        //Dummy just to create the queue. 
        MessageConsumer consumer2 = session.createConsumer(queue2);
        consumer2.close();
        producer2 = session.createProducer(queue2);
        con.start();

        prepCon = new AMQConnection("vm://:1", "guest", "guest", "PrepConnection", "test");
        prepSession = prepCon.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        prepProducer1 = prepSession.createProducer(queue1);
        prepCon.start();


        //add some messages
        prepProducer1.send(prepSession.createTextMessage("A"));
        prepProducer1.send(prepSession.createTextMessage("B"));
        prepProducer1.send(prepSession.createTextMessage("C"));

        testCon = new AMQConnection("vm://:1", "guest", "guest", "TestConnection", "test");
        testSession = testCon.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        testConsumer2 = testSession.createConsumer(queue2);

    }

    protected void tearDown() throws Exception
    {
        con.close();
        testCon.close();
        prepCon.close();
        TransportConnection.killAllVMBrokers();
        super.tearDown();
    }

    public void testCommit() throws Exception
    {
        //send and receive some messages
        producer2.send(session.createTextMessage("X"));
        producer2.send(session.createTextMessage("Y"));
        producer2.send(session.createTextMessage("Z"));
        expect("A", consumer1.receive(1000));
        expect("B", consumer1.receive(1000));
        expect("C", consumer1.receive(1000));

        //commit
        session.commit();
        testCon.start();
        //ensure sent messages can be received and received messages are gone
        expect("X", testConsumer2.receive(1000));
        expect("Y", testConsumer2.receive(1000));
        expect("Z", testConsumer2.receive(1000));

        testConsumer1 = testSession.createConsumer(queue1);
        assertTrue(null == testConsumer1.receive(1000));
        assertTrue(null == testConsumer2.receive(1000));
    }

    public void testRollback() throws Exception
    {
        producer2.send(session.createTextMessage("X"));
        producer2.send(session.createTextMessage("Y"));
        producer2.send(session.createTextMessage("Z"));
        expect("A", consumer1.receive(1000));
        expect("B", consumer1.receive(1000));
        expect("C", consumer1.receive(1000));

        //rollback
        session.rollback();

        //ensure sent messages are not visible and received messages are requeued
        expect("A", consumer1.receive(1000));
        expect("B", consumer1.receive(1000));
        expect("C", consumer1.receive(1000));
        testCon.start();
        testConsumer1 = testSession.createConsumer(queue1);
        assertTrue(null == testConsumer1.receive(1000));
        assertTrue(null == testConsumer2.receive(1000));
    }

    public void testResendsMsgsAfterSessionClose() throws Exception
    {
        Connection con = new AMQConnection("vm://:1", "guest", "guest", "consumer1", "test");

        Session consumerSession = con.createSession(true, Session.CLIENT_ACKNOWLEDGE);
        AMQQueue queue3 = new AMQQueue("Q3", false);
        MessageConsumer consumer = consumerSession.createConsumer(queue3);
        //force synch to ensure the consumer has resulted in a bound queue
        ((AMQSession) consumerSession).declareExchangeSynch(ExchangeDefaults.DIRECT_EXCHANGE_NAME, ExchangeDefaults.DIRECT_EXCHANGE_CLASS);

        Connection con2 = new AMQConnection("vm://:1", "guest", "guest", "producer1", "test");
        Session producerSession = con2.createSession(true, Session.CLIENT_ACKNOWLEDGE);
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

        tm.acknowledge();
        consumerSession.commit();

        _logger.info("Received and acknowledged first message");
        tm = (TextMessage) consumer.receive(1000);
        assertNotNull(tm);
        tm = (TextMessage) consumer.receive(1000);
        assertNotNull(tm);
        tm = (TextMessage) consumer.receive(1000);
        assertNotNull(tm);
        _logger.info("Received all four messages. Closing connection with three outstanding messages");

        consumerSession.close();

        consumerSession = con.createSession(true, Session.CLIENT_ACKNOWLEDGE);

        consumer = consumerSession.createConsumer(queue3);

        // no ack for last three messages so when I call recover I expect to get three messages back

        tm = (TextMessage) consumer.receive(3000);
        assertNotNull(tm);
        assertEquals("msg2", tm.getText());

        tm = (TextMessage) consumer.receive(3000);
        assertNotNull(tm);
        assertEquals("msg3", tm.getText());

        tm = (TextMessage) consumer.receive(3000);
        assertNotNull(tm);
        assertEquals("msg4", tm.getText());

        _logger.info("Received redelivery of three messages. Acknowledging last message");
        tm.acknowledge();
        consumerSession.commit();
        _logger.info("Calling acknowledge with no outstanding messages");
        // all acked so no messages to be delivered


        tm = (TextMessage) consumer.receiveNoWait();
        assertNull(tm);
        _logger.info("No messages redelivered as is expected");

        con.close();
        con2.close();

    }


    private void expect(String text, Message msg) throws JMSException
    {
        assertTrue(msg instanceof TextMessage);
        assertEquals(text, ((TextMessage) msg).getText());
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(TransactedTest.class);
    }
}
