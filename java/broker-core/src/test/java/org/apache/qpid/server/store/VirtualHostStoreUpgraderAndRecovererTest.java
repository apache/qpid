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
package org.apache.qpid.server.store;

import static java.util.Arrays.asList;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.SystemConfig;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;
import org.apache.qpid.server.virtualhost.TestMemoryVirtualHost;
import org.apache.qpid.server.virtualhostnode.TestVirtualHostNode;
import org.apache.qpid.test.utils.QpidTestCase;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.security.auth.Subject;

public class VirtualHostStoreUpgraderAndRecovererTest extends QpidTestCase
{
    private ConfiguredObjectRecord _hostRecord;
    private CurrentThreadTaskExecutor _taskExecutor;
    private UUID _hostId;
    private VirtualHostNode _virtualHostNode;
    private DurableConfigurationStore _durableConfigurationStore;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        UUID hostParentId = UUID.randomUUID();
        _hostId = UUID.randomUUID();
        Map<String, Object> hostAttributes = new HashMap<>();
        hostAttributes.put("modelVersion", "0.0");
        hostAttributes.put("name", "test");
        hostAttributes.put("type", TestMemoryVirtualHost.VIRTUAL_HOST_TYPE);

        _hostRecord = mock(ConfiguredObjectRecord.class);
        when(_hostRecord.getId()).thenReturn(_hostId);
        when(_hostRecord.getAttributes()).thenReturn(hostAttributes);
        when(_hostRecord.getType()).thenReturn("VirtualHost");
        when(_hostRecord.toString()).thenReturn("VirtualHost[name='test',id='" + _hostId + "']");

        _taskExecutor = new CurrentThreadTaskExecutor();
        _taskExecutor.start();

        SystemConfig<?> systemConfig = mock(SystemConfig.class);
        when(systemConfig.getEventLogger()).thenReturn(new EventLogger());

        Broker<?> broker = mock(Broker.class);
        when(broker.getParent(SystemConfig.class)).thenReturn(systemConfig);
        when(broker.getTaskExecutor()).thenReturn(_taskExecutor);
        when(broker.getModel()).thenReturn(BrokerModel.getInstance());

        _durableConfigurationStore = mock(DurableConfigurationStore.class);
        Map<String,Object> attributes = new HashMap<>();
        attributes.put(VirtualHostNode.ID, hostParentId);
        attributes.put(VirtualHostNode.NAME, "test");
        _virtualHostNode = new TestVirtualHostNode(broker, attributes, _durableConfigurationStore);
    }

    @Override
    public void tearDown()throws Exception
    {
        super.tearDown();
        _taskExecutor.stopImmediately();
    }

    public void testRecoverQueueWithDLQEnabled() throws Exception
    {
        ConfiguredObjectRecord queue = mockQueue("test", Collections.<String,Object>singletonMap("x-qpid-dlq-enabled", "true"));
        ConfiguredObjectRecord dlq = mockQueue("test_DLQ", Collections.<String,Object>singletonMap("x-qpid-dlq-enabled", "false"));
        ConfiguredObjectRecord dle = mockExchange("test_DLE", "fanout");
        ConfiguredObjectRecord dlqBinding = mockBinding("dlq", dlq, dle);
        ConfiguredObjectRecord directExchange = mock(ConfiguredObjectRecord.class);
        when(directExchange.getId()).thenReturn(UUIDGenerator.generateExchangeUUID("amq.direct", "test"));
        ConfiguredObjectRecord queueBinding =  mockBinding("test", queue, directExchange);
        setUpVisit(_hostRecord, queue, dlq, dle, queueBinding, dlqBinding);

        VirtualHostStoreUpgraderAndRecoverer upgraderAndRecoverer = new VirtualHostStoreUpgraderAndRecoverer(_virtualHostNode);
        upgraderAndRecoverer.perform(_durableConfigurationStore);

        final VirtualHost<?,?,?>  host = _virtualHostNode.getVirtualHost();
        Subject.doAs(org.apache.qpid.server.security.SecurityManager.getSubjectWithAddedSystemRights(), new PrivilegedAction<Void>()
                {
                    @Override
                    public Void run()
                    {
                        host.open();
                        return null;
                    }
                }
        );

        assertNotNull("Virtual host is not recovered", host);
        Queue<?> recoveredQueue = host.findConfiguredObject(Queue.class, "test");
        assertNotNull("Queue is not recovered", recoveredQueue);

        Queue<?> recoveredDLQ = host.findConfiguredObject(Queue.class, "test_DLQ");
        assertNotNull("DLQ queue is not recovered", recoveredDLQ);

        Exchange<?> recoveredDLE = host.findConfiguredObject(Exchange.class, "test_DLE");
        assertNotNull("DLE exchange is not recovered", recoveredDLE);

        assertEquals("Unexpected alternative exchange", recoveredDLE, recoveredQueue.getAlternateExchange());
    }

    public void testRecordUpdatedInOneUpgraderAndRemovedInAnotherUpgraderIsNotRecovered()
    {
        ConfiguredObjectRecord queue = mockQueue("test-queue", null);
        ConfiguredObjectRecord exchange = mockExchange("test-direct", "direct");
        ConfiguredObjectRecord queueBinding1 =  mockBinding("test-binding", queue, exchange);
        ConfiguredObjectRecord nonExistingExchange = mock(ConfiguredObjectRecord.class);

        // selector on non-topic exchange should be removed from binding arguments in upgrader 0.0->0.1
        // binding to non-existing exchange is removed in upgrader 0.1->0.2
        when(nonExistingExchange.getId()).thenReturn(UUIDGenerator.generateExchangeUUID("non-existing", "test"));
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("x-filter-jms-selector", "id=1");
        ConfiguredObjectRecord queueBinding2 =  mockBinding("test-non-existing", queue, nonExistingExchange, arguments);
        setUpVisit(_hostRecord, queue, exchange, queueBinding1, queueBinding2);

        VirtualHostStoreUpgraderAndRecoverer upgraderAndRecoverer = new VirtualHostStoreUpgraderAndRecoverer(_virtualHostNode);
        upgraderAndRecoverer.perform(_durableConfigurationStore);

        final VirtualHost<?,?,?>  host = _virtualHostNode.getVirtualHost();
        Subject.doAs(org.apache.qpid.server.security.SecurityManager.getSubjectWithAddedSystemRights(), new PrivilegedAction<Void>()
                {
                    @Override
                    public Void run()
                    {
                        host.open();
                        return null;
                    }
                }
        );

        assertNotNull("Virtual host is not recovered", host);
        Queue<?> recoveredQueue = host.findConfiguredObject(Queue.class, "test-queue");
        assertNotNull("Queue is not recovered", recoveredQueue);

        Exchange<?> recoveredExchange= host.findConfiguredObject(Exchange.class, "test-direct");
        assertNotNull("Exchange is not recovered", recoveredExchange);

        Binding<?> recoveredBinding1 = recoveredQueue.findConfiguredObject(Binding.class, "test-binding");
        assertNotNull("Correct binding is not recovered", recoveredBinding1);

        Binding<?> recoveredBinding2 = recoveredQueue.findConfiguredObject(Binding.class, "test-non-existing");
        assertNull("Incorrect binding is recovered", recoveredBinding2);
    }

    private ConfiguredObjectRecord mockBinding(String bindingName, ConfiguredObjectRecord queue, ConfiguredObjectRecord exchange)
    {
        return mockBinding(bindingName, queue, exchange, null);
    }

    private ConfiguredObjectRecord mockBinding(String bindingName, ConfiguredObjectRecord queue, ConfiguredObjectRecord exchange, Map<String, Object> arguments)
    {
        ConfiguredObjectRecord binding = mock(ConfiguredObjectRecord.class);
        when(binding.getId()).thenReturn(UUID.randomUUID());
        when(binding.getType()).thenReturn("org.apache.qpid.server.model.Binding");
        Map<String,UUID> parents = new HashMap<>();
        parents.put("Queue", queue.getId());
        parents.put("Exchange", exchange.getId());
        when(binding.getParents()).thenReturn(parents);
        when(binding.toString()).thenReturn("Binding[" + bindingName + "]");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("durable", true);
        attributes.put("name", bindingName);
        if (arguments != null)
        {
            attributes.put("arguments", arguments);
        }
        when(binding.getAttributes()).thenReturn(attributes);
        return binding;
    }

    private ConfiguredObjectRecord mockExchange(String exchangeName, String exchangeType)
    {
        ConfiguredObjectRecord exchange = mock(ConfiguredObjectRecord.class);
        when(exchange.getId()).thenReturn(UUID.randomUUID());
        when(exchange.getType()).thenReturn("org.apache.qpid.server.model.Exchange");
        when(exchange.getParents()).thenReturn(Collections.singletonMap("VirtualHost", _hostId));
        when(exchange.toString()).thenReturn("Exchange[" + exchangeName + "]");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("type", exchangeType);
        attributes.put("durable", true);
        attributes.put("name", exchangeName);
        when(exchange.getAttributes()).thenReturn(attributes);
        return exchange;
    }

    private ConfiguredObjectRecord mockQueue(String queueName, Map<String, Object> arguments)
    {
        ConfiguredObjectRecord queue = mock(ConfiguredObjectRecord.class);
        when(queue.getId()).thenReturn(UUID.randomUUID());
        when(queue.getType()).thenReturn("org.apache.qpid.server.model.Queue");
        when(queue.getParents()).thenReturn(Collections.singletonMap("VirtualHost", _hostId));
        when(queue.toString()).thenReturn("Queue[" + queueName + "]");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("durable", true);
        attributes.put("name", queueName);
        if (arguments != null)
        {
            attributes.put("arguments", arguments);
        }
        when(queue.getAttributes()).thenReturn(attributes);
        return queue;
    }


    private void setUpVisit(final ConfiguredObjectRecord... records)
    {
        doAnswer(new Answer()
        {
            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable
            {
                Iterator<ConfiguredObjectRecord> iterator = asList(records).iterator();
                ConfiguredObjectRecordHandler handler = (ConfiguredObjectRecordHandler) invocation.getArguments()[0];
                handler.begin();
                boolean handlerContinue = true;
                while(iterator.hasNext() && handlerContinue)
                {
                    handlerContinue = handler.handle(iterator.next());
                }
                handler.end();
                return null;
            }
        }).when(_durableConfigurationStore).visitConfiguredObjectRecords(any(ConfiguredObjectRecordHandler.class));
    }
}
