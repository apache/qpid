#ifndef QPID_BROKER_AMQP_INTERCONNECT_H
#define QPID_BROKER_AMQP_INTERCONNECT_H

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
#include "Connection.h"

namespace qpid {
struct Address;
namespace broker {
namespace amqp {
class Domain;
class Interconnects;
class Relay;

/**
 *
 */
class Interconnect : public Connection
{
  public:
    Interconnect(qpid::sys::OutputControl& out, const std::string& id, BrokerContext& broker, bool saslInUse,
                 bool incoming, const std::string& name,
                 const std::string& source, const std::string& target, const std::string& domain);
    void setRelay(boost::shared_ptr<Relay>);
    ~Interconnect();
    size_t encode(char* buffer, size_t size);
    void deletedFromRegistry();
    void transportDeleted();
    bool isLink() const;
  private:
    bool incoming;
    std::string name;
    std::string source;
    std::string target;
    std::string domain;
    bool headerDiscarded;
    boost::shared_ptr<Relay> relay;
    bool isOpened;
    bool closeRequested;
    bool isTransportDeleted;

    void process();
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_INTERCONNECT_H*/
