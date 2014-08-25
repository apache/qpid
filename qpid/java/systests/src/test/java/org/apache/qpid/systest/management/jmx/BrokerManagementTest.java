/*
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
 */
package org.apache.qpid.systest.management.jmx;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.management.common.mbeans.ManagedExchange;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

/**
 * Tests the JMX API for the Managed Broker.
 *
 */
public class BrokerManagementTest extends QpidBrokerTestCase
{
    private static final String VIRTUAL_HOST = "test";

    /**
     * JMX helper.
     */
    private JMXTestUtils _jmxUtils;
    private ManagedBroker _managedBroker;

    public void setUp() throws Exception
    {
        getBrokerConfiguration().addJmxManagementConfiguration();

        _jmxUtils = new JMXTestUtils(this);

        super.setUp();
        _jmxUtils.open();
        _managedBroker = _jmxUtils.getManagedBroker(VIRTUAL_HOST);
    }

    public void tearDown() throws Exception
    {
        if (_jmxUtils != null)
        {
            _jmxUtils.close();
        }
        super.tearDown();
    }

    /**
     * Tests queue creation/deletion also verifying the automatic binding to the default exchange.
     */
    public void testCreateQueueAndDeletion() throws Exception
    {
        final String queueName = getTestQueueName();


        _managedBroker.createNewQueue(queueName, "testowner", true);

        // Ensure the queue exists
        assertNotNull("Queue object name expected to exist", _jmxUtils.getQueueObjectName(VIRTUAL_HOST, queueName));
        assertNotNull("Manager queue expected to be available", _jmxUtils.getManagedQueue(queueName));


        // Now delete the queue
        _managedBroker.deleteQueue(queueName);


    }

    /**
     * Tests exchange creation/deletion via JMX API.
     */
    public void testCreateExchangeAndUnregister() throws Exception
    {
        String exchangeName = getTestName();
        _managedBroker.createNewExchange(exchangeName, "topic", true);

        ManagedExchange exchange = _jmxUtils.getManagedExchange(exchangeName);
        assertNotNull("Exchange should exist", exchange);

        _managedBroker.unregisterExchange(exchangeName);
    }

    /**
     * Tests that it is disallowed to unregister the default exchange.
     */
    public void testUnregisterOfAmqDirectExchangeDisallowed() throws Exception
    {
        String amqDirectExchangeName = "amq.direct";

        ManagedExchange amqDirectExchange = _jmxUtils.getManagedExchange(amqDirectExchangeName);
        assertNotNull("Exchange should exist", amqDirectExchange);
        try
        {
            _managedBroker.unregisterExchange(amqDirectExchangeName);
            fail("Exception not thrown");
        }
        catch (UnsupportedOperationException e)
        {
            // PASS
            assertEquals("'"+amqDirectExchangeName+"' is a reserved exchange and can't be deleted", e.getMessage());
        }
        amqDirectExchange = _jmxUtils.getManagedExchange(amqDirectExchangeName);
        assertNotNull("Exchange should exist", amqDirectExchange);
    }

}
