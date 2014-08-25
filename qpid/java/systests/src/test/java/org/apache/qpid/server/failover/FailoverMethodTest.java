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
package org.apache.qpid.server.failover;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQConnectionClosedException;
import org.apache.qpid.AMQDisconnectedException;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.util.SystemUtils;

import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FailoverMethodTest extends QpidBrokerTestCase implements ExceptionListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FailoverMethodTest.class);
    private CountDownLatch _failoverComplete = new CountDownLatch(1);
    private final int _freePortWithNoBroker = findFreePort();

    /**
     * Test that the round robin method has the correct delays.
     * The first connection will work but the localhost connection should fail but the duration it takes
     * to report the failure is what is being tested.
     *
     */
    public void testFailoverRoundRobinDelay() throws Exception
    {
        if (SystemUtils.isWindows())
        {
            //TODO Test requires redevelopment - timings/behaviour on windows mean it fails
            return;
        }

        //note: The first broker has no connect delay and the default 1 retry
        //        while the tcp:localhost broker has 3 retries with a 2s connect delay
        String connectionString = "amqp://guest:guest@/test?brokerlist=" +
                                  "'tcp://localhost:" + getPort() +
                                  ";tcp://localhost:" + _freePortWithNoBroker + "?connectdelay='2000',retries='3''";

        AMQConnectionURL url = new AMQConnectionURL(connectionString);

        try
        {
            long start = System.currentTimeMillis();
            AMQConnection connection = new AMQConnection(url);

            connection.setExceptionListener(this);

            LOGGER.debug("Stopping broker");
            stopBroker();
            LOGGER.debug("Stopped broker");

            _failoverComplete.await(30, TimeUnit.SECONDS);
            assertEquals("failoverLatch was not decremented in given timeframe",
                    0, _failoverComplete.getCount());

            long end = System.currentTimeMillis();

            long duration = (end - start);

            //Failover should take more that 6 seconds.
            // 3 Retries
            // so VM Broker NoDelay 0 (Connect) NoDelay 0
            // then TCP NoDelay 0 Delay 1 Delay 2 Delay  3
            // so 3 delays of 2s in total for connection
            // as this is a tcp connection it will take 1second per connection to fail
            // so max time is 6seconds of delay plus 4 seconds of TCP Delay + 1 second of runtime. == 11 seconds

            // Ensure we actually had the delay
            assertTrue("Failover took less than 6 seconds", duration > 6000);

            // Ensure we don't have delays before initial connection and reconnection.
            // We allow 1 second for initial connection and failover logic on top of 6s of sleep.
            assertTrue("Failover took more than 11 seconds:(" + duration + ")", duration < 11000);
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }

    public void testFailoverSingleDelay() throws Exception
    {
        if (SystemUtils.isWindows())
        {
            //TODO Test requires redevelopment - timings/behaviour on windows mean it fails
            return;
        }

        String connectionString = "amqp://guest:guest@/test?brokerlist='tcp://localhost:" + getPort() + "?connectdelay='2000',retries='3''";

        AMQConnectionURL url = new AMQConnectionURL(connectionString);

        try
        {
            long start = System.currentTimeMillis();
            AMQConnection connection = new AMQConnection(url);

            connection.setExceptionListener(this);

            LOGGER.debug("Stopping broker");
            stopBroker();
            LOGGER.debug("Stopped broker");

            _failoverComplete.await(30, TimeUnit.SECONDS);
            assertEquals("failoverLatch was not decremented in given timeframe",
                    0, _failoverComplete.getCount());

            long end = System.currentTimeMillis();

            long duration = (end - start);

            //Failover should take more that 6 seconds.
            // 3 Retries
            // so NoDelay 0 (Connect) NoDelay 0 Delay 1 Delay 2 Delay  3
            // so 3 delays of 2s in total for connection
            // so max time is 6 seconds of delay + 1 second of runtime. == 7 seconds

            // Ensure we actually had the delay
            assertTrue("Failover took less than 6 seconds", duration > 6000);

            // Ensure we don't have delays before initial connection and reconnection.
            // We allow 3 second for initial connection and failover logic on top of 6s of sleep.
            assertTrue("Failover took more than 9 seconds:(" + duration + ")", duration < 9000);
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }


    /**
     * Test that setting 'nofailover' as the failover policy does not result in
     * delays or connection attempts when the initial connection is lost.
     *
     * Test validates that there is a connection delay as required on initial
     * connection.
     */
    public void testNoFailover() throws Exception
    {
        if (SystemUtils.isWindows())
        {
            //TODO Test requires redevelopment - timings/behaviour on windows mean it fails
            return;
        }

        int CONNECT_DELAY = 2000;
        String connectionString = "amqp://guest:guest@/test?brokerlist='tcp://localhost:" + getPort() + "?connectdelay='" + CONNECT_DELAY + "'," +
                                  "retries='3'',failover='nofailover'";

        
        AMQConnectionURL url = new AMQConnectionURL(connectionString);

        Thread brokerStart = null;
        try
        {
            //Kill initial broker
            stopBroker();

            //Create a thread to start the broker asynchronously
            brokerStart = new Thread(new Runnable()
            {
                public void run()
                {
                    try
                    {
                        //Wait before starting broker
                        // The wait should allow at least 1 retries to fail before broker is ready
                        Thread.sleep(750);
                        startBroker();
                    }
                    catch (Exception e)
                    {
                        LOGGER.error("Exception whilst starting broker", e);
                    }
                }
            });

            brokerStart.start();
            long start = System.currentTimeMillis();
            //Start the connection so it will use the retries
            AMQConnection connection = new AMQConnection(url);

            long end = System.currentTimeMillis();
            long duration = (end - start);

            // Check that we actually had a delay in connection
            assertTrue("Initial connection should be longer than 1 delay : " + CONNECT_DELAY + " <:(" + duration + ")", duration > CONNECT_DELAY);


            connection.setExceptionListener(this);

            //Ensure we collect the brokerStart thread
            brokerStart.join();
            brokerStart = null;

            start = System.currentTimeMillis();

            //Kill connection
            stopBroker();

            _failoverComplete.await(30, TimeUnit.SECONDS);
            assertEquals("failoverLatch was not decremented in given timeframe", 0, _failoverComplete.getCount());

            end = System.currentTimeMillis();

            duration = (end - start);

            // Notification of the connection failure should be very quick as we are denying the ability to failover.
            // It may not be as quick for Java profile tests so lets just make sure it is less than the connectiondelay
            // Occasionally it takes 1s so we have to set CONNECT_DELAY to be higher to take that in to account. 
            assertTrue("Notification of the connection failure took was : " + CONNECT_DELAY + " >:(" + duration + ")", duration < CONNECT_DELAY);
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
        finally
        {
            // Guard against the case where the broker took too long to start
            // and the initial connection failed to be formed.
            if (brokerStart != null)
            {
                brokerStart.join();
            }
        }
    }

    @Override
    public void onException(JMSException e)
    {
        if (e.getLinkedException() instanceof AMQDisconnectedException || e.getLinkedException() instanceof AMQConnectionClosedException)
        {
            LOGGER.debug("Received AMQDisconnectedException");
            _failoverComplete.countDown();
        }
        else
        {
            LOGGER.error("Unexpected underlying exception", e.getLinkedException());
        }
    }

}
