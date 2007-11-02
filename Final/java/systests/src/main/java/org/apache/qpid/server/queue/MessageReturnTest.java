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

import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQNoRouteException;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.jndi.PropertiesFileInitialContextFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.virtualhost.VirtualHost;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import java.util.Hashtable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


/**
 * Test Case to ensure that messages are correctly returned.
 * This includes checking:
 * - The message is returned.
 * - The broker doesn't leak memory.
 * - The broker's state is correct after test.
 */
public class MessageReturnTest extends TestCase implements ExceptionListener
{
    private static final Logger _logger = Logger.getLogger(MessageReturnTest.class);


    protected final String BROKER = "vm://:1";
    protected final String VHOST = "test";
    protected final String QUEUE = "MessageReturnTest";
    protected final String BADQUEUE = "MessageReturnTest-bad-to-force-returns";


    private Context _context;

    private Connection _producerConnection;

    private MessageProducer _producer;
    private Session _clientSession, _producerSession;
    private static final int MSG_COUNT = 50;

    private Message[] _messages = new Message[MSG_COUNT];

    private CountDownLatch _returns = new CountDownLatch(1);
    private int _receivedCount = 0;
    private int _initialContentBodyMapSize;
    private int _initilaMessageMetaDataMapSize;

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
        env.put("queue.badQueue", QUEUE);

        _context = factory.getInitialContext(env);

        getBrokerInitialState();
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();

        if (_producerConnection != null)
        {
            _producerConnection.close();
        }
        
        if (BROKER.startsWith("vm://"))
        {
            TransportConnection.killAllVMBrokers();
        }
    }

    public void test() throws Exception
    {
        init();
        //Send Msgs
        for (int msg = 0; msg < MSG_COUNT; msg++)
        {
            _producer.send(nextMessage(msg));
        }

        try
        {
            // Wait for all returns to arrive any longer than 5secs and something has gone wrong.
            _returns.await(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException e)
        {

        }

        //Close the connection.. .giving the broker time to clean up its state.
        _producerConnection.close();

        //Verify we get all the messages.
        verifyAllMessagesRecevied();
        //Verify Broker state
        verifyBrokerState();
    }

    private void init() throws NamingException, JMSException
    {
        _receivedCount = 0;
        _messages = new Message[MSG_COUNT];
        _returns = new CountDownLatch(1);

        //Create Producer
        _producerConnection = ((ConnectionFactory) _context.lookup("connection")).createConnection();

        _producerConnection.setExceptionListener(this);

        _producerConnection.start();

        _producerSession = _producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        _producer = _producerSession.createProducer((Queue) _context.lookup("badQueue"));
    }

    // todo: collect to a general testing class - duplicated in AMQQueueMBeanTest
    private void getBrokerInitialState()
    {
        IApplicationRegistry registry = ApplicationRegistry.getInstance();

        VirtualHost testVhost = registry.getVirtualHostRegistry().getVirtualHost(VHOST);

        assertNotNull("Unable to get test Vhost", testVhost.getMessageStore());

        TestableMemoryMessageStore store = new TestableMemoryMessageStore((MemoryMessageStore) testVhost.getMessageStore());

        _initialContentBodyMapSize = store.getContentBodyMap() == null ? 0 : store.getContentBodyMap().size();
        _initilaMessageMetaDataMapSize = store.getMessageMetaDataMap() == null ? 0 : store.getMessageMetaDataMap().size();

        if (_initialContentBodyMapSize != 0)
        {
            _logger.warn("Store is dirty: ContentBodyMap has Size:" + _initialContentBodyMapSize);
            System.out.println("Store is dirty: ContentBodyMap has Size:" + _initialContentBodyMapSize);
        }

        if (_initilaMessageMetaDataMapSize != 0)
        {
            _logger.warn("Store is dirty: MessageMetaDataMap has Size:" + _initilaMessageMetaDataMapSize);
            System.out.println("Store is dirty: MessageMetaDataMap has Size:" + _initilaMessageMetaDataMapSize);
        }

    }

    private void verifyBrokerState()
    {
        IApplicationRegistry registry = ApplicationRegistry.getInstance();

        VirtualHost testVhost = registry.getVirtualHostRegistry().getVirtualHost(VHOST);

        assertNotNull("Unable to get test Vhost", testVhost.getMessageStore());

        TestableMemoryMessageStore store = new TestableMemoryMessageStore((MemoryMessageStore) testVhost.getMessageStore());


        assertNotNull("ContentBodyMap should not be null", store.getContentBodyMap());

        // If the CBM has content it may be due to the broker not yet purging.
        // Closing the producer connection before testing should give the store time to clean up.
        // Perform a quick sleep just in case
        while (store.getContentBodyMap().size() > _initialContentBodyMapSize)
        {
            try
            {
                Thread.sleep(500);
            }
            catch (InterruptedException e)
            {
            }
        }
        assertTrue("Expected the store content size not reached at test start it was :" + _initialContentBodyMapSize + " Now it is :" + store.getContentBodyMap().size(), _initialContentBodyMapSize >= store.getContentBodyMap().size());
        assertNotNull("MessageMetaDataMap should not be null", store.getMessageMetaDataMap());
        assertTrue("Expected the store MessageMetaData size not reached at test start it was :" + _initilaMessageMetaDataMapSize + " Now it is :" + store.getMessageMetaDataMap().size(), _initialContentBodyMapSize >= store.getMessageMetaDataMap().size());
    }

    private void verifyAllMessagesRecevied()
    {

        boolean[] msgIdRecevied = new boolean[MSG_COUNT];

        int msgId = 0;

        //Check received messages
        for (Message msg : _messages)
        {
            assertNotNull("Missing message:" + msgId, msg);
            assertFalse("Already received msg id " + msgId, msgIdRecevied[msgId]);
            msgIdRecevied[msgId] = true;
            msgId++;
        }

        //Check all recevied
        for (msgId = 0; msgId < MSG_COUNT; msgId++)
        {
            assertTrue("Message " + msgId + " not received.", msgIdRecevied[msgId]);
        }
    }

    /**
     * We can't verify messageOrder here as the return threads are not synchronized so we have no way of
     * guarranting the order.
     */
    private void verifyMessageOrder()
    {
        int msgId = 0;
        for (Message msg : _messages)
        {
            assertNotNull("Missing message:" + msgId, msg);
            try
            {
                assertEquals("Message not received in correct order", msgId, msg.getIntProperty("ID"));
            }
            catch (JMSException e)
            {
                fail("Unable to get messageID for msg:" + msg);
            }

            msgId++;
        }
    }

    /**
     * Get the next message putting the given count into the intProperties as ID.
     *
     * @param msgNo the message count to store as ID.
     * @return
     * @throws JMSException
     */

    private Message nextMessage(int msgNo) throws JMSException
    {
        Message send = _producerSession.createTextMessage("MessageReturnTest");
        send.setIntProperty("ID", msgNo);
        return send;
    }


    public void onException(JMSException jmsException)
    {
        // NOTE:
        // This method MUST be thread-safe. Mulitple threads can call this at once.
        synchronized (this)
        {
            if (jmsException.getLinkedException() instanceof AMQNoRouteException)
            {
                AMQNoRouteException amq = (AMQNoRouteException) jmsException.getLinkedException();

                Message msg = (Message) amq.getUndeliveredMessage();

                if (_receivedCount < MSG_COUNT)
                {
                    assertNotNull("Reeceived Null message:" + _receivedCount, msg);
                    _messages[_receivedCount] = msg;
                    _receivedCount++;
                }
                else
                {
                    fail("Received to many messages expected :" + MSG_COUNT + " received: " + _receivedCount + 1);
                }

                if (_receivedCount == MSG_COUNT)
                {
                    _returns.countDown();
                }
            }
        }
    }
}
