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
package org.apache.qpid.ping;

import javax.jms.*;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.log4j.Logger;

import org.apache.qpid.requestreply.PingPongProducer;

import uk.co.thebadgerset.junit.extensions.AsymptoticTestCase;
import uk.co.thebadgerset.junit.extensions.TestThreadAware;
import uk.co.thebadgerset.junit.extensions.util.ParsedProperties;
import uk.co.thebadgerset.junit.extensions.util.TestContextProperties;

/**
 * PingTestPerf is a ping test, that has been written with the intention of being scaled up to run many times
 * simultaneously to simluate many clients/producers/connections.
 *
 * <p/>A single run of the test using the default JUnit test runner will result in the sending and timing of a single
 * full round trip ping. This test may be scaled up using a suitable JUnit test runner.
 *
 * <p/>The setup/teardown cycle establishes a connection to a broker and sets up a queue to send ping messages to and a
 * temporary queue for replies. This setup is only established once for all the test repeats/threads that may be run,
 * except if the connection is lost in which case an attempt to re-establish the setup is made.
 *
 * <p/>The test cycle is: Connects to a queue, creates a temporary queue, creates messages containing a property that
 * is the name of the temporary queue, fires off a message on the original queue and waits for a response on the
 * temporary queue.
 *
 * <p/>Configurable test properties: message size, transacted or not, persistent or not. Broker connection details.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * </table>
 *
 * @author Rupert Smith
 */
public class PingTestPerf extends AsymptoticTestCase implements TestThreadAware
{
    private static Logger _logger = Logger.getLogger(PingTestPerf.class);

    /** Thread local to hold the per-thread test setup fields. */
    ThreadLocal<PerThreadSetup> threadSetup = new ThreadLocal<PerThreadSetup>();

    /** Holds a property reader to extract the test parameters from. */
    protected ParsedProperties testParameters = new ParsedProperties(System.getProperties());

    public PingTestPerf(String name)
    {
        super(name);

        // Sets up the test parameters with defaults.
        testParameters.setPropertyIfNull(PingPongProducer.COMMIT_BATCH_SIZE_PROPNAME,
                                         Integer.toString(PingPongProducer.DEFAULT_TX_BATCH_SIZE));
        testParameters.setPropertyIfNull(PingPongProducer.MESSAGE_SIZE_PROPNAME,
                                         Integer.toString(PingPongProducer.DEFAULT_MESSAGE_SIZE));
        testParameters.setPropertyIfNull(PingPongProducer.PING_QUEUE_NAME_PROPNAME,
                                         PingPongProducer.DEFAULT_PING_DESTINATION_NAME);
        testParameters.setPropertyIfNull(PingPongProducer.PERSISTENT_MODE_PROPNAME,
                                         Boolean.toString(PingPongProducer.DEFAULT_PERSISTENT_MODE));
        testParameters.setPropertyIfNull(PingPongProducer.TRANSACTED_PROPNAME,
                                         Boolean.toString(PingPongProducer.DEFAULT_TRANSACTED));
        testParameters.setPropertyIfNull(PingPongProducer.BROKER_PROPNAME, PingPongProducer.DEFAULT_BROKER);
        testParameters.setPropertyIfNull(PingPongProducer.USERNAME_PROPNAME, PingPongProducer.DEFAULT_USERNAME);
        testParameters.setPropertyIfNull(PingPongProducer.PASSWORD_PROPNAME, PingPongProducer.DEFAULT_PASSWORD);
        testParameters.setPropertyIfNull(PingPongProducer.VIRTUAL_PATH_PROPNAME, PingPongProducer.DEFAULT_VIRTUAL_PATH);
        testParameters.setPropertyIfNull(PingPongProducer.VERBOSE_OUTPUT_PROPNAME,
                                         Boolean.toString(PingPongProducer.DEFAULT_VERBOSE));
        testParameters.setPropertyIfNull(PingPongProducer.RATE_PROPNAME, Integer.toString(PingPongProducer.DEFAULT_RATE));
        testParameters.setPropertyIfNull(PingPongProducer.IS_PUBSUB_PROPNAME,
                                         Boolean.toString(PingPongProducer.DEFAULT_PUBSUB));
        testParameters.setPropertyIfNull(PingPongProducer.COMMIT_BATCH_SIZE_PROPNAME,
                                         Integer.toString(PingPongProducer.DEFAULT_TX_BATCH_SIZE));
        testParameters.setPropertyIfNull(PingPongProducer.TIMEOUT_PROPNAME, Long.toString(PingPongProducer.DEFAULT_TIMEOUT));
        testParameters.setPropertyIfNull(PingPongProducer.PING_DESTINATION_COUNT_PROPNAME,
                                         Integer.toString(PingPongProducer.DEFAULT_DESTINATION_COUNT));
        testParameters.setPropertyIfNull(PingPongProducer.FAIL_AFTER_COMMIT_PROPNAME,
                                         PingPongProducer.DEFAULT_FAIL_AFTER_COMMIT);
        testParameters.setPropertyIfNull(PingPongProducer.FAIL_BEFORE_COMMIT_PROPNAME,
                                         PingPongProducer.DEFAULT_FAIL_BEFORE_COMMIT);
        testParameters.setPropertyIfNull(PingPongProducer.FAIL_AFTER_SEND_PROPNAME,
                                         PingPongProducer.DEFAULT_FAIL_AFTER_SEND);
        testParameters.setPropertyIfNull(PingPongProducer.FAIL_BEFORE_SEND_PROPNAME,
                                         PingPongProducer.DEFAULT_FAIL_BEFORE_SEND);
        testParameters.setPropertyIfNull(PingPongProducer.FAIL_ONCE_PROPNAME, PingPongProducer.DEFAULT_FAIL_ONCE);
        testParameters.setPropertyIfNull(PingPongProducer.UNIQUE_PROPNAME, PingPongProducer.DEFAULT_UNIQUE);
        testParameters.setSysPropertyIfNull(PingPongProducer.ACK_MODE_PROPNAME,
                                              Integer.toString(PingPongProducer.DEFAULT_ACK_MODE));
        testParameters.setSysPropertyIfNull(PingPongProducer.PAUSE_AFTER_BATCH_PROPNAME, 0l);
    }

    /**
     * Compile all the tests into a test suite.
     * @return The test method testPingOk.
     */
    public static Test suite()
    {
        // Build a new test suite
        TestSuite suite = new TestSuite("Ping Performance Tests");

        // Run performance tests in read committed mode.
        suite.addTest(new PingTestPerf("testPingOk"));

        return suite;
    }

    public void testPingOk(int numPings) throws Exception
    {
        if (numPings == 0)
        {
            Assert.fail("Number of pings requested was zero.");
        }

        // Get the per thread test setup to run the test through.
        PerThreadSetup perThreadSetup = threadSetup.get();

        if (perThreadSetup == null)
        {
            Assert.fail("Could not get per thread test setup, it was null.");
        }

        // Generate a sample message. This message is already time stamped and has its reply-to destination set.
        ObjectMessage msg =
                perThreadSetup._pingClient.getTestMessage(perThreadSetup._pingClient.getReplyDestinations().get(0),
                                                          testParameters.getPropertyAsInteger(
                                                                  PingPongProducer.MESSAGE_SIZE_PROPNAME),
                                                          testParameters.getPropertyAsBoolean(
                                                                  PingPongProducer.PERSISTENT_MODE_PROPNAME));

        // start the test
        long timeout = Long.parseLong(testParameters.getProperty(PingPongProducer.TIMEOUT_PROPNAME));
        int numReplies = perThreadSetup._pingClient.pingAndWaitForReply(msg, numPings, timeout);

        // Fail the test if the timeout was exceeded.
        if (numReplies != perThreadSetup._pingClient.getExpectedNumPings(numPings))
        {
            Assert.fail("The ping timed out after " + timeout + " ms. Messages Sent = " + numPings + ", MessagesReceived = "
                        + numReplies);
        }
    }

    /**
     * Performs test fixture creation on a per thread basis. This will only be called once for each test thread.
     */
    public void threadSetUp()
    {
        _logger.debug("public void threadSetUp(): called");

        try
        {
            PerThreadSetup perThreadSetup = new PerThreadSetup();

            // Extract the test set up paramaeters.
            String brokerDetails = testParameters.getProperty(PingPongProducer.BROKER_PROPNAME);
            String username = testParameters.getProperty(PingPongProducer.USERNAME_PROPNAME);
            String password = testParameters.getProperty(PingPongProducer.PASSWORD_PROPNAME);
            String virtualPath = testParameters.getProperty(PingPongProducer.VIRTUAL_PATH_PROPNAME);
            String destinationName = testParameters.getProperty(PingPongProducer.PING_QUEUE_NAME_PROPNAME);
            boolean persistent = testParameters.getPropertyAsBoolean(PingPongProducer.PERSISTENT_MODE_PROPNAME);
            boolean transacted = testParameters.getPropertyAsBoolean(PingPongProducer.TRANSACTED_PROPNAME);
            String selector = testParameters.getProperty(PingPongProducer.SELECTOR_PROPNAME);
            boolean verbose = testParameters.getPropertyAsBoolean(PingPongProducer.VERBOSE_OUTPUT_PROPNAME);
            int messageSize = testParameters.getPropertyAsInteger(PingPongProducer.MESSAGE_SIZE_PROPNAME);
            int rate = testParameters.getPropertyAsInteger(PingPongProducer.RATE_PROPNAME);
            boolean pubsub = testParameters.getPropertyAsBoolean(PingPongProducer.IS_PUBSUB_PROPNAME);
            boolean failAfterCommit = testParameters.getPropertyAsBoolean(PingPongProducer.FAIL_AFTER_COMMIT_PROPNAME);
            boolean failBeforeCommit = testParameters.getPropertyAsBoolean(PingPongProducer.FAIL_BEFORE_COMMIT_PROPNAME);
            boolean failAfterSend = testParameters.getPropertyAsBoolean(PingPongProducer.FAIL_AFTER_SEND_PROPNAME);
            boolean failBeforeSend = testParameters.getPropertyAsBoolean(PingPongProducer.FAIL_BEFORE_SEND_PROPNAME);
            int batchSize = testParameters.getPropertyAsInteger(PingPongProducer.COMMIT_BATCH_SIZE_PROPNAME);
            Boolean failOnce = testParameters.getPropertyAsBoolean(PingPongProducer.FAIL_ONCE_PROPNAME);
            boolean unique = testParameters.getPropertyAsBoolean(PingPongProducer.UNIQUE_PROPNAME);
            int ackMode = testParameters.getPropertyAsInteger(PingPongProducer.ACK_MODE_PROPNAME);
            int pausetime = testParameters.getPropertyAsInteger(PingPongProducer.PAUSE_AFTER_BATCH_PROPNAME);

            // Extract the test set up paramaeters.
            int destinationscount =
                    Integer.parseInt(testParameters.getProperty(PingPongProducer.PING_DESTINATION_COUNT_PROPNAME));

            // This is synchronized because there is a race condition, which causes one connection to sleep if
            // all threads try to create connection concurrently.
            synchronized (this)
            {
                // Establish a client to ping a Destination and listen the reply back from same Destination
                perThreadSetup._pingClient = new PingClient(brokerDetails, username, password, virtualPath, destinationName,
                                                            selector, transacted, persistent, messageSize, verbose,
                                                            failAfterCommit, failBeforeCommit, failAfterSend, failBeforeSend,
                                                            failOnce, batchSize, destinationscount, rate, pubsub,
                                                            unique, ackMode, pausetime);
            }
            // Start the client connection
            perThreadSetup._pingClient.getConnection().start();

            // Attach the per-thread set to the thread.
            threadSetup.set(perThreadSetup);
        }
        catch (Exception e)
        {
            _logger.warn("There was an exception during per thread setup.", e);
        }
    }

    /**
     * Performs test fixture clean
     */
    public void threadTearDown()
    {
        _logger.debug("public void threadTearDown(): called");

        try
        {
            // Get the per thread test fixture.
            PerThreadSetup perThreadSetup = threadSetup.get();

            // Close the pingers so that it cleans up its connection cleanly.
            synchronized (this)
            {
                if ((perThreadSetup != null) && (perThreadSetup._pingClient != null))
                {
                    perThreadSetup._pingClient.close();
                }
            }
        }
        catch (JMSException e)
        {
            _logger.warn("There was an exception during per thread tear down.");
        }
        finally
        {
            // Ensure the per thread fixture is reclaimed.
            threadSetup.remove();
        }
    }

    protected static class PerThreadSetup
    {
        /**
         * Holds the test ping client.
         */
        protected PingClient _pingClient;
        protected String _correlationId;
    }
}
