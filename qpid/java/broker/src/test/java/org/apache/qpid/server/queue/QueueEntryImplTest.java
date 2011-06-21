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
 */
package org.apache.qpid.server.queue;

import java.lang.reflect.Field;

import junit.framework.TestCase;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.message.AMQMessage;
import org.apache.qpid.server.queue.QueueEntry.EntryState;
import org.apache.qpid.server.subscription.MockSubscription;

/**
 * Tests for {@link QueueEntryImpl}
 *
 */
public class QueueEntryImplTest extends TestCase
{
    // tested entry
    private QueueEntryImpl _queueEntry;

    public void setUp() throws Exception
    {
        AMQMessage message = new MockAMQMessage(1);
        SimpleQueueEntryList queueEntryList = new SimpleQueueEntryList(new MockAMQQueue("test"));
        _queueEntry = new QueueEntryImpl(queueEntryList, message, 1);
    }

    public void testAquire()
    {
        assertTrue("Queue entry should be in AVAILABLE state before invoking of acquire method",
                _queueEntry.isAvailable());
        acquire();
    }

    public void testDequeue()
    {
        dequeue();
    }

    public void testDelete()
    {
        delete();
    }

    /**
     * Tests release method for entry in acquired state.
     * <p>
     * Entry in state ACQUIRED should be released and its status should be
     * changed to AVAILABLE.
     */
    public void testReleaseAquired()
    {
        acquire();
        _queueEntry.release();
        assertTrue("Queue entry should be in AVAILABLE state after invoking of release method",
                _queueEntry.isAvailable());
    }

    /**
     * Tests release method for entry in dequeued state.
     * <p>
     * Invoking release on dequeued entry should not have any effect on its
     * state.
     */
    public void testReleaseDequeued()
    {
        dequeue();
        _queueEntry.release();
        EntryState state = getState();
        assertEquals("Invoking of release on entry in DEQUEUED state should not have any effect",
                QueueEntry.DEQUEUED_STATE, state);
    }

    /**
     * Tests release method for entry in deleted state.
     * <p>
     * Invoking release on deleted entry should not have any effect on its
     * state.
     */
    public void testReleaseDeleted()
    {
        delete();
        _queueEntry.release();
        assertTrue("Invoking of release on entry in DELETED state should not have any effect",
                _queueEntry.isDeleted());
    }

    /**
     * Tests if entries in DEQUQUED or DELETED state are not returned by getNext method.
     */
    public void testGetNext()
    {
        int numberOfEntries = 5;
        QueueEntryImpl[] entries = new QueueEntryImpl[numberOfEntries];
        SimpleQueueEntryList queueEntryList = new SimpleQueueEntryList(new MockAMQQueue("test"));

        // create test entries
        for(int i = 0; i < numberOfEntries ; i++)
        {
            AMQMessage message = null;;
            try
            {
                message = new MockAMQMessage(i);
            }
            catch (AMQException e)
            {
                fail("Failure to create a mock message:" + e.getMessage());
            }
            QueueEntryImpl entry = (QueueEntryImpl)queueEntryList.add(message);
            entries[i] = entry;
        }

        // test getNext for not acquired entries
        for(int i = 0; i < numberOfEntries ; i++)
        {
            QueueEntryImpl queueEntry = entries[i];
            QueueEntryImpl next = queueEntry.getNext();
            if (i < numberOfEntries - 1)
            {
                assertEquals("Unexpected entry from QueueEntryImpl#getNext()", entries[i + 1], next);
            }
            else
            {
                assertNull("The next entry after the last should be null", next);
            }
        }

        // delete second
        entries[1].acquire();
        entries[1].delete();

        // dequeue third
        entries[2].acquire();
        entries[2].dequeue();

        QueueEntryImpl next = entries[0].getNext();
        assertEquals("expected forth entry",entries[3], next);
        next = next.getNext();
        assertEquals("expected fifth entry", entries[4], next);
        next = next.getNext();
        assertNull("The next entry after the last should be null", next);
    }
    /**
     * A helper method to put tested object into deleted state and assert the state
     */
    private void delete()
    {
        _queueEntry.delete();
        assertTrue("Queue entry should be in DELETED state after invoking of delete method",
                _queueEntry.isDeleted());
    }

    /**
     * A helper method to put tested entry into dequeue state and assert the sate
     */
    private void dequeue()
    {
        acquire();
        _queueEntry.dequeue();
        EntryState state = getState();
        assertEquals("Queue entry should be in DEQUEUED state after invoking of dequeue method",
                QueueEntry.DEQUEUED_STATE, state);
    }

    /**
     * A helper method to put tested entry into acquired state and assert the sate
     */
    private void acquire()
    {
        _queueEntry.acquire(new MockSubscription());
        assertTrue("Queue entry should be in ACQUIRED state after invoking of acquire method",
                _queueEntry.isAcquired());
    }

    /**
     * A helper method to get entry state
     *
     * @return entry state
     */
    private EntryState getState()
    {
        EntryState state = null;
        try
        {
            Field f = QueueEntryImpl.class.getDeclaredField("_state");
            f.setAccessible(true);
            state = (EntryState) f.get(_queueEntry);
        }
        catch (Exception e)
        {
            fail("Failure to get a state field: " + e.getMessage());
        }
        return state;
    }
}
