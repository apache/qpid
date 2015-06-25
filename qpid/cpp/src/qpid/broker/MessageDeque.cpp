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
#include "assert.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/QueueCursor.h"
#include "qpid/framing/SequenceNumber.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {
namespace {
Message padding(qpid::framing::SequenceNumber id) {
    Message m;
    m.setState(DELETED);
    m.setSequence(id);
    return m;
}
}

using qpid::framing::SequenceNumber;

MessageDeque::MessageDeque() : messages(&padding) {}


bool MessageDeque::deleted(const QueueCursor& cursor)
{
    return messages.deleted(cursor);
}

void MessageDeque::publish(const Message& added)
{
    messages.publish(added);
}

Message* MessageDeque::release(const QueueCursor& cursor)
{
    return messages.release(cursor);
}

Message* MessageDeque::next(QueueCursor& cursor)
{
    return messages.next(cursor);
}

size_t MessageDeque::size()
{
    return messages.size();
}

Message* MessageDeque::find(const framing::SequenceNumber& position, QueueCursor* cursor)
{
    return messages.find(position, cursor);
}

Message* MessageDeque::find(const QueueCursor& cursor)
{
    return messages.find(cursor);
}

void MessageDeque::foreach(Functor f)
{
    messages.foreach(f);
}

void MessageDeque::resetCursors()
{
    messages.resetCursors();
}

}} // namespace qpid::broker
