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

// FIXME aconway 2008-04-24: Reminders for handler implementation.
//
// - execution.sync results must be communicated to SessionState::peerConfirmed.
//
// 

#include "SessionState.h"
#include "qpid/amqp_0_10/exceptions.h"
#include "qpid/framing/AMQMethodBody.h"
#include <boost/bind.hpp>
#include <numeric>

namespace qpid {
using framing::AMQFrame;
using amqp_0_10::NotImplementedException;

/** A point in the session - command id + offset */
void SessionPoint::advance(const AMQFrame& f) {
    if (f.isLastSegment() && f.isLastFrame()) {
        ++command;
        offset = 0;
    }
    else {
        // TODO aconway 2008-04-24: if we go to support for partial
        // command replay, then it may be better to record the unframed
        // data size in a command point rather than the framed size so
        // that the relationship of fragment offsets to the replay
        // list can be computed more easily.
        // 
        offset += f.size();
    }
}

bool SessionPoint::operator<(const SessionPoint& x) const {
    return command < x.command || (command == x.command && offset < x.offset);
}

bool SessionPoint::operator==(const SessionPoint& x) const {
    return command == x.command && offset == x.offset;
}

SendState::SendState(size_t syncSize, size_t killSize)
    : replaySyncSize(syncSize), replayKillSize(killSize), unflushedSize() {}

void SendState::send(const AMQFrame& f) {
    if (f.getMethod() && f.getMethod()->type() == 0)         
        return;                 // Don't replay control frames.
    replayList.push_back(f);
    unflushedSize += f.size();
    sendPoint.advance(f);
}

bool SendState::needFlush() const { return unflushedSize >= replaySyncSize; }

void SendState::sendFlush() {
    assert(flushPoint <= sendPoint);
    flushPoint = sendPoint;
    unflushedSize = 0;
}

void SendState::peerConfirmed(const SessionPoint& confirmed) {
    ReplayList::iterator i = replayList.begin();
    // Ignore peerConfirmed.offset, we don't support partial replay.
    while (i != replayList.end() && replayPoint.command < confirmed.command) {
        assert(replayPoint <= flushPoint);
        replayPoint.advance(*i);
        assert(replayPoint <= sendPoint);
        if (replayPoint > flushPoint) {
            flushPoint.advance(*i);
            assert(replayPoint <= flushPoint);
            unflushedSize -= i->size();
        }
        ++i;
    }
    replayList.erase(replayList.begin(), i);
    assert(replayPoint.offset == 0);
}

void SendState::peerCompleted(const SequenceSet& commands) {
    if (commands.empty()) return;
    sentCompleted += commands;
    // Completion implies confirmation but we don't handle out-of-order
    // confirmation, so confirm only the first contiguous range of commands.
    peerConfirmed(SessionPoint(commands.rangesBegin()->end()));
}

bool ReceiveState::hasState() { return stateful; }

void ReceiveState::setExpecting(const SessionPoint& point) {
    if (!hasState())          // initializing a new session.
        expecting = received = point;
    else {                  // setting point in an existing session.
        if (point > received)
            throw NotImplementedException("command-point out of bounds.");
        expecting = point;
    }
}

ReceiveState::ReceiveState() : stateful() {}

bool ReceiveState::receive(const AMQFrame& f) {
    stateful = true;
    expecting.advance(f);
    if (expecting > received) {
        received = expecting;
        return true;
    }
    return false;
}

void ReceiveState::localCompleted(SequenceNumber command) {
    assert(command < received.command); // Can't complete what we haven't received.
    receivedCompleted += command;
}
    
void ReceiveState::peerKnownComplete(const SequenceSet& commands) {
    receivedCompleted -= commands;
}

SessionId::SessionId(const std::string& u, const std::string& n) : userId(u), name(n) {}

bool SessionId::operator<(const SessionId& id) const {
    return userId < id.userId || (userId == id.userId && name < id.name);
}

bool SessionId::operator==(const SessionId& id) const {
    return id.name == name  && id.userId == userId;
}

SessionState::Configuration::Configuration()
    : replaySyncSize(std::numeric_limits<size_t>::max()),
      replayKillSize(std::numeric_limits<size_t>::max()) {}

SessionState::SessionState(const SessionId& i, const Configuration& c)
    : SendState(c.replaySyncSize, c.replayKillSize),
      id(i), timeout(), config(c) {}

void SessionState::clear() { *this = SessionState(id, config); }

std::ostream& operator<<(std::ostream& o, const SessionPoint& p) {
    return o << "(" << p.command.getValue() << "+" << p.offset << ")";
}

} // namespace qpid
