#ifndef QPID_BROKER_PRIORITYQUEUE_H
#define QPID_BROKER_PRIORITYQUEUE_H

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
#include "qpid/broker/Messages.h"
#include "qpid/sys/IntegerTypes.h"
#include <deque>
#include <vector>

namespace qpid {
namespace broker {

/**
 * Basic priority queue with a configurable number of recognised
 * priority levels. This is implemented as a separate deque per
 * priority level. Browsing is FIFO not priority order.
 */
class PriorityQueue : public Messages
{
  public:
    PriorityQueue(int levels);
    virtual ~PriorityQueue() {}
    size_t size();
    bool empty();

    void reinsert(const QueuedMessage&);
    bool remove(const framing::SequenceNumber&, QueuedMessage&);
    bool find(const framing::SequenceNumber&, QueuedMessage&);
    bool next(const framing::SequenceNumber&, QueuedMessage&);
    bool next(const QueuedMessage&, QueuedMessage&);

    QueuedMessage& front();
    void pop();
    bool pop(QueuedMessage&);
    bool push(const QueuedMessage& added, QueuedMessage& removed);

    void foreach(Functor);
    void removeIf(Predicate);
    static uint getPriority(const QueuedMessage&);
  protected:
    typedef std::deque<QueuedMessage> Deque;
    typedef std::vector<Deque> PriorityLevels;
    virtual bool findFrontLevel(uint& p, PriorityLevels&);

    const int levels;
  private:
    PriorityLevels messages;
    uint frontLevel;
    bool haveFront;
    bool cached;
    
    bool find(const framing::SequenceNumber&, QueuedMessage&, bool remove);
    uint getPriorityLevel(const QueuedMessage&) const;
    void clearCache();
    bool checkFront();
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_PRIORITYQUEUE_H*/
