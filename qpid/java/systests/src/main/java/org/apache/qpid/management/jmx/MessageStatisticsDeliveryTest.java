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
package org.apache.qpid.management.jmx;

import java.util.ArrayList;
import java.util.List;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.management.common.mbeans.ManagedConnection;

/**
 * Test statistics for delivery and receipt.
 */
public class MessageStatisticsDeliveryTest extends MessageStatisticsTestCase
{
    public void configureStatistics() throws Exception
    {
        setConfigurationProperty("statistics.generation.broker", "true");
        setConfigurationProperty("statistics.generation.virtualhosts", "true");
        setConfigurationProperty("statistics.generation.connections", "true");
    }

    public void testDeliveryAndReceiptStatistics() throws Exception
    {
        ManagedBroker vhost = _jmxUtils.getManagedBroker("test");
        
        sendUsing(_test, 5, 200);
        Thread.sleep(1000);
        
        List<String> addresses = new ArrayList<String>();
        for (ManagedConnection mc : _jmxUtils.getManagedConnections("test"))
        {
            assertEquals("Incorrect connection delivery total", 0,  mc.getTotalMessagesDelivered());
            assertEquals("Incorrect connection delivery data", 0, mc.getTotalDataDelivered());
            assertEquals("Incorrect connection receipt total", 5,  mc.getTotalMessagesReceived());
            assertEquals("Incorrect connection receipt data", 1000, mc.getTotalDataReceived());
            
            addresses.add(mc.getRemoteAddress());
        }
        
        assertEquals("Incorrect vhost delivery total", 0, vhost.getTotalMessagesDelivered());
        assertEquals("Incorrect vhost delivery data", 0, vhost.getTotalDataDelivered());
        assertEquals("Incorrect vhost receipt total", 5, vhost.getTotalMessagesReceived());
        assertEquals("Incorrect vhost receipt data", 1000, vhost.getTotalDataReceived());
        
        Connection test = new AMQConnection(_brokerUrl, USER, USER, "clientid", "test");
        test.start();
        receiveUsing(test, 5);
        
        for (ManagedConnection mc : _jmxUtils.getManagedConnections("test"))
        {
            if (addresses.contains(mc.getRemoteAddress()))
            {
                assertEquals("Incorrect connection delivery total", 0,  mc.getTotalMessagesDelivered());
                assertEquals("Incorrect connection delivery data", 0, mc.getTotalDataDelivered());
                assertEquals("Incorrect connection receipt total", 5,  mc.getTotalMessagesReceived());
                assertEquals("Incorrect connection receipt data", 1000, mc.getTotalDataReceived());
            }
            else
            {
                assertEquals("Incorrect connection delivery total", 5,  mc.getTotalMessagesDelivered());
                assertEquals("Incorrect connection delivery data", 1000, mc.getTotalDataDelivered());
                assertEquals("Incorrect connection receipt total", 0,  mc.getTotalMessagesReceived());
                assertEquals("Incorrect connection receipt data", 0, mc.getTotalDataReceived());
            }
        }
        assertEquals("Incorrect vhost delivery total", 5, vhost.getTotalMessagesDelivered());
        assertEquals("Incorrect vhost delivery data", 1000, vhost.getTotalDataDelivered());
        assertEquals("Incorrect vhost receipt total", 5, vhost.getTotalMessagesReceived());
        assertEquals("Incorrect vhost receipt data", 1000, vhost.getTotalDataReceived());
        
        test.close();
    }

    protected void receiveUsing(Connection con, int number) throws Exception
    {
        Session session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
        createQueue(session);
        MessageConsumer consumer = session.createConsumer(_queue);
        for (int i = 0; i < number; i++)
        {
            Message msg = consumer.receive(100);
            assertNotNull("Message " + i + " was not received", msg);
        }
    }
}
