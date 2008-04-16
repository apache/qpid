package org.apache.qpid.test.unit.ack;

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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.test.VMTestCase;

public class AcknowledgeTest extends VMTestCase
{
    private static final int NUM_MESSAGES = 50;
    private Connection _con;
    private Queue _queue;
    private MessageProducer _producer;
    private Session _producerSession;
	private Session _consumerSession;
	private MessageConsumer _consumerA;
	private MessageConsumer _consumerB;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _queue = (Queue) _context.lookup("queue");

        //CreateQueue
        ((ConnectionFactory) _context.lookup("connection")).createConnection().createSession(false, Session.AUTO_ACKNOWLEDGE).createConsumer(_queue).close();

        //Create Producer put some messages on the queue
        _con = ((ConnectionFactory) _context.lookup("connection")).createConnection();
        _con.start();
    }

	private void init(boolean transacted, int mode) throws JMSException {
		_producerSession = _con.createSession(false, Session.AUTO_ACKNOWLEDGE);
        _consumerSession = _con.createSession(transacted, mode);
        _producer = _producerSession.createProducer(_queue);
        _consumerA = _consumerSession.createConsumer(_queue);
	}

    @Override
    protected void tearDown() throws Exception
    {
    	super.tearDown();
    	try
    	{
    		TransportConnection.killAllVMBrokers();
    		ApplicationRegistry.removeAll();
    	}
    	catch (Exception e)
    	{
    		fail("Unable to clean up");
    	}

    }

    private void consumeMessages(int toConsume, MessageConsumer consumer) throws JMSException
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
            _producer.send(_producerSession.createTextMessage("message " + i));
        }
    }

    private void testMessageAck(boolean transacted, int mode) throws Exception
    {
    	init(transacted, mode);
        sendMessages(NUM_MESSAGES/2);
        Thread.sleep(1500);
        _consumerB = _consumerSession.createConsumer(_queue);
        sendMessages(NUM_MESSAGES/2);
        int count = 0;
        Message msg = _consumerB.receive(1500);
        while (msg != null) 
        {
        	if (mode == Session.CLIENT_ACKNOWLEDGE)
            {
        		msg.acknowledge();
            }
        	count++;
        	msg = _consumerB.receive(1500);
        }
        if (transacted)
        {
        	_consumerSession.commit();
        }  
        _consumerA.close();
        _consumerB.close();
        _consumerSession.close();
        assertEquals("Wrong number of messages on queue", NUM_MESSAGES - count,
                        ((AMQSession) _producerSession).getQueueDepth((AMQDestination) _queue));

        // Clean up messages that may be left on the queue
        _consumerSession = _con.createSession(transacted, mode);
        _consumerA = _consumerSession.createConsumer(_queue);
        msg = _consumerA.receive(1500);
        while (msg != null)
        {
            if (mode == Session.CLIENT_ACKNOWLEDGE)
            {
                msg.acknowledge();
            }
            msg = _consumerA.receive(1500);
        }
        _consumerA.close();
        if (transacted)
        {
            _consumerSession.commit();
        }
        _consumerSession.close();
        super.tearDown();
    }
    
    public void test2ConsumersAutoAck() throws Exception
    {
    	testMessageAck(false, Session.AUTO_ACKNOWLEDGE);
    }

    public void test2ConsumersClientAck() throws Exception
    {
    	testMessageAck(true, Session.CLIENT_ACKNOWLEDGE);
    }
    
    public void test2ConsumersTx() throws Exception
    {
    	testMessageAck(true, Session.AUTO_ACKNOWLEDGE);
    }
    
}
