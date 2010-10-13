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

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.NamingException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.AMQException;
import org.apache.qpid.jms.ConnectionListener;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class TestingBaseCase extends QpidBrokerTestCase implements ExceptionListener, ConnectionListener
{

    Topic _destination;
    protected CountDownLatch _disconnectionLatch = new CountDownLatch(1);
    protected int MAX_QUEUE_MESSAGE_COUNT;
    protected int MESSAGE_SIZE = DEFAULT_MESSAGE_SIZE;

    private Thread _publisher;
    protected static final long DISCONNECTION_WAIT = 5;
    protected Exception _publisherError = null;
    protected JMSException _connectionException = null;
    private static final long JOIN_WAIT = 5000;

    @Override
    public void setUp() throws Exception
    {

        setConfigurationProperty("virtualhosts.virtualhost."
                                 + getConnectionURL().getVirtualHost().substring(1) +
                                 ".slow-consumer-detection.delay", "1");

        setConfigurationProperty("virtualhosts.virtualhost."
                                 + getConnectionURL().getVirtualHost().substring(1) +
                                 ".slow-consumer-detection.timeunit", "SECONDS");

    }

    protected void setProperty(String property, String value) throws NamingException, IOException, ConfigurationException
    {
        setConfigurationProperty("virtualhosts.virtualhost." +
                                 getConnectionURL().getVirtualHost().substring(1) +
                                 property, value);
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
     * Perform the Main test of a topic Consumer with the given AckMode.
     *
     * Test creates a new connection and sets up the connection to prevent
     * failover
     * <p>
     * A new consumer is connected and started so that it will prefetch msgs.
     * <p>
     * An asynchrounous publisher is started to fill the broker with messages.
     * <p>
     * We then wait to be notified of the disconnection via the ExceptionListener
     *
     * @param ackMode see {@link javax.jms.Session} for modes
     * @throws Exception
     */
    protected void topicConsumer(int ackMode, boolean durable) throws Exception
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
        
        assertTrue("Client was not disconnected", disconnected);

        assertNotNull("No error received onException listener.", _connectionException);

        Exception linked = _connectionException.getLinkedException();

        _publisher.join(JOIN_WAIT);

        assertFalse("Publisher still running", _publisher.isAlive());

        //Validate publishing occurred ok
        if (_publisherError != null)
        {
            throw _publisherError;
        }

        assertNotNull("No linked exception set on:" + _connectionException.getMessage(), linked);

        assertTrue("Incorrect linked exception received.", linked instanceof AMQException);

        AMQException amqException = (AMQException) linked;

        assertEquals("Channel was not closed with correct code.", AMQConstant.RESOURCE_ERROR, amqException.getErrorCode());
    }


    // Exception Listener

    public void onException(JMSException e)
    {
        _connectionException = e;

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
