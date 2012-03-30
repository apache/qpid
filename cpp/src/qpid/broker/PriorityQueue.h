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
#include <list>
#include <map>

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

    bool deleted(const QueuedMessage&);
    void release(const QueuedMessage&);
    bool acquire(const framing::SequenceNumber&, QueuedMessage&);
    bool find(const framing::SequenceNumber&, QueuedMessage&);
    bool browse(const framing::SequenceNumber&, QueuedMessage&, bool);
    virtual bool consume(QueuedMessage&);
    bool push(const QueuedMessage& added, QueuedMessage& removed);

    void foreach(Functor);
    void removeIf(Predicate);
    static uint getPriority(const QueuedMessage&);
  protected:
    typedef std::list<QueuedMessage*> Available;
    typedef std::map<framing::SequenceNumber, QueuedMessage> Index;

    const int levels;
    Index messages;
    Available available;

    bool compare(const QueuedMessage* a, const QueuedMessage* b) const;
    uint getPriorityLevel(const QueuedMessage& m) const;
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_PRIORITYQUEUE_H*/
