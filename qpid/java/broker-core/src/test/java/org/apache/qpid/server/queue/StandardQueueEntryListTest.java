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
package org.apache.qpid.server.queue;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObjectFactoryImpl;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class StandardQueueEntryListTest extends QueueEntryListTestBase
{

    private StandardQueueImpl _testQueue;
    private StandardQueueEntryList _sqel;

    private static final String SCAVENGE_PROP = "qpid.queue.scavenge_count";
    private String oldScavengeValue = null;
    private ConfiguredObjectFactoryImpl _factory;

    @Override
    protected void setUp()
    {
        oldScavengeValue = System.setProperty(SCAVENGE_PROP, "9");

        Map<String,Object> queueAttributes = new HashMap<String, Object>();
        queueAttributes.put(Queue.ID, UUID.randomUUID());
        queueAttributes.put(Queue.NAME, getName());
        final VirtualHostImpl virtualHost = mock(VirtualHostImpl.class);
        when(virtualHost.getSecurityManager()).thenReturn(mock(SecurityManager.class));
        when(virtualHost.getEventLogger()).thenReturn(new EventLogger());
        _factory = new ConfiguredObjectFactoryImpl(BrokerModel.getInstance());
        when(virtualHost.getObjectFactory()).thenReturn(_factory);
        when(virtualHost.getModel()).thenReturn(_factory.getModel());
        TaskExecutor taskExecutor = CurrentThreadTaskExecutor.newStartedInstance();
        when(virtualHost.getTaskExecutor()).thenReturn(taskExecutor);
        when(virtualHost.getChildExecutor()).thenReturn(taskExecutor);
        _testQueue = new StandardQueueImpl(queueAttributes, virtualHost);
        _testQueue.open();
        _sqel = _testQueue.getEntries();
        for(int i = 1; i <= 100; i++)
        {
            final ServerMessage message = mock(ServerMessage.class);
            when(message.getMessageNumber()).thenReturn((long) i);
            MessageReference ref = mock(MessageReference.class);
            when(ref.getMessage()).thenReturn(message);
            when(message.newReference()).thenReturn(ref);
            when(message.newReference(any(TransactionLogResource.class))).thenReturn(ref);

            final QueueEntry bleh = _sqel.add(message);
            assertNotNull("QE should not have been null", bleh);
        }
    }

    @Override
    protected void tearDown()
    {
        if(oldScavengeValue != null)
        {
            System.setProperty(SCAVENGE_PROP, oldScavengeValue);
        }
        else
        {
            System.clearProperty(SCAVENGE_PROP);
        }
    }

    @Override
    public StandardQueueEntryList getTestList()
    {
        return getTestList(false);
    }

    @Override
    public StandardQueueEntryList getTestList(boolean newList)
    {
        if(newList)
        {
            Map<String,Object> queueAttributes = new HashMap<String, Object>();
            queueAttributes.put(Queue.ID, UUID.randomUUID());
            queueAttributes.put(Queue.NAME, getName());
            final VirtualHostImpl virtualHost = mock(VirtualHostImpl.class);
            when(virtualHost.getSecurityManager()).thenReturn(mock(SecurityManager.class));
            when(virtualHost.getEventLogger()).thenReturn(new EventLogger());
            when(virtualHost.getObjectFactory()).thenReturn(_factory);
            when(virtualHost.getModel()).thenReturn(_factory.getModel());
            TaskExecutor taskExecutor = CurrentThreadTaskExecutor.newStartedInstance();
            when(virtualHost.getTaskExecutor()).thenReturn(taskExecutor);
            when(virtualHost.getChildExecutor()).thenReturn(taskExecutor);

            StandardQueueImpl queue = new StandardQueueImpl(queueAttributes, virtualHost);
            queue.open();
            return queue.getEntries();
        }
        else
        {
            return _sqel;
        }
    }

    @Override
    public long getExpectedFirstMsgId()
    {
        return 1;
    }

    @Override
    public int getExpectedListLength()
    {
        return 100;
    }

    @Override
    public ServerMessage getTestMessageToAdd()
    {
        ServerMessage msg = mock(ServerMessage.class);
        when(msg.getMessageNumber()).thenReturn(1l);
        return msg;
    }

    @Override
    protected StandardQueueImpl getTestQueue()
    {
        return _testQueue;
    }

    public void testScavenge() throws Exception
    {
        OrderedQueueEntryList sqel = new StandardQueueEntryList(mock(StandardQueueImpl.class));
        ConcurrentMap<Integer,QueueEntry> entriesMap = new ConcurrentHashMap<Integer,QueueEntry>();


        //Add messages to generate QueueEntry's
        for(int i = 1; i <= 100 ; i++)
        {
            ServerMessage message = mock(ServerMessage.class);
            when(message.getMessageNumber()).thenReturn((long) i);
            MessageReference ref = mock(MessageReference.class);
            when(ref.getMessage()).thenReturn(message);
            when(message.newReference()).thenReturn(ref);
            when(message.newReference(any(TransactionLogResource.class))).thenReturn(ref);
            QueueEntry bleh = sqel.add(message);
            assertNotNull("QE should not have been null", bleh);
            entriesMap.put(i,bleh);
        }

        OrderedQueueEntry head = (OrderedQueueEntry) sqel.getHead();

        //We shall now delete some specific messages mid-queue that will lead to
        //requiring a scavenge once the requested threshold of 9 deletes is passed

        //Delete the 2nd message only
        assertTrue("Failed to delete QueueEntry", remove(entriesMap,2));
        verifyDeletedButPresentBeforeScavenge(head, 2);

        //Delete messages 12 to 14
        assertTrue("Failed to delete QueueEntry", remove(entriesMap,12));
        verifyDeletedButPresentBeforeScavenge(head, 12);
        assertTrue("Failed to delete QueueEntry", remove(entriesMap,13));
        verifyDeletedButPresentBeforeScavenge(head, 13);
        assertTrue("Failed to delete QueueEntry", remove(entriesMap,14));
        verifyDeletedButPresentBeforeScavenge(head, 14);

        //Delete message 20 only
        assertTrue("Failed to delete QueueEntry", remove(entriesMap,20));
        verifyDeletedButPresentBeforeScavenge(head, 20);

        //Delete messages 81 to 84
        assertTrue("Failed to delete QueueEntry", remove(entriesMap,81));
        verifyDeletedButPresentBeforeScavenge(head, 81);
        assertTrue("Failed to delete QueueEntry", remove(entriesMap,82));
        verifyDeletedButPresentBeforeScavenge(head, 82);
        assertTrue("Failed to delete QueueEntry", remove(entriesMap,83));
        verifyDeletedButPresentBeforeScavenge(head, 83);
        assertTrue("Failed to delete QueueEntry", remove(entriesMap,84));
        verifyDeletedButPresentBeforeScavenge(head, 84);

        //Delete message 99 - this is the 10th message deleted that is after the queue head
        //and so will invoke the scavenge() which is set to go after 9 previous deletions
        assertTrue("Failed to delete QueueEntry", remove(entriesMap,99));

        verifyAllDeletedMessagedNotPresent(head, entriesMap);
    }
    
    private boolean remove(Map<Integer,QueueEntry> entriesMap, int pos)
    {
        QueueEntry entry = entriesMap.remove(pos);
        boolean wasDeleted = entry.isDeleted();
        entry.acquire();
        entry.delete();
        return entry.isDeleted() && !wasDeleted;
    }

    private void verifyDeletedButPresentBeforeScavenge(OrderedQueueEntry head, long messageId)
    {
        //Use the head to get the initial entry in the queue
        OrderedQueueEntry entry = head.getNextNode();

        for(long i = 1; i < messageId ; i++)
        {
            assertEquals("Expected QueueEntry was not found in the list", i, (long) entry.getMessage().getMessageNumber());
            entry = entry.getNextNode();
        }

        assertTrue("Entry should have been deleted", entry.isDeleted());
    }

    private void verifyAllDeletedMessagedNotPresent(OrderedQueueEntry head, Map<Integer,QueueEntry> remainingMessages)
    {
        //Use the head to get the initial entry in the queue
        OrderedQueueEntry entry = head.getNextNode();

        assertNotNull("Initial entry should not have been null", entry);

        int count = 0;

        while (entry != null)
        {
            assertFalse("Entry " + entry.getMessage().getMessageNumber() + " should not have been deleted", entry.isDeleted());
            assertNotNull("QueueEntry "+entry.getMessage().getMessageNumber()+" was not found in the list of remaining entries " + remainingMessages,
                    remainingMessages.get((int)(entry.getMessage().getMessageNumber())));

            count++;
            entry = entry.getNextNode();
        }

        assertEquals("Count should have been equal",count,remainingMessages.size());
    }

    public void testGettingNextElement()
    {
        final int numberOfEntries = 5;
        final OrderedQueueEntry[] entries = new OrderedQueueEntry[numberOfEntries];
        final OrderedQueueEntryList queueEntryList = getTestList(true);

        // create test entries
        for(int i = 0; i < numberOfEntries; i++)
        {
            ServerMessage message =  mock(ServerMessage.class);
            when(message.getMessageNumber()).thenReturn((long)i);
            final MessageReference reference = mock(MessageReference.class);
            when(reference.getMessage()).thenReturn(message);
            when(message.newReference()).thenReturn(reference);
            when(message.newReference(any(TransactionLogResource.class))).thenReturn(reference);
            entries[i] = (OrderedQueueEntry) queueEntryList.add(message);
        }

        // test getNext for not acquired entries
        for(int i = 0; i < numberOfEntries; i++)
        {
            final OrderedQueueEntry next = entries[i].getNextValidEntry();

            if(i < numberOfEntries - 1)
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
        entries[2].delete();

        OrderedQueueEntry next = entries[2].getNextValidEntry();
        assertEquals("expected forth entry", entries[3], next);
        next = next.getNextValidEntry();
        assertEquals("expected fifth entry", entries[4], next);
        next = next.getNextValidEntry();
        assertNull("The next entry after the last should be null", next);
    }
}
