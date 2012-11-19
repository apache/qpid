#ifndef QPID_MESSAGING_AMQP_ADDRESSHELPER_H
#define QPID_MESSAGING_AMQP_ADDRESSHELPER_H

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
#include "qpid/types/Variant.h"

struct pn_terminus_t;

namespace qpid {
namespace messaging {
class Address;
namespace amqp {

class AddressHelper
{
  public:
    enum CheckMode {FOR_RECEIVER, FOR_SENDER};

    AddressHelper(const Address& address);
    bool createEnabled(CheckMode mode) const;
    bool deleteEnabled(CheckMode mode) const;
    bool assertEnabled(CheckMode mode) const;

    void setNodeProperties(pn_terminus_t*);
    const qpid::types::Variant::Map& getNodeProperties() const;
    const qpid::types::Variant::Map& getLinkProperties() const;
  private:
    std::string createPolicy;
    std::string assertPolicy;
    std::string deletePolicy;
    qpid::types::Variant::Map node;
    qpid::types::Variant::Map link;

    bool enabled(const std::string& policy, CheckMode mode) const;
};
}}} // namespace qpid::messaging::amqp

#endif  /*!QPID_MESSAGING_AMQP_ADDRESSHELPER_H*/
