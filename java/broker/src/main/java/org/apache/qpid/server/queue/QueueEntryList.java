package org.apache.qpid.server.queue;

import org.apache.qpid.server.store.StoreContext;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
public class QueueEntryList
{
    private final QueueEntryNode _head = new QueueEntryNode();

    private AtomicReference<QueueEntryNode> _tail = new AtomicReference<QueueEntryNode>(_head);
    private final AMQQueue _queue;



    public final class QueueEntryNode extends QueueEntryImpl
    {
        private final AtomicBoolean _deleted = new AtomicBoolean();
        private final AtomicReference<QueueEntryNode> _next = new AtomicReference<QueueEntryNode>();



        public QueueEntryNode()
        {
            super(null,null,Long.MIN_VALUE);
            _deleted.set(true);
        }

        public QueueEntryNode(AMQQueue queue, AMQMessage message)
        {
            super(queue,message);
        }

        public QueueEntryNode getNext()
        {

            QueueEntryNode next = nextNode();
            while(next != null && next.isDeleted())
            {

                final QueueEntryList.QueueEntryNode newNext = next.nextNode();
                if(newNext != null)
                {
                    _next.compareAndSet(next, newNext);
                    next = nextNode();
                }
                else
                {
                    next = null;
                }

            }
            return next;
        }

        private QueueEntryList.QueueEntryNode nextNode()
        {
            return _next.get();
        }

        public boolean isDeleted()
        {
            return _deleted.get();
        }


        public boolean delete()
        {
            if(_deleted.compareAndSet(false,true))
            {
                advanceHead();
                return true;
            }
            else
            {
                return false;
            }
        }


        public void dispose(final StoreContext storeContext) throws MessageCleanupException
        {
            super.dispose(storeContext);
            delete();
        }
    }


    public QueueEntryList(AMQQueue queue)
    {
        _queue = queue;
    }

    private void advanceHead()
    {
        QueueEntryNode head = _head.nextNode();
        while(head._next.get() != null && head.isDeleted())
        {

            final QueueEntryList.QueueEntryNode newhead = head.nextNode();
            if(newhead != null)
            {
                _head._next.compareAndSet(head, newhead);
            }
            head = _head.nextNode();
        }
    }


    public QueueEntry add(AMQMessage message)
    {
        QueueEntryNode node = new QueueEntryNode(_queue, message);
        for (;;)
        {
            QueueEntryNode tail = _tail.get();
            QueueEntryNode next = tail.nextNode();
            if (tail == _tail.get())
            {
                if (next == null)
                {
                    node.setEntryId(tail.getEntryId()+1);
                    if (tail._next.compareAndSet(null, node))
                    {
                        _tail.compareAndSet(tail, node);

                        return node;
                    }
                }
                else
                {
                    _tail.compareAndSet(tail, next);
                }
            }
        }
    }


    public class QueueEntryIterator
    {

        private QueueEntryNode _lastNode;

        QueueEntryIterator(QueueEntryNode startNode)
        {
            _lastNode = startNode;
        }


        public boolean atTail()
        {
            return _lastNode.nextNode() == null;
        }

        public QueueEntryNode getNode()
        {

            return _lastNode;

        }

        boolean advance()
        {

            if(!atTail())
            {
                QueueEntryNode nextNode = _lastNode.nextNode();
                while(nextNode.isDeleted() && nextNode.nextNode() != null)
                {
                    nextNode = nextNode.nextNode();
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


    public QueueEntryIterator iterator()
    {
        return new QueueEntryIterator(_head);
    }


    public QueueEntryNode getHead()
    {
        return _head;
    }


    

}
