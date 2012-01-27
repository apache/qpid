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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntry;

import java.util.HashMap;
import java.util.Map;

public class DefinedGroupMessageGroupManager implements MessageGroupManager
{
    private static final Logger _logger = LoggerFactory.getLogger(DefinedGroupMessageGroupManager.class);

    private final String _groupId;
    private final String _defaultGroup;
    private final Map<Object, Group> _groupMap = new HashMap<Object, Group>();
    private final SubscriptionResetHelper _resetHelper;

    private final class Group
    {
        private final Object _group;
        private Subscription _subscription;
        private int _activeCount;

        private Group(final Object key, final Subscription subscription)
        {
            _group = key;
            _subscription = subscription;
        }
        
        public boolean add()
        {
            if(_subscription != null)
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
                _resetHelper.resetSubPointersForGroups(_subscription, false);
                _subscription = null;
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
            return !(_subscription == null || (_activeCount == 0 && _subscription.isClosed()));
        }

        public Subscription getSubscription()
        {
            return _subscription;
        }

        @Override
        public String toString()
        {
            return "Group{" +
                    "_group=" + _group +
                    ", _subscription=" + _subscription +
                    ", _activeCount=" + _activeCount +
                    '}';
        }
    }

    public DefinedGroupMessageGroupManager(final String groupId, String defaultGroup, SubscriptionResetHelper resetHelper)
    {
        _groupId = groupId;
        _defaultGroup = defaultGroup;
        _resetHelper = resetHelper;
    }
    
    public synchronized Subscription getAssignedSubscription(final QueueEntry entry)
    {
        Object groupId = getKey(entry);

        Group group = _groupMap.get(groupId);
        return group == null || !group.isValid() ? null : group.getSubscription();
    }

    public synchronized boolean acceptMessage(final Subscription sub, final QueueEntry entry)
    {
        Object groupId = getKey(entry);
        Group group = _groupMap.get(groupId);
        
        if(group == null || !group.isValid())
        {
            group = new Group(groupId, sub);

            _groupMap.put(groupId, group);

            // there's a small change that the group became empty between the point at which getNextAvailable() was
            // called on the subscription, and when accept message is called... in that case we want to avoid delivering
            // out of order
            if(_resetHelper.isEntryAheadOfSubscription(entry, sub))
            {
                return false;
            }

        }
        
        Subscription assignedSub = group.getSubscription();
        
        if(assignedSub == sub)
        {
            entry.addStateChangeListener(new GroupStateChangeListener(group, entry));
            return true;
        }
        else
        {
            return false;            
        }
    }
    
    
    public synchronized QueueEntry findEarliestAssignedAvailableEntry(final Subscription sub)
    {
        EntryFinder visitor = new EntryFinder(sub);
        sub.getQueue().visit(visitor);
        return visitor.getEntry();
    }

    private class EntryFinder implements AMQQueue.Visitor
    {
        private QueueEntry _entry;
        private Subscription _sub;

        public EntryFinder(final Subscription sub)
        {
            _sub = sub;
        }

        public boolean visit(final QueueEntry entry)
        {
            if(!entry.isAvailable())
                return false;

            Object groupId = getKey(entry);

            Group group = _groupMap.get(groupId);
            if(group != null && group.getSubscription() == _sub)
            {
                _entry = entry;
                return true;
            }
            else
            {
                return false;
            }
        }

        public QueueEntry getEntry()
        {
            return _entry;
        }
    }

    
    public void clearAssignments(final Subscription sub)
    {
    }
    
    private Object getKey(QueueEntry entry)
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

    private class GroupStateChangeListener implements QueueEntry.StateChangeListener
    {
        private final Group _group;

        public GroupStateChangeListener(final Group group,
                                        final QueueEntry entry)
        {
            _group = group;
        }

        public void stateChanged(final QueueEntry entry,
                                 final QueueEntry.State oldState,
                                 final QueueEntry.State newState)
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
