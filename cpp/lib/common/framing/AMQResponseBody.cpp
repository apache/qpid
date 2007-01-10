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

#include "AMQResponseBody.h"
#include "AMQP_MethodVersionMap.h"

namespace qpid {
namespace framing {

AMQResponseBody::AMQResponseBody(AMQP_MethodVersionMap& vm, ProtocolVersion v)
    : versionMap(vm), version(v) {}

AMQResponseBody::AMQResponseBody(
    AMQP_MethodVersionMap& vm, ProtocolVersion v,
    u_int64_t respId, u_int64_t reqId, u_int32_t batch,
    AMQMethodBody::shared_ptr m
) : versionMap(vm), version(v), 
    responseId(respId), requestId(reqId), batchOffset(batch), method(m)
{}

void
AMQResponseBody::encode(Buffer& buffer) const {
    assert(method.get());
    buffer.putLongLong(responseId);
    buffer.putLongLong(requestId);
    buffer.putLong(batchOffset);
    method->encode(buffer);
}

void
AMQResponseBody::decode(Buffer& buffer, u_int32_t /*size*/) {
    responseId = buffer.getLongLong();
    requestId = buffer.getLongLong();
    batchOffset = buffer.getLong();
    method = AMQMethodBody::create(versionMap, version, buffer);
}

void
AMQResponseBody::print(std::ostream& out) const
{
    out << "response(" << size() << " bytes) " << *method;
}

}} // namespace qpid::framing
