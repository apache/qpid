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
#include "qpid/broker/MessageMap.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/QueueCursor.h"
#include "qpid/log/Statement.h"
#include <algorithm>

namespace qpid {
namespace broker {
namespace {
const std::string EMPTY;
}


std::string MessageMap::getKey(const Message& message)
{
    return message.getPropertyAsString(key);
}

size_t MessageMap::size()
{
    size_t count(0);
    for (Ordering::iterator i = messages.begin(); i != messages.end(); ++i) {
        if (i->second.getState() == AVAILABLE) ++count;
    }
    return count;
}

bool MessageMap::empty()
{
    return size() == 0;//TODO: more efficient implementation
}

bool MessageMap::deleted(const QueueCursor& cursor)
{
    Ordering::iterator i = messages.find(cursor.position);
    if (i != messages.end()) {
        erase(i);
        return true;
    } else {
        return false;
    }
}

Message* MessageMap::find(const QueueCursor& cursor)
{
    if (cursor.valid) return find(cursor.position, 0);
    else return 0;
}

Message* MessageMap::find(const framing::SequenceNumber& position, QueueCursor* cursor)
{
    Ordering::iterator i = messages.lower_bound(position);
    if (i != messages.end()) {
        if (cursor) cursor->setPosition(i->first, version);
        if (i->first == position) return &(i->second);
        else return 0;
    } else {
        //there is no message whose sequence is greater than position,
        //i.e. haven't got there yet
        if (cursor) cursor->setPosition(position, version);
        return 0;
    }
}

Message* MessageMap::next(QueueCursor& cursor)
{
    Ordering::iterator i;
    if (!cursor.valid) i = messages.begin(); //start with oldest message
    else i = messages.upper_bound(cursor.position); //get first message that is greater than position

    while (i != messages.end()) {
        Message& m = i->second;
        cursor.setPosition(m.getSequence(), version);
        if (cursor.check(m)) {
            return &m;
        } else {
            ++i;
        }
    }
    return 0;
}

const Message& MessageMap::replace(const Message& original, const Message& update)
{
    messages.erase(original.getSequence());
    std::pair<Ordering::iterator, bool> i = messages.insert(Ordering::value_type(update.getSequence(), update));
    i.first->second.setState(AVAILABLE);
    return i.first->second;
}

void MessageMap::publish(const Message& added)
{
    Message dummy;
    update(added, dummy);
}

bool MessageMap::update(const Message& added, Message& removed)
{
    std::pair<Index::iterator, bool> result = index.insert(Index::value_type(getKey(added), added));
    if (result.second) {
        //there was no previous message for this key; nothing needs to
        //be removed, just add the message into its correct position
        messages.insert(Ordering::value_type(added.getSequence(), added)).first->second.setState(AVAILABLE);
        return false;
    } else {
        //there is already a message with that key which needs to be replaced
        removed = result.first->second;
        result.first->second = replace(result.first->second, added);
        result.first->second.setState(AVAILABLE);
        QPID_LOG(debug, "Displaced message at " << removed.getSequence() << " with " << result.first->second.getSequence() << ": " << result.first->first);
        return true;
    }
}

Message* MessageMap::release(const QueueCursor& cursor)
{
    Ordering::iterator i = messages.find(cursor.position);
    if (i != messages.end()) {
        i->second.setState(AVAILABLE);
        return &i->second;
    } else {
        return 0;
    }
}

void MessageMap::foreach(Functor f)
{
    for (Ordering::iterator i = messages.begin(); i != messages.end(); ++i) {
        if (i->second.getState() == AVAILABLE) f(i->second);
    }
}

void MessageMap::erase(Ordering::iterator i)
{
    index.erase(getKey(i->second));
    messages.erase(i);
}

MessageMap::MessageMap(const std::string& k) : key(k), version(0) {}

}} // namespace qpid::broker
