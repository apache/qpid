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

/* Used by queues to allocate the next "most desirable" message to a consuming client */

#include "qpid/broker/Queue.h"
#include "qpid/broker/MessageAllocator.h"
#include "qpid/sys/Mutex.h"

using namespace qpid::broker;

bool MessageAllocator::nextConsumableMessage( Consumer::shared_ptr&, QueuedMessage& next,
                                              const qpid::sys::Mutex::ScopedLock&)
{
    Messages& messages(queue->getMessages());
    if (!messages.empty()) {
        next = messages.front();    // by default, consume oldest msg
        return true;
    }
    return false;
}

bool MessageAllocator::nextBrowsableMessage( Consumer::shared_ptr& c, QueuedMessage& next,
                                             const qpid::sys::Mutex::ScopedLock&)
{
    Messages& messages(queue->getMessages());
    if (!messages.empty() && messages.next(c->position, next))
        return true;
    return false;
}


bool MessageAllocator::acquirable( const std::string&,
                                   const QueuedMessage&,
                                   const qpid::sys::Mutex::ScopedLock&)
{
    // by default, all messages present on the queue are acquireable
    return true;
}

void MessageAllocator::query(qpid::types::Variant::Map&, const qpid::sys::Mutex::ScopedLock&) const
{
}

