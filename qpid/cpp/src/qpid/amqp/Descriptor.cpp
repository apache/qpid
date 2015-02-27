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
#include "Descriptor.h"
#include "descriptors.h"
#include <qpid/framing/reply_exceptions.h>
#include <map>

namespace qpid {
namespace amqp {

Descriptor::Descriptor(uint64_t code) : type(NUMERIC) { value.code = code; }

Descriptor::Descriptor(const CharSequence& symbol) : type(SYMBOLIC) { value.symbol = symbol; }

bool Descriptor::match(const std::string& symbol, uint64_t code) const
{
    switch (type) {
      case SYMBOLIC:
        return symbol.compare(0, symbol.size(), value.symbol.data, value.symbol.size) == 0;
      case NUMERIC:
        return code == value.code;
    }
    return false;
}

size_t Descriptor::getSize() const
{
    size_t size = 1/*descriptor indicator*/ + 1/*type code*/;
    switch (type) {
        case Descriptor::NUMERIC:
          if (value.code > 0)  size += value.code < 256 ? 1/*encode as byte*/ : 8/*encode as long*/;
          //else value will be indicated through ULONG_ZERO typecode
        break;
        case Descriptor::SYMBOLIC:
          size += value.symbol.size < 256? 1/*size field is a byte*/ : 4/*size field is an int*/;
          size += value.symbol.size;
          break;
    }
    return size;
}

Descriptor* Descriptor::nest(const Descriptor& d)
{
    nested = boost::shared_ptr<Descriptor>(new Descriptor(0));
    *nested = d;
    return nested.get();
}

namespace {

class DescriptorMap {
    typedef std::map<uint64_t, std::string> SymbolMap;
    typedef std::map<std::string, uint64_t> CodeMap;

    SymbolMap symbols;
    CodeMap codes;

  public:
    DescriptorMap() {
        symbols[message::HEADER_CODE] = message::HEADER_SYMBOL;
        symbols[message::DELIVERY_ANNOTATIONS_CODE] = message::DELIVERY_ANNOTATIONS_SYMBOL;
        symbols[message::MESSAGE_ANNOTATIONS_CODE] = message::MESSAGE_ANNOTATIONS_SYMBOL;
        symbols[message::PROPERTIES_CODE] = message::PROPERTIES_SYMBOL;
        symbols[message::APPLICATION_PROPERTIES_CODE] = message::APPLICATION_PROPERTIES_SYMBOL;
        symbols[message::DATA_CODE] = message::DATA_SYMBOL;
        symbols[message::AMQP_SEQUENCE_CODE] = message::AMQP_SEQUENCE_SYMBOL;
        symbols[message::AMQP_VALUE_CODE] = message::AMQP_VALUE_SYMBOL;
        symbols[message::FOOTER_CODE] = message::FOOTER_SYMBOL;
        symbols[message::ACCEPTED_CODE] = message::ACCEPTED_SYMBOL;
        symbols[sasl::SASL_MECHANISMS_CODE] = sasl::SASL_MECHANISMS_SYMBOL;
        symbols[sasl::SASL_INIT_CODE] = sasl::SASL_INIT_SYMBOL;
        symbols[sasl::SASL_CHALLENGE_CODE] = sasl::SASL_CHALLENGE_SYMBOL;
        symbols[sasl::SASL_RESPONSE_CODE] = sasl::SASL_RESPONSE_SYMBOL;
        symbols[sasl::SASL_OUTCOME_CODE] = sasl::SASL_OUTCOME_SYMBOL;
        symbols[filters::LEGACY_DIRECT_FILTER_CODE] = filters::LEGACY_DIRECT_FILTER_SYMBOL;
        symbols[filters::LEGACY_TOPIC_FILTER_CODE] = filters::LEGACY_TOPIC_FILTER_SYMBOL;
        symbols[filters::LEGACY_HEADERS_FILTER_CODE] = filters::LEGACY_HEADERS_FILTER_SYMBOL;
        symbols[filters::SELECTOR_FILTER_CODE] = filters::SELECTOR_FILTER_SYMBOL;
        symbols[filters::XQUERY_FILTER_CODE] = filters::XQUERY_FILTER_SYMBOL;
        symbols[lifetime_policy::DELETE_ON_CLOSE_CODE] = lifetime_policy::DELETE_ON_CLOSE_SYMBOL;
        symbols[lifetime_policy::DELETE_ON_NO_LINKS_CODE] = lifetime_policy::DELETE_ON_NO_LINKS_SYMBOL;
        symbols[lifetime_policy::DELETE_ON_NO_MESSAGES_CODE] = lifetime_policy::DELETE_ON_NO_MESSAGES_SYMBOL;
        symbols[lifetime_policy::DELETE_ON_NO_LINKS_OR_MESSAGES_CODE] = lifetime_policy::DELETE_ON_NO_LINKS_OR_MESSAGES_SYMBOL;
        symbols[transaction::DECLARE_CODE] = transaction::DECLARE_SYMBOL;
        symbols[transaction::DISCHARGE_CODE] = transaction::DISCHARGE_SYMBOL;
        symbols[transaction::DECLARED_CODE] = transaction::DECLARED_SYMBOL;
        symbols[transaction::TRANSACTIONAL_STATE_CODE] = transaction::TRANSACTIONAL_STATE_SYMBOL;
        symbols[0] = "unknown-descriptor";

        for (SymbolMap::const_iterator i = symbols.begin(); i != symbols.end(); ++i)
            codes[i->second] = i->first;
    }

    std::string operator[](uint64_t code) const {
        SymbolMap::const_iterator i = symbols.find(code);
        return (i == symbols.end()) ? "unknown-descriptor" : i->second;
    }

    uint64_t operator[](const std::string& symbol) const {
         CodeMap::const_iterator i = codes.find(symbol);
         return (i == codes.end()) ? 0 : i->second;
     }
};

DescriptorMap DESCRIPTOR_MAP;
}

std::string Descriptor::symbol() const {
    switch (type) {
      case Descriptor::NUMERIC: return DESCRIPTOR_MAP[value.code];
      case Descriptor::SYMBOLIC: return value.symbol.str();
    }
    assert(0);
    return std::string();
}

uint64_t Descriptor::code() const {
    switch (type) {
      case Descriptor::NUMERIC: return value.code;
      case Descriptor::SYMBOLIC: return DESCRIPTOR_MAP[value.symbol.str()];
    }
    assert(0);
    return 0;
}

std::ostream& operator<<(std::ostream& os, const Descriptor& d) {
    return os << d.symbol() << "(" << "0x" << std::hex << d.code() << ")";
}

}} // namespace qpid::amqp
