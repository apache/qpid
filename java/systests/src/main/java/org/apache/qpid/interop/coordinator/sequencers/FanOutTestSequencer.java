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
package org.apache.qpid.interop.coordinator.sequencers;

import org.apache.log4j.Logger;

import org.apache.qpid.interop.coordinator.TestClientDetails;
import org.apache.qpid.test.framework.Assertion;
import org.apache.qpid.test.framework.Circuit;
import org.apache.qpid.test.framework.TestUtils;
import org.apache.qpid.util.ConversationFactory;

import uk.co.thebadgerset.junit.extensions.util.ParsedProperties;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import java.util.List;
import java.util.Properties;

/**
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td>
 * </table>
 */
public class FanOutTestSequencer extends BaseDistributedTestSequencer
{
    /** Used for debugging. */
    Logger log = Logger.getLogger(FanOutTestSequencer.class);

    /**
     * Holds a test coordinating conversation with the test clients. This should consist of assigning the test roles,
     * begining the test, gathering the test reports from the participants, and checking for assertion failures against
     * the test reports.
     *
     * @param testCircuit    The test circuit.
     * @param assertions     The list of assertions to apply to the test circuit.
     * @param testProperties The test case definition.
     */
    public void sequenceTest(Circuit testCircuit, List<Assertion> assertions, Properties testProperties)
    {
        log.debug("protected Message[] sequenceTest(Object... testProperties = " + testProperties + "): called");

        TestClientDetails sender = getSender();
        List<TestClientDetails> receivers = getReceivers();
        ConversationFactory conversationFactory = getConversationFactory();

        try
        {
            // Create a conversation on the sender clients private control rouete.
            Session session = conversationFactory.getSession();
            Destination senderControlTopic = session.createTopic(sender.privateControlKey);
            ConversationFactory.Conversation senderConversation = conversationFactory.startConversation();

            // Assign the sender role to the sending test client.
            Message assignSender = conversationFactory.getSession().createMessage();
            TestUtils.setPropertiesOnMessage(assignSender, testProperties);
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
            // return new Message[] { senderReport };
        }
        catch (JMSException e)
        {
            throw new RuntimeException("Unhandled JMSException.");
        }
    }

    /**
     * Creates a test circuit for the test, configered by the test parameters specified.
     *
     * @param testProperties The test parameters.
     * @return A test circuit.
     */
    public Circuit createCircuit(ParsedProperties testProperties)
    {
        throw new RuntimeException("Not implemented.");
    }

    /**
     * Assigns the receivers role to the specified test client that is to act as a receivers during the test. This method
     * does not always wait for the receiving clients to confirm their role assignments. This is because this method
     * may be called from an 'onMessage' method, when a client is joining the test at a later point in time, and it
     * is not possible to do a synchronous receive during an 'onMessage' method. There is a flag to indicate whether
     * or not to wait for role confirmations.
     *
     * @param receiver       The test client to assign the receivers role to.
     * @param testProperties The test parameters.
     * @param confirm        Indicates whether role confirmation should be waited for.
     *
     * @throws JMSException Any JMSExceptions occurring during the conversation are allowed to fall through.
     */
    protected void assignReceiverRole(TestClientDetails receiver, Properties testProperties, boolean confirm)
        throws JMSException
    {
        log.info("assignReceiverRole(TestClientDetails receivers = " + receiver + ", Map<String, Object> testProperties = "
            + testProperties + "): called");

        ConversationFactory conversationFactory = getConversationFactory();

        // Create a conversation with the receiving test client.
        Session session = conversationFactory.getSession();
        Destination receiverControlTopic = session.createTopic(receiver.privateControlKey);
        ConversationFactory.Conversation receiverConversation = conversationFactory.startConversation();

        // Assign the receivers role to the receiving client.
        Message assignReceiver = session.createMessage();
        TestUtils.setPropertiesOnMessage(assignReceiver, testProperties);
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
