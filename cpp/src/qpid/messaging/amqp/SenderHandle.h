#ifndef QPID_MESSAGING_AMQP_SENDERHANDLE_H
#define QPID_MESSAGING_AMQP_SENDERHANDLE_H

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
#include <boost/shared_ptr.hpp>
#include "qpid/messaging/SenderImpl.h"

namespace qpid {
namespace messaging {
namespace amqp {

class ConnectionContext;
class SessionContext;
class SenderContext;
/**
 *
 */
class SenderHandle : public qpid::messaging::SenderImpl
{
  public:
    SenderHandle(boost::shared_ptr<ConnectionContext> connection,
                 boost::shared_ptr<SessionContext> session,
                 boost::shared_ptr<SenderContext> sender
    );
    void send(const Message& message, bool sync);
    void close();
    void setCapacity(uint32_t);
    uint32_t getCapacity();
    uint32_t getUnsettled();
    const std::string& getName() const;
    Session getSession() const;
    Address getAddress() const;
  private:
    boost::shared_ptr<ConnectionContext> connection;
    boost::shared_ptr<SessionContext> session;
    boost::shared_ptr<SenderContext> sender;
};
}}} // namespace qpid::messaging::amqp

#endif  /*!QPID_MESSAGING_AMQP_SENDERHANDLE_H*/
