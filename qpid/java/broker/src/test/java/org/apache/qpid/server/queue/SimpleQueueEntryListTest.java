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

import org.apache.qpid.AMQException;
import org.apache.qpid.server.message.AMQMessage;
import org.apache.qpid.server.message.ServerMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleQueueEntryListTest extends QueueEntryListTestBase
{
    private SimpleQueueEntryList _sqel;

    private static final String SCAVENGE_PROP = "qpid.queue.scavenge_count";
    String oldScavengeValue = null;

    @Override
    protected void setUp()
    {
        oldScavengeValue = System.setProperty(SCAVENGE_PROP, "9");
        _sqel = new SimpleQueueEntryList(_testQueue);
        for(int i = 1; i <= 100; i++)
        {
            final ServerMessage msg = new MockAMQMessage(i);
            final QueueEntry bleh = _sqel.add(msg);
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
    public QueueEntryList getTestList()
    {
        return _sqel;
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
    public AMQMessage getTestMessageToAdd() throws AMQException
    {
        return new MockAMQMessage(1l);
    }

    public void testScavenge() throws Exception
    {
        SimpleQueueEntryList sqel = new SimpleQueueEntryList(null);
        ConcurrentHashMap<Integer,QueueEntry> entriesMap = new ConcurrentHashMap<Integer,QueueEntry>();
        
        
        //Add messages to generate QueueEntry's
        for(int i = 1; i <= 100 ; i++)
        {
            AMQMessage msg = new MockAMQMessage(i);
            QueueEntry bleh = sqel.add(msg);
            assertNotNull("QE should not have been null", bleh);
            entriesMap.put(i,bleh);
        }
        
        SimpleQueueEntryImpl head = sqel.getHead();
        
        //We shall now delete some specific messages mid-queue that will lead to 
        //requiring a scavenge once the requested threshold of 9 deletes is passed
        
        //Delete the 2nd message only
        assertTrue("Failed to delete QueueEntry", entriesMap.remove(2).delete());
        verifyDeletedButPresentBeforeScavenge(head, 2);
        
        //Delete messages 12 to 14
        assertTrue("Failed to delete QueueEntry", entriesMap.remove(12).delete());
        verifyDeletedButPresentBeforeScavenge(head, 12);
        assertTrue("Failed to delete QueueEntry", entriesMap.remove(13).delete());
        verifyDeletedButPresentBeforeScavenge(head, 13);
        assertTrue("Failed to delete QueueEntry", entriesMap.remove(14).delete());
        verifyDeletedButPresentBeforeScavenge(head, 14);

        //Delete message 20 only
        assertTrue("Failed to delete QueueEntry", entriesMap.remove(20).delete());
        verifyDeletedButPresentBeforeScavenge(head, 20);

        //Delete messages 81 to 84
        assertTrue("Failed to delete QueueEntry", entriesMap.remove(81).delete());
        verifyDeletedButPresentBeforeScavenge(head, 81);
        assertTrue("Failed to delete QueueEntry", entriesMap.remove(82).delete());
        verifyDeletedButPresentBeforeScavenge(head, 82);
        assertTrue("Failed to delete QueueEntry", entriesMap.remove(83).delete());
        verifyDeletedButPresentBeforeScavenge(head, 83);
        assertTrue("Failed to delete QueueEntry", entriesMap.remove(84).delete());
        verifyDeletedButPresentBeforeScavenge(head, 84);

        //Delete message 99 - this is the 10th message deleted that is after the queue head
        //and so will invoke the scavenge() which is set to go after 9 previous deletions
        assertTrue("Failed to delete QueueEntry", entriesMap.remove(99).delete());
        
        verifyAllDeletedMessagedNotPresent(head, entriesMap);
    }

    private void verifyDeletedButPresentBeforeScavenge(SimpleQueueEntryImpl head, long messageId)
    {
        //Use the head to get the initial entry in the queue
        SimpleQueueEntryImpl entry = head.getNextNode();

        for(long i = 1; i < messageId ; i++)
        {
            assertEquals("Expected QueueEntry was not found in the list", i, (long) entry.getMessage().getMessageNumber());
            entry = entry.getNextNode();
        }

        assertTrue("Entry should have been deleted", entry.isDeleted());
    }

    private void verifyAllDeletedMessagedNotPresent(SimpleQueueEntryImpl head, Map<Integer,QueueEntry> remainingMessages)
    {
        //Use the head to get the initial entry in the queue
        SimpleQueueEntryImpl entry = head.getNextNode();

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
        final SimpleQueueEntryImpl[] entries = new SimpleQueueEntryImpl[numberOfEntries];
        final SimpleQueueEntryList queueEntryList = new SimpleQueueEntryList(new MockAMQQueue("test"));

        // create test entries
        for(int i = 0; i < numberOfEntries; i++)
        {
            AMQMessage message =  new MockAMQMessage(i);
            entries[i] = queueEntryList.add(message);
        }

        // test getNext for not acquired entries
        for(int i = 0; i < numberOfEntries; i++)
        {
            final SimpleQueueEntryImpl next = entries[i].getNextValidEntry();

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
        entries[2].dequeue();

        SimpleQueueEntryImpl next = entries[2].getNextValidEntry();
        assertEquals("expected forth entry", entries[3], next);
        next = next.getNextValidEntry();
        assertEquals("expected fifth entry", entries[4], next);
        next = next.getNextValidEntry();
        assertNull("The next entry after the last should be null", next);
    }

}
