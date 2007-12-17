package org.apache.qpid.test.client;

import org.apache.qpid.test.VMTestCase;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.NamingException;
import java.util.concurrent.CountDownLatch;/*
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

public class DupsOkTest extends VMTestCase
{

    private Queue _queue;
    private static final int MSG_COUNT = 9999;
    private CountDownLatch _awaitCompletion = new CountDownLatch(1);

    public void setUp() throws Exception
    {
        super.setUp();

        _queue = (Queue) _context.lookup("queue");

        //CreateQueue
        ((ConnectionFactory) _context.lookup("connection")).createConnection().createSession(false, Session.AUTO_ACKNOWLEDGE).createConsumer(_queue).close();

        //Create Producer put some messages on the queue
        Connection producerConnection = ((ConnectionFactory) _context.lookup("connection")).createConnection();

        producerConnection.start();

        Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        MessageProducer producer = producerSession.createProducer(_queue);

        for (int count = 1; count <= MSG_COUNT; count++)
        {
            Message msg = producerSession.createTextMessage("Message " + count);
            msg.setIntProperty("count", count);
            producer.send(msg);
        }

        producerConnection.close();
    }

    public void testDupsOK() throws NamingException, JMSException, InterruptedException
    {
        //Create Client
        Connection clientConnection = ((ConnectionFactory) _context.lookup("connection")).createConnection();

        clientConnection.start();

        Session clientSession = clientConnection.createSession(false, Session.DUPS_OK_ACKNOWLEDGE);

        MessageConsumer consumer = clientSession.createConsumer(_queue);

        consumer.setMessageListener(new MessageListener()
        {
            public void onMessage(Message message)
            {
                if (message == null)
                {
                    fail("Should not get null messages");
                }

                if (message instanceof TextMessage)
                {
                    try
                    {
                        /*if (message.getIntProperty("count") == 5000)
                        {
                            assertEquals("The queue should have 4999 msgs left", 4999, getMessageCount(_queue.getQueueName()));
                        }*/

                        if (message.getIntProperty("count") == 9999)
                        {
                            assertEquals("The queue should have 0 msgs left", 0, getMessageCount(_queue.getQueueName()));

                            //This is the last message so release test.
                            _awaitCompletion.countDown();
                        }

                    }
                    catch (JMSException e)
                    {
                        fail("Unable to get int property 'count'");
                    }
                }
                else
                {
                    fail("");
                }
            }
        });

        try
        {
            _awaitCompletion.await();
        }
        catch (InterruptedException e)
        {
            fail("Unable to wait for test completion");
            throw e;
        }

//        consumer.close();

        clientConnection.close();

        assertEquals("The queue should have 0 msgs left", 0, getMessageCount(_queue.getQueueName()));
    }


}
