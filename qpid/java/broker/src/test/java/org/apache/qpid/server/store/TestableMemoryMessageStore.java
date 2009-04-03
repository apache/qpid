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

import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.MessageMetaData;
import org.apache.qpid.server.transactionlog.BaseTransactionLog;
import org.apache.qpid.server.transactionlog.TestableTransactionLog;
import org.apache.qpid.server.transactionlog.TransactionLog;

import java.util.List;

/** Adds some extra methods to the memory message store for testing purposes. */
public class TestableMemoryMessageStore extends MemoryMessageStore implements TestTransactionLog
{
    private TestableTransactionLog _base;

    public void setBaseTransactionLog(BaseTransactionLog base)
    {
        if (!(base instanceof TestableTransactionLog))
        {
            throw new RuntimeException("base must be a TestableTransactionLog for correct operation in a TestMemoryMessageStore");
        }

        _base = (TestableTransactionLog) base;
    }

    @Override
    public TransactionLog configure()
    {
        BaseTransactionLog base = (BaseTransactionLog) super.configure();

        _base = new TestableTransactionLog(base.getDelegate());

        return _base;
    }

    @Override
    public TransactionLog configure(String base, VirtualHostConfiguration config)
    {
        //Only initialise when called with current 'store' configs i.e. don't reinit when used as a 'RoutingTable'
        if (base.equals("store"))
        {
            super.configure();

            _base = new TestableTransactionLog(this);

            return _base;
        }

        return super.configure();
    }

    public List<AMQQueue> getMessageReferenceMap(Long messageId)
    {
        return _base.getMessageReferenceMap(messageId);
    }

    public MessageMetaData getMessageMetaData(StoreContext context, Long messageId)
    {
        return _metaDataMap.get(messageId);
    }

    public ContentChunk getContentBodyChunk(StoreContext context, Long messageId, int index)
    {
        List<ContentChunk> bodyList = _contentBodyMap.get(messageId);
        return bodyList.get(index);
    }

    public long getMessageMetaDataSize()
    {
        return _metaDataMap.size();
    }

    public TransactionLog getDelegate()
    {
        return _base;
    }
}
