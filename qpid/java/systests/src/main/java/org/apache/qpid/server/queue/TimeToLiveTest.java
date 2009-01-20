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

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.test.utils.QpidTestCase;


public class TimeToLiveTest extends QpidTestCase
{
    private static final Logger _logger = Logger.getLogger(TimeToLiveTest.class);

    protected final String QUEUE = "TimeToLiveQueue";

    private final long TIME_TO_LIVE = 100L;

    private static final int MSG_COUNT = 50;
    private static final long SERVER_TTL_TIMEOUT = 60000L;

    public void testPassiveTTL() throws Exception
    {
        //Create Client 1
        Connection clientConnection = getConnection();
        
        Session clientSession = clientConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = clientSession.createQueue(QUEUE); 
            
        MessageConsumer consumer = clientSession.createConsumer(queue);

        //Create Producer
        Connection producerConnection = getConnection();

        producerConnection.start();

        Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        MessageProducer producer = producerSession.createProducer(queue);

        //Set TTL
        int msg = 0;
        producer.send(nextMessage(String.valueOf(msg), true, producerSession, producer));

        producer.setTimeToLive(TIME_TO_LIVE);

        for (; msg < MSG_COUNT - 2; msg++)
        {
            producer.send(nextMessage(String.valueOf(msg), false, producerSession, producer));
        }

        //Reset TTL
        producer.setTimeToLive(0L);
        producer.send(nextMessage(String.valueOf(msg), false, producerSession, producer));

         try
        {
            // Sleep to ensure TTL reached
            Thread.sleep(2000);
        }
        catch (InterruptedException e)
        {

        }

        clientConnection.start();

        //Receive Message 0
        Message receivedFirst = consumer.receive(1000);
        Message receivedSecond = consumer.receive(1000);
        Message receivedThird = consumer.receive(1000);
        
        // Only first and last messages sent should survive expiry
        Assert.assertNull("More messages received", receivedThird); 

        Assert.assertNotNull("First message not received", receivedFirst);
        Assert.assertTrue("First message doesn't have first set.", receivedFirst.getBooleanProperty("first"));
        Assert.assertEquals("First message has incorrect TTL.", 0L, receivedFirst.getLongProperty("TTL"));

        Assert.assertNotNull("Final message not received", receivedSecond);
        Assert.assertFalse("Final message has first set.", receivedSecond.getBooleanProperty("first"));
        Assert.assertEquals("Final message has incorrect TTL.", 0L, receivedSecond.getLongProperty("TTL"));

        clientConnection.close();

        producerConnection.close();
    }

    private Message nextMessage(String msg, boolean first, Session producerSession, MessageProducer producer) throws JMSException
    {
        Message send = producerSession.createTextMessage("Message " + msg);
        send.setBooleanProperty("first", first);
        send.setLongProperty("TTL", producer.getTimeToLive());
        return send;
    }


    /**
     * Tests the expired messages get actively deleted even on queues which have no consumers
     * @throws Exception 
     */
    public void testActiveTTL() throws Exception
    {
        Connection producerConnection = getConnection();
        AMQSession producerSession = (AMQSession) producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = producerSession.createTemporaryQueue();
        producerSession.declareAndBind((AMQDestination) queue);
        MessageProducer producer = producerSession.createProducer(queue);
        producer.setTimeToLive(1000L);

        // send Messages
        for(int i = 0; i < MSG_COUNT; i++)
        {
            producer.send(producerSession.createTextMessage("Message: "+i));
        }
        long failureTime = System.currentTimeMillis() + 2*SERVER_TTL_TIMEOUT;

        // check Queue depth for up to TIMEOUT seconds
        long messageCount;

        do
        {
            Thread.sleep(100);
            messageCount = producerSession.getQueueDepth((AMQDestination) queue);
        }
        while(messageCount > 0L && System.currentTimeMillis() < failureTime);

        assertEquals("Messages not automatically expired: ", 0L, messageCount);

        producer.close();
        producerSession.close();
        producerConnection.close();
    }

}
