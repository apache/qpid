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

#include <qpid/framing/SequenceNumber.h>
#include <qpid/framing/SequenceSet.h>
#include <qpid/framing/AMQFrame.h>
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

/** The sending half of session state */
class SendState {
  public:
    typedef std::vector<framing::AMQFrame> ReplayList;

    /** Record frame f for replay. Should not be called during replay. */
    void send(const framing::AMQFrame& f);

    /** @return true if we should send flush for confirmed and completed commands. */
    bool needFlush() const;

    /** Called when flush for confirmed and completed commands is sent to peer. */
    void sendFlush();

    /** Called when the peer confirms up to commands. */
    void peerConfirmed(const SessionPoint& confirmed);

    /** Called when the peer indicates commands completed */
    void peerCompleted(const SequenceSet& commands);

    /** Get the replay list. @see getReplayPoint. */
    const ReplayList& getReplayList() const { return replayList; }

    /**
     * The replay point is the point up to which all data has been
     * confirmed.  Partial replay is not supported, it will always
     * have offset==0.
     */
    const SessionPoint& getReplayPoint() const { return replayPoint; }

    const SessionPoint& getSendPoint() const { return sendPoint; } 
    const SequenceSet& getCompleted() const { return sentCompleted; }

  protected:
    SendState(size_t replaySyncSize, size_t replayKillSize);

  private:
    size_t replaySyncSize, replayKillSize; // @see SessionState::Configuration.
    // invariant: replayPoint <= flushPoint <= sendPoint
    SessionPoint replayPoint;   // Can replay from this point
    SessionPoint sendPoint;     // Send from this point
    SessionPoint flushPoint;    // Point of last flush
    ReplayList replayList; // Starts from replayPoint.
    size_t unflushedSize;       // Un-flushed bytes in replay list.
    SequenceSet sentCompleted; // Commands sent and acknowledged as completed.
};

/** Receiving half of SessionState */
class ReceiveState {
  public:
    bool hasState();

    /** Set the command point. */
    void setExpecting(const SessionPoint& point);

    /** Returns true if frame should be be processed, false if it is a duplicate. */
    bool receive(const framing::AMQFrame& f);

    /** Command completed locally */
    void localCompleted(SequenceNumber command);

    /** Peer has indicated commands are known completed */
    void peerKnownComplete(const SequenceSet& commands);

    /** Recieved, completed and possibly not known by peer to be completed */
    const SequenceSet& getReceivedCompleted() const { return receivedCompleted; }
    const SessionPoint& getExpecting() const { return expecting; }
    const SessionPoint& getReceived() const { return received; }

  protected:
    ReceiveState();

  private:
    bool stateful;              // True if session has state.
    SessionPoint expecting;     // Expecting from here
    SessionPoint received;      // Received to here. Invariant: expecting <= received.
    SequenceSet receivedCompleted; // Received & completed, may not be not known-completed by peer
};

/** Identifier for a session */
class SessionId : boost::totally_ordered1<SessionId> {
    std::string userId;
    std::string name;
  public:
    SessionId(const std::string& userId=std::string(), const std::string& name=std::string());
    std::string getUserId() const { return userId; }
    std::string getName() const { return name; }
    bool operator<(const SessionId&) const ;
    bool operator==(const SessionId& id) const;
};


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
class SessionState : public SendState, public ReceiveState {
  public:
    struct Configuration {
        Configuration();
        size_t replaySyncSize; // Issue a sync when the replay list holds >= N bytes
        size_t replayKillSize; // Kill session if replay list grows beyond N bytes.
    };
    
    SessionState(const SessionId& =SessionId(), const Configuration& =Configuration());

    const SessionId& getId() const { return id; }
    uint32_t getTimeout() const { return timeout; }
    void setTimeout(uint32_t seconds) { timeout = seconds; }

    /** Clear all state except Id. */
    void clear();

  private:
    SessionId id;
    uint32_t timeout;
    Configuration config;
};

} // namespace qpid


#endif  /*!QPID_SESSIONSTATE_H*/
