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

import java.util.Hashtable;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;

import junit.framework.TestCase;

import org.apache.log4j.Logger;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.jndi.PropertiesFileInitialContextFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;


/**
 * Test Case to ensure that messages are correctly returned.
 * This includes checking:
 * - The message is returned.
 * - The broker doesn't leak memory.
 * - The broker's state is correct after test.
 */
public class QueueDepthWithSelectorTest extends TestCase
{
    private static final Logger _logger = Logger.getLogger(QueueDepthWithSelectorTest.class);

    protected final String BROKER = "vm://:1";
    protected final String VHOST = "test";
    protected final String QUEUE = this.getClass().getName();

    private Context _context;

    private Connection _clientConnection, _producerConnection;
    private Session _clientSession, _producerSession;
    private MessageProducer _producer;
    private MessageConsumer _consumer;

    private static final int MSG_COUNT = 50;

    private Message[] _messages = new Message[MSG_COUNT];

    protected void setUp() throws Exception
    {
        if (BROKER.startsWith("vm://"))
        {
            TransportConnection.createVMBroker(1);
        }
        InitialContextFactory factory = new PropertiesFileInitialContextFactory();

        Hashtable<String, String> env = new Hashtable<String, String>();

        env.put("connectionfactory.connection", "amqp://guest:guest@TTL_TEST_ID/" + VHOST + "?brokerlist='" + BROKER + "'");
        env.put("queue.queue", QUEUE);

        _context = factory.getInitialContext(env);

    }

    protected void tearDown() throws Exception
    {
        super.tearDown();

        if (_producerConnection != null)
        {
            _producerConnection.close();
        }

        if (_clientConnection != null)
        {
            _clientConnection.close();
        }

        if (BROKER.startsWith("vm://"))
        {
            TransportConnection.killAllVMBrokers();
        }
    } 

    public void test() throws Exception
    {
        init();
        //Send messages
        _logger.info("Starting to send messages");
        for (int msg = 0; msg < MSG_COUNT; msg++)
        {
            _producer.send(nextMessage(msg));
        }
        _logger.info("Closing connection");
        //Close the connection.. .giving the broker time to clean up its state.
        _producerConnection.close();

        //Verify we get all the messages.
        _logger.info("Verifying messages");
        verifyAllMessagesRecevied();

        //Close the connection.. .giving the broker time to clean up its state.
        _clientConnection.close();

        //Verify Broker state
        _logger.info("Verifying broker state");
        verifyBrokerState();
    }

    private void init() throws NamingException, JMSException
    {
        _messages = new Message[MSG_COUNT];

        //Create Producer
        _producerConnection = ((ConnectionFactory) _context.lookup("connection")).createConnection();
        _producerConnection.start();
        _producerSession = _producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        _producer = _producerSession.createProducer((Queue) _context.lookup("queue"));

        // Create consumer
        _clientConnection = ((ConnectionFactory) _context.lookup("connection")).createConnection();
        _clientConnection.start();
        _clientSession = _clientConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        _consumer = _clientSession.createConsumer((Queue) _context.lookup("queue"), "key = 23");
    }

    private void verifyBrokerState()
    {
        IApplicationRegistry registry = ApplicationRegistry.getInstance();

        VirtualHost testVhost = registry.getVirtualHostRegistry().getVirtualHost(VHOST);
        assertNotNull("Unable to get test Vhost", testVhost);
        assertNotNull("Unable to get test queue registry", testVhost.getQueueRegistry());
        AMQQueue q = testVhost.getQueueRegistry().getQueue(new AMQShortString(QUEUE));
        assertNotNull("Unable to get test queue", q);
        assertEquals("Queue count too big", 0, q.getMessageCount());
    }

    private void verifyAllMessagesRecevied() throws JMSException
    {

        boolean[] msgIdRecevied = new boolean[MSG_COUNT];


        for (int i = 0; i < MSG_COUNT; i++)
        {
            _messages[i] = _consumer.receive(1000);
            assertNotNull("should have received a message but didn't", _messages[i]);
        }

        //Check received messages
        int msgId = 0;
        for (Message msg : _messages)
        {
            assertNotNull("Message should not be null", msg);
            assertEquals("msgId was wrong", msgId, msg.getIntProperty("ID"));
            assertFalse("Already received msg id " + msgId, msgIdRecevied[msgId]);
            msgIdRecevied[msgId] = true;
            msgId++;
        }

        //Check all received
        for (msgId = 0; msgId < MSG_COUNT; msgId++)
        {
            assertTrue("Message " + msgId + " not received.", msgIdRecevied[msgId]);
        }
    }

    /**
     * Get the next message putting the given count into the intProperties as ID.
     *
     * @param msgNo the message count to store as ID.
     *
     * @return
     *
     * @throws JMSException
     */

    private Message nextMessage(int msgNo) throws JMSException
    {
        Message send = _producerSession.createTextMessage("MessageReturnTest");
        send.setIntProperty("ID", msgNo);
        send.setIntProperty("key", 23);
        return send;
    }

}
