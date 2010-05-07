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
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.test.utils.QpidTestCase;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.naming.NamingException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * QPID-1447 : Add slow consumer detection and disconnection.
 *
 * Slow consumers should on a topic should expect to receive a
 * 506 : Resource Error if the hit a predefined threshold.
 */
public class SlowConsumerTest extends QpidTestCase implements ExceptionListener
{
    Destination _destination;
    private CountDownLatch _disconnectionLatch = new CountDownLatch(1);
    private int MAX_QUEUE_MESSAGE_COUNT;
    private int MESSAGE_SIZE = DEFAULT_MESSAGE_SIZE;

    private Thread _publisher;
    private static final long DISCONNECTION_WAIT = 5;
    private Exception _publisherError = null;
    private JMSException _connectionException = null;

    @Override
    public void setUp() throws Exception, ConfigurationException, NamingException
    {
        // Set the houseKeepingThread period to be every 500
        setConfigurationProperty("virtualhosts.virtualhost."
                                 + getConnectionURL().getVirtualHost().substring(1) +
                                 ".slow-consumer-detection.delay", "1");

        setConfigurationProperty("virtualhosts.virtualhost."
                                 + getConnectionURL().getVirtualHost().substring(1) +
                                 ".slow-consumer-detection.timeunit", "SECONDS");

        setConfigurationProperty("virtualhosts.virtualhost." +
                                 getConnectionURL().getVirtualHost().substring(1) +
                                 "queues.slow-consumer-detection." +
                                 "policy[@name]", "TopicDelete");

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
         <policy name="TopicDelete">
         <options>
         <option name="delete-persistent" value="true"/>
         </options>
         </policy>
         </slow-consumer-detection>

         */

        /**
         *  Plugin Configuration
         *
         <slow-consumer-detection>
         <delay>1</delay>
         <timeunit>MINUTES</timeunit>
         </slow-consumer-detection>

         */

        super.setUp();
    }

    public void exclusiveTransientQueue(int ackMode) throws Exception
    {

    }

    public void tempQueue(int ackMode) throws Exception
    {

    }

    public void topicConsumer(int ackMode) throws Exception
    {
        Connection connection = getConnection();

        connection.setExceptionListener(this);

        Session session = connection.createSession(ackMode == Session.SESSION_TRANSACTED, ackMode);

        _destination = session.createTopic(getName());

        MessageConsumer consumer = session.createConsumer(_destination);

        connection.start();

        // Start the consumer pre-fetching
        // Don't care about response as we will fill the broker up with messages
        // after this point and ensure that the client is disconnected at the
        // right point.
        consumer.receiveNoWait();
        startPublisher(_destination);

        boolean disconnected = _disconnectionLatch.await(DISCONNECTION_WAIT, TimeUnit.SECONDS);

        System.out.println("Validating");

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
                _connectionException = jmsException;
            }
        }

        System.err.println("ConnectionException:" + _connectionException);

        assertTrue("Client was not disconnected.", _connectionException != null);

        Exception linked = _connectionException.getLinkedException();

        System.err.println("Linked:" + linked);

        _publisher.join();

        //Validate publishing occurred ok
        if (_publisherError != null)
        {
            throw _publisherError;
        }

        assertNotNull("No error received onException listener.", _connectionException);

        assertNotNull("No linked exception set on:" + _connectionException.getMessage(), linked);

        assertEquals("Incorrect linked exception received.", AMQChannelClosedException.class, linked.getClass());

        AMQChannelClosedException ccException = (AMQChannelClosedException) linked;

        assertEquals("Channel was not closed with correct code.", AMQConstant.RESOURCE_ERROR, ccException.getErrorCode());
    }

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

                    setMessageSize(MESSAGE_SIZE);

                    for (int count = 0; count < MAX_QUEUE_MESSAGE_COUNT; count++)
                    {
                        publisher.send(createNextMessage(session, count));
                    }
                }
                catch (Exception e)
                {
                    _publisherError = e;
                    _disconnectionLatch.countDown();
                }
            }
        });
    }

    public void testAutoAckTopicConsumerMessageCount() throws Exception
    {
        MAX_QUEUE_MESSAGE_COUNT = 10;
        setConfigurationProperty("virtualhosts.virtualhost." +
                                 getConnectionURL().getVirtualHost().substring(1) +
                                 "queues.slow-consumer-detection" +
                                 "messageCount", "9");

        setMessageSize(MESSAGE_SIZE);

        topicConsumer(Session.AUTO_ACKNOWLEDGE);
    }

    public void onException(JMSException e)
    {
        _connectionException = e;

        _disconnectionLatch.countDown();
    }
}
