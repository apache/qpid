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
        String connectionString = "amqp://guest:guest@/test?brokerlist='vm://:1;tcp://localhost:5670?connectdelay='2000',retries='3''";

        AMQConnectionURL url = new AMQConnectionURL(connectionString);

        try
        {
            long start = System.currentTimeMillis();
            AMQConnection connection = new AMQConnection(url, null);

            connection.setExceptionListener(this);

            TransportConnection.killAllVMBrokers();

            _failoverComplete.await();

            long end = System.currentTimeMillis();

            //Failover should take less that 10 seconds.
            // This is calculated by vm://:1 two retries left after initial connection ( 4s)
            // localhost get three retries so (6s) so 10s in total for connection dropping
            assertTrue("Failover took over 10 seconds:"+(end - start), (end - start) > 10000);

        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }

    public void testFailoverSingleDelay() throws URLSyntaxException, AMQVMBrokerCreationException, InterruptedException, JMSException
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

            //Failover should take less that 10 seconds.
            // This is calculated by vm://:1 two retries left after initial connection
            // so 4s in total for connection dropping

            assertTrue("Failover took over 4 seconds", (end - start) > 4000);

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
