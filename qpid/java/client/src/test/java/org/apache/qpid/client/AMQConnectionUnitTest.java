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

import junit.framework.TestCase;

import org.apache.qpid.client.configuration.ClientProperties;
import org.apache.qpid.jms.ConnectionURL;

public class AMQConnectionUnitTest extends TestCase
{
    public void testMaxDeliveryCountPresent() throws Exception
    {
        String url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'&maxdeliverycount='3'";
        ConnectionURL connectionURL = new AMQConnectionURL(url);

        //check the max delivery count option is successfully passed through to the AMQConnection
        AMQConnection conn = new MockAMQConnection(connectionURL, null);
        assertEquals("Max Delivery Count option was not as expected", 3, 
                conn.getMaxDeliveryCount());
    }
    
    public void testMaxDeliveryCountNotPresent() throws Exception
    {
        String url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'";
        ConnectionURL connectionURL = new AMQConnectionURL(url);

        //check the max delivery count value defaults to 0 when no url option or sys prop is specified.
        AMQConnection conn = new MockAMQConnection(connectionURL, null);
        assertEquals("Max Delivery Count option was not as expected", 0, 
                conn.getMaxDeliveryCount());
    }
    
    public void testMaxDeliverySystemProperty() throws Exception
    {
        String oldSysPropValue = System.setProperty(
                ClientProperties.MAX_DELIVERY_COUNT_PROP_NAME, "15");
        try
        {
            String url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'";
            ConnectionURL connectionURL = new AMQConnectionURL(url);

            //check the max delivery count system property was successfully picked up and
            //the value seet for the AMQConnection
            AMQConnection conn = new MockAMQConnection(connectionURL, null);
            assertEquals("Max Delivery Count option was not as expected", 15, 
                    conn.getMaxDeliveryCount());
        }
        finally
        {
            if(oldSysPropValue != null)
            {
                System.setProperty(
                        ClientProperties.MAX_DELIVERY_COUNT_PROP_NAME, oldSysPropValue);
            }
            else
            {
                System.clearProperty(ClientProperties.MAX_DELIVERY_COUNT_PROP_NAME);
            }
        }
    }
    
    public void testMaxDeliveryUrlOptionOverridesSystemProperty() throws Exception
    {
        String oldSysPropValue = System.setProperty(
                ClientProperties.MAX_DELIVERY_COUNT_PROP_NAME, "15");
        try
        {
            String url = "amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'&maxdeliverycount='5'";
            ConnectionURL connectionURL = new AMQConnectionURL(url);

            //check the max delivery count system property was overridden by
            //the specified connectionURL option value
            AMQConnection conn = new MockAMQConnection(connectionURL, null);
            assertEquals("Max Delivery Count option was not as expected", 5, 
                    conn.getMaxDeliveryCount());
        }
        finally
        {
            if(oldSysPropValue != null)
            {
                System.setProperty(
                        ClientProperties.MAX_DELIVERY_COUNT_PROP_NAME, oldSysPropValue);
            }
            else
            {
                System.clearProperty(ClientProperties.MAX_DELIVERY_COUNT_PROP_NAME);
            }
        }
    }
}
