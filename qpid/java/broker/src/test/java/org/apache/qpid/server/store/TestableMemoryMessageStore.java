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

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.MessageMetaData;
import org.apache.qpid.server.routing.RoutingTable;
import org.apache.qpid.server.transactionlog.BaseTransactionLog;
import org.apache.qpid.server.transactionlog.TransactionLog;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

/** Adds some extra methods to the memory message store for testing purposes. */
public class TestableMemoryMessageStore implements TestTransactionLog, TransactionLog, RoutingTable
{
    private TransactionLog _transactionLog;
    private RoutingTable _routingTable;
    private MemoryMessageStore _mms;

    public TestableMemoryMessageStore(TransactionLog log)
    {
        _transactionLog = log;
        if (log instanceof BaseTransactionLog)
        {
            TransactionLog delegate = ((BaseTransactionLog) log).getDelegate();
            if (delegate instanceof RoutingTable)
            {
                _routingTable = (RoutingTable) delegate;
            }
            else
            {
                throw new RuntimeException("Specified BaseTransactionLog does not delegate to a RoutingTable:" + log);
            }

            if (delegate instanceof MemoryMessageStore)
            {
                _mms = (MemoryMessageStore) delegate;
            }

        }
        else
        {
            throw new RuntimeException("Specified BaseTransactionLog is not testable:" + log);
        }

    }

    public TestableMemoryMessageStore(MemoryMessageStore mms)
    {
        _routingTable = mms;
        _transactionLog = mms.configure();
    }

    public TestableMemoryMessageStore()
    {
        _mms = new MemoryMessageStore();
        _transactionLog = _mms.configure();
        _routingTable = _mms;        
    }

    public ConcurrentMap<Long, MessageMetaData> getMessageMetaDataMap()
    {
        return ((MemoryMessageStore) _routingTable)._metaDataMap;
    }

    public ConcurrentMap<Long, List<ContentChunk>> getContentBodyMap()
    {
        return ((MemoryMessageStore) _routingTable)._contentBodyMap;
    }

    public List<AMQQueue> getMessageReferenceMap(Long messageId)
    {
//        return _mms._messageEnqueueMap.get(messageId);
//        ((BaseTransactionLog)_transactionLog).
        return new ArrayList<AMQQueue>();
    }

    public Object configure(VirtualHost virtualHost, String base, VirtualHostConfiguration config) throws Exception
    {
        _transactionLog  = (TransactionLog) _transactionLog.configure(virtualHost, base, config);
        return _transactionLog;
    }

    public void close() throws Exception
    {
        _transactionLog.close();
        _routingTable.close();
    }

    public void createExchange(Exchange exchange) throws AMQException
    {
        _routingTable.createExchange(exchange);
    }

    public void removeExchange(Exchange exchange) throws AMQException
    {
        _routingTable.removeExchange(exchange);
    }

    public void bindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        _routingTable.bindQueue(exchange, routingKey, queue, args);
    }

    public void unbindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        _routingTable.unbindQueue(exchange, routingKey, queue, args);
    }

    public void createQueue(AMQQueue queue) throws AMQException
    {
        _routingTable.createQueue(queue);
    }

    public void createQueue(AMQQueue queue, FieldTable arguments) throws AMQException
    {
        _routingTable.createQueue(queue, arguments);
    }

    public void removeQueue(AMQQueue queue) throws AMQException
    {
        _routingTable.removeQueue(queue);
    }

    public void enqueueMessage(StoreContext context, ArrayList<AMQQueue> queues, Long messageId) throws AMQException
    {
        _transactionLog.enqueueMessage(context, queues, messageId);
    }

    public void dequeueMessage(StoreContext context, AMQQueue queue, Long messageId) throws AMQException
    {
        _transactionLog.dequeueMessage(context, queue, messageId);
    }

    public void removeMessage(StoreContext context, Long messageId) throws AMQException
    {
        _transactionLog.removeMessage(context, messageId);
    }

    public void beginTran(StoreContext context) throws AMQException
    {
        _transactionLog.beginTran(context);
    }

    public void commitTran(StoreContext context) throws AMQException
    {
        _transactionLog.commitTran(context);
    }

    public void abortTran(StoreContext context) throws AMQException
    {
        _transactionLog.abortTran(context);
    }

    public boolean inTran(StoreContext context)
    {
        return _transactionLog.inTran(context);
    }

    public void storeContentBodyChunk(StoreContext context, Long messageId, int index, ContentChunk contentBody, boolean lastContentBody) throws AMQException
    {
        _transactionLog.storeContentBodyChunk(context, messageId, index, contentBody, lastContentBody);
    }

    public void storeMessageMetaData(StoreContext context, Long messageId, MessageMetaData messageMetaData) throws AMQException
    {
        _transactionLog.storeMessageMetaData(context, messageId, messageMetaData);
    }

    public boolean isPersistent()
    {
        return _transactionLog.isPersistent();
    }

    public MessageMetaData getMessageMetaData(StoreContext context, Long messageId) throws AMQException
    {
        return _mms.getMessageMetaData(context, messageId);
    }

    public ContentChunk getContentBodyChunk(StoreContext context, Long messageId, int index) throws AMQException
    {
        return _mms.getContentBodyChunk(context, messageId, index);
    }
}
