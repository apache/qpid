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
package org.apache.qpid.test.unit.client.connection;

import junit.framework.TestCase;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.transport.TransportConnection;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * ConnectionStartTest:
 * This test verifies that a fresh connection is not started and no messages are delivered until the connection is
 * started.
 *
 * After the connection is started then the message should be there, and the connection started.
 *
 * This Test verifies that using receive() and a messageListener does not cause message delivery before start is called.   
 *
 */
public class ConnectionStartTest extends TestCase
{

    String _broker = "vm://:1";

    AMQConnection _connection;
    private Session _consumerSess;
    private MessageConsumer _consumer;

    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);

        try
        {
            // Create Consumer Connection
            _connection = new AMQConnection(_broker, "guest", "guest", "fred", "test");

            _consumerSess = _connection.createSession(false, AMQSession.AUTO_ACKNOWLEDGE);

            Queue queue = _consumerSess.createQueue("ConnectionStartTest");

            _consumer = _consumerSess.createConsumer(queue);


            // Create Producer Connection to send message
            AMQConnection pubCon = new AMQConnection(_broker, "guest", "guest", "fred", "test");

            Session pubSess = pubCon.createSession(false, AMQSession.AUTO_ACKNOWLEDGE);

            MessageProducer pub = pubSess.createProducer(queue);

            pub.send(pubSess.createTextMessage("Initial Message"));

            pubCon.close();

        }
        catch (Exception e)
        {
            fail("Connection to " + _broker + " should succeed. Reason: " + e);
        }
    }

    protected void tearDown() throws Exception
    {
        _connection.close();
        TransportConnection.killVMBroker(1);
        super.tearDown();
    }

    public void testSimpleReceiveConnection()
    {
        try
        {
            assertTrue("Connection should not be started", !_connection.started());
            //Note that this next line will start the dispatcher in the session
            // should really not be called before _connection start
            assertNull("There should not be messages waiting for the consumer", _consumer.receiveNoWait());
            _connection.start();
            assertNotNull("There should be messages waiting for the consumer", _consumer.receive(1000));
            assertTrue("Connection should be started", _connection.started());

        }
        catch (JMSException e)
        {
            fail("An error occured during test because:" + e);
        }

    }

    public void testMessageListenerConnection()
    {
        final CountDownLatch _gotMessage = new CountDownLatch(1);

        try
        {
            assertTrue("Connection should not be started", !_connection.started());
            _consumerSess.setMessageListener(new MessageListener()
            {
                public void onMessage(Message message)
                {
                    try
                    {
                        assertTrue("Connection should be started", _connection.started());
                        assertEquals("Mesage Received", "Initial Message", ((TextMessage) message).getText());
                        _gotMessage.countDown();
                    }
                    catch (JMSException e)
                    {
                        fail("Couldn't get message text because:" + e.getCause());
                    }
                }
            });

            // Ensure that setting a ML doesn't start the connection
            assertTrue("Connection should not be started", !_connection.started());
            // Ensure that the message wasn't delivered while the connection was stopped.
            assertEquals("Message latch should still be set",1,_gotMessage.getCount());

            _connection.start();
            assertTrue("Connection should be started", _connection.started());

            try
            {
                _gotMessage.await(1000, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e)
            {
                fail("Timed out awaiting message via onMessage");
            }

        }
        catch (JMSException e)
        {
            fail("Failed because:" + e.getCause());
        }

    }


    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(ConnectionStartTest.class);
    }
}
