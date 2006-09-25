/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.store;

import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.AMQException;
import org.apache.log4j.Logger;
import org.apache.commons.configuration.Configuration;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;

/**
 * A simple message store that stores the messages in a threadsafe structure in memory.
 *
 */
public class MemoryMessageStore implements MessageStore
{
    private static final Logger _log = Logger.getLogger(MemoryMessageStore.class);

    private static final int DEFAULT_HASHTABLE_CAPACITY = 50000;

    private static final String HASHTABLE_CAPACITY_CONFIG = "hashtable-capacity";

    protected ConcurrentMap<Long, AMQMessage> _messageMap;

    private final AtomicLong _messageId = new AtomicLong(1);

    public void configure()
    {
        _log.info("Using capacity " + DEFAULT_HASHTABLE_CAPACITY + " for hash table");
        _messageMap = new ConcurrentHashMap<Long, AMQMessage>(DEFAULT_HASHTABLE_CAPACITY);        
    }

    public void configure(String base, Configuration config)
    {
        int hashtableCapacity = config.getInt(base + "." + HASHTABLE_CAPACITY_CONFIG, DEFAULT_HASHTABLE_CAPACITY);
        _log.info("Using capacity " + hashtableCapacity + " for hash table");
        _messageMap = new ConcurrentHashMap<Long, AMQMessage>(hashtableCapacity);
    }

    public void configure(QueueRegistry queueRegistry, String base, Configuration config) throws Exception
    {
        configure(base, config);
    }

    public void close() throws Exception
    {
        if(_messageMap != null)
        {
            _messageMap.clear();
            _messageMap = null;
        }
    }

    public void put(AMQMessage msg)
    {
        _messageMap.put(msg.getMessageId(), msg);
    }

    public void removeMessage(long messageId)
    {
        if (_log.isDebugEnabled())
        {
            _log.debug("Removing message with id " + messageId);
        }
        _messageMap.remove(messageId);
    }

    public void createQueue(AMQQueue queue) throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void removeQueue(String name) throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void enqueueMessage(String name, long messageId) throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void dequeueMessage(String name, long messageId) throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void beginTran() throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void commitTran() throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void abortTran() throws AMQException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean inTran()
    {
        return false;
    }

    public List<AMQQueue> createQueues() throws AMQException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getNewMessageId()
    {
        return _messageId.getAndIncrement();
    }

    public AMQMessage getMessage(long messageId)
    {
        return _messageMap.get(messageId);
    }
}
