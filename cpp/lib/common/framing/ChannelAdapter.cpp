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

#include "ChannelAdapter.h"
#include "AMQFrame.h"

namespace qpid {
namespace framing {

void ChannelAdapter::send(AMQFrame* frame) {
    AMQBody::shared_ptr body = frame->getBody();
    switch (body->type()) {
      case REQUEST_BODY: {
          AMQRequestBody::shared_ptr request =
              boost::shared_polymorphic_downcast<AMQRequestBody>(body);
          requester.sending(request->getData());
          break;
      }
      case RESPONSE_BODY: {
          AMQResponseBody::shared_ptr response =
              boost::shared_polymorphic_downcast<AMQResponseBody>(body);
          responder.sending(response->getData());
          break;
      }
    }
    out.send(frame);
}

void ChannelAdapter::handleRequest(AMQRequestBody::shared_ptr request) {
    responder.received(request->getData());
    MethodContext context(id, &out, request->getRequestId());
    handleMethodInContext(request, context);
}

void ChannelAdapter::handleResponse(AMQResponseBody::shared_ptr response) {
    handleMethod(response);
    requester.processed(response->getData());
}

void ChannelAdapter::handleMethod(AMQMethodBody::shared_ptr method) {
    MethodContext context(id, this);
    handleMethodInContext(method, context);
}

void ChannelAdapter::assertChannelZero(u_int16_t id) {
    if (id != 0)
        throw ConnectionException(504, "Invalid channel id, not 0");
}

void ChannelAdapter::assertChannelNonZero(u_int16_t id) {
    if (id == 0)
        throw ConnectionException(504, "Invalid channel id 0");
}

}} // namespace qpid::framing
