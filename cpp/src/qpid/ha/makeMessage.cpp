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
#include "makeMessage.h"
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/MessageTransferBody.h"

namespace qpid {
namespace ha {

broker::Message makeMessage(const framing::Buffer& buffer,
                            const std::string& destination)
{
    using namespace framing;
    using broker::amqp_0_10::MessageTransfer;

    boost::intrusive_ptr<MessageTransfer> transfer(
        new qpid::broker::amqp_0_10::MessageTransfer());
    AMQFrame method((MessageTransferBody(ProtocolVersion(), destination, 0, 0)));
    AMQFrame header((AMQHeaderBody()));
    AMQFrame content((AMQContentBody()));
    // AMQContentBody::decode is missing a const declaration, so cast it here.
    content.castBody<AMQContentBody>()->decode(
        const_cast<Buffer&>(buffer), buffer.getSize());
    header.setBof(false);
    header.setEof(false);
    header.setBos(true);
    header.setEos(true);
    content.setBof(false);
    content.setEof(true);
    content.setBos(true);
    content.setEos(true);
    transfer->getFrames().append(method);
    transfer->getFrames().append(header);
    transfer->getFrames().append(content);
    return broker::Message(transfer, 0);
}

broker::Message makeMessage(const std::string& content, const std::string& destination) {
    framing::Buffer buffer(const_cast<char*>(&content[0]), content.size());
    return makeMessage(buffer, destination);
}

}} // namespace qpid::ha
