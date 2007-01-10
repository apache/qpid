/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "AMQRequestBody.h"
#include "AMQP_MethodVersionMap.h"

namespace qpid {
namespace framing {

AMQRequestBody::AMQRequestBody(AMQP_MethodVersionMap& vm, ProtocolVersion v)
    : versionMap(vm), version(v) {}

AMQRequestBody::AMQRequestBody(
    AMQP_MethodVersionMap& vm, ProtocolVersion v,
    u_int64_t reqId, u_int64_t respMark,
    AMQMethodBody::shared_ptr m
) : versionMap(vm), version(v), 
    requestId(reqId), responseMark(respMark), method(m)
{}
    

void
AMQRequestBody::encode(Buffer& buffer) const {
    assert(method.get());
    buffer.putLongLong(requestId);
    buffer.putLongLong(responseMark);
    method->encode(buffer);
}

void
AMQRequestBody::decode(Buffer& buffer, u_int32_t /*size*/) {
    requestId = buffer.getLongLong();
    responseMark = buffer.getLongLong();
    method = AMQMethodBody::create(versionMap, version, buffer);
}

void
AMQRequestBody::print(std::ostream& out) const
{
    out << "request(" << size() << " bytes) " << *method;
}

}} // namespace qpid::framing
