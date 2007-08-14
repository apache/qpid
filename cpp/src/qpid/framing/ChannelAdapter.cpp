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
#include "OutputHandler.h"
#include "AMQFrame.h"
#include "FrameHandler.h"
#include "qpid/Exception.h"

using boost::format;

namespace qpid {
namespace framing {

/** Framehandler that feeds into the channel. */
struct ChannelAdapter::ChannelAdapterHandler : public FrameHandler {
    ChannelAdapterHandler(ChannelAdapter& channel_) : channel(channel_) {}
    void handle(AMQFrame& frame) { channel.handleBody(frame.getBody()); }
    ChannelAdapter& channel;
};

void ChannelAdapter::init(ChannelId i, OutputHandler& out, ProtocolVersion v)
{
    assertChannelNotOpen();
    id = i;
    version = v;

    handlers.in = make_shared_ptr(new ChannelAdapterHandler(*this));
    handlers.out= make_shared_ptr(new OutputHandlerFrameHandler(out));
}

void ChannelAdapter::send(shared_ptr<AMQBody> body)
{
    assertChannelOpen();
    AMQFrame frame(getVersion(), getId(), body);
    handlers.out->handle(frame);
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
