#ifndef QPID_BROKER_AMQP_MANAGEDCONNECTION_H
#define QPID_BROKER_AMQP_MANAGEDCONNECTION_H

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
#include "qpid/broker/ConnectionToken.h"
#include "qmf/org/apache/qpid/broker/Connection.h"

namespace qpid {
namespace management {
class ManagementAgent;
class ManagementObject;
}
namespace broker {
class Broker;
namespace amqp {

class ManagedConnection : public qpid::management::Manageable, public ConnectionToken
{
  public:
    ManagedConnection(Broker& broker, const std::string id);
    virtual ~ManagedConnection();
    void setUserid(const std::string&);
    std::string getId() const;
    std::string getUserid() const;
    void setSaslMechanism(const std::string&);
    void setSaslSsf(int);
    void setContainerId(const std::string&);
    const std::string& getContainerId() const;
    qpid::management::ManagementObject::shared_ptr GetManagementObject() const;
    bool isLocal(const ConnectionToken* t) const;
    void incomingMessageReceived();
    void outgoingMessageSent();
  private:
    const std::string id;
    std::string userid;
    std::string containerid;
    qmf::org::apache::qpid::broker::Connection::shared_ptr connection;
    qpid::management::ManagementAgent* agent;
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_MANAGEDCONNECTION_H*/
