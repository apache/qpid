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

import org.apache.log4j.Logger;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.*;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.jndi.PropertiesFileInitialContextFactory;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Queue;
import javax.jms.MessageConsumer;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import java.util.Hashtable;
import java.util.Map;
import java.util.HashMap;

import junit.framework.TestCase;

public class ConflationQueueTest extends TestCase
{
    private static final int TIMEOUT = 1500;


    private static final Logger _logger = Logger.getLogger(ConflationQueueTest.class);


    protected final String BROKER = "vm://:1";
    protected final String VHOST = "/test";
    protected final String QUEUE = "PriorityQueue";

    private static final int MSG_COUNT = 4000;

    private Context context = null;
    private Connection producerConnection;
    private MessageProducer producer;
    private Session producerSession;
    private Queue queue;
    private Connection consumerConnection;
    private Session consumerSession;


    private MessageConsumer consumer;

    protected void setUp() throws Exception
    {
        super.setUp();

        if (usingInVMBroker())
        {
            TransportConnection.createVMBroker(1);
        }

        InitialContextFactory factory = new PropertiesFileInitialContextFactory();
        Hashtable<String, String> env = new Hashtable<String, String>();

        env.put("connectionfactory.connection", "amqp://guest:guest@PRIORITY_TEST_ID" + VHOST + "?brokerlist='" + BROKER + "'");
        env.put("queue.queue", QUEUE);

        context = factory.getInitialContext(env);
        producerConnection = ((ConnectionFactory) context.lookup("connection")).createConnection();
        producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        producerConnection.start();


    }

    private boolean usingInVMBroker()
    {
        return BROKER.startsWith("vm://");
    }

    protected void tearDown() throws Exception
    {
        producerConnection.close();
        consumerConnection.close();
        if (usingInVMBroker())
        {
            TransportConnection.killAllVMBrokers();
        }
        super.tearDown();
    }

    public void testConflation() throws JMSException, NamingException, AMQException, InterruptedException
    {
        consumerConnection = ((ConnectionFactory) context.lookup("connection")).createConnection();
        consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);


        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put("x-qpid-conflation-key","key");
        ((AMQSession) producerSession).createQueue(new AMQShortString(QUEUE), true, false, false, arguments);
        queue = new org.apache.qpid.client.AMQQueue("amq.direct",QUEUE);
        ((AMQSession) producerSession).declareAndBind((AMQDestination)queue);
        producer = producerSession.createProducer(queue);

        for (int msg = 0; msg < MSG_COUNT; msg++)
        {
            producer.send(nextMessage(msg, false, producerSession, producer));
            if(msg%10000 == 0)
            {
                System.err.println("Sent... " + msg);
                Thread.sleep(1000);
            }

        }

        producer.close();
        producerSession.close();
        producerConnection.close();

        consumer = consumerSession.createConsumer(queue);
        consumerConnection.start();
        Message received;
        int receivedCount = 0;
        while((received = consumer.receive(1000))!=null)
        {
            receivedCount++;
            System.err.println("Message: " + received.getIntProperty("msg") + " Conflation Key: " + received.getStringProperty("key"));
        }

        System.err.println("Received Count: " + receivedCount);



    }


    public void testConflationWithRelease() throws JMSException, NamingException, AMQException, InterruptedException
    {
        consumerConnection = ((ConnectionFactory) context.lookup("connection")).createConnection();
        consumerSession = consumerConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);


        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put("x-qpid-conflation-key","key");
        ((AMQSession) producerSession).createQueue(new AMQShortString(QUEUE), false, false, false, arguments);
        queue = new org.apache.qpid.client.AMQQueue("amq.direct",QUEUE);
        ((AMQSession) producerSession).declareAndBind((AMQDestination)queue);
        producer = producerSession.createProducer(queue);

        for (int msg = 0; msg < MSG_COUNT/2; msg++)
        {
            producer.send(nextMessage(msg, false, producerSession, producer));
            if(msg%10000 == 0)
            {
                System.err.println("Sent... " + msg);
                Thread.sleep(1000);
            }

        }

        // HACK to do something synchronous
        producerSession.createTemporaryQueue();

        consumer = consumerSession.createConsumer(queue);
        consumerConnection.start();
        Message received;
        int receivedCount = 0;
        while((received = consumer.receive(1000))!=null)
        {
            receivedCount++;
            System.err.println("Message: " + received.getIntProperty("msg") + " Conflation Key: " + received.getStringProperty("key"));
        }

        System.err.println("Received Count: " + receivedCount);

        consumerSession.close();
        consumerConnection.close();


        consumerConnection = ((ConnectionFactory) context.lookup("connection")).createConnection();
        consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);


        for (int msg = MSG_COUNT/2; msg < MSG_COUNT; msg++)
        {
            producer.send(nextMessage(msg, false, producerSession, producer));
            if(msg%10000 == 0)
            {
                System.err.println("Sent... " + msg);
                Thread.sleep(1000);
            }

        }


        consumer = consumerSession.createConsumer(queue);
        consumerConnection.start();
        receivedCount = 0;
        while((received = consumer.receive(1000))!=null)
        {
            receivedCount++;
            System.err.println("Message: " + received.getIntProperty("msg") + " Conflation Key: " + received.getStringProperty("key"));
        }

        System.err.println("Received Count: " + receivedCount);



    }



    public void testConflationWithReleaseAfterNewPublish() throws JMSException, NamingException, AMQException, InterruptedException
    {
        consumerConnection = ((ConnectionFactory) context.lookup("connection")).createConnection();
        consumerSession = consumerConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);


        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put("x-qpid-conflation-key","key");
        ((AMQSession) producerSession).createQueue(new AMQShortString(QUEUE), false, false, false, arguments);
        queue = new org.apache.qpid.client.AMQQueue("amq.direct",QUEUE);
        ((AMQSession) producerSession).declareAndBind((AMQDestination)queue);
        producer = producerSession.createProducer(queue);

        for (int msg = 0; msg < MSG_COUNT/2; msg++)
        {
            producer.send(nextMessage(msg, false, producerSession, producer));
            if(msg%10000 == 0)
            {
                System.err.println("Sent... " + msg);
                Thread.sleep(1000);
            }

        }

        // HACK to do something synchronous
        producerSession.createTemporaryQueue();

        consumer = consumerSession.createConsumer(queue);
        consumerConnection.start();
        Message received;
        int receivedCount = 0;
        while((received = consumer.receive(1000))!=null)
        {
            receivedCount++;
            System.err.println("Message: " + received.getIntProperty("msg") + " Conflation Key: " + received.getStringProperty("key"));
        }

        System.err.println("Received Count: " + receivedCount);

        consumer.close();

        for (int msg = MSG_COUNT/2; msg < MSG_COUNT; msg++)
        {
            producer.send(nextMessage(msg, false, producerSession, producer));
            if(msg%10000 == 0)
            {
                System.err.println("Sent... " + msg);
                Thread.sleep(1000);
            }

        }



        consumerSession.close();
        consumerConnection.close();


        consumerConnection = ((ConnectionFactory) context.lookup("connection")).createConnection();
        consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);



        consumer = consumerSession.createConsumer(queue);
        consumerConnection.start();
        receivedCount = 0;
        while((received = consumer.receive(1000))!=null)
        {
            receivedCount++;
            System.err.println("Message: " + received.getIntProperty("msg") + " Conflation Key: " + received.getStringProperty("key"));
        }

        System.err.println("Received Count: " + receivedCount);



    }




    private Message nextMessage(int msg, boolean first, Session producerSession, MessageProducer producer) throws JMSException
    {
        Message send = producerSession.createTextMessage("Message: " + msg);

        send.setStringProperty("key", String.valueOf(msg % 10));
        send.setIntProperty("msg", msg);

        return send;
    }


}

