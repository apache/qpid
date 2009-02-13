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
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.server.queue.MessageMetaData;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.transactionlog.TransactionLog;
import org.apache.qpid.server.routing.RoutingTable;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A message store that does nothing. Designed to be used in tests that do not want to use any message store
 * functionality.
 */
public class SkeletonMessageStore implements TransactionLog , RoutingTable
{
    private final AtomicLong _messageId = new AtomicLong(1);

    public void configure(String base, Configuration config) throws Exception
    {
    }
    
    public void configure(VirtualHost virtualHost, String base, Configuration config) throws Exception
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void close() throws Exception
    {
    }

    public void removeMessage(StoreContext s, Long messageId)
    {
    }

    public void createExchange(Exchange exchange) throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void removeExchange(Exchange exchange) throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void bindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void unbindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void createQueue(AMQQueue queue) throws AMQException
    {
    }

    public void createQueue(AMQQueue queue, FieldTable arguments) throws AMQException
    {
    }        

    public void beginTran(StoreContext s) throws AMQException
    {
    }

    public boolean inTran(StoreContext sc)
    {
        return false;
    }

    public void commitTran(StoreContext storeContext) throws AMQException
    {
    }

    public void abortTran(StoreContext storeContext) throws AMQException
    {
    }

    public List<AMQQueue> createQueues() throws AMQException
    {
        return null;
    }

    public Long getNewMessageId()
    {
        return _messageId.getAndIncrement();
    }

    public void storeContentBodyChunk(StoreContext sc, Long messageId, int index, ContentChunk contentBody, boolean lastContentBody) throws AMQException
    {

    }

    public void storeMessageMetaData(StoreContext sc, Long messageId, MessageMetaData messageMetaData) throws AMQException
    {

    }

    public MessageMetaData getMessageMetaData(StoreContext s,Long messageId) throws AMQException
    {
        return null;
    }

    public ContentChunk getContentBodyChunk(StoreContext s,Long messageId, int index) throws AMQException
    {
        return null;
    }

    public boolean isPersistent()
    {
        return false;
    }

    public void removeQueue(final AMQQueue queue) throws AMQException
    {

    }

    public void enqueueMessage(StoreContext context, final AMQQueue queue, Long messageId) throws AMQException
    {

    }

    public void dequeueMessage(StoreContext context, final AMQQueue queue, Long messageId) throws AMQException
    {

    }
}
