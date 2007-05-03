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
import java.util.concurrent.LinkedBlockingQueue;

import javax.jms.*;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import org.apache.log4j.Logger;

import org.apache.qpid.interop.coordinator.testcases.CoordinatingTestCase2BasicP2P;
import org.apache.qpid.interop.testclient.InteropClientTestCase;
import org.apache.qpid.interop.testclient.TestClient;
import org.apache.qpid.interop.testclient.testcases.TestCase1DummyRun;
import org.apache.qpid.interop.testclient.testcases.TestCase2BasicP2P;
import org.apache.qpid.util.ClasspathScanner;
import org.apache.qpid.util.CommandLineParser;
import org.apache.qpid.util.ConversationFactory;
import org.apache.qpid.util.PrettyPrintingUtils;

import uk.co.thebadgerset.junit.extensions.TestRunnerImprovedErrorHandling;
import uk.co.thebadgerset.junit.extensions.WrappedSuiteTestDecorator;

/**
 * <p/>Implements the coordinator client described in the interop testing specification
 * (http://cwiki.apache.org/confluence/display/qpid/Interop+Testing+Specification). This coordinator is built on
 * top of the JUnit testing framework.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Find out what test clients are available. <td> {@link ConversationFactory}
 * <tr><td> Decorate available tests to run all available clients. <td> {@link InvitingTestDecorator}
 * <tr><td> Attach XML test result logger.
 * <tr><td> Terminate the interop testing framework.
 * </table>
 */
public class Coordinator extends TestRunnerImprovedErrorHandling
{
    private static final Logger log = Logger.getLogger(Coordinator.class);

    public static final String DEFAULT_CONNECTION_PROPS_RESOURCE = "org/apache/qpid/interop/connection.properties";

    /** Holds the URL of the broker to coordinate the tests on. */
    String brokerUrl;

    /** Holds the virtual host to coordinate the tests on. If <tt>null</tt>, then the default virtual host is used. */
    String virtualHost;

    /** Holds the list of all clients that enlisted, when the compulsory invite was issued. */
    Set<TestClientDetails> enlistedClients = new HashSet<TestClientDetails>();

    /** Holds the conversation helper for the control conversation. */
    private ConversationFactory conversationFactory;

    /** Holds the connection that the coordinating messages are sent over. */
    private Connection connection;

    /**
     * Creates an interop test coordinator on the specified broker and virtual host.
     *
     * @param brokerUrl   The URL of the broker to connect to.
     * @param virtualHost The virtual host to run all tests on. Optional, may be <tt>null</tt>.
     */
    Coordinator(String brokerUrl, String virtualHost)
    {
        log.debug("Coordinator(String brokerUrl = " + brokerUrl + ", String virtualHost = " + virtualHost + "): called");

        // Retain the connection parameters.
        this.brokerUrl = brokerUrl;
        this.virtualHost = virtualHost;
    }

    /**
     * The entry point for the interop test coordinator. This client accepts the following command line arguments:
     *
     * <p/><table>
     * <tr><td> -b         <td> The broker URL.   <td> Mandatory.
     * <tr><td> -h         <td> The virtual host. <td> Optional.
     * <tr><td> name=value <td> Trailing argument define name/value pairs. Added to system properties. <td> Optional.
     * </table>
     *
     * @param args The command line arguments.
     */
    public static void main(String[] args)
    {
        try
        {
            // Use the command line parser to evaluate the command line with standard handling behaviour (print errors
            // and usage then exist if there are errors).
            Properties options =
                CommandLineParser.processCommandLine(args,
                    new CommandLineParser(
                        new String[][]
                        {
                            { "b", "The broker URL.", "broker", "false" },
                            { "h", "The virtual host to use.", "virtual host", "false" }
                        }));

            // Extract the command line options.
            String brokerUrl = options.getProperty("b");
            String virtualHost = options.getProperty("h");

            // Scan for available test cases using a classpath scanner.
            Collection<Class<? extends CoordinatingTestCase>> testCaseClasses =
                new ArrayList<Class<? extends CoordinatingTestCase>>();
            // ClasspathScanner.getMatches(CoordinatingTestCase.class, "^Test.*", true);
            // Hard code the test classes till the classpath scanner is fixed.
            Collections.addAll(testCaseClasses, new Class[] { CoordinatingTestCase2BasicP2P.class });

            // Check that some test classes were actually found.
            if ((testCaseClasses == null) || testCaseClasses.isEmpty())
            {
                throw new RuntimeException(
                    "No test classes implementing CoordinatingTestCase were found on the class path.");
            }

            int i = 0;
            String[] testClassNames = new String[testCaseClasses.size()];

            for (Class testClass : testCaseClasses)
            {
                testClassNames[i++] = testClass.getName();
            }

            // Create a coordinator and begin its test procedure.
            Coordinator coordinator = new Coordinator(brokerUrl, virtualHost);
            TestResult testResult = coordinator.start(testClassNames);

            if (!testResult.wasSuccessful())
            {
                System.exit(FAILURE_EXIT);
            }
            else
            {
                System.exit(SUCCESS_EXIT);
            }
        }
        catch (Exception e)
        {
            System.err.println(e.getMessage());
            log.error("Top level handler caught execption.", e);
            System.exit(EXCEPTION_EXIT);
        }
    }

    /**
     * Starts all of the test classes to be run by this coordinator running.
     *
     * @param testClassNames An array of all the coordinating test case implementations.
     *
     * @return A JUnit TestResult to run the tests with.
     *
     * @throws Exception Any underlying exceptions are allowed to fall through, and fail the test process.
     */
    public TestResult start(String[] testClassNames) throws Exception
    {
        log.debug("public TestResult start(String[] testClassNames = " + PrettyPrintingUtils.printArray(testClassNames)
            + "): called");

        // Connect to the broker.
        connection = TestClient.createConnection(DEFAULT_CONNECTION_PROPS_RESOURCE, brokerUrl, virtualHost);
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Destination controlTopic = session.createTopic("iop.control");
        Destination responseQueue = session.createQueue("coordinator");

        conversationFactory = new ConversationFactory(connection, responseQueue, LinkedBlockingQueue.class);
        ConversationFactory.Conversation conversation = conversationFactory.startConversation();

        // Broadcast the compulsory invitation to find out what clients are available to test.
        Message invite = session.createMessage();
        invite.setStringProperty("CONTROL_TYPE", "INVITE");
        invite.setJMSReplyTo(responseQueue);

        conversation.send(controlTopic, invite);

        // Wait for a short time, to give test clients an opportunity to reply to the invitation.
        Collection<Message> enlists = conversation.receiveAll(0, 10000);

        enlistedClients = extractEnlists(enlists);

        // Run all of the tests in the suite using JUnit.
        TestResult result = super.start(testClassNames);

        // At this point in time, all tests have completed. Broadcast the shutdown message.
        Message terminate = session.createMessage();
        terminate.setStringProperty("CONTROL_TYPE", "TERMINATE");

        conversation.send(controlTopic, terminate);

        return result;
    }

    /**
     * For a collection of enlist messages, this method pulls out of the client details for the enlisting clients.
     *
     * @param enlists The enlist messages.
     *
     * @return A set of enlisting clients, extracted from the enlist messages.
     *
     * @throws JMSException Any underlying JMSException is allowed to fall through.
     */
    public static Set<TestClientDetails> extractEnlists(Collection<Message> enlists) throws JMSException
    {
        log.debug("public static Set<TestClientDetails> extractEnlists(Collection<Message> enlists = " + enlists
            + "): called");

        Set<TestClientDetails> enlistedClients = new HashSet<TestClientDetails>();

        // Retain the list of all available clients.
        for (Message enlist : enlists)
        {
            TestClientDetails clientDetails = new TestClientDetails();
            clientDetails.clientName = enlist.getStringProperty("CLIENT_NAME");
            clientDetails.privateControlKey = enlist.getStringProperty("CLIENT_PRIVATE_CONTROL_KEY");

            enlistedClients.add(clientDetails);
        }

        return enlistedClients;
    }

    /**
     * Runs a test or suite of tests, using the super class implemenation. This method wraps the test to be run
     * in any test decorators needed to add in the coordinators ability to invite test clients to participate in
     * tests.
     *
     * @param test The test to run.
     * @param wait Undocumented. Nothing in the JUnit javadocs to say what this is for.
     *
     * @return The results of the test run.
     */
    public TestResult doRun(Test test, boolean wait)
    {
        log.debug("public TestResult doRun(Test \"" + test + "\", boolean " + wait + "): called");

        // Wrap all tests in the test suite with WrappedSuiteTestDecorators. This is quite ugly and a bit baffling,
        // but the reason it is done is because the JUnit implementation of TestDecorator has some bugs in it.
        WrappedSuiteTestDecorator targetTest = null;

        if (test instanceof TestSuite)
        {
            log.debug("targetTest is a TestSuite");

            TestSuite suite = (TestSuite) test;

            int numTests = suite.countTestCases();
            log.debug("There are " + numTests + " in the suite.");

            for (int i = 0; i < numTests; i++)
            {
                Test nextTest = suite.testAt(i);
                log.debug("suite.testAt(" + i + ") = " + nextTest);

                if (nextTest instanceof CoordinatingTestCase)
                {
                    log.debug("nextTest is a CoordinatingTestCase");
                }
            }

            targetTest = new WrappedSuiteTestDecorator(suite);
            log.debug("Wrapped with a WrappedSuiteTestDecorator.");
        }

        // Wrap the tests in an inviting test decorator, to perform the invite/test cycle.
        targetTest = new InvitingTestDecorator(targetTest, enlistedClients, conversationFactory, connection);

        return super.doRun(targetTest, wait);
    }
}
