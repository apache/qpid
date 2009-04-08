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
import org.apache.qpid.server.store.TestTransactionLog;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaseTransactionLogTest extends TestCase implements TransactionLog
{
    private boolean _inTransaction;
    final private Map<Long, ArrayList<AMQQueue>> _enqueues = new HashMap<Long, ArrayList<AMQQueue>>();
    final private Map<Long, ArrayList<ContentChunk>> _storeChunks = new HashMap<Long, ArrayList<ContentChunk>>();
    final private Map<Long, MessageMetaData> _storeMetaData = new HashMap<Long, MessageMetaData>();

    TestTransactionLog _transactionLog;
    private ArrayList<AMQQueue> _queues;
    private MockPersistentAMQMessage _message;
    StoreContext _context;

    public void setUp() throws Exception
    {
        super.setUp();
        _transactionLog = new TestableBaseTransactionLog(this);
        _context = new StoreContext();
    }

    public void testSingleEnqueueNoTransactional() throws AMQException
    {
        //Store Data

        _message = new MockPersistentAMQMessage(1L, this);

        _message.addContentBodyFrame(_context, new MockContentChunk(100), true);

        MessagePublishInfo mpi = new MessagePublishInfoImpl();

        ContentHeaderBody chb = new ContentHeaderBody();

        _message.setPublishAndContentHeaderBody(_context, mpi, chb);

        verifyMessageStored(_message.getMessageId());
        // Enqueue

        _queues = new ArrayList<AMQQueue>();
        MockAMQQueue queue = new MockAMQQueue(this.getName());
        _queues.add(queue);

        _transactionLog.enqueueMessage(_context, _queues, _message.getMessageId());

        verifyEnqueuedOnQueues(_message.getMessageId(), _queues);
    }

    public void testSingleDequeueNoTransaction() throws AMQException
    {
        // Enqueue a message to dequeue
        testSingleEnqueueNoTransactional();

        _transactionLog.dequeueMessage(_context, _queues.get(0), _message.getMessageId());

        verifyMessageRemoved(_message.getMessageId());
    }

    public void testSingleEnqueueTransactional() throws AMQException
    {
        StoreContext context = _context;

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

        StoreContext context = _context;

        _transactionLog.beginTran(context);

        _transactionLog.dequeueMessage(context, _queues.get(0), _message.getMessageId());

        _transactionLog.commitTran(context);

        verifyMessageRemoved(_message.getMessageId());
    }

    public void testMultipleEnqueueNoTransactional() throws AMQException
    {
        //Store Data

        _message = new MockPersistentAMQMessage(1L, this);

        _message.addContentBodyFrame(_context, new MockContentChunk(100), true);

        MessagePublishInfo mpi = new MessagePublishInfoImpl();

        ContentHeaderBody chb = new ContentHeaderBody();

        _message.setPublishAndContentHeaderBody(_context, mpi, chb);

        verifyMessageStored(_message.getMessageId());
        // Enqueue

        _queues = new ArrayList<AMQQueue>();

        MockAMQQueue queue = new MockAMQQueue(this.getName());
        _queues.add(queue);

        queue = new MockAMQQueue(this.getName() + "2");
        _queues.add(queue);

        queue = new MockAMQQueue(this.getName() + "3");
        _queues.add(queue);

        _transactionLog.enqueueMessage(_context, _queues, _message.getMessageId());

        verifyEnqueuedOnQueues(_message.getMessageId(), _queues);
    }

    public void testMultipleDequeueNoTransaction() throws AMQException
    {
        // Enqueue a message to dequeue
        testMultipleEnqueueNoTransactional();

        _transactionLog.dequeueMessage(_context, _queues.get(0), _message.getMessageId());

        ArrayList<AMQQueue> enqueued = _enqueues.get(_message.getMessageId());

        assertFalse("Message still enqueued on the first queue,", enqueued.contains(_queues.get(0)));
        _queues.remove(0);

        verifyEnqueuedOnQueues(_message.getMessageId(), _queues);
        verifyMessageStored(_message.getMessageId());

        _transactionLog.dequeueMessage(_context, _queues.get(0), _message.getMessageId());

        assertFalse("Message still enqueued on the first queue,", enqueued.contains(_queues.get(0)));
        _queues.remove(0);

        ArrayList<AMQQueue> enqueues = _enqueues.get(_message.getMessageId());

        assertNotNull("Message not enqueued", enqueues);
        assertEquals("Message is not enqueued on the right number of queues", _queues.size(), enqueues.size());
        for (AMQQueue queue : _queues)
        {
            assertTrue("Message not enqueued on:" + queue, enqueues.contains(queue));
        }

        //Use the reference map to ensure that we are enqueuing the right number of messages
        List<AMQQueue> references = _transactionLog.getMessageReferenceMap(_message.getMessageId());

        assertNotNull("Message not enqueued", references);
        assertEquals("Message is not enqueued on the right number of queues", _queues.size(), references.size());
        for (AMQQueue queue : references)
        {
            assertTrue("Message not enqueued on:" + queue, references.contains(queue));
        }

        verifyMessageStored(_message.getMessageId());

        _transactionLog.dequeueMessage(_context, _queues.get(0), _message.getMessageId());

        verifyMessageRemoved(_message.getMessageId());
    }

    private void verifyMessageRemoved(Long messageID)
    {
        assertNull("Message references exist", _transactionLog.getMessageReferenceMap(messageID));
        assertNull("Message enqueued", _enqueues.get(messageID));
        assertNull("Message chunks enqueued", _storeChunks.get(messageID));
        assertNull("Message meta data enqueued", _storeMetaData.get(messageID));
    }

    public void testMultipleEnqueueTransactional() throws AMQException
    {
        StoreContext context = _context;

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

        StoreContext context = _context;

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

        verifyMessageRemoved(_message.getMessageId());
    }

    public void testMultipleDequeueSingleTransaction() throws AMQException
    {
        // Enqueue a message to dequeue
        testMultipleEnqueueTransactional();

        StoreContext context = _context;

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

        verifyMessageRemoved(_message.getMessageId());
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

        //Use the reference map to ensure that we are enqueuing the right number of messages
        List<AMQQueue> references = _transactionLog.getMessageReferenceMap(messageId);

        if (queues.size() == 1)
        {
            assertNull("Message has an enqueued list", references);
        }
        else
        {
            assertNotNull("Message not enqueued", references);
            assertEquals("Message is not enqueued on the right number of queues", queues.size(), references.size());
            for (AMQQueue queue : references)
            {
                assertTrue("Message not enqueued on:" + queue, references.contains(queue));
            }
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
            boolean found = false;
            // If we are in a transaction we may have already done the dequeue.
            if (context.inTransaction())
            {

                for (Object record : (ArrayList) context.getPayload())
                {
                    if (record instanceof RemoveRecord)
                    {
                        if (((RemoveRecord) record)._messageId.equals(messageId))
                        {
                            found = true;
                            break;
                        }
                    }
                }
            }

            if (!found)
            {
                throw new RuntimeException("Attempt to dequeue message(" + messageId + ") from " +
                                           "queue(" + queue + ") but no enqueue data available");
            }
        }
        else
        {
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

        if (queues.size() > 1)
        {
            throw new RuntimeException("Removed a message(" + messageId + ") that still had references.");
        }

        MessageMetaData mmd;
        synchronized (_storeMetaData)
        {
            mmd = _storeMetaData.remove(messageId);
        }

        ArrayList<ContentChunk> chunks;
        synchronized (_storeChunks)
        {
            chunks = _storeChunks.remove(messageId);
        }

        //Record the remove for part of the transaction
        if (context.inTransaction())
        {
            ArrayList transactionData = (ArrayList) context.getPayload();
            transactionData.add(new RemoveRecord(messageId, queues, mmd, chunks));
        }
    }

    //
    // This class does not attempt to operate transactionally. It only knows when it should be in a transaction.
    //  Data is stored immediately.
    //

    public void beginTran(StoreContext context) throws AMQException
    {
        context.setPayload(new ArrayList());
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

    class RemoveRecord
    {
        MessageMetaData _mmd;
        ArrayList<AMQQueue> _queues;
        ArrayList<ContentChunk> _chunks;
        Long _messageId;

        RemoveRecord(Long messageId, ArrayList<AMQQueue> queues, MessageMetaData mmd, ArrayList<ContentChunk> chunks)
        {
            _messageId = messageId;
            _queues = queues;
            _mmd = mmd;
            _chunks = chunks;
        }
    }
}
