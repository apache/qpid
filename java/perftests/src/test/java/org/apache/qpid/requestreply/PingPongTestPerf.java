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
package org.apache.qpid.requestreply;

import javax.jms.*;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.log4j.Logger;

import uk.co.thebadgerset.junit.extensions.AsymptoticTestCase;
import uk.co.thebadgerset.junit.extensions.util.ParsedProperties;

/**
 * PingPongTestPerf is a full round trip ping test, that has been written with the intention of being scaled up to run
 * many times simultaneously to simluate many clients/producer/connections. A full round trip ping sends a message from
 * a producer to a conumer, then the consumer replies to the message on a temporary queue.
 *
 * <p/>A single run of the test using the default JUnit test runner will result in the sending and timing of the number
 * of pings specified by the test size and time how long it takes for all of these to complete. This test may be scaled
 * up using a suitable JUnit test runner. See {@link uk.co.thebadgerset.junit.extensions.TKTestRunner} for more
 * information on how to do this.
 *
 * <p/>The setup/teardown cycle establishes a connection to a broker and sets up a queue to send ping messages to and a
 * temporary queue for replies. This setup is only established once for all the test repeats, but each test threads
 * gets its own connection/producer/consumer, this is only re-established if the connection is lost.
 *
 * <p/>The test cycle is: Connects to a queue, creates a temporary queue, creates messages containing a property that
 * is the name of the temporary queue, fires off many messages on the original queue and waits for them all to come
 * back on the temporary queue.
 *
 * <p/>Configurable test properties: message size, transacted or not, persistent or not. Broker connection details.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * </table>
 *
 * @author Rupert Smith
 */
public class PingPongTestPerf extends AsymptoticTestCase
{
    private static Logger _logger = Logger.getLogger(PingPongTestPerf.class);

    /** Thread local to hold the per-thread test setup fields. */
    ThreadLocal<PerThreadSetup> threadSetup = new ThreadLocal<PerThreadSetup>();

    // Set up a property reader to extract the test parameters from. Once ContextualProperties is available in
    // the project dependencies, use it to get property overrides for configurable tests and to notify the test runner
    // of the test parameters to log with the results. It also providers some basic type parsing convenience methods.
    //private Properties testParameters = System.getProperties();
    private ParsedProperties testParameters = new ParsedProperties(System.getProperties());

    public PingPongTestPerf(String name)
    {
        super(name);

        // Sets up the test parameters with defaults.
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.COMMIT_BATCH_SIZE_PROPNAME,
                                              Integer.toString(PingPongProducer.DEFAULT_TX_BATCH_SIZE));
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.MESSAGE_SIZE_PROPNAME,
                                              Integer.toString(PingPongProducer.DEFAULT_MESSAGE_SIZE));
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.PING_QUEUE_NAME_PROPNAME,
                                              PingPongProducer.DEFAULT_PING_DESTINATION_NAME);
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.PERSISTENT_MODE_PROPNAME,
                                              Boolean.toString(PingPongProducer.DEFAULT_PERSISTENT_MODE));
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.TRANSACTED_PROPNAME,
                                              Boolean.toString(PingPongProducer.DEFAULT_TRANSACTED));
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.BROKER_PROPNAME, PingPongProducer.DEFAULT_BROKER);
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.USERNAME_PROPNAME, PingPongProducer.DEFAULT_USERNAME);
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.PASSWORD_PROPNAME, PingPongProducer.DEFAULT_PASSWORD);
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.VIRTUAL_PATH_PROPNAME, PingPongProducer.DEFAULT_VIRTUAL_PATH);
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.VERBOSE_OUTPUT_PROPNAME,
                                              Boolean.toString(PingPongProducer.DEFAULT_VERBOSE));
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.RATE_PROPNAME,
                                              Integer.toString(PingPongProducer.DEFAULT_RATE));
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.IS_PUBSUB_PROPNAME,
                                              Boolean.toString(PingPongProducer.DEFAULT_PUBSUB));
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.COMMIT_BATCH_SIZE_PROPNAME,
                                              Integer.toString(PingPongProducer.DEFAULT_TX_BATCH_SIZE));
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.TIMEOUT_PROPNAME,
                                              Long.toString(PingPongProducer.DEFAULT_TIMEOUT));
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.PING_DESTINATION_COUNT_PROPNAME,
                                              Integer.toString(PingPongProducer.DEFAULT_DESTINATION_COUNT));
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.FAIL_AFTER_COMMIT_PROPNAME,
                                              PingPongProducer.DEFAULT_FAIL_AFTER_COMMIT);
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.FAIL_BEFORE_COMMIT_PROPNAME,
                                              PingPongProducer.DEFAULT_FAIL_BEFORE_COMMIT);
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.FAIL_AFTER_SEND_PROPNAME,
                                              PingPongProducer.DEFAULT_FAIL_AFTER_SEND);
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.FAIL_BEFORE_SEND_PROPNAME,
                                              PingPongProducer.DEFAULT_FAIL_BEFORE_SEND);
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.FAIL_ONCE_PROPNAME, PingPongProducer.DEFAULT_FAIL_ONCE);
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.UNIQUE_PROPNAME, Boolean.toString(PingPongProducer.DEFAULT_UNIQUE));
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.ACK_MODE_PROPNAME,
                                              Integer.toString(PingPongProducer.DEFAULT_ACK_MODE));
        ParsedProperties.setSysPropertyIfNull(PingPongProducer.PAUSE_AFTER_BATCH_PROPNAME, 0l);
    }

    /**
     * Compile all the tests into a test suite.
     */
    public static Test suite()
    {
        // Build a new test suite
        TestSuite suite = new TestSuite("Ping-Pong Performance Tests");

        // Run performance tests in read committed mode.
        suite.addTest(new PingPongTestPerf("testPingPongOk"));

        return suite;
    }

    private static void setSystemPropertyIfNull(String propName, String propValue)
    {
        if (System.getProperty(propName) == null)
        {
            System.setProperty(propName, propValue);
        }
    }

    public void testPingPongOk(int numPings) throws Exception
    {
        // Get the per thread test setup to run the test through.
        PerThreadSetup perThreadSetup = threadSetup.get();

        // Generate a sample message. This message is already time stamped and has its reply-to destination set.
        ObjectMessage msg =
            perThreadSetup._testPingProducer.getTestMessage(perThreadSetup._testPingProducer.getReplyDestinations().get(0),
                                                            testParameters.getPropertyAsInteger(
                                                                PingPongProducer.MESSAGE_SIZE_PROPNAME),
                                                            testParameters.getPropertyAsBoolean(
                                                                PingPongProducer.PERSISTENT_MODE_PROPNAME));

        // Send the message and wait for a reply.
        int numReplies =
            perThreadSetup._testPingProducer.pingAndWaitForReply(msg, numPings, PingPongProducer.DEFAULT_TIMEOUT);

        // Fail the test if the timeout was exceeded.
        if (numReplies != numPings)
        {
            Assert.fail("The ping timed out, got " + numReplies + " out of " + numPings);
        }
    }

    /**
     * Performs test fixture creation on a per thread basis. This will only be called once for each test thread.
     */
    public void threadSetUp()
    {
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
            long pause = testParameters.getPropertyAsInteger(PingPongProducer.PAUSE_AFTER_BATCH_PROPNAME);

            synchronized (this)
            {
                // Establish a bounce back client on the ping queue to bounce back the pings.
                perThreadSetup._testPingBouncer = new PingPongBouncer(brokerDetails, username, password, virtualPath,
                                                                      destinationName, persistent, transacted, selector,
                                                                      verbose, pubsub);

                // Start the connections for client and producer running.
                perThreadSetup._testPingBouncer.getConnection().start();

                // Establish a ping-pong client on the ping queue to send the pings with.

                perThreadSetup._testPingProducer = new PingPongProducer(brokerDetails, username, password, virtualPath,
                                                                        destinationName, selector, transacted, persistent,
                                                                        messageSize, verbose, failAfterCommit,
                                                                        failBeforeCommit, failAfterSend, failBeforeSend,
                                                                        failOnce, batchSize, 0, rate, pubsub,
                                                                        unique, ackMode, pause);
                perThreadSetup._testPingProducer.getConnection().start();
            }

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
                perThreadSetup._testPingProducer.close();
                //perThreadSetup._testPingBouncer.close();
            }

            // Ensure the per thread fixture is reclaimed.
            threadSetup.remove();
        }
        catch (JMSException e)
        {
            _logger.warn("There was an exception during per thread tear down.");
        }
    }

    protected static class PerThreadSetup
    {
        /**
         * Holds the test ping-pong producer.
         */
        private PingPongProducer _testPingProducer;

        /**
         * Holds the test ping client.
         */
        private PingPongBouncer _testPingBouncer;
    }
}
