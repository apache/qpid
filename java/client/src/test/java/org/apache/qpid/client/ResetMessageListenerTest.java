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
package org.apache.qpid.client;

import junit.framework.TestCase;

import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.jndi.PropertiesFileInitialContextFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.spi.InitialContextFactory;

import java.util.Hashtable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * QPID-293 Setting MessageListener after connection has started can cause messages to be "lost" on a internal delivery
 * queue <p/> The message delivery process: Mina puts a message on _queue in AMQSession and the dispatcher thread
 * take()s from here and dispatches to the _consumers. If the _consumer1 doesn't have a message listener set at
 * connection start then messages are stored on _synchronousQueue (which needs to be > 1 to pass JMS TCK as multiple
 * consumers on a session can run in any order and a synchronous put/poll will block the dispatcher). <p/> When setting
 * the message listener later the _synchronousQueue is just poll()'ed and the first message delivered the remaining
 * messages will be left on the queue and lost, subsequent messages on the session will arrive first.
 */
public class ResetMessageListenerTest extends TestCase
{
    private static final Logger _logger = LoggerFactory.getLogger(ResetMessageListenerTest.class);

    Context _context;

    private static final int MSG_COUNT = 6;
    private int receivedCount1ML1 = 0;
    private int receivedCount1ML2 = 0;
    private int receivedCount2 = 0;
    private Connection _clientConnection, _producerConnection;
    private MessageConsumer _consumer1;
    private MessageConsumer _consumer2;
    MessageProducer _producer;
    Session _clientSession, _producerSession;

    private final CountDownLatch _allFirstMessagesSent = new CountDownLatch(2); // all messages Sent Lock
    private final CountDownLatch _allSecondMessagesSent = new CountDownLatch(2); // all messages Sent Lock

    private String oldImmediatePrefetch;

    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);

        oldImmediatePrefetch = System.getProperty(AMQSession.IMMEDIATE_PREFETCH);
        System.setProperty(AMQSession.IMMEDIATE_PREFETCH, "true");

        InitialContextFactory factory = new PropertiesFileInitialContextFactory();

        Hashtable<String, String> env = new Hashtable<String, String>();

        env.put("connectionfactory.connection", "amqp://guest:guest@MLT_ID/test?brokerlist='vm://:1'");
        env.put("queue.queue", "direct://amq.direct//ResetMessageListenerTest");

        _context = factory.getInitialContext(env);

        Queue queue = (Queue) _context.lookup("queue");

        // Create Client 1
        _clientConnection = ((ConnectionFactory) _context.lookup("connection")).createConnection();

        _clientSession = _clientConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        _consumer1 = _clientSession.createConsumer(queue);

        // Create Client 2 on same session
        _consumer2 = _clientSession.createConsumer(queue);

        // Create Producer
        _producerConnection = ((ConnectionFactory) _context.lookup("connection")).createConnection();

        _producerConnection.start();

        _producerSession = _producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        _producer = _producerSession.createProducer(queue);

        for (int msg = 0; msg < MSG_COUNT; msg++)
        {
            _producer.send(_producerSession.createTextMessage("Message " + msg));
        }
//        Thread.sleep(120000);

    }

    protected void tearDown() throws Exception
    {       
        _clientConnection.close();
        _producerConnection.close();

        super.tearDown();
        if (oldImmediatePrefetch == null)
        {
            oldImmediatePrefetch = AMQSession.IMMEDIATE_PREFETCH_DEFAULT;
        }
        System.setProperty(AMQSession.IMMEDIATE_PREFETCH, oldImmediatePrefetch);
        TransportConnection.killAllVMBrokers();
    }

    public void testAsynchronousRecieve()
    {

        _logger.info("Test Start");

        // Set default Message Listener
        try
        {
            _consumer1.setMessageListener(new MessageListener()
                {
                    public void onMessage(Message message)
                    {
                        _logger.info("Client 1 ML 1 Received Message(" + receivedCount1ML1 + "):" + message);

                        receivedCount1ML1++;
                        if (receivedCount1ML1 == (MSG_COUNT / 2))
                        {
                            _allFirstMessagesSent.countDown();
                        }
                    }
                });
        }
        catch (JMSException e)
        {
            _logger.error("Error Setting Default ML on consumer1");
        }

        try
        {
            _consumer2.setMessageListener(new MessageListener()
                {
                    public void onMessage(Message message)
                    {
                        _logger.info("Client 2 Received Message(" + receivedCount2 + "):" + message);

                        receivedCount2++;
                        if (receivedCount2 == (MSG_COUNT / 2))
                        {
                            _logger.info("Client 2 received all its messages1");
                            _allFirstMessagesSent.countDown();
                        }

                        if (receivedCount2 == MSG_COUNT)
                        {
                            _logger.info("Client 2 received all its messages2");
                            _allSecondMessagesSent.countDown();
                        }
                    }
                });

            _clientConnection.start();
        }
        catch (JMSException e)
        {
            _logger.error("Error Setting Default ML on consumer2");

        }

        try
        {
            _allFirstMessagesSent.await(1000, TimeUnit.MILLISECONDS);
            _logger.info("Received first batch of messages");
        }
        catch (InterruptedException e)
        {
            // do nothing
        }

        try
        {
            _clientConnection.stop();
        }
        catch (JMSException e)
        {
            _logger.error("Error stopping connection");
        }

        _logger.info("Reset Message Listener to better listener while connection stopped, will restart session");
        try
        {
            _consumer1.setMessageListener(new MessageListener()
                {
                    public void onMessage(Message message)
                    {
                        _logger.info("Client 1 ML2 Received Message(" + receivedCount1ML1 + "):" + message);

                        receivedCount1ML2++;
                        if (receivedCount1ML2 == (MSG_COUNT / 2))
                        {
                            _allSecondMessagesSent.countDown();
                        }
                    }
                });

            _clientConnection.start();
        }
        catch (javax.jms.IllegalStateException e)
        {
            _logger.error("Connection not stopped while setting ML", e);
            fail("Unable to change message listener:" + e.getCause());
        }
        catch (JMSException e)
        {
            _logger.error("Error Setting Better ML on consumer1", e);
        }

        try
        {
            _logger.info("Send additional messages");

            for (int msg = 0; msg < MSG_COUNT; msg++)
            {
                _producer.send(_producerSession.createTextMessage("Message " + msg));
            }
//            try
//            {
//                Thread.sleep(120000);
//            }
//            catch (InterruptedException e)
//            {
//                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//            }
        }
        catch (JMSException e)
        {
            _logger.error("Unable to send additional messages", e);
        }

        _logger.info("Waiting upto 2 seconds for messages");

        try
        {
            _allSecondMessagesSent.await(500000, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            // do nothing
        }
        assertEquals("First batch of messages not received correctly", 0, _allFirstMessagesSent.getCount());
        assertEquals("Second batch of messages not received correctly", 0, _allSecondMessagesSent.getCount());
        assertEquals("Client 1 ML1 didn't get all messages", MSG_COUNT / 2, receivedCount1ML1);
        assertEquals("Client 2 didn't get all messages", MSG_COUNT, receivedCount2);
        assertEquals("Client 1 ML2 didn't get all messages", MSG_COUNT / 2, receivedCount1ML2);
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(ResetMessageListenerTest.class);
    }
}
