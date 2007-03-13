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

import java.util.Properties;

import javax.jms.Message;
import javax.jms.MessageListener;

import org.apache.qpid.util.CommandLineParser;

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
    /** Holds the URL of the broker to run the tests on. */
    String brokerUrl;

    /** Holds the virtual host to run the tests on. If <tt>null</tt>, then the default virtual host is used. */
    String virtualHost;

    /** Defines an enumeration of the control message types and handling behaviour for each. */
    protected enum ControlMessages implements MessageListener
    {
        INVITE_COMPULSORY
        {
            public void onMessage(Message message)
            {
                // Reply with the client name in an Enlist message.
            }
        },
        INVITE
        {
            public void onMessage(Message message)
            {
                // Extract the test properties.

                // Check if the requested test case is available.
                {
                    // Make the requested test case the current test case.

                    // Reply by accepting the invite in an Enlist message.
                }
            }
        },
        ASSIGN_ROLE
        {
            public void onMessage(Message message)
            {
                // Extract the test properties.

                // Reply by accepting the role in an Accept Role message.
            }
        },
        START
        {
            public void onMessage(Message message)
            {
                // Start the current test case.

                // Generate the report from the test case and reply with it as a Report message.
            }
        },
        STATUS_REQUEST
        {
            public void onMessage(Message message)
            {
                // Generate the report from the test case and reply with it as a Report message.
            }
        },
        UNKNOWN
        {
            public void onMessage(Message message)
            {
                // Log a warning about this but otherwise ignore it.
            }
        };

        /**
         * Handles control messages appropriately depending on the message type.
         *
         * @param message The incoming message to handle.
         */
        public abstract void onMessage(Message message);
    }

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
        client.start();
    }

    private void start()
    {
        // Use a class path scanner to find all the interop test case implementations.

        // Create all the test case implementations and index them by the test names.

        // Open a connection to communicate with the coordinator on.

        // Set this up to listen for control messages.

        // Create a producer to send replies with.
    }

    /**
     * Handles all incoming control messages.
     *
     * @param message The incoming message.
     */
    public void onMessage(Message message)
    {
        // Delegate the message handling to the message type specific handler.
        extractMessageType(message).onMessage(message);
    }

    /**
     * Determines the control messsage type of incoming messages.
     *
     * @param message The message to determine the type of.
     *
     * @return The control message type of the message.
     */
    protected ControlMessages extractMessageType(Message message)
    {
        return null;
    }
}
