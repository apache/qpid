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

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.exchange.DirectExchange;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.exchange.HeadersExchange;
import org.apache.qpid.server.exchange.TopicExchange;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.test.utils.QpidTestCase;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.apache.qpid.server.model.VirtualHost.CURRENT_CONFIG_VERSION;

public class VirtualHostConfigRecoveryHandlerTest extends QpidTestCase
{
    private Exchange _directExchange;
    private Exchange _topicExchange;
    private VirtualHost _vhost;
    private VirtualHostConfigRecoveryHandler _virtualHostConfigRecoveryHandler;
    private DurableConfigurationStore _store;

    private static final UUID QUEUE_ID = new UUID(0,0);
    private static final UUID TOPIC_EXCHANGE_ID = new UUID(0,1);
    private static final UUID DIRECT_EXCHANGE_ID = new UUID(0,2);

    @Override
    public void setUp() throws Exception
    {
        super.setUp();


        _directExchange = mock(Exchange.class);
        when(_directExchange.getType()).thenReturn(DirectExchange.TYPE);


        _topicExchange = mock(Exchange.class);
        when(_topicExchange.getType()).thenReturn(TopicExchange.TYPE);

        AMQQueue queue = mock(AMQQueue.class);

        _vhost = mock(VirtualHost.class);

        ExchangeRegistry exchangeRegistry = mock(ExchangeRegistry.class);
        when(exchangeRegistry.getExchange(eq(DIRECT_EXCHANGE_ID))).thenReturn(_directExchange);
        when(exchangeRegistry.getExchange(eq(TOPIC_EXCHANGE_ID))).thenReturn(_topicExchange);

        QueueRegistry queueRegistry = mock(QueueRegistry.class);
        when(_vhost.getQueueRegistry()).thenReturn(queueRegistry);

        when(queueRegistry.getQueue(eq(QUEUE_ID))).thenReturn(queue);

        ExchangeFactory exchangeFactory = mock(ExchangeFactory.class);
        _virtualHostConfigRecoveryHandler = new VirtualHostConfigRecoveryHandler(_vhost, exchangeRegistry, exchangeFactory);

        _store = mock(DurableConfigurationStore.class);

        CurrentActor.set(mock(LogActor.class));
    }

    public void testUpgradeEmptyStore() throws Exception
    {
        _virtualHostConfigRecoveryHandler.beginConfigurationRecovery(_store, 0);
        assertEquals("Did not upgrade to the expected version", CURRENT_CONFIG_VERSION, _virtualHostConfigRecoveryHandler.completeConfigurationRecovery());
    }

    public void testUpgradeNewerStoreFails() throws Exception
    {
        try
        {
            _virtualHostConfigRecoveryHandler.beginConfigurationRecovery(_store, CURRENT_CONFIG_VERSION+1);
            _virtualHostConfigRecoveryHandler.completeConfigurationRecovery();
            fail("Should not be able to start when config model is newer than current");
        }
        catch (IllegalStateException e)
        {
            // pass
        }
    }

    public void testUpgradeRemovesBindingsToNonTopicExchanges() throws Exception
    {

        _virtualHostConfigRecoveryHandler.beginConfigurationRecovery(_store, 0);

        _virtualHostConfigRecoveryHandler.configuredObject(new UUID(1, 0),
                "org.apache.qpid.server.model.Binding",
                createBinding("key", DIRECT_EXCHANGE_ID, QUEUE_ID, "x-filter-jms-selector", "wibble"));

        final ConfiguredObjectRecord[] expected = {
                new ConfiguredObjectRecord(new UUID(1, 0), "org.apache.qpid.server.model.Binding",
                        createBinding("key", DIRECT_EXCHANGE_ID, QUEUE_ID))
        };

        verifyCorrectUpdates(expected);

        _virtualHostConfigRecoveryHandler.completeConfigurationRecovery();
    }



    public void testUpgradeOnlyRemovesSelectorBindings() throws Exception
    {

        _virtualHostConfigRecoveryHandler.beginConfigurationRecovery(_store, 0);

        _virtualHostConfigRecoveryHandler.configuredObject(new UUID(1, 0),
                "org.apache.qpid.server.model.Binding",
                createBinding("key", DIRECT_EXCHANGE_ID, QUEUE_ID, "x-filter-jms-selector", "wibble", "not-a-selector", "moo"));


        UUID customExchangeId = new UUID(3,0);

        _virtualHostConfigRecoveryHandler.configuredObject(new UUID(2, 0),
                "org.apache.qpid.server.model.Binding",
                createBinding("key", customExchangeId, QUEUE_ID, "x-filter-jms-selector", "wibble", "not-a-selector", "moo"));

        _virtualHostConfigRecoveryHandler.configuredObject(customExchangeId,
                "org.apache.qpid.server.model.Exchange",
                createExchange("customExchange", HeadersExchange.TYPE));



        final ConfiguredObjectRecord[] expected = {
                new ConfiguredObjectRecord(new UUID(1, 0), "org.apache.qpid.server.model.Binding",
                        createBinding("key", DIRECT_EXCHANGE_ID, QUEUE_ID, "not-a-selector", "moo")),
                new ConfiguredObjectRecord(new UUID(3, 0), "org.apache.qpid.server.model.Binding",
                        createBinding("key", customExchangeId, QUEUE_ID, "not-a-selector", "moo"))
        };

        verifyCorrectUpdates(expected);

        _virtualHostConfigRecoveryHandler.completeConfigurationRecovery();
    }


    public void testUpgradeKeepsBindingsToTopicExchanges() throws Exception
    {

        _virtualHostConfigRecoveryHandler.beginConfigurationRecovery(_store, 0);

        _virtualHostConfigRecoveryHandler.configuredObject(new UUID(1, 0),
                "org.apache.qpid.server.model.Binding",
                createBinding("key", TOPIC_EXCHANGE_ID, QUEUE_ID, "x-filter-jms-selector", "wibble"));

        final ConfiguredObjectRecord[] expected = {
                new ConfiguredObjectRecord(new UUID(1, 0), "org.apache.qpid.server.model.Binding",
                        createBinding("key", TOPIC_EXCHANGE_ID, QUEUE_ID, "x-filter-jms-selector", "wibble"))
        };

        verifyCorrectUpdates(expected);

        _virtualHostConfigRecoveryHandler.completeConfigurationRecovery();
    }

    public void testUpgradeDoesNotRecur() throws Exception
    {

        _virtualHostConfigRecoveryHandler.beginConfigurationRecovery(_store, 1);

        _virtualHostConfigRecoveryHandler.configuredObject(new UUID(1, 0),
                "org.apache.qpid.server.model.Binding",
                createBinding("key", DIRECT_EXCHANGE_ID, QUEUE_ID, "x-filter-jms-selector", "wibble"));

        doThrow(new RuntimeException("Update Should not be called")).when(_store).update(any(ConfiguredObjectRecord[].class));

        _virtualHostConfigRecoveryHandler.completeConfigurationRecovery();
    }

    private void verifyCorrectUpdates(final ConfiguredObjectRecord[] expected) throws AMQStoreException
    {
        doAnswer(new Answer()
        {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable
            {
                Object[] args = invocation.getArguments();
                assertEquals("Updated records are not as expected", new HashSet(Arrays.asList(
                        expected)), new HashSet(Arrays.asList(args)));

                return null;
            }
        }).when(_store).update(any(ConfiguredObjectRecord[].class));
    }

    private Map<String,Object> createBinding(String bindingKey, UUID exchangeId, UUID queueId, String... args)
    {
        Map<String, Object> binding = new LinkedHashMap<String, Object>();

        binding.put("name", bindingKey);
        binding.put(Binding.EXCHANGE, exchangeId.toString());
        binding.put(Binding.QUEUE, queueId.toString());
        Map<String,String> argumentMap = new LinkedHashMap<String, String>();
        if(args != null && args.length != 0)
        {
            String key = null;
            for(String arg : args)
            {
                if(key == null)
                {
                    key = arg;
                }
                else
                {
                    argumentMap.put(key, arg);
                    key = null;
                }
            }
        }
        binding.put(Binding.ARGUMENTS, argumentMap);
        return binding;
    }


    private Map<String, Object> createExchange(String name, ExchangeType<HeadersExchange> type)
    {
        Map<String, Object> exchange = new LinkedHashMap<String, Object>();

        exchange.put(org.apache.qpid.server.model.Exchange.NAME, name);
        exchange.put(org.apache.qpid.server.model.Exchange.TYPE, type.getType());

        return exchange;

    }
}
