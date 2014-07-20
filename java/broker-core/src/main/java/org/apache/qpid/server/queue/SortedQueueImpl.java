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

import java.util.Map;

import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class SortedQueueImpl extends OutOfOrderQueue<SortedQueueImpl> implements SortedQueue<SortedQueueImpl>
{
    //Lock object to synchronize enqueue. Used instead of the object
    //monitor to prevent lock order issues with consumer sendLocks
    //and consumer updates in the super classes
    private final Object _sortedQueueLock = new Object();

    @ManagedAttributeField
    private String _sortKey;
    private SortedQueueEntryList _entries;

    @ManagedObjectFactoryConstructor
    public SortedQueueImpl(Map<String, Object> attributes, VirtualHostImpl virtualHost)
    {
        super(attributes, virtualHost);
    }

    @Override
    protected void onOpen()
    {
        super.onOpen();
        _entries = new SortedQueueEntryList(this);
    }

    @Override
    protected void doEnqueue(final ServerMessage message,
                        final Action<? super MessageInstance> action)
    {
        synchronized (_sortedQueueLock)
        {
            super.doEnqueue(message, action);
        }
    }

    @Override
    SortedQueueEntryList getEntries()
    {
        return _entries;
    }

    @Override
    public String getSortKey()
    {
        return _sortKey;
    }
}
