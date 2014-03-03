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
#include "qpid/broker/amqp/Header.h"
#include "qpid/broker/Message.h"

namespace qpid {
namespace broker {
namespace amqp {

bool Header::isDurable() const
{
    return message.isPersistent();
}

uint8_t Header::getPriority() const
{
    return message.getPriority();
}

bool Header::hasTtl() const
{
    uint64_t dummy(0);
    return message.getTtl(dummy);
}

uint32_t Header::getTtl() const
{
    uint64_t ttl(0);
    message.getTtl(ttl);
    if (ttl > std::numeric_limits<uint32_t>::max()) return std::numeric_limits<uint32_t>::max();
    else return (uint32_t) ttl;
}

bool Header::isFirstAcquirer() const
{
    return (!message.hasBeenAcquired());
}

uint32_t Header::getDeliveryCount() const
{
    return message.isRedelivered() ? message.getDeliveryCount() : 0;
}

Header::Header(const qpid::broker::Message& m) : message(m) {}


}}} // namespace qpid::broker::amqp
