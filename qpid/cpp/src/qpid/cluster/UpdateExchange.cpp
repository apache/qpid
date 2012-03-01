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
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/broker/Message.h"
#include "UpdateExchange.h"

namespace qpid {
namespace cluster {

using framing::MessageTransferBody;
using framing::DeliveryProperties;
using framing::MessageProperties;
using framing::FieldTable;

UpdateExchange::UpdateExchange(management::Manageable* parent)
    : broker::Exchange(UpdateClient::UPDATE, parent),
      broker::FanOutExchange(UpdateClient::UPDATE, parent) {}


void UpdateExchange::setProperties(const boost::intrusive_ptr<broker::Message>& msg) {
    // Copy exchange name to destination property.
    MessageTransferBody* transfer = msg->getMethod<MessageTransferBody>();
    assert(transfer);
    const DeliveryProperties* props = msg->getProperties<DeliveryProperties>();
    assert(props);
    if (props->hasExchange())
        transfer->setDestination(props->getExchange());
    else
        transfer->clearDestinationFlag();

    // Copy expiration from x-property if present.
    if (msg->hasProperties<MessageProperties>()) {
        const MessageProperties* mprops = msg->getProperties<MessageProperties>();
        if (mprops->hasApplicationHeaders()) {
            const FieldTable& headers = mprops->getApplicationHeaders();
            if (headers.isSet(UpdateClient::X_QPID_EXPIRATION)) {
                msg->setExpiration(
                    sys::AbsTime(sys::EPOCH, headers.getAsInt64(UpdateClient::X_QPID_EXPIRATION)));
                msg->removeCustomProperty(UpdateClient::X_QPID_EXPIRATION);
                // Erase props/headers that were added by the UpdateClient
                if (headers.isSet(UpdateClient::X_QPID_NO_MESSAGE_PROPS))
                    msg->eraseProperties<MessageProperties>();
                else if (headers.isSet(UpdateClient::X_QPID_NO_HEADERS))
                    msg->clearApplicationHeadersFlag();
            }
        }
    }
}
}} // namespace qpid::cluster
