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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.configuration.Configuration;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.server.binding.BindingImpl;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.ExclusivityPolicy;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.MessageStoreRecoveryHandler.StoredMessageRecoveryHandler;
import org.apache.qpid.server.store.Transaction.Record;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public abstract class AbstractDurableConfigurationStoreTestCase extends QpidTestCase
{
    private static final String EXCHANGE_NAME = "exchangeName";

    private static final String EXCHANGE = org.apache.qpid.server.model.Exchange.class.getSimpleName();
    private static final String BINDING = org.apache.qpid.server.model.Binding.class.getSimpleName();
    private static final String QUEUE = Queue.class.getSimpleName();

    private static final UUID ANY_UUID = UUID.randomUUID();
    private static final Map ANY_MAP = new HashMap();


    private String _storePath;
    private String _storeName;
    private MessageStore _messageStore;
    private Configuration _configuration;
    private VirtualHost _virtualHost;

    private ConfigurationRecoveryHandler _recoveryHandler;
    private MessageStoreRecoveryHandler _messageStoreRecoveryHandler;
    private StoredMessageRecoveryHandler _storedMessageRecoveryHandler;
    private TransactionLogRecoveryHandler _logRecoveryHandler;
    private TransactionLogRecoveryHandler.QueueEntryRecoveryHandler _queueEntryRecoveryHandler;
    private TransactionLogRecoveryHandler.DtxRecordRecoveryHandler _dtxRecordRecoveryHandler;

    private ExchangeImpl _exchange = mock(ExchangeImpl.class);
    private static final String ROUTING_KEY = "routingKey";
    private static final String QUEUE_NAME = "queueName";
    private Map<String,Object> _bindingArgs;
    private UUID _queueId;
    private UUID _exchangeId;
    private DurableConfigurationStore _configStore;

    public void setUp() throws Exception
    {
        super.setUp();

        _queueId = UUIDGenerator.generateRandomUUID();
        _exchangeId = UUIDGenerator.generateRandomUUID();

        _storeName = getName();
        _storePath = TMP_FOLDER + File.separator + _storeName;
        FileUtils.delete(new File(_storePath), true);
        setTestSystemProperty("QPID_WORK", TMP_FOLDER);
        _configuration = mock(Configuration.class);
        _recoveryHandler = mock(ConfigurationRecoveryHandler.class);
        _storedMessageRecoveryHandler = mock(StoredMessageRecoveryHandler.class);
        _logRecoveryHandler = mock(TransactionLogRecoveryHandler.class);
        _messageStoreRecoveryHandler = mock(MessageStoreRecoveryHandler.class);
        _queueEntryRecoveryHandler = mock(TransactionLogRecoveryHandler.QueueEntryRecoveryHandler.class);
        _dtxRecordRecoveryHandler = mock(TransactionLogRecoveryHandler.DtxRecordRecoveryHandler.class);
        _virtualHost = mock(VirtualHost.class);

        when(_messageStoreRecoveryHandler.begin()).thenReturn(_storedMessageRecoveryHandler);
        when(_logRecoveryHandler.begin(any(MessageStore.class))).thenReturn(_queueEntryRecoveryHandler);
        when(_queueEntryRecoveryHandler.completeQueueEntryRecovery()).thenReturn(_dtxRecordRecoveryHandler);
        when(_exchange.getName()).thenReturn(EXCHANGE_NAME);

        when(_exchange.getId()).thenReturn(_exchangeId);
        when(_exchange.getExchangeType()).thenReturn(mock(ExchangeType.class));
        when(_exchange.getEventLogger()).thenReturn(new EventLogger());

        ConfiguredObjectRecord exchangeRecord = mock(ConfiguredObjectRecord.class);
        when(exchangeRecord.getId()).thenReturn(_exchangeId);
        when(exchangeRecord.getType()).thenReturn(Exchange.class.getSimpleName());
        when(_exchange.asObjectRecord()).thenReturn(exchangeRecord);

        when(_configuration.getString(eq(MessageStoreConstants.ENVIRONMENT_PATH_PROPERTY), anyString())).thenReturn(
                _storePath);
        when(_virtualHost.getAttribute(eq(VirtualHost.STORE_PATH))).thenReturn(_storePath);


        _bindingArgs = new HashMap<String, Object>();
        String argKey = AMQPFilterTypes.JMS_SELECTOR.toString();
        String argValue = "some selector expression";
        _bindingArgs.put(argKey, argValue);

        reopenStore();
    }

    public void tearDown() throws Exception
    {
        try
        {
            closeMessageStore();
            closeConfigStore();
            FileUtils.delete(new File(_storePath), true);
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testCreateExchange() throws Exception
    {
        ExchangeImpl exchange = createTestExchange();
        DurableConfigurationStoreHelper.createExchange(_configStore, exchange);

        reopenStore();
        verify(_recoveryHandler).configuredObject(matchesRecord(_exchangeId, EXCHANGE,
                map( org.apache.qpid.server.model.Exchange.NAME, getName(),
                        org.apache.qpid.server.model.Exchange.TYPE, getName()+"Type",
                        org.apache.qpid.server.model.Exchange.LIFETIME_POLICY, LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS.name())));
    }

    private Map<String,Object> map(Object... vals)
    {
        Map<String,Object> map = new HashMap<String, Object>();
        boolean isValue = false;
        String key = null;
        for(Object obj : vals)
        {
            if(isValue)
            {
                map.put(key,obj);
            }
            else
            {
                key = (String) obj;
            }
            isValue = !isValue;
        }
        return map;
    }

    public void testRemoveExchange() throws Exception
    {
        ExchangeImpl exchange = createTestExchange();
        DurableConfigurationStoreHelper.createExchange(_configStore, exchange);

        DurableConfigurationStoreHelper.removeExchange(_configStore, exchange);

        reopenStore();
        verify(_recoveryHandler, never()).configuredObject(any(ConfiguredObjectRecord.class));
    }

    public void testBindQueue() throws Exception
    {
        AMQQueue queue = createTestQueue(QUEUE_NAME, "queueOwner", false, null);
        BindingImpl binding = new BindingImpl(UUIDGenerator.generateRandomUUID(), ROUTING_KEY, queue,
                _exchange, _bindingArgs);
        DurableConfigurationStoreHelper.createQueue(_configStore, queue);
        DurableConfigurationStoreHelper.createBinding(_configStore, binding);

        reopenStore();

        Map<String,Object> map = new HashMap<String, Object>();
        map.put(Binding.NAME, ROUTING_KEY);
        map.put(Binding.ARGUMENTS,_bindingArgs);

        Map<String,UUID> parents = new HashMap<String, UUID>();

        parents.put(Exchange.class.getSimpleName(), _exchange.getId());
        parents.put(Queue.class.getSimpleName(), queue.getId());

        verify(_recoveryHandler).configuredObject(matchesRecord(binding.getId(), BINDING, map, parents));
    }


    private ConfiguredObjectRecord matchesRecord(UUID id,
                                                 String type,
                                                 Map<String, Object> attributes,
                                                 final Map<String, UUID> parents)
    {
        return argThat(new ConfiguredObjectMatcher(id, type, attributes, parents));
    }

    private ConfiguredObjectRecord matchesRecord(UUID id, String type, Map<String, Object> attributes)
    {
        return argThat(new ConfiguredObjectMatcher(id, type, attributes, ANY_MAP));
    }

    private static class ConfiguredObjectMatcher extends ArgumentMatcher<ConfiguredObjectRecord>
    {
        private final Map<String,Object> _matchingMap;
        private final UUID _id;
        private final String _name;
        private final Map<String,UUID> _parents;

        private ConfiguredObjectMatcher(final UUID id, final String type, final Map<String, Object> matchingMap, Map<String,UUID> parents)
        {
            _id = id;
            _name = type;
            _matchingMap = matchingMap;
            _parents = parents;
        }

        @Override
        public boolean matches(final Object argument)
        {
            if(argument instanceof ConfiguredObjectRecord)
            {
                ConfiguredObjectRecord binding = (ConfiguredObjectRecord) argument;

                Map<String,Object> arg = new HashMap<String, Object>(binding.getAttributes());
                arg.remove("createdBy");
                arg.remove("createdTime");
                return (_id == ANY_UUID || _id.equals(binding.getId()))
                       && _name.equals(binding.getType())
                       && (_matchingMap == ANY_MAP || arg.equals(_matchingMap))
                       && (_parents == ANY_MAP || matchesParents(binding));
            }
            return false;
        }

        private boolean matchesParents(ConfiguredObjectRecord binding)
        {
            Map<String, ConfiguredObjectRecord> bindingParents = binding.getParents();
            if(bindingParents.size() != _parents.size())
            {
                return false;
            }
            for(Map.Entry<String,UUID> entry : _parents.entrySet())
            {
                if(!bindingParents.get(entry.getKey()).getId().equals(entry.getValue()))
                {
                    return false;
                }
            }
            return true;
        }
    }

    public void testUnbindQueue() throws Exception
    {
        AMQQueue queue = createTestQueue(QUEUE_NAME, "queueOwner", false, null);
        BindingImpl binding = new BindingImpl(UUIDGenerator.generateRandomUUID(), ROUTING_KEY, queue,
                _exchange, _bindingArgs);
        DurableConfigurationStoreHelper.createBinding(_configStore, binding);

        DurableConfigurationStoreHelper.removeBinding(_configStore, binding);
        reopenStore();

        verify(_recoveryHandler, never()).configuredObject(matchesRecord(ANY_UUID, BINDING,
                                                                         ANY_MAP));
    }

    public void testCreateQueueAMQQueue() throws Exception
    {
        AMQQueue queue = createTestQueue(getName(), getName() + "Owner", true, null);
        DurableConfigurationStoreHelper.createQueue(_configStore, queue);

        reopenStore();
        Map<String, Object> queueAttributes = new HashMap<String, Object>();
        queueAttributes.put(Queue.NAME, getName());
        queueAttributes.put(Queue.OWNER, getName()+"Owner");
        queueAttributes.put(Queue.EXCLUSIVE, ExclusivityPolicy.CONTAINER.name());
        verify(_recoveryHandler).configuredObject(matchesRecord(_queueId, QUEUE, queueAttributes));
    }

    public void testCreateQueueAMQQueueFieldTable() throws Exception
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.CREATE_DLQ_ON_CREATION, Boolean.TRUE);
        attributes.put(Queue.MAXIMUM_DELIVERY_ATTEMPTS, 10);
        AMQQueue queue = createTestQueue(getName(), getName() + "Owner", true, attributes);

        DurableConfigurationStoreHelper.createQueue(_configStore, queue);

        reopenStore();


        Map<String,Object> queueAttributes = new HashMap<String, Object>();

        queueAttributes.put(Queue.NAME, getName());
        queueAttributes.put(Queue.OWNER, getName()+"Owner");
        queueAttributes.put(Queue.EXCLUSIVE, ExclusivityPolicy.CONTAINER.name());
        queueAttributes.putAll(attributes);

        verify(_recoveryHandler).configuredObject(matchesRecord(_queueId, QUEUE, queueAttributes));
    }

    public void testCreateQueueAMQQueueWithAlternateExchange() throws Exception
    {
        ExchangeImpl alternateExchange = createTestAlternateExchange();

        AMQQueue queue = createTestQueue(getName(), getName() + "Owner", true, alternateExchange, null);
        DurableConfigurationStoreHelper.createQueue(_configStore, queue);

        reopenStore();

        Map<String, Object> queueAttributes = new HashMap<String, Object>();
        queueAttributes.put(Queue.NAME, getName());
        queueAttributes.put(Queue.OWNER, getName()+"Owner");
        queueAttributes.put(Queue.EXCLUSIVE, ExclusivityPolicy.CONTAINER.name());
        queueAttributes.put(Queue.ALTERNATE_EXCHANGE, alternateExchange.getId().toString());

        verify(_recoveryHandler).configuredObject(matchesRecord(_queueId, QUEUE, queueAttributes));
    }

    private ExchangeImpl createTestAlternateExchange()
    {
        UUID exchUuid = UUID.randomUUID();
        ExchangeImpl alternateExchange = mock(ExchangeImpl.class);
        when(alternateExchange.getId()).thenReturn(exchUuid);
        return alternateExchange;
    }

    public void testUpdateQueueExclusivity() throws Exception
    {
        // create queue
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.CREATE_DLQ_ON_CREATION, Boolean.TRUE);
        attributes.put(Queue.MAXIMUM_DELIVERY_ATTEMPTS, 10);
        AMQQueue queue = createTestQueue(getName(), getName() + "Owner", true, attributes);

        DurableConfigurationStoreHelper.createQueue(_configStore, queue);

        // update the queue to have exclusive=false
        queue = createTestQueue(getName(), getName() + "Owner", false, attributes);

        DurableConfigurationStoreHelper.updateQueue(_configStore, queue);

        reopenStore();

        Map<String,Object> queueAttributes = new HashMap<String, Object>();

        queueAttributes.put(Queue.NAME, getName());
        queueAttributes.putAll(attributes);

        verify(_recoveryHandler).configuredObject(matchesRecord(_queueId, QUEUE, queueAttributes));

    }

    public void testUpdateQueueAlternateExchange() throws Exception
    {
        // create queue
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.CREATE_DLQ_ON_CREATION, Boolean.TRUE);
        attributes.put(Queue.MAXIMUM_DELIVERY_ATTEMPTS, 10);
        AMQQueue queue = createTestQueue(getName(), getName() + "Owner", true, attributes);
        DurableConfigurationStoreHelper.createQueue(_configStore, queue);

        // update the queue to have exclusive=false
        ExchangeImpl alternateExchange = createTestAlternateExchange();
        queue = createTestQueue(getName(), getName() + "Owner", false, alternateExchange, attributes);

        DurableConfigurationStoreHelper.updateQueue(_configStore, queue);

        reopenStore();

        Map<String,Object> queueAttributes = new HashMap<String, Object>();

        queueAttributes.put(Queue.NAME, getName());
        queueAttributes.putAll(attributes);
        queueAttributes.put(Queue.ALTERNATE_EXCHANGE, alternateExchange.getId().toString());

        verify(_recoveryHandler).configuredObject(matchesRecord(_queueId, QUEUE, queueAttributes));
    }

    public void testRemoveQueue() throws Exception
    {
        // create queue
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.CREATE_DLQ_ON_CREATION, Boolean.TRUE);
        attributes.put(Queue.MAXIMUM_DELIVERY_ATTEMPTS, 10);
        AMQQueue queue = createTestQueue(getName(), getName() + "Owner", true, attributes);
        DurableConfigurationStoreHelper.createQueue(_configStore, queue);

        // remove queue
        DurableConfigurationStoreHelper.removeQueue(_configStore,queue);
        reopenStore();
        verify(_recoveryHandler, never()).configuredObject(any(ConfiguredObjectRecord.class));
    }

    private AMQQueue createTestQueue(String queueName,
                                     String queueOwner,
                                     boolean exclusive,
                                     final Map<String, Object> arguments) throws StoreException
    {
        return createTestQueue(queueName, queueOwner, exclusive, null, arguments);
    }

    private AMQQueue createTestQueue(String queueName,
                                     String queueOwner,
                                     boolean exclusive,
                                     ExchangeImpl alternateExchange,
                                     final Map<String, Object> arguments) throws StoreException
    {
        AMQQueue<?> queue = mock(AMQQueue.class);
        when(queue.getName()).thenReturn(queueName);
        when(queue.isExclusive()).thenReturn(exclusive);
        when(queue.getId()).thenReturn(_queueId);
        when(queue.getAlternateExchange()).thenReturn(alternateExchange);
        final org.apache.qpid.server.virtualhost.VirtualHost vh = mock(org.apache.qpid.server.virtualhost.VirtualHost.class);
        when(vh.getSecurityManager()).thenReturn(mock(SecurityManager.class));
        when(queue.getVirtualHost()).thenReturn(vh);
        final Map<String,Object> attributes = arguments == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(arguments);
        attributes.put(Queue.NAME, queueName);
        if(alternateExchange != null)
        {
            attributes.put(Queue.ALTERNATE_EXCHANGE, alternateExchange);
        }
        if(exclusive)
        {
            when(queue.getOwner()).thenReturn(queueOwner);

            attributes.put(Queue.OWNER, queueOwner);
            attributes.put(Queue.EXCLUSIVE, ExclusivityPolicy.CONTAINER);
        }
            when(queue.getAvailableAttributes()).thenReturn(attributes.keySet());
            final ArgumentCaptor<String> requestedAttribute = ArgumentCaptor.forClass(String.class);
            when(queue.getAttribute(requestedAttribute.capture())).then(
                    new Answer()
                    {

                        @Override
                        public Object answer(final InvocationOnMock invocation) throws Throwable
                        {
                            String attrName = requestedAttribute.getValue();
                            return attributes.get(attrName);
                        }
                    });

        when(queue.getActualAttributes()).thenReturn(attributes);

        ConfiguredObjectRecord objectRecord = mock(ConfiguredObjectRecord.class);
        when(objectRecord.getId()).thenReturn(_queueId);
        when(objectRecord.getType()).thenReturn(Queue.class.getSimpleName());
        when(objectRecord.getAttributes()).thenReturn(attributes);
        when(queue.asObjectRecord()).thenReturn(objectRecord);
        return queue;
    }

    private ExchangeImpl createTestExchange()
    {
        ExchangeImpl exchange = mock(ExchangeImpl.class);
        Map<String,Object> actualAttributes = new HashMap<String, Object>();
        actualAttributes.put("id", _exchangeId);
        actualAttributes.put("name", getName());
        actualAttributes.put("type", getName() + "Type");
        actualAttributes.put("lifetimePolicy", LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS);
        when(exchange.getActualAttributes()).thenReturn(actualAttributes);
        when(exchange.getName()).thenReturn(getName());
        when(exchange.getTypeName()).thenReturn(getName() + "Type");
        when(exchange.isAutoDelete()).thenReturn(true);
        when(exchange.getId()).thenReturn(_exchangeId);
        ConfiguredObjectRecord exchangeRecord = mock(ConfiguredObjectRecord.class);
        when(exchangeRecord.getId()).thenReturn(_exchangeId);
        when(exchangeRecord.getType()).thenReturn(Exchange.class.getSimpleName());
        Map<String,Object> actualAttributesExceptId = new HashMap<String, Object>(actualAttributes);
        actualAttributesExceptId.remove("id");
        when(exchangeRecord.getAttributes()).thenReturn(actualAttributesExceptId);
        when(exchange.asObjectRecord()).thenReturn(exchangeRecord);

        return exchange;
    }

    private void reopenStore() throws Exception
    {
        closeMessageStore();
        closeConfigStore();
        _messageStore = createMessageStore();
        _configStore = createConfigStore();

        _configStore.configureConfigStore(_virtualHost, _recoveryHandler);
        _messageStore.configureMessageStore(_virtualHost, _messageStoreRecoveryHandler, _logRecoveryHandler);
        _messageStore.activate();
    }

    protected abstract MessageStore createMessageStore() throws Exception;
    protected abstract DurableConfigurationStore createConfigStore() throws Exception;
    protected abstract void closeMessageStore() throws Exception;
    protected abstract void closeConfigStore() throws Exception;

    public void testRecordXid() throws Exception
    {
        Record enqueueRecord = getTestRecord(1);
        Record dequeueRecord = getTestRecord(2);
        Record[] enqueues = { enqueueRecord };
        Record[] dequeues = { dequeueRecord };
        byte[] globalId = new byte[] { 1 };
        byte[] branchId = new byte[] { 2 };

        Transaction transaction = _messageStore.newTransaction();
        transaction.recordXid(1l, globalId, branchId, enqueues, dequeues);
        transaction.commitTran();
        reopenStore();
        verify(_dtxRecordRecoveryHandler).dtxRecord(1l, globalId, branchId, enqueues, dequeues);

        transaction = _messageStore.newTransaction();
        transaction.removeXid(1l, globalId, branchId);
        transaction.commitTran();

        reopenStore();
        verify(_dtxRecordRecoveryHandler, times(1)).dtxRecord(1l, globalId, branchId, enqueues, dequeues);
    }

    private Record getTestRecord(long messageNumber)
    {
        UUID queueId1 = UUIDGenerator.generateRandomUUID();
        TransactionLogResource queue1 = mock(TransactionLogResource.class);
        when(queue1.getId()).thenReturn(queueId1);
        EnqueueableMessage message1 = mock(EnqueueableMessage.class);
        when(message1.isPersistent()).thenReturn(true);
        when(message1.getMessageNumber()).thenReturn(messageNumber);
        final StoredMessage storedMessage = mock(StoredMessage.class);
        when(storedMessage.getMessageNumber()).thenReturn(messageNumber);
        when(message1.getStoredMessage()).thenReturn(storedMessage);
        Record enqueueRecord = new TestRecord(queue1, message1);
        return enqueueRecord;
    }

    private static class TestRecord implements Record
    {
        private TransactionLogResource _queue;
        private EnqueueableMessage _message;

        public TestRecord(TransactionLogResource queue, EnqueueableMessage message)
        {
            super();
            _queue = queue;
            _message = message;
        }

        @Override
        public TransactionLogResource getResource()
        {
            return _queue;
        }

        @Override
        public EnqueueableMessage getMessage()
        {
            return _message;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((_message == null) ? 0 : new Long(_message.getMessageNumber()).hashCode());
            result = prime * result + ((_queue == null) ? 0 : _queue.getId().hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (!(obj instanceof Record))
            {
                return false;
            }
            Record other = (Record) obj;
            if (_message == null && other.getMessage() != null)
            {
                return false;
            }
            if (_queue == null && other.getResource() != null)
            {
                return false;
            }
            if (_message.getMessageNumber() != other.getMessage().getMessageNumber())
            {
                return false;
            }
            return _queue.getId().equals(other.getResource().getId());
        }

    }
}
