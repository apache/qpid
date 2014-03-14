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
package org.apache.qpid.server.virtualhost;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.binding.BindingImpl;
import org.apache.qpid.server.exchange.AbstractExchange;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler;
import org.apache.qpid.server.store.JsonFileConfigStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.TestMemoryMessageStore;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;
import org.apache.qpid.util.FileUtils;
import org.codehaus.jackson.map.ObjectMapper;

public class StandardVirtualHostTest extends QpidTestCase
{
    private VirtualHostRegistry _virtualHostRegistry;
    private File _storeFolder;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _storeFolder = TestFileUtils.createTestDirectory(".tmp.store", true);
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            if (_virtualHostRegistry != null)
            {
                _virtualHostRegistry.close();
            }
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
            FileUtils.delete(_storeFolder, false);
        }

    }

    /**
     * Tests that custom routing keys for the queue specified in the configuration
     * file are correctly bound to the exchange (in addition to the queue name)
     */
    public void testSpecifyingCustomBindings() throws Exception
    {
        customBindingTestImpl(new String[]{"custom1","custom2"});
    }

    /**
     * Tests that a queue specified in the configuration file to be bound to a
     * specified(non-default) direct exchange is a correctly bound to the exchange
     * and the default exchange using the queue name.
     */
    public void testQueueSpecifiedInConfigurationIsBoundToDefaultExchange() throws Exception
    {
        customBindingTestImpl(new String[0]);
    }

    /**
     * Tests that specifying custom routing keys for a queue in the configuration file results in failure
     * to create the vhost (since this is illegal, only queue names are used with the default exchange)
     */
    public void testSpecifyingCustomBindingForDefaultExchangeThrowsException() throws Exception
    {
        final String queueName = getName();
        final String customBinding = "custom-binding";
        writeConfigFile(queueName, queueName, null, false, new String[]{customBinding});

        try
        {
            createVirtualHost(queueName);
            fail("virtualhost creation should have failed due to illegal configuration");
        }
        catch (IllegalConfigurationException e)
        {
            // pass
        }
    }

    public void testVirtualHostBecomesActive() throws Exception
    {
        writeConfigFile(getName(), getName(), getName() +".direct", false, new String[0]);
        VirtualHost vhost = createVirtualHost(getName());
        assertNotNull(vhost);
        assertEquals(State.ACTIVE, vhost.getState());
    }

    public void testVirtualHostHavingStoreSetAsTypeBecomesActive() throws Exception
    {
        String virtualHostName = getName();
        VirtualHost host = createVirtualHost(virtualHostName);
        assertNotNull(host);
        assertEquals(State.ACTIVE, host.getState());
    }

    public void testVirtualHostBecomesStoppedOnClose() throws Exception
    {
        writeConfigFile(getName(), getName(), getName() +".direct", false, new String[0]);
        VirtualHost vhost = createVirtualHost(getName());
        assertNotNull(vhost);
        assertEquals(State.ACTIVE, vhost.getState());
        vhost.close();
        assertEquals(State.STOPPED, vhost.getState());
        assertEquals(0, vhost.getHouseKeepingActiveCount());
    }

    public void testVirtualHostHavingStoreSetAsTypeBecomesStoppedOnClose() throws Exception
    {
        String virtualHostName = getName();
        VirtualHost host = createVirtualHost(virtualHostName);
        assertNotNull(host);
        assertEquals(State.ACTIVE, host.getState());
        host.close();
        assertEquals(State.STOPPED, host.getState());
        assertEquals(0, host.getHouseKeepingActiveCount());
    }

    /**
     * Tests that specifying an unknown exchange to bind the queue to results in failure to create the vhost
     */
    public void testSpecifyingUnknownExchangeThrowsException() throws Exception
    {
        final String queueName = getName();
        final String exchangeName = "made-up-exchange";
        writeConfigFile(queueName, queueName, exchangeName, true, new String[0]);

        try
        {
            createVirtualHost(queueName);
            fail("virtualhost creation should have failed due to illegal configuration");
        }
        catch (IllegalConfigurationException e)
        {
            // pass
        }
    }

    public void testBindingArguments() throws Exception
    {
        String exchangeName = getName() +".direct";
        String vhostName = getName();
        String queueName = getName();

        Map<String, String[]> bindingArguments = new HashMap<String, String[]>();
        bindingArguments.put("ping", new String[]{"x-filter-jms-selector=select=1", "x-qpid-no-local"});
        bindingArguments.put("pong", new String[]{"x-filter-jms-selector=select='pong'"});
        writeConfigFile(vhostName, queueName, exchangeName, false, new String[]{"ping","pong"}, bindingArguments);
        VirtualHost vhost = createVirtualHost(vhostName);

        ExchangeImpl exch = vhost.getExchange(getName() +".direct");
        Collection<BindingImpl> bindings = ((AbstractExchange)exch).getBindings();
        assertNotNull("Bindings cannot be null", bindings);
        assertEquals("Unexpected number of bindings", 3, bindings.size());

        boolean foundPong = false;
        boolean foundPing = false;
        for (BindingImpl binding : bindings)
        {
            String qn = binding.getAMQQueue().getName();
            assertEquals("Unexpected queue name", getName(), qn);
            Map<String, Object> arguments = binding.getArguments();

            if ("ping".equals(binding.getBindingKey()))
            {
                foundPing = true;
                assertEquals("Unexpected number of binding arguments for ping", 2, arguments.size());
                assertEquals("Unexpected x-filter-jms-selector for ping", "select=1", arguments.get("x-filter-jms-selector"));
                assertTrue("Unexpected x-qpid-no-local for ping", arguments.containsKey("x-qpid-no-local"));
            }
            else if ("pong".equals(binding.getBindingKey()))
            {
                foundPong = true;
                assertEquals("Unexpected number of binding arguments for pong", 1, arguments.size());
                assertEquals("Unexpected x-filter-jms-selector for pong", "select='pong'", arguments.get("x-filter-jms-selector"));
            }
        }

        assertTrue("Pong binding is not found", foundPong);
        assertTrue("Ping binding is not found", foundPing);
    }

    private void customBindingTestImpl(final String[] routingKeys) throws Exception
    {
        String exchangeName = getName() +".direct";
        String vhostName = getName();
        String queueName = getName();

        writeConfigFile(vhostName, queueName, exchangeName, false, routingKeys);
        VirtualHost vhost = createVirtualHost(vhostName);
        assertNotNull("virtualhost should exist", vhost);

        AMQQueue queue = vhost.getQueue(queueName);
        assertNotNull("queue should exist", queue);

        ExchangeImpl exch = vhost.getExchange(exchangeName);
        assertTrue("queue should have been bound to " + exchangeName + " with its name", exch.isBound(queueName, queue));

        for(String key: routingKeys)
        {
            assertTrue("queue should have been bound to " + exchangeName + " with key " + key, exch.isBound(key, queue));
        }
    }


    private VirtualHost createVirtualHost(String virtualHostName) throws Exception
    {
        Broker<?> broker = BrokerTestHelper.createBrokerMock();

        _virtualHostRegistry = broker.getVirtualHostRegistry();

        org.apache.qpid.server.model.VirtualHost<?> model = mock(org.apache.qpid.server.model.VirtualHost.class);
        when(model.getAttribute(org.apache.qpid.server.model.VirtualHost.CONFIG_STORE_TYPE)).thenReturn(JsonFileConfigStore.TYPE);
        when(model.getAttribute(org.apache.qpid.server.model.VirtualHost.CONFIG_STORE_PATH)).thenReturn(_storeFolder.getAbsolutePath());

        Map<String, Object> messageStoreSettings = new HashMap<String, Object>();
        messageStoreSettings.put(MessageStore.STORE_TYPE, TestableMemoryMessageStore.TYPE);
        when(model.getMessageStoreSettings()).thenReturn(messageStoreSettings);
        when(model.getName()).thenReturn(virtualHostName);

        VirtualHost host = new StandardVirtualHostFactory().createVirtualHost(_virtualHostRegistry, mock(StatisticsGatherer.class),
                new SecurityManager(broker, false), model);
        _virtualHostRegistry.registerVirtualHost(host);
        return host;
    }

    /**
     * Create a configuration file for testing virtualhost creation
     *
     * @param vhostName name of the virtualhost
     * @param queueName name of the queue
     * @param exchangeName name of a direct exchange to declare (unless dontDeclare = true) and bind the queue to (null = none)
     * @param dontDeclare if true then don't declare the exchange, even if its name is non-null
     * @param routingKeys routingKeys to bind the queue with (empty array = none)
     * @return
     * @throws Exception
     */
    private void writeConfigFile(String vhostName, String queueName, String exchangeName, boolean dontDeclare, String[] routingKeys) throws Exception
    {
        writeConfigFile(vhostName, queueName, exchangeName, dontDeclare, routingKeys, null);
    }

    private void writeConfigFile(String vhostName, String queueName, String exchangeName, boolean dontDeclare,
            String[] routingKeys, Map<String, String[]> bindingArguments) throws Exception
    {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("modelVersion", Model.MODEL_VERSION);
        data.put("configVersion", org.apache.qpid.server.model.VirtualHost.CURRENT_CONFIG_VERSION);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValue(new File(_storeFolder, vhostName + ".json"), data);

        JsonFileConfigStore store = new JsonFileConfigStore();
        org.apache.qpid.server.model.VirtualHost<?> virtualHost = mock(org.apache.qpid.server.model.VirtualHost.class);
        when(virtualHost.getName()).thenReturn(vhostName);
        when(virtualHost.getAttribute(org.apache.qpid.server.model.VirtualHost.CONFIG_STORE_PATH)).thenReturn(_storeFolder.getAbsolutePath());
        ConfigurationRecoveryHandler recoveryHandler = mock(ConfigurationRecoveryHandler.class);
        when(recoveryHandler.completeConfigurationRecovery()).thenReturn(org.apache.qpid.server.model.VirtualHost.CURRENT_CONFIG_VERSION);
        store.configureConfigStore(virtualHost , recoveryHandler );

        UUID exchangeId = UUIDGenerator.generateExchangeUUID(exchangeName == null? "amq.direct" : exchangeName, vhostName);
        if(exchangeName != null && !dontDeclare)
        {
            Map<String, Object> exchangeAttributes = new HashMap<String, Object>();
            exchangeAttributes.put(org.apache.qpid.server.model.Exchange.NAME, exchangeName);
            exchangeAttributes.put(org.apache.qpid.server.model.Exchange.TYPE, "direct");
            exchangeAttributes.put(org.apache.qpid.server.model.Exchange.DURABLE, true);
            store.create(exchangeId, org.apache.qpid.server.model.Exchange.class.getSimpleName(), exchangeAttributes);
        }

        UUID queueId = UUID.randomUUID();
        Map<String, Object> queueAttributes = new HashMap<String, Object>();
        queueAttributes.put(org.apache.qpid.server.model.Queue.NAME, queueName);
        queueAttributes.put(org.apache.qpid.server.model.Queue.DURABLE, true);
        store.create(queueId, org.apache.qpid.server.model.Queue.class.getSimpleName(), queueAttributes);

        Map<String, Object> bindingAttributes = new HashMap<String, Object>();
        bindingAttributes.put(org.apache.qpid.server.model.Binding.NAME, queueName);
        bindingAttributes.put(org.apache.qpid.server.model.Binding.QUEUE, queueId);
        bindingAttributes.put(org.apache.qpid.server.model.Binding.EXCHANGE, exchangeId );
        store.create(UUID.randomUUID(), org.apache.qpid.server.model.Binding.class.getSimpleName(), bindingAttributes);

        for (int i = 0; i < routingKeys.length; i++)
        {
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(org.apache.qpid.server.model.Binding.NAME, routingKeys[i]);
            attributes.put(org.apache.qpid.server.model.Binding.QUEUE, queueId);
            attributes.put(org.apache.qpid.server.model.Binding.EXCHANGE, exchangeId );
            if (bindingArguments != null && bindingArguments.containsKey(routingKeys[i]))
            {
                String[] args = (String[])bindingArguments.get(routingKeys[i]);
                Map<String, Object> arguments = new HashMap<String, Object>();
                for (int j = 0; j < args.length; j++)
                {
                    int pos = args[j].indexOf('=');
                    if (pos == -1)
                    {
                        arguments.put(args[j], null);
                    }
                    else
                    {
                        arguments.put(args[j].substring(0, pos), args[j].substring(pos + 1));
                    }
                }
                attributes.put(org.apache.qpid.server.model.Binding.ARGUMENTS, arguments );
            }
            store.create(UUID.randomUUID(), org.apache.qpid.server.model.Binding.class.getSimpleName(), attributes);
        }
        store.close();
    }

}
