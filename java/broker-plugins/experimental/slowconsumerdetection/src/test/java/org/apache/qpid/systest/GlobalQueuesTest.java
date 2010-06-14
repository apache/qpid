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
package org.apache.qpid.systest;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.AMQChannelClosedException;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQSession_0_10;
import org.apache.qpid.jms.ConnectionListener;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.NamingException;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * QPID-1447 : Add slow consumer detection and disconnection.
 *
 * Slow consumers should on a topic should expect to receive a
 * 506 : Resource Error if the hit a predefined threshold.
 */
public class GlobalQueuesTest extends QpidBrokerTestCase implements ExceptionListener, ConnectionListener
{
    Topic _destination;
    private CountDownLatch _disconnectionLatch = new CountDownLatch(1);
    private int MAX_QUEUE_MESSAGE_COUNT;
    private int MESSAGE_SIZE = DEFAULT_MESSAGE_SIZE;

    private Thread _publisher;
    private static final long DISCONNECTION_WAIT = 5;
    private Exception _publisherError = null;
    private JMSException _connectionException = null;
    private static final long JOIN_WAIT = 5000;
    protected String CONFIG_SECTION = ".queues";

    @Override
    public void setUp() throws IOException, ConfigurationException, NamingException
    {
        setConfigurationProperty("virtualhosts.virtualhost."
                                 + getConnectionURL().getVirtualHost().substring(1) +
                                 ".slow-consumer-detection.delay", "1");

        setConfigurationProperty("virtualhosts.virtualhost."
                                 + getConnectionURL().getVirtualHost().substring(1) +
                                 ".slow-consumer-detection.timeunit", "SECONDS");

        /**
         *  Queue Configuration

         <slow-consumer-detection>
         <!-- The depth before which the policy will be applied-->
         <depth>4235264</depth>

         <!-- The message age before which the policy will be applied-->
         <messageAge>600000</messageAge>

         <!-- The number of message before which the policy will be applied-->
         <messageCount>50</messageCount>

         <!-- Policies configuration -->
         <policy>
         <name>TopicDelete</name>
         <topicDelete>
         <delete-persistent/>
         </topicDelete>
         </policy>
         </slow-consumer-detection>

         */

        /**
         *  VirtualHost Plugin Configuration

         <slow-consumer-detection>
         <delay>1</delay>
         <timeunit>MINUTES</timeunit>
         </slow-consumer-detection>

         */
    }

    /**
     * Perform the Main test of a topic Consumer with the given AckMode.
     *
     * Test creates a new connection and sets up the connection to prevent
     * failover
     *
     * A new consumer is connected and started so that it will prefetch msgs.
     *
     * An asynchrounous publisher is started to fill the broker with messages.
     *
     * We then wait to be notified of the disconnection via the ExceptionListener
     *
     * 0-10 does not have the same notification paths but sync() apparently should
     * give us the exception, currently it doesn't, so the test is excluded from 0-10
     *
     * We should ensure that this test has the same path for all protocol versions.
     *
     * Clients should not have to modify their code based on the protocol in use.
     *
     * @param ackMode @see javax.jms.Session
     *
     * @throws Exception
     */
    public void topicConsumer(int ackMode, boolean durable) throws Exception
    {
        Connection connection = getConnection();

        connection.setExceptionListener(this);

        Session session = connection.createSession(ackMode == Session.SESSION_TRANSACTED, ackMode);

        _destination = session.createTopic(getName());

        MessageConsumer consumer;

        if (durable)
        {
            consumer = session.createDurableSubscriber(_destination, getTestQueueName());
        }
        else
        {
            consumer = session.createConsumer(_destination);
        }

        connection.start();

        // Start the consumer pre-fetching
        // Don't care about response as we will fill the broker up with messages
        // after this point and ensure that the client is disconnected at the
        // right point.
        consumer.receiveNoWait();
        startPublisher(_destination);

        boolean disconnected = _disconnectionLatch.await(DISCONNECTION_WAIT, TimeUnit.SECONDS);

        if (!disconnected && isBroker010())
        {
            try
            {
                ((AMQSession_0_10) session).sync();
            }
            catch (AMQException amqe)
            {
                JMSException jmsException = new JMSException(amqe.getMessage());
                jmsException.setLinkedException(amqe);
                jmsException.initCause(amqe);
                _connectionException = jmsException;
            }
        }

        assertTrue("Client was not disconnected.", _connectionException != null);

        Exception linked = _connectionException.getLinkedException();

        _publisher.join(JOIN_WAIT);

        assertFalse("Publisher still running", _publisher.isAlive());

        //Validate publishing occurred ok
        if (_publisherError != null)
        {
            throw _publisherError;
        }

        // NOTE these exceptions will need to be modeled so that they are not
        // 0-8 specific. e.g. JMSSessionClosedException

        assertNotNull("No error received onException listener.", _connectionException);

        assertNotNull("No linked exception set on:" + _connectionException.getMessage(), linked);

        assertEquals("Incorrect linked exception received.", AMQChannelClosedException.class, linked.getClass());

        AMQChannelClosedException ccException = (AMQChannelClosedException) linked;

        assertEquals("Channel was not closed with correct code.", AMQConstant.RESOURCE_ERROR, ccException.getErrorCode());
    }

    /**
     * Create and start an asynchrounous publisher that will send MAX_QUEUE_MESSAGE_COUNT
     * messages to the provided destination. Messages are sent in a new connection
     * on a transaction. Any error is captured and the test is signalled to exit.
     *
     * @param destination
     */
    private void startPublisher(final Destination destination)
    {
        _publisher = new Thread(new Runnable()
        {

            public void run()
            {
                try
                {
                    Connection connection = getConnection();
                    Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

                    MessageProducer publisher = session.createProducer(destination);

                    for (int count = 0; count < MAX_QUEUE_MESSAGE_COUNT; count++)
                    {
                        publisher.send(createNextMessage(session, count));
                        session.commit();
                    }
                }
                catch (Exception e)
                {
                    _publisherError = e;
                    _disconnectionLatch.countDown();
                }
            }
        });

        _publisher.start();
    }

    /**
     * Test to write: Check that exclusive Transient Queues are not
     * disconnected. i.e. JMS Temporary Queues
     *
     * @param ackMode
     *
     * @throws Exception
     */
    public void exclusiveTransientQueue(int ackMode) throws Exception
    {

    }

    public void setConfig(String property, String value, boolean deleteDurable) throws NamingException, IOException, ConfigurationException
    {
        setConfigurationProperty("virtualhosts.virtualhost."
                                 + getConnectionURL().getVirtualHost().substring(1) +
                                 CONFIG_SECTION + ".slow-consumer-detection." +
                                 "policy.name", "TopicDelete");

        setConfigurationProperty("virtualhosts.virtualhost." +
                                 getConnectionURL().getVirtualHost().substring(1) +
                                 CONFIG_SECTION + ".slow-consumer-detection." +
                                 property, value);

        if (deleteDurable)
        {
            setConfigurationProperty("virtualhosts.virtualhost."
                                     + getConnectionURL().getVirtualHost().substring(1) +
                                     CONFIG_SECTION + ".slow-consumer-detection." +
                                     "policy.topicdelete.delete-persistent", "");
        }
    }

    /**
     * Test that setting messageCount takes affect on topics
     *
     * We send 10 messages and disconnect at 9
     *
     * @throws Exception
     */
    public void testTopicConsumerMessageCount() throws Exception
    {
        MAX_QUEUE_MESSAGE_COUNT = 10;

        setConfig("messageCount", String.valueOf(MAX_QUEUE_MESSAGE_COUNT - 1), false);

        //Start the broker
        super.setUp();

        topicConsumer(Session.AUTO_ACKNOWLEDGE, false);
    }

    /**
     * Test that setting depth has an effect on topics
     *
     * Sets the message size for the test
     * Sets the depth to be 9 * the depth
     * Ensure that sending 10 messages causes the disconnection
     *
     * @throws Exception
     */
    public void testTopicConsumerMessageSize() throws Exception
    {
        MAX_QUEUE_MESSAGE_COUNT = 10;

        setConfig("depth", String.valueOf(MESSAGE_SIZE * 9), false);

        //Start the broker
        super.setUp();

        setMessageSize(MESSAGE_SIZE);

        topicConsumer(Session.AUTO_ACKNOWLEDGE, false);
    }

    /**
     * Test that setting messageAge has an effect on topics
     *
     * Sets the messageAge to be half the disconnection wait timeout
     * Send 10 messages and then ensure that we get disconnected as we will
     * wait for the full timeout.
     *
     * @throws Exception
     */
    public void testTopicConsumerMessageAge() throws Exception
    {
        MAX_QUEUE_MESSAGE_COUNT = 10;

        setConfig("messageAge", String.valueOf(DISCONNECTION_WAIT / 2), false);

        //Start the broker
        super.setUp();

        topicConsumer(Session.AUTO_ACKNOWLEDGE, false);
    }

    /**
     * Test that setting messageCount takes affect on a durable Consumer
     *
     * Ensure we set the delete-persistent option
     *
     * We send 10 messages and disconnect at 9
     *
     * @throws Exception
     */

    public void testTopicDurableConsumerMessageCount() throws Exception
    {
        MAX_QUEUE_MESSAGE_COUNT = 10;

        setConfig("messageCount", String.valueOf(MAX_QUEUE_MESSAGE_COUNT - 1), true);

        //Start the broker
        super.setUp();

        topicConsumer(Session.AUTO_ACKNOWLEDGE, true);
    }

    /**
     * Test that setting depth has an effect on durable consumer topics
     *
     * Ensure we set the delete-persistent option
     *
     * Sets the message size for the test
     * Sets the depth to be 9 * the depth
     * Ensure that sending 10 messages causes the disconnection
     *
     * @throws Exception
     */
    public void testTopicDurableConsumerMessageSize() throws Exception
    {
        MAX_QUEUE_MESSAGE_COUNT = 10;

        setConfig("depth", String.valueOf(MESSAGE_SIZE * 9), true);

        //Start the broker
        super.setUp();

        setMessageSize(MESSAGE_SIZE);

        topicConsumer(Session.AUTO_ACKNOWLEDGE, true);
    }

    /**
     * Test that setting messageAge has an effect on topics
     *
     * Ensure we set the delete-persistent option
     *
     * Sets the messageAge to be 1/5 the disconnection wait timeout (or 1sec)
     * Send 10 messages and then ensure that we get disconnected as we will
     * wait for the full timeout.
     *
     * @throws Exception
     */
    public void testTopicDurableConsumerMessageAge() throws Exception
    {
        MAX_QUEUE_MESSAGE_COUNT = 10;

        setConfig("messageAge", String.valueOf(DISCONNECTION_WAIT / 5), true);

        //Start the broker
        super.setUp();

        topicConsumer(Session.AUTO_ACKNOWLEDGE, true);
    }

    // Exception Listener

    public void onException(JMSException e)
    {
        _connectionException = e;

        System.out.println("***** SCT Received Exception: " + e);
        e.printStackTrace();

        _disconnectionLatch.countDown();
    }


    /// Connection Listener

    public void bytesSent(long count)
    {
    }

    public void bytesReceived(long count)
    {
    }

    public boolean preFailover(boolean redirect)
    {
        // Prevent Failover
        return false;
    }

    public boolean preResubscribe()
    {
        return false;
    }

    public void failoverComplete()
    {
    }
}
