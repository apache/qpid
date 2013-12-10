#ifndef QPID_MESSAGING_AMQP_RECEIVERHANDLE_H
#define QPID_MESSAGING_AMQP_RECEIVERHANDLE_H

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
#include "qpid/messaging/ReceiverImpl.h"

namespace qpid {
namespace messaging {
namespace amqp {

class ConnectionContext;
class SessionContext;
class ReceiverContext;
/**
 *
 */
class ReceiverHandle : public qpid::messaging::ReceiverImpl
{
  public:
    ReceiverHandle(boost::shared_ptr<ConnectionContext>,
                   boost::shared_ptr<SessionContext>,
                   boost::shared_ptr<ReceiverContext>
    );
    bool get(Message& message, qpid::messaging::Duration timeout);
    qpid::messaging::Message get(qpid::messaging::Duration timeout);
    bool fetch(Message& message, qpid::messaging::Duration timeout);
    qpid::messaging::Message fetch(qpid::messaging::Duration timeout);
    void setCapacity(uint32_t);
    uint32_t getCapacity();
    uint32_t getAvailable();
    uint32_t getUnsettled();
    void close();
    const std::string& getName() const;
    qpid::messaging::Session getSession() const;
    bool isClosed() const;
    Address getAddress() const;
  private:
    boost::shared_ptr<ConnectionContext> connection;
    boost::shared_ptr<SessionContext> session;
    boost::shared_ptr<ReceiverContext> receiver;
};
}}} // namespace qpid::messaging::amqp

#endif  /*!QPID_MESSAGING_AMQP_RECEIVERHANDLE_H*/
