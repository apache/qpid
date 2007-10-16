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
#include "BasicHeaderProperties.h"

//TODO: This could be easily generated from the spec

qpid::framing::BasicHeaderProperties::BasicHeaderProperties() : deliveryMode(DeliveryMode(0)), 
                                                                priority(0), 
                                                                timestamp(0), 
                                                                contentLength(0){}
qpid::framing::BasicHeaderProperties::~BasicHeaderProperties(){}

uint32_t qpid::framing::BasicHeaderProperties::size() const{
    uint32_t bytes = 2;//flags
    if(contentType.length() > 0) bytes += contentType.length() + 1;
    if(contentEncoding.length() > 0) bytes += contentEncoding.length() + 1;
    if(headers.count() > 0) bytes += headers.size();
    if(deliveryMode != 0) bytes += 1;
    if(priority != 0) bytes += 1;
    if(correlationId.length() > 0) bytes += correlationId.length() + 1;
    if(replyTo.length() > 0) bytes += replyTo.length() + 1;
    if(expiration.length() > 0) bytes += expiration.length() + 1;
    if(messageId.length() > 0) bytes += messageId.length() + 1;
    if(timestamp != 0) bytes += 8;
    if(type.length() > 0) bytes += type.length() + 1;
    if(userId.length() > 0) bytes += userId.length() + 1;
    if(appId.length() > 0) bytes += appId.length() + 1;
    if(clusterId.length() > 0) bytes += clusterId.length() + 1;
    if(contentLength != 0) bytes += 8;

    return bytes;
}

void qpid::framing::BasicHeaderProperties::encode(qpid::framing::Buffer& buffer) const{
    uint16_t flags = getFlags();
    buffer.putShort(flags);
    
    if(contentType.length() > 0) buffer.putShortString(contentType);
    if(contentEncoding.length() > 0) buffer.putShortString(contentEncoding);
    if(headers.count() > 0) buffer.put(headers);
    if(deliveryMode != 0) buffer.putOctet(deliveryMode);
    if(priority != 0) buffer.putOctet(priority);
    if(correlationId.length() > 0) buffer.putShortString(correlationId);
    if(replyTo.length() > 0) buffer.putShortString(replyTo);
    if(expiration.length() > 0) buffer.putShortString(expiration);
    if(messageId.length() > 0) buffer.putShortString(messageId);
    if(timestamp != 0) buffer.putLongLong(timestamp);;
    if(type.length() > 0) buffer.putShortString(type);
    if(userId.length() > 0) buffer.putShortString(userId);
    if(appId.length() > 0) buffer.putShortString(appId);
    if(clusterId.length() > 0) buffer.putShortString(clusterId);    
    if(contentLength != 0) buffer.putLongLong(contentLength);
}

void qpid::framing::BasicHeaderProperties::decode(qpid::framing::Buffer& buffer, uint32_t /*size*/){
    uint16_t flags = buffer.getShort();
    if(flags & (1 << 15)) buffer.getShortString(contentType);
    if(flags & (1 << 14)) buffer.getShortString(contentEncoding);
    if(flags & (1 << 13)) buffer.get(headers);
    if(flags & (1 << 12)) deliveryMode = DeliveryMode(buffer.getOctet());
    if(flags & (1 << 11)) priority = buffer.getOctet();
    if(flags & (1 << 10)) buffer.getShortString(correlationId);
    if(flags & (1 <<  9)) buffer.getShortString(replyTo);
    if(flags & (1 <<  8)) buffer.getShortString(expiration);
    if(flags & (1 <<  7)) buffer.getShortString(messageId);
    if(flags & (1 <<  6)) timestamp = buffer.getLongLong();
    if(flags & (1 <<  5)) buffer.getShortString(type);
    if(flags & (1 <<  4)) buffer.getShortString(userId);
    if(flags & (1 <<  3)) buffer.getShortString(appId);
    if(flags & (1 <<  2)) buffer.getShortString(clusterId);    
    if(flags & (1 <<  1)) contentLength = buffer.getLongLong();    
}

uint16_t qpid::framing::BasicHeaderProperties::getFlags() const{
    uint16_t flags(0);
    if(contentType.length() > 0)     flags |= (1 << 15);
    if(contentEncoding.length() > 0) flags |= (1 << 14);
    if(headers.count() > 0)          flags |= (1 << 13);
    if(deliveryMode != 0)            flags |= (1 << 12);
    if(priority != 0)                flags |= (1 << 11);
    if(correlationId.length() > 0)   flags |= (1 << 10); 
    if(replyTo.length() > 0)         flags |= (1 <<  9);
    if(expiration.length() > 0)      flags |= (1 <<  8);
    if(messageId.length() > 0)       flags |= (1 <<  7);
    if(timestamp != 0)               flags |= (1 <<  6);
    if(type.length() > 0)            flags |= (1 <<  5);
    if(userId.length() > 0)          flags |= (1 <<  4);
    if(appId.length() > 0)           flags |= (1 <<  3);
    if(clusterId.length() > 0)       flags |= (1 <<  2);
    if(contentLength != 0)           flags |= (1 <<  1);
    return flags;
}

namespace qpid{
namespace framing{

    std::ostream& operator<<(std::ostream& out, const BasicHeaderProperties& props) 
    {
        if(props.contentType.length() > 0) out << "contentType=" << props.contentType << ";";
        if(props.contentEncoding.length() > 0) out << "contentEncoding=" << props.contentEncoding << ";";
        if(props.headers.count() > 0) out << "headers=" << props.headers << ";";
        if(props.deliveryMode != 0) out << "deliveryMode=" << props.deliveryMode << ";";
        if(props.priority != 0) out << "priority=" << props.priority << ";";
        if(props.correlationId.length() > 0) out << "correlationId=" << props.correlationId << ";";
        if(props.replyTo.length() > 0) out << "replyTo=" << props.replyTo << ";";
        if(props.expiration.length() > 0) out << "expiration=" << props.expiration << ";";
        if(props.messageId.length() > 0) out << "messageId=" << props.messageId << ";";
        if(props.timestamp != 0) out << "timestamp=" << props.timestamp << ";";
        if(props.type.length() > 0) out << "type=" << props.type << ";";
        if(props.userId.length() > 0) out << "userId=" << props.userId << ";";
        if(props.appId.length() > 0) out << "appId=" << props.appId << ";";
        if(props.clusterId.length() > 0) out << "clusterId=" << props.clusterId << ";";    
        if(props.contentLength != 0) out << "contentLength=" << props.contentLength << ";";

        return out;
    }

}}
