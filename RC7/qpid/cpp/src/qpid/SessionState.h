#ifndef QPID_SESSIONSTATE_H
#define QPID_SESSIONSTATE_H

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

#include <qpid/SessionId.h>
#include <qpid/framing/SequenceNumber.h>
#include <qpid/framing/SequenceSet.h>
#include <qpid/framing/AMQFrame.h>
#include <qpid/framing/FrameHandler.h>
#include <boost/operators.hpp>
#include <boost/range/iterator_range.hpp>
#include <vector>
#include <iosfwd>

namespace qpid {
using framing::SequenceNumber;
using framing::SequenceSet;

/** A point in the session. Points to command id + offset */
struct SessionPoint : boost::totally_ordered1<SessionPoint> {
    SessionPoint(SequenceNumber command = 0, uint64_t offset = 0);

    SequenceNumber command;
    uint64_t offset;

    /** Advance past frame f */
    void advance(const framing::AMQFrame& f);

    bool operator<(const SessionPoint&) const;
    bool operator==(const SessionPoint&) const;
};

std::ostream& operator<<(std::ostream&, const SessionPoint&);

/**
 * Support for session idempotence barrier and resume as defined in
 * AMQP 0-10.
 *
 * We only issue/use contiguous confirmations, out-of-order confirmation
 * is ignored. Out of order completion is fully supported.
 * 
 * Raises NotImplemented if the command point is set greater than the
 * max currently received command data, either explicitly via
 * session.command-point or implicitly via session.gap.
 *
 * Partial replay is not supported, replay always begins on a command
 * boundary, and we never confirm partial commands.
 *
 * The SessionPoint data structure does store offsets so this class
 * could be extended to support partial replay without
 * source-incompatbile API changes.
 */
class SessionState {
    typedef std::vector<framing::AMQFrame> ReplayList;

  public:

    typedef boost::iterator_range<ReplayList::iterator> ReplayRange;

    struct Configuration {
        Configuration(size_t flush=1024*1024, size_t hard=0);
        size_t replayFlushLimit; // Flush when the replay list >= N bytes. 0 disables.
        size_t replayHardLimit; // Kill session if replay list > N bytes. 0 disables.
    };

    SessionState(const SessionId& =SessionId(), const Configuration& =Configuration());
    
    virtual ~SessionState();

    bool hasState() const;

    const SessionId& getId() const { return id; }

    uint32_t getTimeout() const { return timeout; }
    void setTimeout(uint32_t seconds) { timeout = seconds; }

    bool operator==(const SessionId& other) const { return id == other; }
    bool operator==(const SessionState& other) const { return id == other.id; }

    // ==== Functions for sender state.

    /** Record frame f for replay. Should not be called during replay. */
    virtual void senderRecord(const framing::AMQFrame& f);

    /** @return true if we should send flush for confirmed and completed commands. */
    virtual bool senderNeedFlush() const;

    /** Called when flush for confirmed and completed commands is sent to peer. */
    virtual void senderRecordFlush();

    /** True if we should reply to the next incoming completed command */
    virtual bool senderNeedKnownCompleted() const;

    /** Called when knownCompleted is sent to peer. */
    virtual void senderRecordKnownCompleted();

    /** Called when the peer confirms up to comfirmed. */
    virtual void senderConfirmed(const SessionPoint& confirmed);

    /** Called when the peer indicates commands completed */
    virtual void senderCompleted(const SequenceSet& commands);

    /** Point from which the next new (not replayed) data will be sent. */
    virtual SessionPoint senderGetCommandPoint();

    /** Set of outstanding incomplete commands */
    virtual SequenceSet senderGetIncomplete() const;

    /** Point from which we can replay. */
    virtual SessionPoint senderGetReplayPoint() const;

    /** Peer expecting commands from this point.
     *@return Range of frames to be replayed.
     */
    virtual ReplayRange senderExpected(const SessionPoint& expected);

    // ==== Functions for receiver state

    /** Set the command point. */
    virtual void receiverSetCommandPoint(const SessionPoint& point);

    /** Returns true if frame should be be processed, false if it is a duplicate. */
    virtual bool receiverRecord(const framing::AMQFrame& f);

    /** Command completed locally */
    virtual void receiverCompleted(SequenceNumber command, bool cumulative=false);

    /** Peer has indicated commands are known completed */
    virtual void receiverKnownCompleted(const SequenceSet& commands);

    /** True if the next completed control should set the timely-reply argument
     * to request a knonw-completed response.
     */
    virtual bool receiverNeedKnownCompleted() const;

    /** Get the incoming command point */
    virtual const SessionPoint& receiverGetExpected() const;

    /** Get the received high-water-mark, may be > getExpected() during replay */
    virtual const SessionPoint& receiverGetReceived() const;

    /** Completed received commands that the peer may not know about. */
    virtual const SequenceSet& receiverGetUnknownComplete() const;

    /** Incomplete received commands. */
    virtual const SequenceSet& receiverGetIncomplete() const;

    /** ID of the command currently being handled. */
    virtual SequenceNumber receiverGetCurrent() const;

    /** Set the state variables, used to create a session that will resume
     *  from some previously established point.
     */
    virtual void setState(
        const SequenceNumber& replayStart,
        const SequenceNumber& sendCommandPoint,
        const SequenceSet& sentIncomplete,
        const SequenceNumber& expected,
        const SequenceNumber& received,
        const SequenceSet& unknownCompleted,
        const SequenceSet& receivedIncomplete
    );

  private:

    struct SendState {
        SendState();
        // invariant: replayPoint <= flushPoint <= sendPoint
        SessionPoint replayPoint;   // Can replay from this point
        SessionPoint flushPoint;    // Point of last flush
        SessionPoint sendPoint;     // Send from this point
        ReplayList replayList;      // Starts from replayPoint.
        size_t unflushedSize;       // Un-flushed bytes in replay list.
        size_t replaySize;          // Total bytes in replay list.
        SequenceSet incomplete;     // Commands sent and not yet completed.
        size_t bytesSinceKnownCompleted; // Bytes sent since we last issued a knownCompleted.
    } sender;

    struct ReceiveState {
        ReceiveState();
        SessionPoint expected;  // Expected from here
        SessionPoint received; // Received to here. Invariant: expected <= received.
        SequenceSet unknownCompleted; // Received & completed, may not  not known-complete by peer.
        SequenceSet incomplete;       // Incomplete received commands.
        size_t bytesSinceKnownCompleted; // Bytes sent since we last issued a knownCompleted.
    } receiver;

    SessionId id;
    uint32_t timeout;
    Configuration config;
    bool stateful;
};

inline bool operator==(const SessionId& id, const SessionState& s) { return s == id; }

} // namespace qpid


#endif  /*!QPID_SESSIONSTATE_H*/
