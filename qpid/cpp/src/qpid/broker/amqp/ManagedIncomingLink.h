#ifndef QPID_BROKER_AMQP_MANAGEDINCOMINGLINK_H
#define QPID_BROKER_AMQP_MANAGEDINCOMINGLINK_H

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
#include "qmf/org/apache/qpid/broker/Incoming.h"

namespace qpid {
namespace management {
class ManagementObject;
}
namespace broker {
class Broker;
namespace amqp {
class ManagedSession;

class ManagedIncomingLink : public qpid::management::Manageable
{
  public:
    ManagedIncomingLink(Broker& broker, ManagedSession& parent, const std::string& source, const std::string& target, const std::string& name);
    virtual ~ManagedIncomingLink();
    qpid::management::ManagementObject::shared_ptr GetManagementObject() const;
    void incomingMessageReceived();
  private:
    ManagedSession& parent;
    const std::string name;
    qmf::org::apache::qpid::broker::Incoming::shared_ptr incoming;
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_MANAGEDINCOMINGLINK_H*/
