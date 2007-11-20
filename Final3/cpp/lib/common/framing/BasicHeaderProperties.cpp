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
#include <BasicHeaderProperties.h>

//TODO: This could be easily generated from the spec

qpid::framing::BasicHeaderProperties::BasicHeaderProperties() : deliveryMode(0), priority(0), timestamp(0){}
qpid::framing::BasicHeaderProperties::~BasicHeaderProperties(){}

u_int32_t qpid::framing::BasicHeaderProperties::size() const{
    u_int32_t bytes = 2;//flags
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

    return bytes;
}

void qpid::framing::BasicHeaderProperties::encode(qpid::framing::Buffer& buffer) const{
    u_int16_t flags = getFlags();
    buffer.putShort(flags);
    
    if(contentType.length() > 0) buffer.putShortString(contentType);
    if(contentEncoding.length() > 0) buffer.putShortString(contentEncoding);
    if(headers.count() > 0) buffer.putFieldTable(headers);
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
}

void qpid::framing::BasicHeaderProperties::decode(qpid::framing::Buffer& buffer, u_int32_t /*size*/){
    u_int16_t flags = buffer.getShort();
    if(flags & (1 << 15)) buffer.getShortString(contentType);
    if(flags & (1 << 14)) buffer.getShortString(contentEncoding);
    if(flags & (1 << 13)) buffer.getFieldTable(headers);
    if(flags & (1 << 12)) deliveryMode = buffer.getOctet();
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
}

u_int16_t qpid::framing::BasicHeaderProperties::getFlags() const{
    u_int16_t flags(0);
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
    return flags;
}
