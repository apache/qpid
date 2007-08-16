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

#include "ReceivedContent.h"
#include "qpid/framing/all_method_bodies.h"

using qpid::client::ReceivedContent;
using namespace qpid::framing;
using namespace boost;

ReceivedContent::ReceivedContent(const SequenceNumber& _id) : id(_id) {}

void ReceivedContent::append(AMQBody* part)
{
    parts.push_back(AMQFrame(ProtocolVersion(), 0, part));
}

bool ReceivedContent::isComplete() const
{
    if (parts.empty()) {
         return false;
    } else if (isA<BasicDeliverBody>() || isA<BasicGetOkBody>()) {
        const AMQHeaderBody* headers(getHeaders());
        return headers && headers->getContentSize() == getContentSize();
    } else if (isA<MessageTransferBody>()) {
        //no longer support references, headers and data are still method fields
        return true;
    } else {
        throw Exception("Unknown content class");
    }
}


const AMQMethodBody* ReceivedContent::getMethod() const
{
    return parts.empty() ? 0 : dynamic_cast<const AMQMethodBody*>(parts[0].getBody());
}

const AMQHeaderBody* ReceivedContent::getHeaders() const
{
    return parts.size() < 2 ? 0 : dynamic_cast<const AMQHeaderBody*>(parts[1].getBody());
}

uint64_t ReceivedContent::getContentSize() const
{
    if (isA<BasicDeliverBody>() || isA<BasicGetOkBody>()) {
        uint64_t size(0);
        for (uint i = 2; i < parts.size(); i++) {
            size += parts[i].getBody()->size();
        }
        return size;
    } else if (isA<MessageTransferBody>()) {
        return as<MessageTransferBody>()->getBody().getValue().size();
    } else {
        throw Exception("Unknown content class");
    }    
}

std::string ReceivedContent::getContent() const
{
    if (isA<BasicDeliverBody>() || isA<BasicGetOkBody>()) {
        string data;
        for (uint i = 2; i < parts.size(); i++) {
            data += static_cast<const AMQContentBody*>(parts[i].getBody())->getData();
        }
        return data;
    } else if (isA<MessageTransferBody>()) {
        return as<MessageTransferBody>()->getBody().getValue();
    } else {
        throw Exception("Unknown content class");
    }
}

void ReceivedContent::populate(Message& msg)
{
    if (!isComplete()) throw Exception("Incomplete message");

    if (isA<BasicDeliverBody>() || isA<BasicGetOkBody>()) {
        const BasicHeaderProperties* properties = dynamic_cast<const BasicHeaderProperties*>(getHeaders()->getProperties());
        BasicHeaderProperties::copy<Message, BasicHeaderProperties>(msg, *properties);
        msg.setData(getContent());
    } else if (isA<MessageTransferBody>()) {
        throw Exception("Transfer not yet supported");
    } else {
        throw Exception("Unknown content class");
    }    
}
