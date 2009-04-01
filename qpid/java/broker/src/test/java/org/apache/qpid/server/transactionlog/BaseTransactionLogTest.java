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

import junit.framework.TestCase;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.MessagePublishInfoImpl;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.MessageMetaData;
import org.apache.qpid.server.queue.MockAMQQueue;
import org.apache.qpid.server.queue.MockContentChunk;
import org.apache.qpid.server.queue.MockPersistentAMQMessage;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BaseTransactionLogTest extends TestCase implements TransactionLog
{
    private boolean _inTransaction;
    final private Map<Long, ArrayList<AMQQueue>> _enqueues = new HashMap<Long, ArrayList<AMQQueue>>();
    final private Map<Long, ArrayList<ContentChunk>> _storeChunks = new HashMap<Long, ArrayList<ContentChunk>>();
    final private Map<Long, MessageMetaData> _storeMetaData = new HashMap<Long, MessageMetaData>();

    BaseTransactionLog _transactionLog;
    private ArrayList<AMQQueue> _queues;
    private MockPersistentAMQMessage _message;

    public void setUp() throws Exception
    {
        super.setUp();
        _transactionLog = new BaseTransactionLog(this);
    }

    public void testSingleEnqueueNoTransactional() throws AMQException
    {
        //Store Data

        _message = new MockPersistentAMQMessage(1L, this);

        _message.addContentBodyFrame(new StoreContext(), new MockContentChunk(100), true);

        MessagePublishInfo mpi = new MessagePublishInfoImpl();

        ContentHeaderBody chb = new ContentHeaderBody();

        _message.setPublishAndContentHeaderBody(new StoreContext(), mpi, chb);

        verifyMessageStored(_message.getMessageId());
        // Enqueue

        _queues = new ArrayList<AMQQueue>();
        MockAMQQueue queue = new MockAMQQueue(this.getName());
        _queues.add(queue);

        _transactionLog.enqueueMessage(new StoreContext(), _queues, _message.getMessageId());

        verifyEnqueuedOnQueues(_message.getMessageId(), _queues);
    }

    public void testSingleDequeueNoTransaction() throws AMQException
    {
        // Enqueue a message to dequeue
        testSingleEnqueueNoTransactional();

        _transactionLog.dequeueMessage(new StoreContext(),_queues.get(0), _message.getMessageId());

        assertNull("Message enqueued", _enqueues.get(_message.getMessageId()));
        assertNull("Message enqueued", _storeChunks.get(_message.getMessageId()));
        assertNull("Message enqueued", _storeMetaData.get(_message.getMessageId()));
    }

    public void testSingleEnqueueTransactional() throws AMQException
    {
        StoreContext context = new StoreContext();

        _transactionLog.beginTran(context);

        //Store Data
        _message = new MockPersistentAMQMessage(1L, this);

        _message.addContentBodyFrame(context, new MockContentChunk(100), true);

        MessagePublishInfo mpi = new MessagePublishInfoImpl();

        ContentHeaderBody chb = new ContentHeaderBody();

        _message.setPublishAndContentHeaderBody(context, mpi, chb);

        _transactionLog.commitTran(context);

        verifyMessageStored(_message.getMessageId());

        // Enqueue
        _transactionLog.beginTran(context);

        _queues = new ArrayList<AMQQueue>();
        MockAMQQueue queue = new MockAMQQueue(this.getName());
        _queues.add(queue);

        _transactionLog.enqueueMessage(context, _queues, _message.getMessageId());

        _transactionLog.commitTran(context);
        verifyEnqueuedOnQueues(_message.getMessageId(), _queues);
    }

    public void testSingleDequeueTransaction() throws AMQException
    {
        // Enqueue a message to dequeue
        testSingleEnqueueTransactional();

        StoreContext context = new StoreContext();

        _transactionLog.beginTran(context);

        _transactionLog.dequeueMessage(context,_queues.get(0), _message.getMessageId());

        _transactionLog.commitTran(context);

        assertNull("Message enqueued", _enqueues.get(_message.getMessageId()));
        assertNull("Message enqueued", _storeChunks.get(_message.getMessageId()));
        assertNull("Message enqueued", _storeMetaData.get(_message.getMessageId()));
    }


    public void testMultipleEnqueueNoTransactional() throws AMQException
    {
        //Store Data

        _message = new MockPersistentAMQMessage(1L, this);

        _message.addContentBodyFrame(new StoreContext(), new MockContentChunk(100), true);

        MessagePublishInfo mpi = new MessagePublishInfoImpl();

        ContentHeaderBody chb = new ContentHeaderBody();

        _message.setPublishAndContentHeaderBody(new StoreContext(), mpi, chb);

        verifyMessageStored(_message.getMessageId());
        // Enqueue

        _queues = new ArrayList<AMQQueue>();

        MockAMQQueue queue = new MockAMQQueue(this.getName());
        _queues.add(queue);

        queue = new MockAMQQueue(this.getName() + "2");
        _queues.add(queue);

        queue = new MockAMQQueue(this.getName() + "3");
        _queues.add(queue);

        _transactionLog.enqueueMessage(new StoreContext(), _queues, _message.getMessageId());

        verifyEnqueuedOnQueues(_message.getMessageId(), _queues);
    }

    public void testMultipleDequeueNoTransaction() throws AMQException
    {
        // Enqueue a message to dequeue
        testMultipleEnqueueNoTransactional();

        _transactionLog.dequeueMessage(new StoreContext(),_queues.get(0), _message.getMessageId());

        ArrayList<AMQQueue> enqueued = _enqueues.get(_message.getMessageId());
        assertNotNull("Message not enqueued", enqueued);
        assertFalse("Message still enqueued on the first queue,",enqueued.contains(_queues.get(0)));
        assertEquals("Message should still be enqueued on 2 queues", 2, enqueued.size());

        assertNotNull("Message not enqueued", _storeChunks.get(_message.getMessageId()));
        assertNotNull("Message not enqueued", _storeMetaData.get(_message.getMessageId()));


        _transactionLog.dequeueMessage(new StoreContext(),_queues.get(1), _message.getMessageId());

        enqueued = _enqueues.get(_message.getMessageId());
        assertNotNull("Message not enqueued", enqueued);
        assertFalse("Message still enqueued on the second queue,",enqueued.contains(_queues.get(1)));
        assertEquals("Message should still be enqueued on 2 queues", 1, enqueued.size());
        
        assertNotNull("Message not enqueued", _storeChunks.get(_message.getMessageId()));
        assertNotNull("Message not enqueued", _storeMetaData.get(_message.getMessageId()));

        _transactionLog.dequeueMessage(new StoreContext(),_queues.get(2), _message.getMessageId());

        assertNull("Message enqueued", _enqueues.get(_message.getMessageId()));
        assertNull("Message enqueued", _storeChunks.get(_message.getMessageId()));
        assertNull("Message enqueued", _storeMetaData.get(_message.getMessageId()));
    }


    public void testMultipleEnqueueTransactional() throws AMQException
    {
        StoreContext context = new StoreContext();

        _transactionLog.beginTran(context);

        //Store Data
        _message = new MockPersistentAMQMessage(1L, this);

        _message.addContentBodyFrame(context, new MockContentChunk(100), true);

        MessagePublishInfo mpi = new MessagePublishInfoImpl();

        ContentHeaderBody chb = new ContentHeaderBody();

        _message.setPublishAndContentHeaderBody(context, mpi, chb);

        _transactionLog.commitTran(context);

        verifyMessageStored(_message.getMessageId());

        // Enqueue
        _transactionLog.beginTran(context);

        _queues = new ArrayList<AMQQueue>();
        MockAMQQueue queue = new MockAMQQueue(this.getName());
        _queues.add(queue);

        queue = new MockAMQQueue(this.getName() + "2");
        _queues.add(queue);

        queue = new MockAMQQueue(this.getName() + "3");
        _queues.add(queue);

        _transactionLog.enqueueMessage(context, _queues, _message.getMessageId());

        _transactionLog.commitTran(context);
        verifyEnqueuedOnQueues(_message.getMessageId(), _queues);
    }

    public void testMultipleDequeueMultipleTransactions() throws AMQException
    {
        // Enqueue a message to dequeue
        testMultipleEnqueueTransactional();

        StoreContext context = new StoreContext();

        _transactionLog.beginTran(context);

        _transactionLog.dequeueMessage(context, _queues.get(0), _message.getMessageId());

        _transactionLog.commitTran(context);
        ArrayList<AMQQueue> enqueued = _enqueues.get(_message.getMessageId());
        assertNotNull("Message not enqueued", enqueued);
        assertFalse("Message still enqueued on the first queue,", enqueued.contains(_queues.get(0)));
        assertEquals("Message should still be enqueued on 2 queues", 2, enqueued.size());

        assertNotNull("Message not enqueued", _storeChunks.get(_message.getMessageId()));
        assertNotNull("Message not enqueued", _storeMetaData.get(_message.getMessageId()));

        _transactionLog.beginTran(context);

        _transactionLog.dequeueMessage(context, _queues.get(1), _message.getMessageId());

        _transactionLog.commitTran(context);

        enqueued = _enqueues.get(_message.getMessageId());
        assertNotNull("Message not enqueued", enqueued);
        assertFalse("Message still enqueued on the second queue,", enqueued.contains(_queues.get(1)));
        assertEquals("Message should still be enqueued on 2 queues", 1, enqueued.size());

        assertNotNull("Message not enqueued", _storeChunks.get(_message.getMessageId()));
        assertNotNull("Message not enqueued", _storeMetaData.get(_message.getMessageId()));

        _transactionLog.beginTran(context);

        _transactionLog.dequeueMessage(context, _queues.get(2), _message.getMessageId());

        _transactionLog.commitTran(context);

        assertNull("Message enqueued", _enqueues.get(_message.getMessageId()));
        assertNull("Message enqueued", _storeChunks.get(_message.getMessageId()));
        assertNull("Message enqueued", _storeMetaData.get(_message.getMessageId()));
    }

     public void testMultipleDequeueSingleTransaction() throws AMQException
    {
        // Enqueue a message to dequeue
        testMultipleEnqueueTransactional();

        StoreContext context = new StoreContext();

        _transactionLog.beginTran(context);

        _transactionLog.dequeueMessage(context, _queues.get(0), _message.getMessageId());

        ArrayList<AMQQueue> enqueued = _enqueues.get(_message.getMessageId());
        assertNotNull("Message not enqueued", enqueued);
        assertFalse("Message still enqueued on the first queue,", enqueued.contains(_queues.get(0)));
        assertEquals("Message should still be enqueued on 2 queues", 2, enqueued.size());

        assertNotNull("Message not enqueued", _storeChunks.get(_message.getMessageId()));
        assertNotNull("Message not enqueued", _storeMetaData.get(_message.getMessageId()));


        _transactionLog.dequeueMessage(context, _queues.get(1), _message.getMessageId());


        enqueued = _enqueues.get(_message.getMessageId());
        assertNotNull("Message not enqueued", enqueued);
        assertFalse("Message still enqueued on the second queue,", enqueued.contains(_queues.get(1)));
        assertEquals("Message should still be enqueued on 2 queues", 1, enqueued.size());

        assertNotNull("Message not enqueued", _storeChunks.get(_message.getMessageId()));
        assertNotNull("Message not enqueued", _storeMetaData.get(_message.getMessageId()));


        _transactionLog.dequeueMessage(context, _queues.get(2), _message.getMessageId());

        _transactionLog.commitTran(context);

        assertNull("Message enqueued", _enqueues.get(_message.getMessageId()));
        assertNull("Message enqueued", _storeChunks.get(_message.getMessageId()));
        assertNull("Message enqueued", _storeMetaData.get(_message.getMessageId()));
    }

    private void verifyMessageStored(Long messageId)
    {
        assertTrue("MessageMD has not been stored", _storeMetaData.containsKey(messageId));
        assertTrue("Messasge Chunk has not been stored", _storeChunks.containsKey(messageId));
    }

    private void verifyEnqueuedOnQueues(Long messageId, ArrayList<AMQQueue> queues)
    {
        ArrayList<AMQQueue> enqueues = _enqueues.get(messageId);

        assertNotNull("Message not enqueued", enqueues);
        assertEquals("Message is not enqueued on the right number of queues", queues.size(), enqueues.size());
        for (AMQQueue queue : queues)
        {
            assertTrue("Message not enqueued on:" + queue, enqueues.contains(queue));
        }
    }

    /*************************** TransactionLog *******************************
     *
     * Simple InMemory TransactionLog that actually records enqueues/dequeues
     */

    /**
     * @param virtualHost The virtual host using by this store
     * @param base        The base element identifier from which all configuration items are relative. For example, if
     *                    the base element is "store", the all elements used by concrete classes will be "store.foo" etc.
     * @param config      The apache commons configuration object.
     *
     * @return
     *
     * @throws Exception
     */
    public Object configure(VirtualHost virtualHost, String base, VirtualHostConfiguration config) throws Exception
    {
        return this;
    }

    public void close() throws Exception
    {
    }

    public void enqueueMessage(StoreContext context, ArrayList<AMQQueue> queues, Long messageId) throws AMQException
    {
        for (AMQQueue queue : queues)
        {
            enqueueMessage(messageId, queue);
        }
    }

    private void enqueueMessage(Long messageId, AMQQueue queue)
    {
        ArrayList<AMQQueue> queues = _enqueues.get(messageId);

        if (queues == null)
        {
            synchronized (_enqueues)
            {
                queues = _enqueues.get(messageId);
                if (queues == null)
                {
                    queues = new ArrayList<AMQQueue>();
                    _enqueues.put(messageId, queues);
                }
            }
        }

        synchronized (queues)
        {
            queues.add(queue);
        }
    }

    public void dequeueMessage(StoreContext context, AMQQueue queue, Long messageId) throws AMQException
    {
        ArrayList<AMQQueue> queues = _enqueues.get(messageId);

        if (queues == null)
        {
            throw new RuntimeException("Attempt to dequeue message(" + messageId + ") from " +
                                       "queue(" + queue + ") but no enqueue data available");
        }

        synchronized (queues)
        {
            if (!queues.contains(queue))
            {
                throw new RuntimeException("Attempt to dequeue message(" + messageId + ") from " +
                                           "queue(" + queue + ") but no message not enqueued on queue");
            }
                       
            queues.remove(queue);
        }
    }

    public void removeMessage(StoreContext context, Long messageId) throws AMQException
    {
        ArrayList<AMQQueue> queues;

        synchronized (_enqueues)
        {
            queues = _enqueues.remove(messageId);
        }

        if (queues == null)
        {
            throw new RuntimeException("Attempt to remove message(" + messageId + ") but " +
                                       "no enqueue data available");
        }

        if (!queues.isEmpty())
        {
            throw new RuntimeException("Removed a message(" + messageId + ") that still had references.");
        }

        synchronized (_storeMetaData)
        {
            _storeMetaData.remove(messageId);
        }

        synchronized (_storeChunks)
        {
            _storeChunks.remove(messageId);
        }

    }

    //
    // This class does not attempt to operate transactionally. It only knows when it should be in a transaction.
    //  Data is stored immediately.
    //

    public void beginTran(StoreContext context) throws AMQException
    {
        context.setPayload(new Object());
    }

    public void commitTran(StoreContext context) throws AMQException
    {
        context.setPayload(null);
    }

    public void abortTran(StoreContext context) throws AMQException
    {
        _inTransaction = false;
    }

    public boolean inTran(StoreContext context)
    {
        return _inTransaction;
    }

    public void storeContentBodyChunk(StoreContext context, Long messageId, int index, ContentChunk contentBody, boolean lastContentBody) throws AMQException
    {
        ArrayList<ContentChunk> chunks = _storeChunks.get(messageId);

        if (chunks == null)
        {
            synchronized (_storeChunks)
            {
                chunks = _storeChunks.get(messageId);
                if (chunks == null)
                {
                    chunks = new ArrayList<ContentChunk>();
                    _storeChunks.put(messageId, chunks);
                }
            }
        }

        synchronized (chunks)
        {
            chunks.add(contentBody);
        }
    }

    public void storeMessageMetaData(StoreContext context, Long messageId, MessageMetaData messageMetaData) throws AMQException
    {
        if (_storeMetaData.get(messageId) != null)
        {
            throw new RuntimeException("Attempt to storeMessageMetaData for messageId(" + messageId + ") but data already exists");
        }

        synchronized (_storeMetaData)
        {
            _storeMetaData.put(messageId, messageMetaData);
        }
    }

    public boolean isPersistent()
    {
        return false;
    }
}
