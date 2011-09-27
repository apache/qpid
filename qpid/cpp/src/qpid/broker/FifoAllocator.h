#ifndef _broker_FifoAllocator_h
#define _broker_FifoAllocator_h

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

/** Simple MessageAllocator for FIFO Queues - the HEAD message is always the next
 * available message for consumption.
 */

#include "qpid/broker/MessageAllocator.h"

namespace qpid {
namespace broker {

class Messages;

class FifoAllocator : public MessageAllocator
{
 public:
    FifoAllocator(Messages& container);

    /** Locking Note: all methods assume the caller is holding the Queue::messageLock
     * during the method call.
     */

    /** MessageAllocator interface */

    bool nextConsumableMessage( Consumer::shared_ptr& consumer, QueuedMessage& next );
    bool allocate(const std::string& consumer, const QueuedMessage& target);
    bool nextBrowsableMessage( Consumer::shared_ptr& consumer, QueuedMessage& next );
    void query(qpid::types::Variant::Map&) const;

 private:
    Messages& messages;
};

}}

#endif
