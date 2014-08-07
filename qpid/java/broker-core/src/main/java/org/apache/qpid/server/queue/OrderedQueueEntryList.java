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

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.apache.qpid.server.message.ServerMessage;

public abstract class OrderedQueueEntryList implements QueueEntryList
{

    private final OrderedQueueEntry _head;

    private volatile OrderedQueueEntry _tail;

    static final AtomicReferenceFieldUpdater<OrderedQueueEntryList, OrderedQueueEntry>
            _tailUpdater =
        AtomicReferenceFieldUpdater.newUpdater
        (OrderedQueueEntryList.class, OrderedQueueEntry.class, "_tail");


    private final AMQQueue _queue;

    static final AtomicReferenceFieldUpdater<OrderedQueueEntry, OrderedQueueEntry>
                _nextUpdater = OrderedQueueEntry._nextUpdater;

    private AtomicLong _scavenges = new AtomicLong(0L);
    private final long _scavengeCount = Integer.getInteger("qpid.queue.scavenge_count", 50);
    private final AtomicReference<QueueEntry> _unscavengedHWM = new AtomicReference<QueueEntry>();


    public OrderedQueueEntryList(AMQQueue queue, HeadCreator headCreator)
    {
        _queue = queue;
        _head = headCreator.createHead(this);
        _tail = _head;
    }

    void scavenge()
    {
        QueueEntry hwm = _unscavengedHWM.getAndSet(null);
        QueueEntry next = _head.getNextValidEntry();

        if(hwm != null)
        {
            while (next != null && hwm.compareTo(next)>0)
            {
                next = next.getNextValidEntry();
            }
        }
    }


    public AMQQueue getQueue()
    {
        return _queue;
    }


    public QueueEntry add(ServerMessage message)
    {
        OrderedQueueEntry node = createQueueEntry(message);
        for (;;)
        {
            OrderedQueueEntry tail = _tail;
            OrderedQueueEntry next = tail.getNextNode();
            if (tail == _tail)
            {
                if (next == null)
                {
                    node.setEntryId(tail.getEntryId()+1);
                    if (_nextUpdater.compareAndSet(tail, null, node))
                    {
                        _tailUpdater.compareAndSet(this, tail, node);

                        return node;
                    }
                }
                else
                {
                    _tailUpdater.compareAndSet(this,tail, next);
                }
            }
        }
    }

    abstract protected OrderedQueueEntry createQueueEntry(ServerMessage<?> message);

    @Override
    public QueueEntry next(QueueEntry node)
    {
        return node.getNextValidEntry();
    }

    public static interface HeadCreator
    {
        OrderedQueueEntry createHead(QueueEntryList list);
    }

    public static class QueueEntryIteratorImpl implements QueueEntryIterator
    {
        private QueueEntry _lastNode;

        QueueEntryIteratorImpl(QueueEntry startNode)
        {
            _lastNode = startNode;
        }

        public boolean atTail()
        {
            return _lastNode.getNextValidEntry() == null;
        }

        public QueueEntry getNode()
        {
            return _lastNode;
        }

        public boolean advance()
        {
            QueueEntry nextValidNode = _lastNode.getNextValidEntry();

            if(nextValidNode != null)
            {
                _lastNode = nextValidNode;
            }

            return nextValidNode != null;
        }
    }

    public QueueEntryIterator iterator()
    {
        return new QueueEntryIteratorImpl(_head);
    }


    public QueueEntry getHead()
    {
        return _head;
    }

    public void entryDeleted(QueueEntry queueEntry)
    {
        QueueEntry next = _head.getNextNode();
        QueueEntry newNext = _head.getNextValidEntry();

        // the head of the queue has not been deleted, hence the deletion must have been mid queue.
        if (next == newNext)
        {
            QueueEntry unscavengedHWM = _unscavengedHWM.get();
            while(unscavengedHWM == null || unscavengedHWM.compareTo(queueEntry)<0)
            {
                _unscavengedHWM.compareAndSet(unscavengedHWM, queueEntry);
                unscavengedHWM = _unscavengedHWM.get();
            }
            if (_scavenges.incrementAndGet() > _scavengeCount)
            {
                _scavenges.set(0L);
                scavenge();
            }
        }
        else
        {
            QueueEntry unscavengedHWM = _unscavengedHWM.get();
            if(unscavengedHWM != null && (next == null || unscavengedHWM.compareTo(next) < 0))
            {
                _unscavengedHWM.compareAndSet(unscavengedHWM, null);
            }
        }
    }

    public int getPriorities()
    {
        return 0;
    }

    @Override
    public QueueEntry getOldestEntry()
    {
        return next(getHead());
    }

}
