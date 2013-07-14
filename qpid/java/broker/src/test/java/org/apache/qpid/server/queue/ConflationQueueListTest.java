/*
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
package org.apache.qpid.server.queue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import junit.framework.TestCase;

import org.apache.qpid.server.protocol.v0_8.AMQMessage;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.protocol.v0_8.AMQMessageReference;
import org.apache.qpid.server.protocol.v0_8.MessageMetaData;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class ConflationQueueListTest extends TestCase
{
    private static final String CONFLATION_KEY = "CONFLATION_KEY";

    private static final String TEST_KEY_VALUE = "testKeyValue";
    private static final String TEST_KEY_VALUE1 = "testKeyValue1";
    private static final String TEST_KEY_VALUE2 = "testKeyValue2";

    private ConflationQueueList _list;
    private AMQQueue _queue = createTestQueue();

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _list = new ConflationQueueList(_queue, CONFLATION_KEY);
    }

    public void testListHasNoEntries()
    {
        int numberOfEntries = countEntries(_list);
        assertEquals(0, numberOfEntries);
    }

    public void testAddMessageWithoutConflationKeyValue()
    {
        ServerMessage<MessageMetaData> message = createTestServerMessage(null);

        _list.add(message);
        int numberOfEntries = countEntries(_list);
        assertEquals(1, numberOfEntries);
    }

    public void testAddAndDiscardMessageWithoutConflationKeyValue()
    {
        ServerMessage<MessageMetaData> message = createTestServerMessage(null);

        QueueEntry addedEntry = _list.add(message);
        addedEntry.discard();

        int numberOfEntries = countEntries(_list);
        assertEquals(0, numberOfEntries);
    }

    public void testAddMessageWithConflationKeyValue()
    {
        ServerMessage<MessageMetaData> message = createTestServerMessage(TEST_KEY_VALUE);

        _list.add(message);
        int numberOfEntries = countEntries(_list);
        assertEquals(1, numberOfEntries);
    }

    public void testAddAndRemoveMessageWithConflationKeyValue()
    {
        ServerMessage<MessageMetaData> message = createTestServerMessage(TEST_KEY_VALUE);

        QueueEntry addedEntry = _list.add(message);
        addedEntry.discard();

        int numberOfEntries = countEntries(_list);
        assertEquals(0, numberOfEntries);
    }

    public void testAddTwoMessagesWithDifferentConflationKeyValue()
    {
        ServerMessage<MessageMetaData> message1 = createTestServerMessage(TEST_KEY_VALUE1);
        ServerMessage<MessageMetaData> message2 = createTestServerMessage(TEST_KEY_VALUE2);

        _list.add(message1);
        _list.add(message2);

        int numberOfEntries = countEntries(_list);
        assertEquals(2, numberOfEntries);
    }

    public void testAddTwoMessagesWithSameConflationKeyValue()
    {
        ServerMessage<MessageMetaData> message1 = createTestServerMessage(TEST_KEY_VALUE);
        ServerMessage<MessageMetaData> message2 = createTestServerMessage(TEST_KEY_VALUE);

        _list.add(message1);
        _list.add(message2);

        int numberOfEntries = countEntries(_list);
        assertEquals(1, numberOfEntries);
    }

    public void testSupersededEntryIsDiscardedOnRelease()
    {
        ServerMessage<MessageMetaData> message1 = createTestServerMessage(TEST_KEY_VALUE);
        ServerMessage<MessageMetaData> message2 = createTestServerMessage(TEST_KEY_VALUE);

        QueueEntry entry1 = _list.add(message1);
        entry1.acquire(); // simulate an in-progress delivery to consumer

        _list.add(message2);
        assertFalse(entry1.isDeleted());

        assertEquals(2, countEntries(_list));

        entry1.release(); // simulate consumer rollback/recover

        assertEquals(1, countEntries(_list));
        assertTrue(entry1.isDeleted());
    }

    public void testConflationMapMaintained()
    {
        assertEquals(0, _list.getLatestValuesMap().size());

        ServerMessage<MessageMetaData> message = createTestServerMessage(TEST_KEY_VALUE);

        QueueEntry addedEntry = _list.add(message);

        assertEquals(1, countEntries(_list));
        assertEquals(1, _list.getLatestValuesMap().size());

        addedEntry.discard();

        assertEquals(0, countEntries(_list));
        assertEquals(0, _list.getLatestValuesMap().size());
    }

    public void testConflationMapMaintainedWithDifferentConflationKeyValue()
    {

        assertEquals(0, _list.getLatestValuesMap().size());

        ServerMessage<MessageMetaData> message1 = createTestServerMessage(TEST_KEY_VALUE1);
        ServerMessage<MessageMetaData> message2 = createTestServerMessage(TEST_KEY_VALUE2);

        QueueEntry addedEntry1 = _list.add(message1);
        QueueEntry addedEntry2 = _list.add(message2);

        assertEquals(2, countEntries(_list));
        assertEquals(2, _list.getLatestValuesMap().size());

        addedEntry1.discard();
        addedEntry2.discard();

        assertEquals(0, countEntries(_list));
        assertEquals(0, _list.getLatestValuesMap().size());
    }

    private int countEntries(ConflationQueueList list)
    {
        QueueEntryIterator<SimpleQueueEntryImpl> iterator = list.iterator();
        int count = 0;
        while(iterator.advance())
        {
            count++;
        }
        return count;
    }

    private ServerMessage<MessageMetaData> createTestServerMessage(String conflationKeyValue)
    {
        AMQMessage mockMessage = mock(AMQMessage.class);

        AMQMessageHeader messageHeader = mock(AMQMessageHeader.class);
        when(messageHeader.getHeader(CONFLATION_KEY)).thenReturn(conflationKeyValue);
        when(mockMessage.getMessageHeader()).thenReturn(messageHeader);

        AMQMessageReference messageReference = new AMQMessageReference(mockMessage);
        when(mockMessage.newReference()).thenReturn(messageReference);

        return mockMessage;
    }

    private AMQQueue createTestQueue()
    {
        AMQQueue queue = mock(AMQQueue.class);
        VirtualHost virtualHost = mock(VirtualHost.class);
        when(queue.getVirtualHost()).thenReturn(virtualHost);

        return queue;
    }
}
