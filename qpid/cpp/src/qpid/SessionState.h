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
#include <vector>
#include <iosfwd>

namespace qpid {
using framing::SequenceNumber;
using framing::SequenceSet;

/** A point in the session. Points to command id + offset */
struct SessionPoint : boost::totally_ordered1<SessionPoint> {
    SessionPoint(SequenceNumber command_=0, uint64_t offset_ = 0) : command(command_), offset(offset_) {}

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
  public:

    /** State for commands sent. Records commands for replay,
     * tracks confirmation and completion of sent commands.
     */
class SendState {
  public:
    typedef std::vector<framing::AMQFrame> ReplayList;

    /** Record frame f for replay. Should not be called during replay. */
        void record(const framing::AMQFrame& f);

    /** @return true if we should send flush for confirmed and completed commands. */
    bool needFlush() const;

    /** Called when flush for confirmed and completed commands is sent to peer. */
        void recordFlush();

        /** Called when the peer confirms up to comfirmed. */
        void confirmed(const SessionPoint& confirmed);

    /** Called when the peer indicates commands completed */
        void completed(const SequenceSet& commands);

        /** Point from which we can replay. All data < replayPoint is confirmed. */
    const SessionPoint& getReplayPoint() const { return replayPoint; }

        /** Get the replay list, starting from getReplayPoint() */
        // TODO aconway 2008-04-30: should be const, but FrameHandler takes non-const AMQFrame&.
        ReplayList& getReplayList() { return replayList; }

        /** Point from which the next data will be sent. */
        const SessionPoint& getCommandPoint();

        /** Set of outstanding incomplete commands */
        const SequenceSet& getIncomplete() const { return incomplete; }

        /** Peer expecting commands from this point.
         *@return true if replay is required, sets replayPoint.
         */
        bool expected(const SessionPoint& expected);

  private:
        SendState(SessionState& s);

        SessionState* session;
    // invariant: replayPoint <= flushPoint <= sendPoint
    SessionPoint replayPoint;   // Can replay from this point
    SessionPoint flushPoint;    // Point of last flush
        SessionPoint sendPoint;     // Send from this point
    ReplayList replayList; // Starts from replayPoint.
    size_t unflushedSize;       // Un-flushed bytes in replay list.
        SequenceSet incomplete;     // Commands sent and not yet completed.

      friend class SessionState;
};

    /** State for commands received.
     * Idempotence barrier for duplicate commands, tracks completion
     * and of received commands.
     */
class ReceiveState {
  public:
    /** Set the command point. */
        void setCommandPoint(const SessionPoint& point);

    /** Returns true if frame should be be processed, false if it is a duplicate. */
        bool record(const framing::AMQFrame& f);

    /** Command completed locally */
        void completed(SequenceNumber command, bool cumulative=false);

    /** Peer has indicated commands are known completed */
        void knownCompleted(const SequenceSet& commands);

        /** Get the incoming command point */
        const SessionPoint& getExpected() const { return expected; }

        /** Get the received high-water-mark, may be > getExpected() during replay */
    const SessionPoint& getReceived() const { return received; }

        /** Completed commands that the peer may not know about */
        const SequenceSet& getUnknownComplete() const { return unknownCompleted; }

        /** ID of the command currently being handled. */
        SequenceNumber getCurrent() const;

  private:
        ReceiveState(SessionState&);

        SessionState* session;
        SessionPoint expected;  // Expected from here
        SessionPoint received; // Received to here. Invariant: expected <= received.
        SequenceSet unknownCompleted; // Received & completed, may not  not known-complete by peer.
        SequenceNumber firstIncomplete;  // First incomplete command.

      friend class SessionState;
    };

    struct Configuration {
        Configuration();
        size_t replaySyncSize; // Issue a sync when the replay list holds >= N bytes
        size_t replayKillSize; // Kill session if replay list grows beyond N bytes.
    };
    
    SessionState(const SessionId& =SessionId(), const Configuration& =Configuration());

    virtual ~SessionState();

    const SessionId& getId() const { return id; }
    uint32_t getTimeout() const { return timeout; }
    void setTimeout(uint32_t seconds) { timeout = seconds; }

    bool operator==(const SessionId& other) const { return id == other; }
    bool operator==(const SessionState& other) const { return id == other.id; }

    SendState sender;           ///< State for commands sent
    ReceiveState receiver;      ///< State for commands received

    bool hasState() const;

  private:
    SessionId id;
    uint32_t timeout;
    Configuration config;
    bool stateful;

  friend class SendState;
  friend class ReceiveState;
};

inline bool operator==(const SessionId& id, const SessionState& s) { return s == id; }

} // namespace qpid


#endif  /*!QPID_SESSIONSTATE_H*/
