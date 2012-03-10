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

package org.apache.qpid.test.unit.client.temporaryqueue;

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;

/**
 * Tests the behaviour of {@link TemporaryQueue}.
 */
public class TemporaryQueueTest extends QpidBrokerTestCase
{
    /**
     * Tests the basic produce/consume behaviour of a temporary queue.
     */
    public void testMessageDeliveryUsingTemporaryQueue() throws Exception
    {
        final Connection conn = getConnection();
        final Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final TemporaryQueue queue = session.createTemporaryQueue();
        assertNotNull(queue);
        final MessageProducer producer = session.createProducer(queue);
        final MessageConsumer consumer = session.createConsumer(queue);
        conn.start();
        producer.send(session.createTextMessage("hello"));
        TextMessage tm = (TextMessage) consumer.receive(2000);
        assertNotNull("Message not received", tm);
        assertEquals("hello", tm.getText());
    }

    /**
     * Tests that a temporary queue cannot be used by another {@link Session}.
     */
    public void testUseFromAnotherSessionProhibited() throws Exception
    {
        final Connection conn = getConnection();
        final Session session1 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final Session session2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final TemporaryQueue queue = session1.createTemporaryQueue();
        assertNotNull(queue);

        try
        {
            session2.createConsumer(queue);
            fail("Expected a JMSException when subscribing to a temporary queue created on a different session");
        }
        catch (JMSException je)
        {
            //pass
            assertEquals("Cannot consume from a temporary destination created on another session", je.getMessage());
        }
    }

    /**
     * Tests that the client is able to explicitly delete a temporary queue using
     * {@link TemporaryQueue#delete()} and is prevented from deleting one that
     * still has consumers.
     *
     * Note: Under < 0-10 {@link TemporaryQueue#delete()} only marks the queue as deleted
     * on the client. 0-10 causes the queue to be deleted from the Broker.
     */
    public void testExplictTemporaryQueueDeletion() throws Exception
    {
        final Connection conn = getConnection();
        final Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final AMQSession<?, ?> amqSession = (AMQSession<?, ?>)session; // Required to observe the queue binding on the Broker
        final TemporaryQueue queue = session.createTemporaryQueue();
        assertNotNull(queue);
        final MessageConsumer consumer = session.createConsumer(queue);
        conn.start();

        assertTrue("Queue should be bound", amqSession.isQueueBound((AMQDestination)queue));

        try
        {
            queue.delete();
            fail("Expected JMSException : should not be able to delete while there are active consumers");
        }
        catch (JMSException je)
        {
            //pass
            assertEquals("Temporary Queue has consumers so cannot be deleted", je.getMessage());
        }
        consumer.close();

        // Now deletion should succeed.
        queue.delete();

        try
        {
            session.createConsumer(queue);
            fail("Exception not thrown");
        }
        catch (JMSException je)
        {
            //pass
            assertEquals("Cannot consume from a deleted destination", je.getMessage());
        }

        if (isBroker010())
        {
            assertFalse("Queue should no longer be bound", amqSession.isQueueBound((AMQDestination)queue));
        }
    }

    /**
     * Tests that a temporary queue remains available for reuse even after its initial
     * consumer has disconnected.
     *
     *  This test would fail under < 0-10 as their temporary queues are deleted automatically
     *  (broker side) after the last consumer disconnects (so message2 would be lost). For this
     *  reason this test is excluded from those profiles.
     */
    public void testTemporaryQueueReused() throws Exception
    {
        final Connection conn = getConnection();
        final Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final TemporaryQueue queue = session.createTemporaryQueue();
        assertNotNull(queue);

        final MessageProducer producer1 = session.createProducer(queue);
        final MessageConsumer consumer1 = session.createConsumer(queue);
        conn.start();
        producer1.send(session.createTextMessage("message1"));
        producer1.send(session.createTextMessage("message2"));
        TextMessage tm = (TextMessage) consumer1.receive(2000);
        assertNotNull("Message not received by first consumer", tm);
        assertEquals("message1", tm.getText());
        consumer1.close();

        final MessageConsumer consumer2 = session.createConsumer(queue);
        conn.start();
        tm = (TextMessage) consumer2.receive(2000);
        assertNotNull("Message not received by second consumer", tm);
        assertEquals("message2", tm.getText());
        consumer2.close();
    }
}
