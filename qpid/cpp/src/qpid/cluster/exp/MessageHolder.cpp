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

#include "MessageHolder.h"
#include "qpid/broker/Message.h"

namespace qpid {
namespace cluster {

uint16_t MessageHolder::sending(const MessagePtr& msg, const QueuePtr& q) {
    sys::Mutex::ScopedLock l(lock);
    Channel channel = getChannel(l);
    messages[channel] = std::make_pair(msg,q);
    return channel;
}

bool MessageHolder::check(const framing::AMQFrame& frame,
                          QueuePtr& queueOut, MessagePtr& msgOut)
{
    if (frame.getEof() && frame.getEos()) { //end of frameset
        sys::Mutex::ScopedLock l(lock);
        MessageMap::iterator i = messages.find(frame.getChannel());
        assert(i != messages.end());
        msgOut = i->second.first;
        queueOut = i->second.second;
        messages.erase(frame.getChannel()); // re-use the channel.
        return true;
    }
    return false;
}

MessageHolder::Channel MessageHolder::getChannel(const sys::Mutex::ScopedLock&) {
    Channel old = mark;
    while (messages.find(++mark) != messages.end())
        assert(mark != old); // check wrap-around
    return mark;
}

}} // namespace qpid::cluster
