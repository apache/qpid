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
package org.apache.qpid.interop.testclient;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.log4j.Logger;

import org.apache.qpid.util.ClasspathScanner;
import org.apache.qpid.util.CommandLineParser;
import org.apache.qpid.util.PropertiesUtils;

/**
 * Implements a test client as described in the interop testing spec
 * (http://cwiki.apache.org/confluence/display/qpid/Interop+Testing+Specification). A test client is an agent that
 * reacts to control message sequences send by the test {@link org.apache.qpid.interop.coordinator.Coordinator}.
 *
 * <p/><table><caption>Messages Handled by TestClient</caption>
 * <tr><th> Message               <th> Action
 * <tr><td> Invite(compulsory)    <td> Reply with Enlist.
 * <tr><td> Invite(test case)     <td> Reply with Enlist if test case available.
 * <tr><td> AssignRole(test case) <td> Reply with Accept Role if matches an enlisted test. Keep test parameters.
 * <tr><td> Start                 <td> Send test messages defined by test parameters. Send report on messages sent.
 * <tr><td> Status Request        <td> Send report on messages received.
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
    private static Logger log = Logger.getLogger(TestClient.class);

    /** Holds the URL of the broker to run the tests on. */
    String brokerUrl;

    /** Holds the virtual host to run the tests on. If <tt>null</tt>, then the default virtual host is used. */
    String virtualHost;

    /** Holds all the test cases loaded from the classpath. */
    Map<String, InteropClientTestCase> testCases = new HashMap<String, InteropClientTestCase>();

    InteropClientTestCase currentTestCase;

    public static final String CONNECTION_PROPERTY = "connectionfactory.broker";
    public static final String CONNECTION_NAME = "broker";
    public static final String CLIENT_NAME = "java";
    public static final String DEFAULT_CONNECTION_PROPS_RESOURCE = "org/apache/qpid/interop/client/connection.properties";

    private MessageProducer producer;
    private Session session;

    public TestClient(String brokerUrl, String virtualHost)
    {
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
        // Use the command line parser to evaluate the command line.
        CommandLineParser commandLine =
            new CommandLineParser(new String[][]
                                  {
                                      { "b", "The broker URL.", "broker", "true" },
                                      { "h", "The virtual host to use.", "virtual host", "false" }
                                  });

        // Capture the command line arguments or display errors and correct usage and then exit.
        Properties options = null;

        try
        {
            options = commandLine.parseCommandLine(args);
        }
        catch (IllegalArgumentException e)
        {
            System.out.println(commandLine.getErrors());
            System.out.println(commandLine.getUsage());
            System.exit(1);
        }

        // Extract the command line options.
        String brokerUrl = options.getProperty("b");
        String virtualHost = options.getProperty("h");

        // Add all the trailing command line options (name=value pairs) to system properties. Tests may pick up
        // overridden values from there.
        commandLine.addCommandLineToSysProperties();

        // Create a test client and start it running.
        TestClient client = new TestClient(brokerUrl, virtualHost);

        try
        {
            client.start();
        }
        catch (Exception e)
        {
            log.error("The test client was unable to start.", e);
            System.exit(1);
        }
    }

    private void start() throws JMSException
    {
        // Use a class path scanner to find all the interop test case implementations.
        Collection<Class<? extends InteropClientTestCase>> testCaseClasses =
            ClasspathScanner.getMatches(InteropClientTestCase.class, "^TestCase.*", true);

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
        Connection connection = createConnection(DEFAULT_CONNECTION_PROPS_RESOURCE, brokerUrl, virtualHost);

        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Set this up to listen for control messages.
        MessageConsumer consumer = session.createConsumer(session.createTopic("iop.control." + CLIENT_NAME));
        consumer.setMessageListener(this);

        // Create a producer to send replies with.
        producer = session.createProducer(null);

        // Start listening for incoming control messages.
        connection.start();
    }

    /**
     * Establishes a JMS connection using a properties file and qpids built in JNDI implementation. This is a simple
     * convenience method for code that does anticipate handling connection failures. All exceptions that indicate
     * that the connection has failed, are wrapped as rutime exceptions, preumably handled by a top level failure
     * handler.
     *
     * @todo Make username/password configurable. Allow multiple urls for fail over. Once it feels right, move it
     *       to a Utils library class.
     *
     * @param connectionPropsResource The name of the connection properties file.
     * @param brokerUrl               The broker url to connect to, <tt>null</tt> to use the default from the properties.
     * @param virtualHost             The virtual host to connectio to, <tt>null</tt> to use the default.
     *
     * @return A JMS conneciton.
     */
    private static Connection createConnection(String connectionPropsResource, String brokerUrl, String virtualHost)
    {
        try
        {
            Properties connectionProps =
                PropertiesUtils.getProperties(TestClient.class.getClassLoader().getResourceAsStream(
                                                  connectionPropsResource));

            String connectionString =
                "amqp://guest:guest/" + ((virtualHost != null) ? virtualHost : "") + "?brokerlist='" + brokerUrl + "'";
            connectionProps.setProperty(CONNECTION_PROPERTY, connectionString);

            Context ctx = new InitialContext(connectionProps);

            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(CONNECTION_NAME);
            Connection connection = cf.createConnection();

            return connection;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        catch (NamingException e)
        {
            throw new RuntimeException(e);
        }
        catch (JMSException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Handles all incoming control messages.
     *
     * @param message The incoming message.
     */
    public void onMessage(Message message)
    {
        try
        {
            String controlType = message.getStringProperty("CONTROL_TYPE");
            String testName = message.getStringProperty("TEST_NAME");

            // Check if the message is a test invite.
            if ("INVITE".equals(controlType))
            {
                String testCaseName = message.getStringProperty("TEST_NAME");

                // Flag used to indicate that an enlist should be sent. Only enlist to compulsory invites or invites
                // for which test cases exist.
                boolean enlist = false;

                if (testCaseName != null)
                {
                    // Check if the requested test case is available.
                    InteropClientTestCase testCase = testCases.get(testCaseName);

                    if (testCase != null)
                    {
                        // Make the requested test case the current test case.
                        currentTestCase = testCase;
                        enlist = true;
                    }
                }
                else
                {
                    enlist = true;
                }

                if (enlist)
                {
                    // Reply with the client name in an Enlist message.
                    Message enlistMessage = session.createMessage();
                    enlistMessage.setStringProperty("CONTROL_TYPE", "ENLIST");
                    enlistMessage.setStringProperty("CLIENT_NAME", CLIENT_NAME);
                    enlistMessage.setStringProperty("CLIENT_PRIVATE_CONTROL_KEY", "iop.control." + CLIENT_NAME);
                    enlistMessage.setJMSCorrelationID(message.getJMSCorrelationID());

                    producer.send(message.getJMSReplyTo(), enlistMessage);
                }
            }
            else if ("ASSIGN_ROLE".equals(controlType))
            {
                // Assign the role to the current test case.
                String roleName = message.getStringProperty("");
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
                    // Start the current test case.
                    currentTestCase.start();
                }

                // Generate the report from the test case and reply with it as a Report message.
                Message reportMessage = currentTestCase.getReport(session);
                reportMessage.setStringProperty("CONTROL_TYPE", "REPORT");
                reportMessage.setJMSCorrelationID(message.getJMSCorrelationID());

                producer.send(message.getJMSReplyTo(), reportMessage);
            }
            else
            {
                // Log a warning about this but otherwise ignore it.
                log.warn("Got an unknown control message: " + message);
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
