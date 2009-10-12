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
package org.apache.qpid.server.store;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.server.queue.MessageMetaData;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.message.ServerMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.ByteBuffer;

/** A simple message store that stores the messages in a threadsafe structure in memory. */
public class MemoryMessageStore extends AbstractMessageStore
{
    private static final Logger _log = Logger.getLogger(MemoryMessageStore.class);

    private static final int DEFAULT_HASHTABLE_CAPACITY = 50000;

    private static final String HASHTABLE_CAPACITY_CONFIG = "hashtable-capacity";

    protected ConcurrentMap<Long, MessageMetaData> _metaDataMap;

    protected ConcurrentMap<Long, List<ContentChunk>> _contentBodyMap;

    private final AtomicLong _messageId = new AtomicLong(1);
    private AtomicBoolean _closed = new AtomicBoolean(false);
    private LogSubject _logSubject;

    public void configure(VirtualHost virtualHost, String base, VirtualHostConfiguration config) throws Exception
    {
        super.configure(virtualHost,base,config);

        int hashtableCapacity = config.getStoreConfiguration().getInt(base + "." + HASHTABLE_CAPACITY_CONFIG, DEFAULT_HASHTABLE_CAPACITY);
        _log.info("Using capacity " + hashtableCapacity + " for hash tables");
        _metaDataMap = new ConcurrentHashMap<Long, MessageMetaData>(hashtableCapacity);
        _contentBodyMap = new ConcurrentHashMap<Long, List<ContentChunk>>(hashtableCapacity);
    }

    public void close() throws Exception
    {
        _closed.getAndSet(true);
        if (_metaDataMap != null)
        {
            _metaDataMap.clear();
            _metaDataMap = null;
        }
        if (_contentBodyMap != null)
        {
            _contentBodyMap.clear();
            _contentBodyMap = null;
        }

        super.close();
    }

    public void removeMessage(Long messageId) throws AMQException
    {
        checkNotClosed();
        if (_log.isDebugEnabled())
        {
            _log.debug("Removing message with id " + messageId);
        }
        _metaDataMap.remove(messageId);
        _contentBodyMap.remove(messageId);
    }

    public void createExchange(Exchange exchange) throws AMQException
    {

    }

    public void removeExchange(Exchange exchange) throws AMQException
    {

    }

    public void bindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {

    }

    public void unbindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {

    }


    public void createQueue(AMQQueue queue) throws AMQException
    {
        // Not requred to do anything
    }

    public void createQueue(AMQQueue queue, FieldTable arguments) throws AMQException
    {
        // Not required to do anything
    }

    public void removeQueue(final AMQQueue queue) throws AMQException
    {
        // Not required to do anything
    }

    public void enqueueMessage(StoreContext context, final AMQQueue queue, Long messageId) throws AMQException
    {
        // Not required to do anything
    }

    public void dequeueMessage(StoreContext context, final AMQQueue queue, Long messageId) throws AMQException
    {
        // Not required to do anything
    }

    public void beginTran(StoreContext context) throws AMQException
    {
        // Not required to do anything
    }

    public void commitTran(StoreContext context) throws AMQException
    {
        // Not required to do anything
    }

    public StoreFuture commitTranAsync(StoreContext context) throws AMQException
    {
        commitTran(context);
        return new StoreFuture()
                    {
                        public boolean isComplete()
                        {
                            return true;
                        }

                        public void waitForCompletion()
                        {

                        }
                    };

    }

    public void abortTran(StoreContext context) throws AMQException
    {
        // Not required to do anything
    }

    public boolean inTran(StoreContext context)
    {
        return false;
    }

    public List<AMQQueue> createQueues() throws AMQException
    {
        return null;
    }

    public Long getNewMessageId()
    {
        return _messageId.getAndIncrement();
    }

    public void storeContentBodyChunk(
            Long messageId,
            int index,
            ContentChunk contentBody,
            boolean lastContentBody)
            throws AMQException
    {
        checkNotClosed();
        List<ContentChunk> bodyList = _contentBodyMap.get(messageId);

        if (bodyList == null && lastContentBody)
        {
            _contentBodyMap.put(messageId, Collections.singletonList(contentBody));
        }
        else
        {
            if (bodyList == null)
            {
                bodyList = new ArrayList<ContentChunk>();
                _contentBodyMap.put(messageId, bodyList);
            }

            bodyList.add(index, contentBody);
        }
    }

    public void storeMessageMetaData(Long messageId, MessageMetaData messageMetaData)
            throws AMQException
    {
        checkNotClosed();
        _metaDataMap.put(messageId, messageMetaData);
    }

    public MessageMetaData getMessageMetaData(Long messageId) throws AMQException
    {
        checkNotClosed();
        return _metaDataMap.get(messageId);
    }

    public ContentChunk getContentBodyChunk(Long messageId, int index) throws AMQException
    {
        checkNotClosed();
        List<ContentChunk> bodyList = _contentBodyMap.get(messageId);
        return bodyList.get(index);
    }

    public boolean isPersistent()
    {
        return false;
    }

    public void storeMessageHeader(Long messageNumber, ServerMessage message)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void storeContent(Long messageNumber, long offset, ByteBuffer body)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public ServerMessage getMessage(Long messageNumber)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    private void checkNotClosed() throws MessageStoreClosedException
     {
        if (_closed.get())
        {
            throw new MessageStoreClosedException();
        }
    }
}
