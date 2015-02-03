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
package org.apache.qpid.systest.management.jmx;


import java.util.Collections;
import java.util.List;

import org.apache.qpid.server.logging.AbstractTestLogging;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.util.LogMonitor;

/**
 * Management Console Test Suite
 *
 * The Management Console test suite validates that the follow log messages as specified in the Functional Specification.
 *
 * This suite of tests validate that the management console messages occur correctly and according to the following format:
 *
 * MNG-1001 : <type> Management Startup
 * MNG-1002 : Starting : <service> : Listening on port <Port>
 * MNG-1003 : Shutting down : <service> : port <Port>
 * MNG-1004 : <type> Management Ready
 * MNG-1005 : <type> Management Stopped
 * MNG-1006 : Using SSL Keystore : <path>
 * MNG-1007 : Open : User <username>
 * MNG-1008 : Close : User <username>
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
        // This test only works on java brokers
        if (isJavaBroker())
        {
            startBrokerAndCreateMonitor(true, false);

            // Ensure we have received the MNG log msg.
            waitForMessage("MNG-1001");

            List<String> results = findMatches(MNG_PREFIX);
            // Validation

            assertTrue("MNGer message not logged", results.size() > 0);

            String log = getLogMessage(results, 0);

            //1
            validateMessageID("MNG-1001", log);

            //2
            //There will be 2 copies of the startup message (one via SystemOut, and one via Log4J)
            results = findMatches("MNG-1001");
            assertEquals("Unexpected startup message count.",
                         1, results.size());

            //3
            assertEquals("Startup log message is not 'Startup'.", "JMX Management Startup",
                         getMessageString(log));
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
        if (isJavaBroker())
        {
            startBrokerAndCreateMonitor(false, false);

            List<String> results = findMatches(MNG_PREFIX);
            // Validation

            assertEquals("MNGer messages logged", 0, results.size());
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
        if (isJavaBroker())
        {
            startBrokerAndCreateMonitor(true, false);

            List<String> results = waitAndFindMatches("MNG-1002");
            // Validation

            //There will be 4 startup messages (two via SystemOut, and two via Log4J)
            assertEquals("Unexpected MNG-1002 message count", 2, results.size());

            String log = getLogMessage(results, 0);

            //1
            validateMessageID("MNG-1002", log);

            //Check the RMI Registry port is as expected
            int mPort = getManagementPort(getPort());
            assertTrue("RMI Registry port not as expected(" + mPort + ").:" + getMessageString(log),
                       getMessageString(log).endsWith(String.valueOf(mPort)));

            log = getLogMessage(results, 1);

            //1
            validateMessageID("MNG-1002", log);

            // We expect the RMI Registry port (the defined 'management port') to be
            // 100 lower than the JMX RMIConnector Server Port (the actual JMX server)
            int jmxPort = mPort + JMXPORT_CONNECTORSERVER_OFFSET;
            assertTrue("JMX RMIConnectorServer port not as expected(" + jmxPort + ").:" + getMessageString(log),
                       getMessageString(log).endsWith(String.valueOf(jmxPort)));
        }
    }

    /**
     * Description: Tests the management connection open/close are logged correctly.
     *
     * Output:
     *
     * <date> MESSAGE MNG-1007 : Open : User <username>
     * <date> MESSAGE MNG-1008 : Close : User <username>
     *
     * Validation Steps:
     *
     * 1. The MNG ID is correct
     * 2. The message and username are correct
     */
    public void testManagementUserOpenClose() throws Exception
    {
        if (isJavaBroker())
        {
            startBrokerAndCreateMonitor(true, false);

            final JMXTestUtils jmxUtils = new JMXTestUtils(this);
            List<String> openResults = null;
            List<String> closeResults = null;
            try
            {
                jmxUtils.open();
                openResults = waitAndFindMatches("MNG-1007");
            }
            finally
            {
                if (jmxUtils != null)
                {
                    jmxUtils.close();
                    closeResults = waitAndFindMatches("MNG-1008");
                }
            }

            assertNotNull("Management Open results null", openResults.size());
            assertEquals("Management Open logged unexpected number of times", 1, openResults.size());

            assertNotNull("Management Close results null", closeResults.size());
            assertEquals("Management Close logged unexpected number of times", 1, closeResults.size());

            final String openMessage = getMessageString(getLogMessage(openResults, 0));
            assertTrue("Unexpected open message " + openMessage, openMessage.endsWith("Open : User admin"));
            final String closeMessage = getMessageString(getLogMessage(closeResults, 0));
            assertTrue("Unexpected close message " + closeMessage, closeMessage.endsWith("Close : User admin"));
        }
    }

    private void startBrokerAndCreateMonitor(boolean managementEnabled, boolean useManagementSSL) throws Exception
    {
        TestBrokerConfiguration config = getBrokerConfiguration();

        if (managementEnabled)
        {
            config.addJmxManagementConfiguration();
        }

        if(useManagementSSL)
        {
            // This test requires we have ssl, change the transport and add they keystore to the port config
            config.setObjectAttribute(Port.class, TestBrokerConfiguration.ENTRY_NAME_JMX_PORT, Port.TRANSPORTS, Collections.singleton(Transport.SSL));
            config.setObjectAttribute(Port.class, TestBrokerConfiguration.ENTRY_NAME_JMX_PORT, Port.KEY_STORE, TestBrokerConfiguration.ENTRY_NAME_SSL_KEYSTORE);
        }

        startBroker();

        // Now we can create the monitor as _outputFile will now be defined
        _monitor = new LogMonitor(_outputFile);
    }
}
