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

package org.apache.qpid.server.txn;

import junit.framework.TestCase;
import junit.framework.Assert;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.jndi.PropertiesFileInitialContextFactory;
import org.apache.log4j.Logger;

import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.ConnectionFactory;
import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.TextMessage;
import javax.jms.MessageListener;
import javax.naming.spi.InitialContextFactory;
import javax.naming.Context;
import java.util.Hashtable;
import java.util.concurrent.CountDownLatch;


/** Test Case Qpid-617 */
public class TxnTest extends TestCase implements MessageListener
{
    private static final Logger _logger = Logger.getLogger(TxnTest.class);


    protected final String BROKER = "vm://:1";//"localhost";
    protected final String VHOST = "/test";
    protected final String QUEUE = "TxnTestQueue";


    Context _context;
    Queue _queue;

    private Connection _clientConnection, _producerConnection;

    private MessageConsumer _consumer;
    MessageProducer _producer;
    Session _clientSession, _producerSession;
    private CountDownLatch commit = new CountDownLatch(1);

    protected void setUp() throws Exception
    {
        if (BROKER.startsWith("vm://"))
        {
            TransportConnection.createVMBroker(1);
        }
        InitialContextFactory factory = new PropertiesFileInitialContextFactory();

        Hashtable<String, String> env = new Hashtable<String, String>();

        env.put("connectionfactory.connection", "amqp://guest:guest@TTL_TEST_ID" + VHOST + "?brokerlist='" + BROKER + "'");
        env.put("queue.queue", QUEUE);

        _context = factory.getInitialContext(env);

        _queue = (Queue) _context.lookup("queue");

        //Create Client 1
        _clientConnection = ((ConnectionFactory) _context.lookup("connection")).createConnection();

        _clientSession = _clientConnection.createSession(true, 0);

        _consumer = _clientSession.createConsumer(_queue);

        //Create Producer
        _producerConnection = ((ConnectionFactory) _context.lookup("connection")).createConnection();

        _producerConnection.start();

        _producerSession = _producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        _producer = _producerSession.createProducer(_queue);
    }

    protected void tearDown() throws Exception
    {
        if (_clientConnection != null)
        {
            _clientConnection.close();
        }

        if (_producerConnection != null)
        {
            _producerConnection.close();
        }
        
        super.tearDown();

        if (BROKER.startsWith("vm://"))
        {
            TransportConnection.killAllVMBrokers();
        }
    }


    public void testMessageListener() throws JMSException
    {
        _consumer.setMessageListener(this);
        _clientConnection.start();

        //Set TTL
        _producer.send(_producerSession.createTextMessage("TxtTestML"));


        try
        {
            //Wait for message to arrive
            commit.await();
        }
        catch (InterruptedException e)
        {

        }
        _consumer.close();

        _consumer = _clientSession.createConsumer(_queue);

        //Receive Message
        Message received = _consumer.receive(1000);
        assertNull("More messages received", received);

        _consumer.close();
    }

    public void onMessage(Message message)
    {

        try
        {
            assertEquals("Incorrect Message Received.", "TxtTestML", ((TextMessage) message).getText());

            _clientSession.commit();
        }
        catch (JMSException e)
        {
            fail("Failed to commit");
        }

        commit.countDown();
    }


    public void testReceive() throws JMSException
    {
        _clientConnection.start();

        //Set TTL
        _producer.send(_producerSession.createTextMessage("TxtTestReceive"));

        //Receive Message
        Message received = _consumer.receive(1000);

        assertEquals("Incorrect Message Received.", "TxtTestReceive", ((TextMessage) received).getText());
        //Receive Message

        received = _consumer.receive(1000);

        assertNull("More messages received", received);

        _consumer.close();
    }
}
