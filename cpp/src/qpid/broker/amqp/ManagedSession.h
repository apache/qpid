#ifndef QPID_BROKER_AMQP_MANAGEDSESSION_H
#define QPID_BROKER_AMQP_MANAGEDSESSION_H

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
#include "qpid/management/Manageable.h"
#include "qmf/org/apache/qpid/broker/Session.h"
#include "qpid/broker/OwnershipToken.h"

namespace qpid {
namespace management {
class ManagementObject;
}
namespace broker {
class Broker;
namespace amqp {
class ManagedConnection;

class ManagedSession : public qpid::management::Manageable, public OwnershipToken
{
  public:
    ManagedSession(Broker& broker, ManagedConnection& parent, const std::string id);
    virtual ~ManagedSession();
    qpid::management::ManagementObject::shared_ptr GetManagementObject() const;
    bool isLocal(const OwnershipToken* t) const;
    void incomingMessageReceived();
    void incomingMessageAccepted();
    void incomingMessageRejected();
    void outgoingMessageSent();
    void outgoingMessageAccepted();
    void outgoingMessageRejected();
    void txStarted();
    void txCommitted();
    void txAborted();
    ManagedConnection& getParent();

    qpid::management::Manageable::status_t ManagementMethod (uint32_t, qpid::management::Args&, std::string&);
  protected:
    virtual void detachedByManagement();
  private:
    ManagedConnection& parent;
    const std::string id;
    qmf::org::apache::qpid::broker::Session::shared_ptr session;
    size_t unacked;
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_MANAGEDSESSION_H*/
