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
#include "qpid/broker/QueuedMessage.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {
namespace {
const std::string EMPTY;
}

bool MessageMap::deleted(const QueuedMessage& message)
{
    Ordering::iterator i = messages.find(message.position);
    if (i != messages.end()) {
        erase(i);
        return true;
    } else {
        return false;
    }
}

std::string MessageMap::getKey(const QueuedMessage& message)
{
    const framing::FieldTable* ft = message.payload->getApplicationHeaders();
    if (ft) return ft->getAsString(key);
    else return EMPTY;
}

size_t MessageMap::size()
{
    size_t count(0);
    for (Ordering::iterator i = messages.begin(); i != messages.end(); ++i) {
        if (i->second.status == QueuedMessage::AVAILABLE) ++count;
    }
    return count;
}

bool MessageMap::empty()
{
    return size() == 0;//TODO: more efficient implementation
}

void MessageMap::release(const QueuedMessage& message)
{
    Ordering::iterator i = messages.find(message.position);
    if (i != messages.end() && i->second.status == QueuedMessage::ACQUIRED) {
        i->second.status = QueuedMessage::AVAILABLE;
    }
}

bool MessageMap::acquire(const framing::SequenceNumber& position, QueuedMessage& message)
{
    Ordering::iterator i = messages.find(position);
    if (i != messages.end() && i->second.status == QueuedMessage::AVAILABLE) {
        i->second.status = QueuedMessage::ACQUIRED;
        message = i->second;
        return true;
    } else {
        return false;
    }
}

bool MessageMap::find(const framing::SequenceNumber& position, QueuedMessage& message)
{
    Ordering::iterator i = messages.find(position);
    if (i != messages.end() && i->second.status == QueuedMessage::AVAILABLE) {
        message = i->second;
        return true;
    } else {
        return false;
    }
}

bool MessageMap::browse(const framing::SequenceNumber& position, QueuedMessage& message, bool unacquired)
{
    Ordering::iterator i = messages.lower_bound(position+1);
    if (i != messages.end() && (i->second.status == QueuedMessage::AVAILABLE  || (!unacquired && i->second.status == QueuedMessage::ACQUIRED))) {
        message = i->second;
        return true;
    } else {
        return false;
    }
}

bool MessageMap::consume(QueuedMessage& message)
{
    for (Ordering::iterator i = messages.begin(); i != messages.end(); ++i) {
        if (i->second.status == QueuedMessage::AVAILABLE) {
            i->second.status = QueuedMessage::ACQUIRED;
            message = i->second;
            return true;
        }
    }
    return false;
}

const QueuedMessage& MessageMap::replace(const QueuedMessage& original, const QueuedMessage& update)
{
    messages.erase(original.position);
    messages[update.position] = update;
    return update;
}

bool MessageMap::push(const QueuedMessage& added, QueuedMessage& removed)
{
    std::pair<Index::iterator, bool> result = index.insert(Index::value_type(getKey(added), added));
    if (result.second) {
        //there was no previous message for this key; nothing needs to
        //be removed, just add the message into its correct position
        QueuedMessage& a = messages[added.position];
        a = added;
        a.status = QueuedMessage::AVAILABLE;
        QPID_LOG(debug, "Added message at " << a.position);
        return false;
    } else {
        //there is already a message with that key which needs to be replaced
        removed = result.first->second;
        result.first->second = replace(result.first->second, added);
        result.first->second.status = QueuedMessage::AVAILABLE;
        QPID_LOG(debug, "Displaced message at " << removed.position << " with " << result.first->second.position << ": " << result.first->first);
        return true;
    }
}

void MessageMap::foreach(Functor f)
{
    for (Ordering::iterator i = messages.begin(); i != messages.end(); ++i) {
        if (i->second.status == QueuedMessage::AVAILABLE) f(i->second);
    }
}

void MessageMap::removeIf(Predicate p)
{
    for (Ordering::iterator i = messages.begin(); i != messages.end();) {
        if (i->second.status == QueuedMessage::AVAILABLE && p(i->second)) {
            index.erase(getKey(i->second));
            //Note: Removing from messages means that the subsequent
            //call to deleted() for the same message will return
            //false. At present that is not a problem. If this were
            //changed to hold onto the message until dequeued
            //(e.g. with REMOVED state), then the erase() below would
            //need to take that into account.
            messages.erase(i++);
        } else {
            ++i;
        }
    }
}

void MessageMap::erase(Ordering::iterator i)
{
    index.erase(getKey(i->second));
    messages.erase(i);
}

MessageMap::MessageMap(const std::string& k) : key(k) {}

}} // namespace qpid::broker
