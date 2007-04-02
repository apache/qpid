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

#include <boost/format.hpp>

#include "Requester.h"
#include "../QpidError.h"

namespace qpid {
namespace framing {

Requester::Requester() : lastId(0), responseMark(0) {}

void Requester::sending(AMQRequestBody::Data& request) {
    request.requestId = ++lastId;
    request.responseMark = responseMark;
}

void Requester::processed(const AMQResponseBody::Data& response) {
    responseMark = response.responseId;
    firstAckRequest = response.requestId;
    lastAckRequest = firstAckRequest + response.batchOffset;
}

}} // namespace qpid::framing
