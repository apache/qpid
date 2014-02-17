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

import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ConflationQueueListTest extends TestCase
{
    private static final String CONFLATION_KEY = "CONFLATION_KEY";

    private static final String TEST_KEY_VALUE = "testKeyValue";
    private static final String TEST_KEY_VALUE1 = "testKeyValue1";
    private static final String TEST_KEY_VALUE2 = "testKeyValue2";

    private ConflationQueueList _list;
    private ConflationQueue _queue;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        Map<String,Object> queueAttributes = new HashMap<String, Object>();
        queueAttributes.put(Queue.ID, UUID.randomUUID());
        queueAttributes.put(Queue.NAME, getName());
        queueAttributes.put(Queue.LVQ_KEY, CONFLATION_KEY);
        _queue = new ConflationQueue(mock(VirtualHost.class), null, queueAttributes);
        _list = _queue.getEntries();
    }

    public void testListHasNoEntries()
    {
        int numberOfEntries = countEntries(_list);
        assertEquals(0, numberOfEntries);
    }

    public void testAddMessageWithoutConflationKeyValue()
    {
        ServerMessage message = createTestServerMessage(null);

        _list.add(message);
        int numberOfEntries = countEntries(_list);
        assertEquals(1, numberOfEntries);
    }

    public void testAddAndDiscardMessageWithoutConflationKeyValue()
    {
        ServerMessage message = createTestServerMessage(null);

        QueueEntry addedEntry = _list.add(message);
        addedEntry.delete();

        int numberOfEntries = countEntries(_list);
        assertEquals(0, numberOfEntries);
    }

    public void testAddMessageWithConflationKeyValue()
    {
        ServerMessage message = createTestServerMessage(TEST_KEY_VALUE);

        _list.add(message);
        int numberOfEntries = countEntries(_list);
        assertEquals(1, numberOfEntries);
    }

    public void testAddAndRemoveMessageWithConflationKeyValue()
    {
        ServerMessage message = createTestServerMessage(TEST_KEY_VALUE);

        QueueEntry addedEntry = _list.add(message);
        addedEntry.delete();

        int numberOfEntries = countEntries(_list);
        assertEquals(0, numberOfEntries);
    }

    public void testAddTwoMessagesWithDifferentConflationKeyValue()
    {
        ServerMessage message1 = createTestServerMessage(TEST_KEY_VALUE1);
        ServerMessage message2 = createTestServerMessage(TEST_KEY_VALUE2);

        _list.add(message1);
        _list.add(message2);

        int numberOfEntries = countEntries(_list);
        assertEquals(2, numberOfEntries);
    }

    public void testAddTwoMessagesWithSameConflationKeyValue()
    {
        ServerMessage message1 = createTestServerMessage(TEST_KEY_VALUE);
        ServerMessage message2 = createTestServerMessage(TEST_KEY_VALUE);

        _list.add(message1);
        _list.add(message2);

        int numberOfEntries = countEntries(_list);
        assertEquals(1, numberOfEntries);
    }

    public void testSupersededEntryIsDiscardedOnRelease()
    {
        ServerMessage message1 = createTestServerMessage(TEST_KEY_VALUE);
        ServerMessage message2 = createTestServerMessage(TEST_KEY_VALUE);

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

        ServerMessage message = createTestServerMessage(TEST_KEY_VALUE);

        QueueEntry addedEntry = _list.add(message);

        assertEquals(1, countEntries(_list));
        assertEquals(1, _list.getLatestValuesMap().size());

        addedEntry.delete();

        assertEquals(0, countEntries(_list));
        assertEquals(0, _list.getLatestValuesMap().size());
    }

    public void testConflationMapMaintainedWithDifferentConflationKeyValue()
    {

        assertEquals(0, _list.getLatestValuesMap().size());

        ServerMessage message1 = createTestServerMessage(TEST_KEY_VALUE1);
        ServerMessage message2 = createTestServerMessage(TEST_KEY_VALUE2);

        QueueEntry addedEntry1 = _list.add(message1);
        QueueEntry addedEntry2 = _list.add(message2);

        assertEquals(2, countEntries(_list));
        assertEquals(2, _list.getLatestValuesMap().size());

        addedEntry1.delete();
        addedEntry2.delete();

        assertEquals(0, countEntries(_list));
        assertEquals(0, _list.getLatestValuesMap().size());
    }

    private int countEntries(ConflationQueueList list)
    {
        QueueEntryIterator<ConflationQueueList.ConflationQueueEntry, ConflationQueue, ConflationQueueList,QueueConsumer<?,ConflationQueueList.ConflationQueueEntry, ConflationQueue, ConflationQueueList>> iterator =
                list.iterator();
        int count = 0;
        while(iterator.advance())
        {
            count++;
        }
        return count;
    }

    private ServerMessage createTestServerMessage(String conflationKeyValue)
    {
        ServerMessage mockMessage = mock(ServerMessage.class);

        AMQMessageHeader messageHeader = mock(AMQMessageHeader.class);
        when(messageHeader.getHeader(CONFLATION_KEY)).thenReturn(conflationKeyValue);
        when(mockMessage.getMessageHeader()).thenReturn(messageHeader);

        MessageReference messageReference = mock(MessageReference.class);
        when(mockMessage.newReference()).thenReturn(messageReference);
        when(messageReference.getMessage()).thenReturn(mockMessage);

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
