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
package org.apache.qpid.test.unit.ack;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.jms.ConnectionListener;
import org.apache.qpid.util.FileUtils;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TransactionRolledBackException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.io.File;

/**
 * The AcknowlegeAfterFailoverOnMessageTests
 *
 * Extends the OnMessage AcknowledgeTests to validate that after the client has
 * failed over that the client can still receive and ack messages.
 *
 * All the AcknowledgeTest ack modes are exercised here though some are disabled
 * due to know issues (e.g. DupsOk, AutoAck : QPID-143 and the clientAck
 * and dirtyClientAck due to QPID-1816)
 *
 * This class has two main test structures, overrides of AcknowledgeOnMessageTest
 * to perform the clean acking based on session ack mode and a series of dirty
 * ack tests that test what happends if you receive a message then try and ack
 * AFTER you have failed over.
 *
 *
 */
public class AcknowledgeAfterFailoverOnMessageTest extends AcknowledgeOnMessageTest implements ConnectionListener
{

    protected CountDownLatch _failoverCompleted = new CountDownLatch(1);
    private MessageListener _listener = null;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        NUM_MESSAGES = 10;
    }

    /**
     * Override default init to add connectionListener so we can verify that
     * failover took place
     *
     * @param transacted create a transacted session for this test
     * @param mode       if not transacted what ack mode to use for this test
     *
     * @throws Exception if a problem occured during test setup.
     */
    @Override
    public void init(boolean transacted, int mode) throws Exception
    {
        super.init(transacted, mode);
        ((AMQConnection) _connection).setConnectionListener(this);
        // Override the listener for the dirtyAck testing.
        if (_listener != null)
        {
            _consumer.setMessageListener(_listener);
        }
    }

    /**
     * Prepare the broker for the next round.
     *
     * Called after acknowledging the messsage this method shuts the current
     * broker down connnects to the new broker and send a new message for the
     * client to failover to and receive.
     *
     * It ends by restarting the orignal broker so that the cycle can repeat.
     *
     * When we are able to cluster the java broker then will not need to do the
     * message repopulation or QPID_WORK clearing. All that we will need to do
     * is send the initial NUM_MESSAGES during startup and then bring the
     * brokers down at the right time to cause the client to fail between them.
     *
     * @param index
     * @throws Exception
     */
    protected void prepBroker(int index) throws Exception
    {
        // Alternate killing the broker based on the message index we are at.

        if (index % 2 == 0)
        {
            failBroker(getFailingPort());
            // Clean up the failed broker
            FileUtils.delete(new File(System.getProperty("QPID_WORK") + "/" + getFailingPort()), true);
        }
        else
        {
            failBroker(getPort());
            // Clean up the failed broker
            FileUtils.delete(new File(System.getProperty("QPID_WORK") + "/" + getPort()), true);
        }

        _failoverCompleted = new CountDownLatch(1);

        _logger.info("AAFOMT: prepNewBroker for message send");
        Connection connection = getConnection();

        try
        {

            //Stop the connection whilst we repopulate the broker, or the no_ack
            // test will drain the msgs before we can check we put the right number
            // back on again.

            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            // ensure destination is created.
            session.createConsumer(_queue).close();


            // If this is the last message then we can skip the send.
            // But we MUST ensure that we have created the queue with the
            // above createConsumer(_queue).close() as the test will end by
            // testing the queue depth which will fail if we don't ensure we
            // declare the queue.
            // index is 0 based so we need to check +1 against NUM_MESSAGES
            if ((index + 1) == NUM_MESSAGES)
            {
                return;
            }


            sendMessage(session, _queue, 1, index + 1, 0);

            // Validate that we have the message on the queue
            // In NoAck mode though the messasge may already have been sent to
            // the client so we have to skip the vaildation.
            if (_consumerSession.getAcknowledgeMode() != AMQSession.NO_ACKNOWLEDGE)
            {
                assertEquals("Wrong number of messages on queue", 1,
                             ((AMQSession) session).getQueueDepth((AMQDestination) _queue));
            }


        }
        catch (Exception e)
        {
            fail("Unable to prep new broker," + e.getMessage());
        }
        finally
        {
            connection.close();
        }

        try
        {

            //Restart the broker
            if (index % 2 == 0)
            {
                startBroker(getFailingPort());
            }
            else
            {
                startBroker(getPort());
            }
        }
        catch (Exception e)
        {
            fail("Unable to start failover broker," + e.getMessage());
        }

    }

    @Override
    public void doAcknowlegement(Message msg) throws JMSException
    {
        //Acknowledge current message
        super.doAcknowlegement(msg);

        try
        {
            prepBroker(msg.getIntProperty(INDEX));
        }
        catch (Exception e)
        {
            // Provide details of what went wrong with the stack trace
            e.printStackTrace();
            fail("Unable to prep new broker," + e);
        }
    }

    // Instance varilable for DirtyAcking test    
    int _msgCount = 0;
    boolean _cleaned = false;

    class DirtyAckingHandler implements MessageListener
    {
        /**
         * Validate first message but do nothing with it.
         *
         * Failover
         *
         * The receive the message again
         *
         * @param message
         */
        public void onMessage(Message message)
        {
            // Stop processing if we have an error and had to stop running.
            if (_receivedAll.getCount() == 0)
            {
                _logger.debug("Dumping msgs due to error(" + _causeOfFailure.get().getMessage() + "):" + message);
                return;
            }

            try
            {
                // Check we have the next message as expected
                assertNotNull("Message " + _msgCount + " not correctly received.", message);
                assertEquals("Incorrect message received", _msgCount, message.getIntProperty(INDEX));

                if (_msgCount == 0 && _failoverCompleted.getCount() != 0)
                {
                    // This is the first message we've received so lets fail the broker

                    failBroker(getFailingPort());

                    repopulateBroker();

                    _logger.error("Received first msg so failing over");

                    return;
                }

                _msgCount++;

                // Don't acknowlege the first message after failover so we can commit
                // them together
                if (_msgCount == 1)
                {
                    _logger.error("Received first msg after failover ignoring:" + _msgCount);

                    // Acknowledge the first message if we are now on the cleaned pass
                    if (_cleaned)
                    {
                        _receivedAll.countDown();
                    }

                    return;
                }

                if (_consumerSession.getTransacted())
                {
                    try
                    {
                        _consumerSession.commit();
                        if (!_cleaned)
                        {
                            fail("Session is dirty we should get an TransactionRolledBackException");
                        }
                    }
                    catch (TransactionRolledBackException trbe)
                    {
                        //expected path
                    }
                }
                else
                {
                    try
                    {
                        message.acknowledge();
                        if (!_cleaned)
                        {
                            fail("Session is dirty we should get an IllegalStateException");
                        }
                    }
                    catch (javax.jms.IllegalStateException ise)
                    {
                        assertEquals("Incorrect Exception thrown", "has failed over", ise.getMessage());
                        // Recover the sesion and try again.
                        _consumerSession.recover();
                    }
                }

                // Acknowledge the last message if we are in a clean state
                // this will then trigger test teardown.
                if (_cleaned)
                {
                    _receivedAll.countDown();
                }

                //Reset message count so we can try again.
                _msgCount = 0;
                _cleaned = true;
            }
            catch (Exception e)
            {
                // If something goes wrong stop and notifiy main thread.
                fail(e);
            }
        }
    }

    /**
     * Test that Acking/Committing a message received before failover causes
     * an exception at commit/ack time.
     *
     * Expected behaviour is that in:
     * * tx mode commit() throws a transacted RolledBackException
     * * client ack mode throws an IllegalStateException
     *
     * @param transacted is this session trasacted
     * @param mode       What ack mode should be used if not trasacted
     *
     * @throws Exception if something goes wrong.
     */
    protected void testDirtyAcking(boolean transacted, int mode) throws Exception
    {
        NUM_MESSAGES = 2;
        _listener = new DirtyAckingHandler();

        super.testAcking(transacted, mode);
    }

    public void testDirtyClientAck() throws Exception
    {
        testDirtyAcking(false, Session.CLIENT_ACKNOWLEDGE);
    }

    public void testDirtyAckingTransacted() throws Exception
    {
        testDirtyAcking(true, Session.SESSION_TRANSACTED);
    }

    private void repopulateBroker() throws Exception
    {
        // Repopulate this new broker so we can test what happends after failover

        //Get the connection to the first (main port) broker.
        Connection connection = getConnection();
        // Use a transaction to send messages so we can be sure they arrive.
        Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
        // ensure destination is created.
        session.createConsumer(_queue).close();

        sendMessage(session, _queue, NUM_MESSAGES);

        assertEquals("Wrong number of messages on queue", NUM_MESSAGES,
                     ((AMQSession) session).getQueueDepth((AMQDestination) _queue));

        connection.close();
    }

    // AMQConnectionListener Interface.. used so we can validate that we
    // actually failed over.

    public void bytesSent(long count)
    {
    }

    public void bytesReceived(long count)
    {
    }

    public boolean preFailover(boolean redirect)
    {
        //Allow failover
        return true;
    }

    public boolean preResubscribe()
    {
        //Allow failover
        return true;
    }

    public void failoverComplete()
    {
        _failoverCompleted.countDown();
    }

    /**
     * Override so we can block until failover has completd
     *
     * @param port
     */
    @Override
    public void failBroker(int port)
    {
        super.failBroker(port);

        try
        {
            if (!_failoverCompleted.await(DEFAULT_FAILOVER_TIME, TimeUnit.MILLISECONDS))
            {
                // Use an exception so that we use our local fail() that notifies the main thread of failure
                throw new Exception("Failover did not occur in specified time:" + DEFAULT_FAILOVER_TIME);
            }

        }
        catch (Exception e)
        {
            // Use an exception so that we use our local fail() that notifies the main thread of failure
            fail(e);
        }
    }

}
