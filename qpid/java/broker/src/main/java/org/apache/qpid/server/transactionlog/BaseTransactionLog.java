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
package org.apache.qpid.server.transactionlog;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.MessageMetaData;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.List;

public class BaseTransactionLog implements TransactionLog
{
    private static final Logger _logger = Logger.getLogger(BaseTransactionLog.class);

    TransactionLog _delegate;
    protected Map<Long, List<AMQQueue>> _idToQueues = Collections.synchronizedMap(new HashMap<Long, List<AMQQueue>>());

    public BaseTransactionLog(TransactionLog delegate)
    {
        _delegate = delegate;
    }

    public Object configure(VirtualHost virtualHost, String base, VirtualHostConfiguration config) throws Exception
    {
        return _delegate.configure(virtualHost, base, config);
    }

    public void close() throws Exception
    {
        _delegate.close();
    }

    public void enqueueMessage(StoreContext context, ArrayList<AMQQueue> queues, Long messageId) throws AMQException
    {
        if (queues.size() > 1)
        {
            if (_logger.isInfoEnabled())
            {
                _logger.info("Recording Enqueue of (" + messageId + ") on queue:" + queues);
            }
            
            //list to hold which new queues to enqueue the message on
            ArrayList<AMQQueue> toEnqueueList = new ArrayList<AMQQueue>();
            
            List<AMQQueue> enqueuedList = _idToQueues.get(messageId);
            if (enqueuedList != null)
            {
                //There are previous enqueues for this messageId
                synchronized (enqueuedList)
                {
                    for(AMQQueue queue : queues)
                    {
                        if(!enqueuedList.contains(queue))
                        {
                            //update the old list.
                            enqueuedList.add(queue);
                            //keep track of new enqueues to be made
                            toEnqueueList.add(queue);
                        }
                    }
                }
                
                if(toEnqueueList.isEmpty())
                {
                    //no new queues to enqueue message on
                    return;
                }
            }
            else
            {
                //No existing list, add all provided queues (cloning toEnqueueList in case someone else changes original).
                toEnqueueList.addAll(queues);
                _idToQueues.put(messageId, Collections.synchronizedList((ArrayList<AMQQueue>)toEnqueueList.clone()));
            }

            _delegate.enqueueMessage(context, toEnqueueList, messageId);
        }
        else
        {
            _delegate.enqueueMessage(context, queues, messageId);
        }
    }

    public void dequeueMessage(StoreContext context, AMQQueue queue, Long messageId) throws AMQException
    {
        context.dequeueMessage(queue, messageId);

        if (context.inTransaction())
        {

            Map<Long, List<AMQQueue>> messageMap = context.getDequeueMap();

            //For each Message ID that is in the map check
            Set<Long> messageIDs = messageMap.keySet();

            if (_logger.isInfoEnabled())
            {
                _logger.info("Pre-Processing single dequeue of:" + messageIDs);
            }

            Iterator iterator = messageIDs.iterator();

            while (iterator.hasNext())
            {
                Long messageID = (Long) iterator.next();
                //If we don't have a gloabl reference for this message then there is only a single enqueue
                //can check here to see if this is the last reference?
                if (_idToQueues.get(messageID) == null)
                {
                    // Add the removal of the message to this transaction
                    _delegate.removeMessage(context, messageID);
                    // Remove this message ID as we have processed it so we don't reprocess after the main commmit
                    iterator.remove();
                }
            }
        }

        _delegate.dequeueMessage(context, queue, messageId);

        if (!context.inTransaction())
        {
            processDequeues(context.getDequeueMap());
        }
    }

    /**
     * This should not be called from main broker code.
     * // Perhaps we need a new interface:
     *
     * Broker <->TransactionLog
     * Broker <->BaseTransactionLog<->(Log with removeMessage())
     */
    public void removeMessage(StoreContext context, Long messageId) throws AMQException
    {
        _delegate.removeMessage(context, messageId);
    }

    public void beginTran(StoreContext context) throws AMQException
    {
        context.beginTransaction();
        _delegate.beginTran(context);
    }

    public void commitTran(StoreContext context) throws AMQException
    {
        //Perform real commit of current data
        _delegate.commitTran(context);

        processDequeues(context.getDequeueMap());

        //Commit the recorded state for this transaction.
        context.commitTransaction();
    }

    public void abortTran(StoreContext context) throws AMQException
    {
        //Abort the recorded state for this transaction.
        context.abortTransaction();

        _delegate.abortTran(context);
    }

    private void processDequeues(Map<Long, List<AMQQueue>> messageMap)
            throws AMQException
    {
        // Check we have dequeues to process then process them
        if (messageMap == null || messageMap.isEmpty())
        {
            return;
        }

        // Process any enqueues to bring our model up to date.
        Set<Long> messageIDs = messageMap.keySet();

        //Create a new Asynchronous Context.
        StoreContext removeContext = new StoreContext(true);

        //Batch Process the Dequeues on the delegate
        _delegate.beginTran(removeContext);
        removeContext.beginTransaction();

        try
        {
            //For each Message ID Decrement the reference for each of the queues it was on.

            if (_logger.isInfoEnabled())
            {
                _logger.info("Processing Dequeue for:" + messageIDs);
            }

            Iterator<Long> messageIDIterator = messageIDs.iterator();

            while(messageIDIterator.hasNext())
            {
                Long messageID = messageIDIterator.next();
                List<AMQQueue> queueList = messageMap.get(messageID);

               //Remove this message from our DequeueMap as we are processing it.
                messageIDIterator.remove();

                // For each of the queues decrement the reference
                for (AMQQueue queue : queueList)
                {
                    List<AMQQueue> enqueuedList = _idToQueues.get(messageID);

                    if (_logger.isInfoEnabled())
                    {
                        _logger.info("Dequeue message:" + messageID + " from :" + queue);
                    }


                    // If we have no mapping then this message was only enqueued on a single queue
                    // This will be the case when we are not in a larger transaction
                    if (enqueuedList == null)
                    {
                        _delegate.removeMessage(removeContext, messageID);
                    }
                    else
                    {
                        //When a message is on more than one queue it is possible that this code section is exectuted
                        // by one thread per enqueue.
                        // It is however, thread safe because there is only removes being performed and so the
                        // last thread that does the remove will see the empty queue and remove the message
                        // At this stage there is nothing that is going to cause this operation to abort. So we don't
                        // need to worry about any potential adds.
                        // The message will no longer be enqueued as that operation has been committed before now so
                        // this is clean up of the data.

                        //Must synchronize here as this list may have been extracted from _idToQueues in many threads
                        // and we must ensure only one of them update the list at a time.
                        synchronized (enqueuedList)
                        {
                            // Update the enqueued list but if the queue is not in the list then we are trying
                            // to dequeue something that is not there anymore, or was never there.
                            if (!enqueuedList.remove(queue))
                            {
                                throw new UnableToDequeueException(messageID, queue);
                            }

                            // If the list is now empty then remove the message
                            if (enqueuedList.isEmpty())
                            {
                                _delegate.removeMessage(removeContext, messageID);
                                //Remove references list
                                _idToQueues.remove(messageID);
                            }
                        }
                    }
                }
            }
            //Commit the removes on the delegate.
            _delegate.commitTran(removeContext);
            // Mark this context as committed.
            removeContext.commitTransaction();
        }
        finally
        {
            if (removeContext.inTransaction())
            {
                _delegate.abortTran(removeContext);
            }
        }
    }

    public boolean inTran(StoreContext context)
    {
        return _delegate.inTran(context);
    }

    public void storeContentBodyChunk(StoreContext context, Long messageId, int index, ContentChunk contentBody, boolean lastContentBody) throws AMQException
    {
        _delegate.storeContentBodyChunk(context, messageId, index, contentBody, lastContentBody);
    }

    public void storeMessageMetaData(StoreContext context, Long messageId, MessageMetaData messageMetaData) throws AMQException
    {
        _delegate.storeMessageMetaData(context, messageId, messageMetaData);
    }

    public boolean isPersistent()
    {
        return _delegate.isPersistent();
    }

    public TransactionLog getDelegate()
    {
        return _delegate;
    }

    private class UnableToDequeueException extends RuntimeException
    {
        Long _messageID;
        AMQQueue _queue;

        public UnableToDequeueException(Long messageID, AMQQueue queue)
        {
            super("Unable to dequeue message(" + messageID + ") from queue " +
                  "(" + queue + ") it is not/nolonger enqueued on it.");
            _messageID = messageID;
            _queue = queue;
        }
    }
}
