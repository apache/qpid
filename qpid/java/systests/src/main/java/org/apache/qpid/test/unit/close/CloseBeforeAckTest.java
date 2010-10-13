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
package org.apache.qpid.test.unit.close;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This test forces the situation where a session is closed whilst a message consumer is still in its onMessage method.
 * Running in AUTO_ACK mode, the close call ought to wait until the onMessage method completes, and the ack is sent
 * before closing the connection.
 *
 * <p><table id="crc"><caption>CRC Card</caption> <tr><th> Responsibilities <th> Collaborations <tr><td> Check that
 * closing a connection whilst handling a message, blocks till completion of the handler. </table>
 */
public class CloseBeforeAckTest extends QpidBrokerTestCase
{
    private static final Logger log = LoggerFactory.getLogger(CloseBeforeAckTest.class);
    private static final String TEST_QUEUE_NAME = "TestQueue";

    private Connection connection;
    private Session session;
    private int TEST_COUNT = 25;
    
    private CountDownLatch allowClose = new CountDownLatch(1);
    private CountDownLatch allowContinue = new CountDownLatch(1);
    
    private Callable<Void> one = new Callable<Void>()
    {
        public Void call() throws Exception
        {
            // Set this up to listen for message on the test session.
            MessageConsumer consumer = session.createConsumer(session.createQueue(TEST_QUEUE_NAME));
            consumer.setMessageListener(new MessageListener()
            {
                public void onMessage(Message message)
                {
                    // Give thread 2 permission to close the session.
                    allowClose.countDown();

                    // Wait until thread 2 has closed the connection, or is blocked waiting for this to complete.
                    try
                    {
                        allowContinue.await(1000, TimeUnit.MILLISECONDS);
                    }
                    catch (InterruptedException e)
                    {
                        // ignore
                    }
                }
            });
            
            return null;
        }
    };

    private Callable<Void> two = new Callable<Void>()
    {
        public Void call() throws Exception
        {
            // Send a message to be picked up by thread 1.
            MessageProducer producer = session.createProducer(null);
            producer.send(session.createQueue(TEST_QUEUE_NAME), session.createTextMessage("Hi there thread 1!"));

            // Wait for thread 1 to pick up the message and give permission to continue.
            allowClose.await();

            // Close the connection.
            session.close();

            // Allow thread 1 to continue to completion, if it is erronously still waiting.
            allowContinue.countDown();
            
            return null;
        }
    };

    public void testCloseBeforeAutoAck_QPID_397() throws Exception
    {
        // Create a session in auto acknowledge mode. This problem shows up in auto acknowledge if the client acks
        // message at the end of the onMessage method, after a close has been sent.
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        ExecutorService executor = new ScheduledThreadPoolExecutor(2);
        Future<Void> first =  executor.submit(one);
        Future<Void> second = executor.submit(two);
        executor.shutdown();

        if (!executor.awaitTermination(2000, TimeUnit.MILLISECONDS))
        {
            fail("Deadlocked threads after 2000ms");
        }
        
        List<String> errors = new ArrayList<String>(2);
        try
        {
	        first.get();
        }
        catch (ExecutionException ee)
        {
            errors.add(ee.getCause().getMessage());
        }
        try
        {
            second.get();
        }
        catch (ExecutionException ee)
        {
            errors.add(ee.getCause().getMessage());
        }
        
        assertTrue("Errors found: " + errors.toArray(new String[0]), errors.isEmpty());
    }

    public void closeBeforeAutoAckManyTimes() throws Exception
    {
        for (int i = 0; i < TEST_COUNT; i++)
        {
            testCloseBeforeAutoAck_QPID_397();
        }
    }

    protected void setUp() throws Exception
    {
        super.setUp();
        connection =  getConnection("guest", "guest");
        connection.start();
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
    }
}
