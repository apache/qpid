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

package org.apache.qpid.test.client.failover;

import junit.framework.TestCase;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.jms.ConnectionListener;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.log4j.Logger;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.concurrent.CountDownLatch;

public class FailoverTest extends TestCase implements ConnectionListener
{
    private static final Logger _logger = Logger.getLogger(FailoverTest.class);

    private static final int NUM_BROKERS = 2;
    private static final String BROKER = "amqp://guest:guest@/test?brokerlist='vm://:%d;vm://:%d'";
    private static final String QUEUE = "queue";
    private static final int NUM_MESSAGES = 10;
    private Connection con;
    private AMQConnectionFactory conFactory;
    private Session prodSess;
    private AMQQueue q;
    private MessageProducer prod;
    private Session conSess;
    private MessageConsumer consumer;

    private static int usedBrokers = 0;
    private CountDownLatch failoverComplete;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        // Create two VM brokers

        for (int i = 0; i < NUM_BROKERS; i++)
        {
            usedBrokers++;

            TransportConnection.createVMBroker(usedBrokers);
        }

        conFactory = new AMQConnectionFactory(String.format(BROKER, usedBrokers - 1, usedBrokers));
        _logger.info("Connecting on:" + conFactory.getConnectionURL());
        con = conFactory.createConnection();
        ((AMQConnection) con).setConnectionListener(this);
        con.start();
        failoverComplete = new CountDownLatch(1);
    }

    private void init(boolean transacted, int mode) throws JMSException
    {
        prodSess = con.createSession(transacted, mode);
        q = new AMQQueue("amq.direct", QUEUE);
        prod = prodSess.createProducer(q);
        conSess = con.createSession(transacted, mode);
        consumer = conSess.createConsumer(q);
    }

    @Override
    protected void tearDown() throws Exception
    {
        try
        {
            con.close();
        }
        catch (Exception e)
        {

        }

        try
        {
            TransportConnection.killAllVMBrokers();
            ApplicationRegistry.removeAll();
        }
        catch (Exception e)
        {
            fail("Unable to clean up");
        }
        super.tearDown();
    }

    private void consumeMessages(int toConsume) throws JMSException
    {
        Message msg;
        for (int i = 0; i < toConsume; i++)
        {
            msg = consumer.receive(1000);
            assertNotNull("Message " + i + " was null!", msg);
            assertEquals("message " + i, ((TextMessage) msg).getText());
        }
    }

    private void sendMessages(int totalMessages) throws JMSException
    {
        for (int i = 0; i < totalMessages; i++)
        {
            prod.send(prodSess.createTextMessage("message " + i));
        }

//        try
//        {
//            Thread.sleep(100 * totalMessages);
//        }
//        catch (InterruptedException e)
//        {
//            //evil ignoring of IE
//        }
    }

    public void testP2PFailover() throws Exception
    {
        testP2PFailover(NUM_MESSAGES, true);
    }

    public void testP2PFailoverWithMessagesLeft() throws Exception
    {
        testP2PFailover(NUM_MESSAGES, false);
    }

    private void testP2PFailover(int totalMessages, boolean consumeAll) throws JMSException
    {
        Message msg = null;
        init(false, Session.AUTO_ACKNOWLEDGE);
        sendMessages(totalMessages);

        // Consume some messages
        int toConsume = totalMessages;
        if (!consumeAll)
        {
            toConsume = totalMessages / 2;
        }

        consumeMessages(toConsume);

        _logger.info("Failing over");

        causeFailure();

        msg = consumer.receive(500);
        //todo: reinstate
        assertNull("Should not have received message from new broker!", msg);
        // Check that messages still sent / received
        sendMessages(totalMessages);
        consumeMessages(totalMessages);
    }

    private void causeFailure()
    {
        _logger.info("Failover");

        TransportConnection.killVMBroker(usedBrokers - 1);
        ApplicationRegistry.remove(usedBrokers - 1);

        _logger.info("Awaiting Failover completion");
        try
        {
            failoverComplete.await();
        }
        catch (InterruptedException e)
        {
            //evil ignore IE.
        }
    }

    public void testClientAckFailover() throws Exception
    {
        init(false, Session.CLIENT_ACKNOWLEDGE);
        sendMessages(1);
        Message msg = consumer.receive();
        assertNotNull("Expected msgs not received", msg);


        causeFailure();

        Exception failure = null;
        try
        {
            msg.acknowledge();
        }
        catch (Exception e)
        {
            failure = e;
        }
        assertNotNull("Exception should be thrown", failure);
    }

    // This test disabled so that it doesn't add 4 minnutes to the length of time it takes to run, which would be lame
    public void txest4MinuteFailover() throws Exception
    {
        conFactory = new AMQConnectionFactory("amqp://guest:guest@/test?brokerlist='vm://:"+(usedBrokers-1)+"?connectdelay='60000'&retries='2''");
        _logger.info("Connecting on:" + conFactory.getConnectionURL());
        con = conFactory.createConnection();
        ((AMQConnection) con).setConnectionListener(this);
        con.start();

        long failTime = System.currentTimeMillis() + 60000;
        causeFailure();
        assertTrue("Failover did not take long enough", System.currentTimeMillis() > failTime);
    }

    public void bytesSent(long count)
    {
    }

    public void bytesReceived(long count)
    {
    }

    public boolean preFailover(boolean redirect)
    {
        return true;
    }

    public boolean preResubscribe()
    {
        return true;
    }

    public void failoverComplete()
    {
        failoverComplete.countDown();
    }
}
