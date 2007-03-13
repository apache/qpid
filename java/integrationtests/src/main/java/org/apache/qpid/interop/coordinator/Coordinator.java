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

import java.util.Properties;

import junit.framework.Test;
import junit.framework.TestResult;

import org.apache.qpid.util.CommandLineParser;

import uk.co.thebadgerset.junit.extensions.TestRunnerImprovedErrorHandling;

/**
 * <p/>Implements the coordinator client described in the interop testing specification
 * (http://cwiki.apache.org/confluence/display/qpid/Interop+Testing+Specification). This coordinator is built on
 * top of the JUnit testing framework.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Find out what test clients are available.
 * <tr><td> Decorate available tests to run all available clients.
 * <tr><td> Attach XML test result logger.
 * <tr><td> Terminate the interop testing framework.
 * </table>
 */
public class Coordinator extends TestRunnerImprovedErrorHandling
{
    /** Holds the URL of the broker to coordinate the tests on. */
    String brokerUrl;

    /** Holds the virtual host to coordinate the tests on. If <tt>null</tt>, then the default virtual host is used. */
    String virtualHost;

    /**
     * Creates an interop test coordinator on the specified broker and virtual host.
     *
     * @param brokerUrl   The URL of the broker to connect to.
     * @param virtualHost The virtual host to run all tests on. Optional, may be <tt>null</tt>.
     */
    Coordinator(String brokerUrl, String virtualHost)
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

        // Scan for available test cases using a classpath scanner.
        String[] testClassNames = null;

        // Create a coordinator and begin its test procedure.
        try
        {
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
            System.exit(EXCEPTION_EXIT);
        }
    }

    public TestResult start(String[] testClassNames) throws Exception
    {
        // Connect to the broker.

        // Broadcast the compulsory invitation to find out what clients are available to test.

        // Wait for a short time, to give test clients an opportunity to reply to the invitation.

        // Retain the list of all available clients.

        // Run all of the tests in the suite using JUnit.
        TestResult result = super.start(testClassNames);

        // At this point in time, all tests have completed. Broadcast the shutdown message.

        return result;
    }

    /**
     * Runs a test or suite of tests, using the super class implemenation. This method wraps the test to be run
     * in any test decorators needed to add in the configured toolkits enhanced junit functionality.
     *
     * @param test The test to run.
     * @param wait Undocumented. Nothing in the JUnit javadocs to say what this is for.
     *
     * @return The results of the test run.
     */
    public TestResult doRun(Test test, boolean wait)
    {
        // Combine together the available test cases and test clients to produce a complete list of test case instances
        // to run as a JUnit test suite.

        return null;
    }
}
