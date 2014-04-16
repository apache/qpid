#ifndef QPID_BROKER_AMQP_DOMAIN_H
#define QPID_BROKER_AMQP_DOMAIN_H

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
#include "qpid/sys/ConnectionCodec.h"
#include "qpid/types/Variant.h"
#include "qpid/Url.h"
#include "qpid/Version.h"
#include "qpid/broker/PersistableObject.h"
#include "qpid/management/Manageable.h"
#include "qpid/sys/Mutex.h"
#include "qmf/org/apache/qpid/broker/Domain.h"
#include <boost/shared_ptr.hpp>
#include <boost/enable_shared_from_this.hpp>
#include <memory>
#include <set>

namespace qpid {
class Sasl;
namespace management {
class ManagementAgent;
class ManagementObject;
}
namespace broker {
class Broker;
namespace amqp {
class InterconnectFactory;
class BrokerContext;
class Relay;

class Domain : public PersistableObject, public qpid::management::Manageable, public boost::enable_shared_from_this<Domain>
{
  public:
    Domain(const std::string& name, const qpid::types::Variant::Map& properties, Broker&);
    ~Domain();
    void connect(bool incoming, const std::string& name, const qpid::types::Variant::Map& properties, BrokerContext&);
    void connect(bool incoming, const std::string& name, const std::string& source, const std::string& target, BrokerContext&, boost::shared_ptr<Relay>);
    std::auto_ptr<qpid::Sasl> sasl(const std::string& hostname);
    const std::string& getMechanisms() const;
    qpid::Url getUrl() const;
    bool isDurable() const;
    void addPending(boost::shared_ptr<InterconnectFactory>);
    void removePending(boost::shared_ptr<InterconnectFactory>);
    boost::shared_ptr<qpid::management::ManagementObject> GetManagementObject() const;
  private:
    std::string name;
    bool durable;
    Broker& broker;
    qpid::Url url;
    std::string username;
    std::string password;
    std::string mechanisms;
    std::string service;
    int minSsf;
    int maxSsf;
    boost::shared_ptr<qmf::org::apache::qpid::broker::Domain> domain;
    qpid::management::ManagementAgent* agent;
    std::set< boost::shared_ptr<InterconnectFactory> > pending;
    qpid::sys::Mutex lock;
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_DOMAIN_H*/
