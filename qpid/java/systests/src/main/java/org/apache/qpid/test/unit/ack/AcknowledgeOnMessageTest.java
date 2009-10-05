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

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class AcknowledgeOnMessageTest extends AcknowledgeTest implements MessageListener
{
    protected CountDownLatch _receviedAll;
    protected AtomicReference<Exception> _causeOfFailure = new AtomicReference<Exception>(null);

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
    }

    @Override
    public void init(boolean transacted, int mode) throws Exception
    {
        _receviedAll = new CountDownLatch(NUM_MESSAGES);

        super.init(transacted, mode);
        _consumer.setMessageListener(this);
    }

    /**
     * @param transacted
     * @param mode
     *
     * @throws Exception
     */
    protected void testAcking(boolean transacted, int mode) throws Exception
    {
        init(transacted, mode);

        _connection.start();

        if (!_receviedAll.await(10000L, TimeUnit.MILLISECONDS))
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
                fail("All messages not received:" + _receviedAll.getCount() + "/" + NUM_MESSAGES);
            }
        }

        // Check to see if we ended due to an exception in the onMessage handler
        Exception cause = _causeOfFailure.get();
        if (cause != null)
        {
            cause.printStackTrace();
            fail(cause.getMessage());
        }

        _consumer.close();
        _consumerSession.close();

        assertEquals("Wrong number of messages on queue", 0,
                     ((AMQSession) getConnection().createSession(false, Session.AUTO_ACKNOWLEDGE)).getQueueDepth((AMQDestination) _queue));
    }

    public void onMessage(Message message)
    {
        try
        {
            int count = NUM_MESSAGES - (int) _receviedAll.getCount();

            assertEquals("Incorrect message received", count, message.getIntProperty(INDEX));

            count++;
            if (count < NUM_MESSAGES)
            {
                //Send the next message
                _producer.send(createNextMessage(_consumerSession, count));
            }

            doAcknowlegement(message);

            _receviedAll.countDown();
        }
        catch (Exception e)
        {
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
        _causeOfFailure.set(e);
        // End the test.
        while (_receviedAll.getCount() != 0)
        {
            _receviedAll.countDown();
        }
    }
}
