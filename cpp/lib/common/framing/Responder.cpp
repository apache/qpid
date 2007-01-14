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

#include "Responder.h"
#include "QpidError.h"

namespace qpid {
namespace framing {

Responder::Responder() : lastId(0), responseMark(0) {}

void Responder::received(const AMQRequestBody::Data& request) {
    if (request.responseMark < responseMark || request.responseMark > lastId)
        THROW_QPID_ERROR(PROTOCOL_ERROR, "Invalid resposne mark");
    responseMark = request.responseMark;
}

void Responder::sending(AMQResponseBody::Data& response, RequestId toRequest) {
    response.responseId = ++lastId;
    response.requestId = toRequest;
    response.batchOffset = 0;
}

}} // namespace qpid::framing

