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
package org.apache.qpid.management.jmx;

import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.ObjectName;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.management.common.mbeans.ManagedExchange;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

/**
 * Tests the JMX API for the Managed Broker.
 *
 */
public class ManagedBrokerMBeanTest extends QpidBrokerTestCase
{
    /**
     * Test virtual host
     */
    private static final String VIRTUAL_HOST = "test";

    /**
     * Test exchange type
     */
    private static final String EXCHANGE_TYPE = "topic";

    /**
     * JMX helper.
     */
    private JMXTestUtils _jmxUtils;
    private ManagedBroker _managedBroker;

    public void setUp() throws Exception
    {
        _jmxUtils = new JMXTestUtils(this);
        _jmxUtils.setUp();
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
        final ManagedExchange defaultExchange = _jmxUtils.getManagedExchange(ExchangeDefaults.DEFAULT_EXCHANGE_NAME.asString());

        // Check that bind does not exist before queue creation
        assertFalse("Binding to " + queueName + " should not exist in default exchange before queue creation",
                     defaultExchange.bindings().containsKey(new String[] {queueName}));

        _managedBroker.createNewQueue(queueName, "testowner", true);

        // Ensure the queue exists
        assertNotNull("Queue object name expected to exist", _jmxUtils.getQueueObjectName("test", queueName));
        assertNotNull("Manager queue expected to be available", _jmxUtils.getManagedQueue(queueName));

        // Now verify that the default exchange has been bound.
        assertTrue("Binding to " + queueName + " should exist in default exchange after queue creation",
                     defaultExchange.bindings().containsKey(new String[] {queueName}));

        // Now delete the queue
        _managedBroker.deleteQueue(queueName);

        // Finally ensure that the binding has been removed.
        assertFalse("Binding to " + queueName + " should not exist in default exchange after queue deletion",
                defaultExchange.bindings().containsKey(new String[] {queueName}));
    }

    /**
     * Tests exchange creation/deletion via JMX API.
     */
    public void testCreateExchangeAndUnregister() throws Exception
    {
        String exchangeName = getTestName();
        _managedBroker.createNewExchange(exchangeName, "topic", true);
        String queryString = "org.apache.qpid:type=VirtualHost.Exchange,VirtualHost="
                + ObjectName.quote(VIRTUAL_HOST) + ",name=" + ObjectName.quote(exchangeName) + ",ExchangeType="
                + EXCHANGE_TYPE;
        ManagedExchange exchange = _jmxUtils.getManagedObject(ManagedExchange.class, queryString);
        assertNotNull("Exchange should exist", exchange);

        _managedBroker.unregisterExchange(exchangeName);
        assertFalse("Exchange should have been removed", _jmxUtils.isManagedObjectExist(queryString));
    }

    /**
     * Tests that it is disallowed to unregister the default exchange.
     */
    public void testUnregisterOfDefaultExchangeDisallowed() throws Exception
    {
        String defaultExchangeName = ExchangeDefaults.DEFAULT_EXCHANGE_NAME.asString();

        try
        {
            _managedBroker.unregisterExchange(defaultExchangeName);
            fail("Exception not thrown");
        }
        catch (MBeanException mbe)
        {
            // PASS
            assertEquals("Error in unregistering exchange " + defaultExchangeName, mbe.getMessage());
            assertTrue(mbe.getCause().getMessage().contains("Cannot unregister the default exchange"));
        }
        final ManagedExchange defaultExchange = _jmxUtils.getManagedExchange(defaultExchangeName);
        assertNotNull("Exchange should exist", defaultExchange);
    }
}
