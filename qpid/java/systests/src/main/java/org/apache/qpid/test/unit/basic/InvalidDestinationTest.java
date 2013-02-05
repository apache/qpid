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

package org.apache.qpid.test.unit.basic;

import java.util.Collections;
import java.util.Map;
import javax.jms.Connection;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class InvalidDestinationTest extends QpidBrokerTestCase
{
    private AMQConnection _connection;

    protected void setUp() throws Exception
    {
        super.setUp();
        _connection = (AMQConnection) getConnection("guest", "guest");
    }

    protected void tearDown() throws Exception
    {
        _connection.close();
        super.tearDown();
    }

    public void testInvalidDestination() throws Exception
    {
        QueueSession queueSession = _connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

        Queue invalidDestination = queueSession.createQueue("unknownQ");

        Queue validDestination = queueSession.createQueue(getTestQueueName());

        // This is the only easy way to create and bind a queue from the API :-(
        queueSession.createConsumer(validDestination);
        QueueSender sender;
        TextMessage msg= queueSession.createTextMessage("Hello");

        try
        {
            sender = queueSession.createSender(invalidDestination);

            sender.send(msg);
            fail("Expected InvalidDestinationException");
        }
        catch (InvalidDestinationException ex)
        {
            // pass
        }

        sender = queueSession.createSender(null);

        try
        {
            sender.send(invalidDestination,msg);
            fail("Expected InvalidDestinationException");
        }
        catch (InvalidDestinationException ex)
        {
            // pass
        }
        sender.send(validDestination,msg);
        sender.close();
        sender = queueSession.createSender(validDestination);
        sender.send(msg);
    }

    /**
     * Tests that specifying the {@value ClientProperties#VERIFY_QUEUE_ON_SEND} system property
     * results in an exception when sending to an invalid queue destination.
     */
    public void testInvalidDestinationOnMessageProducer() throws Exception
    {
        setTestSystemProperty(ClientProperties.VERIFY_QUEUE_ON_SEND, "true");
        final AMQConnection connection = (AMQConnection) getConnection();
        doInvalidDestinationOnMessageProducer(connection);
    }

    /**
     * Tests that specifying the {@value ConnectionURL.OPTIONS_VERIFY_QUEUE_ON_SEND}
     * connection URL option property results in an exception when sending to an
     * invalid queue destination.
     */
    public void testInvalidDestinationOnMessageProducerURL() throws Exception
    {
        Map<String, String> options = Collections.singletonMap(ConnectionURL.OPTIONS_VERIFY_QUEUE_ON_SEND, "true");
        doInvalidDestinationOnMessageProducer(getConnectionWithOptions(options));
    }

    private void doInvalidDestinationOnMessageProducer(Connection connection) throws JMSException
    {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        String invalidQueueName = getTestQueueName() + "UnknownQ";
        Queue invalidDestination = session.createQueue(invalidQueueName);

        String validQueueName = getTestQueueName() + "KnownQ";
        Queue validDestination = session.createQueue(validQueueName);

        // This is the only easy way to create and bind a queue from the API :-(
        session.createConsumer(validDestination);

        MessageProducer sender;
        TextMessage msg = session.createTextMessage("Hello");
        try
        {
            sender = session.createProducer(invalidDestination);
            sender.send(msg);
            fail("Expected InvalidDestinationException");
        }
        catch (InvalidDestinationException ex)
        {
            // pass
        }

        sender = session.createProducer(null);
        invalidDestination = new AMQQueue("amq.direct",invalidQueueName);

        try
        {
            sender.send(invalidDestination,msg);
            fail("Expected InvalidDestinationException");
        }
        catch (InvalidDestinationException ex)
        {
            // pass
        }
        sender.send(validDestination, msg);
        sender.close();
        sender = session.createProducer(validDestination);
        sender.send(msg);

        //Verify sending to an 'invalid' Topic doesn't throw an exception
        String invalidTopic = getTestQueueName() + "UnknownT";
        Topic topic = session.createTopic(invalidTopic);
        sender = session.createProducer(topic);
        sender.send(msg);
    }
}
