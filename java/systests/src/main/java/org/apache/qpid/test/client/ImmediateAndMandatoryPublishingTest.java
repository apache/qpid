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

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;

import org.apache.qpid.client.AMQSession;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

/**
 * @see CloseOnNoRouteForMandatoryMessageTest for related tests
 */
public class ImmediateAndMandatoryPublishingTest extends QpidBrokerTestCase
{
    private Connection _connection;
    private UnroutableMessageTestExceptionListener _testExceptionListener = new UnroutableMessageTestExceptionListener();

    @Override
    public void setUp() throws Exception
    {
        getBrokerConfiguration().setBrokerAttribute(Broker.CONNECTION_CLOSE_WHEN_NO_ROUTE, false);
        super.setUp();
        _connection = getConnection();
        _connection.setExceptionListener(_testExceptionListener);
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
        _testExceptionListener.assertReceivedNoRouteWithReturnedMessage(message, getTestQueueName());
    }

    private void publishIntoExistingDestinationWithNoConsumerAndImmediateOn(int acknowledgeMode, boolean pubSub)
            throws JMSException, InterruptedException
    {
        consumerCreateAndClose(pubSub, true);

        Message message = produceMessage(acknowledgeMode, pubSub, false, true);

        _testExceptionListener.assertReceivedNoConsumersWithReturnedMessage(message);
    }

    private void publishWithMandatoryOnImmediateOff(int acknowledgeMode, boolean pubSub) throws JMSException,
            InterruptedException
    {
        Message message = produceMessage(acknowledgeMode, pubSub, true, false);
        _testExceptionListener.assertReceivedNoRouteWithReturnedMessage(message, getTestQueueName());
    }

    private void publishWithMandatoryOffImmediateOff(int acknowledgeMode, boolean pubSub) throws JMSException,
            InterruptedException
    {
        produceMessage(acknowledgeMode, pubSub, false, false);

        _testExceptionListener.assertNoException();
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

        _testExceptionListener.assertReceivedNoRouteWithReturnedMessage(message, getTestQueueName());

        producer = session.createProducer(null);
        message = session.createMessage();
        producer.send(session.createQueue(getTestQueueName()), message);

        _testExceptionListener.assertReceivedNoRouteWithReturnedMessage(message, getTestQueueName());

        // publish to non-existent topic - should get no failure
        producer = session.createProducer(session.createTopic(getTestQueueName()));
        message = session.createMessage();
        producer.send(message);

        _testExceptionListener.assertNoException();

        producer = session.createProducer(null);
        message = session.createMessage();
        producer.send(session.createTopic(getTestQueueName()), message);

        _testExceptionListener.assertNoException();

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

        _testExceptionListener.assertReceivedNoRouteWithReturnedMessage(message, getTestQueueName());

        // now set topic specific system property to false - should no longer get mandatory failure on new producer
        setTestClientSystemProperty("qpid.default_mandatory_topic","false");
        producer = session.createProducer(null);
        message = session.createMessage();
        producer.send(session.createTopic(getTestQueueName()), message);

        _testExceptionListener.assertNoException();
    }
}
