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
#include <AMQHeaderBody.h>
#include <QpidError.h>
#include <BasicHeaderProperties.h>

qpid::framing::AMQHeaderBody::AMQHeaderBody(int classId) : weight(0), contentSize(0){
    createProperties(classId);
}

qpid::framing::AMQHeaderBody::AMQHeaderBody() : properties(0), weight(0), contentSize(0){
}

qpid::framing::AMQHeaderBody::~AMQHeaderBody(){ 
    delete properties;
}

u_int32_t qpid::framing::AMQHeaderBody::size() const{
    return 12 + properties->size();
}

void qpid::framing::AMQHeaderBody::encode(Buffer& buffer) const {
    buffer.putShort(properties->classId());
    buffer.putShort(weight);
    buffer.putLongLong(contentSize);
    properties->encode(buffer);
}

void qpid::framing::AMQHeaderBody::decode(Buffer& buffer, u_int32_t bufSize){
    u_int16_t classId = buffer.getShort();
    weight = buffer.getShort();
    contentSize = buffer.getLongLong();
    createProperties(classId);
    properties->decode(buffer, bufSize - 12);
}

void qpid::framing::AMQHeaderBody::createProperties(int classId){
    switch(classId){
    case BASIC:
	properties = new qpid::framing::BasicHeaderProperties();
	break;
    default:
	THROW_QPID_ERROR(FRAMING_ERROR, "Unknown header class");
    }
}

void qpid::framing::AMQHeaderBody::print(std::ostream& out) const
{
    out << "header (" << size() << " bytes)"  << " content_size=" << getContentSize();
    const BasicHeaderProperties* props =
        dynamic_cast<const BasicHeaderProperties*>(getProperties());
    if (props) {
        out << ", message_id=" << props->getMessageId(); 
        out << ", delivery_mode=" << (int) props->getDeliveryMode(); 
        out << ", headers=" << const_cast<BasicHeaderProperties*>(props)->getHeaders();
    }
}
