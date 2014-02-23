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

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

final class QueueContext<E extends QueueEntryImpl<E,Q,L>, Q extends AbstractQueue<E,Q,L>, L extends QueueEntryListBase<E,Q,L>>
{
    private volatile E _lastSeenEntry;
    private volatile E _releasedEntry;

    static final AtomicReferenceFieldUpdater<QueueContext, QueueEntryImpl>
            _lastSeenUpdater =
        AtomicReferenceFieldUpdater.newUpdater
        (QueueContext.class, QueueEntryImpl.class, "_lastSeenEntry");
    static final AtomicReferenceFieldUpdater<QueueContext, QueueEntryImpl>
            _releasedUpdater =
        AtomicReferenceFieldUpdater.newUpdater
        (QueueContext.class, QueueEntryImpl.class, "_releasedEntry");

    public QueueContext(E head)
    {
        _lastSeenEntry = head;
    }

    public E getLastSeenEntry()
    {
        return _lastSeenEntry;
    }


    E getReleasedEntry()
    {
        return _releasedEntry;
    }

    @Override
    public String toString()
    {
        return "QueueContext{" +
               "_lastSeenEntry=" + _lastSeenEntry +
               ", _releasedEntry=" + _releasedEntry +
               '}';
    }
}
