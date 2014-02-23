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

import org.apache.qpid.server.consumer.Consumer;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.util.StateChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.ServerMessage;

import java.util.HashMap;
import java.util.Map;

public class DefinedGroupMessageGroupManager<E extends QueueEntryImpl<E,Q,L>, Q extends AbstractQueue<E,Q,L>, L extends QueueEntryListBase<E,Q,L>> implements MessageGroupManager<E,Q,L>
{
    private static final Logger _logger = LoggerFactory.getLogger(DefinedGroupMessageGroupManager.class);

    private final String _groupId;
    private final String _defaultGroup;
    private final Map<Object, Group> _groupMap = new HashMap<Object, Group>();
    private final ConsumerResetHelper<E,Q,L> _resetHelper;

    private final class Group
    {
        private final Object _group;
        private QueueConsumer<?,E,Q,L> _consumer;
        private int _activeCount;

        private Group(final Object key, final QueueConsumer<?,E,Q,L> consumer)
        {
            _group = key;
            _consumer = consumer;
        }
        
        public boolean add()
        {
            if(_consumer != null)
            {
                _activeCount++;
                return true;
            }
            else
            {
                return false;
            }
        }
        
        public void subtract()
        {
            if(--_activeCount == 0)
            {
                _resetHelper.resetSubPointersForGroups(_consumer, false);
                _consumer = null;
                _groupMap.remove(_group);
            }
        }

        @Override
        public boolean equals(final Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o == null || getClass() != o.getClass())
            {
                return false;
            }

            Group group = (Group) o;

            return _group.equals(group._group);
        }

        @Override
        public int hashCode()
        {
            return _group.hashCode();
        }

        public boolean isValid()
        {
            return !(_consumer == null || (_activeCount == 0 && _consumer.isClosed()));
        }

        public QueueConsumer<?,E,Q,L> getConsumer()
        {
            return _consumer;
        }

        @Override
        public String toString()
        {
            return "Group{" +
                    "_group=" + _group +
                    ", _consumer=" + _consumer +
                    ", _activeCount=" + _activeCount +
                    '}';
        }
    }

    public DefinedGroupMessageGroupManager(final String groupId, String defaultGroup, ConsumerResetHelper<E,Q,L> resetHelper)
    {
        _groupId = groupId;
        _defaultGroup = defaultGroup;
        _resetHelper = resetHelper;
    }
    
    public synchronized QueueConsumer<?,E,Q,L> getAssignedConsumer(final E entry)
    {
        Object groupId = getKey(entry);

        Group group = _groupMap.get(groupId);
        return group == null || !group.isValid() ? null : group.getConsumer();
    }

    public synchronized boolean acceptMessage(final QueueConsumer<?,E,Q,L> sub, final E entry)
    {
        return assignMessage(sub, entry) && entry.acquire(sub);
    }

    private boolean assignMessage(final QueueConsumer<?,E,Q,L> sub, final E entry)
    {
        Object groupId = getKey(entry);
        Group group = _groupMap.get(groupId);

        if(group == null || !group.isValid())
        {
            group = new Group(groupId, sub);

            _groupMap.put(groupId, group);

            // there's a small change that the group became empty between the point at which getNextAvailable() was
            // called on the consumer, and when accept message is called... in that case we want to avoid delivering
            // out of order
            if(_resetHelper.isEntryAheadOfConsumer(entry, sub))
            {
                return false;
            }
        }

        Consumer assignedSub = group.getConsumer();

        if(assignedSub == sub)
        {
            entry.addStateChangeListener(new GroupStateChangeListener(group));
            return true;
        }
        else
        {
            return false;            
        }
    }

    public synchronized E findEarliestAssignedAvailableEntry(final QueueConsumer<?,E,Q,L> sub)
    {
        EntryFinder visitor = new EntryFinder(sub);
        sub.getQueue().visit(visitor);
        return visitor.getEntry();
    }

    private class EntryFinder implements QueueEntryVisitor<E>
    {
        private E _entry;
        private QueueConsumer _sub;

        public EntryFinder(final QueueConsumer sub)
        {
            _sub = sub;
        }

        public boolean visit(final E entry)
        {
            if(!entry.isAvailable())
            {
                return false;
            }

            Object groupId = getKey(entry);

            Group group = _groupMap.get(groupId);
            if(group != null && group.getConsumer() == _sub)
            {
                _entry = entry;
                return true;
            }
            else
            {
                return false;
            }
        }

        public E getEntry()
        {
            return _entry;
        }
    }

    
    public void clearAssignments(final QueueConsumer sub)
    {
    }
    
    private Object getKey(E entry)
    {
        ServerMessage message = entry.getMessage();
        AMQMessageHeader messageHeader = message == null ? null : message.getMessageHeader();
        Object groupVal = messageHeader == null ? _defaultGroup : messageHeader.getHeader(_groupId);
        if(groupVal == null)
        {
            groupVal = _defaultGroup;
        }
        return groupVal;
    }

    private class GroupStateChangeListener implements StateChangeListener<MessageInstance<?, ? extends QueueConsumer>, QueueEntry.State>
    {
        private final Group _group;

        public GroupStateChangeListener(final Group group)
        {
            _group = group;
        }

        public void stateChanged(final MessageInstance<?, ? extends QueueConsumer> entry,
                                 final MessageInstance.State oldState,
                                 final MessageInstance.State newState)
        {
            synchronized (DefinedGroupMessageGroupManager.this)
            {
                if(_group.isValid())
                {
                    if(oldState != newState)
                    {
                        if(newState == QueueEntry.State.ACQUIRED)
                        {
                            _group.add();
                        }
                        else if(oldState == QueueEntry.State.ACQUIRED)
                        {
                            _group.subtract();
                        }
                    }
                }
                else
                {
                    entry.removeStateChangeListener(this);
                }
            }
        }
    }
}
