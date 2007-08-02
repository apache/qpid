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

using qpid::client::ReceivedContent;
using namespace qpid::framing;
using namespace boost;

ReceivedContent::ReceivedContent(const SequenceNumber& _id) : id(_id) {}

void ReceivedContent::append(AMQBody::shared_ptr part)
{
    parts.push_back(part);
}

bool ReceivedContent::isComplete() const
{
    if (parts.empty()) {
         return false;
    } else if (isA<BasicDeliverBody>() || isA<BasicGetOkBody>()) {
        AMQHeaderBody::shared_ptr headers(getHeaders());
        return headers && headers->getContentSize() == getContentSize();
    } else if (isA<MessageTransferBody>()) {
        //no longer support references, headers and data are still method fields
        return true;
    } else {
        throw Exception("Unknown content class");
    }
}


AMQMethodBody::shared_ptr ReceivedContent::getMethod() const
{
    return parts.empty() ? AMQMethodBody::shared_ptr() : dynamic_pointer_cast<AMQMethodBody>(parts[0]);
}

AMQHeaderBody::shared_ptr ReceivedContent::getHeaders() const
{
    return parts.size() < 2 ? AMQHeaderBody::shared_ptr() : dynamic_pointer_cast<AMQHeaderBody>(parts[1]);
}

uint64_t ReceivedContent::getContentSize() const
{
    if (isA<BasicDeliverBody>() || isA<BasicGetOkBody>()) {
        uint64_t size(0);
        for (uint i = 2; i < parts.size(); i++) {
            size += parts[i]->size();
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
            data += dynamic_pointer_cast<AMQContentBody>(parts[i])->getData();
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
        BasicHeaderProperties* properties = dynamic_cast<BasicHeaderProperties*>(getHeaders()->getProperties());
        BasicHeaderProperties::copy<Message, BasicHeaderProperties>(msg, *properties);
        msg.setData(getContent());
    } else if (isA<MessageTransferBody>()) {
        throw Exception("Transfer not yet supported");
    } else {
        throw Exception("Unknown content class");
    }    
}
