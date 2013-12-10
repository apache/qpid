#ifndef QPID_MESSAGING_AMQP_SESSIONIMPL_H
#define QPID_MESSAGING_AMQP_SESSIONIMPL_H

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
#include "qpid/messaging/SessionImpl.h"

namespace qpid {
namespace messaging {
namespace amqp {

class ConnectionContext;
class SessionContext;
/**
 *
 */
class SessionHandle : public qpid::messaging::SessionImpl
{
  public:
    SessionHandle(boost::shared_ptr<ConnectionContext>, boost::shared_ptr<SessionContext>);
    void commit();
    void rollback();
    void acknowledge(bool sync);
    void acknowledge(Message&, bool);
    void reject(Message&);
    void release(Message&);
    void close();
    void sync(bool block);
    qpid::messaging::Sender createSender(const Address& address);
    qpid::messaging::Receiver createReceiver(const Address& address);
    bool nextReceiver(Receiver& receiver, Duration timeout);
    qpid::messaging::Receiver nextReceiver(Duration timeout);
    uint32_t getReceivable();
    uint32_t getUnsettledAcks();
    qpid::messaging::Sender getSender(const std::string& name) const;
    qpid::messaging::Receiver getReceiver(const std::string& name) const;
    qpid::messaging::Connection getConnection() const;
    void checkError();
  private:
    boost::shared_ptr<ConnectionContext> connection;
    boost::shared_ptr<SessionContext> session;
};
}}} // namespace qpid::messaging::amqp

#endif  /*!QPID_MESSAGING_AMQP_SESSIONIMPL_H*/
