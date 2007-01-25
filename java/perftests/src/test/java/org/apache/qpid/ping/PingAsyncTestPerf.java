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
package org.apache.qpid.ping;

import uk.co.thebadgerset.junit.extensions.TimingControllerAware;
import uk.co.thebadgerset.junit.extensions.TimingController;

import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.JMSException;
import javax.jms.Message;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;


public class PingAsyncTestPerf extends PingTestPerf implements TimingControllerAware
{
    private static Logger _logger = Logger.getLogger(PingAsyncTestPerf.class);

    private TimingController _timingController;

    private AsyncMessageListener _listener;

    private volatile boolean _done = false;

    public PingAsyncTestPerf(String name)
    {
        super(name);
    }

    /**
     * Compile all the tests into a test suite.
     */
    public static Test suite()
    {
        // Build a new test suite
        TestSuite suite = new TestSuite("Ping Performance Tests");

        // Run performance tests in read committed mode.
        suite.addTest(new PingAsyncTestPerf("testAsyncPingOk"));

        return suite;
    }

    protected void setUp() throws Exception
    {
        // Create the test setups on a per thread basis, only if they have not already been created.

        if (threadSetup.get() == null)
        {
            PerThreadSetup perThreadSetup = new PerThreadSetup();

            // Extract the test set up paramaeters.
            String brokerDetails = testParameters.getProperty(BROKER_PROPNAME);
            String username = "guest";
            String password = "guest";
            String virtualpath = testParameters.getProperty(VIRTUAL_PATH_PROPNAME);
            int destinationscount = Integer.parseInt(testParameters.getProperty(PING_DESTINATION_COUNT_PROPNAME));
            String destinationname = testParameters.getProperty(PING_DESTINATION_NAME_PROPNAME);
            boolean persistent = Boolean.parseBoolean(testParameters.getProperty(PERSISTENT_MODE_PROPNAME));
            boolean transacted = Boolean.parseBoolean(testParameters.getProperty(TRANSACTED_PROPNAME));
            String selector = null;
            boolean verbose = Boolean.parseBoolean(testParameters.getProperty(VERBOSE_OUTPUT_PROPNAME));
            int messageSize = Integer.parseInt(testParameters.getProperty(MESSAGE_SIZE_PROPNAME));
            int rate = Integer.parseInt(testParameters.getProperty(RATE_PROPNAME));
            boolean pubsub = Boolean.parseBoolean(testParameters.getProperty(IS_PUBSUB_PROPNAME));


            boolean afterCommit = Boolean.parseBoolean(testParameters.getProperty(FAIL_AFTER_COMMIT));
            boolean beforeCommit = Boolean.parseBoolean(testParameters.getProperty(FAIL_BEFORE_COMMIT));
            boolean afterSend = Boolean.parseBoolean(testParameters.getProperty(FAIL_AFTER_SEND));
            boolean beforeSend = Boolean.parseBoolean(testParameters.getProperty(FAIL_BEFORE_SEND));
            boolean failOnce = Boolean.parseBoolean(testParameters.getProperty(FAIL_ONCE));

            int batchSize = Integer.parseInt(testParameters.getProperty(BATCH_SIZE));
            int commitbatchSize = Integer.parseInt(testParameters.getProperty(COMMIT_BATCH_SIZE));

            // This is synchronized because there is a race condition, which causes one connection to sleep if
            // all threads try to create connection concurrently
            synchronized (this)
            {
                // Establish a client to ping a Queue and listen the reply back from same Queue
                perThreadSetup._pingItselfClient = new TestPingItself(brokerDetails, username, password, virtualpath,
                                                                      destinationname, selector, transacted, persistent,
                                                                      messageSize, verbose,
                                                                      afterCommit, beforeCommit, afterSend, beforeSend, failOnce,
                                                                      commitbatchSize, destinationscount, rate, pubsub);


                _listener = new AsyncMessageListener(batchSize);

                perThreadSetup._pingItselfClient.setMessageListener(_listener);
                // Start the client connection
                perThreadSetup._pingItselfClient.getConnection().start();

                // Attach the per-thread set to the thread.
                threadSetup.set(perThreadSetup);
            }
        }
    }


    public void testAsyncPingOk(int numPings)
    {
        _timingController = this.getTimingController();

        _listener.setTotalMessages(numPings);

        PerThreadSetup perThreadSetup = threadSetup.get();
        if (numPings == 0)
        {
            _logger.error("Number of pings requested was zero.");
        }

        // Generate a sample message. This message is already time stamped and has its reply-to destination set.
        ObjectMessage msg = null;

        try
        {
            msg = perThreadSetup._pingItselfClient.getTestMessage(null,
                                                                  Integer.parseInt(testParameters.getProperty(
                                                                          MESSAGE_SIZE_PROPNAME)),
                                                                  Boolean.parseBoolean(testParameters.getProperty(
                                                                          PERSISTENT_MODE_PROPNAME)));
        }
        catch (JMSException e)
        {

        }

        // start the test
        long timeout = Long.parseLong(testParameters.getProperty(TIMEOUT_PROPNAME));

        String correlationID = Long.toString(perThreadSetup._pingItselfClient.getNewID());

        try
        {
            _logger.debug("Sending messages");

            perThreadSetup._pingItselfClient.pingNoWaitForReply(msg, numPings, correlationID);

            _logger.debug("All sent");
        }
        catch (JMSException e)
        {
            e.printStackTrace();
            Assert.fail("JMS Exception Recevied" + e);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        while (!_done)
        {
            try
            {
                _logger.debug("Awating test finish");

                perThreadSetup._pingItselfClient.getEndLock(correlationID).await(timeout, TimeUnit.MILLISECONDS);

                if (perThreadSetup._pingItselfClient.getEndLock(correlationID).getCount() != 0)
                {
                    _logger.error("Timeout occured");
                    _done = true;
                }
                else
                {
                    _logger.error("Countdown reached Done?" + _done);
                }
                //Allow the time out to exit the loop.
            }
            catch (InterruptedException e)
            {
                //ignore
                _logger.error("Awaiting test end interrupted.");

            }
        }

        perThreadSetup._pingItselfClient.removeLock(correlationID);

        // Fail the test if the timeout was exceeded.
        int numReplies = _listener.getReplyCount();

        _logger.info("Test Finished");

        if (numReplies != numPings)
        {

            try
            {
                perThreadSetup._pingItselfClient.commitTx(perThreadSetup._pingItselfClient.getConsumerSession());
            }
            catch (JMSException e)
            {
                _logger.error("Error commiting recevied messages", e);
            }
            try
            {
                _timingController.completeTest(false, numPings - numReplies);
            }
            catch (InterruptedException e)
            {
                //ignore
            }
            Assert.fail("The ping timed out after " + timeout + " ms. Messages Sent = " + numPings + ", MessagesReceived = " + numReplies);
        }
    }

    public void setTimingController(TimingController timingController)
    {
        _timingController = timingController;
    }

    public TimingController getTimingController()
    {
        return _timingController;
    }


    private class AsyncMessageListener implements MessageListener
    {
        private AtomicInteger _messageReceived;
        private volatile int _totalMessages;
        private int _batchSize;

        public AsyncMessageListener(int batchSize, int totalMessages)
        {
            _batchSize = batchSize;
            _totalMessages = totalMessages;
            _messageReceived = new AtomicInteger(0);
        }

        public AsyncMessageListener(int batchSize)
        {
            _batchSize = batchSize;
            _totalMessages = -1;
            _messageReceived = new AtomicInteger(0);
        }

        public void setTotalMessages(int newTotal)
        {
            _totalMessages = newTotal;
            _messageReceived.set(0);
        }

        public void onMessage(Message message)
        {
            _logger.trace("Message Recevied");

            int messagesReceived = _messageReceived.incrementAndGet();

            try
            {
                if (messagesReceived % _batchSize == 0)
                {
                    if (_timingController != null)
                    {
                        _timingController.completeTest(true, _batchSize);
                    }

                    if (messagesReceived == _totalMessages)
                    {
                        _done = true;
                    }
                }
                else if (messagesReceived == _totalMessages)
                {
                    _logger.info("Test Completed.. signalling");
                    doDone();
                }

            }
            catch (InterruptedException e)
            {
                _logger.error("Interupted Test");
//                doDone();
            }

        }

        private void doDone()
        {
            _done = true;

            _logger.trace("Messages received:" + _messageReceived.get());
            _logger.trace("Total Messages :" + _totalMessages);

            try
            {
                _timingController.completeTest(true, _totalMessages - _messageReceived.get());
            }
            catch (InterruptedException e)
            {
                //ignore
            }
        }

        public int getReplyCount()
        {
            return _messageReceived.get();
        }

    }

}
