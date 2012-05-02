package org.apache.qpid.server.store;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.configuration.Configuration;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.logging.SystemOutMessageLogger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.TestLogActor;
import org.apache.qpid.server.logging.subjects.TestBlankSubject;
import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.MockStoredMessage;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreRecoveryHandler;
import org.apache.qpid.server.store.TransactionLogRecoveryHandler;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler.BindingRecoveryHandler;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler.ExchangeRecoveryHandler;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler.QueueRecoveryHandler;
import org.apache.qpid.server.store.MessageStoreRecoveryHandler.StoredMessageRecoveryHandler;
import org.apache.qpid.server.store.Transaction.Record;
import org.apache.qpid.server.store.derby.DerbyMessageStoreFactory;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

public class DurableConfigurationStoreTest extends QpidTestCase
{
    private static final String EXCHANGE_NAME = "exchangeName";
    private String _storePath;
    private String _storeName;
    private MessageStore _store;
    private Configuration _configuration;

    private ConfigurationRecoveryHandler _recoveryHandler;
    private QueueRecoveryHandler _queueRecoveryHandler;
    private ExchangeRecoveryHandler _exchangeRecoveryHandler;
    private BindingRecoveryHandler _bindingRecoveryHandler;
    private ConfigurationRecoveryHandler.BrokerLinkRecoveryHandler _linkRecoveryHandler;
    private MessageStoreRecoveryHandler _messageStoreRecoveryHandler;
    private StoredMessageRecoveryHandler _storedMessageRecoveryHandler;
    private TransactionLogRecoveryHandler _logRecoveryHandler;
    private TransactionLogRecoveryHandler.QueueEntryRecoveryHandler _queueEntryRecoveryHandler;
    private TransactionLogRecoveryHandler.DtxRecordRecoveryHandler _dtxRecordRecoveryHandler;

    private Exchange _exchange = mock(Exchange.class);
    private static final String ROUTING_KEY = "routingKey";
    private static final String QUEUE_NAME = "queueName";
    private FieldTable _bindingArgs;
    private UUID _queueId;
    private UUID _exchangeId;

    public void setUp() throws Exception
    {
        super.setUp();

        _queueId = UUIDGenerator.generateUUID();
        _exchangeId = UUIDGenerator.generateUUID();

        _storeName = getName();
        _storePath = TMP_FOLDER + "/" + _storeName;
        FileUtils.delete(new File(_storePath), true);
        setTestSystemProperty("QPID_WORK", TMP_FOLDER);
        _configuration = mock(Configuration.class);
        _recoveryHandler = mock(ConfigurationRecoveryHandler.class);
        _queueRecoveryHandler = mock(QueueRecoveryHandler.class);
        _exchangeRecoveryHandler = mock(ExchangeRecoveryHandler.class);
        _bindingRecoveryHandler = mock(BindingRecoveryHandler.class);
        _storedMessageRecoveryHandler = mock(StoredMessageRecoveryHandler.class);
        _logRecoveryHandler = mock(TransactionLogRecoveryHandler.class);
        _messageStoreRecoveryHandler = mock(MessageStoreRecoveryHandler.class);
        _queueEntryRecoveryHandler = mock(TransactionLogRecoveryHandler.QueueEntryRecoveryHandler.class);
        _dtxRecordRecoveryHandler = mock(TransactionLogRecoveryHandler.DtxRecordRecoveryHandler.class);

        when(_messageStoreRecoveryHandler.begin()).thenReturn(_storedMessageRecoveryHandler);
        when(_recoveryHandler.begin(isA(MessageStore.class))).thenReturn(_queueRecoveryHandler);
        when(_queueRecoveryHandler.completeQueueRecovery()).thenReturn(_exchangeRecoveryHandler);
        when(_exchangeRecoveryHandler.completeExchangeRecovery()).thenReturn(_bindingRecoveryHandler);
        when(_bindingRecoveryHandler.completeBindingRecovery()).thenReturn(_linkRecoveryHandler);
        when(_logRecoveryHandler.begin(any(MessageStore.class))).thenReturn(_queueEntryRecoveryHandler);
        when(_queueEntryRecoveryHandler.completeQueueEntryRecovery()).thenReturn(_dtxRecordRecoveryHandler);
        when(_exchange.getNameShortString()).thenReturn(AMQShortString.valueOf(EXCHANGE_NAME));
        when(_exchange.getId()).thenReturn(_exchangeId);
        when(_configuration.getString(eq(MessageStoreConstants.ENVIRONMENT_PATH_PROPERTY), anyString())).thenReturn(
                _storePath);

        _bindingArgs = new FieldTable();
        AMQShortString argKey = AMQPFilterTypes.JMS_SELECTOR.getValue();
        String argValue = "some selector expression";
        _bindingArgs.put(argKey, argValue);

        reopenStore();
    }

    public void tearDown() throws Exception
    {
        FileUtils.delete(new File(_storePath), true);
        super.tearDown();
    }

    public void testCreateExchange() throws Exception
    {
        Exchange exchange = createTestExchange();
        _store.createExchange(exchange);

        reopenStore();
        verify(_exchangeRecoveryHandler).exchange(_exchangeId, getName(), getName() + "Type", true);
    }

    public void testRemoveExchange() throws Exception
    {
        Exchange exchange = createTestExchange();
        _store.createExchange(exchange);

        _store.removeExchange(exchange);

        reopenStore();
        verify(_exchangeRecoveryHandler, never()).exchange(any(UUID.class), anyString(), anyString(), anyBoolean());
    }

    public void testBindQueue() throws Exception
    {
        AMQQueue queue = createTestQueue(QUEUE_NAME, "queueOwner", false);
        Binding binding = new Binding(UUIDGenerator.generateUUID(), ROUTING_KEY, queue, _exchange,
                FieldTable.convertToMap(_bindingArgs));
        _store.bindQueue(binding);

        reopenStore();

        ByteBuffer argsAsBytes = ByteBuffer.wrap(_bindingArgs.getDataAsBytes());

        verify(_bindingRecoveryHandler).binding(binding.getId(), _exchange.getId(), queue.getId(), ROUTING_KEY, argsAsBytes);
    }

    public void testUnbindQueue() throws Exception
    {
        AMQQueue queue = createTestQueue(QUEUE_NAME, "queueOwner", false);
        Binding binding = new Binding(UUIDGenerator.generateUUID(), ROUTING_KEY, queue, _exchange,
                FieldTable.convertToMap(_bindingArgs));
        _store.bindQueue(binding);

        _store.unbindQueue(binding);
        reopenStore();

        verify(_bindingRecoveryHandler, never()).binding(any(UUID.class), any(UUID.class), any(UUID.class), anyString(),
                isA(ByteBuffer.class));
    }

    public void testCreateQueueAMQQueue() throws Exception
    {
        AMQQueue queue = createTestQueue(getName(), getName() + "Owner", true);
        _store.createQueue(queue);

        reopenStore();
        verify(_queueRecoveryHandler).queue(_queueId, getName(), getName() + "Owner", true, null);
    }

    public void testCreateQueueAMQQueueFieldTable() throws Exception
    {
        AMQQueue queue = createTestQueue(getName(), getName() + "Owner", true);
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("x-qpid-dlq-enabled", Boolean.TRUE);
        attributes.put("x-qpid-maximum-delivery-count", new Integer(10));

        FieldTable arguments = FieldTable.convertToFieldTable(attributes);
        _store.createQueue(queue, arguments);

        reopenStore();
        verify(_queueRecoveryHandler).queue(_queueId, getName(), getName() + "Owner", true, arguments);
    }

    public void testUpdateQueue() throws Exception
    {
        // create queue
        AMQQueue queue = createTestQueue(getName(), getName() + "Owner", true);
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("x-qpid-dlq-enabled", Boolean.TRUE);
        attributes.put("x-qpid-maximum-delivery-count", new Integer(10));
        FieldTable arguments = FieldTable.convertToFieldTable(attributes);
        _store.createQueue(queue, arguments);

        // update the queue to have exclusive=false
        queue = createTestQueue(getName(), getName() + "Owner", false);
        _store.updateQueue(queue);

        reopenStore();
        verify(_queueRecoveryHandler).queue(_queueId, getName(), getName() + "Owner", false, arguments);
    }

    public void testRemoveQueue() throws Exception
    {
        // create queue
        AMQQueue queue = createTestQueue(getName(), getName() + "Owner", true);
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("x-qpid-dlq-enabled", Boolean.TRUE);
        attributes.put("x-qpid-maximum-delivery-count", new Integer(10));
        FieldTable arguments = FieldTable.convertToFieldTable(attributes);
        _store.createQueue(queue, arguments);

        // remove queue
        _store.removeQueue(queue);
        reopenStore();
        verify(_queueRecoveryHandler, never()).queue(any(UUID.class), anyString(), anyString(), anyBoolean(),
                any(FieldTable.class));
    }

    private AMQQueue createTestQueue(String queueName, String queueOwner, boolean exclusive) throws AMQStoreException
    {
        AMQQueue queue = mock(AMQQueue.class);
        when(queue.getName()).thenReturn(queueName);
        when(queue.getNameShortString()).thenReturn(AMQShortString.valueOf(queueName));
        when(queue.getOwner()).thenReturn(AMQShortString.valueOf(queueOwner));
        when(queue.isExclusive()).thenReturn(exclusive);
        when(queue.getId()).thenReturn(_queueId);
        return queue;
    }

    private Exchange createTestExchange()
    {
        Exchange exchange = mock(Exchange.class);
        when(exchange.getNameShortString()).thenReturn(AMQShortString.valueOf(getName()));
        when(exchange.getName()).thenReturn(getName());
        when(exchange.getTypeShortString()).thenReturn(AMQShortString.valueOf(getName() + "Type"));
        when(exchange.isAutoDelete()).thenReturn(true);
        when(exchange.getId()).thenReturn(_exchangeId);
        return exchange;
    }

    private void reopenStore() throws Exception
    {
        if (_store != null)
        {
            _store.close();
        }
        _store = createStore();

        _store.configureConfigStore(_storeName, _recoveryHandler, _configuration);
        _store.configureMessageStore(_storeName, _messageStoreRecoveryHandler, _logRecoveryHandler, _configuration);
        _store.activate();
    }

    protected MessageStore createStore() throws Exception
    {
        String storeFactoryClass = System.getProperty(MS_FACTORY_CLASS_NAME_KEY);
        if (storeFactoryClass == null)
        {
            storeFactoryClass = DerbyMessageStoreFactory.class.getName();
        }
        CurrentActor.set(new TestLogActor(new SystemOutMessageLogger()));
        MessageStoreFactory factory = (MessageStoreFactory) Class.forName(storeFactoryClass).newInstance();
        return factory.createMessageStore();
    }

    public void testRecordXid() throws Exception
    {
        Record enqueueRecord = getTestRecord(1);
        Record dequeueRecord = getTestRecord(2);
        Record[] enqueues = { enqueueRecord };
        Record[] dequeues = { dequeueRecord };
        byte[] globalId = new byte[] { 1 };
        byte[] branchId = new byte[] { 2 };

        Transaction transaction = _store.newTransaction();
        transaction.recordXid(1l, globalId, branchId, enqueues, dequeues);
        transaction.commitTran();
        reopenStore();
        verify(_dtxRecordRecoveryHandler).dtxRecord(1l, globalId, branchId, enqueues, dequeues);

        transaction = _store.newTransaction();
        transaction.removeXid(1l, globalId, branchId);
        transaction.commitTran();

        reopenStore();
        verify(_dtxRecordRecoveryHandler, times(1)).dtxRecord(1l, globalId, branchId, enqueues, dequeues);
    }

    private Record getTestRecord(long messageNumber)
    {
        UUID queueId1 = UUIDGenerator.generateUUID();
        TransactionLogResource queue1 = mock(TransactionLogResource.class);
        when(queue1.getId()).thenReturn(queueId1);
        EnqueableMessage message1 = mock(EnqueableMessage.class);
        when(message1.isPersistent()).thenReturn(true);
        when(message1.getMessageNumber()).thenReturn(messageNumber);
        when(message1.getStoredMessage()).thenReturn(new MockStoredMessage(messageNumber));
        Record enqueueRecord = new TestRecord(queue1, message1);
        return enqueueRecord;
    }

    private static class TestRecord implements Record
    {
        private TransactionLogResource _queue;
        private EnqueableMessage _message;

        public TestRecord(TransactionLogResource queue, EnqueableMessage message)
        {
            super();
            _queue = queue;
            _message = message;
        }

        @Override
        public TransactionLogResource getQueue()
        {
            return _queue;
        }

        @Override
        public EnqueableMessage getMessage()
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
            if (_queue == null && other.getQueue() != null)
            {
                return false;
            }
            if (_message.getMessageNumber() != other.getMessage().getMessageNumber())
            {
                return false;
            }
            return _queue.getId().equals(other.getQueue().getId());
        }

    }
}
