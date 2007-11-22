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
#include "OutputHandler.h"
#include "AMQFrame.h"
#include "FrameHandler.h"
#include "qpid/Exception.h"

#include "AMQMethodBody.h"
#include "qpid/framing/ConnectionOpenBody.h"

namespace qpid {
namespace framing {

ChannelAdapter::Handler::Handler(ChannelAdapter& c) : parent(c) {}
void ChannelAdapter::Handler::handle(AMQFrame& f) { parent.handleBody(f.getBody()); }

ChannelAdapter::ChannelAdapter() : handler(*this), id(0) {}

void ChannelAdapter::init(ChannelId i, FrameHandler& out, ProtocolVersion v) 
{
    assertChannelNotOpen();
    id = i;
    version = v;
    handlers.reset(&handler, &out);
}

void ChannelAdapter::send(const AMQBody& body)
{
    assertChannelOpen();
    AMQFrame frame(body);
    frame.setChannel(getId());
    handlers.out(frame);
}

void ChannelAdapter::assertMethodOk(AMQMethodBody& method) const {
    if (getId() != 0 && method.amqpClassId() == ConnectionOpenBody::CLASS_ID)
        throw ChannelErrorException(
            QPID_MSG("Connection method on non-0 channel " << getId()));
}

void ChannelAdapter::assertChannelOpen() const {
    if (getId() != 0 && !isOpen())
        throw ChannelErrorException(
            QPID_MSG("Channel " << getId() << " is not open."));
}

void ChannelAdapter::assertChannelNotOpen() const {
    if (getId() != 0 && isOpen())
        throw ChannelErrorException(
            QPID_MSG("Channel " << getId() << " is already open."));
}

void ChannelAdapter::handle(AMQFrame& f) { handleBody(f.getBody()); }

}} // namespace qpid::framing
