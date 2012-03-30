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
#include "qpid/broker/PriorityQueue.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueuedMessage.h"
#include "qpid/framing/reply_exceptions.h"
#include <cmath>

namespace qpid {
namespace broker {

PriorityQueue::PriorityQueue(int l) : 
    levels(l) {}

bool PriorityQueue::deleted(const QueuedMessage& message)
{
    Index::iterator i = messages.find(message.position);
    if (i != messages.end()) {
        //remove from available list if necessary
        if (i->second.status == QueuedMessage::AVAILABLE) {
            Available::iterator j = std::find(available.begin(), available.end(), &i->second);
            if (j != available.end()) available.erase(j);
        }
        //remove from messages map
        messages.erase(i);
        return true;
    } else {
        return false;
    }
}

size_t PriorityQueue::size()
{
    return available.size();
}

void PriorityQueue::release(const QueuedMessage& message)
{
    Index::iterator i = messages.find(message.position);
    if (i != messages.end() && i->second.status == QueuedMessage::ACQUIRED) {
        i->second.status = QueuedMessage::AVAILABLE;
        //insert message back into the correct place in available queue, based on priority:
        Available::iterator j = upper_bound(available.begin(), available.end(), &i->second, boost::bind(&PriorityQueue::compare, this, _1, _2));
        available.insert(j, &i->second);
    }
}

bool PriorityQueue::acquire(const framing::SequenceNumber& position, QueuedMessage& message)
{
    Index::iterator i = messages.find(position);
    if (i != messages.end() && i->second.status == QueuedMessage::AVAILABLE) {
        i->second.status = QueuedMessage::ACQUIRED;
        message = i->second;
        //remove it from available list (could make this faster by using ordering):
        Available::iterator j = std::find(available.begin(), available.end(), &i->second);
        assert(j != available.end());
        available.erase(j);
        return true;
    } else {
        return false;
    }
}

bool PriorityQueue::find(const framing::SequenceNumber& position, QueuedMessage& message)
{
    Index::iterator i = messages.find(position);
    if (i != messages.end() && i->second.status == QueuedMessage::AVAILABLE) {
        message = i->second;
        return true;
    } else {
        return false;
    }
}

bool PriorityQueue::browse(const framing::SequenceNumber& position, QueuedMessage& message, bool unacquired)
{
    Index::iterator i = messages.lower_bound(position+1);
    if (i != messages.end() && (i->second.status == QueuedMessage::AVAILABLE  || (!unacquired && i->second.status == QueuedMessage::ACQUIRED))) {
        message = i->second;
        return true;
    } else {
        return false;
    }
}

bool PriorityQueue::consume(QueuedMessage& message)
{
    if (!available.empty()) {
        QueuedMessage* next = available.front();
        messages[next->position].status = QueuedMessage::ACQUIRED;
        message = *next;
        available.pop_front();
        return true;
    } else {
        return false;
    }
}

bool PriorityQueue::compare(const QueuedMessage* a, const QueuedMessage* b) const
{
    int priorityA = getPriorityLevel(*a);
    int priorityB = getPriorityLevel(*b);
    if (priorityA == priorityB) return a->position < b->position;
    else return priorityA > priorityB;
}

bool PriorityQueue::push(const QueuedMessage& added, QueuedMessage& /*not needed*/)
{
    Index::iterator i = messages.insert(Index::value_type(added.position, added)).first;
    i->second.status = QueuedMessage::AVAILABLE;
    //insert message into the correct place in available queue, based on priority:
    Available::iterator j = upper_bound(available.begin(), available.end(), &i->second, boost::bind(&PriorityQueue::compare, this, _1, _2));
    available.insert(j, &i->second);
    return false;//adding a message never causes one to be removed for deque
}

void PriorityQueue::foreach(Functor f)
{
    for (Available::iterator i = available.begin(); i != available.end(); ++i) {
        f(**i);
    }
}

void PriorityQueue::removeIf(Predicate p)
{
    for (Available::iterator i = available.begin(); i != available.end();) {
        if (p(**i)) {
            messages[(*i)->position].status = QueuedMessage::REMOVED;
            i = available.erase(i);
        } else {
            ++i;
        }
    }
}

uint PriorityQueue::getPriorityLevel(const QueuedMessage& m) const
{
    uint priority = m.payload->getPriority();
    //Use AMQP 0-10 approach to mapping priorities to a fixed level
    //(see rule priority-level-implementation)
    const uint firstLevel = 5 - uint(std::min(5.0, std::ceil((double) levels/2.0)));
    if (priority <= firstLevel) return 0;
    return std::min(priority - firstLevel, (uint)levels-1);
}


uint PriorityQueue::getPriority(const QueuedMessage& message)
{
    const PriorityQueue* queue = dynamic_cast<const PriorityQueue*>(&(message.queue->getMessages()));
    if (queue) return queue->getPriorityLevel(message);
    else return 0;
}

}} // namespace qpid::broker
