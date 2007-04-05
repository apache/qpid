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
#include "../Exception.h"

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

RequestId ChannelAdapter::send(
    shared_ptr<AMQBody> body, Correlator::Action action)
{
    RequestId requestId = 0;
    assertChannelOpen();
    switch (body->type()) {
      case REQUEST_BODY: {
          AMQRequestBody::shared_ptr request =
              boost::shared_polymorphic_downcast<AMQRequestBody>(body);
          requester.sending(request->getData());
          requestId = request->getData().requestId;
          if (!action.empty())
              correlator.request(requestId, action);
          break;
      }
      case RESPONSE_BODY: {
          AMQResponseBody::shared_ptr response =
              boost::shared_polymorphic_downcast<AMQResponseBody>(body);
          responder.sending(response->getData());
          break;
      }
        // No action required for other body types.
    }
    out->send(new AMQFrame(getVersion(), getId(), body));
    return requestId;
}

void ChannelAdapter::handleRequest(AMQRequestBody::shared_ptr request) {
    assertMethodOk(*request);
    AMQRequestBody::Data& requestData = request->getData();
    responder.received(requestData);
    handleMethodInContext(request, MethodContext(this, request));
}

void ChannelAdapter::handleResponse(AMQResponseBody::shared_ptr response) {
    assertMethodOk(*response);
    AMQResponseBody::Data& responseData = response->getData();

    // FIXME aconway 2007-04-05: processed should be last
    // but causes problems with InProcessBroker tests because
    // we execute client code in handleMethod.
    // Need to introduce a queue & 2 threads for inprocess.
    requester.processed(responseData);
    // FIXME aconway 2007-04-04: exception handling.
    correlator.response(response);
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
