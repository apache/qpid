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

import org.apache.qpid.framing.CommonContentHeaderProperties;

public class PriorityQueueEntryList extends FlowableBaseQueueEntryList implements QueueEntryList
{
    private final AMQQueue _queue;
    private final QueueEntryList[] _priorityLists;
    private final int _priorities;
    private final int _priorityOffset;

    public PriorityQueueEntryList(AMQQueue queue, int priorities)
    {
        super(queue);
        _queue = queue;
        _priorityLists = new QueueEntryList[priorities];
        _priorities = priorities;
        _priorityOffset = 5 - ((priorities + 1) / 2);
        for (int i = 0; i < priorities; i++)
        {
            _priorityLists[i] = new SimpleQueueEntryList(queue);
        }

        showUsage("Created:" + _queue.getName());
    }

    public int getPriorities()
    {
        return _priorities;
    }

    public AMQQueue getQueue()
    {
        return _queue;
    }

    public QueueEntry add(AMQMessage message)
    {
        int index = ((CommonContentHeaderProperties) ((message.getContentHeaderBody().properties))).getPriority() - _priorityOffset;
        if (index >= _priorities)
        {
            index = _priorities - 1;
        }
        else if (index < 0)
        {
            index = 0;
        }

        long requriedSize = message.getSize();
        // Check and see if list would flow on adding message
        if (!_disableFlowToDisk && !isFlowed() && _priorityLists[index].memoryUsed() + requriedSize > _priorityLists[index].getMemoryUsageMaximum())
        {
            if (_log.isDebugEnabled())
            {
                _log.debug("Message(" + message.debugIdentity() + ") Add of size ("
                              + requriedSize + ") will cause flow. Searching for space");
            }

            long reclaimed = 0;

            //work down the priorities looking for memory

            //First: Don't take all the memory. So look for a queue that has more than 50% free
            long currentMax;
            int scavangeIndex = 0;

            if (scavangeIndex == index)
            {
                scavangeIndex++;
            }

            while (scavangeIndex < _priorities  && reclaimed <= requriedSize)
            {
                currentMax = _priorityLists[scavangeIndex].getMemoryUsageMaximum();
                long used = _priorityLists[scavangeIndex].memoryUsed();

                if (used < currentMax / 2)
                {
                    long newMax = currentMax / 2;

                    _priorityLists[scavangeIndex].setMemoryUsageMaximum(newMax);

                    reclaimed += currentMax - newMax;
                    if (_log.isDebugEnabled())
                    {
                        _log.debug("Reclaiming(1) :" + (currentMax - newMax) + "(" + reclaimed + "/" + requriedSize + ") from queue:" + scavangeIndex);
                    }
                    break;
                }
                else
                {
                    scavangeIndex++;
                    if (scavangeIndex == index)
                    {
                        scavangeIndex++;
                    }
                }
            }                                   

            //Second: Just take the free memory we need
            if (scavangeIndex == _priorities)
            {
                scavangeIndex = 0;
                if (scavangeIndex == index)
                {
                    scavangeIndex++;
                }

                while (scavangeIndex < _priorities && reclaimed <= requriedSize)
                {
                    currentMax = _priorityLists[scavangeIndex].getMemoryUsageMaximum();
                    long used = _priorityLists[scavangeIndex].memoryUsed();

                    if (used < currentMax)
                    {
                        long newMax = currentMax - used;

                        // if there are no messages at this priority just take it all
                        if (newMax == currentMax)
                        {
                            newMax = 0;
                        }

                        _priorityLists[scavangeIndex].setMemoryUsageMaximum(newMax);

                        reclaimed += currentMax - newMax;
                        if (_log.isDebugEnabled())
                        {
                            _log.debug("Reclaiming(2) :" + (currentMax - newMax) + "(" + reclaimed + "/" + requriedSize + ") from queue:" + scavangeIndex);
                        }
                        break;
                    }
                    else
                    {
                        scavangeIndex++;
                        if (scavangeIndex == index)
                        {
                            scavangeIndex++;
                        }
                    }
                }

                //Third: Take required memory
                if (scavangeIndex == _priorities)
                {
                    scavangeIndex = 0;
                    if (scavangeIndex == index)
                    {
                        scavangeIndex++;
                    }
                    while (scavangeIndex < _priorities && reclaimed <= requriedSize)
                    {
                        currentMax = _priorityLists[scavangeIndex].getMemoryUsageMaximum();

                        if (currentMax > 0 )
                        {
                            long newMax = currentMax;
                            // Just take the amount of space required for this message.
                            if (newMax > requriedSize)
                            {
                                newMax = requriedSize;
                            }
                            _priorityLists[scavangeIndex].setMemoryUsageMaximum(newMax);

                            reclaimed += currentMax - newMax;
                            if (_log.isDebugEnabled())
                            {
                                _log.debug("Reclaiming(3) :" + (currentMax - newMax) + "(" + reclaimed + "/" + requriedSize + ") from queue:" + scavangeIndex);
                            }
                            break;
                        }
                        else
                        {
                            scavangeIndex++;
                            if (scavangeIndex == index)
                            {
                                scavangeIndex++;
                            }
                        }
                    }
                }
            }

            //Increment Maximum
            if (reclaimed > 0)
            {
                if (_log.isDebugEnabled())
                {
                    _log.debug("Increasing queue(" + index + ") maximum by " + reclaimed
                               + " to " + (_priorityLists[index].getMemoryUsageMaximum() + reclaimed));
                }
                _priorityLists[index].setMemoryUsageMaximum(_priorityLists[index].getMemoryUsageMaximum() + reclaimed);
            }
            else
            {
                _log.debug("No space found.");
            }

            if (_log.isTraceEnabled())
            {
                showUsage("Add");
            }
        }

        return _priorityLists[index].add(message);
    }

    @Override
    protected void showUsage(String prefix)
    {
        if (_log.isDebugEnabled())
        {
            if (prefix.length() != 0)
            {
                _log.debug(prefix);
            }
            for (int index = 0; index < _priorities; index++)
            {
                QueueEntryList queueEntryList = _priorityLists[index];
                _log.debug("Queue (" + _queue.getName() + ")[" + index + "] usage:" + queueEntryList.memoryUsed()
                           + "/" + queueEntryList.getMemoryUsageMaximum()
                           + "/" + queueEntryList.dataSize());
            }
        }
    }

    public QueueEntry next(QueueEntry node)
    {
        QueueEntryImpl nodeImpl = (QueueEntryImpl) node;
        QueueEntry next = nodeImpl.getNext();

        if (next == null)
        {
            QueueEntryList nodeEntryList = nodeImpl.getQueueEntryList();
            int index;
            for (index = _priorityLists.length - 1; _priorityLists[index] != nodeEntryList; index--)
            {
                ;
            }

            while (next == null && index != 0)
            {
                index--;
                next = ((QueueEntryImpl) _priorityLists[index].getHead()).getNext();
            }

        }
        return next;
    }

    private final class PriorityQueueEntryListIterator implements QueueEntryIterator
    {
        private final QueueEntryIterator[] _iterators = new QueueEntryIterator[_priorityLists.length];
        private QueueEntry _lastNode;

        PriorityQueueEntryListIterator()
        {
            for (int i = 0; i < _priorityLists.length; i++)
            {
                _iterators[i] = _priorityLists[i].iterator();
            }
            _lastNode = _iterators[_iterators.length - 1].getNode();
        }

        public boolean atTail()
        {
            for (int i = 0; i < _iterators.length; i++)
            {
                if (!_iterators[i].atTail())
                {
                    return false;
                }
            }
            return true;
        }

        public QueueEntry getNode()
        {
            return _lastNode;
        }

        public boolean advance()
        {
            for (int i = _iterators.length - 1; i >= 0; i--)
            {
                if (_iterators[i].advance())
                {
                    _lastNode = _iterators[i].getNode();
                    return true;
                }
            }
            return false;
        }
    }

    public QueueEntryIterator iterator()
    {
        return new PriorityQueueEntryListIterator();
    }

    public QueueEntry getHead()
    {
        return _priorityLists[_priorities - 1].getHead();
    }

    static class Factory implements QueueEntryListFactory
    {
        private final int _priorities;

        Factory(int priorities)
        {
            _priorities = priorities;
        }

        public QueueEntryList createQueueEntryList(AMQQueue queue)
        {
            return new PriorityQueueEntryList(queue, _priorities);
        }
    }

    @Override
    public boolean isFlowed()
    {
        boolean flowed = false;
        boolean full = true;

        if (_log.isTraceEnabled())
        {
            showUsage("isFlowed");
        }

        for (QueueEntryList queueEntryList : _priorityLists)
        {
            //full = full && queueEntryList.getMemoryUsageMaximum() == queueEntryList.memoryUsed();
            full = full && queueEntryList.getMemoryUsageMaximum() <= queueEntryList.dataSize();
            flowed = flowed || (queueEntryList.isFlowed());
        }
        return flowed && full;
    }

    @Override
    public int size()
    {
        int size = 0;
        for (QueueEntryList queueEntryList : _priorityLists)
        {
            size += queueEntryList.size();
        }

        return size;
    }

    @Override
    public long dataSize()
    {
        int dataSize = 0;
        for (QueueEntryList queueEntryList : _priorityLists)
        {
            dataSize += queueEntryList.dataSize();
        }

        return dataSize;
    }

    @Override
    public long memoryUsed()
    {
        int memoryUsed = 0;
        for (QueueEntryList queueEntryList : _priorityLists)
        {
            memoryUsed += queueEntryList.memoryUsed();
        }

        return memoryUsed;
    }

    @Override
    public void setMemoryUsageMaximum(long maximumMemoryUsage)
    {
        _memoryUsageMaximum = maximumMemoryUsage;

        if (maximumMemoryUsage >= 0)
        {
            _disableFlowToDisk = false;
        }

        long share = maximumMemoryUsage / _priorities;

        //Apply a share of the maximum To each prioirty quue
        for (QueueEntryList queueEntryList : _priorityLists)
        {
            queueEntryList.setMemoryUsageMaximum(share);
        }

        if (maximumMemoryUsage < 0)
        {
            if (_log.isInfoEnabled())
            {
                _log.info("Disabling Flow to Disk for queue:" + _queue.getName());
            }
            _disableFlowToDisk = true;
            return;
        }

        //ensure we use the full allocation of memory
        long remainder = maximumMemoryUsage - (share * _priorities);
        if (remainder > 0)
        {
            _priorityLists[_priorities - 1].setMemoryUsageMaximum(share + remainder);
        }
    }

    @Override
    public long getMemoryUsageMaximum()
    {
        return _memoryUsageMaximum;
    }

    @Override
    public void setMemoryUsageMinimum(long minimumMemoryUsage)
    {
        _memoryUsageMinimum = minimumMemoryUsage;

        //Apply a share of the minimum To each prioirty quue
        for (QueueEntryList queueEntryList : _priorityLists)
        {
            queueEntryList.setMemoryUsageMaximum(minimumMemoryUsage / _priorities);
        }
    }

    @Override
    public long getMemoryUsageMinimum()
    {
        return _memoryUsageMinimum;
    }

    @Override
    public void stop()
    {
        super.stop();
        for (QueueEntryList queueEntryList : _priorityLists)
        {
            queueEntryList.stop();
        }
    }
}
