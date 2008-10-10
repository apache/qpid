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
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.abstraction.ContentChunk;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.HashMap;
import java.util.List;

/**
 * Adds some extra methods to the memory message store for testing purposes.
 */
public class TestableMemoryMessageStore extends MemoryMessageStore
{

    MemoryMessageStore _mms = null;
    private HashMap<Long, AMQQueue> _messages = new HashMap<Long, AMQQueue>();

    public TestableMemoryMessageStore(MemoryMessageStore mms)
    {
        _mms = mms;
    }

    public TestableMemoryMessageStore()
    {
        _metaDataMap = new ConcurrentHashMap<Long, MessageMetaData>();
        _contentBodyMap = new ConcurrentHashMap<Long, List<ContentChunk>>();
    }

    public ConcurrentMap<Long, MessageMetaData> getMessageMetaDataMap()
    {
        if (_mms != null)
        {
            return _mms._metaDataMap;
        }
        else
        {
            return _metaDataMap;
        }
    }

    public ConcurrentMap<Long, List<ContentChunk>> getContentBodyMap()
    {
        if (_mms != null)
        {
            return _mms._contentBodyMap;
        }
        else
        {
            return _contentBodyMap;
        }
    }
    
    public void enqueueMessage(StoreContext context, final AMQQueue queue, Long messageId) throws AMQException
    {
        getMessages().put(messageId, queue);
    }

    public void dequeueMessage(StoreContext context, final AMQQueue queue, Long messageId) throws AMQException
    {
        getMessages().remove(messageId);
    }

    public HashMap<Long, AMQQueue> getMessages()
    {
        return _messages;
    }
}
