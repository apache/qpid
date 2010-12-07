package org.apache.qpid.client;

import java.util.ArrayList;

import junit.framework.TestCase;

public class DeliveryCountTrackerTest extends TestCase
{
    private DeliveryCountTracker _tracker;
    private final int CAPACITY=50;

    protected void setUp()
    {
        _tracker = new DeliveryCountTracker(CAPACITY);
    }

    /**
     * Test the LRU based eviction policy of the tracker. Both the process of tracking new sightings of a given
     * JMSMessageID and retrieving the existing count will involve accessing an existing record and making it 
     * the most recently accessed. Commit/Rollback/Recover should remove any messages that can't be seen again
     * due to consumption or rejection. Any other messages must be evicted by LRU policy as and when necessary
     * to make way for new entries.
     * 
     * Test this by validating that upon tracking one more message than the capacity, the first message count 
     * is lost. Then access the second message count and insert a further new message occurrence. Verify the 
     * third message count is removed and not the second message count.
     */
    public void testLRUeviction()
    {
        long id;

        for(id=1; id <= CAPACITY + 1; id ++)
        {
            _tracker.recordMessage(String.valueOf(id), id);
        }
        
        assertEquals("Count was not as expected. First delivery tag " +
        		"should have been evicted already:", _tracker.getDeliveryCount(1L), 0L);
        
        //Retrieve second delivery tag count, ensure it is not zero.
        //This will also make it the most recently accessed record.
        assertEquals("Count was not as expected.", _tracker.getDeliveryCount(2L), 1L);
        
        //Add a new record, check that tag 2 remains and tag 3 was evicted.
        _tracker.recordMessage(String.valueOf(id), id);
        assertEquals("Count was not as expected. Second delivery tag " +
                "should NOT have been evicted already:", _tracker.getDeliveryCount(2L), 1L);
        assertEquals("Count was not as expected. Third delivery tag " +
                "should have been evicted already:", _tracker.getDeliveryCount(3L), 0L);
    }

    /**
     * Test that once it is known a record can never be useful again it can be successfully removed
     * from the records to allow room for new records without causing eviction of information that 
     * could still be useful.
     * 
     * Fill the tracker with records, ensure the counts are correct and then delete them, and ensure the
     * counts get reset.
     */
    public void testMessageRecordRemoval()
    {
        long id;

        for(id=1 ; id <= CAPACITY; id ++)
        {
            _tracker.recordMessage(String.valueOf(id), id);
        }

        assertEquals("Count was not as expected.", _tracker.getDeliveryCount(1L), 1L);
        assertEquals("Count was not as expected.", _tracker.getDeliveryCount(CAPACITY/2), 1L);
        assertEquals("Count was not as expected.", _tracker.getDeliveryCount(CAPACITY-1), 1L);
        
        //remove records for first deliveryTag, ensure the others remain as expected
        _tracker.removeRecordsForMessage(1L);
        assertEquals("Count was not as expected.", _tracker.getDeliveryCount(1L), 0L);
        assertEquals("Count was not as expected.", _tracker.getDeliveryCount(CAPACITY/2), 1L);
        assertEquals("Count was not as expected.", _tracker.getDeliveryCount(CAPACITY-1), 1L);
        
        //remove records for next deliveryTag, ensure the others remain as expected
        _tracker.removeRecordsForMessage(CAPACITY/2);
        assertEquals("Count was not as expected.", _tracker.getDeliveryCount(1L), 0L);
        assertEquals("Count was not as expected.", _tracker.getDeliveryCount(CAPACITY/2), 0L);
        assertEquals("Count was not as expected.", _tracker.getDeliveryCount(CAPACITY-1), 1L);
        
        //remove records for next deliveryTag, ensure the others remain as expected
        _tracker.removeRecordsForMessage(CAPACITY-1);
        assertEquals("Count was not as expected.", _tracker.getDeliveryCount(1L), 0L);
        assertEquals("Count was not as expected.", _tracker.getDeliveryCount(CAPACITY/2), 0L);
        assertEquals("Count was not as expected.", _tracker.getDeliveryCount(CAPACITY-1), 0L);
        
        //ensure records for last deliveryTag is still as expected
        assertEquals("Count was not as expected.", _tracker.getDeliveryCount(CAPACITY), 1L);
    }
    
    /**
     * Test that counts are accurately incremented and associated with the new deliveryTag when
     * a message with the same JMSMessageID is encountered again, also ensuring that record of
     * the count is no longer returned via the old deliveryTag
     */
    public void testCounting()
    {
        long id;

        for(id=1 ; id <= CAPACITY; id ++)
        {
            _tracker.recordMessage(String.valueOf(id), id);
        }
        
        //verify all counts are currently 1
        ArrayList<Long> exclusions = new ArrayList<Long>();
        verifyCounts(1,exclusions, CAPACITY);
        
        //Gather some of the existing JMSMessageIDs and create new deliveryTags for them, which can
        //be used to represent receiving the same message again.
        String msgId1 = String.valueOf(1L);
        long newTag1 = id;
        String msgId2 = String.valueOf(CAPACITY/2);
        long newTag2 = ++id;
        String msgId3 = String.valueOf(CAPACITY);
        long newTag3 = ++id;
        _tracker.recordMessage(String.valueOf(msgId1), newTag1);
        _tracker.recordMessage(String.valueOf(msgId2), newTag2);
        _tracker.recordMessage(String.valueOf(msgId3), newTag3);
        
        //Now check the updated values returned are as expected. 
        
        //entries for delivery tags with value 1,CAPACITY/2,CAPACITY should have just been removed 
        //because new delivery tags associated with the same JMSMessageID were just recorded.
        assertEquals("Count was not as expected.", 0L, _tracker.getDeliveryCount(1));
        assertEquals("Count was not as expected.", 0L, _tracker.getDeliveryCount(CAPACITY/2));
        assertEquals("Count was not as expected.", 0L, _tracker.getDeliveryCount(CAPACITY));
        
        //The count for the 'redelivered' messages with new deliveryTag should have increased. 
        assertEquals("Count was not as expected.", 2L, _tracker.getDeliveryCount(newTag1));
        assertEquals("Count was not as expected.", 2L, _tracker.getDeliveryCount(newTag2));
        assertEquals("Count was not as expected.", 2L, _tracker.getDeliveryCount(newTag3));
        
        //all the other delivery tags should remain at 1:
        exclusions.add(1L);
        exclusions.add((long) CAPACITY/2);
        exclusions.add((long) CAPACITY);
        exclusions.add(newTag1);
        exclusions.add(newTag2);
        exclusions.add(newTag3);
        verifyCounts(1,exclusions, CAPACITY+3);
    }
    
    private void verifyCounts(long expectedValue, ArrayList<Long> excludeFromCheck, long numValues)
    {
        for(long id=1 ; id <= numValues; id ++)
        {
            if (!excludeFromCheck.contains(id))
            {
                assertEquals("Count was not as expected for id '" + id + "'.", 1L, _tracker.getDeliveryCount(id));
            }
        }
    }
}
