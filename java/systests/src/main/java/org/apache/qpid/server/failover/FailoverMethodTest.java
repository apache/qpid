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

import junit.framework.TestCase;
import org.apache.qpid.AMQDisconnectedException;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.apache.qpid.url.URLSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import java.util.concurrent.CountDownLatch;

public class FailoverMethodTest extends TestCase implements ExceptionListener
{
    private CountDownLatch _failoverComplete = new CountDownLatch(1);

    public void setUp() throws AMQVMBrokerCreationException
    {
        TransportConnection.createVMBroker(1);
    }

    public void tearDown() throws AMQVMBrokerCreationException
    {
        TransportConnection.killAllVMBrokers();
    }

    /**
     * Test that the round robin method has the correct delays.
     * The first connection to vm://:1 will work but the localhost connection should fail but the duration it takes
     * to report the failure is what is being tested.
     *
     * @throws URLSyntaxException
     * @throws InterruptedException
     * @throws JMSException
     */
    public void testFailoverRoundRobinDelay() throws URLSyntaxException, InterruptedException, JMSException
    {
        //note: The VM broker has no connect delay and the default 1 retry
        //        while the tcp:localhost broker has 3 retries with a 2s connect delay
        String connectionString = "amqp://guest:guest@/test?brokerlist=" +
                                  "'vm://:1;tcp://localhost:5670?connectdelay='2000',retries='3''";

        AMQConnectionURL url = new AMQConnectionURL(connectionString);

        try
        {
            long start = System.currentTimeMillis();
            AMQConnection connection = new AMQConnection(url, null);

            connection.setExceptionListener(this);

            TransportConnection.killAllVMBrokers();

            _failoverComplete.await();

            long end = System.currentTimeMillis();

            long duration = (end - start);

            //Failover should take more that 6 seconds.
            // 3 Retires
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

    public void testFailoverSingleDelay() throws URLSyntaxException, AMQVMBrokerCreationException,
                                                 InterruptedException, JMSException
    {
        String connectionString = "amqp://guest:guest@/test?brokerlist='vm://:1?connectdelay='2000',retries='3''";

        AMQConnectionURL url = new AMQConnectionURL(connectionString);

        try
        {
            long start = System.currentTimeMillis();
            AMQConnection connection = new AMQConnection(url, null);

            connection.setExceptionListener(this);

            TransportConnection.killAllVMBrokers();

            _failoverComplete.await();

            long end = System.currentTimeMillis();

            long duration = (end - start);

            //Failover should take more that 6 seconds.
            // 3 Retires
            // so NoDelay 0 (Connect) NoDelay 0 Delay 1 Delay 2 Delay  3
            // so 3 delays of 2s in total for connection
            // so max time is 6 seconds of delay + 1 second of runtime. == 7 seconds

            // Ensure we actually had the delay
            assertTrue("Failover took less than 6 seconds", duration > 6000);

            // Ensure we don't have delays before initial connection and reconnection.
            // We allow 1 second for initial connection and failover logic on top of 6s of sleep.
            assertTrue("Failover took more than 7 seconds:(" + duration + ")", duration < 7000);
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }

    public void onException(JMSException e)
    {
        if (e.getLinkedException() instanceof AMQDisconnectedException)
        {
            _failoverComplete.countDown();
        }
    }
}
