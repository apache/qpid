#ifndef QPID_BROKER_AMQP_HEADER_H
#define QPID_BROKER_AMQP_HEADER_H

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
#include "qpid/amqp/MessageEncoder.h"

namespace qpid {
namespace broker {
class Message;
namespace amqp {

/**
 * Adapts the broker current message abstraction to provide that
 * required by the AMQP 1.0 message encoder.
 */
class Header : public qpid::amqp::MessageEncoder::Header
{
  public:
    Header(const qpid::broker::Message&);
    bool isDurable() const;
    uint8_t getPriority() const;
    bool hasTtl() const;
    uint32_t getTtl() const;
    bool isFirstAcquirer() const;
    uint32_t getDeliveryCount() const;
  private:
    const qpid::broker::Message& message;
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_HEADER_H*/
