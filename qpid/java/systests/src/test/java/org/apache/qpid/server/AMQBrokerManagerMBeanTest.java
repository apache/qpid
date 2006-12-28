/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server;

import junit.framework.TestCase;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.management.ManagedBroker;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;

public class AMQBrokerManagerMBeanTest extends TestCase
{
    private QueueRegistry _queueRegistry;
    private ExchangeRegistry _exchangeRegistry;

    public void testExchangeOperations() throws Exception
    {
        assertTrue(_exchangeRegistry.getExchange("testExchange1") == null);
        assertTrue(_exchangeRegistry.getExchange("testExchange2") == null);
        assertTrue(_exchangeRegistry.getExchange("testExchange3") == null);

        ManagedBroker mbean = new AMQBrokerManagerMBean();
        mbean.createNewExchange("testExchange1","direct",false, false);
        mbean.createNewExchange("testExchange2","topic",false, false);
        mbean.createNewExchange("testExchange3","headers",false, false);

        assertTrue(_exchangeRegistry.getExchange("testExchange1") != null);
        assertTrue(_exchangeRegistry.getExchange("testExchange2") != null);
        assertTrue(_exchangeRegistry.getExchange("testExchange3") != null);

        mbean.unregisterExchange("testExchange1");
        mbean.unregisterExchange("testExchange2");
        mbean.unregisterExchange("testExchange3");

        assertTrue(_exchangeRegistry.getExchange("testExchange1") == null);
        assertTrue(_exchangeRegistry.getExchange("testExchange2") == null);
        assertTrue(_exchangeRegistry.getExchange("testExchange3") == null);
    }

    public void testQueueOperations() throws Exception
    {
        String queueName = "testQueue";
        ManagedBroker mbean = new AMQBrokerManagerMBean();

        assertTrue(_queueRegistry.getQueue(queueName) == null);
                
        mbean.createNewQueue(queueName, false, "test", true);
        assertTrue(_queueRegistry.getQueue(queueName) != null);

        mbean.deleteQueue(queueName);
        assertTrue(_queueRegistry.getQueue(queueName) == null);
    }

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        IApplicationRegistry appRegistry = ApplicationRegistry.getInstance();
        _queueRegistry    = appRegistry.getQueueRegistry();
        _exchangeRegistry = appRegistry.getExchangeRegistry();
    }
}
