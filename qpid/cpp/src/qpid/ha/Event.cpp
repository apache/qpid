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
#include "Event.h"
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/DeliveryProperties.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace ha {

using namespace std;
using namespace framing;
using namespace broker::amqp_0_10;

namespace {
const string QPID_HA(QPID_HA_PREFIX);
}

bool isEventKey(const std::string& key) {
    const std::string& prefix = QPID_HA;
    bool ret = key.size() > prefix.size() && key.compare(0, prefix.size(), prefix) == 0;
    return ret;
}

const string DequeueEvent::KEY(QPID_HA+"de");
const string IdEvent::KEY(QPID_HA+"id");

broker::Message makeMessage(
    const string& data, const string& destination, const string& routingKey)
{
    boost::intrusive_ptr<MessageTransfer> transfer(new MessageTransfer());
    AMQFrame method((MessageTransferBody(ProtocolVersion(), destination, 0, 0)));
    method.setBof(true);
    method.setEof(false);
    method.setBos(true);
    method.setEos(true);
    AMQFrame header((AMQHeaderBody()));
    header.setBof(false);
    header.setEof(false);
    header.setBos(true);
    header.setEos(true);
    AMQFrame content((AMQContentBody()));
    content.setBof(false);
    content.setEof(true);
    content.setBos(true);
    content.setEos(true);
    Buffer buffer(const_cast<char*>(&data[0]), data.size());
    content.castBody<AMQContentBody>()->decode(
        const_cast<Buffer&>(buffer), buffer.getSize());
    transfer->getFrames().append(method);
    transfer->getFrames().append(header);
    transfer->getFrames().append(content);
    transfer->getFrames().getHeaders()->
        get<DeliveryProperties>(true)->setRoutingKey(routingKey);
    return broker::Message(transfer, 0);
}

}} // namespace qpid::ha
