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
package org.apache.qpid.server;

import java.io.File;
import java.util.List;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Session;

import junit.framework.AssertionFailedError;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.server.logging.AbstractTestLogging;
import org.apache.qpid.util.LogMonitor;

/**
 * Series of tests to validate the external Java broker starts up as expected.
 */
public class BrokerStartupTest extends AbstractTestLogging
{
    public void setUp() throws Exception
    {
        // We either do this here or have a null check in tearDown.
        // As when this test is run against profiles other than java it will NPE
        _monitor = new LogMonitor(_outputFile);
        //We explicitly do not call super.setUp as starting up the broker is
        //part of the test case.
    }

    /**
     * This test simply tests that the broker will startup even if there is no config file (i.e. that it can use the
     * currently packaged initial config file (all system tests by default generate their own config file).
     *
     *
     * @throws Exception
     */
    public void testStartupWithNoConfig() throws Exception
    {
        if (isJavaBroker())
        {
            int port = getPort(0);
            int managementPort = getManagementPort(port);
            int connectorServerPort = managementPort + JMXPORT_CONNECTORSERVER_OFFSET;

            setTestSystemProperty("qpid.amqp_port",String.valueOf(port));
            setTestSystemProperty("qpid.jmx_port",String.valueOf(managementPort));
            setTestSystemProperty("qpid.rmi_port",String.valueOf(connectorServerPort));
            setTestSystemProperty("qpid.http_port",String.valueOf(DEFAULT_HTTP_MANAGEMENT_PORT));

            File brokerConfigFile = new File(getTestConfigFile(port));
            if (brokerConfigFile.exists())
            {
                // Config exists from previous test run, delete it.
                brokerConfigFile.delete();
            }

            startBroker(port, null);

            AMQConnectionURL url = new AMQConnectionURL(String.format("amqp://"
                                                                      + GUEST_USERNAME
                                                                      + ":"
                                                                      + GUEST_PASSWORD
                                                                      + "@clientid/?brokerlist='localhost:%d'", port));
            Connection conn = getConnection(url);
            assertNotNull(conn);
            conn.close();
        }
    }
    /**
     * Description:
     * Test that providing an invalid broker logging configuration file does not
     * cause the broker to enable DEBUG logging that will seriously impair
     * performance
     * Input:
     * -l value that does not exist
     * <p/>
     * Output:
     * <p/>
     * No DEBUG output
     * <p/>
     * Validation Steps:
     * <p/>
     * 1. Start the broker and verify no DEBUG output exists
     *
     * @throws Exception caused by broker startup
     */
    public void testInvalidLog4jConfigurationFile() throws Exception
    {
        // This logging startup code only occurs when you run a Java broker,
        // that broker must be started via Main so not an InVM broker.
        if (isJavaBroker() && isExternalBroker() && !isInternalBroker())
        {
            //Remove test Log4j config from the commandline
            setBrokerCommandLog4JFile(new File("invalid file"));

            // The  broker has a built in default log4j configuration set up
            // so if the the broker cannot load the -l value it will use default
            // use this default. Test that this is correctly loaded, by
            // including -Dlog4j.debug so we can validate.
            setBrokerEnvironment("QPID_OPTS", "-Dlog4j.debug");

            // Disable all client logging so we can test for broker DEBUG only.
            setLoggerLevel(Logger.getRootLogger(), Level.WARN);
            setLoggerLevel(Logger.getLogger("qpid.protocol"), Level.WARN);
            setLoggerLevel(Logger.getLogger("org.apache.qpid"), Level.WARN);

            // Set the broker to use info level logging, which is the qpid-server
            // default. Rather than debug which is the test default.
            setBrokerOnlySystemProperty("amqj.server.logging.level", "info");
            // Set the logging defaults to info for this test.
            setBrokerOnlySystemProperty("amqj.logging.level", "info");
            setBrokerOnlySystemProperty("root.logging.level", "info");

            startBroker();

            assertEquals("Log4j could not load desired configruation.",
                         0, findMatches("log4j:ERROR Could not read configuration file from URL").size());

            assertEquals("Logging did not error as expected",
                         1, waitAndFindMatches("Logging configuration error: unable to read file ").size());


            // Perfom some action on the broker to ensure that we hit the DEBUG
            // messages that we know are there. Though the current xml parsing
            // will generate a LOT of DEBUG on startup.
            Connection connection = getConnection();

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Queue queue = session.createQueue(getTestQueueName());
            session.createConsumer(queue).close();

            int COUNT = 10;
            sendMessage(session, queue, COUNT);

            assertEquals(COUNT,drainQueue(queue));

            List<String> results = waitAndFindMatches("DEBUG");
            try
            {
                // Validation

                assertEquals("DEBUG messages should not be logged", 0, results.size());
            }
            catch (AssertionFailedError afe)
            {
                System.err.println("Log Dump:");
                for (String log : results)
                {
                    System.err.println(log);
                }

                if (results.size() == 0)
                {
                    System.err.println("Monitored file contents:");
                    System.err.println(_monitor.readFile());
                }

                throw afe;
            }
        }
    }

    public void testStartupWithErroredChildrenCanBeConfigured() throws Exception
    {
        if (isJavaBroker())
        {
            int port = getPort(0);
            int managementPort = getManagementPort(port);
            int connectorServerPort = managementPort + JMXPORT_CONNECTORSERVER_OFFSET;

            setTestSystemProperty("qpid.amqp_port",String.valueOf(port));
            setTestSystemProperty("qpid.jmx_port",String.valueOf(managementPort));
            setTestSystemProperty("qpid.rmi_port",String.valueOf(connectorServerPort));

            //Purposely set the HTTP port to be the same as the AMQP port so that the port object becomes ERRORED
            setTestSystemProperty("qpid.http_port",String.valueOf(port));

            // Set broker to fail on startup if it has ERRORED children
            setTestSystemProperty("broker.failStartupWithErroredChild", String.valueOf(Boolean.TRUE));

            File brokerConfigFile = new File(getTestConfigFile(port));
            if (brokerConfigFile.exists())
            {
                // Config exists from previous test run, delete it.
                brokerConfigFile.delete();
            }

            startBroker(port, null);

            AMQConnectionURL url = new AMQConnectionURL(String.format("amqp://"
                    + GUEST_USERNAME
                    + ":"
                    + GUEST_PASSWORD
                    + "@clientid/?brokerlist='localhost:%d'", port));

            try
            {
                Connection conn = getConnection(url);
                fail("Connection should fail as broker startup should have failed due to ERRORED children (port)");
                conn.close();
            }
            catch (JMSException jmse)
            {
                //pass
            }
        }
    }

}
