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
import org.apache.commons.configuration.Configuration;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A message store that does nothing. Designed to be used in tests that do not want to use any message store
 * functionality.
 */
public class SkeletonMessageStore implements MessageStore
{
    private final AtomicLong _messageId = new AtomicLong(1);

    public void configure(String base, Configuration config) throws Exception
    {
    }

    public void configure(QueueRegistry queueRegistry, String base, Configuration config) throws Exception
    {
    }

    public void close() throws Exception
    {
    }

    public void put(AMQMessage msg)
    {
    }

    public void removeMessage(long messageId)
    {
    }

    public void createQueue(AMQQueue queue) throws AMQException
    {
    }

    public void removeQueue(String name) throws AMQException
    {
    }

    public void enqueueMessage(String name, long messageId) throws AMQException
    {
    }

    public void dequeueMessage(String name, long messageId) throws AMQException
    {
    }

    public void beginTran() throws AMQException
    {
    }

    public boolean inTran()
    {
        return false;
    }
    
    public void commitTran() throws AMQException
    {
    }

    public void abortTran() throws AMQException
    {
    }

    public List<AMQQueue> createQueues() throws AMQException
    {
        return null;
    }

    public long getNewMessageId()
    {
        return _messageId.getAndIncrement();
    }
}
