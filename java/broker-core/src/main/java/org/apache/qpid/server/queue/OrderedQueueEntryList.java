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

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public abstract class OrderedQueueEntryList<E extends OrderedQueueEntry<E,Q,L>, Q extends AbstractQueue<E,Q,L>, L extends OrderedQueueEntryList<E,Q,L>> implements
                                                                                                                                                        QueueEntryListBase<E,Q,L>
{

    private final E _head;

    private volatile E _tail;

    static final AtomicReferenceFieldUpdater<OrderedQueueEntryList, OrderedQueueEntry>
            _tailUpdater =
        AtomicReferenceFieldUpdater.newUpdater
        (OrderedQueueEntryList.class, OrderedQueueEntry.class, "_tail");


    private final Q _queue;

    static final AtomicReferenceFieldUpdater<OrderedQueueEntry, OrderedQueueEntry>
                _nextUpdater = OrderedQueueEntry._nextUpdater;

    private AtomicLong _scavenges = new AtomicLong(0L);
    private final long _scavengeCount = Integer.getInteger("qpid.queue.scavenge_count", 50);
    private final AtomicReference<E> _unscavengedHWM = new AtomicReference<E>();


    public OrderedQueueEntryList(Q queue, HeadCreator<E,Q,L> headCreator)
    {
        _queue = queue;
        _head = headCreator.createHead((L)this);
        _tail = _head;
    }

    void scavenge()
    {
        E hwm = _unscavengedHWM.getAndSet(null);
        E next = _head.getNextValidEntry();

        if(hwm != null)
        {
            while (next != null && hwm.compareTo(next)>0)
            {
                next = next.getNextValidEntry();
            }
        }
    }


    public Q getQueue()
    {
        return _queue;
    }


    public E add(ServerMessage message)
    {
        E node = createQueueEntry(message);
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

    abstract protected E createQueueEntry(ServerMessage<?> message);

    public E next(E node)
    {
        return node.getNextValidEntry();
    }

    public static interface HeadCreator<E extends QueueEntryImpl<E,Q,L>, Q extends AbstractQueue<E,Q,L>, L extends QueueEntryListBase<E,Q,L>>
    {
        E createHead(L list);
    }

    public static class QueueEntryIteratorImpl<E extends OrderedQueueEntry<E,Q,L>, Q extends AbstractQueue<E,Q,L>, L extends OrderedQueueEntryList<E,Q,L>> implements QueueEntryIterator<E,Q,L,QueueConsumer<?,E,Q,L>>
    {
        private E _lastNode;

        QueueEntryIteratorImpl(E startNode)
        {
            _lastNode = startNode;
        }

        public boolean atTail()
        {
            return _lastNode.getNextValidEntry() == null;
        }

        public E getNode()
        {
            return _lastNode;
        }

        public boolean advance()
        {
            E nextValidNode = _lastNode.getNextValidEntry();

            if(nextValidNode != null)
            {
                _lastNode = nextValidNode;
            }

            return nextValidNode != null;
        }
    }

    public QueueEntryIterator<E,Q,L,QueueConsumer<?,E,Q,L>> iterator()
    {
        return new QueueEntryIteratorImpl<E,Q,L>(_head);
    }


    public E getHead()
    {
        return _head;
    }

    public void entryDeleted(E queueEntry)
    {
        E next = _head.getNextNode();
        E newNext = _head.getNextValidEntry();

        // the head of the queue has not been deleted, hence the deletion must have been mid queue.
        if (next == newNext)
        {
            E unscavengedHWM = _unscavengedHWM.get();
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
            E unscavengedHWM = _unscavengedHWM.get();
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


}
