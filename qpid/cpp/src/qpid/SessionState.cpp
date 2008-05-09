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

#include "SessionState.h"
#include "qpid/amqp_0_10/exceptions.h"
#include "qpid/framing/AMQMethodBody.h"
#include "qpid/log/Statement.h"
#include <boost/bind.hpp>
#include <numeric>

namespace qpid {
using framing::AMQFrame;
using amqp_0_10::NotImplementedException;
using amqp_0_10::InvalidArgumentException;
using amqp_0_10::IllegalStateException;

namespace {
bool isControl(const AMQFrame& f) {
    return f.getMethod() && f.getMethod()->type() == 0;
}
} // namespace

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

SessionState::SendState::SendState(SessionState& s) : session(&s), unflushedSize(0) {}

const SessionPoint& SessionState::SendState::getCommandPoint() {
    return sendPoint;
}

bool SessionState::SendState::expected(const SessionPoint& point) {
    if (point < replayPoint || sendPoint < point)
        throw InvalidArgumentException(QPID_MSG(session->getId() << ": expected command-point out of range."));
    // FIXME aconway 2008-05-06: this is not strictly correct, we should keep
    // an intermediate replay pointer into the replay list.
    confirmed(point);           // Drop commands prior to expected from replay.
    return (!replayList.empty());
}

void SessionState::SendState::record(const AMQFrame& f) {
    if (isControl(f)) return;   // Ignore control frames.
    session->stateful = true;
    replayList.push_back(f);
    unflushedSize += f.size();
    incomplete += sendPoint.command;
    sendPoint.advance(f);
}

bool SessionState::SendState::needFlush() const { return unflushedSize >= session->config.replaySyncSize; }

void SessionState::SendState::recordFlush() {
    assert(flushPoint <= sendPoint);
    flushPoint = sendPoint;
    unflushedSize = 0;
}

void SessionState::SendState::confirmed(const SessionPoint& confirmed) {
    if (confirmed > sendPoint)
        throw InvalidArgumentException(QPID_MSG(session->getId() << "Confirmed commands not yet sent."));
    ReplayList::iterator i = replayList.begin();
    while (i != replayList.end() && replayPoint.command < confirmed.command) {
        replayPoint.advance(*i);
        assert(replayPoint <= sendPoint);
        if (replayPoint > flushPoint) 
            unflushedSize -= i->size();
        ++i;
    }
    if (replayPoint > flushPoint) flushPoint = replayPoint;
    replayList.erase(replayList.begin(), i);
    assert(replayPoint.offset == 0);
}

void SessionState::SendState::completed(const SequenceSet& commands) {
    if (commands.empty()) return;
    incomplete -= commands;
    // Completion implies confirmation but we don't handle out-of-order
    // confirmation, so confirm only the first contiguous range of commands.
    confirmed(SessionPoint(commands.rangesBegin()->end()));
}

SessionState::ReceiveState::ReceiveState(SessionState& s) : session(&s) {}

void SessionState::ReceiveState::setCommandPoint(const SessionPoint& point) {
    if (session->hasState() && point > received)
        throw InvalidArgumentException(QPID_MSG(session->getId() << ": Command-point out of range."));
    expected = point;
    if (expected > received)
        received = expected;
}

bool SessionState::ReceiveState::record(const AMQFrame& f) {
    if (isControl(f)) return true; // Ignore control frames.
    session->stateful = true;
    expected.advance(f);
    if (expected > received) {
        received = expected;
        return true;
    }
    else {
        QPID_LOG(debug, "Ignoring duplicate: " << f);
    return false;
}
}
    
void SessionState::ReceiveState::completed(SequenceNumber command, bool cumulative) {
    assert(command <= received.command); // Internal error to complete an unreceived command.
    assert(firstIncomplete <= command);
    if (cumulative)
        unknownCompleted.add(firstIncomplete, command);
    else
        unknownCompleted += command;
    firstIncomplete = unknownCompleted.rangeContaining(firstIncomplete).end();
}

void SessionState::ReceiveState::knownCompleted(const SequenceSet& commands) {
    if (!commands.empty() && commands.back() > received.command)
        throw InvalidArgumentException(QPID_MSG(session->getId() << ": Known-completed has invalid commands."));
    unknownCompleted -= commands;
}

SequenceNumber SessionState::ReceiveState::getCurrent() const {
    SequenceNumber current = expected.command; // FIXME aconway 2008-05-08: SequenceNumber arithmetic.
    return --current;
}

// FIXME aconway 2008-05-02: implement sync & kill limits.
SessionState::Configuration::Configuration()
    : replaySyncSize(std::numeric_limits<size_t>::max()),
      replayKillSize(std::numeric_limits<size_t>::max()) {}

SessionState::SessionState(const SessionId& i, const Configuration& c)
    : sender(*this), receiver(*this), id(i), timeout(), config(c), stateful()
{
    QPID_LOG(debug, "SessionState::SessionState " << id << ": " << this);
}

bool SessionState::hasState() const {
    return stateful;
}

SessionState::~SessionState() {}

std::ostream& operator<<(std::ostream& o, const SessionPoint& p) {
    return o << "(" << p.command.getValue() << "+" << p.offset << ")";
}

} // namespace qpid
