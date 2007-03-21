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

#include "ChannelAdapter.h"
#include "AMQFrame.h"
#include "Exception.h"

using boost::format;

namespace qpid {
namespace framing {

void ChannelAdapter::init(
    ChannelId i, OutputHandler& o, ProtocolVersion v)
{
    assertChannelNotOpen();
    id = i;
    out = &o;
    version = v;
}

RequestId ChannelAdapter::send(AMQBody::shared_ptr body) {
    RequestId result = 0;
    assertChannelOpen();
    switch (body->type()) {
      case REQUEST_BODY: {
          AMQRequestBody::shared_ptr request =
              boost::shared_polymorphic_downcast<AMQRequestBody>(body);
          requester.sending(request->getData());
          result = request->getData().requestId;
          break;
      }
      case RESPONSE_BODY: {
          AMQResponseBody::shared_ptr response =
              boost::shared_polymorphic_downcast<AMQResponseBody>(body);
          responder.sending(response->getData());
          break;
      }
    }
    out->send(new AMQFrame(getVersion(), getId(), body));
    return result;
}

void ChannelAdapter::handleRequest(AMQRequestBody::shared_ptr request) {
    assertMethodOk(*request);
    AMQRequestBody::Data& requestData = request->getData();
    responder.received(requestData);
    handleMethodInContext(request, MethodContext(this, request));
}

void ChannelAdapter::handleResponse(AMQResponseBody::shared_ptr response) {
    assertMethodOk(*response);
    // TODO aconway 2007-01-30: Consider a response handled on receipt.
    // Review - any cases where this is not the case?
    AMQResponseBody::Data& responseData = response->getData();
    requester.processed(responseData);
    handleMethod(response);
}

void ChannelAdapter::handleMethod(AMQMethodBody::shared_ptr method) {
    assertMethodOk(*method);
    handleMethodInContext(method, MethodContext(this, method));
}

void ChannelAdapter::assertMethodOk(AMQMethodBody& method) const {
    if (getId() != 0 && method.amqpClassId() == ConnectionOpenBody::CLASS_ID)
        throw ConnectionException(
            504, format("Connection method on non-0 channel %d.")%getId());
}

void ChannelAdapter::assertChannelOpen() const {
    if (getId() != 0 && !isOpen())
        throw ConnectionException(
            504, format("Channel %d is not open.")%getId());
}

void ChannelAdapter::assertChannelNotOpen() const {
    if (getId() != 0 && isOpen())
        throw ConnectionException(
            504, format("Channel %d is already open.") % getId());
}

}} // namespace qpid::framing
