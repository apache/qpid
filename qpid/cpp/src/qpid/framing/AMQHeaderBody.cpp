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
#include "AMQHeaderBody.h"
#include "qpid/Exception.h"
#include "qpid/log/Statement.h"

qpid::framing::AMQHeaderBody::AMQHeaderBody() {}

qpid::framing::AMQHeaderBody::~AMQHeaderBody() {}

uint32_t qpid::framing::AMQHeaderBody::size() const{
    CalculateSize visitor;
    for_each(properties.begin(), properties.end(), boost::apply_visitor(visitor));
    return visitor.totalSize();
}

void qpid::framing::AMQHeaderBody::encode(Buffer& buffer) const {
    Encode visitor(buffer);
    for_each(properties.begin(), properties.end(), boost::apply_visitor(visitor));
}

void qpid::framing::AMQHeaderBody::decode(Buffer& buffer, uint32_t size){
    uint32_t limit = buffer.available() - size;
    while (buffer.available() > limit + 2) {
        uint32_t len = buffer.getLong();
        uint16_t type = buffer.getShort();
        //The following switch could be generated as the number of options increases:
        switch(type) {
        case BasicHeaderProperties::TYPE: 
            decode(BasicHeaderProperties(), buffer, len - 2);
            break;
        case MessageProperties::TYPE:
            decode(MessageProperties(), buffer, len - 2);
            break;
        case DeliveryProperties::TYPE:
            decode(DeliveryProperties(), buffer, len - 2);
            break;
        default:
            //TODO: should just skip over them keeping them for later dispatch as is
            throw Exception(QPID_MSG("Unexpected property type: " << type));
        }
    }
}

uint64_t qpid::framing::AMQHeaderBody::getContentLength() const
{    
    const MessageProperties* mProps = get<MessageProperties>();
    if (mProps) {
        return mProps->getContentLength();
    }
    const BasicHeaderProperties* bProps = get<BasicHeaderProperties>();
    if (bProps) {
        return bProps->getContentLength();
    }
    return 0;
}

void qpid::framing::AMQHeaderBody::print(std::ostream& out) const
{
    out << "header (" << size() << " bytes)";
    out << "; properties={";
    Print visitor(out);
    for_each(properties.begin(), properties.end(), boost::apply_visitor(visitor));
    out << "}";
}
