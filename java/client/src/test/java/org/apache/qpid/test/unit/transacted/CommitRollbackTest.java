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
package org.apache.qpid.test.unit.transacted;

import junit.framework.TestCase;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.AMQException;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.log4j.Logger;

import javax.jms.Session;
import javax.jms.MessageProducer;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;

/**
 * This class tests a number of commits and roll back scenarios
 *
 * Assumptions; - Assumes empty Queue
 */
public class CommitRollbackTest extends TestCase
{
    protected AMQConnection conn;
    protected final String queue = "direct://amq.direct//Qpid.Client.Transacted.CommitRollback.queue";
    protected String payload = "xyzzy";
    private Session _session;
    private MessageProducer _publisher;
    private Session _pubSession;
    private MessageConsumer _consumer;
    Queue _jmsQueue;

    private static final Logger _logger = Logger.getLogger(CommitRollbackTest.class);

    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);
        newConnection();
    }

    private void newConnection() throws AMQException, URLSyntaxException, JMSException
    {
        conn = new AMQConnection("amqp://guest:guest@client/test?brokerlist='vm://:1'");

        _session = conn.createSession(true, Session.CLIENT_ACKNOWLEDGE);

        _jmsQueue = _session.createQueue(queue);
        _consumer = _session.createConsumer(_jmsQueue);

        _pubSession = conn.createSession(true, Session.CLIENT_ACKNOWLEDGE);

        _publisher = _pubSession.createProducer(_pubSession.createQueue(queue));

        conn.start();
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();

        conn.close();
        TransportConnection.killVMBroker(1);
    }

    /** PUT a text message, disconnect before commit, confirm it is gone. */
    public void testPutThenDisconnect() throws Exception
    {
        assertTrue("session is not transacted", _session.getTransacted());
        assertTrue("session is not transacted", _pubSession.getTransacted());


        _logger.info("sending test message");
        String MESSAGE_TEXT = "testPutThenDisconnect";
        _publisher.send(_pubSession.createTextMessage(MESSAGE_TEXT));

        _logger.info("reconnecting without commit");
        conn.close();

        newConnection();

        _logger.info("receiving result");
        Message result = _consumer.receive(1000);

        //commit to ensure message is removed from queue
        _session.commit();

        assertNull("test message was put and disconnected before commit, but is still present", result);
    }

    /** PUT a text message, disconnect before commit, confirm it is gone. */
    public void testPutThenCloseDisconnect() throws Exception
    {
        assertTrue("session is not transacted", _session.getTransacted());
        assertTrue("session is not transacted", _pubSession.getTransacted());


        _logger.info("sending test message");
        String MESSAGE_TEXT = "testPutThenDisconnect";
        _publisher.send(_pubSession.createTextMessage(MESSAGE_TEXT));

        _logger.info("closing publisher without commit");
        _publisher.close();

        _logger.info("reconnecting without commit");
        conn.close();

        newConnection();

        _logger.info("receiving result");
        Message result = _consumer.receive(1000);

        //commit to ensure message is removed from queue
        _session.commit();

        assertNull("test message was put and disconnected before commit, but is still present", result);
    }

    /**
     * PUT a text message, rollback, confirm message is gone. The consumer is on the same connection but different
     * session as producer
     */
    public void testPutThenRollback() throws Exception
    {
        assertTrue("session is not transacted", _session.getTransacted());
        assertTrue("session is not transacted", _pubSession.getTransacted());


        _logger.info("sending test message");
        String MESSAGE_TEXT = "testPutThenRollback";
        _publisher.send(_pubSession.createTextMessage(MESSAGE_TEXT));

        _logger.info("rolling back");
        _pubSession.rollback();

        _logger.info("receiving result");
        Message result = _consumer.receive(1000);

        assertNull("test message was put and rolled back, but is still present", result);
    }

    /** GET a text message, disconnect before commit, confirm it is still there. The consumer is on a new connection */
    public void testGetThenDisconnect() throws Exception
    {
        assertTrue("session is not transacted", _session.getTransacted());
        assertTrue("session is not transacted", _pubSession.getTransacted());

        _logger.info("sending test message");
        String MESSAGE_TEXT = "testGetThenDisconnect";
        _publisher.send(_pubSession.createTextMessage(MESSAGE_TEXT));

        _pubSession.commit();

        _logger.info("getting test message");

        Message msg = _consumer.receive(1000);
        assertNotNull("retrieved message is null", msg);

        _logger.info("closing connection");
        conn.close();

        newConnection();

        _logger.info("receiving result");
        Message result = _consumer.receive(1000);

        _session.commit();

        assertNotNull("test message was consumed and disconnected before commit, but is gone", result);
        assertEquals("test message was correct message", MESSAGE_TEXT, ((TextMessage) result).getText());
    }

    /**
     * GET a text message, close consumer, disconnect before commit, confirm it is still there. The consumer is on the
     * same connection but different session as producer
     */
    public void testGetThenCloseDisconnect() throws Exception
    {
        assertTrue("session is not transacted", _session.getTransacted());
        assertTrue("session is not transacted", _pubSession.getTransacted());

        _logger.info("sending test message");
        String MESSAGE_TEXT = "testGetThenCloseDisconnect";
        _publisher.send(_pubSession.createTextMessage(MESSAGE_TEXT));

        _pubSession.commit();

        _logger.info("getting test message");

        Message msg = _consumer.receive(1000);
        assertNotNull("retrieved message is null", msg);
        assertEquals("test message was correct message", MESSAGE_TEXT, ((TextMessage) msg).getText());

        _logger.info("reconnecting without commit");
        _consumer.close();
        conn.close();

        newConnection();

        _logger.info("receiving result");
        Message result = _consumer.receive(1000);

        _session.commit();

        assertNotNull("test message was consumed and disconnected before commit, but is gone", result);
        assertEquals("test message was correct message", MESSAGE_TEXT, ((TextMessage) result).getText());
    }

    /**
     * GET a text message, rollback, confirm it is still there. The consumer is on the same connection but differnt
     * session to the producer
     */
    public void testGetThenRollback() throws Exception
    {
        assertTrue("session is not transacted", _session.getTransacted());
        assertTrue("session is not transacted", _pubSession.getTransacted());

        _logger.info("sending test message");
        String MESSAGE_TEXT = "testGetThenDisconnect";
        _publisher.send(_pubSession.createTextMessage(MESSAGE_TEXT));

        _pubSession.commit();

        _logger.info("getting test message");

        Message msg = _consumer.receive(1000);

        assertNotNull("retrieved message is null", msg);
        assertEquals("test message was correct message", MESSAGE_TEXT, ((TextMessage) msg).getText());

        _logger.info("rolling back");

        _session.rollback();

        _logger.info("receiving result");

        Message result = _consumer.receive(1000);

        _session.commit();
        assertNotNull("test message was consumed and rolled back, but is gone", result);
        assertEquals("test message was correct message", MESSAGE_TEXT, ((TextMessage) result).getText());
        assertTrue("Messasge is not marked as redelivered", result.getJMSRedelivered());
    }

    /**
     * GET a text message, close message producer, rollback, confirm it is still there. The consumer is on the same
     * connection but different session as producer
     */
    public void testGetThenCloseRollback() throws Exception
    {
        assertTrue("session is not transacted", _session.getTransacted());
        assertTrue("session is not transacted", _pubSession.getTransacted());

        _logger.info("sending test message");
        String MESSAGE_TEXT = "testGetThenCloseRollback";
        _publisher.send(_pubSession.createTextMessage(MESSAGE_TEXT));

        _pubSession.commit();

        _logger.info("getting test message");

        Message msg = _consumer.receive(1000);

        assertNotNull("retrieved message is null", msg);
        assertEquals("test message was correct message", MESSAGE_TEXT, ((TextMessage) msg).getText());

        _logger.info("Closing consumer");
        _consumer.close();

        _logger.info("rolling back");
        _session.rollback();

        _logger.info("receiving result");

        _consumer = _session.createConsumer(_jmsQueue);

        Message result = _consumer.receive(1000);

        _session.commit();
        assertNotNull("test message was consumed and rolled back, but is gone", result);
        assertEquals("test message was correct message", MESSAGE_TEXT, ((TextMessage) result).getText());
        assertTrue("Messasge is not marked as redelivered", result.getJMSRedelivered());
    }


    /** Test that rolling back a session purges the dispatcher queue, and the messages arrive in the correct order */
    public void testSend2ThenRollback() throws Exception
    {
        assertTrue("session is not transacted", _session.getTransacted());
        assertTrue("session is not transacted", _pubSession.getTransacted());

        _logger.info("sending two test messages");
        _publisher.send(_pubSession.createTextMessage("1"));
        _publisher.send(_pubSession.createTextMessage("2"));
        _pubSession.commit();

        _logger.info("getting test message");
        assertEquals("1", ((TextMessage) _consumer.receive(1000)).getText());

        _logger.info("rolling back");
        _session.rollback();

        _logger.info("receiving result");
        Message result = _consumer.receive(1000);
        assertNotNull("test message was consumed and rolled back, but is gone", result);
        assertEquals("1", ((TextMessage) result).getText());
        assertTrue("Messasge is not marked as redelivered", result.getJMSRedelivered());

        result = _consumer.receive(1000);
        assertNotNull("test message was consumed and rolled back, but is gone", result);
        assertEquals("2", ((TextMessage) result).getText());
        assertTrue("Messasge is not marked as redelivered", result.getJMSRedelivered());

        result = _consumer.receive(1000);

        assertNull("test message should be null", result);
    }

    public void testSend2ThenCloseAfter1andTryAgain() throws Exception
    {
//        assertTrue("session is not transacted", _session.getTransacted());
//        assertTrue("session is not transacted", _pubSession.getTransacted());
//
//        _logger.info("sending two test messages");
//        _publisher.send(_pubSession.createTextMessage("1"));
//        _publisher.send(_pubSession.createTextMessage("2"));
//        _pubSession.commit();
//
//        _logger.info("getting test message");
//        assertEquals("1", ((TextMessage) _consumer.receive(1000)).getText());
//
//        _consumer.close();
//
//        _consumer = _session.createConsumer(_jmsQueue);
//
//        _logger.info("receiving result");
//        Message result = _consumer.receive(1000);
//        _logger.error("1:" + result);
////        assertNotNull("test message was consumed and rolled back, but is gone", result);
////        assertEquals("1" , ((TextMessage) result).getText());
////        assertTrue("Messasge is not marked as redelivered" + result, result.getJMSRedelivered());
//
//        result = _consumer.receive(1000);
//        _logger.error("2" + result);
////        assertNotNull("test message was consumed and rolled back, but is gone", result);
////        assertEquals("2", ((TextMessage) result).getText());
////        assertTrue("Messasge is marked as redelivered" + result, !result.getJMSRedelivered());
//
//        result = _consumer.receive(1000);
//        _logger.error("3" + result);
//        assertNull("test message should be null:" + result, result);
    }

}
