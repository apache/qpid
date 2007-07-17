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
package org.apache.qpid.interop.coordinator;

import org.apache.log4j.Logger;

import org.apache.qpid.test.framework.TestUtils;
import org.apache.qpid.util.ConversationFactory;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * FanOutTestCase is a {@link org.apache.qpid.interop.coordinator.InteropTestCase} across one sending client and
 * zero or more receiving clients. Its main purpose is to coordinate the setting up of one test client in the sending
 * role and the remainder in the receiving role.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Accept notification of test case participants.
 *     <td> {@link org.apache.qpid.interop.coordinator.InvitingTestDecorator}
 * <tr><td> Accept JMS Connection to carry out the coordination over.
 * <tr><td> Coordinate the test sequence amongst participants. <td> {@link ConversationFactory}
 * <tr><td> Supply test properties
 * </table>
 *
 * @todo Gather all the receivers reports.
 */
public abstract class FanOutTestCase extends InteropTestCase
{
    /** Used for debugging. */
    private static final Logger log = Logger.getLogger(FanOutTestCase.class);

    /** The test clients in the receiving role. */
    private List<TestClientDetails> receivers = new LinkedList<TestClientDetails>();

    /**
     * Creates a new coordinating test case with the specified name.
     *
     * @param name The test case name.
     */
    public FanOutTestCase(String name)
    {
        super(name);
    }

    /**
     * Adds a receiver to this test.
     *
     * @param receiver The contact details of the sending client in the test.
     */
    public void setReceiver(TestClientDetails receiver)
    {
        receivers.add(receiver);
    }

    /**
     * Holds a test coordinating conversation with the test clients. This is the basic implementation of the inner loop
     * of Use Case 5. It consists of assigning the test roles, begining the test and gathering the test reports from the
     * participants.
     *
     * @param testProperties The test case definition.
     *
     * @return The test results from the senders and receivers. The senders report will always be returned first,
     *         followed by the receivers reports.
     *
     * @throws JMSException All underlying JMSExceptions are allowed to fall through.
     */
    protected Message[] sequenceTest(Map<String, Object> testProperties) throws JMSException
    {
        log.debug("protected Message[] sequenceTest(Object... testProperties = " + testProperties + "): called");

        // Create a conversation on the sender clients private control rouete.
        Session session = conversationFactory.getSession();
        Destination senderControlTopic = session.createTopic(sender.privateControlKey);
        ConversationFactory.Conversation senderConversation = conversationFactory.startConversation();

        // Assign the sender role to the sending test client.
        Message assignSender = conversationFactory.getSession().createMessage();
        setPropertiesOnMessage(assignSender, testProperties);
        assignSender.setStringProperty("CONTROL_TYPE", "ASSIGN_ROLE");
        assignSender.setStringProperty("ROLE", "SENDER");
        assignSender.setStringProperty("CLIENT_NAME", "Sustained_SENDER");

        senderConversation.send(senderControlTopic, assignSender);

        // Wait for the sender to confirm its role.
        senderConversation.receive();

        // Assign the receivers roles.
        for (TestClientDetails receiver : receivers)
        {
            assignReceiverRole(receiver, testProperties, true);
        }

        // Start the test on the sender.
        Message start = session.createMessage();
        start.setStringProperty("CONTROL_TYPE", "START");

        senderConversation.send(senderControlTopic, start);

        // Wait for the test sender to return its report.
        Message senderReport = senderConversation.receive();
        TestUtils.pause(500);

        // Ask the receivers for their reports.
        Message statusRequest = session.createMessage();
        statusRequest.setStringProperty("CONTROL_TYPE", "STATUS_REQUEST");

        // Gather the reports from all of the receiving clients.

        // Return all of the test reports, the senders report first.
        return new Message[] { senderReport };
    }

    /**
     * Assigns the receiver role to the specified test client that is to act as a receiver during the test. This method
     * does not always wait for the receiving clients to confirm their role assignments. This is because this method
     * may be called from an 'onMessage' method, when a client is joining the test at a later point in time, and it
     * is not possible to do a synchronous receive during an 'onMessage' method. There is a flag to indicate whether
     * or not to wait for role confirmations.
     *
     * @param receiver       The test client to assign the receiver role to.
     * @param testProperties The test parameters.
     * @param confirm        Indicates whether role confirmation should be waited for.
     *
     * @throws JMSException Any JMSExceptions occurring during the conversation are allowed to fall through.
     */
    protected void assignReceiverRole(TestClientDetails receiver, Map<String, Object> testProperties, boolean confirm)
        throws JMSException
    {
        log.info("assignReceiverRole(TestClientDetails receiver = " + receiver + ", Map<String, Object> testProperties = "
            + testProperties + "): called");

        // Create a conversation with the receiving test client.
        Session session = conversationFactory.getSession();
        Destination receiverControlTopic = session.createTopic(receiver.privateControlKey);
        ConversationFactory.Conversation receiverConversation = conversationFactory.startConversation();

        // Assign the receiver role to the receiving client.
        Message assignReceiver = session.createMessage();
        setPropertiesOnMessage(assignReceiver, testProperties);
        assignReceiver.setStringProperty("CONTROL_TYPE", "ASSIGN_ROLE");
        assignReceiver.setStringProperty("ROLE", "RECEIVER");
        assignReceiver.setStringProperty("CLIENT_NAME", receiver.clientName);

        receiverConversation.send(receiverControlTopic, assignReceiver);

        // Wait for the role confirmation to come back.
        if (confirm)
        {
            receiverConversation.receive();
        }
    }
}
