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

void ChannelAdapter::init(
    ChannelId i, OutputHandler& o, const ProtocolVersion& v)
{
    assertChannelNotOpen();
    id = i;
    out = &o;
    version = v;
    context = MethodContext(id, this);
}

void ChannelAdapter::send(AMQFrame* frame) {
    assertChannelOpen();
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
    out->send(frame);
}

void ChannelAdapter::send(AMQBody::shared_ptr body) {
    send(new AMQFrame(getVersion(), getId(), body));
}

void ChannelAdapter::handleRequest(AMQRequestBody::shared_ptr request) {
    assertMethodOk(*request);
    responder.received(request->getData());
    context =MethodContext(id, this, request->getRequestId());
    handleMethodInContext(request, context);
}

void ChannelAdapter::handleResponse(AMQResponseBody::shared_ptr response) {
    assertMethodOk(*response);
    // TODO aconway 2007-01-30: Consider a response handled on receipt.
    // Review - any cases where this is not the case?
    requester.processed(response->getData());
    handleMethod(response);
}

void ChannelAdapter::handleMethod(AMQMethodBody::shared_ptr method) {
    assertMethodOk(*method);
    context = MethodContext(id, this);
    handleMethodInContext(method, context);
}

void ChannelAdapter::assertMethodOk(AMQMethodBody& /*method*/) const {
    // No connection methods allowed on a non-zero channel
    // Subclass ChannelZero overrides for 0 channels.
    // FIXME aconway 2007-01-25: with ctors
//     assertChannelOpen();
//     if (method.amqpClassId() == ConnectionOpenBody::CLASS_ID)
//         throw ConnectionException(
//             504, "Connection method on non-0 channel.");
}

void ChannelAdapter::assertChannelOpen() const {
    // FIXME aconway 2007-01-25: with ctors
//     if (!isOpen())
//         throw ConnectionException(504, "Channel is not open");
}

void ChannelAdapter::assertChannelNotOpen() const {
    // FIXME aconway 2007-01-25: with ctors
//     if (isOpen())
//         throw ConnectionException(504, "Channel is already open");
}

}} // namespace qpid::framing
