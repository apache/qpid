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
#include "SenderContext.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/messaging/Message.h"
#include "qpid/log/Statement.h"
extern "C" {
#include "proton/engine.h"
#include "proton/message.h"
}

namespace qpid {
namespace messaging {
namespace amqp {
//TODO: proper conversion to wide string for address
SenderContext::SenderContext(pn_session_t* session, const std::string& n, const std::string& t)
  : name(n),
    target(t.begin(), t.end()),
    sender(pn_sender(session, target.c_str())), capacity(1000) {}

SenderContext::~SenderContext()
{
    pn_link_destroy(sender);
}

void SenderContext::close()
{

}

void SenderContext::setCapacity(uint32_t c)
{
    if (c < deliveries.size()) throw qpid::messaging::SenderError("Desired capacity is less than unsettled message count!");
    capacity = c;
}

uint32_t SenderContext::getCapacity()
{
    return capacity;
}

uint32_t SenderContext::getUnsettled()
{
    return processUnsettled();
}

const std::string& SenderContext::getName() const
{
    return name;
}

const std::wstring& SenderContext::getTarget() const
{
    return target;
}

SenderContext::Delivery* SenderContext::send(const qpid::messaging::Message& message)
{
    if (processUnsettled() < capacity) {
        deliveries.push_back(Delivery(nextId++, message));
        Delivery& delivery = deliveries.back();
        delivery.send(sender);
        return &delivery;
    } else {
        return 0;
    }
}

uint32_t SenderContext::processUnsettled()
{
    //remove accepted messages from front of deque
    while (!deliveries.empty() && deliveries.front().accepted()) {
        deliveries.pop_front();
    }
    return deliveries.size();
}

SenderContext::Delivery::Delivery(int32_t i, const qpid::messaging::Message& msg) :
    id(i), token(0)
{
    data.resize(msg.getContentSize() + 15);
    //TODO: full message encoding including headers etc
    pn_message_data(&data[0], data.size(), msg.getContentPtr(), msg.getContentSize());
}
void SenderContext::Delivery::send(pn_link_t* sender)
{
    pn_delivery_tag_t tag;
    tag.size = sizeof(int32_t);
    tag.bytes = reinterpret_cast<char*>(&id);
    token = pn_delivery(sender, tag);
    pn_send(sender, &data[0], data.size());
}
bool SenderContext::Delivery::accepted()
{
    return pn_remote_disp(token) == PN_ACCEPTED;
}

}}} // namespace qpid::messaging::amqp
