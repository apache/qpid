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
#include "qpid/broker/MessageDeque.h"
#include "qpid/broker/QueuedMessage.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {

MessageDeque::MessageDeque() : available(0), head(0) {}

size_t MessageDeque::index(const framing::SequenceNumber& position)
{
    //assuming a monotonic sequence, with no messages removed except
    //from the ends of the deque, we can use the position to determin
    //an index into the deque
    if (messages.empty() || position < messages.front().position) return 0;
    return position - messages.front().position;
}

bool MessageDeque::deleted(const QueuedMessage& m)
{
    size_t i = index(m.position);
    if (i < messages.size()) {
        messages[i].status = QueuedMessage::DELETED;
        clean();
        return true;
    } else {
        return false;
    }
}

size_t MessageDeque::size()
{
    return available;
}

void MessageDeque::release(const QueuedMessage& message)
{
    size_t i = index(message.position);
    if (i < messages.size()) {
        QueuedMessage& m = messages[i];
        if (m.status == QueuedMessage::ACQUIRED) {
            if (head > i) head = i;
            m.status = QueuedMessage::AVAILABLE;
            ++available;
        }
    } else {
        QPID_LOG(error, "Failed to release message at " << message.position << " " << message.payload->getFrames().getContent() << "; no such message (index=" << i << ", size=" << messages.size() << ")");
    }
}

bool MessageDeque::acquire(const framing::SequenceNumber& position, QueuedMessage& message)
{
    size_t i = index(position);
    if (i < messages.size()) {
        QueuedMessage& temp = messages[i];
        if (temp.status == QueuedMessage::AVAILABLE) {
            temp.status = QueuedMessage::ACQUIRED;
            --available;
            message = temp;
            return true;
        }
    }
    return false;
}

bool MessageDeque::find(const framing::SequenceNumber& position, QueuedMessage& message)
{
    size_t i = index(position);
    if (i < messages.size()) {
        message = messages[i];
        return true;
    } else {
        return false;
    }
}

bool MessageDeque::browse(const framing::SequenceNumber& position, QueuedMessage& message, bool unacquired)
{
    //get first message that is greater than position
    size_t i = index(position + 1);
    while (i < messages.size()) {
        QueuedMessage& m = messages[i++];
        if (m.status == QueuedMessage::AVAILABLE || (!unacquired && m.status == QueuedMessage::ACQUIRED)) {
            message = m;
            return true;
        }
    }
    return false;
}

bool MessageDeque::consume(QueuedMessage& message)
{
    while (head < messages.size()) {
        QueuedMessage& i = messages[head++];
        if (i.status == QueuedMessage::AVAILABLE) {
            i.status = QueuedMessage::ACQUIRED;
            --available;
            message = i;
            return true;
        }
    }
    return false;
}

bool MessageDeque::push(const QueuedMessage& added, QueuedMessage& /*not needed*/)
{
    //add padding to prevent gaps in sequence, which break the index
    //calculation (needed for queue replication)
    while (messages.size() && (added.position - messages.back().position) > 1) {
        QueuedMessage dummy;
        dummy.position = messages.back().position + 1;
        dummy.status = QueuedMessage::DELETED;
        messages.push_back(dummy);
        QPID_LOG(debug, "Adding padding at " << dummy.position << ", between " << messages.back().position << " and " << added.position);
    }
    messages.push_back(added);
    messages.back().status = QueuedMessage::AVAILABLE;
    if (head >= messages.size()) head = messages.size() - 1;
    ++available;
    return false;//adding a message never causes one to be removed for deque
}

void MessageDeque::clean()
{
    while (messages.size() && messages.front().status == QueuedMessage::DELETED) {
        messages.pop_front();
        if (head) --head;
    }
}

void MessageDeque::foreach(Functor f)
{
    for (Deque::iterator i = messages.begin(); i != messages.end(); ++i) {
        if (i->status == QueuedMessage::AVAILABLE) {
            f(*i);
        }
    }
}

void MessageDeque::removeIf(Predicate p)
{
    for (Deque::iterator i = messages.begin(); i != messages.end(); ++i) {
        if (i->status == QueuedMessage::AVAILABLE && p(*i)) {
            //Use special status for this as messages are not yet
            //dequeued, but should not be considered on the queue
            //either (used for purging and moving)
            i->status = QueuedMessage::REMOVED;
            --available;
        }
    }
    clean();
}

}} // namespace qpid::broker
