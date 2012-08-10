#ifndef _broker_MessageDistributor_h
#define _broker_MessageDistributor_h

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
#include "qpid/types/Variant.h"
/** Abstraction used by Queue to determine the next "most desirable" message to provide to
 * a particular consuming client
 */

namespace qpid {
namespace broker {

class Message;

class MessageDistributor
{
 public:
    virtual ~MessageDistributor() {};

    /**
     * Determine whether the named consumer can take ownership of the specified message.
     * @param consumer the name of the consumer that is attempting to acquire the message
     * @param target the message to be acquired
     * @return true if ownership is permitted, false if ownership cannot be assigned.
     */
    virtual bool acquire(const std::string& consumer, Message& target) = 0;

    /** hook to add any interesting management state to the status map */
    virtual void query(qpid::types::Variant::Map&) const = 0;
};

}}

#endif
