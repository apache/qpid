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
using amqp_0_10::ResourceLimitExceededException;
using amqp_0_10::InternalErrorException;

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

SessionPoint SessionState::senderGetCommandPoint() { return sender.sendPoint; }
SequenceSet  SessionState::senderGetIncomplete() const { return sender.incomplete; }
SessionPoint SessionState::senderGetReplayPoint() const { return sender.replayPoint; }

SessionState::ReplayRange SessionState::senderExpected(const SessionPoint& expect) {
    if (expect < sender.replayPoint || sender.sendPoint < expect)
        throw InvalidArgumentException(QPID_MSG(getId() << ": expected command-point out of range."));
    QPID_LOG(debug, getId() << ": sender expected point moved to " << expect);
    ReplayList::iterator i = sender.replayList.begin();
    SessionPoint p = sender.replayPoint;
    while (i != sender.replayList.end() && p.command < expect.command)
        p.advance(*i++);
    assert(p.command == expect.command);
    return boost::make_iterator_range(i, sender.replayList.end());
}

void SessionState::senderRecord(const AMQFrame& f) {
    if (isControl(f)) return;   // Ignore control frames.
    QPID_LOG_IF(debug, f.getMethod(), getId() << ": sent cmd " << sender.sendPoint.command << ": " << *f.getMethod());
    stateful = true;
    sender.replayList.push_back(f);
    sender.unflushedSize += f.size();
    sender.replaySize += f.size();
    sender.incomplete += sender.sendPoint.command;
    sender.sendPoint.advance(f);
    if (config.replayHardLimit && config.replayHardLimit < sender.replaySize) 
        throw ResourceLimitExceededException("Replay bufffer exceeeded hard limit");
}

bool SessionState::senderNeedFlush() const {
    return config.replayFlushLimit && sender.unflushedSize >= config.replayFlushLimit;
}

void SessionState::senderRecordFlush() {
    assert(sender.flushPoint <= sender.sendPoint);
    sender.flushPoint = sender.sendPoint;
    sender.unflushedSize = 0;
}

void SessionState::senderConfirmed(const SessionPoint& confirmed) {
    if (confirmed > sender.sendPoint)
        throw InvalidArgumentException(QPID_MSG(getId() << "Confirmed commands not yet sent."));
    QPID_LOG(debug, getId() << ": sender confirmed point moved to " << confirmed);
    ReplayList::iterator i = sender.replayList.begin();
    while (i != sender.replayList.end() && sender.replayPoint.command < confirmed.command) {
        sender.replayPoint.advance(*i);
        assert(sender.replayPoint <= sender.sendPoint);
        sender.replaySize -= i->size();
        if (sender.replayPoint > sender.flushPoint) 
            sender.unflushedSize -= i->size();
        ++i;
    }
    if (sender.replayPoint > sender.flushPoint)
        sender.flushPoint = sender.replayPoint;
    sender.replayList.erase(sender.replayList.begin(), i);
    assert(sender.replayPoint.offset == 0);
}

void SessionState::senderCompleted(const SequenceSet& commands) {
    if (commands.empty()) return;
    QPID_LOG(debug, getId() << ": sender marked completed: " << commands);
    sender.incomplete -= commands;
    // Completion implies confirmation but we don't handle out-of-order
    // confirmation, so confirm only the first contiguous range of commands.
    senderConfirmed(SessionPoint(commands.rangesBegin()->end()));
}

void SessionState::receiverSetCommandPoint(const SessionPoint& point) {
    if (hasState() && point > receiver.received)
        throw InvalidArgumentException(QPID_MSG(getId() << ": Command-point out of range."));
    QPID_LOG(debug, getId() << ": receiver command-point set to: " << point);
    receiver.expected = point;
    if (receiver.expected > receiver.received)
        receiver.received = receiver.expected;
}

bool SessionState::receiverRecord(const AMQFrame& f) {
    if (isControl(f)) return true; // Ignore control frames.
    stateful = true;
    receiver.expected.advance(f);
    bool firstTime = receiver.expected > receiver.received;
    if (firstTime) {
        receiver.received = receiver.expected;
        receiver.incomplete += receiverGetCurrent();
    }
    QPID_LOG_IF(debug, f.getMethod(), getId() << ": recv cmd " << receiverGetCurrent() << ": " << *f.getMethod());
    QPID_LOG_IF(debug, !firstTime, "Ignoring duplicate frame: " << receiverGetCurrent() << ": " << f);
    return firstTime;
}
    
void SessionState::receiverCompleted(SequenceNumber command, bool cumulative) {
    if (!receiver.incomplete.contains(command))
        throw InternalErrorException(QPID_MSG(getId() << "command is not received-incomplete: " << command ));
    SequenceNumber first =cumulative ? receiver.incomplete.front() : command;
    SequenceNumber last = command;
    receiver.unknownCompleted.add(first, last);
    receiver.incomplete.remove(first, last);
    QPID_LOG(debug, getId() << ": receiver marked completed: " << command
             << " incomplete: " << receiver.incomplete
             << " unknown-completed: " << receiver.unknownCompleted);
}

void SessionState::receiverKnownCompleted(const SequenceSet& commands) {
    if (!commands.empty() && commands.back() > receiver.received.command)
        throw InvalidArgumentException(QPID_MSG(getId() << ": Known-completed has invalid commands."));
    receiver.unknownCompleted -= commands;
    QPID_LOG(debug, getId() << ": receiver known completed: " << commands << " unknown: " << receiver.unknownCompleted);
}

const SessionPoint& SessionState::receiverGetExpected() const { return receiver.expected; }
const SessionPoint& SessionState::receiverGetReceived() const { return receiver.received; }
const SequenceSet& SessionState::receiverGetUnknownComplete() const { return receiver.unknownCompleted; }
const SequenceSet& SessionState::receiverGetIncomplete() const { return receiver.incomplete; }

SequenceNumber SessionState::receiverGetCurrent() const {
    SequenceNumber current = receiver.expected.command;
    if (receiver.expected.offset == 0)
        --current;
    return current;
}

SessionState::Configuration::Configuration(size_t flush, size_t hard) :
    replayFlushLimit(flush), replayHardLimit(hard) {}

SessionState::SessionState(const SessionId& i, const Configuration& c) : id(i), timeout(), config(c), stateful()
{
    QPID_LOG(debug, "SessionState::SessionState " << id << ": " << this);
}

bool SessionState::hasState() const { return stateful; }

SessionState::~SessionState() {}

std::ostream& operator<<(std::ostream& o, const SessionPoint& p) {
    return o << "(" << p.command.getValue() << "+" << p.offset << ")";
}

} // namespace qpid
