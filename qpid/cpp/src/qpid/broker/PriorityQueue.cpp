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
    levels(l),
    messages(levels, Deque()),
    frontLevel(0), haveFront(false), cached(false) {}

size_t PriorityQueue::size()
{
    size_t total(0);
    for (int i = 0; i < levels; ++i) {
        total += messages[i].size();
    }
    return total;
}

bool PriorityQueue::empty()
{
    for (int i = 0; i < levels; ++i) {
        if (!messages[i].empty()) return false;
    }
    return true;
}

void PriorityQueue::reinsert(const QueuedMessage& message)
{
    uint p = getPriorityLevel(message);
    messages[p].insert(lower_bound(messages[p].begin(), messages[p].end(), message), message);
    clearCache();
}

bool PriorityQueue::find(const framing::SequenceNumber& position, QueuedMessage& message, bool remove)
{
    QueuedMessage comp;
    comp.position = position;
    for (int i = 0; i < levels; ++i) {
        if (!messages[i].empty()) {
            unsigned long diff = position.getValue() - messages[i].front().position.getValue();
            long maxEnd = diff < messages[i].size() ? diff : messages[i].size();        
            Deque::iterator l = lower_bound(messages[i].begin(),messages[i].begin()+maxEnd,comp);
            if (l != messages[i].end() && l->position == position) {
                message = *l;
                if (remove) {
                    messages[i].erase(l);
                    clearCache();
                }
                return true;
            }
        }
    }
    return false;
}

bool PriorityQueue::remove(const framing::SequenceNumber& position, QueuedMessage& message)
{
    return find(position, message, true);
}

bool PriorityQueue::find(const framing::SequenceNumber& position, QueuedMessage& message)
{
    return find(position, message, false);
}

bool PriorityQueue::next(const QueuedMessage& message, QueuedMessage& next)
{
    uint p = getPriorityLevel(message);
    QueuedMessage match;
    match.position = message.position+1;
    Deque::iterator m = lower_bound(messages[p].begin(), messages[p].end(), match);
    if (m != messages[p].end()) {
        next = *m;
        return true;
    }
    while (p-- > 0) {
        if (!messages[p].empty()) {
            next = messages[p].front();
            return true;
        }
    }
    return false;
}

bool PriorityQueue::next(const framing::SequenceNumber& position, QueuedMessage& message)
{
    QueuedMessage match;
    match.position = position+1;
    Deque::iterator lowest;
    bool found = false;
    for (int i = 0; i < levels; ++i) {
        Deque::iterator m = lower_bound(messages[i].begin(), messages[i].end(), match); 
        if (m != messages[i].end()) {
            if (m->position == match.position) {
                message = *m;
                return true;
            } else if (!found || m->position < lowest->position) {
                lowest = m;
                found = true;
            }
        }
    }
    if (found) {
        message = *lowest;
    }
    return found;
}

QueuedMessage& PriorityQueue::front()
{
    if (checkFront()) {
        return messages[frontLevel].front();
    } else {
        throw qpid::framing::InternalErrorException(QPID_MSG("No message available"));
    }
}

bool PriorityQueue::pop(QueuedMessage& message)
{
    if (checkFront()) {
        message = messages[frontLevel].front();
        messages[frontLevel].pop_front();
        clearCache();
        return true;
    } else {
        return false;
    }
}

void PriorityQueue::pop()
{
    QueuedMessage dummy;
    pop(dummy);
}

bool PriorityQueue::push(const QueuedMessage& added, QueuedMessage& /*not needed*/)
{
    messages[getPriorityLevel(added)].push_back(added);
    clearCache();
    return false;//adding a message never causes one to be removed for deque
}

void PriorityQueue::foreach(Functor f)
{
    for (int i = 0; i < levels; ++i) {
        std::for_each(messages[i].begin(), messages[i].end(), f);
    }
}

void PriorityQueue::removeIf(Predicate p)
{
    for (int priority = 0; priority < levels; ++priority) {
        for (Deque::iterator i = messages[priority].begin(); i != messages[priority].end();) {
            if (p(*i)) {
                i = messages[priority].erase(i);
                clearCache();
            } else {
                ++i;
            }
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

void PriorityQueue::clearCache()
{
    cached = false;
}

bool PriorityQueue::findFrontLevel(uint& l, PriorityLevels& m)
{
    for (int p = levels-1; p >= 0; --p) {
        if (!m[p].empty()) {
            l = p;
            return true;
        }
    }
    return false;
}

bool PriorityQueue::checkFront()
{
    if (!cached) {
        haveFront = findFrontLevel(frontLevel, messages);
        cached = true;
    }
    return haveFront;
}

uint PriorityQueue::getPriority(const QueuedMessage& message)
{
    const PriorityQueue* queue = dynamic_cast<const PriorityQueue*>(&(message.queue->getMessages()));
    if (queue) return queue->getPriorityLevel(message);
    else return 0;
}

}} // namespace qpid::broker
