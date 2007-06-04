/* Copyright Rupert Smith, 2005 to 2006, all rights reserved. */
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

import java.util.Map;

import javax.jms.*;

import junit.framework.TestCase;

import org.apache.log4j.Logger;

import org.apache.qpid.util.ConversationFactory;

/**
 * A CoordinatingTestCase is a JUnit test case extension that knows how to coordinate test clients that take part in a
 * test case as defined in the interop testing specification
 * (http://cwiki.apache.org/confluence/display/qpid/Interop+Testing+Specification).
 *
 * <p/>The real logic of the test cases built on top of this, is embeded in the comparison of the sender and receiver
 * reports. An example test method might look like:
 *
 * <p/><pre>
 * public void testExample()
 * {
 *   Properties testConfig = new Properties();
 *   testConfig.add("TEST_CASE", "example");
 *   ...
 *
 *   Report[] reports = sequenceTest(testConfig);
 *
 *   // Compare sender and receiver reports.
 *   if (report[0] ... report[1] ...)
 *   {
 *     Assert.fail("Sender and receiver reports did not match up.");
 *   }
 * }
 *
 * </pre>
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Accept notification of test case participants. <td> {@link InvitingTestDecorator}
 * <tr><td> Accpet JMS Connection to carry out the coordination over.
 * <tr><td> Coordinate the test sequence amongst participants. <td> {@link ConversationFactory}
 * <tr><td> Supply test properties
 * </table>
 */
public abstract class CoordinatingTestCase extends TestCase
{
    /** Used for debugging. */
    private static final Logger log = Logger.getLogger(CoordinatingTestCase.class);

    /** Holds the contact details for the sending test client. */
    protected TestClientDetails sender;

    /** Holds the contact details for the receving test client. */
    protected TestClientDetails receiver;

    /** Holds the conversation factory over which to coordinate the test. */
    protected ConversationFactory conversationFactory;

    /**
     * Creates a new coordinating test case with the specified name.
     *
     * @param name The test case name.
     */
    public CoordinatingTestCase(String name)
    {
        super(name);
    }

    /**
     * Sets the sender test client to coordinate the test with.
     *
     * @param sender The contact details of the sending client in the test.
     */
    public void setSender(TestClientDetails sender)
    {
        log.debug("public void setSender(TestClientDetails sender = " + sender + "): called");

        this.sender = sender;
    }

    /**
     * Sets the receiving test client to coordinate the test with.
     *
     * @param receiver The contact details of the sending client in the test.
     */
    public void setReceiver(TestClientDetails receiver)
    {
        log.debug("public void setReceiver(TestClientDetails receiver = " + receiver + "): called");

        this.receiver = receiver;
    }

    /**
     * Supplies the sending test client.
     *
     * @return The sending test client.
     */
    public TestClientDetails getSender()
    {
        return sender;
    }

    /**
     * Supplies the receiving test client.
     *
     * @return The receiving test client.
     */
    public TestClientDetails getReceiver()
    {
        return receiver;
    }

    /**
     * Returns the name of the current test method of this test class, with the sending and receiving client names
     * appended on to it, so that the resulting name unqiuely identifies the test and the clients that participated
     * in it.
     *
     * @return The unique test and client name.
     */
    public String getName()
    {
        if ((sender == null) || (receiver == null))
        {
            return super.getName();
        }
        else
        {
            return super.getName() + "_sender_" + sender.clientName + "_receiver_" + receiver.clientName;
        }
    }

    /**
     * Should provide a translation from the junit method name of a test to its test case name as defined in the
     * interop testing specification. For example the method "testP2P" might map onto the interop test case name
     * "TC2_BasicP2P".
     *
     * @param methodName The name of the JUnit test method.
     *
     * @return The name of the corresponding interop test case.
     */
    public abstract String getTestCaseNameForTestMethod(String methodName);

    /**
     * Accepts the conversation factory over which to hold the test coordinating conversation.
     *
     * @param conversationFactory The conversation factory to coordinate the test over.
     */
    public void setConversationFactory(ConversationFactory conversationFactory)
    {
        this.conversationFactory = conversationFactory;
    }

    /**
     * Holds a test coordinating conversation with the test clients. This is the basic implementation of the inner
     * loop of Use Case 5. It consists of assigning the test roles, begining the test and gathering the test reports
     * from the participants.
     *
     * @param testProperties The test case definition.
     *
     * @return The test results from the senders and receivers.
     *
     * @throws JMSException All underlying JMSExceptions are allowed to fall through.
     */
    protected Message[] sequenceTest(Map<String, Object> testProperties) throws JMSException
    {
        log.debug("protected Message[] sequenceTest(Object... testProperties = " + testProperties + "): called");

        Session session = conversationFactory.getSession();
        Destination senderControlTopic = session.createTopic(sender.privateControlKey);
        Destination receiverControlTopic = session.createTopic(receiver.privateControlKey);

        ConversationFactory.Conversation senderConversation = conversationFactory.startConversation();
        ConversationFactory.Conversation receiverConversation = conversationFactory.startConversation();

        // Assign the sender role to the sending test client.
        Message assignSender = conversationFactory.getSession().createMessage();
        setPropertiesOnMessage(assignSender, testProperties);
        assignSender.setStringProperty("CONTROL_TYPE", "ASSIGN_ROLE");
        assignSender.setStringProperty("ROLE", "SENDER");

        senderConversation.send(senderControlTopic, assignSender);

        // Assign the receiver role the receiving client.
        Message assignReceiver = session.createMessage();
        setPropertiesOnMessage(assignReceiver, testProperties);
        assignReceiver.setStringProperty("CONTROL_TYPE", "ASSIGN_ROLE");
        assignReceiver.setStringProperty("ROLE", "RECEIVER");

        receiverConversation.send(receiverControlTopic, assignReceiver);

        // Wait for the senders and receivers to confirm their roles.
        senderConversation.receive();
        receiverConversation.receive();

        // Start the test.
        Message start = session.createMessage();
        start.setStringProperty("CONTROL_TYPE", "START");

        senderConversation.send(senderControlTopic, start);

        // Wait for the test sender to return its report.
        Message senderReport = senderConversation.receive();

        try
        {
            Thread.sleep(500);
        }
        catch (InterruptedException e)
        { }

        // Ask the receiver for its report.
        Message statusRequest = session.createMessage();
        statusRequest.setStringProperty("CONTROL_TYPE", "STATUS_REQUEST");

        receiverConversation.send(receiverControlTopic, statusRequest);

        // Wait for the receiver to send its report.
        Message receiverReport = receiverConversation.receive();

        return new Message[] { senderReport, receiverReport };
    }

    /**
     * Sets properties of different types on a JMS Message.
     *
     * @param message    The message to set properties on.
     * @param properties The property name/value pairs to set.
     *
     * @throws JMSException All underlying JMSExceptions are allowed to fall through.
     */
    public void setPropertiesOnMessage(Message message, Map<String, Object> properties) throws JMSException
    {
        for (Map.Entry<String, Object> entry : properties.entrySet())
        {
            String name = entry.getKey();
            Object value = entry.getValue();

            message.setObjectProperty(name, value);
        }
    }
}
