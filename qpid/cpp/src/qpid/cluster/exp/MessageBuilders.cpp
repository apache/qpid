/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
#include "MessageBuilders.h"
#include "qpid/log/Statement.h"
#include "qpid/Exception.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/broker/Message.h"

namespace qpid {
namespace cluster {

MessageBuilders::MessageBuilders(broker::MessageStore* s) : store(s) {}

void MessageBuilders::announce(MemberId sender, uint16_t channel,
                               const boost::shared_ptr<broker::Queue>& queue)
{
    ChannelId key(sender, channel);
    if (map.find(key) != map.end())
        throw Exception(
            QPID_MSG("MessageBuilder channel " << channel << " on " << sender
                     << " is already assigned."));
    map[key] = std::make_pair(queue, new broker::MessageBuilder(store));
}

bool MessageBuilders::handle(
    MemberId sender,
    const framing::AMQFrame& frame,
    boost::shared_ptr<broker::Queue>& queueOut,
    boost::intrusive_ptr<broker::Message>& messageOut)
{
    ChannelId key(sender, frame.getChannel());
    Map::iterator i = map.find(key);
    if (i == map.end())
        throw Exception(QPID_MSG("MessageBuilder channel " << frame.getChannel()
                                 << " on " << sender << " is not assigned."));
    boost::shared_ptr<broker::MessageBuilder> msgBuilder = i->second.second;
    // Nasty bit of code pasted from broker::SessionState::handleContent.
    // Should really be part of broker::MessageBuilder
    if (frame.getBof() && frame.getBos()) //start of frameset
        msgBuilder->start(0);
    msgBuilder->handle(const_cast<framing::AMQFrame&>(frame));
    if (frame.getEof() && frame.getEos()) { //end of frameset
        if (frame.getBof()) {
            //i.e this is a just a command frame, add a dummy header
            framing::AMQFrame header((framing::AMQHeaderBody()));
            header.setBof(false);
            header.setEof(false);
            msgBuilder->getMessage()->getFrames().append(header);
        }
        queueOut = i->second.first;
        messageOut = msgBuilder->getMessage();
        map.erase(key);
        return true;
    }
    return false;
}

}} // namespace qpid::cluster
