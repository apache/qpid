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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

public abstract class MessageStoreQuotaEventsTestBase extends QpidTestCase implements EventListener, TransactionLogResource
{
    private static final Logger _logger = Logger.getLogger(MessageStoreQuotaEventsTestBase.class);

    protected static final byte[] MESSAGE_DATA = new byte[32 * 1024];

    private MessageStore _store;
    private File _storeLocation;

    private List<Event> _events;
    private UUID _transactionResource;

    protected abstract MessageStore createStore() throws Exception;
    protected abstract Map<String, Object> createStoreSettings(String storeLocation);
    protected abstract Map<String, String> createContextSettings();
    protected abstract int getNumberOfMessagesToFillStore();

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        _storeLocation = new File(new File(TMP_FOLDER), getTestName());
        FileUtils.delete(_storeLocation, true);


        Map<String, Object> storeSettings = createStoreSettings(_storeLocation.getAbsolutePath());

        _store = createStore();

        ConfiguredObject<?> parent = mock(ConfiguredObject.class);
        when(parent.getName()).thenReturn("test");
        when(parent.getContext()).thenReturn(createContextSettings());

        _store.openMessageStore(parent, storeSettings);

        _transactionResource = UUID.randomUUID();
        _events = new ArrayList<Event>();
        _store.addEventListener(this, Event.PERSISTENT_MESSAGE_SIZE_OVERFULL, Event.PERSISTENT_MESSAGE_SIZE_UNDERFULL);
    }


    @Override
    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            if (_store != null)
            {
                _store.closeMessageStore();
            }
            FileUtils.delete(_storeLocation, true);
        }
    }

    public void testOverflow() throws Exception
    {
        Transaction transaction = _store.newTransaction();

        List<EnqueueableMessage> messages = new ArrayList<EnqueueableMessage>();
        for (int i = 0; i < getNumberOfMessagesToFillStore(); i++)
        {
            EnqueueableMessage m = addMessage(i);
            messages.add(m);
            transaction.enqueueMessage(this, m);
        }
        transaction.commitTran();

        assertEvent(1, Event.PERSISTENT_MESSAGE_SIZE_OVERFULL);

        for (EnqueueableMessage m : messages)
        {
            m.getStoredMessage().remove();
        }

        assertEvent(2, Event.PERSISTENT_MESSAGE_SIZE_UNDERFULL);
    }

    protected EnqueueableMessage addMessage(long id)
    {
        StorableMessageMetaData metaData = createMetaData(id, MESSAGE_DATA.length);
        StoredMessage<?> handle = _store.addMessage(metaData);
        handle.addContent(0, ByteBuffer.wrap(MESSAGE_DATA));
        TestMessage message = new TestMessage(id, handle);
        return message;
    }

    private StorableMessageMetaData createMetaData(long id, int length)
    {
        return new TestMessageMetaData(id, length);
    }

    @Override
    public void event(Event event)
    {
        _logger.debug("Test event listener received event " + event);
        _events.add(event);
    }

    private void assertEvent(int expectedNumberOfEvents, Event... expectedEvents)
    {
        assertEquals("Unexpected number of events received ", expectedNumberOfEvents, _events.size());
        for (Event event : expectedEvents)
        {
            assertTrue("Expected event is not found:" + event, _events.contains(event));
        }
    }

    @Override
    public UUID getId()
    {
        return _transactionResource;
    }

    @Override
    public boolean isDurable()
    {
        return true;
    }

    private static class TestMessage implements EnqueueableMessage
    {
        private final StoredMessage<?> _handle;
        private final long _messageId;

        public TestMessage(long messageId, StoredMessage<?> handle)
        {
            _messageId = messageId;
            _handle = handle;
        }

        public long getMessageNumber()
        {
            return _messageId;
        }

        public boolean isPersistent()
        {
            return true;
        }

        public StoredMessage<?> getStoredMessage()
        {
            return _handle;
        }
    }
}
