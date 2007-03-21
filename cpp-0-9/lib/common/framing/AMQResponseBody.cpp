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

#include "AMQFrame.h"
#include "AMQResponseBody.h"
#include "AMQP_MethodVersionMap.h"

namespace qpid {
namespace framing {

void AMQResponseBody::Data::encode(Buffer& buffer) const {
    buffer.putLongLong(responseId);
    buffer.putLongLong(requestId);
    buffer.putLong(batchOffset);
}

void AMQResponseBody::Data::decode(Buffer& buffer) {
    responseId = buffer.getLongLong();
    requestId = buffer.getLongLong();
    batchOffset = buffer.getLong();
}

void AMQResponseBody::encode(Buffer& buffer) const {
    data.encode(buffer);
    encodeId(buffer);
    encodeContent(buffer);
}

AMQResponseBody::shared_ptr AMQResponseBody::create(
    AMQP_MethodVersionMap& versionMap, ProtocolVersion version,
    Buffer& buffer)
{
    ClassMethodId id;
    Data data;
    data.decode(buffer);
    id.decode(buffer);
    AMQResponseBody* body = dynamic_cast<AMQResponseBody*>(
        versionMap.createMethodBody(
        id.classId, id.methodId, version.getMajor(), version.getMinor()));
    assert(body);
    body->data = data;
    return AMQResponseBody::shared_ptr(body);
}

void AMQResponseBody::printPrefix(std::ostream& out) const {
    out << "response(id=" << data.responseId << ",request=" << data.requestId
        << ",batch=" << data.batchOffset << "): ";
}

}} // namespace qpid::framing
