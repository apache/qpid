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

import org.apache.qpid.server.message.ServerMessage;

/**
 * An implementation of QueueEntryImpl to be used in SortedQueueEntryList.
 */
public class SortedQueueEntryImpl extends QueueEntryImpl
{
    public static enum Colour
    {
        RED, BLACK
    };

    private volatile SortedQueueEntryImpl _next;
    private SortedQueueEntryImpl _prev;
    private String _key;

    private Colour _colour = Colour.BLACK;
    private SortedQueueEntryImpl _parent;
    private SortedQueueEntryImpl _left;
    private SortedQueueEntryImpl _right;

    public SortedQueueEntryImpl(final SortedQueueEntryList queueEntryList)
    {
        super(queueEntryList);
    }

    public SortedQueueEntryImpl(final SortedQueueEntryList queueEntryList,
            final ServerMessage message, final long entryId)
    {
        super(queueEntryList, message, entryId);
    }

    @Override
    public int compareTo(final QueueEntry o)
    {
        final String otherKey = ((SortedQueueEntryImpl) o)._key;
        final int compare = _key == null ? (otherKey == null ? 0 : -1) : otherKey == null ? 1 : _key.compareTo(otherKey);
        return compare == 0 ? super.compareTo(o) : compare;
    }

    public Colour getColour()
    {
        return _colour;
    }

    public String getKey()
    {
        return _key;
    }

    public SortedQueueEntryImpl getLeft()
    {
        return _left;
    }

    public SortedQueueEntryImpl getNextNode()
    {
        return _next;
    }

    @Override
    public SortedQueueEntryImpl getNextValidEntry()
    {
        return getNextNode();
    }

    public SortedQueueEntryImpl getParent()
    {
        return _parent;
    }

    public SortedQueueEntryImpl getPrev()
    {
        return _prev;
    }

    public SortedQueueEntryImpl getRight()
    {
        return _right;
    }

    public void setColour(final Colour colour)
    {
        _colour = colour;
    }

    public void setKey(final String key)
    {
        _key = key;
    }

    public void setLeft(final SortedQueueEntryImpl left)
    {
        _left = left;
    }

    public void setNext(final SortedQueueEntryImpl next)
    {
        _next = next;
    }

    public void setParent(final SortedQueueEntryImpl parent)
    {
        _parent = parent;
    }

    public void setPrev(final SortedQueueEntryImpl prev)
    {
        _prev = prev;
    }

    public void setRight(final SortedQueueEntryImpl right)
    {
        _right = right;
    }

    @Override
    public String toString()
    {
        return "(" + (_colour == Colour.RED ? "Red," : "Black,") + _key + ")";
    }
}
