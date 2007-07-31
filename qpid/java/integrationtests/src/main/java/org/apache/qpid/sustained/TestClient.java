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

import org.apache.log4j.Logger;
import org.apache.qpid.interop.testclient.InteropClientTestCase;
import org.apache.qpid.util.CommandLineParser;

import javax.jms.JMSException;
import javax.jms.Message;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;

public class TestClient extends org.apache.qpid.interop.testclient.TestClient
{
    private static Logger log = Logger.getLogger(TestClient.class);

    /**
     * Creates a new interop test client, listenting to the specified broker and virtual host, with the specified client
     * identifying name.
     *
     * @param brokerUrl   The url of the broker to connect to.
     * @param virtualHost The virtual host to conect to.
     * @param clientName  The client name to use.
     */
    public TestClient(String brokerUrl, String virtualHost, String clientName)
    {
        super(brokerUrl, virtualHost, clientName);
    }

    /**
     * The entry point for the interop test coordinator. This client accepts the following command line arguments:
     *
     * <p/><table> <tr><td> -b         <td> The broker URL.       <td> Optional. <tr><td> -h         <td> The virtual
     * host.     <td> Optional. <tr><td> -n         <td> The test client name. <td> Optional. <tr><td> name=value <td>
     * Trailing argument define name/value pairs. Added to system properties. <td> Optional. </table>
     *
     * @param args The command line arguments.
     */
    public static void main(String[] args)
    {
        // Use the command line parser to evaluate the command line.
        CommandLineParser commandLine =
                new CommandLineParser(
                        new String[][]
                                {
                                        {"b", "The broker URL.", "broker", "false"},
                                        {"h", "The virtual host to use.", "virtual host", "false"},
                                        {"n", "The test client name.", "name", "false"},
                                        {"j", "Join this test client to running test.", "join", ""}
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
        String clientName = options.getProperty("n");
        String join = options.getProperty("j");

        // Add all the trailing command line options (name=value pairs) to system properties. Tests may pick up
        // overridden values from there.
        commandLine.addCommandLineToSysProperties();

        // Create a test client and start it running.
        TestClient client = new TestClient(brokerUrl, virtualHost, (clientName == null) ? CLIENT_NAME : clientName);

        // Use a class path scanner to find all the interop test case implementations.
        Collection<Class<? extends InteropClientTestCase>> testCaseClasses =
                new ArrayList<Class<? extends InteropClientTestCase>>();
        // ClasspathScanner.getMatches(InteropClientTestCase.class, "^TestCase.*", true);
        // Hard code the test classes till the classpath scanner is fixed.
        Collections.addAll(testCaseClasses,
                           SustainedTestClient.class);


        try
        {
            client.start(testCaseClasses, join);
        }
        catch (Exception e)
        {
            log.error("The test client was unable to start.", e);
            System.exit(1);
        }
    }

    protected void start(Collection<Class<? extends InteropClientTestCase>> testCaseClasses, String join) throws JMSException, ClassNotFoundException
    {
        super.start(testCaseClasses);
        log.debug("private void start(): called");

        if (join != null && !join.equals(""))
        {
            Message latejoin = session.createMessage();

            try
            {
                Object test = Class.forName(join).newInstance();
                if (test instanceof InteropClientTestCase)
                {
                    currentTestCase = (InteropClientTestCase) test;
                }
                else
                {
                    throw new RuntimeException("Requested to join class '" + join + "' but this is not a InteropClientTestCase.");
                }

                latejoin.setStringProperty("CONTROL_TYPE", "LATEJOIN");
                latejoin.setStringProperty("CLIENT_NAME", clientName);
                latejoin.setStringProperty("CLIENT_PRIVATE_CONTROL_KEY", "iop.control." + clientName);
                producer.send(session.createTopic("iop.control.test." + currentTestCase.getName()), latejoin);
            }
            catch (InstantiationException e)
            {
                log.warn("Unable to request latejoining of test:" + currentTestCase);
            }
            catch (IllegalAccessException e)
            {
                log.warn("Unable to request latejoining of test:" + currentTestCase);
            }
        }
    }

}
