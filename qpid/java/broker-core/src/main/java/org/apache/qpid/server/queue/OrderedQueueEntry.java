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
package org.apache.qpid.server.queue;

import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.store.MessageEnqueueRecord;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public abstract class OrderedQueueEntry extends QueueEntryImpl
{
    static final AtomicReferenceFieldUpdater<OrderedQueueEntry, OrderedQueueEntry>
                _nextUpdater =
            AtomicReferenceFieldUpdater.newUpdater
            (OrderedQueueEntry.class, OrderedQueueEntry.class, "_next");

    private volatile OrderedQueueEntry _next;

    public OrderedQueueEntry(OrderedQueueEntryList queueEntryList)
    {
        super(queueEntryList);
    }

    public OrderedQueueEntry(OrderedQueueEntryList queueEntryList,
                             ServerMessage message,
                             final MessageEnqueueRecord messageEnqueueRecord)
    {
        super(queueEntryList, message, messageEnqueueRecord);
    }

    @Override
    public OrderedQueueEntry getNextNode()
    {
        return _next;
    }

    @Override
    public OrderedQueueEntry getNextValidEntry()
    {

        OrderedQueueEntry next = getNextNode();
        while(next != null && next.isDeleted())
        {

            final OrderedQueueEntry newNext = next.getNextNode();
            if(newNext != null)
            {
                OrderedQueueEntryList._nextUpdater.compareAndSet(this,next, newNext);
                next = getNextNode();
            }
            else
            {
                next = null;
            }

        }
        return next;
    }

}
