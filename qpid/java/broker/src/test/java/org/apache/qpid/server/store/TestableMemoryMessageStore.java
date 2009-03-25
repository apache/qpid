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
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.MessageMetaData;
import org.apache.qpid.server.routing.RoutingTable;
import org.apache.qpid.server.transactionlog.TransactionLog;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.commons.configuration.Configuration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds some extra methods to the memory message store for testing purposes.
 */
public class TestableMemoryMessageStore implements TestTransactionLog, TransactionLog, RoutingTable
{

    MemoryMessageStore _mms = null;

    public TestableMemoryMessageStore(MemoryMessageStore mms)
    {
        _mms = mms;
    }

    public TestableMemoryMessageStore()
    {
        _mms = new MemoryMessageStore();
        _mms.configure();
    }

    public ConcurrentMap<Long, MessageMetaData> getMessageMetaDataMap()
    {
        return _mms._metaDataMap;
    }

    public ConcurrentMap<Long, List<ContentChunk>> getContentBodyMap()
    {
        return _mms._contentBodyMap;
    }

    public List<AMQQueue> getMessageReferenceMap(Long messageId)
    {
        return _mms._messageEnqueueMap.get(messageId);
    }

    public void configure(VirtualHost virtualHost, String base, VirtualHostConfiguration config) throws Exception
    {
        _mms.configure(virtualHost,base,config);
    }

    public void close() throws Exception
    {
        _mms.close();
    }

    public void createExchange(Exchange exchange) throws AMQException
    {
        _mms.createExchange(exchange);
    }

    public void removeExchange(Exchange exchange) throws AMQException
    {
        _mms.removeExchange(exchange);
    }

    public void bindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        _mms.bindQueue(exchange,routingKey,queue,args);
    }

    public void unbindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        _mms.unbindQueue(exchange,routingKey,queue,args);
    }

    public void createQueue(AMQQueue queue) throws AMQException
    {
        _mms.createQueue(queue);
    }

    public void createQueue(AMQQueue queue, FieldTable arguments) throws AMQException
    {
        _mms.createQueue(queue,arguments);
    }

    public void removeQueue(AMQQueue queue) throws AMQException
    {
        _mms.removeQueue(queue);
    }

    public void enqueueMessage(StoreContext context, AMQQueue queue, Long messageId) throws AMQException
    {
        _mms.enqueueMessage(context,queue,messageId);
    }

    public void dequeueMessage(StoreContext context, AMQQueue queue, Long messageId) throws AMQException
    {
        _mms.dequeueMessage(context,queue,messageId);
    }

    public void beginTran(StoreContext context) throws AMQException
    {
        _mms.beginTran(context);
    }

    public void commitTran(StoreContext context) throws AMQException
    {
        _mms.commitTran(context);
    }

    public void abortTran(StoreContext context) throws AMQException
    {
    _mms.abortTran(context);
    }

    public boolean inTran(StoreContext context)
    {
        return _mms.inTran(context);
    }

    public void storeContentBodyChunk(StoreContext context, Long messageId, int index, ContentChunk contentBody, boolean lastContentBody) throws AMQException
    {
        _mms.storeContentBodyChunk(context,messageId,index,contentBody,lastContentBody);
    }

    public void storeMessageMetaData(StoreContext context, Long messageId, MessageMetaData messageMetaData) throws AMQException
    {
        _mms.storeMessageMetaData(context,messageId,messageMetaData);
    }

    public MessageMetaData getMessageMetaData(StoreContext context, Long messageId) throws AMQException
    {
        return _mms.getMessageMetaData(context,messageId);
    }

    public ContentChunk getContentBodyChunk(StoreContext context, Long messageId, int index) throws AMQException
    {
        return _mms.getContentBodyChunk(context,messageId,index);
    }

    public boolean isPersistent()
    {
        return _mms.isPersistent();
    }
}
