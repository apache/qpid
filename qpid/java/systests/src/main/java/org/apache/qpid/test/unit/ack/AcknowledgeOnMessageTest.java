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

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.JMSAMQException;
import org.apache.qpid.client.failover.FailoverException;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This test extends the synchronous AcknowledgeTest to use a MessageListener
 * and receive messages asynchronously.
 */
public class AcknowledgeOnMessageTest extends AcknowledgeTest implements MessageListener
{
    protected CountDownLatch _receivedAll;
    protected AtomicReference<Exception> _causeOfFailure = new AtomicReference<Exception>(null);

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
    }

    /**
     * Override the synchronous AcknowledgeTest init to provide the _receivedAll
     * CountDownLatch init and ensure that we set the MessageListener.
     * @param transacted
     * @param mode
     * @throws Exception
     */
    @Override
    public void init(boolean transacted, int mode) throws Exception
    {
        _receivedAll = new CountDownLatch(NUM_MESSAGES);

        super.init(transacted, mode);
        _consumer.setMessageListener(this);
    }

    /**
     * This test overrides the testAcking from the simple recieve() model to all
     * for asynchronous receiving of messages.
     *
     * Again the transaction/ack mode is provided to this main test run
     *
     * The init method is called which will setup the listener so that we can
     * then sit and await using the _receivedAll CountDownLatch. We wait for up
     * to 10s if no messages have been received in the last 10s then test will
     * fail.
     *
     * If the test fails then it will attempt to retrieve any exception that the
     * asynchronous delivery thread may have recorded.
     *
     * @param transacted
     * @param mode
     *
     * @throws Exception
     */
    @Override
    protected void testAcking(boolean transacted, int mode) throws Exception
    {
        init(transacted, mode);

        _connection.start();

        // Set the lastCount to NUM_MESSAGES, this ensures that the compare
        // against the receviedAll count is accurate.
        int lastCount = NUM_MESSAGES;

        // Wait for messages to arrive
        boolean complete = _receivedAll.await(10000L, TimeUnit.MILLISECONDS);

        // If the messasges haven't arrived
        while (!complete)
        {
            // Check how many we have received
            int currentCount = (int) _receivedAll.getCount();

            // make sure we have received a message in the last cycle.
            if (lastCount == currentCount)
            {
                // If we didn't receive any messages then stop.
                // Something must have gone wrong.
                System.err.println("Giving up waiting as we didn't receive anything.");
                break;
            }
            // Remember the currentCount as the lastCount for the next cycle.
            // so we can exit if things get locked up.
            lastCount = currentCount;

            // Wait again for messages to arrive.
            complete = _receivedAll.await(10000L, TimeUnit.MILLISECONDS);
        }

        // If we failed to receive all the messages then fail the test.
        if (!complete)
        {
            // Check to see if we ended due to an exception in the onMessage handler
            Exception cause = _causeOfFailure.get();
            if (cause != null)
            {
                cause.printStackTrace();
                fail(cause.getMessage());
            }
            else
            {
                _logger.info("AOMT: Check QueueDepth:" + _queue);
                long onQueue=((AMQSession) getConnection().createSession(false, Session.AUTO_ACKNOWLEDGE)).getQueueDepth((AMQDestination) _queue);
                fail("All messages not received missing:" + _receivedAll.getCount() + "/" + NUM_MESSAGES+" On Queue:"+onQueue);

            }
        }

        // Even if we received all the messages.
        // Check to see if we ended due to an exception in the onMessage handler
        Exception cause = _causeOfFailure.get();
        if (cause != null)
        {
            cause.printStackTrace();
            fail(cause.getMessage());
        }

        try
        {
            _consumer.close();
        }
        catch (JMSAMQException amqe)
        {
            if (amqe.getLinkedException() instanceof FailoverException)
            {
                fail("QPID-143 : Auto Ack can acknowledge message from previous session after failver. If failover occurs between deliver and ack.");
            }
            // else Rethrow for TestCase to catch.
            throw amqe;
        }

        _consumerSession.close();

        _logger.info("AOMT: check number of message at end of test.");
        assertEquals("Wrong number of messages on queue", 0,
                     ((AMQSession) getConnection().createSession(false, Session.AUTO_ACKNOWLEDGE)).getQueueDepth((AMQDestination) _queue));
    }

    /**
     * The MessageListener interface that recieves the message and counts down
     * the _receivedAll CountDownLatch.
     *
     * Again like AcknowledgeTest acknowledgement is actually handled in
     * doAcknowlegement.
     *
     * The message INDEX is validated to ensure the correct message order is
     * preserved.
     *
     * @param message
     */
    public void onMessage(Message message)
    {
        // Log received Message for debugging
        _logger.info("RECEIVED MESSAGE:" + message);

        try
        {
            int count = NUM_MESSAGES - (int) _receivedAll.getCount();

            assertEquals("Incorrect message received", count, message.getIntProperty(INDEX));

            count++;
            if (count < NUM_MESSAGES)
            {
                //Send the next message
                _producer.send(createNextMessage(_consumerSession, count));
            }

            doAcknowlegement(message);

            _receivedAll.countDown();
        }
        catch (Exception e)
        {
            // This will end the test run by counting down _receivedAll
            fail(e);
        }
    }

    /**
     * Pass the given exception back to the waiting thread to fail the test run.
     *
     * @param e The exception that is causing the test to fail.
     */
    protected void fail(Exception e)
    {
        //record the failure
        _causeOfFailure.set(e);
        // End the test.
        while (_receivedAll.getCount() != 0)
        {
            _receivedAll.countDown();
        }
    }
}
