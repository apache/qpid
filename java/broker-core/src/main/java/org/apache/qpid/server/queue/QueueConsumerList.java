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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class QueueConsumerList<E extends QueueEntryImpl<E,Q,L>, Q extends AbstractQueue<E, Q,L>, L extends QueueEntryListBase<E,Q,L>>
{
    private final ConsumerNode<E,Q,L> _head = new ConsumerNode<E,Q,L>();

    private final AtomicReference<ConsumerNode<E,Q,L>> _tail = new AtomicReference<ConsumerNode<E,Q,L>>(_head);
    private final AtomicReference<ConsumerNode<E,Q,L>> _subNodeMarker = new AtomicReference<ConsumerNode<E,Q,L>>(_head);
    private final AtomicInteger _size = new AtomicInteger();

    public static final class ConsumerNode<E extends QueueEntryImpl<E,Q,L>, Q extends AbstractQueue<E, Q,L>, L extends QueueEntryListBase<E,Q,L>>
    {
        private final AtomicBoolean _deleted = new AtomicBoolean();
        private final AtomicReference<ConsumerNode<E,Q,L>> _next = new AtomicReference<ConsumerNode<E,Q,L>>();
        private final QueueConsumer<?,E,Q,L> _sub;

        public ConsumerNode()
        {
            //used for sentinel head and dummy node construction
            _sub = null;
            _deleted.set(true);
        }

        public ConsumerNode(final QueueConsumer<?,E,Q,L> sub)
        {
            //used for regular node construction
            _sub = sub;
        }

        /**
         * Retrieves the first non-deleted node following the current node.
         * Any deleted non-tail nodes encountered during the search are unlinked.
         *
         * @return the next non-deleted node, or null if none was found.
         */
        public ConsumerNode<E,Q,L> findNext()
        {
            ConsumerNode<E,Q,L> next = nextNode();
            while(next != null && next.isDeleted())
            {
                final ConsumerNode<E,Q,L> newNext = next.nextNode();
                if(newNext != null)
                {
                    //try to move our _next reference forward to the 'newNext'
                    //node to unlink the deleted node
                    _next.compareAndSet(next, newNext);
                    next = nextNode();
                }
                else
                {
                    //'newNext' is null, meaning 'next' is the current tail. Can't unlink
                    //the tail node for thread safety reasons, just use the null.
                    next = null;
                }
            }

            return next;
        }

        /**
         * Gets the immediately next referenced node in the structure.
         *
         * @return the immediately next node in the structure, or null if at the tail.
         */
        protected ConsumerNode<E,Q,L> nextNode()
        {
            return _next.get();
        }

        /**
         * Used to initialise the 'next' reference. Will only succeed if the reference was not previously set.
         *
         * @param node the ConsumerNode to set as 'next'
         * @return whether the operation succeeded
         */
        private boolean setNext(final ConsumerNode<E,Q,L> node)
        {
            return _next.compareAndSet(null, node);
        }

        public boolean isDeleted()
        {
            return _deleted.get();
        }

        public boolean delete()
        {
            return _deleted.compareAndSet(false,true);
        }

        public QueueConsumer<?,E,Q,L> getConsumer()
        {
            return _sub;
        }
    }

    private void insert(final ConsumerNode<E,Q,L> node, final boolean count)
    {
        for (;;)
        {
            ConsumerNode<E,Q,L> tail = _tail.get();
            ConsumerNode<E,Q,L> next = tail.nextNode();
            if (tail == _tail.get())
            {
                if (next == null)
                {
                    if (tail.setNext(node))
                    {
                        _tail.compareAndSet(tail, node);
                        if(count)
                        {
                            _size.incrementAndGet();
                        }
                        return;
                    }
                }
                else
                {
                    _tail.compareAndSet(tail, next);
                }
            }
        }
    }

    public void add(final QueueConsumer<?,E,Q,L> sub)
    {
        ConsumerNode<E,Q,L> node = new ConsumerNode<E,Q,L>(sub);
        insert(node, true);
    }

    public boolean remove(final QueueConsumer<?, E,Q,L> sub)
    {
        ConsumerNode<E,Q,L> prevNode = _head;
        ConsumerNode<E,Q,L> node = _head.nextNode();

        while(node != null)
        {
            if(sub.equals(node.getConsumer()) && node.delete())
            {
                _size.decrementAndGet();

                ConsumerNode tail = _tail.get();
                if(node == tail)
                {
                    //we cant remove the last node from the structure for
                    //correctness reasons, however we have just 'deleted'
                    //the tail. Inserting an empty dummy node after it will
                    //let us scavenge the node containing the Consumer.
                    insert(new ConsumerNode<E,Q,L>(), false);
                }

                //advance the next node reference in the 'prevNode' to scavenge
                //the newly 'deleted' node for the Consumer.
                prevNode.findNext();

                nodeMarkerCleanup(node);

                return true;
            }

            prevNode = node;
            node = node.findNext();
        }

        return false;
    }

    private void nodeMarkerCleanup(final ConsumerNode<E,Q,L> node)
    {
        ConsumerNode<E,Q,L> markedNode = _subNodeMarker.get();
        if(node == markedNode)
        {
            //if the marked node is the one we are removing, then
            //replace it with a dummy pointing at the next node.
            //this is OK as the marked node is only used to index
            //into the list and find the next node to use.
            //Because we inserted a dummy if node was the
            //tail, markedNode.nextNode() can never be null.
            ConsumerNode<E,Q,L> dummy = new ConsumerNode<E,Q,L>();
            dummy.setNext(markedNode.nextNode());

            //if the CAS fails the marked node has changed, thus
            //we don't care about the dummy and just forget it
            _subNodeMarker.compareAndSet(markedNode, dummy);
        }
        else if(markedNode != null)
        {
            //if the marked node was already deleted then it could
            //hold subsequently removed nodes after it in the list 
            //in memory. Scavenge it to ensure their actual removal.
            if(markedNode != _head && markedNode.isDeleted())
            {
                markedNode.findNext();
            }
        }
    }

    public boolean updateMarkedNode(final ConsumerNode<E,Q,L> expected, final ConsumerNode<E,Q,L> nextNode)
    {
        return _subNodeMarker.compareAndSet(expected, nextNode);
    }

    /**
     * Get the current marked ConsumerNode. This should only be used only to index into the list and find the next node
     * after the mark, since if the previously marked node was subsequently deleted the item returned may be a dummy node
     * with reference to the next node.
     *
     * @return the previously marked node (or a dummy if it was subsequently deleted)
     */
    public ConsumerNode<E,Q,L> getMarkedNode()
    {
        return _subNodeMarker.get();
    }


    public static class ConsumerNodeIterator<E extends QueueEntryImpl<E,Q,L>, Q extends AbstractQueue<E, Q,L>, L extends QueueEntryListBase<E,Q,L>>
    {
        private ConsumerNode<E,Q,L> _lastNode;

        ConsumerNodeIterator(ConsumerNode<E,Q,L> startNode)
        {
            _lastNode = startNode;
        }

        public ConsumerNode<E,Q,L> getNode()
        {
            return _lastNode;
        }

        public boolean advance()
        {
            ConsumerNode<E,Q,L> nextNode = _lastNode.findNext();
            _lastNode = nextNode;

            return _lastNode != null;
        }
    }

    public ConsumerNodeIterator<E,Q,L> iterator()
    {
        return new ConsumerNodeIterator<E,Q,L>(_head);
    }

    public ConsumerNode<E,Q,L> getHead()
    {
        return _head;
    }

    public int size()
    {
        return _size.get();
    }
}



