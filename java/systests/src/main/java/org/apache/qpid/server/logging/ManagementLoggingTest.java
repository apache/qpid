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
package org.apache.qpid.server.logging;

import junit.framework.AssertionFailedError;

import org.apache.qpid.util.LogMonitor;

import java.util.List;
import java.io.File;

/**
 * Management Console Test Suite
 *
 * The Management Console test suite validates that the follow log messages as specified in the Functional Specification.
 *
 * This suite of tests validate that the management console messages occur correctly and according to the following format:
 *
 * MNG-1001 : Startup
 * MNG-1002 : Starting : <service> : Listening on port <Port>
 * MNG-1003 : Shutting down : <service> : port <Port>
 * MNG-1004 : Ready
 * MNG-1005 : Stopped
 * MNG-1006 : Using SSL Keystore : <path>
 */
public class ManagementLoggingTest extends AbstractTestLogging
{
    private static final String MNG_PREFIX = "MNG-";

    public void setUp() throws Exception
    {
        setLogMessagePrefix();

        // We either do this here or have a null check in tearDown.
        // As when this test is run against profiles other than java it will NPE
        _monitor = new LogMonitor(_outputFile);
        //We explicitly do not call super.setUp as starting up the broker is
        //part of the test case.

    }

    /**
     * Description:
     * Using the startup configuration validate that the management startup
     * message is logged correctly.
     * Input:
     * Standard configuration with management enabled
     * Output:
     *
     * <date> MNG-1001 : Startup
     *
     * Constraints:
     * This is the FIRST message logged by MNG
     * Validation Steps:
     *
     * 1. The BRK ID is correct
     * 2. This is the FIRST message logged by MNG
     */
    public void testManagementStartupEnabled() throws Exception
    {
        // This test only works on external java brokers due to the fact that
        // Management is disabled on InVM brokers.
        if (isJavaBroker() && isExternalBroker())
        {
            startBrokerAndCreateMonitor(true, false);

            // Ensure we have received the MNG log msg.
            waitForMessage("MNG-1001");

            List<String> results = findMatches(MNG_PREFIX);
            
            try
            {
                // Validation

                assertTrue("MNGer message not logged", results.size() > 0);

                String log = getLogMessage(results, 0);

                //1
                validateMessageID("MNG-1001", log);

                //2
                //There will be 2 copies of the startup message (one via SystemOut, and one via Log4J)
                results = findMatches("MNG-1001");
                assertEquals("Unexpected startup message count.",
                             2, results.size());

                //3
                assertEquals("Startup log message is not 'Startup'.", "Startup",
                             getMessageString(log));
            }
            catch (AssertionFailedError afe)
            {
                dumpLogs(results, _monitor);
                throw afe;
            }
        }
    }

    /**
     * Description:
     * Verify that when management is disabled in the configuration file the
     * startup message is not logged.
     * Input:
     * Standard configuration with management disabled
     * Output:
     * NO MNG messages
     * Validation Steps:
     *
     * 1. Validate that no MNG messages are produced.
     */
    public void testManagementStartupDisabled() throws Exception
    {
        // This test only works on external java brokers due to the fact that
        // Management is disabled on InVM brokers.
        if (isJavaBroker() && isExternalBroker())
        {
            startBrokerAndCreateMonitor(false, false);

            List<String> results = findMatches(MNG_PREFIX);
            try
            {
                // Validation

                assertEquals("MNGer messages logged", 0, results.size());
            }
            catch (AssertionFailedError afe)
            {
                dumpLogs(results, _monitor);
                throw afe;
            }
        }
    }

    /**
     * The two MNG-1002 messages are logged at the same time so lets test them
     * at the same time.
     *
     * Description:
     * Using the default configuration validate that the RMI Registry socket is
     * correctly reported as being opened
     *
     * Input:
     * The default configuration file
     * Output:
     *
     * <date> MESSAGE MNG-1002 : Starting : RMI Registry : Listening on port 8999
     *
     * Constraints:
     * The RMI ConnectorServer and Registry log messages do not have a prescribed order
     * Validation Steps:
     *
     * 1. The MNG ID is correct
     * 2. The specified port is the correct '8999'
     *
     * Description:
     * Using the default configuration validate that the RMI ConnectorServer
     * socket is correctly reported as being opened
     *
     * Input:
     * The default configuration file
     * Output:
     *
     * <date> MESSAGE MNG-1002 : Starting : RMI ConnectorServer : Listening on port 9099
     *
     * Constraints:
     * The RMI ConnectorServer and Registry log messages do not have a prescribed order
     * Validation Steps:
     *
     * 1. The MNG ID is correct
     * 2. The specified port is the correct '9099'
     */
    public void testManagementStartupRMIEntries() throws Exception
    {
        // This test only works on external java brokers due to the fact that
        // Management is disabled on InVM brokers.
        if (isJavaBroker() && isExternalBroker())
        {
            startBrokerAndCreateMonitor(true, false);
            
            List<String> results = waitAndFindMatches("MNG-1002");
            try
            {
                // Validation

                //There will be 4 startup messages (two via SystemOut, and two via Log4J)
                assertEquals("Unexpected MNG-1002 message count", 4, results.size());

                String log = getLogMessage(results, 0);

                //1
                validateMessageID("MNG-1002", log);

                //Check the RMI Registry port is as expected
                int mPort = getPort() + (DEFAULT_MANAGEMENT_PORT - DEFAULT_PORT);
                assertTrue("RMI Registry port not as expected(" + mPort + ").:" + getMessageString(log),
                           getMessageString(log).endsWith(String.valueOf(mPort)));

                log = getLogMessage(results, 2);

                //1
                validateMessageID("MNG-1002", log);

                // We expect the RMI Registry port (the defined 'management port') to be
                // 100 lower than the JMX RMIConnector Server Port (the actual JMX server)
                int jmxPort = mPort + 100;
                assertTrue("JMX RMIConnectorServer port not as expected(" + jmxPort + ").:" + getMessageString(log),
                           getMessageString(log).endsWith(String.valueOf(jmxPort)));
            }
            catch (AssertionFailedError afe)
            {
                dumpLogs(results, _monitor);
                throw afe;
            }
        }
    }

    /**
     * Description:
     * Using the default configuration with SSL enabled for the management port the SSL Keystore path should be reported via MNG-1006
     * Input:
     * Management SSL enabled default configuration.
     * Output:
     *
     * <date> MESSAGE MNG-1006 : Using SSL Keystore : test_resources/ssl/keystore.jks
     *
     * Validation Steps:
     *
     * 1. The MNG ID is correct
     * 2. The keystore path is as specified in the configuration
     */
    public void testManagementStartupSSLKeystore() throws Exception
    {
        // This test only works on external java brokers due to the fact that
        // Management is disabled on InVM brokers.
        if (isJavaBroker() && isExternalBroker())
        {
            startBrokerAndCreateMonitor(true, true);

            List<String> results = waitAndFindMatches("MNG-1006");
            try
            {
                // Validation

                assertTrue("MNGer message not logged", results.size() > 0);

                String log = getLogMessage(results, 0);

                //1
                validateMessageID("MNG-1006", log);

                // Validate we only have two MNG-1002 (one via stdout, one via log4j)
                results = findMatches("MNG-1006");
                assertEquals("Upexpected SSL Keystore message count",
                             2, results.size());

                // Validate the keystore path is as expected
                assertTrue("SSL Keystore entry expected.:" + getMessageString(log),
                           getMessageString(log).endsWith(new File(getConfigurationStringProperty("management.ssl.keyStorePath")).getName()));
            }
            catch (AssertionFailedError afe)
            {
                dumpLogs(results, _monitor);
                throw afe;
            }
        }

    }
    
    private void startBrokerAndCreateMonitor(boolean managementEnabled, boolean useManagementSSL) throws Exception
    {
        //Ensure management is on
        setConfigurationProperty("management.enabled", String.valueOf(managementEnabled));

        if(useManagementSSL)
        {
            // This test requires we have an ssl connection
            setConfigurationProperty("management.ssl.enabled", "true");
        }

        startBroker();

        // Now we can create the monitor as _outputFile will now be defined
        _monitor = new LogMonitor(_outputFile);
    }
}
