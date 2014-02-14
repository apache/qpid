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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.qpid.server.store.AMQStoreException;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.exchange.DirectExchange;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.exchange.HeadersExchange;
import org.apache.qpid.server.exchange.TopicExchange;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueFactory;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.DurableConfigurationRecoverer;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.DurableConfiguredObjectRecoverer;
import org.apache.qpid.test.utils.QpidTestCase;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.apache.qpid.server.model.VirtualHost.CURRENT_CONFIG_VERSION;

public class DurableConfigurationRecovererTest extends QpidTestCase
{
    private static final UUID QUEUE_ID = new UUID(0,0);
    private static final UUID TOPIC_EXCHANGE_ID = new UUID(0,1);
    private static final UUID DIRECT_EXCHANGE_ID = new UUID(0,2);
    private static final String CUSTOM_EXCHANGE_NAME = "customExchange";

    private DurableConfigurationRecoverer _durableConfigurationRecoverer;
    private Exchange _directExchange;
    private Exchange _topicExchange;
    private VirtualHost _vhost;
    private DurableConfigurationStore _store;
    private ExchangeFactory _exchangeFactory;
    private ExchangeRegistry _exchangeRegistry;
    private QueueFactory _queueFactory;

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

        _exchangeRegistry = mock(ExchangeRegistry.class);
        when(_exchangeRegistry.getExchange(eq(DIRECT_EXCHANGE_ID))).thenReturn(_directExchange);
        when(_exchangeRegistry.getExchange(eq(TOPIC_EXCHANGE_ID))).thenReturn(_topicExchange);

        when(_vhost.getQueue(eq(QUEUE_ID))).thenReturn(queue);

        final ArgumentCaptor<Exchange> registeredExchange = ArgumentCaptor.forClass(Exchange.class);
        doAnswer(new Answer()
        {

            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable
            {
                Exchange exchange = registeredExchange.getValue();
                when(_exchangeRegistry.getExchange(eq(exchange.getId()))).thenReturn(exchange);
                when(_exchangeRegistry.getExchange(eq(exchange.getName()))).thenReturn(exchange);
                return null;
            }
        }).when(_exchangeRegistry).registerExchange(registeredExchange.capture());



        final ArgumentCaptor<UUID> idArg = ArgumentCaptor.forClass(UUID.class);
        final ArgumentCaptor<String> queueArg = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Map> argsArg = ArgumentCaptor.forClass(Map.class);

        _queueFactory = mock(QueueFactory.class);

        when(_queueFactory.restoreQueue(idArg.capture(), queueArg.capture(),
                anyString(), anyBoolean(), anyBoolean(), anyBoolean(), argsArg.capture())).then(
                new Answer()
                {

                    @Override
                    public Object answer(final InvocationOnMock invocation) throws Throwable
                    {
                        final AMQQueue queue = mock(AMQQueue.class);

                        final String queueName = queueArg.getValue();
                        final UUID queueId = idArg.getValue();

                        when(queue.getName()).thenReturn(queueName);
                        when(queue.getId()).thenReturn(queueId);
                        when(_vhost.getQueue(eq(queueName))).thenReturn(queue);
                        when(_vhost.getQueue(eq(queueId))).thenReturn(queue);

                        final ArgumentCaptor<Exchange> altExchangeArg = ArgumentCaptor.forClass(Exchange.class);
                        doAnswer(
                                new Answer()
                                {
                                    @Override
                                    public Object answer(InvocationOnMock invocation) throws Throwable
                                    {
                                        final Exchange value = altExchangeArg.getValue();
                                        when(queue.getAlternateExchange()).thenReturn(value);
                                        return null;
                                    }
                                }
                        ).when(queue).setAlternateExchange(altExchangeArg.capture());

                        Map args = argsArg.getValue();
                        if(args.containsKey(Queue.ALTERNATE_EXCHANGE))
                        {
                            final UUID exchangeId = UUID.fromString(args.get(Queue.ALTERNATE_EXCHANGE).toString());
                            final Exchange exchange = _exchangeRegistry.getExchange(exchangeId);
                            queue.setAlternateExchange(exchange);
                        }
                        return queue;
                    }
                });

        _exchangeFactory = mock(ExchangeFactory.class);


        DurableConfiguredObjectRecoverer[] recoverers = {
                new QueueRecoverer(_vhost, _exchangeRegistry, _queueFactory),
                new ExchangeRecoverer(_exchangeRegistry, _exchangeFactory),
                new BindingRecoverer(_vhost, _exchangeRegistry)
        };

        final Map<String, DurableConfiguredObjectRecoverer> recovererMap= new HashMap<String, DurableConfiguredObjectRecoverer>();
        for(DurableConfiguredObjectRecoverer recoverer : recoverers)
        {
            recovererMap.put(recoverer.getType(), recoverer);
        }
        _durableConfigurationRecoverer =
                new DurableConfigurationRecoverer(_vhost.getName(), recovererMap,
                                                  new DefaultUpgraderProvider(_vhost, _exchangeRegistry));

        _store = mock(DurableConfigurationStore.class);

        CurrentActor.set(mock(LogActor.class));
    }

    public void testUpgradeEmptyStore() throws Exception
    {
        _durableConfigurationRecoverer.beginConfigurationRecovery(_store, 0);
        assertEquals("Did not upgrade to the expected version",
                     CURRENT_CONFIG_VERSION,
                     _durableConfigurationRecoverer.completeConfigurationRecovery());
    }

    public void testUpgradeNewerStoreFails() throws Exception
    {
        try
        {
            _durableConfigurationRecoverer.beginConfigurationRecovery(_store, CURRENT_CONFIG_VERSION + 1);
            _durableConfigurationRecoverer.completeConfigurationRecovery();
            fail("Should not be able to start when config model is newer than current");
        }
        catch (IllegalStateException e)
        {
            // pass
        }
    }

    public void testUpgradeRemovesBindingsToNonTopicExchanges() throws Exception
    {

        _durableConfigurationRecoverer.beginConfigurationRecovery(_store, 0);

        _durableConfigurationRecoverer.configuredObject(new UUID(1, 0),
                                                           "org.apache.qpid.server.model.Binding",
                                                           createBinding("key",
                                                                         DIRECT_EXCHANGE_ID,
                                                                         QUEUE_ID,
                                                                         "x-filter-jms-selector",
                                                                         "wibble"));

        final ConfiguredObjectRecord[] expected = {
                new ConfiguredObjectRecord(new UUID(1, 0), "Binding",
                        createBinding("key", DIRECT_EXCHANGE_ID, QUEUE_ID))
        };

        verifyCorrectUpdates(expected);

        _durableConfigurationRecoverer.completeConfigurationRecovery();
    }



    public void testUpgradeOnlyRemovesSelectorBindings() throws Exception
    {

        _durableConfigurationRecoverer.beginConfigurationRecovery(_store, 0);

        _durableConfigurationRecoverer.configuredObject(new UUID(1, 0),
                                                           "org.apache.qpid.server.model.Binding",
                                                           createBinding("key",
                                                                         DIRECT_EXCHANGE_ID,
                                                                         QUEUE_ID,
                                                                         "x-filter-jms-selector",
                                                                         "wibble",
                                                                         "not-a-selector",
                                                                         "moo"));


        final UUID customExchangeId = new UUID(3,0);

        _durableConfigurationRecoverer.configuredObject(new UUID(2, 0),
                                                           "org.apache.qpid.server.model.Binding",
                                                           createBinding("key",
                                                                         customExchangeId,
                                                                         QUEUE_ID,
                                                                         "x-filter-jms-selector",
                                                                         "wibble",
                                                                         "not-a-selector",
                                                                         "moo"));

        _durableConfigurationRecoverer.configuredObject(customExchangeId,
                                                           "org.apache.qpid.server.model.Exchange",
                                                           createExchange(CUSTOM_EXCHANGE_NAME, HeadersExchange.TYPE));

        final Exchange customExchange = mock(Exchange.class);

        when(_exchangeFactory.restoreExchange(eq(customExchangeId),
                                             eq(CUSTOM_EXCHANGE_NAME),
                                             eq(HeadersExchange.TYPE.getType()),
                                             anyBoolean())).thenReturn(customExchange);

        final ConfiguredObjectRecord[] expected = {
                new ConfiguredObjectRecord(new UUID(1, 0), "org.apache.qpid.server.model.Binding",
                        createBinding("key", DIRECT_EXCHANGE_ID, QUEUE_ID, "not-a-selector", "moo")),
                new ConfiguredObjectRecord(new UUID(2, 0), "org.apache.qpid.server.model.Binding",
                        createBinding("key", customExchangeId, QUEUE_ID, "not-a-selector", "moo"))
        };

        verifyCorrectUpdates(expected);

        _durableConfigurationRecoverer.completeConfigurationRecovery();
    }


    public void testUpgradeKeepsBindingsToTopicExchanges() throws Exception
    {

        _durableConfigurationRecoverer.beginConfigurationRecovery(_store, 0);

        _durableConfigurationRecoverer.configuredObject(new UUID(1, 0),
                                                           "org.apache.qpid.server.model.Binding",
                                                           createBinding("key",
                                                                         TOPIC_EXCHANGE_ID,
                                                                         QUEUE_ID,
                                                                         "x-filter-jms-selector",
                                                                         "wibble"));

        final ConfiguredObjectRecord[] expected = {
                new ConfiguredObjectRecord(new UUID(1, 0), "Binding",
                        createBinding("key", TOPIC_EXCHANGE_ID, QUEUE_ID, "x-filter-jms-selector", "wibble"))
        };

        verifyCorrectUpdates(expected);

        _durableConfigurationRecoverer.completeConfigurationRecovery();
    }

    public void testUpgradeDoesNotRecur() throws Exception
    {

        _durableConfigurationRecoverer.beginConfigurationRecovery(_store, 2);

        _durableConfigurationRecoverer.configuredObject(new UUID(1, 0),
                                                           "Binding",
                                                           createBinding("key",
                                                                         DIRECT_EXCHANGE_ID,
                                                                         QUEUE_ID,
                                                                         "x-filter-jms-selector",
                                                                         "wibble"));

        doThrow(new RuntimeException("Update Should not be called")).when(_store).update(any(ConfiguredObjectRecord[].class));

        _durableConfigurationRecoverer.completeConfigurationRecovery();
    }

    public void testFailsWithUnresolvedObjects()
    {
        _durableConfigurationRecoverer.beginConfigurationRecovery(_store, 2);


        _durableConfigurationRecoverer.configuredObject(new UUID(1, 0),
                                                        "Binding",
                                                        createBinding("key",
                                                                      new UUID(3,0),
                                                                      QUEUE_ID,
                                                                      "x-filter-jms-selector",
                                                                      "wibble"));

        try
        {
            _durableConfigurationRecoverer.completeConfigurationRecovery();
            fail("Expected resolution to fail due to unknown object");
        }
        catch(IllegalConfigurationException e)
        {
            assertEquals("Durable configuration has unresolved dependencies", e.getMessage());
        }

    }

    public void testFailsWithUnknownObjectType()
    {
        _durableConfigurationRecoverer.beginConfigurationRecovery(_store, 2);


        try
        {
            final Map<String, Object> emptyArguments = Collections.emptyMap();
            _durableConfigurationRecoverer.configuredObject(new UUID(1, 0),
                                                            "Wibble", emptyArguments);
            _durableConfigurationRecoverer.completeConfigurationRecovery();
            fail("Expected resolution to fail due to unknown object type");
        }
        catch(IllegalConfigurationException e)
        {
            assertEquals("Unknown type for configured object: Wibble", e.getMessage());
        }


    }

    public void testRecoveryOfQueueAlternateExchange() throws Exception
    {

        final UUID queueId = new UUID(1, 0);
        final UUID exchangeId = new UUID(2, 0);

        final Exchange customExchange = mock(Exchange.class);

        when(customExchange.getId()).thenReturn(exchangeId);
        when(customExchange.getName()).thenReturn(CUSTOM_EXCHANGE_NAME);

        when(_exchangeFactory.restoreExchange(eq(exchangeId),
                                             eq(CUSTOM_EXCHANGE_NAME),
                                             eq(HeadersExchange.TYPE.getType()),
                                             anyBoolean())).thenReturn(customExchange);

        _durableConfigurationRecoverer.beginConfigurationRecovery(_store, 2);

        _durableConfigurationRecoverer.configuredObject(queueId, Queue.class.getSimpleName(),
                                                        createQueue("testQueue", exchangeId));
        _durableConfigurationRecoverer.configuredObject(exchangeId,
                                                        org.apache.qpid.server.model.Exchange.class.getSimpleName(),
                                                        createExchange(CUSTOM_EXCHANGE_NAME, HeadersExchange.TYPE));

        _durableConfigurationRecoverer.completeConfigurationRecovery();

        assertEquals(customExchange, _vhost.getQueue(queueId).getAlternateExchange());
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


    private Map<String, Object> createQueue(String name, UUID alternateExchangeId)
    {
        Map<String, Object> queue = new LinkedHashMap<String, Object>();

        queue.put(Queue.NAME, name);
        if(alternateExchangeId != null)
        {
            queue.put(Queue.ALTERNATE_EXCHANGE, alternateExchangeId.toString());
        }
        queue.put(Queue.EXCLUSIVE, false);

        return queue;

    }

}
