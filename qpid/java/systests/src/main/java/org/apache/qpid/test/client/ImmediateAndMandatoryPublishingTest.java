/*
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
 */
package org.apache.qpid.test.client;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import org.apache.qpid.client.AMQNoConsumersException;
import org.apache.qpid.client.AMQNoRouteException;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class ImmediateAndMandatoryPublishingTest extends QpidBrokerTestCase implements ExceptionListener
{
    private Connection _connection;
    private BlockingQueue<JMSException> _exceptions;

    public void setUp() throws Exception
    {
        super.setUp();
        _exceptions = new ArrayBlockingQueue<JMSException>(1);
        _connection = getConnection();
        _connection.setExceptionListener(this);
    }

    public void testPublishP2PWithNoConsumerAndImmediateOnAndAutoAck() throws Exception
    {
        publishIntoExistingDestinationWithNoConsumerAndImmediateOn(Session.AUTO_ACKNOWLEDGE, false);
    }

    public void testPublishP2PWithNoConsumerAndImmediateOnAndTx() throws Exception
    {
        publishIntoExistingDestinationWithNoConsumerAndImmediateOn(Session.SESSION_TRANSACTED, false);
    }

    public void testPublishPubSubWithDisconnectedDurableSubscriberAndImmediateOnAndAutoAck() throws Exception
    {
        publishIntoExistingDestinationWithNoConsumerAndImmediateOn(Session.AUTO_ACKNOWLEDGE, true);
    }

    public void testPublishPubSubWithDisconnectedDurableSubscriberAndImmediateOnAndTx() throws Exception
    {
        publishIntoExistingDestinationWithNoConsumerAndImmediateOn(Session.SESSION_TRANSACTED, true);
    }

    public void testPublishP2PIntoNonExistingDesitinationWithMandatoryOnAutoAck() throws Exception
    {
        publishWithMandatoryOnImmediateOff(Session.AUTO_ACKNOWLEDGE, false);
    }

    public void testPublishP2PIntoNonExistingDesitinationWithMandatoryOnAndTx() throws Exception
    {
        publishWithMandatoryOnImmediateOff(Session.SESSION_TRANSACTED, false);
    }

    public void testPubSubMandatoryAutoAck() throws Exception
    {
        publishWithMandatoryOnImmediateOff(Session.AUTO_ACKNOWLEDGE, true);
    }

    public void testPubSubMandatoryTx() throws Exception
    {
        publishWithMandatoryOnImmediateOff(Session.SESSION_TRANSACTED, true);
    }

    public void testP2PNoMandatoryAutoAck() throws Exception
    {
        publishWithMandatoryOffImmediateOff(Session.AUTO_ACKNOWLEDGE, false);
    }

    public void testP2PNoMandatoryTx() throws Exception
    {
        publishWithMandatoryOffImmediateOff(Session.SESSION_TRANSACTED, false);
    }

    public void testPubSubWithImmediateOnAndAutoAck() throws Exception
    {
        consumerCreateAndClose(true, false);

        Message message = produceMessage(Session.AUTO_ACKNOWLEDGE, true, false, true);

        JMSException exception = _exceptions.poll(10, TimeUnit.SECONDS);
        assertNotNull("JMSException is expected", exception);
        AMQNoRouteException noRouteException = (AMQNoRouteException) exception.getLinkedException();
        assertNotNull("AMQNoRouteException should be linked to JMSEXception", noRouteException);
        Message bounceMessage = (Message) noRouteException.getUndeliveredMessage();
        assertNotNull("Bounced Message is expected", bounceMessage);
        assertEquals("Unexpected message is bounced", message.getJMSMessageID(), bounceMessage.getJMSMessageID());
    }

    private void publishIntoExistingDestinationWithNoConsumerAndImmediateOn(int acknowledgeMode, boolean pubSub)
            throws JMSException, InterruptedException
    {
        consumerCreateAndClose(pubSub, true);

        Message message = produceMessage(acknowledgeMode, pubSub, false, true);

        JMSException exception = _exceptions.poll(10, TimeUnit.SECONDS);
        assertNotNull("JMSException is expected", exception);
        AMQNoConsumersException noConsumerException = (AMQNoConsumersException) exception.getLinkedException();
        assertNotNull("AMQNoConsumersException should be linked to JMSEXception", noConsumerException);
        Message bounceMessage = (Message) noConsumerException.getUndeliveredMessage();
        assertNotNull("Bounced Message is expected", bounceMessage);
        assertEquals("Unexpected message is bounced", message.getJMSMessageID(), bounceMessage.getJMSMessageID());
    }

    private void publishWithMandatoryOnImmediateOff(int acknowledgeMode, boolean pubSub) throws JMSException,
            InterruptedException
    {
        Message message = produceMessage(acknowledgeMode, pubSub, true, false);

        JMSException exception = _exceptions.poll(10, TimeUnit.SECONDS);
        assertNotNull("JMSException is expected", exception);
        AMQNoRouteException noRouteException = (AMQNoRouteException) exception.getLinkedException();
        assertNotNull("AMQNoRouteException should be linked to JMSEXception", noRouteException);
        Message bounceMessage = (Message) noRouteException.getUndeliveredMessage();
        assertNotNull("Bounced Message is expected", bounceMessage);
        assertEquals("Unexpected message is bounced", message.getJMSMessageID(), bounceMessage.getJMSMessageID());
    }

    private void publishWithMandatoryOffImmediateOff(int acknowledgeMode, boolean pubSub) throws JMSException,
            InterruptedException
    {
        produceMessage(acknowledgeMode, pubSub, false, false);

        JMSException exception = _exceptions.poll(1, TimeUnit.SECONDS);
        assertNull("Unexpected JMSException", exception);
    }

    private void consumerCreateAndClose(boolean pubSub, boolean durable) throws JMSException
    {
        Session session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination destination = null;
        MessageConsumer consumer = null;
        if (pubSub)
        {
            destination = session.createTopic(getTestQueueName());
            if (durable)
            {
                consumer = session.createDurableSubscriber((Topic) destination, getTestName());
            }
            else
            {
                consumer = session.createConsumer(destination);
            }
        }
        else
        {
            destination = session.createQueue(getTestQueueName());
            consumer = session.createConsumer(destination);
        }
        consumer.close();
    }

    private Message produceMessage(int acknowledgeMode, boolean pubSub, boolean mandatory, boolean immediate)
            throws JMSException
    {
        Session session = _connection.createSession(acknowledgeMode == Session.SESSION_TRANSACTED, acknowledgeMode);
        Destination destination = null;
        if (pubSub)
        {
            destination = session.createTopic(getTestQueueName());
        }
        else
        {
            destination = session.createQueue(getTestQueueName());
        }

        MessageProducer producer = ((AMQSession<?, ?>) session).createProducer(destination, mandatory, immediate);
        Message message = session.createMessage();
        producer.send(message);
        if (session.getTransacted())
        {
            session.commit();
        }
        return message;
    }

    public void testMandatoryAndImmediateDefaults() throws JMSException, InterruptedException
    {
        Session session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // publish to non-existent queue - should get mandatory failure
        MessageProducer producer = session.createProducer(session.createQueue(getTestQueueName()));
        Message message = session.createMessage();
        producer.send(message);

        JMSException exception = _exceptions.poll(10, TimeUnit.SECONDS);
        assertNotNull("JMSException is expected", exception);
        AMQNoRouteException noRouteException = (AMQNoRouteException) exception.getLinkedException();
        assertNotNull("AMQNoRouteException should be linked to JMSEXception", noRouteException);
        Message bounceMessage = (Message) noRouteException.getUndeliveredMessage();
        assertNotNull("Bounced Message is expected", bounceMessage);
        assertEquals("Unexpected message is bounced", message.getJMSMessageID(), bounceMessage.getJMSMessageID());

        producer = session.createProducer(null);
        message = session.createMessage();
        producer.send(session.createQueue(getTestQueueName()), message);

        exception = _exceptions.poll(10, TimeUnit.SECONDS);
        assertNotNull("JMSException is expected", exception);
        noRouteException = (AMQNoRouteException) exception.getLinkedException();
        assertNotNull("AMQNoRouteException should be linked to JMSEXception", noRouteException);
        bounceMessage = (Message) noRouteException.getUndeliveredMessage();
        assertNotNull("Bounced Message is expected", bounceMessage);
        assertEquals("Unexpected message is bounced", message.getJMSMessageID(), bounceMessage.getJMSMessageID());


        // publish to non-existent topic - should get no failure
        producer = session.createProducer(session.createTopic(getTestQueueName()));
        message = session.createMessage();
        producer.send(message);

        exception = _exceptions.poll(1, TimeUnit.SECONDS);
        assertNull("Unexpected JMSException", exception);

        producer = session.createProducer(null);
        message = session.createMessage();
        producer.send(session.createTopic(getTestQueueName()), message);

        exception = _exceptions.poll(1, TimeUnit.SECONDS);
        assertNull("Unexpected JMSException", exception);

        session.close();
    }

    public void testMandatoryAndImmediateSystemProperties() throws JMSException, InterruptedException
    {
        setTestClientSystemProperty("qpid.default_mandatory","true");
        Session session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // publish to non-existent topic - should get mandatory failure

        MessageProducer producer = session.createProducer(session.createTopic(getTestQueueName()));
        Message message = session.createMessage();
        producer.send(message);

        JMSException exception = _exceptions.poll(10, TimeUnit.SECONDS);
        assertNotNull("JMSException is expected", exception);
        AMQNoRouteException noRouteException = (AMQNoRouteException) exception.getLinkedException();
        assertNotNull("AMQNoRouteException should be linked to JMSEXception", noRouteException);
        Message bounceMessage = (Message) noRouteException.getUndeliveredMessage();
        assertNotNull("Bounced Message is expected", bounceMessage);
        assertEquals("Unexpected message is bounced", message.getJMSMessageID(), bounceMessage.getJMSMessageID());

        // now set topic specific system property to false - should no longer get mandatory failure on new producer
        setTestClientSystemProperty("qpid.default_mandatory_topic","false");
        producer = session.createProducer(null);
        message = session.createMessage();
        producer.send(session.createTopic(getTestQueueName()), message);

        exception = _exceptions.poll(1, TimeUnit.SECONDS);
        if(exception != null)
        {
            exception.printStackTrace();
        }
        assertNull("Unexpected JMSException", exception);

    }

    public void onException(JMSException exception)
    {
        _exceptions.add(exception);
    }
}
