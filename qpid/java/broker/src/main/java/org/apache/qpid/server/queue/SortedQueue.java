/*
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
package org.apache.qpid.server.queue;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.Map;
import java.util.UUID;

public class SortedQueue extends OutOfOrderQueue
{
    //Lock object to synchronize enqueue. Used instead of the object
    //monitor to prevent lock order issues with subscription sendLocks
    //and consumer updates in the super classes
    private final Object _sortedQueueLock = new Object();
    private final String _sortedPropertyName;

    protected SortedQueue(UUID id, final String name,
                            final boolean durable, final String owner, final boolean autoDelete,
                            final boolean exclusive, final VirtualHost virtualHost, Map<String, Object> arguments, String sortedPropertyName)
    {
        super(id, name, durable, owner, autoDelete, exclusive,
                virtualHost, new SortedQueueEntryListFactory(sortedPropertyName), arguments);
        this._sortedPropertyName = sortedPropertyName;
    }

    public String getSortedPropertyName()
    {
        return _sortedPropertyName;
    }

    public void enqueue(ServerMessage message, PostEnqueueAction action) throws AMQException
    {
        synchronized (_sortedQueueLock)
        {
            super.enqueue(message, action);
        }
    }
}