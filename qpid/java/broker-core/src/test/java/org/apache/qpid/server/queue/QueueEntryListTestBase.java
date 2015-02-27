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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import junit.framework.TestCase;

import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.store.TransactionLogResource;

/**
 * Abstract test class for QueueEntryList implementations.
 */
public abstract class QueueEntryListTestBase extends TestCase
{
    public abstract QueueEntryList getTestList();
    public abstract QueueEntryList getTestList(boolean newList);
    public abstract long getExpectedFirstMsgId();
    public abstract int getExpectedListLength();
    public abstract ServerMessage getTestMessageToAdd();

    public void testGetQueue()
    {
        assertEquals("Unexpected head entry returned by getHead()", getTestList().getQueue(), getTestQueue());
    }

    protected abstract AMQQueue getTestQueue();

    /**
     * Test to add a message with properties specific to the queue type.
     * @see QueueEntryListTestBase#getTestList()
     * @see QueueEntryListTestBase#getTestMessageToAdd()
     */
    public void testAddSpecificMessage()
    {
        final QueueEntryList list = getTestList();
        list.add(getTestMessageToAdd());

        final QueueEntryIterator iter = list.iterator();
        int count = 0;
        while(iter.advance())
        {
            iter.getNode();
            count++;
        }
        assertEquals("List did not grow by one entry after an add", getExpectedListLength() + 1, count);
    }

    /**
     * Test to add a generic mock message.
     * @see QueueEntryListTestBase#getTestList()
     * @see QueueEntryListTestBase#getExpectedListLength()
     */
    public void testAddGenericMessage()
    {
        final QueueEntryList list = getTestList();
        final ServerMessage message = createServerMessage(666l);
        list.add(message);

        final QueueEntryIterator iter = list.iterator();
        int count = 0;
        while(iter.advance())
        {
            iter.getNode();
            count++;
        }
        assertEquals("List did not grow by one entry after a generic message added", getExpectedListLength() + 1, count);

    }

    private ServerMessage createServerMessage(long number)
    {
        final ServerMessage message = mock(ServerMessage.class);
        when(message.getMessageNumber()).thenReturn(number);
        MessageReference ref = mock(MessageReference.class);
        AMQMessageHeader hdr = mock(AMQMessageHeader.class);
        when(ref.getMessage()).thenReturn(message);
        when(message.newReference()).thenReturn(ref);
        when(message.newReference(any(TransactionLogResource.class))).thenReturn(ref);
        when(message.getMessageHeader()).thenReturn(hdr);
        return message;
    }

    /**
     * Test for getting the next element in a queue list.
     * @see QueueEntryListTestBase#getTestList()
     * @see QueueEntryListTestBase#getExpectedListLength()
     */
    public void testListNext()
    {
        final QueueEntryList entryList = getTestList();
        QueueEntry entry = entryList.getHead();
        int count = 0;
        while(entryList.next(entry) != null)
        {
            entry = entryList.next(entry);
            count++;
        }
        assertEquals("Get next didn't get all the list entries", getExpectedListLength(), count);
    }

    /**
     * Basic test for the associated QueueEntryIterator implementation.
     * @see QueueEntryListTestBase#getTestList()
     * @see QueueEntryListTestBase#getExpectedListLength()
     */
    public void testIterator()
    {
        final QueueEntryIterator iter = getTestList().iterator();
        int count = 0;
        while(iter.advance())
        {
            iter.getNode();
            count++;
        }
        assertEquals("Iterator invalid", getExpectedListLength(), count);
    }

    /**
     * Test for associated QueueEntryIterator implementation that checks it handles "removed" messages.
     * @see QueueEntryListTestBase#getTestList()
     * @see QueueEntryListTestBase#getExpectedListLength()
     */
    public void testDequeuedMessagedNotPresentInIterator() throws Exception
    {
        final int numberOfMessages = getExpectedListLength();
        final QueueEntryList entryList = getTestList();

        // dequeue all even messages
        final QueueEntryIterator it1 = entryList.iterator();
        int counter = 0;
        while (it1.advance())
        {
            final QueueEntry queueEntry = it1.getNode();
            if(counter++ % 2 == 0)
            {
                queueEntry.acquire();
                queueEntry.delete();
            }
        }

        // iterate and check that dequeued messages are not returned by iterator
        final QueueEntryIterator it2 = entryList.iterator();
        int counter2 = 0;
        while(it2.advance())
        {
            it2.getNode();
            counter2++;
        }
        final int expectedNumber = numberOfMessages / 2;
        assertEquals("Expected  " + expectedNumber + " number of entries in iterator but got " + counter2,
                        expectedNumber, counter2);
    }

    /**
     * Test to verify the head of the queue list is returned as expected.
     * @see QueueEntryListTestBase#getTestList()
     * @see QueueEntryListTestBase#getExpectedFirstMsgId()
     */
    public void testGetHead()
    {
        final QueueEntry head = getTestList().getHead();
        assertNull("Head entry should not contain an actual message", head.getMessage());
        assertEquals("Unexpected message id for first list entry", getExpectedFirstMsgId(), getTestList().next(head)
                        .getMessage().getMessageNumber());
    }

    /**
     * Test to verify the entry deletion handled correctly.
     * @see QueueEntryListTestBase#getTestList()
     */
    public void testEntryDeleted()
    {
        final QueueEntry head = getTestList().getHead();

        final QueueEntry first = getTestList().next(head);
        first.acquire();
        first.delete();

        final QueueEntry second = getTestList().next(head);
        assertNotSame("After deletion the next entry should be different", first.getMessage().getMessageNumber(), second
                        .getMessage().getMessageNumber());

        final QueueEntry third = getTestList().next(first);
        assertEquals("After deletion the deleted nodes next node should be the same as the next from head", second
                        .getMessage().getMessageNumber(), third.getMessage().getMessageNumber());
    }

    /**
     * Tests that after the last node of the list is marked deleted but has not yet been removed,
     * the iterator still ignores it and returns that it is 'atTail()' and can't 'advance()'
     *
     * @see QueueEntryListTestBase#getTestList()
     * @see QueueEntryListTestBase#getExpectedListLength()
     */
    public void testIteratorIgnoresDeletedFinalNode() throws Exception
    {
        QueueEntryList list = getTestList(true);
        int i = 0;

        QueueEntry queueEntry1 = list.add(createServerMessage(i++));
        QueueEntry queueEntry2 = list.add(createServerMessage(i++));

        assertSame(queueEntry2, list.next(queueEntry1));
        assertNull(list.next(queueEntry2));

        //'delete' the 2nd QueueEntry
        queueEntry2.acquire();
        queueEntry2.delete();
        assertTrue("Deleting node should have succeeded", queueEntry2.isDeleted());

        QueueEntryIterator iter = list.iterator();

        //verify the iterator isn't 'atTail', can advance, and returns the 1st QueueEntry
        assertFalse("Iterator should not have been 'atTail'", iter.atTail());
        assertTrue("Iterator should have been able to advance", iter.advance());
        assertSame("Iterator returned unexpected QueueEntry", queueEntry1, iter.getNode());

        //verify the iterator is atTail() and can't advance
        assertTrue("Iterator should have been 'atTail'", iter.atTail());
        assertFalse("Iterator should not have been able to advance", iter.advance());
    }
}
