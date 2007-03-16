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

import java.util.Hashtable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.spi.InitialContextFactory;

import junit.framework.TestCase;

import org.apache.log4j.Logger;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.jndi.PropertiesFileInitialContextFactory;

/**
 * QPID-293 Setting MessageListener after connection has started can cause messages to be "lost" on a internal delivery
 * queue <p/> The message delivery process: Mina puts a message on _queue in AMQSession and the dispatcher thread
 * take()s from here and dispatches to the _consumers. If the _consumer1 doesn't have a message listener set at
 * connection start then messages are stored on _synchronousQueue (which needs to be > 1 to pass JMS TCK as multiple
 * consumers on a session can run in any order and a synchronous put/poll will block the dispatcher). <p/> When setting
 * the message listener later the _synchronousQueue is just poll()'ed and the first message delivered the remaining
 * messages will be left on the queue and lost, subsequent messages on the session will arrive first.
 */
public class MessageListenerMultiConsumerTest extends TestCase
{
    private static final Logger _logger = Logger.getLogger(MessageListenerMultiConsumerTest.class);

    Context _context;

    private static final int MSG_COUNT = 6;
    private int receivedCount1 = 0;
    private int receivedCount2 = 0;
    private Connection _clientConnection;
    private MessageConsumer _consumer1;
    private MessageConsumer _consumer2;

    private final CountDownLatch _allMessagesSent = new CountDownLatch(2); //all messages Sent Lock

    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);

        InitialContextFactory factory = new PropertiesFileInitialContextFactory();

        Hashtable<String, String> env = new Hashtable<String, String>();

        env.put("connectionfactory.connection", "amqp://guest:guest@MLT_ID/test?brokerlist='vm://:1'");
        env.put("queue.queue", "direct://amq.direct//MessageListenerTest");

        _context = factory.getInitialContext(env);

        Queue queue = (Queue) _context.lookup("queue");

        //Create Client 1
        _clientConnection = ((ConnectionFactory) _context.lookup("connection")).createConnection();

        _clientConnection.start();

        Session clientSession1 = _clientConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        _consumer1 = clientSession1.createConsumer(queue);

        //Create Client 2
        Session clientSession2 = _clientConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        _consumer2 = clientSession2.createConsumer(queue);

        //Create Producer
        Connection producerConnection = ((ConnectionFactory) _context.lookup("connection")).createConnection();

        producerConnection.start();


        Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        MessageProducer producer = producerSession.createProducer(queue);

        for (int msg = 0; msg < MSG_COUNT; msg++)
        {
            producer.send(producerSession.createTextMessage("Message " + msg));
        }

        producerConnection.close();

    }

    protected void tearDown() throws Exception
    {
        _clientConnection.close();

        super.tearDown();
        TransportConnection.killAllVMBrokers();
    }


    public void testRecieveC1thenC2() throws Exception
    {

        for (int msg = 0; msg < MSG_COUNT / 2; msg++)
        {

            assertTrue(_consumer1.receive() != null);
        }

        for (int msg = 0; msg < MSG_COUNT / 2; msg++)
        {
            assertTrue(_consumer2.receive() != null);
        }
    }

    public void testRecieveInterleaved() throws Exception
    {

        for (int msg = 0; msg < MSG_COUNT / 2; msg++)
        {
            assertTrue(_consumer1.receive() != null);
            assertTrue(_consumer2.receive() != null);
        }
    }


    public void testAsynchronousRecieve() throws Exception
    {
        _consumer1.setMessageListener(new MessageListener()
        {
            public void onMessage(Message message)
            {
                _logger.info("Client 1 Received Message(" + receivedCount1 + "):" + message);

                receivedCount1++;

                if (receivedCount1 == MSG_COUNT / 2)
                {
                    _allMessagesSent.countDown();                    
                }

            }
        });

        _consumer2.setMessageListener(new MessageListener()
        {
            public void onMessage(Message message)
            {
                _logger.info("Client 2 Received Message(" + receivedCount2 + "):" + message);

                receivedCount2++;
                if (receivedCount2 == MSG_COUNT / 2)
                {
                    _allMessagesSent.countDown();
                }
            }
        });


        _logger.info("Waiting upto 2 seconds for messages");

        try
        {
            _allMessagesSent.await(4000, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            //do nothing
        }

        assertEquals(MSG_COUNT, receivedCount1 + receivedCount2);
    }


    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(MessageListenerMultiConsumerTest.class);
    }
}
