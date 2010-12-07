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

import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.test.unit.message.TestAMQSession;
import org.apache.qpid.url.AMQBindingURL;

import junit.framework.TestCase;

public class BasicMessageConsumer_0_8_Test extends TestCase
{
    /**
     * Test that if there is a value for Max Delivery Count specified for the Destination
     * used to create the Consumer, it overrides the value for the Connection.
     */
    public void testDestinationMaxDeliveryCountOverridesConnection() throws Exception
    {
        /*
         * Check that when the connection does not have a value applied that this
         * is successfully overridden with a specific value by the consumer.
         */
        String connUrlString = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'";
        ConnectionURL connectionURL = new AMQConnectionURL(connUrlString);
        AMQConnection conn = new MockAMQConnection(connectionURL, null);

        assertEquals("Max Delivery Count option was not as expected", 0,
                conn.getMaxDeliveryCount());

        String url = "exchangeClass://exchangeName/Destination/Queue?maxdeliverycount='1'";
        AMQBindingURL burl = new AMQBindingURL(url);
        AMQDestination queue = new AMQQueue(burl);
        
        AMQSession<BasicMessageConsumer_0_8, BasicMessageProducer_0_8> testSession = new TestAMQSession();
        BasicMessageConsumer_0_8 consumer = new BasicMessageConsumer_0_8(0, conn, queue, "", false, null, testSession, null, null, 10, 5, false, Session.SESSION_TRANSACTED, false, false);

        assertEquals("Max Delivery Attempts was was not as expected", 1, consumer.getMaxDeliveryAttempts());
        assertTrue("Max Delivery should have been enforced", consumer.isMaxDeliveryCountEnforced());

        /*
         * Check that when the connection does have a specific value applied that this
         * is successfully overridden with another specific value by the consumer.
         */
        connUrlString = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'&maxdeliverycount='5'";
        connectionURL = new AMQConnectionURL(connUrlString);
        conn = new MockAMQConnection(connectionURL, null);

        assertEquals("Max Delivery Count option was not as expected", 5,
                conn.getMaxDeliveryCount());

        url = "exchangeClass://exchangeName/Destination/Queue?maxdeliverycount='3'";
        burl = new AMQBindingURL(url);
        queue = new AMQQueue(burl);

        consumer = new BasicMessageConsumer_0_8(0, conn, queue, "", false, null, testSession, null, null, 10, 5, false, Session.SESSION_TRANSACTED, false, false);

        assertEquals("Max Delivery Attempts was was not as expected", 3, consumer.getMaxDeliveryAttempts());
        assertTrue("Max Delivery should have been enforced", consumer.isMaxDeliveryCountEnforced());
        
        /*
         * Check also that when the connection does have a specific value applied (5 above) that this
         * can be successfully overridden and disabled by the destination value being set to 0.
         */
        url = "exchangeClass://exchangeName/Destination/Queue?maxdeliverycount='0'";
        burl = new AMQBindingURL(url);
        queue = new AMQQueue(burl);
        
        consumer = new BasicMessageConsumer_0_8(0, conn, queue, "", false, null, testSession, null, null, 10, 5, false, Session.SESSION_TRANSACTED, false, false);

        assertEquals("Max Delivery Count option was not as expected", 5, conn.getMaxDeliveryCount());
        assertEquals("Max Delivery Attempts was was not as expected", 0, consumer.getMaxDeliveryAttempts());
        assertFalse("Max Delivery should not have been enforced", consumer.isMaxDeliveryCountEnforced());
    }

    /**
     * Test that if no value for MaxDeliveryCount is applied to the Destination, then the value 
     * from the connection is used and acts as expected.
     */
    public void testMaxDeliveryCountDetectedFromConnection() throws Exception
    {
        /*
         * Check that when the connection does have a specific value applied that this
         * is successfully detected by the consumer.
         */
        String connUrlString = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'&maxdeliverycount='5'";
        ConnectionURL connectionURL = new AMQConnectionURL(connUrlString);
        AMQConnection conn = new MockAMQConnection(connectionURL, null);

        String url = "exchangeClass://exchangeName/Destination/Queue";
        AMQBindingURL burl = new AMQBindingURL(url);
        AMQDestination queue = new AMQQueue(burl);
        
        assertEquals("Max Delivery Count should have been null", null, queue.getMaxDeliveryCount());
        
        AMQSession<BasicMessageConsumer_0_8, BasicMessageProducer_0_8> testSession = new TestAMQSession();
        BasicMessageConsumer_0_8 consumer = new BasicMessageConsumer_0_8(0, conn, queue, "", false, null, testSession, null, null, 10, 5, false, Session.SESSION_TRANSACTED, false, false);

        assertEquals("Max Delivery Attempts was was not as expected", 5, consumer.getMaxDeliveryAttempts());
        assertTrue("Max Delivery should have been enforced", consumer.isMaxDeliveryCountEnforced());
        
        /*
         * Also verify that when the connection does not have a value applied and defaults to 0, that
         * this is successfully detected by the consumer and disables the Max Delivery feature.
         */
        connUrlString = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'";
        connectionURL = new AMQConnectionURL(connUrlString);
        conn = new MockAMQConnection(connectionURL, null);
        
        consumer = new BasicMessageConsumer_0_8(0, conn, queue, "", false, null, testSession, null, null, 10, 5, false, Session.SESSION_TRANSACTED, false, false);
        assertEquals("Max Delivery Attempts was was not as expected", 0, consumer.getMaxDeliveryAttempts());
        assertFalse("Max Delivery should not have been enforced", consumer.isMaxDeliveryCountEnforced());
    }
}
