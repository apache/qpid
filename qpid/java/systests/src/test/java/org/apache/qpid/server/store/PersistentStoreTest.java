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

package org.apache.qpid.server.store;

import java.util.ArrayList;
import java.util.List;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.client.AMQSession;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class PersistentStoreTest extends QpidBrokerTestCase
{
    private static final int NUM_MESSAGES = 100;
    private Connection _con;
    private Session _session;
    private Destination _destination;

    public void setUp() throws Exception
    {
        super.setUp();
        _con = getConnection();
    }

    public void testCommittedMessagesSurviveBrokerNormalShutdown() throws Exception
    {
        sendAndCommitMessages();
        stopBroker();
        startBroker();
        confirmBrokerStillHasCommittedMessages();
    }

    public void testCommittedMessagesSurviveBrokerAbnormalShutdown() throws Exception
    {
        if (isInternalBroker())
        {
            return;
        }

        sendAndCommitMessages();
        killBroker();
        startBroker();
        confirmBrokerStillHasCommittedMessages();
    }

    public void testCommittedMessagesSurviveBrokerNormalShutdownMidTransaction() throws Exception
    {
        sendAndCommitMessages();
        sendMoreMessagesWithoutCommitting();
        stopBroker();
        startBroker();
        confirmBrokerStillHasCommittedMessages();
    }

    public void testCommittedMessagesSurviveBrokerAbnormalShutdownMidTransaction() throws Exception
    {
        if (isInternalBroker())
        {
            return;
        }
        sendAndCommitMessages();
        sendMoreMessagesWithoutCommitting();
        killBroker();
        startBroker();
        confirmBrokerStillHasCommittedMessages();
    }

    private void sendAndCommitMessages() throws Exception
    {
        _session = _con.createSession(true, Session.SESSION_TRANSACTED);
        _destination = _session.createQueue(getTestQueueName());
        // Create queue by consumer side-effect
        _session.createConsumer(_destination).close();

        sendMessage(_session, _destination, NUM_MESSAGES);
        _session.commit();
    }

    private void sendMoreMessagesWithoutCommitting() throws Exception
    {
        sendMessage(_session, _destination, 5);
        // sync to ensure that messages have reached the broker
        ((AMQSession<?,?>) _session).sync();
    }

    private void confirmBrokerStillHasCommittedMessages() throws Exception
    {
        Connection con = getConnection();
        Session session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
        con.start();
        Destination destination = session.createQueue(getTestQueueName());
        MessageConsumer consumer = session.createConsumer(destination);
        for (int i = 1; i <= NUM_MESSAGES; i++)
        {
            Message msg = consumer.receive(RECEIVE_TIMEOUT);
            assertNotNull("Message " + i + " not received", msg);
            assertEquals("Did not receive the expected message", i, msg.getIntProperty(INDEX));
        }

        Message msg = consumer.receive(100);
        if(msg != null)
        {
            fail("No more messages should be received, but received additional message with index: " + msg.getIntProperty(INDEX));
        }
    }

    /**
     * This test requires that we can send messages without committing.
     * QTC always commits the messages sent via sendMessages.
     *
     * @param session the session to use for sending
     * @param destination where to send them to
     * @param count no. of messages to send
     *
     * @return the sent messages
     *
     * @throws Exception
     */
    @Override
    public List<Message> sendMessage(Session session, Destination destination,
                                     int count) throws Exception
    {
        List<Message> messages = new ArrayList<Message>(count);

        MessageProducer producer = session.createProducer(destination);

        for (int i = 1;i <= (count); i++)
        {
            Message next = createNextMessage(session, i);

            producer.send(next);

            messages.add(next);
        }

        return messages;
    }

}
