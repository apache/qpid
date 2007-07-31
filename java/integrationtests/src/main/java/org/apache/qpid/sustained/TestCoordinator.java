/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.sustained;

import org.apache.qpid.interop.coordinator.Coordinator;
import org.apache.qpid.interop.coordinator.ListeningTestDecorator;
import org.apache.qpid.interop.coordinator.TestClientDetails;
import org.apache.qpid.util.CommandLineParser;
import org.apache.qpid.util.ConversationFactory;
import org.apache.log4j.Logger;

import java.util.Properties;
import java.util.Set;

import junit.framework.TestResult;
import uk.co.thebadgerset.junit.extensions.WrappedSuiteTestDecorator;

import javax.jms.Connection;

public class TestCoordinator extends Coordinator
{

    private static final Logger log = Logger.getLogger(TestCoordinator.class);

    /**
     * Creates an interop test coordinator on the specified broker and virtual host.
     *
     * @param brokerUrl   The URL of the broker to connect to.
     * @param virtualHost The virtual host to run all tests on. Optional, may be <tt>null</tt>.
     */
    TestCoordinator(String brokerUrl, String virtualHost)
    {
        super(brokerUrl, virtualHost);
    }

    protected WrappedSuiteTestDecorator newTestDecorator(WrappedSuiteTestDecorator targetTest, Set<TestClientDetails> enlistedClients, ConversationFactory conversationFactory, Connection connection)
    {
        return  new ListeningTestDecorator(targetTest, enlistedClients, conversationFactory, connection);
    }


    /**
     * The entry point for the interop test coordinator. This client accepts the following command line arguments:
     *
     * <p/><table> <tr><td> -b         <td> The broker URL.   <td> Mandatory. <tr><td> -h         <td> The virtual host.
     * <td> Optional. <tr><td> name=value <td> Trailing argument define name/value pairs. Added to system properties.
     * <td> Optional. </table>
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
                                                                                 {"b", "The broker URL.", "broker", "false"},
                                                                                 {"h", "The virtual host to use.", "virtual host", "false"},
                                                                                 {"o", "The name of the directory to output test timings to.", "dir", "false"}
                                                                         }));

            // Extract the command line options.
            String brokerUrl = options.getProperty("b");
            String virtualHost = options.getProperty("h");
            String reportDir = options.getProperty("o");
            reportDir = (reportDir == null) ? "." : reportDir;


            String[] testClassNames = {SustainedTestCoordinator.class.getName()};

            // Create a coordinator and begin its test procedure.
            Coordinator coordinator = new TestCoordinator(brokerUrl, virtualHost);

            coordinator.setReportDir(reportDir);

            TestResult testResult = coordinator.start(testClassNames);

            if (testResult.failureCount() > 0)
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
}
