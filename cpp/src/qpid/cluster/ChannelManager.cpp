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

#include "qpid/log/Statement.h"
#include "qpid/framing/amqp_types.h"
#include "qpid/framing/AMQFrame.h"
#include "ChannelManager.h"

namespace qpid {
namespace cluster {

using namespace framing;

/** Handler to multicast to the cluster */
struct ClusterHandler : public FrameHandler {

    ClusterHandler(FrameHandler::Chain next, ChannelId bitmask_)
        : FrameHandler(next), bitmask(bitmask_) {}

    void handle(AMQFrame& frame) {
        frame.channel |= bitmask; // Mark the frame
        nextHandler(frame);
        // TODO aconway 2007-06-28: Right now everything is backed up
        // via multicast.  When we have point-to-point backups this
        // function must determine where each frame should be sent: to
        // multicast or only to specific backup(s) via AMQP.
    }

    ChannelId bitmask;
};

ChannelManager::ChannelManager(FrameHandler::Chain mcast) : mcastOut(mcast){}

void ChannelManager::update(ChannelId id, FrameHandler::Chains& chains) {
    // Store the original cluster chains for the channel.
    channels[id] = chains;

    // Replace chains with multicast-to-cluster handlers that mark the
    // high-bit of the channel ID on outgoing frames so we can tell
    // them from incoming frames in handle()
    // 
    // When handle() receives the frames from the cluster it
    // will forward them to the original channel chains stored in
    // channels map.
    // 
    chains.in = make_shared_ptr(new ClusterHandler(mcastOut, 0));
    chains.out= make_shared_ptr(new ClusterHandler(mcastOut, CHANNEL_HIGH_BIT));
}

void ChannelManager::handle(AMQFrame& frame) {
    bool isOut = frame.channel | CHANNEL_HIGH_BIT;
    frame.channel |= ~CHANNEL_HIGH_BIT; // Clear the bit.
    ChannelMap::iterator i = channels.find(frame.getChannel());
    if (i != channels.end()) {
        Chain& chain = isOut ? i->second.out : i->second.in;
        chain->handle(frame);
    }
    else
        updateFailoverState(frame);
}

void ChannelManager::updateFailoverState(AMQFrame& ) {
    QPID_LOG(critical, "Failover is not implemented");
    // FIXME aconway 2007-06-28: 
    // If the channel is not in my map then I'm not primary so
    // I don't pass the frame to the channel handler but I
    // do need to update the session failover state.
}

}} // namespace qpid::cluster
