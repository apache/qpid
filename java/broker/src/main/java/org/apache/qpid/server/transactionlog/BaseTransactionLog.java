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
import java.util.Map;
import java.util.Set;

public class BaseTransactionLog implements TransactionLog
{
    private static final Logger _logger = Logger.getLogger(BaseTransactionLog.class);

    TransactionLog _delegate;
    private Map<Long, ArrayList<AMQQueue>> _idToQueues = new HashMap<Long, ArrayList<AMQQueue>>();

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
        context.enqueueMessage(queues, messageId);

        if (queues.size() > 0)
        {
            _logger.info("Recording Enqueue of (" + messageId + ") on queue:" + queues);

            //Clone the list incase someone else changes it.
            _idToQueues.put(messageId, (ArrayList) queues.clone());
        }

        _delegate.enqueueMessage(context, queues, messageId);
    }

    public void dequeueMessage(StoreContext context, AMQQueue queue, Long messageId) throws AMQException
    {
        if (context.inTransaction())
        {
            context.dequeueMessage(queue, messageId);

            Map<Long, ArrayList<AMQQueue>> messageMap = context.getDequeueMap();

            //For each Message ID that is in the map check
            for (Long messageID : messageMap.keySet())
            {
                //If we don't have a gloabl reference for this message then there is only a single enqueue
                if (_idToQueues.get(messageID) == null)
                {
                    // Add the removal of the message to this transaction
                    _delegate.removeMessage(context,messageID);
                    // Remove this message ID as we have processed it so we don't reprocess after the main commmit
                    messageMap.remove(messageID);
                }
            }
        }

        _delegate.dequeueMessage(context, queue, messageId);

        if (!context.inTransaction())
        {
            HashMap<Long, ArrayList<AMQQueue>> dequeue = new HashMap<Long, ArrayList<AMQQueue>>();
            ArrayList list = new ArrayList();
            list.add(queue);
            dequeue.put(messageId, list);
            processDequeues(dequeue);
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

        // If we have dequeues to process then process them
        if (context.getDequeueMap() != null)
        {
            processDequeues(context.getDequeueMap());
        }

        //Commit the recorded state for this transaction.
        context.commitTransaction();
    }

    public void abortTran(StoreContext context) throws AMQException
    {
        // If we have enqueues to rollback
        if (context.getEnqueueMap() != null)
        {
            processDequeues(context.getEnqueueMap());
        }
        //Abort the recorded state for this transaction.
        context.abortTransaction();

        _delegate.abortTran(context);
    }

    private void processDequeues(Map<Long, ArrayList<AMQQueue>> messageMap)
            throws AMQException
    {
        // Process any enqueues to bring our model up to date.
        Set<Long> messageIDs = messageMap.keySet();

        //Create a new Asynchronous Context.
        StoreContext removeContext = new StoreContext(true);

        //Batch Process the Dequeues on the delegate
        _delegate.beginTran(removeContext);

        try
        {
            //For each Message ID Decrement the reference for each of the queues it was on.
            for (Long messageID : messageIDs)
            {
                ArrayList<AMQQueue> queueList = messageMap.get(messageID);

                // For each of the queues decrement the reference
                for (AMQQueue queue : queueList)
                {
                    ArrayList<AMQQueue> enqueuedList = _idToQueues.get(messageID);

                    // If we have no mapping then this message was only enqueued on a single queue
                    // This will be the case when we are not in a larger transaction
                    if (enqueuedList == null)
                    {
                        _delegate.removeMessage(removeContext, messageID);
                    }
                    else
                    {
                        // Update the enqueued list
                        enqueuedList.remove(queue);

                        // If the list is now empty then remove the message
                        if (enqueuedList.isEmpty())
                        {
                            _delegate.removeMessage(removeContext, messageID);
                        }
                    }
                }
            }

            //Commit the removes on the delegate.
            _delegate.commitTran(removeContext);
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
}
