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
package org.apache.qpid.server.subscription;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SubscriptionList
{
    private final SubscriptionNode _head = new SubscriptionNode();

    private final AtomicReference<SubscriptionNode> _tail = new AtomicReference<SubscriptionNode>(_head);
    private final AtomicReference<SubscriptionNode> _subNodeMarker = new AtomicReference<SubscriptionNode>(_head);
    private final AtomicInteger _size = new AtomicInteger();

    public static final class SubscriptionNode
    {
        private final AtomicBoolean _deleted = new AtomicBoolean();
        private final AtomicReference<SubscriptionNode> _next = new AtomicReference<SubscriptionNode>();
        private final Subscription _sub;

        public SubscriptionNode()
        {
            //used for sentinel head and dummy node construction
            _sub = null;
            _deleted.set(true);
        }

        public SubscriptionNode(final Subscription sub)
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
        public SubscriptionNode findNext()
        {
            SubscriptionNode next = nextNode();
            while(next != null && next.isDeleted())
            {
                final SubscriptionNode newNext = next.nextNode();
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
        protected SubscriptionNode nextNode()
        {
            return _next.get();
        }

        /**
         * Used to initialise the 'next' reference. Will only succeed if the reference was not previously set.
         *
         * @param node the SubscriptionNode to set as 'next'
         * @return whether the operation succeeded
         */
        private boolean setNext(final SubscriptionNode node)
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

        public Subscription getSubscription()
        {
            return _sub;
        }
    }

    private void insert(final SubscriptionNode node, final boolean count)
    {
        for (;;)
        {
            SubscriptionNode tail = _tail.get();
            SubscriptionNode next = tail.nextNode();
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

    public void add(final Subscription sub)
    {
        SubscriptionNode node = new SubscriptionNode(sub);
        insert(node, true);
    }

    public boolean remove(final Subscription sub)
    {
        SubscriptionNode prevNode = _head;
        SubscriptionNode node = _head.nextNode();

        while(node != null)
        {
            if(sub.equals(node.getSubscription()) && node.delete())
            {
                _size.decrementAndGet();

                SubscriptionNode tail = _tail.get();
                if(node == tail)
                {
                    //we cant remove the last node from the structure for
                    //correctness reasons, however we have just 'deleted'
                    //the tail. Inserting an empty dummy node after it will
                    //let us scavenge the node containing the Subscription.
                    insert(new SubscriptionNode(), false);
                }

                //advance the next node reference in the 'prevNode' to scavange
                //the newly 'deleted' node for the Subscription.
                prevNode.findNext();

                nodeMarkerCleanup(node);

                return true;
            }

            prevNode = node;
            node = node.findNext();
        }

        return false;
    }

    private void nodeMarkerCleanup(final SubscriptionNode node)
    {
        SubscriptionNode markedNode = _subNodeMarker.get();
        if(node == markedNode)
        {
            //if the marked node is the one we are removing, then
            //replace it with a dummy pointing at the next node.
            //this is OK as the marked node is only used to index
            //into the list and find the next node to use.
            //Because we inserted a dummy if node was the
            //tail, markedNode.nextNode() can never be null.
            SubscriptionNode dummy = new SubscriptionNode();
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

    public boolean updateMarkedNode(final SubscriptionNode expected, final SubscriptionNode nextNode)
    {
        return _subNodeMarker.compareAndSet(expected, nextNode);
    }

    /**
     * Get the current marked SubscriptionNode. This should only be used only to index into the list and find the next node
     * after the mark, since if the previously marked node was subsequently deleted the item returned may be a dummy node
     * with reference to the next node.
     *
     * @return the previously marked node (or a dummy if it was subsequently deleted)
     */
    public SubscriptionNode getMarkedNode()
    {
        return _subNodeMarker.get();
    }


    public static class SubscriptionNodeIterator
    {
        private SubscriptionNode _lastNode;

        SubscriptionNodeIterator(SubscriptionNode startNode)
        {
            _lastNode = startNode;
        }

        public SubscriptionNode getNode()
        {
            return _lastNode;
        }

        public boolean advance()
        {
            SubscriptionNode nextNode = _lastNode.findNext();
            _lastNode = nextNode;

            return _lastNode != null;
        }
    }

    public SubscriptionNodeIterator iterator()
    {
        return new SubscriptionNodeIterator(_head);
    }

    public SubscriptionNode getHead()
    {
        return _head;
    }

    public int size()
    {
        return _size.get();
    }
}



