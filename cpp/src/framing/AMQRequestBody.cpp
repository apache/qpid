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
#include "../gen/AMQP_MethodVersionMap.h"

namespace qpid {
namespace framing {

void AMQRequestBody::Data::encode(Buffer& buffer) const {
    buffer.putLongLong(requestId);
    buffer.putLongLong(responseMark);
    buffer.putLong(0);          // Reserved long in spec.
}
    
void AMQRequestBody::Data::decode(Buffer& buffer) {
    requestId = buffer.getLongLong();
    responseMark = buffer.getLongLong();
    buffer.getLong();           // Ignore reserved long.
}

void AMQRequestBody::encode(Buffer& buffer) const {
    data.encode(buffer);
    encodeId(buffer);
    encodeContent(buffer);
}

AMQRequestBody::shared_ptr    
AMQRequestBody::create(
    AMQP_MethodVersionMap& versionMap, ProtocolVersion version,
    Buffer& buffer)
{
    ClassMethodId id;
    Data data;
    data.decode(buffer);
    id.decode(buffer);
    AMQRequestBody* body = dynamic_cast<AMQRequestBody*>(
        versionMap.createMethodBody(
            id.classId, id.methodId, version.getMajor(), version.getMinor()));
    assert(body);
    body->data = data;
    return AMQRequestBody::shared_ptr(body);
}

void AMQRequestBody::printPrefix(std::ostream& out) const {
    out << "request(id=" << data.requestId << ",mark="
        << data.responseMark << "): ";
}

}} // namespace qpid::framing

