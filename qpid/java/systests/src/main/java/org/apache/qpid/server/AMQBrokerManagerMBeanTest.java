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
package org.apache.qpid.server;

import junit.framework.TestCase;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.management.ManagedBroker;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.client.transport.TransportConnection;

public class AMQBrokerManagerMBeanTest extends TestCase
{
    private QueueRegistry _queueRegistry;
    private ExchangeRegistry _exchangeRegistry;

    public void testExchangeOperations() throws Exception
    {
        String exchange1 = "testExchange1_" + System.currentTimeMillis();
        String exchange2 = "testExchange2_" + System.currentTimeMillis();
        String exchange3 = "testExchange3_" + System.currentTimeMillis();

        assertTrue(_exchangeRegistry.getExchange(new AMQShortString(exchange1)) == null);
        assertTrue(_exchangeRegistry.getExchange(new AMQShortString(exchange2)) == null);
        assertTrue(_exchangeRegistry.getExchange(new AMQShortString(exchange3)) == null);

        VirtualHost vHost = ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost("test");

        ManagedBroker mbean = new AMQBrokerManagerMBean((VirtualHost.VirtualHostMBean) vHost.getManagedObject());
        mbean.createNewExchange(exchange1, "direct", false);
        mbean.createNewExchange(exchange2, "topic", false);
        mbean.createNewExchange(exchange3, "headers", false);

        assertTrue(_exchangeRegistry.getExchange(new AMQShortString(exchange1)) != null);
        assertTrue(_exchangeRegistry.getExchange(new AMQShortString(exchange2)) != null);
        assertTrue(_exchangeRegistry.getExchange(new AMQShortString(exchange3)) != null);

        mbean.unregisterExchange(exchange1);
        mbean.unregisterExchange(exchange2);
        mbean.unregisterExchange(exchange3);

        assertTrue(_exchangeRegistry.getExchange(new AMQShortString(exchange1)) == null);
        assertTrue(_exchangeRegistry.getExchange(new AMQShortString(exchange2)) == null);
        assertTrue(_exchangeRegistry.getExchange(new AMQShortString(exchange3)) == null);
    }

    public void testQueueOperations() throws Exception
    {
        String queueName = "testQueue_" + System.currentTimeMillis();
        VirtualHost vHost = ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost("test");

        ManagedBroker mbean = new AMQBrokerManagerMBean((VirtualHost.VirtualHostMBean) vHost.getManagedObject());

        assertTrue(_queueRegistry.getQueue(new AMQShortString(queueName)) == null);

        mbean.createNewQueue(queueName, "test", false);
        assertTrue(_queueRegistry.getQueue(new AMQShortString(queueName)) != null);

        mbean.deleteQueue(queueName);
        assertTrue(_queueRegistry.getQueue(new AMQShortString(queueName)) == null);
    }

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        IApplicationRegistry appRegistry = ApplicationRegistry.getInstance();
        _queueRegistry = appRegistry.getVirtualHostRegistry().getVirtualHost("test").getQueueRegistry();
        _exchangeRegistry = appRegistry.getVirtualHostRegistry().getVirtualHost("test").getExchangeRegistry();
    }
}
