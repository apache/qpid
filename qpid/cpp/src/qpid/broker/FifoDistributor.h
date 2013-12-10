#ifndef _broker_FifoDistributor_h
#define _broker_FifoDistributor_h

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

/** Simple MessageDistributor for FIFO Queues - the HEAD message is always the next
 * available message for consumption.
 */

#include "qpid/broker/MessageDistributor.h"

namespace qpid {
namespace broker {

class Messages;

class FifoDistributor : public MessageDistributor
{
 public:
    FifoDistributor(Messages& container);

    bool acquire(const std::string& consumer, Message& target);
    void query(qpid::types::Variant::Map&) const;

 private:
    Messages& messages;
};

}}

#endif
