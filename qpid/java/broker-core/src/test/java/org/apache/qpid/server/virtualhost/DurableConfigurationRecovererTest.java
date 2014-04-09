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

import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.store.ConfiguredObjectRecordImpl;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.exchange.AMQUnknownExchangeType;
import org.apache.qpid.server.exchange.DirectExchange;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.exchange.FanoutExchange;
import org.apache.qpid.server.exchange.HeadersExchange;
import org.apache.qpid.server.exchange.TopicExchange;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueFactory;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.DurableConfigurationRecoverer;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.DurableConfiguredObjectRecoverer;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.test.utils.QpidTestCase;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.apache.qpid.server.model.VirtualHost.CURRENT_CONFIG_VERSION;

public class DurableConfigurationRecovererTest extends QpidTestCase
{
    private static final String VIRTUAL_HOST_NAME = "test";
    private static final UUID QUEUE_ID = new UUID(0,0);
    private static final UUID TOPIC_EXCHANGE_ID = UUIDGenerator.generateExchangeUUID(TopicExchange.TYPE.getDefaultExchangeName(), VIRTUAL_HOST_NAME);
    private static final UUID DIRECT_EXCHANGE_ID = UUIDGenerator.generateExchangeUUID(DirectExchange.TYPE.getDefaultExchangeName(), VIRTUAL_HOST_NAME);
    private static final String CUSTOM_EXCHANGE_NAME = "customExchange";

    private DurableConfigurationRecoverer _durableConfigurationRecoverer;
    private ExchangeImpl<?> _directExchange;
    private ExchangeImpl<?> _topicExchange;
    private ExchangeImpl<?> _matchExchange;
    private ExchangeImpl<?> _fanoutExchange;
    private VirtualHostImpl _vhost;
    private DurableConfigurationStore _store;
    private ExchangeFactory _exchangeFactory;
    private ExchangeRegistry _exchangeRegistry;
    private QueueFactory _queueFactory;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        _exchangeFactory = mock(ExchangeFactory.class);

        _directExchange = createAndRegisterDefaultExchangeWithFactory(DirectExchange.TYPE);
        _topicExchange = createAndRegisterDefaultExchangeWithFactory(TopicExchange.TYPE);
        _matchExchange = createAndRegisterDefaultExchangeWithFactory(HeadersExchange.TYPE);
        _fanoutExchange = createAndRegisterDefaultExchangeWithFactory(FanoutExchange.TYPE);

        AMQQueue<?> queue = mock(AMQQueue.class);

        _vhost = mock(VirtualHostImpl.class);
        when(_vhost.getName()).thenReturn(VIRTUAL_HOST_NAME);

        _exchangeRegistry = mock(ExchangeRegistry.class);

        when(_vhost.getQueue(eq(QUEUE_ID))).thenReturn(queue);

        final ArgumentCaptor<ExchangeImpl> registeredExchange = ArgumentCaptor.forClass(ExchangeImpl.class);
        doAnswer(new Answer()
        {

            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable
            {
                ExchangeImpl exchange = registeredExchange.getValue();
                when(_exchangeRegistry.getExchange(eq(exchange.getId()))).thenReturn(exchange);
                when(_exchangeRegistry.getExchange(eq(exchange.getName()))).thenReturn(exchange);
                return null;
            }
        }).when(_exchangeRegistry).registerExchange(registeredExchange.capture());



        final ArgumentCaptor<Map> attributesArg = ArgumentCaptor.forClass(Map.class);

        _queueFactory = mock(QueueFactory.class);

        when(_queueFactory.restoreQueue(attributesArg.capture())).then(
                new Answer()
                {

                    @Override
                    public Object answer(final InvocationOnMock invocation) throws Throwable
                    {
                        final AMQQueue queue = mock(AMQQueue.class);

                        final Map attributes = attributesArg.getValue();
                        final String queueName = (String) attributes.get(Queue.NAME);
                        final UUID queueId = MapValueConverter.getUUIDAttribute(Queue.ID, attributes);

                        when(queue.getName()).thenReturn(queueName);
                        when(queue.getId()).thenReturn(queueId);
                        when(_vhost.getQueue(eq(queueName))).thenReturn(queue);
                        when(_vhost.getQueue(eq(queueId))).thenReturn(queue);

                        final ArgumentCaptor<ExchangeImpl> altExchangeArg = ArgumentCaptor.forClass(ExchangeImpl.class);
                        doAnswer(
                                new Answer()
                                {
                                    @Override
                                    public Object answer(InvocationOnMock invocation) throws Throwable
                                    {
                                        final ExchangeImpl value = altExchangeArg.getValue();
                                        when(queue.getAlternateExchange()).thenReturn(value);
                                        return null;
                                    }
                                }
                                ).when(queue).setAlternateExchange(altExchangeArg.capture());

                        Map args = attributes;
                        if (args.containsKey(Queue.ALTERNATE_EXCHANGE))
                        {
                            final UUID exchangeId = UUID.fromString(args.get(Queue.ALTERNATE_EXCHANGE).toString());
                            final ExchangeImpl exchange =
                                    (ExchangeImpl) _exchangeRegistry.getExchange(exchangeId);
                            queue.setAlternateExchange(exchange);
                        }
                        return queue;
                    }
                });



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
                                                  new DefaultUpgraderProvider(_vhost), new EventLogger());

        _store = mock(DurableConfigurationStore.class);

    }

    private ExchangeImpl<?> createAndRegisterDefaultExchangeWithFactory(ExchangeType<?> exchangeType) throws AMQUnknownExchangeType, UnknownExchangeException
    {
        ExchangeImpl exchange = mock(ExchangeImpl.class);
        when(exchange.getExchangeType()).thenReturn(exchangeType);
        Map<String, Object> directExchangeAttrsWithId = new HashMap<String, Object>();
        directExchangeAttrsWithId.put(org.apache.qpid.server.model.Exchange.ID, UUIDGenerator.generateExchangeUUID(exchangeType.getDefaultExchangeName(), VIRTUAL_HOST_NAME));
        directExchangeAttrsWithId.put(org.apache.qpid.server.model.Exchange.DURABLE, true);
        directExchangeAttrsWithId.put(org.apache.qpid.server.model.Exchange.TYPE, exchangeType.getType());
        directExchangeAttrsWithId.put(org.apache.qpid.server.model.Exchange.NAME, exchangeType.getDefaultExchangeName());
        when(_exchangeFactory.restoreExchange(directExchangeAttrsWithId)).thenReturn(exchange);
        return exchange;
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

        _durableConfigurationRecoverer.configuredObject(new ConfiguredObjectRecordImpl(new UUID(1, 0),
                                                           "org.apache.qpid.server.model.Binding",
                                                           createBinding("key",
                                                                         "x-filter-jms-selector",
                                                                         "wibble"),
                                                           createBindingParents(DIRECT_EXCHANGE_ID, QUEUE_ID)));

        final ConfiguredObjectRecord[] expected = {
                new ConfiguredObjectRecordImpl(new UUID(1, 0), "Binding",
                        createBinding("key"))
        };

        verifyCorrectUpdates(expected);

        _durableConfigurationRecoverer.completeConfigurationRecovery();
    }



    public void testUpgradeOnlyRemovesSelectorBindings() throws Exception
    {

        _durableConfigurationRecoverer.beginConfigurationRecovery(_store, 0);

        _durableConfigurationRecoverer.configuredObject(new ConfiguredObjectRecordImpl(new UUID(1, 0),
                                                           "org.apache.qpid.server.model.Binding",
                                                           createBinding("key",
                                                                         "x-filter-jms-selector",
                                                                         "wibble",
                                                                         "not-a-selector",
                                                                         "moo"),
                                                           createBindingParents(DIRECT_EXCHANGE_ID, QUEUE_ID)));


        final UUID customExchangeId = new UUID(3,0);

        _durableConfigurationRecoverer.configuredObject(new ConfiguredObjectRecordImpl(new UUID(2, 0),
                                                           "org.apache.qpid.server.model.Binding",
                                                           createBinding("key",
                                                                         "x-filter-jms-selector",
                                                                         "wibble",
                                                                         "not-a-selector",
                                                                         "moo"),
                                                           createBindingParents(customExchangeId,QUEUE_ID)));

        _durableConfigurationRecoverer.configuredObject(new ConfiguredObjectRecordImpl(customExchangeId,
                                                           "org.apache.qpid.server.model.Exchange",
                                                           createExchange(CUSTOM_EXCHANGE_NAME, HeadersExchange.TYPE)));

        final ExchangeImpl customExchange = mock(ExchangeImpl.class);

        final ArgumentCaptor<Map> attributesCaptor = ArgumentCaptor.forClass(Map.class);
        when(_exchangeFactory.restoreExchange(attributesCaptor.capture())).thenAnswer(new Answer<ExchangeImpl>()
        {
            @Override
            public ExchangeImpl answer(final InvocationOnMock invocation) throws Throwable
            {
                Map arguments = attributesCaptor.getValue();
                String exchangeName = (String) arguments.get(org.apache.qpid.server.model.Exchange.NAME);
                if(CUSTOM_EXCHANGE_NAME.equals(exchangeName)
                    && HeadersExchange.TYPE.getType().equals(arguments.get(org.apache.qpid.server.model.Exchange.TYPE))
                    && customExchangeId.equals((UUID) arguments.get(org.apache.qpid.server.model.Exchange.ID)))
                {
                    return customExchange;
                }
                else if ("amq.topic".equals(exchangeName))
                {
                    return _topicExchange;
                }
                else if ("amq.direct".equals(exchangeName))
                {
                    return _directExchange;
                }
                else if ("amq.fanout".equals(exchangeName))
                {
                    return _fanoutExchange;
                }
                else if ("amq.match".equals(exchangeName))
                {
                    return _matchExchange;
                }
                else
                {
                    return null;
                }
            }
        });



        final ConfiguredObjectRecord[] expected = {
                new ConfiguredObjectRecordImpl(new UUID(1, 0), "org.apache.qpid.server.model.Binding",
                        createBinding("key", "not-a-selector", "moo")),
                new ConfiguredObjectRecordImpl(new UUID(2, 0), "org.apache.qpid.server.model.Binding",
                        createBinding("key", "not-a-selector", "moo"))
        };

        verifyCorrectUpdates(expected);

        _durableConfigurationRecoverer.completeConfigurationRecovery();
    }


    public void testUpgradeKeepsBindingsToTopicExchanges() throws Exception
    {

        _durableConfigurationRecoverer.beginConfigurationRecovery(_store, 0);

        _durableConfigurationRecoverer.configuredObject(new ConfiguredObjectRecordImpl(new UUID(1, 0),
                                                           "org.apache.qpid.server.model.Binding",
                                                           createBinding("key",
                                                                         "x-filter-jms-selector",
                                                                         "wibble"),
                                                           createBindingParents(TOPIC_EXCHANGE_ID,QUEUE_ID)));

        final ConfiguredObjectRecord[] expected = {
                new ConfiguredObjectRecordImpl(new UUID(1, 0), "Binding",
                        createBinding("key", "x-filter-jms-selector", "wibble"))
        };

        verifyCorrectUpdates(expected);

        _durableConfigurationRecoverer.completeConfigurationRecovery();
    }

    public void testUpgradeDoesNotRecur() throws Exception
    {

        _durableConfigurationRecoverer.beginConfigurationRecovery(_store, 2);

        _durableConfigurationRecoverer.configuredObject(new ConfiguredObjectRecordImpl(new UUID(1, 0),
                                                           "Binding",
                                                           createBinding("key",
                                                                         "x-filter-jms-selector",
                                                                         "wibble"),
                                                           createBindingParents(DIRECT_EXCHANGE_ID,QUEUE_ID)));

        doThrow(new RuntimeException("Update Should not be called"))
                .when(_store).update(anyBoolean(), any(ConfiguredObjectRecordImpl[].class));

        _durableConfigurationRecoverer.completeConfigurationRecovery();
    }

    public void testFailsWithUnresolvedObjects()
    {
        _durableConfigurationRecoverer.beginConfigurationRecovery(_store, 2);


        _durableConfigurationRecoverer.configuredObject(new ConfiguredObjectRecordImpl(new UUID(1, 0),
                                                        "Binding",
                                                        createBinding("key",
                                                                      "x-filter-jms-selector",
                                                                      "wibble"),
                                                        createBindingParents(new UUID(3,0),
                                                                             QUEUE_ID)));

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
            _durableConfigurationRecoverer.configuredObject(new ConfiguredObjectRecordImpl(new UUID(1, 0),
                                                            "Wibble", emptyArguments));
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

        final ExchangeImpl customExchange = mock(ExchangeImpl.class);

        when(customExchange.getId()).thenReturn(exchangeId);
        when(customExchange.getName()).thenReturn(CUSTOM_EXCHANGE_NAME);

        final ArgumentCaptor<Map> attributesCaptor = ArgumentCaptor.forClass(Map.class);

        when(_exchangeFactory.restoreExchange(attributesCaptor.capture())).thenAnswer(new Answer<ExchangeImpl>()
        {
            @Override
            public ExchangeImpl answer(final InvocationOnMock invocation) throws Throwable
            {
                Map arguments = attributesCaptor.getValue();
                String exchangeName = (String) arguments.get(org.apache.qpid.server.model.Exchange.NAME);
                if(CUSTOM_EXCHANGE_NAME.equals(exchangeName)
                   && HeadersExchange.TYPE.getType().equals(arguments.get(org.apache.qpid.server.model.Exchange.TYPE))
                   && exchangeId.equals(arguments.get(org.apache.qpid.server.model.Exchange.ID)))
                {
                    return customExchange;
                }
                else if ("amq.topic".equals(exchangeName))
                {
                    return _topicExchange;
                }
                else if ("amq.direct".equals(exchangeName))
                {
                    return _directExchange;
                }
                else if ("amq.fanout".equals(exchangeName))
                {
                    return _fanoutExchange;
                }
                else if ("amq.match".equals(exchangeName))
                {
                    return _matchExchange;
                }
                else
                {
                    return null;
                }
            }
        });

        _durableConfigurationRecoverer.beginConfigurationRecovery(_store, 2);

        _durableConfigurationRecoverer.configuredObject(new ConfiguredObjectRecordImpl(queueId, Queue.class.getSimpleName(),
                                                        createQueue("testQueue", exchangeId)));
        _durableConfigurationRecoverer.configuredObject(new ConfiguredObjectRecordImpl(exchangeId,
                                                        org.apache.qpid.server.model.Exchange.class.getSimpleName(),
                                                        createExchange(CUSTOM_EXCHANGE_NAME, HeadersExchange.TYPE)));

        _durableConfigurationRecoverer.completeConfigurationRecovery();

        assertEquals(customExchange, _vhost.getQueue(queueId).getAlternateExchange());
    }

    private void verifyCorrectUpdates(final ConfiguredObjectRecord[] expected) throws StoreException
    {
        doAnswer(new Answer()
        {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable
            {
                Object[] args = invocation.getArguments();
                final HashSet actual = new HashSet(Arrays.asList(args[1]));
                assertEquals("Updated records are not as expected", new HashSet(Arrays.asList(
                        expected)), actual);

                return null;
            }
        }).when(_store).update(anyBoolean(), any(ConfiguredObjectRecordImpl[].class));
    }

    private Map<String,Object> createBinding(String bindingKey, String... args)
    {
        Map<String, Object> binding = new LinkedHashMap<String, Object>();

        binding.put("name", bindingKey);
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

    private Map<String,ConfiguredObjectRecord> createBindingParents(UUID exchangeId, UUID queueId)
    {
        Map<String,ConfiguredObjectRecord> parents = new HashMap<String, ConfiguredObjectRecord>();
        parents.put("Exchange", new ConfiguredObjectRecordImpl(exchangeId,"Exchange",Collections.<String,Object>emptyMap()));
        parents.put("Queue", new ConfiguredObjectRecordImpl(queueId,"Queue",Collections.<String,Object>emptyMap()));

        return parents;
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

        return queue;

    }

}
