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
package org.apache.qpid.server.protocol.v0_8;

import java.util.UUID;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.MessagePublishInfo;
import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.store.MessageCounter;
import org.apache.qpid.server.store.MessageDurability;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.TestMemoryMessageStore;
import org.apache.qpid.server.store.Transaction;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.test.utils.QpidTestCase;

/**
 * Tests that reference counting works correctly with AMQMessage and the message store
 */
public class ReferenceCountingTest extends QpidTestCase
{
    private TestMemoryMessageStore _store;


    protected void setUp() throws Exception
    {
        _store = new TestMemoryMessageStore();
    }

    /**
     * Check that when the reference count is decremented the message removes itself from the store
     */
    public void testMessageGetsRemoved() throws AMQException
    {
        ContentHeaderBody chb = createPersistentContentHeader();

        MessagePublishInfo info = new MessagePublishInfo(null, false, false, null);

        final MessageMetaData mmd = new MessageMetaData(info, chb);

        StoredMessage storedMessage = _store.addMessage(mmd);
        Transaction txn = _store.newTransaction();
        txn.enqueueMessage(createTransactionLogResource("dummyQ"), createEnqueueableMessage(storedMessage));
        txn.commitTran();
        AMQMessage message = new AMQMessage(storedMessage);

        MessageReference ref = message.newReference();

        assertEquals(1, getStoreMessageCount());

        ref.release();

        assertEquals(0, getStoreMessageCount());
    }

    private int getStoreMessageCount()
    {
        MessageCounter counter = new MessageCounter();
        _store.visitMessages(counter);
        return counter.getCount();
    }

    private ContentHeaderBody createPersistentContentHeader()
    {
        BasicContentHeaderProperties bchp = new BasicContentHeaderProperties();
        bchp.setDeliveryMode((byte)2);
        ContentHeaderBody chb = new ContentHeaderBody(bchp);
        return chb;
    }

    public void testMessageRemains() throws AMQException
    {

        MessagePublishInfo info = new MessagePublishInfo(null, false, false, null);

        final ContentHeaderBody chb = createPersistentContentHeader();

        final MessageMetaData mmd = new MessageMetaData(info, chb);

        StoredMessage storedMessage = _store.addMessage(mmd);
        Transaction txn = _store.newTransaction();
        txn.enqueueMessage(createTransactionLogResource("dummyQ"), createEnqueueableMessage(storedMessage));
        txn.commitTran();
        AMQMessage message = new AMQMessage(storedMessage);


        MessageReference ref = message.newReference();

        assertEquals(1, getStoreMessageCount());
        MessageReference ref2 = message.newReference();
        ref.release();
        assertEquals(1, getStoreMessageCount());
    }

    private TransactionLogResource createTransactionLogResource(final String queueName)
    {
        return new TransactionLogResource()
        {
            @Override
            public String getName()
            {
                return queueName;
            }

            @Override
            public UUID getId()
            {
                return UUID.nameUUIDFromBytes(queueName.getBytes());
            }

            @Override
            public MessageDurability getMessageDurability()
            {
                return MessageDurability.DEFAULT;
            }
        };
    }

    private EnqueueableMessage createEnqueueableMessage(final StoredMessage storedMessage)
    {
        return new EnqueueableMessage()
        {
            @Override
            public long getMessageNumber()
            {
                return storedMessage.getMessageNumber();
            }

            @Override
            public boolean isPersistent()
            {
                return true;
            }

            @Override
            public StoredMessage getStoredMessage()
            {
                return storedMessage;
            }
        };
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(ReferenceCountingTest.class);
    }
}
