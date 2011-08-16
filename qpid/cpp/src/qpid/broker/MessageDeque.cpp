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

namespace qpid {
namespace broker {

size_t MessageDeque::size()
{
    return messages.size();
}

bool MessageDeque::empty()
{
    return messages.empty();
}

void MessageDeque::reinsert(const QueuedMessage& message)
{
    messages.insert(lower_bound(messages.begin(), messages.end(), message), message);
}

MessageDeque::Deque::iterator MessageDeque::seek(const framing::SequenceNumber& position)
{
    if (!messages.empty()) {
        QueuedMessage comp;
        comp.position = position;
        unsigned long diff = position.getValue() - messages.front().position.getValue();
        long maxEnd = diff < messages.size()? diff : messages.size();        
        return lower_bound(messages.begin(),messages.begin()+maxEnd,comp);
    } else {
        return messages.end();
    }
}

bool MessageDeque::find(const framing::SequenceNumber& position, QueuedMessage& message, bool remove)
{
    Deque::iterator i = seek(position);
    if (i != messages.end() && i->position == position) {
        message = *i;
        if (remove) messages.erase(i);
        return true;
    } else {
        return false;
    }
}

bool MessageDeque::remove(const framing::SequenceNumber& position, QueuedMessage& message)
{
    return find(position, message, true);
}

bool MessageDeque::find(const framing::SequenceNumber& position, QueuedMessage& message)
{
    return find(position, message, false);
}

bool MessageDeque::next(const QueuedMessage& message, QueuedMessage& next)
{
    return this->next(message.position, next);
}

bool MessageDeque::next(const framing::SequenceNumber& position, QueuedMessage& message)
{
    if (messages.empty()) {
        return false;
    } else if (position < front().position) {
        message = front();
        return true;
    } else {
        Deque::iterator i = seek(position+1);
        if (i != messages.end()) {
            message = *i;
            return true;
        } else {
            return false;
        }
    }
}

QueuedMessage& MessageDeque::front()
{
    return messages.front();
}

void MessageDeque::pop()
{
    if (!messages.empty()) {
        messages.pop_front();
    }
}

bool MessageDeque::pop(QueuedMessage& out)
{
    if (messages.empty()) {
        return false;
    } else {
        out = front();
        messages.pop_front();
        return true;
    }
}

bool MessageDeque::push(const QueuedMessage& added, QueuedMessage& /*not needed*/)
{
    messages.push_back(added);
    return false;//adding a message never causes one to be removed for deque
}

void MessageDeque::foreach(Functor f)
{
    std::for_each(messages.begin(), messages.end(), f);
}

void MessageDeque::removeIf(Predicate p)
{
    for (Deque::iterator i = messages.begin(); i != messages.end();) {
        if (p(*i)) {
            i = messages.erase(i);
        } else {
            ++i;
        }
    }
}

}} // namespace qpid::broker
