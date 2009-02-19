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

import org.apache.commons.configuration.Configuration;
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
import org.apache.qpid.server.transactionlog.TransactionLog;
import org.apache.qpid.server.routing.RoutingTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** A simple message store that stores the messages in a threadsafe structure in memory.
 *
 * NOTE: Now that we have removed the MessageStore interface and are using a TransactionLog
 *
 * This class really should have no storage unless we want to do inMemory Recovery.
 *
 */
public class MemoryMessageStore implements TransactionLog, RoutingTable
{
    private static final Logger _log = Logger.getLogger(MemoryMessageStore.class);

    private static final int DEFAULT_HASHTABLE_CAPACITY = 50000;

    private static final String HASHTABLE_CAPACITY_CONFIG = "hashtable-capacity";

    protected ConcurrentMap<Long, MessageMetaData> _metaDataMap;

    protected ConcurrentMap<Long, List<ContentChunk>> _contentBodyMap;

    private final AtomicLong _messageId = new AtomicLong(1);
    private AtomicBoolean _closed = new AtomicBoolean(false);

    public void configure()
    {
        _log.info("Using capacity " + DEFAULT_HASHTABLE_CAPACITY + " for hash tables");
        _metaDataMap = new ConcurrentHashMap<Long, MessageMetaData>(DEFAULT_HASHTABLE_CAPACITY);
        _contentBodyMap = new ConcurrentHashMap<Long, List<ContentChunk>>(DEFAULT_HASHTABLE_CAPACITY);
    }

    public void configure(String base, VirtualHostConfiguration config)
    {
        //Only initialise when called with current 'store' configs i.e. don't reinit when used as a 'RoutingTable'
        if (base.equals("store"))
        {
            int hashtableCapacity = config.getStoreConfiguration().getInt(base + "." + HASHTABLE_CAPACITY_CONFIG, DEFAULT_HASHTABLE_CAPACITY);
            _log.info("Using capacity " + hashtableCapacity + " for hash tables");
            _metaDataMap = new ConcurrentHashMap<Long, MessageMetaData>(hashtableCapacity);
            _contentBodyMap = new ConcurrentHashMap<Long, List<ContentChunk>>(hashtableCapacity);
        }
    }

    public void configure(VirtualHost virtualHost, String base, VirtualHostConfiguration config) throws Exception
    {
        configure(base, config);
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
    }

    public void removeMessage(StoreContext context, Long messageId) throws AMQException
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

    public void storeContentBodyChunk(StoreContext context, Long messageId, int index, ContentChunk contentBody, boolean lastContentBody)
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

    public void storeMessageMetaData(StoreContext context, Long messageId, MessageMetaData messageMetaData)
            throws AMQException
    {
        checkNotClosed();
        _metaDataMap.put(messageId, messageMetaData);
    }

    public MessageMetaData getMessageMetaData(StoreContext context, Long messageId) throws AMQException
    {
        checkNotClosed();
        return _metaDataMap.get(messageId);
    }

    public ContentChunk getContentBodyChunk(StoreContext context, Long messageId, int index) throws AMQException
    {
        checkNotClosed();
        List<ContentChunk> bodyList = _contentBodyMap.get(messageId);
        return bodyList.get(index);
    }

    public boolean isPersistent()
    {
        return false;
    }

    private void checkNotClosed() throws MessageStoreClosedException
     {
        if (_closed.get())
        {
            throw new MessageStoreClosedException();
        }
    }
}
