/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownersip.  The ASF licenses this file
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
#include "qpid/log/Statement.h"
#include <cmath>

namespace qpid {
namespace broker {

PriorityQueue::PriorityQueue(int l) :
    levels(l),
    messages(levels, Deque()),
    frontLevel(0), haveFront(false), cached(false) {}

bool PriorityQueue::deleted(const QueuedMessage& qm) {
    bool deleted = fifo.deleted(qm);
    if (deleted) erase(qm);
    return deleted;
}

size_t PriorityQueue::size()
{
    return fifo.size();
}

namespace {
bool before(QueuedMessage* a, QueuedMessage* b) { return *a < *b; }
}

void PriorityQueue::release(const QueuedMessage& message)
{
    QueuedMessage* qm = fifo.releasePtr(message);
    if (qm) {
        uint p = getPriorityLevel(message);
        messages[p].insert(
            lower_bound(messages[p].begin(), messages[p].end(), qm, before), qm);
        clearCache();
    }
}


void PriorityQueue::erase(const QueuedMessage& qm) {
    size_t i = getPriorityLevel(qm);
    if (!messages[i].empty()) {
        long diff = qm.position.getValue() - messages[i].front()->position.getValue();
        if (diff < 0) return;
        long maxEnd = std::min(size_t(diff), messages[i].size());
        QueuedMessage mutableQm = qm; // need non-const qm for lower_bound
        Deque::iterator l =
            lower_bound(messages[i].begin(),messages[i].begin()+maxEnd, &mutableQm, before);
        if (l != messages[i].end() && (*l)->position == qm.position) {
            messages[i].erase(l);
            clearCache();
            return;
        }
    }
}

bool PriorityQueue::acquire(const framing::SequenceNumber& position, QueuedMessage& message)
{
    bool acquired = fifo.acquire(position, message);
    if (acquired) erase(message); // No longer available
    return acquired;
}

bool PriorityQueue::find(const framing::SequenceNumber& position, QueuedMessage& message)
{
    return fifo.find(position, message);
}

bool PriorityQueue::browse(
    const framing::SequenceNumber& position, QueuedMessage& message, bool unacquired)
{
    return fifo.browse(position, message, unacquired);
}

bool PriorityQueue::consume(QueuedMessage& message)
{
    if (checkFront()) {
        QueuedMessage* pm = messages[frontLevel].front();
        messages[frontLevel].pop_front();
        clearCache();
        pm->status = QueuedMessage::ACQUIRED; // Updates FIFO index
        message = *pm;
        return true;
    } else {
        return false;
    }
}

bool PriorityQueue::push(const QueuedMessage& added, QueuedMessage& /*not needed*/)
{
    QueuedMessage* qmp = fifo.pushPtr(added);
    messages[getPriorityLevel(added)].push_back(qmp);
    clearCache();
    return false; // Adding a message never causes one to be removed for deque
}

void PriorityQueue::updateAcquired(const QueuedMessage& acquired) {
    fifo.updateAcquired(acquired);
}

void PriorityQueue::foreach(Functor f)
{
    fifo.foreach(f);
}

void PriorityQueue::removeIf(Predicate p)
{
    for (int priority = 0; priority < levels; ++priority) {
        for (Deque::iterator i = messages[priority].begin(); i != messages[priority].end();) {
            if (p(**i)) {
                (*i)->status = QueuedMessage::DELETED; // Updates fifo index
                i = messages[priority].erase(i);
                clearCache();
            } else {
                ++i;
            }
        }
    }
    fifo.clean();
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
