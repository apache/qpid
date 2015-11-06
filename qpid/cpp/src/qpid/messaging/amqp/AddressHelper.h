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
#include <vector>

struct pn_link_t;
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
    void configure(pn_link_t* link, pn_terminus_t* terminus, CheckMode mode);
    void checkAssertion(pn_terminus_t* terminus, CheckMode mode);

    bool isNameNull() const;
    bool isUnreliable() const;
    const qpid::types::Variant::Map& getNodeProperties() const;
    bool getLinkSource(std::string& out) const;
    bool getLinkTarget(std::string& out) const;
    const qpid::types::Variant::Map& getLinkProperties() const;
    static std::string getLinkName(const Address& address);
  private:
    struct Filter
    {
        std::string name;
        std::string descriptorSymbol;
        uint64_t descriptorCode;
        qpid::types::Variant value;
        bool confirmed;

        Filter();
        Filter(const std::string& name, uint64_t descriptor, const qpid::types::Variant& value);
        Filter(const std::string& name, const std::string& descriptor, const qpid::types::Variant& value);
    };

    bool isTemporary;
    std::string createPolicy;
    std::string assertPolicy;
    std::string deletePolicy;
    qpid::types::Variant::Map node;
    qpid::types::Variant::Map link;
    qpid::types::Variant::Map properties;
    qpid::types::Variant::List capabilities;
    std::string name;
    std::string type;
    std::string reliability;
    bool durableNode;
    bool durableLink;
    uint32_t timeout;
    bool browse;
    std::vector<Filter> filters;

    bool enabled(const std::string& policy, CheckMode mode) const;
    bool createEnabled(CheckMode mode) const;
    bool assertEnabled(CheckMode mode) const;
    void setCapabilities(pn_terminus_t* terminus, bool create);
    void setNodeProperties(pn_terminus_t* terminus);
    void addFilter(const qpid::types::Variant::Map&);
    void addFilter(const std::string& name, uint64_t descriptor, const qpid::types::Variant& value);
    void addFilter(const std::string& name, const std::string& descriptor, const qpid::types::Variant& value);
    void addFilters(const qpid::types::Variant::List&);
    void confirmFilter(const std::string& descriptor);
    void confirmFilter(uint64_t descriptor);
    bool getLinkOption(const std::string& name, std::string& out) const;
};
}}} // namespace qpid::messaging::amqp

#endif  /*!QPID_MESSAGING_AMQP_ADDRESSHELPER_H*/
