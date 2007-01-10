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
#include <AMQMethodBody.h>
#include <QpidError.h>
#include "AMQP_MethodVersionMap.h"

namespace qpid {
namespace framing {

void AMQMethodBody::encode(Buffer& buffer) const{
    buffer.putShort(amqpClassId());
    buffer.putShort(amqpMethodId());
    encodeContent(buffer);
}

void AMQMethodBody::decode(Buffer& buffer, u_int32_t /*size*/){
    decodeContent(buffer);
}

bool AMQMethodBody::match(AMQMethodBody* other) const{
    return other != 0 && other->amqpClassId() == amqpClassId() && other->amqpMethodId() == amqpMethodId();
}

void AMQMethodBody::invoke(AMQP_ServerOperations& /*target*/, u_int16_t /*channel*/){
    THROW_QPID_ERROR(PROTOCOL_ERROR, "Method not supported by AMQP Server.");
}



AMQMethodBody::shared_ptr AMQMethodBody::create(
    AMQP_MethodVersionMap& versionMap, ProtocolVersion version,
    Buffer& buffer)
{
    u_int16_t classId = buffer.getShort();
    u_int16_t methodId = buffer.getShort();
    return AMQMethodBody::shared_ptr(
        versionMap.createMethodBody(
            classId, methodId, version.getMajor(), version.getMinor()));
}

}} // namespace qpid::framing
