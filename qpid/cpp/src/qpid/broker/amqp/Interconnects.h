#ifndef QPID_BROKER_AMQP_INTERCONNECTS_H
#define QPID_BROKER_AMQP_INTERCONNECTS_H

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
#include "qpid/broker/ObjectFactory.h"
#include "qpid/sys/Mutex.h"
#include <string>
#include <map>
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace broker {
namespace amqp {
class BrokerContext;
class Domain;
class Interconnect;
/**
 *
 */
class Interconnects : public ObjectFactory
{
  public:
    bool createObject(Broker&, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                              const std::string& userId, const std::string& connectionId);
    bool deleteObject(Broker&, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                              const std::string& userId, const std::string& connectionId);
    bool recoverObject(Broker&, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                       uint64_t persistenceId);

    bool add(const std::string&, boost::shared_ptr<Interconnect>);
    boost::shared_ptr<Interconnect> get(const std::string&);
    bool remove(const std::string&);

    boost::shared_ptr<Domain> findDomain(const std::string&);
    void setContext(BrokerContext&);
    Interconnects();
  private:
    typedef std::map<std::string, boost::shared_ptr<Interconnect> > InterconnectMap;
    typedef std::map<std::string, boost::shared_ptr<Domain> > DomainMap;
    InterconnectMap interconnects;
    DomainMap domains;
    qpid::sys::Mutex lock;
    BrokerContext* context;
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_INTERCONNECTS_H*/
