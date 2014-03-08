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

import junit.framework.TestCase;

import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.message.MessageInstance.EntryState;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link QueueEntryImpl}
 */
public abstract class QueueEntryImplTestBase extends TestCase
{
    // tested entry
    protected QueueEntryImpl _queueEntry;
    protected QueueEntryImpl _queueEntry2;
    protected QueueEntryImpl _queueEntry3;
    private long _consumerId;

    public abstract QueueEntryImpl getQueueEntryImpl(int msgId);

    public abstract void testCompareTo();

    public abstract void testTraverseWithNoDeletedEntries();

    public abstract void testTraverseWithDeletedEntries();

    public void setUp() throws Exception
    {
        _queueEntry = getQueueEntryImpl(1);
        _queueEntry2 = getQueueEntryImpl(2);
        _queueEntry3 = getQueueEntryImpl(3);
    }

    public void testAcquire()
    {
        assertTrue("Queue entry should be in AVAILABLE state before invoking of acquire method",
                _queueEntry.isAvailable());
        acquire();
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
    public void testReleaseAcquired()
    {
        acquire();
        _queueEntry.release();
        assertTrue("Queue entry should be in AVAILABLE state after invoking of release method",
                   _queueEntry.isAvailable());
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
     * A helper method to put tested object into deleted state and assert the state
     */
    private void delete()
    {
        _queueEntry.delete();
        assertTrue("Queue entry should be in DELETED state after invoking of delete method",
                _queueEntry.isDeleted());
    }


    /**
     * A helper method to put tested entry into acquired state and assert the sate
     */
    private void acquire()
    {
        _queueEntry.acquire(newConsumer());
        assertTrue("Queue entry should be in ACQUIRED state after invoking of acquire method",
                _queueEntry.isAcquired());
    }

    private QueueConsumer newConsumer()
    {
        final QueueConsumer consumer = mock(QueueConsumer.class);

        MessageInstance.ConsumerAcquiredState owningState = new QueueEntryImpl.ConsumerAcquiredState(consumer);
        when(consumer.getOwningState()).thenReturn(owningState);
        when(consumer.getConsumerNumber()).thenReturn(_consumerId++);
        return consumer;
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

    /**
     * Tests rejecting a queue entry records the Consumer ID
     * for later verification by isRejectedBy(consumerId).
     */
    public void testRejectAndRejectedBy()
    {
        QueueConsumer sub = newConsumer();

        assertFalse("Queue entry should not yet have been rejected by the consumer", _queueEntry.isRejectedBy(sub));
        assertFalse("Queue entry should not yet have been acquired by a consumer", _queueEntry.isAcquired());

        //acquire, reject, and release the message using the consumer
        assertTrue("Queue entry should have been able to be acquired", _queueEntry.acquire(sub));
        _queueEntry.reject();
        _queueEntry.release();

        //verify the rejection is recorded
        assertTrue("Queue entry should have been rejected by the consumer", _queueEntry.isRejectedBy(sub));

        //repeat rejection using a second consumer
        QueueConsumer sub2 = newConsumer();

        assertFalse("Queue entry should not yet have been rejected by the consumer", _queueEntry.isRejectedBy(sub2));
        assertTrue("Queue entry should have been able to be acquired", _queueEntry.acquire(sub2));
        _queueEntry.reject();

        //verify it still records being rejected by both consumers
        assertTrue("Queue entry should have been rejected by the consumer", _queueEntry.isRejectedBy(sub));
        assertTrue("Queue entry should have been rejected by the consumer", _queueEntry.isRejectedBy(sub2));
    }

    /**
     * Tests if entries in DEQUEUED or DELETED state are not returned by getNext method.
     */
    public void testGetNext()
    {
        int numberOfEntries = 5;
        QueueEntryImpl[] entries = new QueueEntryImpl[numberOfEntries];
        Map<String,Object> queueAttributes = new HashMap<String, Object>();
        queueAttributes.put(Queue.ID, UUID.randomUUID());
        queueAttributes.put(Queue.NAME, getName());
        final VirtualHost virtualHost = mock(VirtualHost.class);
        when(virtualHost.getSecurityManager()).thenReturn(mock(org.apache.qpid.server.security.SecurityManager.class));
        when(virtualHost.getEventLogger()).thenReturn(new EventLogger());

        StandardQueue queue = new StandardQueue(virtualHost, queueAttributes);
        OrderedQueueEntryList queueEntryList = (OrderedQueueEntryList) queue.getEntries();

        // create test entries
        for(int i = 0; i < numberOfEntries ; i++)
        {
            ServerMessage message = mock(ServerMessage.class);
            when(message.getMessageNumber()).thenReturn((long)i);
            final MessageReference reference = mock(MessageReference.class);
            when(reference.getMessage()).thenReturn(message);
            when(message.newReference()).thenReturn(reference);
            QueueEntryImpl entry = (QueueEntryImpl) queueEntryList.add(message);
            entries[i] = entry;
        }

        // test getNext for not acquired entries
        for(int i = 0; i < numberOfEntries ; i++)
        {
            QueueEntryImpl queueEntry = entries[i];
            QueueEntry next = queueEntry.getNextValidEntry();
            if (i < numberOfEntries - 1)
            {
                assertEquals("Unexpected entry from QueueEntryImpl#getNext()", entries[i + 1], next);
            }
            else
            {
                assertNull("The next entry after the last should be null", next);
            }
        }

        // discard second
        entries[1].acquire();
        entries[1].delete();

        // discard third
        entries[2].acquire();
        entries[2].delete();

        QueueEntry next = entries[0].getNextValidEntry();
        assertEquals("expected forth entry",entries[3], next);
        next = next.getNextValidEntry();
        assertEquals("expected fifth entry", entries[4], next);
        next = next.getNextValidEntry();
        assertNull("The next entry after the last should be null", next);
    }
}
