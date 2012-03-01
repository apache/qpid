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

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.url.AMQBindingURL;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConflationQueueTest extends QpidBrokerTestCase
{
    private static final int TIMEOUT = 1500;


    private static final Logger _logger = Logger.getLogger(ConflationQueueTest.class);



    protected final String VHOST = "/test";
    protected final String QUEUE = "ConflationQueue";

    private static final int MSG_COUNT = 400;

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

        producerConnection = getConnection();
        producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        producerConnection.start();


    }

    protected void tearDown() throws Exception
    {
        producerConnection.close();
        consumerConnection.close();
        super.tearDown();
    }

    public void testConflation() throws Exception
    {
        consumerConnection = getConnection();
        consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);


        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put("qpid.last_value_queue_key","key");
        ((AMQSession) producerSession).createQueue(new AMQShortString(QUEUE), false, true, false, arguments);
        queue = new org.apache.qpid.client.AMQQueue("amq.direct",QUEUE);
        ((AMQSession) producerSession).declareAndBind((AMQDestination)queue);
        producer = producerSession.createProducer(queue);

        for (int msg = 0; msg < MSG_COUNT; msg++)
        {
            producer.send(nextMessage(msg, producerSession));
        }

        producer.close();
        producerSession.close();
        producerConnection.close();

        consumer = consumerSession.createConsumer(queue);
        consumerConnection.start();
        Message received;

        List<Message> messages = new ArrayList<Message>();
        while((received = consumer.receive(1000))!=null)
        {
            messages.add(received);
        }

        assertEquals("Unexpected number of messages received",10,messages.size());

        for(int i = 0 ; i < 10; i++)
        {
            Message msg = messages.get(i);
            assertEquals("Unexpected message number received", MSG_COUNT - 10 + i, msg.getIntProperty("msg"));
        }


    }


    public void testConflationWithRelease() throws Exception
    {
        consumerConnection = getConnection();
        consumerSession = consumerConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);


        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put("qpid.last_value_queue_key","key");
        ((AMQSession) producerSession).createQueue(new AMQShortString(QUEUE), false, true, false, arguments);
        queue = new org.apache.qpid.client.AMQQueue("amq.direct",QUEUE);
        ((AMQSession) producerSession).declareAndBind((AMQDestination)queue);
        producer = producerSession.createProducer(queue);

        for (int msg = 0; msg < MSG_COUNT/2; msg++)
        {
            producer.send(nextMessage(msg, producerSession));

        }

        // HACK to do something synchronous
        ((AMQSession)producerSession).sync();

        consumer = consumerSession.createConsumer(queue);
        consumerConnection.start();
        Message received;
        List<Message> messages = new ArrayList<Message>();
        while((received = consumer.receive(1000))!=null)
        {
            messages.add(received);
        }

        assertEquals("Unexpected number of messages received",10,messages.size());

        for(int i = 0 ; i < 10; i++)
        {
            Message msg = messages.get(i);
            assertEquals("Unexpected message number received", MSG_COUNT/2 - 10 + i, msg.getIntProperty("msg"));
        }

        consumerSession.close();
        consumerConnection.close();


        consumerConnection = getConnection();
        consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);


        for (int msg = MSG_COUNT/2; msg < MSG_COUNT; msg++)
        {
            producer.send(nextMessage(msg, producerSession));
        }


        // HACK to do something synchronous
        ((AMQSession)producerSession).sync();

        consumer = consumerSession.createConsumer(queue);
        consumerConnection.start();

        messages = new ArrayList<Message>();
        while((received = consumer.receive(1000))!=null)
        {
            messages.add(received);
        }

        assertEquals("Unexpected number of messages received",10,messages.size());

        for(int i = 0 ; i < 10; i++)
        {
            Message msg = messages.get(i);
            assertEquals("Unexpected message number received", MSG_COUNT - 10 + i, msg.getIntProperty("msg"));
        }

    }



    public void testConflationWithReleaseAfterNewPublish() throws Exception
    {
        consumerConnection = getConnection();
        consumerSession = consumerConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);


        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put("qpid.last_value_queue_key","key");
        ((AMQSession) producerSession).createQueue(new AMQShortString(QUEUE), false, true, false, arguments);
        queue = new org.apache.qpid.client.AMQQueue("amq.direct",QUEUE);
        ((AMQSession) producerSession).declareAndBind((AMQDestination)queue);
        producer = producerSession.createProducer(queue);

        for (int msg = 0; msg < MSG_COUNT/2; msg++)
        {
            producer.send(nextMessage(msg, producerSession));
        }

        // HACK to do something synchronous
        ((AMQSession)producerSession).sync();

        consumer = consumerSession.createConsumer(queue);
        consumerConnection.start();
        Message received;
        List<Message> messages = new ArrayList<Message>();
        while((received = consumer.receive(1000))!=null)
        {
            messages.add(received);
        }

        assertEquals("Unexpected number of messages received",10,messages.size());

        for(int i = 0 ; i < 10; i++)
        {
            Message msg = messages.get(i);
            assertEquals("Unexpected message number received", MSG_COUNT/2 - 10 + i, msg.getIntProperty("msg"));
        }

        consumer.close();

        for (int msg = MSG_COUNT/2; msg < MSG_COUNT; msg++)
        {
            producer.send(nextMessage(msg, producerSession));
        }

        // HACK to do something synchronous
        ((AMQSession)producerSession).sync();


        // this causes the "old" messages to be released
        consumerSession.close();
        consumerConnection.close();


        consumerConnection = getConnection();
        consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);



        consumer = consumerSession.createConsumer(queue);
        consumerConnection.start();

        messages = new ArrayList<Message>();
        while((received = consumer.receive(1000))!=null)
        {
            messages.add(received);
        }

        assertEquals("Unexpected number of messages received",10,messages.size());

        for(int i = 0 ; i < 10; i++)
        {
            Message msg = messages.get(i);
            assertEquals("Unexpected message number received", MSG_COUNT - 10 + i, msg.getIntProperty("msg"));
        }

    }

    public void testConflationBrowser() throws Exception
    {
        consumerConnection = getConnection();
        consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);


        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put("qpid.last_value_queue_key","key");
        ((AMQSession) producerSession).createQueue(new AMQShortString(QUEUE), false, true, false, arguments);
        queue = new org.apache.qpid.client.AMQQueue("amq.direct",QUEUE);
        ((AMQSession) producerSession).declareAndBind((AMQDestination)queue);
        producer = producerSession.createProducer(queue);

        for (int msg = 0; msg < MSG_COUNT; msg++)
        {
            producer.send(nextMessage(msg, producerSession));

        }

        ((AMQSession)producerSession).sync();

        AMQBindingURL url = new AMQBindingURL("direct://amq.direct//"+QUEUE+"?browse='true'&durable='true'");
        AMQQueue browseQueue = new AMQQueue(url);

        consumer = consumerSession.createConsumer(browseQueue);
        consumerConnection.start();
        Message received;
        List<Message> messages = new ArrayList<Message>();
        while((received = consumer.receive(1000))!=null)
        {
            messages.add(received);
        }

        assertEquals("Unexpected number of messages received",10,messages.size());

        for(int i = 0 ; i < 10; i++)
        {
            Message msg = messages.get(i);
            assertEquals("Unexpected message number received", MSG_COUNT - 10 + i, msg.getIntProperty("msg"));
        }

        messages.clear();

        producer.send(nextMessage(MSG_COUNT, producerSession));

        ((AMQSession)producerSession).sync();

        while((received = consumer.receive(1000))!=null)
        {
            messages.add(received);
        }
        assertEquals("Unexpected number of messages received",1,messages.size());
        assertEquals("Unexpected message number received", MSG_COUNT, messages.get(0).getIntProperty("msg"));


        producer.close();
        producerSession.close();
        producerConnection.close();



    }


    public void testConflation2Browsers() throws Exception
    {
        consumerConnection = getConnection();
        consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);


        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put("qpid.last_value_queue_key","key");
        ((AMQSession) producerSession).createQueue(new AMQShortString(QUEUE), false, true, false, arguments);
        queue = new org.apache.qpid.client.AMQQueue("amq.direct",QUEUE);
        ((AMQSession) producerSession).declareAndBind((AMQDestination)queue);
        producer = producerSession.createProducer(queue);

        for (int msg = 0; msg < MSG_COUNT; msg++)
        {
            producer.send(nextMessage(msg, producerSession));

        }

        ((AMQSession)producerSession).sync();

        AMQBindingURL url = new AMQBindingURL("direct://amq.direct//"+QUEUE+"?browse='true'&durable='true'");
        AMQQueue browseQueue = new AMQQueue(url);

        consumer = consumerSession.createConsumer(browseQueue);
        MessageConsumer consumer2 = consumerSession.createConsumer(browseQueue);
        consumerConnection.start();
        List<Message> messages = new ArrayList<Message>();
        List<Message> messages2 = new ArrayList<Message>();
        Message received  = consumer.receive(1000);
        Message received2  = consumer2.receive(1000);

        while(received!=null || received2!=null)
        {
            if(received != null)
            {
                messages.add(received);
            }
            if(received2 != null)
            {
                messages2.add(received2);
            }


            received  = consumer.receive(1000);
            received2  = consumer2.receive(1000);

        }

        assertEquals("Unexpected number of messages received on first browser",10,messages.size());
        assertEquals("Unexpected number of messages received on second browser",10,messages2.size());

        for(int i = 0 ; i < 10; i++)
        {
            Message msg = messages.get(i);
            assertEquals("Unexpected message number received on first browser", MSG_COUNT - 10 + i, msg.getIntProperty("msg"));
            msg = messages2.get(i);
            assertEquals("Unexpected message number received on second browser", MSG_COUNT - 10 + i, msg.getIntProperty("msg"));
        }


        producer.close();
        producerSession.close();
        producerConnection.close();



    }



    private Message nextMessage(int msg, Session producerSession) throws JMSException
    {
        Message send = producerSession.createTextMessage("Message: " + msg);

        send.setStringProperty("key", String.valueOf(msg % 10));
        send.setIntProperty("msg", msg);

        return send;
    }


}


