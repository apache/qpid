#ifndef _broker_MessageAllocator_h
#define _broker_MessageAllocator_h

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

/** Abstraction used by Queue to determine the next "most desirable" message to provide to
 * a particular consuming client
 */


#include "qpid/broker/Consumer.h"

namespace qpid {
namespace broker {

struct QueuedMessage;

class MessageAllocator
{
 public:
    virtual ~MessageAllocator() {};

    /** Locking Note: all methods assume the caller is holding the Queue::messageLock
     * during the method call.
     */

    /** Determine the next message available for consumption by the consumer
     * @param consumer the consumer that needs a message to consume
     * @param next set to the next message that the consumer may consume.
     * @return true if message is available and next is set
     */
    virtual bool nextConsumableMessage( Consumer::shared_ptr& consumer,
                                        QueuedMessage& next ) = 0;

    /** Allow the comsumer to take ownership of the given message.
     * @param consumer the name of the consumer that is attempting to acquire the message
     * @param qm the message to be acquired, previously returned from nextConsumableMessage()
     * @return true if ownership is permitted, false if ownership cannot be assigned.
     */
    virtual bool allocate( const std::string& consumer,
                           const QueuedMessage& target) = 0;

    /** Determine the next message available for browsing by the consumer
     * @param consumer the consumer that is browsing the queue
     * @param next set to the next message that the consumer may browse.
     * @return true if a message is available and next is returned
     */
    virtual bool nextBrowsableMessage( Consumer::shared_ptr& consumer,
                                       QueuedMessage& next ) = 0;

    /** hook to add any interesting management state to the status map */
    virtual void query(qpid::types::Variant::Map&) const = 0;
};

}}

#endif
