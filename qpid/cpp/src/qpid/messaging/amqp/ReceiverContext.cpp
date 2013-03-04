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
#include "qpid/messaging/amqp/ReceiverContext.h"
#include "qpid/messaging/amqp/AddressHelper.h"
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/Message.h"
#include "qpid/amqp/descriptors.h"
extern "C" {
#include <proton/engine.h>
}

namespace qpid {
namespace messaging {
namespace amqp {
//TODO: proper conversion to wide string for address
ReceiverContext::ReceiverContext(pn_session_t* session, const std::string& n, const qpid::messaging::Address& a)
  : name(n),
    address(a),
    receiver(pn_receiver(session, name.c_str())),
    capacity(0) {}
ReceiverContext::~ReceiverContext()
{
    pn_link_free(receiver);
}

void ReceiverContext::setCapacity(uint32_t c)
{
    if (c != capacity) {
        //stop
        capacity = c;
        //reissue credit
    }
}

uint32_t ReceiverContext::getCapacity()
{
    return capacity;
}

uint32_t ReceiverContext::getAvailable()
{
    uint32_t count(0);
    for (pn_delivery_t* d = pn_unsettled_head(receiver); d; d = pn_unsettled_next(d)) {
        ++count;
        if (d == pn_link_current(receiver)) break;
    }
    return count;
}

uint32_t ReceiverContext::getUnsettled()
{
    uint32_t count(0);
    for (pn_delivery_t* d = pn_unsettled_head(receiver); d; d = pn_unsettled_next(d)) {
        ++count;
    }
    return count;
}

void ReceiverContext::close()
{

}

const std::string& ReceiverContext::getName() const
{
    return name;
}

const std::string& ReceiverContext::getSource() const
{
    return address.getName();
}
namespace {
pn_bytes_t convert(const std::string& s)
{
    pn_bytes_t result;
    result.start = const_cast<char*>(s.data());
    result.size = s.size();
    return result;
}
bool hasWildcards(const std::string& key)
{
    return key.find('*') != std::string::npos || key.find('#') != std::string::npos;
}

uint64_t getFilterDescriptor(const std::string& key)
{
    return hasWildcards(key) ? qpid::amqp::filters::LEGACY_TOPIC_FILTER_CODE : qpid::amqp::filters::LEGACY_DIRECT_FILTER_CODE;
}
}

void ReceiverContext::configure() const
{
    configure(pn_link_source(receiver));
}
void ReceiverContext::configure(pn_terminus_t* source) const
{
    pn_terminus_set_address(source, address.getName().c_str());
    //dynamic create:
    AddressHelper helper(address);
    if (helper.createEnabled(AddressHelper::FOR_RECEIVER)) {
        helper.setNodeProperties(source);
    }

    // Look specifically for qpid.selector link property and add a filter for it
    qpid::types::Variant::Map::const_iterator i = helper.getLinkProperties().find("qpid.selector");
    if (i!=helper.getLinkProperties().end()) {
        pn_data_t* filter = pn_terminus_filter(source);
        pn_data_put_map(filter);
        pn_data_enter(filter);
        pn_data_put_symbol(filter, convert("qpid.selector"));
        pn_data_put_described(filter);
        pn_data_enter(filter);
        pn_data_put_symbol(filter, convert(qpid::amqp::filters::QPID_SELECTOR_FILTER_SYMBOL));
        pn_data_put_string(filter, convert(i->second));
        pn_data_exit(filter);
        pn_data_exit(filter);
    }
    if (!address.getSubject().empty()) {
        //filter:
        pn_data_t* filter = pn_terminus_filter(source);
        pn_data_put_map(filter);
        pn_data_enter(filter);
        pn_data_put_symbol(filter, convert("subject"));
        pn_data_put_described(filter);
        pn_data_enter(filter);
        pn_data_put_ulong(filter, getFilterDescriptor(address.getSubject()));
        pn_data_put_string(filter, convert(address.getSubject()));
        pn_data_exit(filter);
        pn_data_exit(filter);
    }
}

bool ReceiverContext::isClosed() const
{
    return false;//TODO
}



}}} // namespace qpid::messaging::amqp
