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

public class SimpleQueueEntryList implements QueueEntryList<SimpleQueueEntryImpl>
{

    private final SimpleQueueEntryImpl _head;

    private volatile SimpleQueueEntryImpl _tail;

    static final AtomicReferenceFieldUpdater<SimpleQueueEntryList, SimpleQueueEntryImpl>
            _tailUpdater =
        AtomicReferenceFieldUpdater.newUpdater
        (SimpleQueueEntryList.class, SimpleQueueEntryImpl.class, "_tail");


    private final AMQQueue _queue;

    static final AtomicReferenceFieldUpdater<SimpleQueueEntryImpl, SimpleQueueEntryImpl>
                _nextUpdater =
            AtomicReferenceFieldUpdater.newUpdater
            (SimpleQueueEntryImpl.class, SimpleQueueEntryImpl.class, "_next");

    private AtomicLong _scavenges = new AtomicLong(0L);
    private final long _scavengeCount = Integer.getInteger("qpid.queue.scavenge_count", 50);
    private final AtomicReference<SimpleQueueEntryImpl> _unscavengedHWM = new AtomicReference<SimpleQueueEntryImpl>();


    public SimpleQueueEntryList(AMQQueue queue)
    {
        _queue = queue;
        _head = new SimpleQueueEntryImpl(this);
        _tail = _head;
    }

    void scavenge()
    {
        SimpleQueueEntryImpl hwm = _unscavengedHWM.getAndSet(null);
        SimpleQueueEntryImpl next = _head.getNextValidEntry();

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


    public SimpleQueueEntryImpl add(ServerMessage message)
    {
        SimpleQueueEntryImpl node = createQueueEntry(message);
        for (;;)
        {
            SimpleQueueEntryImpl tail = _tail;
            SimpleQueueEntryImpl next = tail.getNextNode();
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

    protected SimpleQueueEntryImpl createQueueEntry(ServerMessage message)
    {
        return new SimpleQueueEntryImpl(this, message);
    }

    public SimpleQueueEntryImpl next(SimpleQueueEntryImpl node)
    {
        return node.getNextValidEntry();
    }

    public static class QueueEntryIteratorImpl implements QueueEntryIterator<SimpleQueueEntryImpl>
    {

        private SimpleQueueEntryImpl _lastNode;

        QueueEntryIteratorImpl(SimpleQueueEntryImpl startNode)
        {
            _lastNode = startNode;
        }


        public boolean atTail()
        {
            return _lastNode.getNextNode() == null;
        }

        public SimpleQueueEntryImpl getNode()
        {
            return _lastNode;
        }

        public boolean advance()
        {

            if(!atTail())
            {
                SimpleQueueEntryImpl nextNode = _lastNode.getNextNode();
                while(nextNode.isDispensed() && nextNode.getNextNode() != null)
                {
                    nextNode = nextNode.getNextNode();
                }
                _lastNode = nextNode;
                return true;

            }
            else
            {
                return false;
            }

        }

    }


    public QueueEntryIteratorImpl iterator()
    {
        return new QueueEntryIteratorImpl(_head);
    }


    public SimpleQueueEntryImpl getHead()
    {
        return _head;
    }

    public void entryDeleted(SimpleQueueEntryImpl queueEntry)
    {
        SimpleQueueEntryImpl next = _head.getNextNode();
        SimpleQueueEntryImpl newNext = _head.getNextValidEntry();

        // the head of the queue has not been deleted, hence the deletion must have been mid queue.
        if (next == newNext)
        {
            SimpleQueueEntryImpl unscavengedHWM = _unscavengedHWM.get();
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
    }

    public int getPriorities()
    {
        return 0;
    }

    static class Factory implements QueueEntryListFactory
    {

        public SimpleQueueEntryList createQueueEntryList(AMQQueue queue)
        {
            return new SimpleQueueEntryList(queue);
        }
    }
    

}
