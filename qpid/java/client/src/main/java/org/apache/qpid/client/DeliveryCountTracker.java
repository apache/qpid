package org.apache.qpid.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.BidiMap;
import org.apache.commons.collections.bidimap.TreeBidiMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeliveryCountTracker
{
    private static final Logger _logger = LoggerFactory.getLogger(DeliveryCountTracker.class);
    
    /**
     * Bidirectional Map of JMSMessageID with MessageTag.
     */
    private BidiMap _jmsIDtoDeliverTag = new TreeBidiMap();
    
    /**
     * Map of JMSMessageIDs with count of deliveries.
     */
    private Map<String,Integer> _receivedMsgIDs;

    private int _capacity;
    
    /**
     * Creates a new DeliveryCountTracker instance.
     * 
     * @param capacity the number of records to track.
     * @throws IllegalArgumentException if specified capacity not > 0
     */
    public DeliveryCountTracker(int capacity) throws IllegalArgumentException
    {
        if(capacity <= 0)
        {
            throw new IllegalArgumentException("Specified capacity must be greater than 0.");
        }
        _capacity  = capacity;
        
        /*
         * HashMap Javadoc states: "If the initial capacity is greater than the maximum number
         * of entries divided by the load factor, no rehash operations will ever occur."
         * 
         * Specifying an additional 5% size at construction with a 1.0 load factor to pre-allocate
         * the entries, bound the max map size, and avoid size increases + associated rehashing.
         */
        int hashMapSize = (int)(_capacity * 1.05f);
        
        /*
         *  Using the access-ordered LinkedHashMap variant to leverage the LRU based entry removal
         *  behaviour provided when then overriding the removeEldestEntry method.
         */
        _receivedMsgIDs = new LinkedHashMap<String,Integer>(hashMapSize, 1.0f, true)
        {
            //Control the max size of the map using LRU based removal upon insertion.
            protected boolean removeEldestEntry(Map.Entry<String,Integer> eldest)
            {
                boolean remove = size() > _capacity;
                
                // If the supplied entry is to be removed, also remove its associated
                // delivery tag
                if(remove)
                {
                    String msgId = eldest.getKey();
                    
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("Removing delivery count records for message : " + msgId);
                    }
                    
                    synchronized (DeliveryCountTracker.this)
                    {
                        //Also remove the message information from the deliveryTag map.
                        if(msgId != null)
                        {
                            _jmsIDtoDeliverTag.remove(msgId);
                        }
                    }
                }
                
                return remove;
            }
        };
    }

    /**
     * Record sighting of a particular JMSMessageID, with the given deliveryTag.
     *
     * @param msgID the JMSMessageID of the message to track
     * @param deliveryTag the delivery tag of the most recent encounter of the message
     * @return the count of how many times the message has now been seen, or 0 if unknown.
     */
    public synchronized int recordMessage(String msgID, long deliveryTag)
    {
        int count = 0;

        if(msgID == null)
        {
            //we can't distinguish between different
            //messages without a JMSMessageID, so skip
            return count;
        }

        _jmsIDtoDeliverTag.put(msgID, deliveryTag);

        //using Integer to allow null check for the count map
        Integer mapCount = _receivedMsgIDs.get(msgID);

        if(mapCount != null)
        {
            count = ++mapCount;

            if (_logger.isDebugEnabled())
            {
                _logger.debug("Incrementing count for JMSMessageID: '" + msgID + "', value now: " + count);
            }
            _receivedMsgIDs.put(msgID, count);
        }
        else
        {
            count = 1;

            if (_logger.isDebugEnabled())
            {
                _logger.debug("Recording first sighting of JMSMessageID '" + msgID + "'");
            }
            _receivedMsgIDs.put(msgID, count);
        }

        return count;
    }

    /**
     * Returns the number of times the message related to the given delivery tag has been seen
     * 
     * @param deliveryTag delivery tag of the message to retrieve the delivery count for
     * @return the delivery count for that message, or 0 if there is no count known
     */
    public synchronized int getDeliveryCount(long deliveryTag)
    {
        String key = (String) _jmsIDtoDeliverTag.getKey(deliveryTag);

        int count = 0;

        if (key != null)
        {
            Integer val = _receivedMsgIDs.get(key);
            if (val != null)
            {
                count = val;
            }
        }

        return count; 
    }

    /**
     * Removes both JMSMessageID and count related records associated with the given deliveryTag if any such records exist.
     * @param deliveryTag the current tag of the message for which the JMSMessageID and count records should be removed
     */
    public synchronized void removeRecordsForMessage(long deliveryTag)
    {
        String msgId = (String) _jmsIDtoDeliverTag.removeValue(deliveryTag);

        if (msgId != null)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Removed deliveryTag mapping for ID: '" + msgId + "'");
            }

            Integer count = _receivedMsgIDs.remove(msgId);
            if(count != null && _logger.isDebugEnabled())
            {
                _logger.debug("Removed count mapping for ID: '" + msgId + "' : " + count);
            }
        }
    }

    /**
     * Removes both JMSMessageID and count related records associated with the given deliveryTags if any such records exist.
     * @param deliveryTags the current tags of the messages for which the JMSMessageID and count records should be removed
     */
    public synchronized void removeRecordsForMessages(List<Long> deliveryTags)
    {
        if (deliveryTags == null)
        {
            return;
        }
        
        for(long tag : deliveryTags)
        {
            removeRecordsForMessage(tag);
        }
    }
}
