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

import junit.framework.Test;
import junit.framework.TestResult;
import org.apache.log4j.Logger;
import org.apache.qpid.util.ConversationFactory;
import uk.co.thebadgerset.junit.extensions.WrappedSuiteTestDecorator;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * <p><table id="crc"><caption>CRC Card</caption> <tr><th> Responsibilities <th> Collaborations <tr><td> Broadcast test
 * invitations and collect enlists. <td> {@link ConversationFactory}. <tr><td> Output test failures for clients
 * unwilling to run the test case. <td> {@link Coordinator} <tr><td> Execute coordinated test cases. <td> {@link
 * CoordinatingTestCase} </table>
 */
public class ListeningTestDecorator extends WrappedSuiteTestDecorator implements MessageListener
{
    private static final Logger log = Logger.getLogger(ListeningTestDecorator.class);

    /** Holds the contact information for all test clients that are available and that may take part in the test. */
    Set<TestClientDetails> allClients;

    /** Holds the conversation helper for the control level conversation for coordinating the test through. */
    ConversationFactory conversationFactory;

    /** Holds the connection that the control conversation is held over. */
    Connection connection;

    /** Holds the underlying {@link CoordinatingTestCase}s that this decorator wraps. */
    WrappedSuiteTestDecorator testSuite;

    /** Hold the current running test case. */
    CoordinatingTestCase _currentTest = null;

    /**
     * Creates a wrapped suite test decorator from another one.
     *
     * @param suite               The test suite.
     * @param availableClients    The list of all clients that responded to the compulsory invite.
     * @param controlConversation The conversation helper for the control level, test coordination conversation.
     * @param controlConnection   The connection that the coordination messages are sent over.
     */
    public ListeningTestDecorator(WrappedSuiteTestDecorator suite, Set<TestClientDetails> availableClients,
                                  ConversationFactory controlConversation, Connection controlConnection)
    {
        super(suite);

        log.debug("public InvitingTestDecorator(WrappedSuiteTestDecorator suite, Set<TestClientDetails> allClients = "
                  + availableClients + ", ConversationHelper controlConversation = " + controlConversation + "): called");

        testSuite = suite;
        allClients = availableClients;
        conversationFactory = controlConversation;
        connection = controlConnection;
    }

    /**
     * Broadcasts a test invitation and accetps enlisting from participating clients. The wrapped test case is then
     * repeated for every combination of test clients (provided the wrapped test case extends {@link
     * CoordinatingTestCase}.
     *
     * <p/>Any JMSExceptions during the invite/enlist conversation will be allowed to fall through as runtime
     * exceptions, resulting in the non-completion of the test run.
     *
     * @param testResult The the results object to monitor the test results with.
     *
     * @todo Better error recovery for failure of the invite/enlist conversation could be added.
     */
    public void run(TestResult testResult)
    {
        log.debug("public void run(TestResult testResult): called");

        Collection<Test> tests = testSuite.getAllUnderlyingTests();

        for (Test test : tests)
        {
            CoordinatingTestCase coordTest = (CoordinatingTestCase) test;

            Set<TestClientDetails> enlists = signupClients(coordTest);

            if (enlists.size() == 0)
            {
                throw new RuntimeException("No clients to test with");
            }

            Iterator<TestClientDetails> clients = enlists.iterator();
            coordTest.setSender(clients.next());

            while (clients.hasNext())
            {
                // Set the sending and receiving client details on the test case.
                coordTest.setReceiver(clients.next());
            }

            // Pass down the connection to hold the coordination conversation over.
            coordTest.setConversationFactory(conversationFactory);


            if (coordTest instanceof ListeningCoordinatorTest)
            {
                _currentTest = coordTest;
            }
            // Execute the test case.
            coordTest.run(testResult);

            _currentTest = null;
        }
    }

    private Set<TestClientDetails> signupClients(CoordinatingTestCase coordTest)
    {
        // Broadcast the invitation to find out what clients are available to test.
        Set<TestClientDetails> enlists;
        try
        {
            Message invite = conversationFactory.getSession().createMessage();
            Destination controlTopic = conversationFactory.getSession().createTopic("iop.control");
            ConversationFactory.Conversation conversation = conversationFactory.startConversation();

            invite.setStringProperty("CONTROL_TYPE", "INVITE");
            invite.setStringProperty("TEST_NAME", coordTest.getTestCaseNameForTestMethod(coordTest.getName()));

            conversation.send(controlTopic, invite);

            // Wait for a short time, to give test clients an opportunity to reply to the invitation.
            Collection<Message> replies = conversation.receiveAll(allClients.size(), 5000);

            log.debug("Received " + replies.size() + " enlist replies");
            
            enlists = Coordinator.extractEnlists(replies);

            //Create topic to listen on for latejoiners
            Destination listenTopic = conversationFactory.getSession().createTopic("iop.control.test." + coordTest.getTestCaseNameForTestMethod(coordTest.getName()));

            //Listen for joiners
            conversationFactory.getSession().createConsumer(listenTopic).setMessageListener(this);
            log.debug("Created consumer on :" + listenTopic);
        }
        catch (JMSException e)
        {
            throw new RuntimeException("There was a JMSException during the invite/enlist conversation.", e);
        }

        return enlists;
    }

    /**
     * Prints a string summarizing this test decorator, mainly for debugging purposes.
     *
     * @return String representation for debugging purposes.
     */
    public String toString()
    {
        return "ListeningTestDecorator: [ testSuite = " + testSuite + " ]";
    }


    public void onMessage(Message message)
    {
        try
        {
            if (message.getStringProperty("CONTROL_TYPE").equals("LATEJOIN"))
            {
                ((ListeningCoordinatorTest) _currentTest).latejoin(message);
            }
        }
        catch (JMSException e)
        {
            log.debug("Unable to process message:" + message);
        }
    }
}
