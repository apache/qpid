#ifndef QPID_BROKER_SESSIONCONTEXT_H
#define QPID_BROKER_SESSIONCONTEXT_H

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

#include "qpid/broker/OwnershipToken.h"

#include <boost/noncopyable.hpp>

namespace qpid {

class SessionId;

namespace framing {
class AMQP_ClientProxy;
}
namespace broker {

class Broker;
namespace amqp_0_10 {
class Connection;
}

class SessionContext : public OwnershipToken
{
  public:
    virtual ~SessionContext(){}
    virtual bool isAttached() const = 0;
    virtual amqp_0_10::Connection& getConnection() = 0;
    virtual framing::AMQP_ClientProxy& getProxy() = 0;
    virtual Broker& getBroker() = 0;
    virtual uint16_t getChannel() const = 0;
    virtual const SessionId& getSessionId() const = 0;
    virtual bool addPendingExecutionSync() = 0;
    virtual void setUnackedCount(uint64_t) {}
};

}} // namespace qpid::broker



#endif  /*!QPID_BROKER_SESSIONCONTEXT_H*/
