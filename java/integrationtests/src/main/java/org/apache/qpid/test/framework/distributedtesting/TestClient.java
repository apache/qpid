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
package org.apache.qpid.test.framework.distributedtesting;

import org.apache.log4j.Logger;

import org.apache.qpid.interop.clienttestcases.TestCase1DummyRun;
import org.apache.qpid.interop.clienttestcases.TestCase2BasicP2P;
import org.apache.qpid.interop.clienttestcases.TestCase3BasicPubSub;
import org.apache.qpid.sustained.SustainedClientTestCase;
import org.apache.qpid.test.framework.MessagingTestConfigProperties;
import org.apache.qpid.test.framework.TestUtils;

import uk.co.thebadgerset.junit.extensions.util.ParsedProperties;
import uk.co.thebadgerset.junit.extensions.util.TestContextProperties;

import javax.jms.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Implements a test client as described in the interop testing spec
 * (http://cwiki.apache.org/confluence/display/qpid/Interop+Testing+Specification). A test client is an agent that
 * reacts to control message sequences send by the test {@link org.apache.qpid.test.framework.distributedtesting.Coordinator}.
 *
 * <p/><table><caption>Messages Handled by SustainedTestClient</caption>
 * <tr><th> Message               <th> Action
 * <tr><td> Invite(compulsory)    <td> Reply with Enlist.
 * <tr><td> Invite(test case)     <td> Reply with Enlist if test case available.
 * <tr><td> AssignRole(test case) <td> Reply with Accept Role if matches an enlisted test. Keep test parameters.
 * <tr><td> Start                 <td> Send test messages defined by test parameters. Send report on messages sent.
 * <tr><td> Status Request        <td> Send report on messages received.
 * <tr><td> Terminate             <td> Terminate the test client.
 * </table>
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Handle all incoming control messages. <td> {@link InteropClientTestCase}
 * <tr><td> Configure and look up test cases by name. <td> {@link InteropClientTestCase}
 * </table>
 */
public class TestClient implements MessageListener
{
    /** Used for debugging. */
    private static final Logger log = Logger.getLogger(TestClient.class);

    /** Used for reporting to the console. */
    private static final Logger console = Logger.getLogger("CONSOLE");

    /** Holds the default identifying name of the test client. */
    public static final String CLIENT_NAME = "java";

    /** Holds the URL of the broker to run the tests on. */
    public static String brokerUrl;

    /** Holds the virtual host to run the tests on. If <tt>null</tt>, then the default virtual host is used. */
    public static String virtualHost;

    /**
     * Holds the test context properties that provides the default test parameters, plus command line overrides.
     * This is initialized with the default test parameters, to which command line overrides may be applied.
     */
    public static ParsedProperties testContextProperties =
        TestContextProperties.getInstance(MessagingTestConfigProperties.defaults);

    /** Holds all the test cases loaded from the classpath. */
    Map<String, InteropClientTestCase> testCases = new HashMap<String, InteropClientTestCase>();

    /** Holds the test case currently being run by this client. */
    protected InteropClientTestCase currentTestCase;

    /** Holds the connection to the broker that the test is being coordinated on. */
    protected Connection connection;

    /** Holds the message producer to hold the test coordination over. */
    protected MessageProducer producer;

    /** Holds the JMS session for the test coordination. */
    protected Session session;

    /** Holds the name of this client, with a default value. */
    protected String clientName = CLIENT_NAME;

    /** This flag indicates that the test client should attempt to join the currently running test case on start up. */
    protected boolean join;

    /**
     * Creates a new interop test client, listenting to the specified broker and virtual host, with the specified client
     * identifying name.
     *
     * @param brokerUrl   The url of the broker to connect to.
     * @param virtualHost The virtual host to conect to.
     * @param clientName  The client name to use.
     */
    public TestClient(String brokerUrl, String virtualHost, String clientName, boolean join)
    {
        log.debug("public SustainedTestClient(String brokerUrl = " + brokerUrl + ", String virtualHost = " + virtualHost
            + ", String clientName = " + clientName + "): called");

        // Retain the connection parameters.
        this.brokerUrl = brokerUrl;
        this.virtualHost = virtualHost;
        this.clientName = clientName;
        this.join = join;
    }

    /**
     * The entry point for the interop test coordinator. This client accepts the following command line arguments:
     *
     * <p/><table>
     * <tr><td> -b         <td> The broker URL.       <td> Optional.
     * <tr><td> -h         <td> The virtual host.     <td> Optional.
     * <tr><td> -n         <td> The test client name. <td> Optional.
     * <tr><td> name=value <td> Trailing argument define name/value pairs. Added to system properties. <td> Optional.
     * </table>
     *
     * @param args The command line arguments.
     */
    public static void main(String[] args)
    {
        // Override the default broker url to be localhost:5672.
        testContextProperties.setProperty(MessagingTestConfigProperties.BROKER_PROPNAME, "tcp://localhost:5672");

        // Use the command line parser to evaluate the command line with standard handling behaviour (print errors
        // and usage then exist if there are errors).
        // Any options and trailing name=value pairs are also injected into the test context properties object,
        // to override any defaults that may have been set up.
        ParsedProperties options =
            new ParsedProperties(uk.co.thebadgerset.junit.extensions.util.CommandLineParser.processCommandLine(args,
                    new uk.co.thebadgerset.junit.extensions.util.CommandLineParser(
                        new String[][]
                        {
                            { "b", "The broker URL.", "broker", "false" },
                            { "h", "The virtual host to use.", "virtual host", "false" },
                            { "o", "The name of the directory to output test timings to.", "dir", "false" },
                            { "n", "The name of the test client.", "name", "false" },
                            { "j", "Join this test client to running test.", "false" }
                        }), testContextProperties));

        // Extract the command line options.
        String brokerUrl = options.getProperty("b");
        String virtualHost = options.getProperty("h");
        String clientName = options.getProperty("n");
        boolean join = options.getPropertyAsBoolean("j");

        // Create a test client and start it running.
        TestClient client = new TestClient(brokerUrl, virtualHost, (clientName == null) ? CLIENT_NAME : clientName, join);

        // Use a class path scanner to find all the interop test case implementations.
        // Hard code the test classes till the classpath scanner is fixed.
        Collection<Class<? extends InteropClientTestCase>> testCaseClasses =
            new ArrayList<Class<? extends InteropClientTestCase>>();
        // ClasspathScanner.getMatches(InteropClientTestCase.class, "^TestCase.*", true);
        Collections.addAll(testCaseClasses, TestCase1DummyRun.class, TestCase2BasicP2P.class, TestCase3BasicPubSub.class,
            SustainedClientTestCase.class);

        try
        {
            client.start(testCaseClasses);
        }
        catch (Exception e)
        {
            log.error("The test client was unable to start.", e);
            System.exit(1);
        }
    }

    /**
     * Starts the interop test client running. This causes it to start listening for incoming test invites.
     *
     * @param testCaseClasses The classes of the available test cases. The test case names from these are used to
     *                        matchin incoming test invites against.
     *
     * @throws JMSException Any underlying JMSExceptions are allowed to fall through.
     */
    protected void start(Collection<Class<? extends InteropClientTestCase>> testCaseClasses) throws JMSException
    {
        log.debug("private void start(): called");

        // Create all the test case implementations and index them by the test names.
        for (Class<? extends InteropClientTestCase> nextClass : testCaseClasses)
        {
            try
            {
                InteropClientTestCase testCase = nextClass.newInstance();
                testCases.put(testCase.getName(), testCase);
            }
            catch (InstantiationException e)
            {
                log.warn("Could not instantiate test case class: " + nextClass.getName(), e);
                // Ignored.
            }
            catch (IllegalAccessException e)
            {
                log.warn("Could not instantiate test case class due to illegal access: " + nextClass.getName(), e);
                // Ignored.
            }
        }

        // Open a connection to communicate with the coordinator on.
        connection = TestUtils.createConnection(testContextProperties);
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Set this up to listen for control messages.
        Topic privateControlTopic = session.createTopic("iop.control." + clientName);
        MessageConsumer consumer = session.createConsumer(privateControlTopic);
        consumer.setMessageListener(this);

        Topic controlTopic = session.createTopic("iop.control");
        MessageConsumer consumer2 = session.createConsumer(controlTopic);
        consumer2.setMessageListener(this);

        // Create a producer to send replies with.
        producer = session.createProducer(null);

        // If the join flag was set, then broadcast a join message to notify the coordinator that a new test client
        // is available to join the current test case, if it supports it. This message may be ignored, or it may result
        // in this test client receiving a test invite.
        if (join)
        {
            Message joinMessage = session.createMessage();

            joinMessage.setStringProperty("CONTROL_TYPE", "JOIN");
            joinMessage.setStringProperty("CLIENT_NAME", clientName);
            joinMessage.setStringProperty("CLIENT_PRIVATE_CONTROL_KEY", "iop.control." + clientName);
            producer.send(controlTopic, joinMessage);
        }

        // Start listening for incoming control messages.
        connection.start();
    }

    /**
     * Handles all incoming control messages.
     *
     * @param message The incoming message.
     */
    public void onMessage(Message message)
    {
        log.debug("public void onMessage(Message message = " + message + "): called");

        try
        {
            String controlType = message.getStringProperty("CONTROL_TYPE");
            String testName = message.getStringProperty("TEST_NAME");

            log.info("onMessage(Message message = " + message + "): for '" + controlType + "' to '" + testName + "'");

            // Check if the message is a test invite.
            if ("INVITE".equals(controlType))
            {
                // Flag used to indicate that an enlist should be sent. Only enlist to compulsory invites or invites
                // for which test cases exist.
                boolean enlist = false;

                if (testName != null)
                {
                    log.debug("Got an invite to test: " + testName);

                    // Check if the requested test case is available.
                    InteropClientTestCase testCase = testCases.get(testName);

                    if (testCase != null)
                    {
                        // Make the requested test case the current test case.
                        currentTestCase = testCase;
                        enlist = true;
                    }
                    else
                    {
                        log.debug("Received an invite to the test '" + testName + "' but this test is not known.");
                    }
                }
                else
                {
                    log.debug("Got a compulsory invite.");

                    enlist = true;
                }

                if (enlist)
                {
                    // Reply with the client name in an Enlist message.
                    Message enlistMessage = session.createMessage();
                    enlistMessage.setStringProperty("CONTROL_TYPE", "ENLIST");
                    enlistMessage.setStringProperty("CLIENT_NAME", clientName);
                    enlistMessage.setStringProperty("CLIENT_PRIVATE_CONTROL_KEY", "iop.control." + clientName);
                    enlistMessage.setJMSCorrelationID(message.getJMSCorrelationID());

                    log.info("Sending Message '" + enlistMessage + "'. to " + message.getJMSReplyTo());

                    producer.send(message.getJMSReplyTo(), enlistMessage);
                }
            }
            else if ("ASSIGN_ROLE".equals(controlType))
            {
                // Assign the role to the current test case.
                String roleName = message.getStringProperty("ROLE");

                log.debug("Got a role assignment to role: " + roleName);

                InteropClientTestCase.Roles role = Enum.valueOf(InteropClientTestCase.Roles.class, roleName);

                currentTestCase.assignRole(role, message);

                // Reply by accepting the role in an Accept Role message.
                Message acceptRoleMessage = session.createMessage();
                acceptRoleMessage.setStringProperty("CONTROL_TYPE", "ACCEPT_ROLE");
                acceptRoleMessage.setJMSCorrelationID(message.getJMSCorrelationID());

                producer.send(message.getJMSReplyTo(), acceptRoleMessage);
            }
            else if ("START".equals(controlType) || "STATUS_REQUEST".equals(controlType))
            {
                if ("START".equals(controlType))
                {
                    log.debug("Got a start notification.");

                    // Start the current test case.
                    currentTestCase.start();
                }
                else
                {
                    log.debug("Got a status request.");
                }

                // Generate the report from the test case and reply with it as a Report message.
                Message reportMessage = currentTestCase.getReport(session);
                reportMessage.setStringProperty("CONTROL_TYPE", "REPORT");
                reportMessage.setJMSCorrelationID(message.getJMSCorrelationID());

                producer.send(message.getJMSReplyTo(), reportMessage);
            }
            else if ("TERMINATE".equals(controlType))
            {
                log.info("Received termination instruction from coordinator.");

                // Is a cleaner shutdown needed?
                connection.close();
                System.exit(0);
            }
            else
            {
                // Log a warning about this but otherwise ignore it.
                log.warn("Got an unknown control message, controlType = " + controlType + ", message = " + message);
            }
        }
        catch (JMSException e)
        {
            // Log a warning about this, but otherwise ignore it.
            log.warn("A JMSException occurred whilst handling a message.");
            log.debug("Got JMSException whilst handling message: " + message, e);
        }
    }
}
