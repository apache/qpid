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
package org.apache.qpid.test.unit.topic;

import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;


/**
 * Tests the behaviour of {@link TemporaryTopic}.
 */
public class TemporaryTopicTest extends QpidBrokerTestCase
{
    /**
     * Tests the basic publish/subscribe behaviour of a temporary topic. Single
     * message is sent to two subscribers.
     */
    public void testMessageDeliveryUsingTemporaryTopic() throws Exception
    {
        final Connection conn = getConnection();
        final Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final TemporaryTopic topic = session.createTemporaryTopic();
        assertNotNull(topic);
        final MessageProducer producer = session.createProducer(topic);
        final MessageConsumer consumer1 = session.createConsumer(topic);
        final MessageConsumer consumer2 = session.createConsumer(topic);
        conn.start();
        producer.send(session.createTextMessage("hello"));

        final TextMessage tm1 = (TextMessage) consumer1.receive(2000);
        final TextMessage tm2 = (TextMessage) consumer2.receive(2000);

        assertNotNull("Message not received by subscriber1", tm1);
        assertEquals("hello", tm1.getText());
        assertNotNull("Message not received by subscriber2", tm2);
        assertEquals("hello", tm2.getText());
    }

    /**
     * Tests that the client is able to explicitly delete a temporary topic using
     * {@link TemporaryTopic#delete()} and is prevented from deleting one that
     * still has consumers.
     *
     * Note: Under < 0-10 {@link TemporaryTopic#delete()} only marks the queue as deleted
     * on the client. 0-10 causes the topic to be deleted from the Broker.
     */
    public void testExplictTemporaryTopicDeletion() throws Exception
    {
        final Connection conn = getConnection();

        final Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final TemporaryTopic topic = session.createTemporaryTopic();
        assertNotNull(topic);
        final MessageConsumer consumer = session.createConsumer(topic);
        conn.start();
        try
        {
            topic.delete();
            fail("Expected JMSException : should not be able to delete while there are active consumers");
        }
        catch (JMSException je)
        {
            //pass
            assertEquals("Temporary Topic has consumers so cannot be deleted", je.getMessage());
        }

        consumer.close();

        // Now deletion should succeed.
        topic.delete();

        try
        {
            session.createConsumer(topic);
            fail("Exception not thrown");
        }
        catch (JMSException je)
        {
            //pass
            assertEquals("Cannot consume from a deleted destination", je.getMessage());
        }
    }

    /**
     * Tests that a temporary topic cannot be used by another {@link Session}.
     */
    public void testUseFromAnotherSessionProhibited() throws Exception
    {
        final Connection conn = getConnection();
        final Session session1 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final Session session2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final TemporaryTopic topic = session1.createTemporaryTopic();

        try
        {
            session2.createConsumer(topic);
            fail("Expected a JMSException when subscribing to a temporary topic created on a different session");
        }
        catch (JMSException je)
        {
            // pass
            assertEquals("Cannot consume from a temporary destination created on another session", je.getMessage());
        }
    }

    /**
     * Tests that the client is prohibited from creating a durable subscriber for a temporary
     * queue.
     */
    public void testDurableSubscriptionProhibited() throws Exception
    {
        final Connection conn = getConnection();

        final Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final TemporaryTopic topic = session.createTemporaryTopic();
        assertNotNull(topic);
        try
        {
            session.createDurableSubscriber(topic, null);
            fail("Expected JMSException : should not be able to create durable subscription from temp topic");
        }
        catch (JMSException je)
        {
            //pass
            assertEquals("Cannot create a durable subscription with a temporary topic: " + topic.toString(), je.getMessage());
        }
    }

    /**
     * Tests that a temporary topic remains available for reuse even after its initial
     * subscribers have disconnected.
     */
    public void testTemporaryTopicReused() throws Exception
    {
        final Connection conn = getConnection();
        final Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final TemporaryTopic topic = session.createTemporaryTopic();
        assertNotNull(topic);

        final MessageProducer producer = session.createProducer(topic);
        final MessageConsumer consumer1 = session.createConsumer(topic);
        conn.start();
        producer.send(session.createTextMessage("message1"));
        TextMessage tm = (TextMessage) consumer1.receive(2000);
        assertNotNull("Message not received by first consumer", tm);
        assertEquals("message1", tm.getText());
        consumer1.close();

        final MessageConsumer consumer2 = session.createConsumer(topic);
        conn.start();
        producer.send(session.createTextMessage("message2"));
        tm = (TextMessage) consumer2.receive(2000);
        assertNotNull("Message not received by second consumer", tm);
        assertEquals("message2", tm.getText());
        consumer2.close();
    }
}
