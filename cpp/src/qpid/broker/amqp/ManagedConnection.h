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
#include "qpid/broker/Connection.h"
#include "qpid/types/Variant.h"
#include "qmf/org/apache/qpid/broker/Connection.h"

namespace qpid {
namespace management {
class ManagementAgent;
class ManagementObject;
}
namespace broker {
class Broker;
namespace amqp {

class ManagedConnection : public qpid::management::Manageable, public qpid::broker::Connection
{
  public:
    ManagedConnection(Broker& broker, const std::string id, bool brokerInitiated);
    virtual ~ManagedConnection();
    virtual void setUserId(const std::string&);
    std::string getId() const;
    void setSaslMechanism(const std::string&);
    void setSaslSsf(int);
    void setContainerId(const std::string&);
    const std::string& getContainerId() const;
    void setInterconnectDomain(const std::string&);
    const std::string& getInterconnectDomain() const;
    void setPeerProperties(std::map<std::string, types::Variant>&);
    qpid::management::ManagementObject::shared_ptr GetManagementObject() const;
    bool isLocal(const OwnershipToken* t) const;
    void incomingMessageReceived();
    void outgoingMessageSent();

    //ConnectionIdentity
    const management::ObjectId getObjectId() const;
    const std::string& getUserId() const;
    const std::string& getMgmtId() const;
    const std::map<std::string, types::Variant>& getClientProperties() const;
    virtual bool isLink() const;
    void opened();

    qpid::management::Manageable::status_t ManagementMethod(uint32_t methodId, qpid::management::Args&, std::string&);

  protected:
    virtual void closedByManagement();
  private:
    const std::string id;
    std::string userid;
    std::string containerid;
    std::string domain;
    qmf::org::apache::qpid::broker::Connection::shared_ptr connection;
    qpid::management::ManagementAgent* agent;
    std::map<std::string, types::Variant> peerProperties;
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_MANAGEDCONNECTION_H*/
