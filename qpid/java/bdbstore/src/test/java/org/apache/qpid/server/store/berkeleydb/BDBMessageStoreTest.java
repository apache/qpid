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
package org.apache.qpid.server.store.berkeleydb;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.message.MessageMetaData;
import org.apache.qpid.server.message.MessageMetaData_0_10;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.store.MessageMetaDataType;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.Transaction;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;
import org.apache.qpid.transport.MessageDeliveryMode;
import org.apache.qpid.transport.MessageDeliveryPriority;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.MessageTransfer;

/**
 * Subclass of MessageStoreTest which runs the standard tests from the superclass against
 * the BDB Store as well as additional tests specific to the BDB store-implementation.
 */
public class BDBMessageStoreTest extends org.apache.qpid.server.store.MessageStoreTest
{
    private static byte[] CONTENT_BYTES = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

    /**
     * Tests that message metadata and content are successfully read back from a
     * store after it has been reloaded. Both 0-8 and 0-10 metadata is used to
     * verify their ability to co-exist within the store and be successful retrieved.
     */
    public void testBDBMessagePersistence() throws Exception
    {
        MessageStore store = getVirtualHost().getMessageStore();

        AbstractBDBMessageStore bdbStore = assertBDBStore(store);

        // Create content ByteBuffers.
        // Split the content into 2 chunks for the 0-8 message, as per broker behaviour.
        // Use a single chunk for the 0-10 message as per broker behaviour.
        String bodyText = "jfhdjsflsdhfjdshfjdslhfjdslhfsjlhfsjkhfdsjkhfdsjkfhdslkjf";

        ByteBuffer firstContentBytes_0_8 = ByteBuffer.wrap(bodyText.substring(0, 10).getBytes());
        ByteBuffer secondContentBytes_0_8 = ByteBuffer.wrap(bodyText.substring(10).getBytes());

        ByteBuffer completeContentBody_0_10 = ByteBuffer.wrap(bodyText.getBytes());
        int bodySize = completeContentBody_0_10.limit();

        /*
         * Create and insert a 0-8 message (metadata and multi-chunk content)
         */
        MessagePublishInfo pubInfoBody_0_8 = createPublishInfoBody_0_8();
        BasicContentHeaderProperties props_0_8 = createContentHeaderProperties_0_8();

        ContentHeaderBody chb_0_8 = createContentHeaderBody_0_8(props_0_8, bodySize);

        MessageMetaData messageMetaData_0_8 = new MessageMetaData(pubInfoBody_0_8, chb_0_8, 0);
        StoredMessage<MessageMetaData> storedMessage_0_8 = bdbStore.addMessage(messageMetaData_0_8);

        long origArrivalTime_0_8 = messageMetaData_0_8.getArrivalTime();
        long messageid_0_8 = storedMessage_0_8.getMessageNumber();

        storedMessage_0_8.addContent(0, firstContentBytes_0_8);
        storedMessage_0_8.addContent(firstContentBytes_0_8.limit(), secondContentBytes_0_8);
        storedMessage_0_8.flushToStore();

        /*
         * Create and insert a 0-10 message (metadata and content)
         */
        MessageProperties msgProps_0_10 = createMessageProperties_0_10(bodySize);
        DeliveryProperties delProps_0_10 = createDeliveryProperties_0_10();
        Header header_0_10 = new Header(delProps_0_10, msgProps_0_10);

        MessageTransfer xfr_0_10 = new MessageTransfer("destination", MessageAcceptMode.EXPLICIT,
                MessageAcquireMode.PRE_ACQUIRED, header_0_10, completeContentBody_0_10);

        MessageMetaData_0_10 messageMetaData_0_10 = new MessageMetaData_0_10(xfr_0_10);
        StoredMessage<MessageMetaData_0_10> storedMessage_0_10 = bdbStore.addMessage(messageMetaData_0_10);

        long origArrivalTime_0_10 = messageMetaData_0_10.getArrivalTime();
        long messageid_0_10 = storedMessage_0_10.getMessageNumber();

        storedMessage_0_10.addContent(0, completeContentBody_0_10);
        storedMessage_0_10.flushToStore();

        /*
         * reload the store only (read-only)
         */
        bdbStore = reloadStore(bdbStore);

        /*
         * Read back and validate the 0-8 message metadata and content
         */
        StorableMessageMetaData storeableMMD_0_8 = bdbStore.getMessageMetaData(messageid_0_8);

        assertEquals("Unexpected message type",MessageMetaDataType.META_DATA_0_8, storeableMMD_0_8.getType());
        assertTrue("Unexpected instance type", storeableMMD_0_8 instanceof MessageMetaData);
        MessageMetaData returnedMMD_0_8 = (MessageMetaData) storeableMMD_0_8;

        assertEquals("Message arrival time has changed", origArrivalTime_0_8, returnedMMD_0_8.getArrivalTime());

        MessagePublishInfo returnedPubBody_0_8 = returnedMMD_0_8.getMessagePublishInfo();
        assertEquals("Message exchange has changed", pubInfoBody_0_8.getExchange(), returnedPubBody_0_8.getExchange());
        assertEquals("Immediate flag has changed", pubInfoBody_0_8.isImmediate(), returnedPubBody_0_8.isImmediate());
        assertEquals("Mandatory flag has changed", pubInfoBody_0_8.isMandatory(), returnedPubBody_0_8.isMandatory());
        assertEquals("Routing key has changed", pubInfoBody_0_8.getRoutingKey(), returnedPubBody_0_8.getRoutingKey());

        ContentHeaderBody returnedHeaderBody_0_8 = returnedMMD_0_8.getContentHeaderBody();
        assertEquals("ContentHeader ClassID has changed", chb_0_8.getClassId(), returnedHeaderBody_0_8.getClassId());
        assertEquals("ContentHeader weight has changed", chb_0_8.getWeight(), returnedHeaderBody_0_8.getWeight());
        assertEquals("ContentHeader bodySize has changed", chb_0_8.getBodySize(), returnedHeaderBody_0_8.getBodySize());

        BasicContentHeaderProperties returnedProperties_0_8 = (BasicContentHeaderProperties) returnedHeaderBody_0_8.getProperties();
        assertEquals("Property ContentType has changed", props_0_8.getContentTypeAsString(), returnedProperties_0_8.getContentTypeAsString());
        assertEquals("Property MessageID has changed", props_0_8.getMessageIdAsString(), returnedProperties_0_8.getMessageIdAsString());

        ByteBuffer recoveredContent_0_8 = ByteBuffer.allocate((int) chb_0_8.getBodySize()) ;
        long recoveredCount_0_8 = bdbStore.getContent(messageid_0_8, 0, recoveredContent_0_8);
        assertEquals("Incorrect amount of payload data recovered", chb_0_8.getBodySize(), recoveredCount_0_8);
        String returnedPayloadString_0_8 = new String(recoveredContent_0_8.array());
        assertEquals("Message Payload has changed", bodyText, returnedPayloadString_0_8);

        /*
         * Read back and validate the 0-10 message metadata and content
         */
        StorableMessageMetaData storeableMMD_0_10 = bdbStore.getMessageMetaData(messageid_0_10);

        assertEquals("Unexpected message type",MessageMetaDataType.META_DATA_0_10, storeableMMD_0_10.getType());
        assertTrue("Unexpected instance type", storeableMMD_0_10 instanceof MessageMetaData_0_10);
        MessageMetaData_0_10 returnedMMD_0_10 = (MessageMetaData_0_10) storeableMMD_0_10;

        assertEquals("Message arrival time has changed", origArrivalTime_0_10, returnedMMD_0_10.getArrivalTime());

        DeliveryProperties returnedDelProps_0_10 = returnedMMD_0_10.getHeader().getDeliveryProperties();
        assertNotNull("DeliveryProperties were not returned", returnedDelProps_0_10);
        assertEquals("Immediate flag has changed", delProps_0_10.getImmediate(), returnedDelProps_0_10.getImmediate());
        assertEquals("Routing key has changed", delProps_0_10.getRoutingKey(), returnedDelProps_0_10.getRoutingKey());
        assertEquals("Message exchange has changed", delProps_0_10.getExchange(), returnedDelProps_0_10.getExchange());
        assertEquals("Message expiration has changed", delProps_0_10.getExpiration(), returnedDelProps_0_10.getExpiration());
        assertEquals("Message delivery priority has changed", delProps_0_10.getPriority(), returnedDelProps_0_10.getPriority());

        MessageProperties returnedMsgProps = returnedMMD_0_10.getHeader().getMessageProperties();
        assertNotNull("MessageProperties were not returned", returnedMsgProps);
        assertTrue("Message correlationID has changed", Arrays.equals(msgProps_0_10.getCorrelationId(), returnedMsgProps.getCorrelationId()));
        assertEquals("Message content length has changed", msgProps_0_10.getContentLength(), returnedMsgProps.getContentLength());
        assertEquals("Message content type has changed", msgProps_0_10.getContentType(), returnedMsgProps.getContentType());

        ByteBuffer recoveredContent = ByteBuffer.allocate((int) msgProps_0_10.getContentLength()) ;
        long recoveredCount = bdbStore.getContent(messageid_0_10, 0, recoveredContent);
        assertEquals("Incorrect amount of payload data recovered", msgProps_0_10.getContentLength(), recoveredCount);

        String returnedPayloadString_0_10 = new String(recoveredContent.array());
        assertEquals("Message Payload has changed", bodyText, returnedPayloadString_0_10);
    }

    private DeliveryProperties createDeliveryProperties_0_10()
    {
        DeliveryProperties delProps_0_10 = new DeliveryProperties();

        delProps_0_10.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        delProps_0_10.setImmediate(true);
        delProps_0_10.setExchange("exchange12345");
        delProps_0_10.setRoutingKey("routingKey12345");
        delProps_0_10.setExpiration(5);
        delProps_0_10.setPriority(MessageDeliveryPriority.ABOVE_AVERAGE);

        return delProps_0_10;
    }

    private MessageProperties createMessageProperties_0_10(int bodySize)
    {
        MessageProperties msgProps_0_10 = new MessageProperties();
        msgProps_0_10.setContentLength(bodySize);
        msgProps_0_10.setCorrelationId("qwerty".getBytes());
        msgProps_0_10.setContentType("text/html");

        return msgProps_0_10;
    }

    /**
     * Close the provided store and create a new (read-only) store to read back the data.
     *
     * Use this method instead of reloading the virtual host like other tests in order
     * to avoid the recovery handler deleting the message for not being on a queue.
     */
    private AbstractBDBMessageStore reloadStore(AbstractBDBMessageStore messageStore) throws Exception
    {
        messageStore.close();

        AbstractBDBMessageStore newStore = new BDBMessageStore();
        newStore.configure("", getConfig().subset("store"));

        newStore.startWithNoRecover();

        return newStore;
    }

    private MessagePublishInfo createPublishInfoBody_0_8()
    {
        return new MessagePublishInfo()
        {
            public AMQShortString getExchange()
            {
                return new AMQShortString("exchange12345");
            }

            public void setExchange(AMQShortString exchange)
            {
            }

            public boolean isImmediate()
            {
                return false;
            }

            public boolean isMandatory()
            {
                return true;
            }

            public AMQShortString getRoutingKey()
            {
                return new AMQShortString("routingKey12345");
            }
        };

    }

    private ContentHeaderBody createContentHeaderBody_0_8(BasicContentHeaderProperties props, int length)
    {
        MethodRegistry methodRegistry = MethodRegistry.getMethodRegistry(ProtocolVersion.v0_9);
        int classForBasic = methodRegistry.createBasicQosOkBody().getClazz();
        return new ContentHeaderBody(classForBasic, 1, props, length);
    }

    private BasicContentHeaderProperties createContentHeaderProperties_0_8()
    {
        BasicContentHeaderProperties props = new BasicContentHeaderProperties();
        props.setDeliveryMode(Integer.valueOf(BasicContentHeaderProperties.PERSISTENT).byteValue());
        props.setContentType("text/html");
        props.getHeaders().setString("Test", "MST");
        return props;
    }

    public void testGetContentWithOffset() throws Exception
    {
        MessageStore store = getVirtualHost().getMessageStore();
        AbstractBDBMessageStore bdbStore = assertBDBStore(store);
        StoredMessage<MessageMetaData> storedMessage_0_8 = createAndStoreSingleChunkMessage_0_8(store);
        long messageid_0_8 = storedMessage_0_8.getMessageNumber();

        // normal case: offset is 0
        ByteBuffer dst = ByteBuffer.allocate(10);
        int length = bdbStore.getContent(messageid_0_8, 0, dst);
        assertEquals("Unexpected length", CONTENT_BYTES.length, length);
        byte[] array = dst.array();
        assertTrue("Unexpected content", Arrays.equals(CONTENT_BYTES, array));

        // offset is in the middle
        dst = ByteBuffer.allocate(10);
        length = bdbStore.getContent(messageid_0_8, 5, dst);
        assertEquals("Unexpected length", 5, length);
        array = dst.array();
        byte[] expected = new byte[10];
        System.arraycopy(CONTENT_BYTES, 5, expected, 0, 5);
        assertTrue("Unexpected content", Arrays.equals(expected, array));

        // offset beyond the content length
        dst = ByteBuffer.allocate(10);
        try
        {
            bdbStore.getContent(messageid_0_8, 15, dst);
            fail("Should fail for the offset greater than message size");
        }
        catch (RuntimeException e)
        {
            assertEquals("Unexpected exception message", "Offset 15 is greater than message size 10 for message id "
                    + messageid_0_8 + "!", e.getMessage());
        }

        // buffer is smaller then message size
        dst = ByteBuffer.allocate(5);
        length = bdbStore.getContent(messageid_0_8, 0, dst);
        assertEquals("Unexpected length", 5, length);
        array = dst.array();
        expected = new byte[5];
        System.arraycopy(CONTENT_BYTES, 0, expected, 0, 5);
        assertTrue("Unexpected content", Arrays.equals(expected, array));

        // buffer is smaller then message size, offset is not 0
        dst = ByteBuffer.allocate(5);
        length = bdbStore.getContent(messageid_0_8, 2, dst);
        assertEquals("Unexpected length", 5, length);
        array = dst.array();
        expected = new byte[5];
        System.arraycopy(CONTENT_BYTES, 2, expected, 0, 5);
        assertTrue("Unexpected content", Arrays.equals(expected, array));
    }
    /**
     * Tests that messages which are added to the store and then removed using the
     * public MessageStore interfaces are actually removed from the store by then
     * interrogating the store with its own implementation methods and verifying
     * expected exceptions are thrown to indicate the message is not present.
     */
    public void testMessageCreationAndRemoval() throws Exception
    {
        MessageStore store = getVirtualHost().getMessageStore();
        AbstractBDBMessageStore bdbStore = assertBDBStore(store);

        StoredMessage<MessageMetaData> storedMessage_0_8 = createAndStoreSingleChunkMessage_0_8(store);
        long messageid_0_8 = storedMessage_0_8.getMessageNumber();

        bdbStore.removeMessage(messageid_0_8, true);

        //verify the removal using the BDB store implementation methods directly
        try
        {
            // the next line should throw since the message id should not be found
            bdbStore.getMessageMetaData(messageid_0_8);
            fail("No exception thrown when message id not found getting metadata");
        }
        catch (AMQStoreException e)
        {
            // pass since exception expected
        }

        //expecting no content, allocate a 1 byte
        ByteBuffer dst = ByteBuffer.allocate(1);

        assertEquals("Retrieved content when none was expected",
                        0, bdbStore.getContent(messageid_0_8, 0, dst));
    }
    private AbstractBDBMessageStore assertBDBStore(MessageStore store)
    {

        assertEquals("Test requires an instance of BDBMessageStore to proceed", BDBMessageStore.class, store.getClass());

        return (AbstractBDBMessageStore) store;
    }

    private StoredMessage<MessageMetaData> createAndStoreSingleChunkMessage_0_8(MessageStore store)
    {
        ByteBuffer chunk1 = ByteBuffer.wrap(CONTENT_BYTES);

        int bodySize = CONTENT_BYTES.length;

        //create and store the message using the MessageStore interface
        MessagePublishInfo pubInfoBody_0_8 = createPublishInfoBody_0_8();
        BasicContentHeaderProperties props_0_8 = createContentHeaderProperties_0_8();

        ContentHeaderBody chb_0_8 = createContentHeaderBody_0_8(props_0_8, bodySize);

        MessageMetaData messageMetaData_0_8 = new MessageMetaData(pubInfoBody_0_8, chb_0_8, 0);
        StoredMessage<MessageMetaData> storedMessage_0_8 = store.addMessage(messageMetaData_0_8);

        storedMessage_0_8.addContent(0, chunk1);
        storedMessage_0_8.flushToStore();

        return storedMessage_0_8;
    }

    /**
     * Tests transaction commit by utilising the enqueue and dequeue methods available
     * in the TransactionLog interface implemented by the store, and verifying the
     * behaviour using BDB implementation methods.
     */
    public void testTranCommit() throws Exception
    {
        MessageStore log = getVirtualHost().getMessageStore();

        AbstractBDBMessageStore bdbStore = assertBDBStore(log);

        final UUID mockQueueId = UUIDGenerator.generateRandomUUID();
        TransactionLogResource mockQueue = new TransactionLogResource()
        {
            @Override
            public UUID getId()
            {
                return mockQueueId;
            }
        };

        Transaction txn = log.newTransaction();

        txn.enqueueMessage(mockQueue, new MockMessage(1L));
        txn.enqueueMessage(mockQueue, new MockMessage(5L));
        txn.commitTran();

        List<Long> enqueuedIds = bdbStore.getEnqueuedMessages(mockQueueId);

        assertEquals("Number of enqueued messages is incorrect", 2, enqueuedIds.size());
        Long val = enqueuedIds.get(0);
        assertEquals("First Message is incorrect", 1L, val.longValue());
        val = enqueuedIds.get(1);
        assertEquals("Second Message is incorrect", 5L, val.longValue());
    }


    /**
     * Tests transaction rollback before a commit has occurred by utilising the
     * enqueue and dequeue methods available in the TransactionLog interface
     * implemented by the store, and verifying the behaviour using BDB
     * implementation methods.
     */
    public void testTranRollbackBeforeCommit() throws Exception
    {
        MessageStore log = getVirtualHost().getMessageStore();

        AbstractBDBMessageStore bdbStore = assertBDBStore(log);

        final UUID mockQueueId = UUIDGenerator.generateRandomUUID();
        TransactionLogResource mockQueue = new TransactionLogResource()
        {
            @Override
            public UUID getId()
            {
                return mockQueueId;
            }
        };

        Transaction txn = log.newTransaction();

        txn.enqueueMessage(mockQueue, new MockMessage(21L));
        txn.abortTran();

        txn = log.newTransaction();
        txn.enqueueMessage(mockQueue, new MockMessage(22L));
        txn.enqueueMessage(mockQueue, new MockMessage(23L));
        txn.commitTran();

        List<Long> enqueuedIds = bdbStore.getEnqueuedMessages(mockQueueId);

        assertEquals("Number of enqueued messages is incorrect", 2, enqueuedIds.size());
        Long val = enqueuedIds.get(0);
        assertEquals("First Message is incorrect", 22L, val.longValue());
        val = enqueuedIds.get(1);
        assertEquals("Second Message is incorrect", 23L, val.longValue());
    }

    /**
     * Tests transaction rollback after a commit has occurred by utilising the
     * enqueue and dequeue methods available in the TransactionLog interface
     * implemented by the store, and verifying the behaviour using BDB
     * implementation methods.
     */
    public void testTranRollbackAfterCommit() throws Exception
    {
        MessageStore log = getVirtualHost().getMessageStore();

        AbstractBDBMessageStore bdbStore = assertBDBStore(log);

        final UUID mockQueueId = UUIDGenerator.generateRandomUUID();
        TransactionLogResource mockQueue = new TransactionLogResource()
        {
            @Override
            public UUID getId()
            {
                return mockQueueId;
            }
        };

        Transaction txn = log.newTransaction();

        txn.enqueueMessage(mockQueue, new MockMessage(30L));
        txn.commitTran();

        txn = log.newTransaction();
        txn.enqueueMessage(mockQueue, new MockMessage(31L));
        txn.abortTran();

        txn = log.newTransaction();
        txn.enqueueMessage(mockQueue, new MockMessage(32L));
        txn.commitTran();

        List<Long> enqueuedIds = bdbStore.getEnqueuedMessages(mockQueueId);

        assertEquals("Number of enqueued messages is incorrect", 2, enqueuedIds.size());
        Long val = enqueuedIds.get(0);
        assertEquals("First Message is incorrect", 30L, val.longValue());
        val = enqueuedIds.get(1);
        assertEquals("Second Message is incorrect", 32L, val.longValue());
    }

    @SuppressWarnings("rawtypes")
    private static class MockMessage implements ServerMessage, EnqueableMessage
    {
        private long _messageId;

        public MockMessage(long messageId)
        {
            _messageId = messageId;
        }

        public String getRoutingKey()
        {
            return null;
        }

        public AMQMessageHeader getMessageHeader()
        {
            return null;
        }

        public StoredMessage getStoredMessage()
        {
            return null;
        }

        public boolean isPersistent()
        {
            return true;
        }

        public long getSize()
        {
            return 0;
        }

        public boolean isImmediate()
        {
            return false;
        }

        public long getExpiration()
        {
            return 0;
        }

        public MessageReference newReference()
        {
            return null;
        }

        public long getMessageNumber()
        {
            return _messageId;
        }

        public long getArrivalTime()
        {
            return 0;
        }

        public int getContent(ByteBuffer buf, int offset)
        {
            return 0;
        }

        public ByteBuffer getContent(int offset, int length)
        {
            return null;
        }
    }
}
