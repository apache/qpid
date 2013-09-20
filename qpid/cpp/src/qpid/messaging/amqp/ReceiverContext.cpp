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
#include "qpid/messaging/AddressImpl.h"
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/Message.h"
#include "qpid/log/Statement.h"
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
    helper(address),
    receiver(pn_receiver(session, name.c_str())),
    capacity(0) {}
ReceiverContext::~ReceiverContext()
{
    //pn_link_free(receiver);
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
    pn_link_close(receiver);
}

const std::string& ReceiverContext::getName() const
{
    return name;
}

const std::string& ReceiverContext::getSource() const
{
    return address.getName();
}
void ReceiverContext::verify()
{
    pn_terminus_t* source = pn_link_remote_source(receiver);
    if (!pn_terminus_get_address(source)) {
        std::string msg("No such source : ");
        msg += getSource();
        QPID_LOG(debug, msg);
        throw qpid::messaging::NotFound(msg);
    } else if (AddressImpl::isTemporary(address)) {
        address.setName(pn_terminus_get_address(source));
        QPID_LOG(debug, "Dynamic source name set to " << address.getName());
    }
    helper.checkAssertion(source, AddressHelper::FOR_RECEIVER);
}
void ReceiverContext::configure()
{
    configure(pn_link_source(receiver));
}
void ReceiverContext::configure(pn_terminus_t* source)
{
    helper.configure(receiver, source, AddressHelper::FOR_RECEIVER);
    std::string option;
    if (helper.getLinkTarget(option)) {
        pn_terminus_set_address(pn_link_target(receiver), option.c_str());
    } else {
        pn_terminus_set_address(pn_link_target(receiver), pn_terminus_get_address(pn_link_source(receiver)));
    }
}

Address ReceiverContext::getAddress() const
{
    return address;
}

bool ReceiverContext::isClosed() const
{
    return false;//TODO
}

void ReceiverContext::reset(pn_session_t* session)
{
    receiver = pn_receiver(session, name.c_str());
    configure();
}

}}} // namespace qpid::messaging::amqp
