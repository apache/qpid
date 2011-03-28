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

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.log4j.Logger;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class MultipleTransactedBatchProducerTest extends QpidBrokerTestCase
{
    private static final Logger _logger = Logger.getLogger(MultipleTransactedBatchProducerTest.class);

    private static final int MESSAGE_COUNT = 1000;
    private static final int BATCH_SIZE = 50;
    private static final int NUM_PRODUCERS = 2;
    private static final int NUM_CONSUMERS = 3;
    private static final Random RANDOM = new Random();

    private CountDownLatch _receivedLatch;
    private String _queueName;

    private volatile String _failMsg;

    public void setUp() throws Exception
    {
        //debug level logging often makes this test pass artificially, turn the level down to info.
        setSystemProperty("amqj.server.logging.level", "INFO");
        _receivedLatch = new CountDownLatch(MESSAGE_COUNT * NUM_PRODUCERS);
        setConfigurationProperty("management.enabled", "true");
        super.setUp();
        _queueName = getTestQueueName();
        _failMsg = null;
    }

    /**
     * When there are multiple producers submitting batches of messages to a given
     * queue using transacted sessions, it is highly probable that concurrent
     * enqueue() activity will occur and attempt delivery of their message to the
     * same subscription. In this scenario it is likely that one of the attempts
     * will succeed and the other will result in use of the deliverAsync() method
     * to start a queue Runner and ensure delivery of the message.
     *
     * A defect within the processQueue() method used by the Runner would mean that
     * delivery of these messages may not occur, should the Runner stop before all
     * messages have been processed. Such a defect was discovered and found to be
     * most visible when Selectors are used such that one and only one subscription
     * can/will accept any given message, but multiple subscriptions are present,
     * and one of the earlier subscriptions receives more messages than the others.
     *
     * This test is to validate that the processQueue() method is able to correctly
     * deliver all of the messages present for asynchronous delivery to subscriptions,
     * by utilising multiple batch transacted producers to create the scenario and
     * ensure all messages are received by a consumer.
     */
    public void testMultipleBatchedProducersWithMultipleConsumersUsingSelectors() throws Exception
    {
        String selector1 = ("(\"" + _queueName +"\" % " + NUM_CONSUMERS + ") = 0");
        String selector2 = ("(\"" + _queueName +"\" % " + NUM_CONSUMERS + ") = 1");
        String selector3 = ("(\"" + _queueName +"\" % " + NUM_CONSUMERS + ") = 2");

        //create consumers
        Connection conn1 = getConnection();
        conn1.setExceptionListener(new ExceptionHandler("conn1"));
        Session sess1 = conn1.createSession(true, Session.SESSION_TRANSACTED);
        MessageConsumer cons1 = sess1.createConsumer(sess1.createQueue(_queueName), selector1);
        cons1.setMessageListener(new Cons(sess1,"consumer1"));

        Connection conn2 = getConnection();
        conn2.setExceptionListener(new ExceptionHandler("conn2"));
        Session sess2 = conn2.createSession(true, Session.SESSION_TRANSACTED);
        MessageConsumer cons2 = sess2.createConsumer(sess2.createQueue(_queueName), selector2);
        cons2.setMessageListener(new Cons(sess2,"consumer2"));

        Connection conn3 = getConnection();
        conn3.setExceptionListener(new ExceptionHandler("conn3"));
        Session sess3 = conn3.createSession(true, Session.SESSION_TRANSACTED);
        MessageConsumer cons3 = sess3.createConsumer(sess3.createQueue(_queueName), selector3);
        cons3.setMessageListener(new Cons(sess3,"consumer3"));

        conn1.start();
        conn2.start();
        conn3.start();

        //create producers
        Connection connA = getConnection();
        connA.setExceptionListener(new ExceptionHandler("connA"));
        Connection connB = getConnection();
        connB.setExceptionListener(new ExceptionHandler("connB"));
        Thread producer1 = new Thread(new ProducerThread(connA, _queueName, "producer1"));
        Thread producer2 = new Thread(new ProducerThread(connB, _queueName, "producer2"));

        producer1.start();
        Thread.sleep(10);
        producer2.start();

        //await delivery of the messages
        boolean result = _receivedLatch.await(75, TimeUnit.SECONDS);

        assertNull("Test failed because: " + String.valueOf(_failMsg), _failMsg);
        assertTrue("Some of the messages were not all recieved in the given timeframe, remaining count was: "+_receivedLatch.getCount(),
                   result);

    }

    @Override
    public Message createNextMessage(Session session, int msgCount) throws JMSException
    {
        Message message = super.createNextMessage(session,msgCount);

        //bias at least 50% of the messages to the first consumers selector because
        //the issue presents itself primarily when an earlier subscription completes
        //delivery after the later subscriptions
        int val;
        if (msgCount % 2 == 0)
        {
            val = 0;
        }
        else
        {
            val = RANDOM.nextInt(Integer.MAX_VALUE);
        }

        message.setIntProperty(_queueName, val);

        return message;
    }

    private class Cons implements MessageListener
    {
        private Session _sess;
        private String _desc;

        public Cons(Session sess, String desc)
        {
            _sess = sess;
            _desc = desc;
        }

        public void onMessage(Message message)
        {
            _receivedLatch.countDown();
            int msgCount = 0;
            int msgID = 0;
            try
            {
                msgCount = message.getIntProperty(INDEX);
                msgID = message.getIntProperty(_queueName);
            }
            catch (JMSException e)
            {
                _logger.error(_desc + " received exception: " + e.getMessage(), e);
                failAsyncTest(e.getMessage());
            }

            _logger.info("Consumer received message:"+ msgCount + " with ID: " + msgID);

            try
            {
                _sess.commit();
            }
            catch (JMSException e)
            {
                _logger.error(_desc + " received exception: " + e.getMessage(), e);
                failAsyncTest(e.getMessage());
            }
        }
    }

    private class ProducerThread implements Runnable
    {
        private Connection _conn;
        private String _dest;
        private String _desc;

        public ProducerThread(Connection conn, String dest, String desc)
        {
            _conn = conn;
            _dest = dest;
            _desc = desc;
        }

        public void run()
        {
            try
            {
                Session session = _conn.createSession(true, Session.SESSION_TRANSACTED);
                sendMessage(session, session.createQueue(_dest), MESSAGE_COUNT, BATCH_SIZE);
            }
            catch (Exception e)
            {
                _logger.error(_desc + " received exception: " + e.getMessage(), e);
                failAsyncTest(e.getMessage());
            }
        }
    }

    private class ExceptionHandler implements javax.jms.ExceptionListener
    {
        private String _desc;

        public ExceptionHandler(String description)
        {
            _desc = description;
        }

        public void onException(JMSException e)
        {
            _logger.error(_desc + " received exception: " + e.getMessage(), e);
            failAsyncTest(e.getMessage());
        }
    }

    private void failAsyncTest(String msg)
    {
        _logger.error("Failing test because: " + msg);
        _failMsg = msg;
    }
}