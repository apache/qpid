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
import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQSessionDirtyException;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.apache.qpid.jndi.PropertiesFileInitialContextFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;

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
import javax.jms.TransactionRolledBackException;
import javax.naming.Context;
import javax.naming.spi.InitialContextFactory;
import java.util.Hashtable;
import java.util.concurrent.CountDownLatch;


/** Test Case Qpid-617 */
public class TxnTest extends TestCase implements MessageListener
{
    private static final Logger _logger = Logger.getLogger(TxnTest.class);


    //Set retries quite high to ensure that it continues to retry whilst the InVM broker is restarted.
    protected final String BROKER = "vm://:1?retries='1000'";
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

        // Ensure that the queue is unique for each test run.
        // There appears to be other old sesssion/consumers when looping the tests this means that sometimes a message
        // will disappear. When it has actually gone to the old client.
        env.put("queue.queue", QUEUE + "-" + System.currentTimeMillis());

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

        _producer.send(_producerSession.createTextMessage("TxtTestReceive"));

        //Receive Message
        Message received = _consumer.receive(1000);

        _clientSession.commit();

        assertEquals("Incorrect Message Received.", "TxtTestReceive", ((TextMessage) received).getText());

        //Receive Message
        received = _consumer.receive(1000);

        assertNull("More messages received", received);

        _consumer.close();
    }

    /**
     * Test that after the connection has failed over that a sent message is still correctly receieved.
     * Using Auto-Ack consumer.
     *
     * @throws JMSException
     */
    public void testReceiveAfterFailover() throws JMSException
    {
//        System.err.println("testReceiveAfterFailover");
        _clientConnection.close();

        MessageConsumer consumer = _producerSession.createConsumer(_queue);

        failServer();

//        System.err.println("Server restarted");

        String MESSAGE_TXT = "TxtTestReceiveAfterFailoverTX";

//        System.err.println("Prod Session:" + _producerSession + ":" + ((AMQSession) _producerSession).isClosed());

        Message sent = _producerSession.createTextMessage(MESSAGE_TXT);
//        System.err.println("Created message");

        _producer.send(sent);
//        System.err.println("Sent message");

        //Verify correct message received
        Message received = consumer.receive(10000);
//        System.err.println("Message Receieved:" + received);

        assertNotNull("Message should be received.", received);
        assertEquals("Incorrect Message Received.", MESSAGE_TXT, ((TextMessage) received).getText());

        //Check no more messages are received
        received = consumer.receive(1000);
//        System.err.println("Second receive completed.");

        assertNull("More messages received", received);

        _producer.close();
//        System.err.println("Close producer");

        consumer.close();
//        System.err.println("Close consumer");

        _producerConnection.close();
    }

    /**
     * Test that after the connection has failed over the dirty transaction is notified when calling commit
     *
     * @throws JMSException
     */
    public void testSendBeforeFailoverThenCommitTx() throws JMSException
    {
//        System.err.println("testSendBeforeFailoverThenCommitTx");
        _clientConnection.start();

        //Create a transacted producer.
        MessageProducer txProducer = _clientSession.createProducer(_queue);

        String MESSAGE_TXT = "testSendBeforeFailoverThenCommitTx";

        //Send the first message
        txProducer.send(_clientSession.createTextMessage(MESSAGE_TXT));

        failServer();

        //Check that the message isn't received.
        Message received = _consumer.receive(1000);
        assertNull("Message received after failover to clean broker!", received);

        //Attempt to commit session
        try
        {
            _clientSession.commit();
            fail("TransactionRolledBackException not thrown");
        }
        catch (JMSException jmse)
        {
            if (!(jmse instanceof TransactionRolledBackException))
            {
                fail(jmse.toString());
            }
        }

        //Close consumer & producer
        _consumer.close();
        txProducer.close();
    }

    /**
     * Test that after the connection has failed over the dirty transaction is fast failed by throwing an
     * Exception on the next send.
     *
     * @throws JMSException
     */
    public void testSendBeforeFailoverThenSendTx() throws JMSException
    {
//        System.err.println("testSendBeforeFailoverThenSendTx");

        _clientConnection.start();
        MessageProducer txProducer = _clientSession.createProducer(_queue);

        String MESSAGE_TXT = "TxtTestSendBeforeFailoverTx";

        //Send the first message
        txProducer.send(_clientSession.createTextMessage(MESSAGE_TXT));

        failServer();

        //Check that the message isn't received.
        Message received = _consumer.receive(1000);
        assertNull("Message received after failover to clean broker!", received);

        //Attempt to send another message on the session, here we should fast fail.
        try
        {
            txProducer.send(_clientSession.createTextMessage(MESSAGE_TXT));
            fail("JMSException not thrown");
        }
        catch (JMSException jmse)
        {
            if (!(jmse.getLinkedException() instanceof AMQSessionDirtyException))
            {
                fail(jmse.toString());
            }
        }


        _consumer.close();
    }

    public void testSendBeforeFailoverThenSend2Tx() throws JMSException
    {
//        System.err.println("testSendBeforeFailoverThenSendTx");

        _clientConnection.start();
        MessageProducer txProducer = _clientSession.createProducer(_queue);

        String MESSAGE_TXT = "TxtTestSendBeforeFailoverTx";

        //Send the first message
        txProducer.send(_clientSession.createTextMessage(MESSAGE_TXT));

        failServer();

        //Check that the message isn't received.
        Message received = _consumer.receive(1000);
        assertNull("Message received after failover to clean broker!", received);

        _clientSession.rollback();

        //Attempt to send another message on the session, here we should fast fail.
        try
        {
            txProducer.send(_clientSession.createTextMessage(MESSAGE_TXT));
            txProducer.send(_clientSession.createTextMessage(MESSAGE_TXT));
            _clientSession.commit();
        }
        catch (JMSException jmse)
        {
            if (jmse.getLinkedException() instanceof AMQSessionDirtyException)
            {
                fail(jmse.toString());
            }
        }

        received = _consumer.receive(10000);
        assertNotNull("Message should be received.", received);
        assertEquals("Incorrect Message Received.", MESSAGE_TXT, ((TextMessage) received).getText());

        received = _consumer.receive(10000);
        assertNotNull("Message should be received.", received);
        assertEquals("Incorrect Message Received.", MESSAGE_TXT, ((TextMessage) received).getText());

        //Check that the message isn't received.
        received = _consumer.receive(1000);
        assertNull("Additional message received", received);

        _consumer.close();
    }


    private void failServer()
    {
        if (BROKER.startsWith("vm://"))
        {
            //Work around for MessageStore not being initialise and the send not fully completing before the failover occurs.
            try
            {
                Thread.sleep(5000);
            }
            catch (InterruptedException e)
            {

            }

            TransportConnection.killAllVMBrokers();
            ApplicationRegistry.remove(1);
            try
            {
                TransportConnection.createVMBroker(1);
            }
            catch (AMQVMBrokerCreationException e)
            {
                _logger.error("Unable to restart broker due to :" + e);
            }

            //Work around for receive not being failover aware.. because it is the first receive it trys to
            // unsuspend the channel but in this case the ChannelFlow command goes on the old session and the response on the
            // new one ... though I thought the statemanager recorded the listeners so should be ok.???
            try
            {
                Thread.sleep(5000);
            }
            catch (InterruptedException e)
            {

            }

        }

    }

}
