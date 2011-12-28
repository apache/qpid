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
package org.apache.qpid.client;

import javax.jms.Session;

import org.apache.qpid.test.unit.message.TestAMQSession;
import org.apache.qpid.url.AMQBindingURL;

import junit.framework.TestCase;

public class BasicMessageConsumer_0_8_Test extends TestCase
{
    /**
     * Test that if there is a value for Reject Behaviour specified for the Destination
     * used to create the Consumer, it overrides the value for the Connection.
     */
    public void testDestinationRejectBehaviourOverridesDefaultConnection() throws Exception
    {
        /*
         * Check that when the connection does not have a value applied that this
         * is successfully overridden with a specific value by the consumer.
         */
        String connUrlString = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'";
        AMQConnection conn = new MockAMQConnection(connUrlString);

        String url = "exchangeClass://exchangeName/Destination/Queue?rejectbehaviour='server'";
        AMQBindingURL burl = new AMQBindingURL(url);
        AMQDestination queue = new AMQQueue(burl);

        TestAMQSession testSession = new TestAMQSession(conn);
        BasicMessageConsumer_0_8 consumer =
                new BasicMessageConsumer_0_8(0, conn, queue, "", false, null, testSession, null, null, 10, 5, false, Session.SESSION_TRANSACTED, false, false);

        assertEquals("Reject behaviour was was not as expected", RejectBehaviour.SERVER, consumer.getRejectBehaviour());
    }

    /**
     * Check that when the connection does have a specific value applied that this
     * is successfully overridden with another specific value by the consumer.
     */
    public void testDestinationRejectBehaviourSpecified() throws Exception
    {
        final String connUrlString = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'&rejectbehaviour='server'";
        final AMQConnection conn = new MockAMQConnection(connUrlString);

        final String url = "exchangeClass://exchangeName/Destination/Queue?rejectbehaviour='normal'";
        final AMQBindingURL burl = new AMQBindingURL(url);
        final AMQDestination queue = new AMQQueue(burl);

        final TestAMQSession testSession = new TestAMQSession(conn);
        final BasicMessageConsumer_0_8 consumer =
                new BasicMessageConsumer_0_8(0, conn, queue, "", false, null, testSession, null, null, 10, 5, false, Session.SESSION_TRANSACTED, false, false);

        assertEquals("Reject behaviour was was not as expected", RejectBehaviour.NORMAL, consumer.getRejectBehaviour());
    }

    /**
     * Test that if no value for Reject Behaviour is applied to the Destination, then the value
     * from the connection is used and acts as expected.
     */
    public void testRejectBehaviourDetectedFromConnection() throws Exception
    {
        /*
         * Check that when the connection does have a specific value applied that this
         * is successfully detected by the consumer.
         */
        String connUrlString = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'&rejectbehaviour='normal'";
        AMQConnection conn = new MockAMQConnection(connUrlString);

        String url = "exchangeClass://exchangeName/Destination/Queue";
        AMQBindingURL burl = new AMQBindingURL(url);
        AMQDestination queue = new AMQQueue(burl);

        assertNull("Reject behaviour should have been null", queue.getRejectBehaviour());

        TestAMQSession testSession = new TestAMQSession(conn);
        BasicMessageConsumer_0_8 consumer =
                new BasicMessageConsumer_0_8(0, conn, queue, "", false, null, testSession, null, null, 10, 5, false, Session.SESSION_TRANSACTED, false, false);

        assertEquals("Reject behaviour was was not as expected", RejectBehaviour.NORMAL, consumer.getRejectBehaviour());
    }


    protected RejectBehaviour getRejectBehaviour(AMQDestination destination)
    {
        return destination.getRejectBehaviour();
    }
}
