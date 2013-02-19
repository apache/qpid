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
package org.apache.qpid.server.persistent;

import java.util.ArrayList;
import java.util.List;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

import org.apache.qpid.test.utils.QpidBrokerTestCase;

/**
 * Verifies that after recovery, a new Connection with no-local in use is
 * able to receive messages sent prior to the broker restart.
 */
public class NoLocalAfterRecoveryTest extends QpidBrokerTestCase
{
    protected final String MY_TOPIC_SUBSCRIPTION_NAME = getTestQueueName();
    protected static final int SEND_COUNT = 10;

    public void testNoLocalNotQueued() throws Exception
    {
        if(!isBrokerStorePersistent())
        {
            fail("This test requires a broker with a persistent store");
        }

        Connection connection = getConnection();
        Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
        Topic topic = session.createTopic(MY_TOPIC_SUBSCRIPTION_NAME);

        TopicSubscriber noLocalSubscriber = session.
                createDurableSubscriber(topic, MY_TOPIC_SUBSCRIPTION_NAME + "-NoLocal",
                                        null, true);

        TopicSubscriber normalSubscriber = session.
                createDurableSubscriber(topic, MY_TOPIC_SUBSCRIPTION_NAME + "-Normal",
                                        null, false);

        sendMessage(session, topic, SEND_COUNT);

        // Check messages can be received as expected.
        connection.start();

        //As the no-local subscriber was on the same connection the messages were
        //published on, tit will receive no messages as they will be discarded on the broker
        List<Message> received = receiveMessage(noLocalSubscriber, SEND_COUNT);
        assertEquals("No Local Subscriber Received messages", 0, received.size());

        received = receiveMessage(normalSubscriber, SEND_COUNT);
        assertEquals("Normal Subscriber Received no messages",
                     SEND_COUNT, received.size());
        session.commit();

        normalSubscriber.close();
        connection.close();

        //Ensure the no-local subscribers messages were discarded by restarting the broker
        //and reconnecting to the subscription to ensure they were not recovered.
        restartBroker();

        Connection connection2 = getConnection();
        connection2.start();

        Session session2 = connection2.createSession(true, Session.SESSION_TRANSACTED);
        Topic topic2 = session2.createTopic(MY_TOPIC_SUBSCRIPTION_NAME);

        TopicSubscriber noLocalSubscriber2 = session2.
                createDurableSubscriber(topic2, MY_TOPIC_SUBSCRIPTION_NAME + "-NoLocal",
                                        null, true);

        // The NO-local subscriber should not get any messages
        received = receiveMessage(noLocalSubscriber2, SEND_COUNT);
        session2.commit();
        assertEquals("No Local Subscriber Received messages", 0, received.size());

        noLocalSubscriber2.close();


    }


    public void testNonNoLocalQueued() throws Exception
    {
        if(!isBrokerStorePersistent())
        {
            fail("This test requires a broker with a persistent store");
        }

        Connection connection = getConnection();
        Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
        Topic topic = session.createTopic(MY_TOPIC_SUBSCRIPTION_NAME);

        TopicSubscriber noLocalSubscriber =
                session.createDurableSubscriber(topic, MY_TOPIC_SUBSCRIPTION_NAME + "-NoLocal", null, true);


        sendMessage(session, topic, SEND_COUNT);

        // Check messages can be received as expected.
        connection.start();

        List<Message> received = receiveMessage(noLocalSubscriber, SEND_COUNT);
        assertEquals("No Local Subscriber Received messages", 0, received.size());



        session.commit();

        Connection connection3 = getConnection();
        Session session3 = connection3.createSession(true, Session.SESSION_TRANSACTED);
        sendMessage(session3, topic, SEND_COUNT);


        connection.close();

        //We didn't receive the messages on the durable queue for the no-local subscriber
        //so they are still on the broker. Restart the broker, prompting their recovery.
        restartBroker();

        Connection connection2 = getConnection();
        connection2.start();

        Session session2 = connection2.createSession(true, Session.SESSION_TRANSACTED);
        Topic topic2 = session2.createTopic(MY_TOPIC_SUBSCRIPTION_NAME);

        TopicSubscriber noLocalSubscriber2 =
                session2.createDurableSubscriber(topic2, MY_TOPIC_SUBSCRIPTION_NAME + "-NoLocal",null, true);

        // The NO-local subscriber should receive messages sent from connection3
        received = receiveMessage(noLocalSubscriber2, SEND_COUNT);
        session2.commit();
        assertEquals("No Local Subscriber did not receive expected messages", SEND_COUNT, received.size());

        noLocalSubscriber2.close();


    }

    protected List<Message> receiveMessage(MessageConsumer messageConsumer,
                                           int count) throws JMSException
    {

        List<Message> receivedMessages = new ArrayList<Message>(count);
        for (int i = 0; i < count; i++)
        {
            Message received = messageConsumer.receive(1000);

            if (received != null)
            {
                receivedMessages.add(received);
            }
            else
            {
                break;
            }
        }

        return receivedMessages;
    }
}
