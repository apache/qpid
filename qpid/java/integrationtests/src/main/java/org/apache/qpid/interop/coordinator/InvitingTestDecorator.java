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

import java.util.*;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;

import junit.framework.Test;
import junit.framework.TestResult;

import org.apache.log4j.Logger;

import org.apache.qpid.util.ConversationFactory;

import uk.co.thebadgerset.junit.extensions.WrappedSuiteTestDecorator;

/**
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Broadcast test invitations and collect enlists. <td> {@link ConversationFactory}.
 * <tr><td> Output test failures for clients unwilling to run the test case. <td> {@link Coordinator}
 * <tr><td> Execute coordinated test cases. <td> {@link CoordinatingTestCase}
 * </table>
 */
public class InvitingTestDecorator extends WrappedSuiteTestDecorator
{
    private static final Logger log = Logger.getLogger(InvitingTestDecorator.class);

    /** Holds the contact information for all test clients that are available and that may take part in the test. */
    Set<TestClientDetails> allClients;

    /** Holds the conversation helper for the control level conversation for coordinating the test through. */
    ConversationFactory conversationFactory;

    /** Holds the connection that the control conversation is held over. */
    Connection connection;

    /** Holds the underlying {@link CoordinatingTestCase}s that this decorator wraps. */
    WrappedSuiteTestDecorator testSuite;

    /**
     * Creates a wrapped suite test decorator from another one.
     *
     * @param suite               The test suite.
     * @param availableClients    The list of all clients that responded to the compulsory invite.
     * @param controlConversation The conversation helper for the control level, test coordination conversation.
     * @param controlConnection   The connection that the coordination messages are sent over.
     */
    public InvitingTestDecorator(WrappedSuiteTestDecorator suite, Set<TestClientDetails> availableClients,
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
     * Broadcasts a test invitation and accetps enlisting from participating clients. The wrapped test case is
     * then repeated for every combination of test clients (provided the wrapped test case extends
     * {@link CoordinatingTestCase}.
     *
     * <p/>Any JMSExceptions during the invite/enlist conversation will be allowed to fall through as runtime exceptions,
     * resulting in the non-completion of the test run.
     *
     * @todo Better error recovery for failure of the invite/enlist conversation could be added.
     *
     * @param testResult The the results object to monitor the test results with.
     */
    public void run(TestResult testResult)
    {
        log.debug("public void run(TestResult testResult): called");

        Collection<Test> tests = testSuite.getAllUnderlyingTests();

        for (Test test : tests)
        {
            CoordinatingTestCase coordTest = (CoordinatingTestCase) test;

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
                Collection<Message> replies = conversation.receiveAll(allClients.size(), 3000);
                enlists = Coordinator.extractEnlists(replies);
            }
            catch (JMSException e)
            {
                throw new RuntimeException("There was a JMSException during the invite/enlist conversation.", e);
            }

            // Compare the list of willing clients to the list of all available.
            Set<TestClientDetails> optOuts = new HashSet<TestClientDetails>(allClients);
            optOuts.removeAll(enlists);

            // Output test failures for clients that will not particpate in the test.
            Set<List<TestClientDetails>> failPairs = allPairs(optOuts, allClients);

            for (List<TestClientDetails> failPair : failPairs)
            {
                CoordinatingTestCase failTest = new OptOutTestCase("testOptOut");
                failTest.setSender(failPair.get(0));
                failTest.setReceiver(failPair.get(1));

                failTest.run(testResult);
            }

            // Loop over all combinations of clients, willing to run the test.
            Set<List<TestClientDetails>> enlistedPairs = allPairs(enlists, enlists);

            for (List<TestClientDetails> enlistedPair : enlistedPairs)
            {
                // Set the sending and receiving client details on the test case.
                coordTest.setSender(enlistedPair.get(0));
                coordTest.setReceiver(enlistedPair.get(1));

                // Pass down the connection to hold the coordination conversation over.
                coordTest.setConversationFactory(conversationFactory);

                // Execute the test case.
                coordTest.run(testResult);
            }
        }
    }

    /**
     * Prints a string summarizing this test decorator, mainly for debugging purposes.
     *
     * @return String representation for debugging purposes.
     */
    public String toString()
    {
        return "InvitingTestDecorator: [ testSuite = " + testSuite + " ]";
    }

    /**
     * Produces all pairs of combinations of elements from two sets. The ordering of the elements in the pair is
     * important, that is the pair <l, r> is distinct from <r, l>; both pairs are generated. For any element, i, in
     * both the left and right sets, the reflexive pair <i, i> is not generated.
     *
     * @param left  The left set.
     * @param right The right set.
     *
     * @return All pairs formed from the permutations of all elements of the left and right sets.
     */
    private <E> Set<List<E>> allPairs(Set<E> left, Set<E> right)
    {
        log.debug("private <E> Set<List<E>> allPairs(Set<E> left = " + left + ", Set<E> right = " + right + "): called");

        Set<List<E>> results = new HashSet<List<E>>();

        // Form all pairs from left to right.
        // Form all pairs from right to left.
        for (E le : left)
        {
            for (E re : right)
            {
                if (!le.equals(re))
                {
                    results.add(new Pair<E>(le, re));
                    results.add(new Pair<E>(re, le));
                }
            }
        }

        log.debug("results = " + results);

        return results;
    }

    /**
     * A simple implementation of a pair, using a list.
     */
    private class Pair<T> extends ArrayList<T>
    {
        public Pair(T first, T second)
        {
            super();
            super.add(first);
            super.add(second);
        }
    }
}
