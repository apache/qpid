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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.message.AMQMessage;

import junit.framework.TestCase;

public class SimpleQueueEntryListTest extends TestCase
{
    private static final String SCAVENGE_PROP = "qpid.queue.scavenge_count";
    String oldScavengeValue = null;
    
    @Override
    protected void setUp()
    {
        oldScavengeValue = System.setProperty(SCAVENGE_PROP, "9");
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
    
    /**
     * Tests the behavior of the next(QueuyEntry) method.
     */
    public void testNext() throws Exception
    {
        SimpleQueueEntryList sqel = new SimpleQueueEntryList(null);
        int i = 0;

        QueueEntry queueEntry1 = sqel.add(new MockAMQMessage(i++));
        QueueEntry queueEntry2 = sqel.add(new MockAMQMessage(i++));

        assertSame(queueEntry2, sqel.next(queueEntry1));
        assertNull(sqel.next(queueEntry2));
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
        
        QueueEntryImpl head = ((QueueEntryImpl) sqel.getHead());
        
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
    
    private void verifyDeletedButPresentBeforeScavenge(QueueEntryImpl head, long messageId)
    {
        //Use the head to get the initial entry in the queue
        QueueEntryImpl entry = head._next;
        
        for(long i = 1; i < messageId ; i++)
        {
            assertEquals("Expected QueueEntry was not found in the list", i, (long) entry.getMessage().getMessageNumber());
            entry = entry._next;
        }
        
        assertTrue("Entry should have been deleted", entry.isDeleted());
    }
    
    private void verifyAllDeletedMessagedNotPresent(QueueEntryImpl head, Map<Integer,QueueEntry> remainingMessages)
    {
        //Use the head to get the initial entry in the queue
        QueueEntryImpl entry = head._next;
        
        assertNotNull("Initial entry should not have been null", entry);
        
        int count = 0;
        
        while (entry != null)
        {           
            assertFalse("Entry " + entry.getMessage().getMessageNumber() + " should not have been deleted", entry.isDeleted());
            assertNotNull("QueueEntry was not found in the list of remaining entries", 
                    remainingMessages.get(entry.getMessage().getMessageNumber().intValue()));
            
            count++;
            entry = entry._next;
        }
        
        assertEquals("Count should have been equal",count,remainingMessages.size());
    }

    public void testDequedMessagedNotPresentInIterator()
    {
        int numberOfMessages = 10;
        SimpleQueueEntryList entryList = new SimpleQueueEntryList(new MockAMQQueue("test"));
        QueueEntry[] entries = new QueueEntry[numberOfMessages];

        for(int i = 0; i < numberOfMessages ; i++)
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
            QueueEntry entry = entryList.add(message);
            assertNotNull("QE should not be null", entry);
            entries[i]= entry;
        }

        // dequeue all even messages
        for (QueueEntry queueEntry : entries)
        {
            long i = ((AMQMessage)queueEntry.getMessage()).getMessageId().longValue();
            if (i%2 == 0)
            {
                queueEntry.acquire();
                queueEntry.dequeue();
            }
        }

        // iterate and check that dequeued messages are not returned by iterator
        QueueEntryIterator it = entryList.iterator();
        int counter = 0;
        int i = 1;
        while (it.advance())
        {
            QueueEntry entry = it.getNode();
            Long id = ((AMQMessage)entry.getMessage()).getMessageId();
            assertEquals("Expected message with id " + i + " but got message with id "
                    + id, new Long(i), id);
            counter++;
            i += 2;
        }
        int expectedNumber = numberOfMessages / 2;
        assertEquals("Expected  " + expectedNumber + " number of entries in iterator but got " + counter,
                expectedNumber, counter);
    }
}
