#ifndef QPID_FRAMING_SESSIONSTATE_H
#define QPID_FRAMING_SESSIONSTATE_H

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

#include "qpid/framing/SequenceNumber.h"
#include "qpid/framing/Uuid.h"
#include "qpid/framing/AMQFrame.h"

#include <boost/optional.hpp>

#include <deque>

namespace qpid {
namespace framing {

/**
 * Session state common to client and broker.
 * 
 * Stores data needed to resume a session: replay frames, implements
 * session ack/resume protcools. Stores handler chains for the session,
 * handlers may themselves store state.
 *
 * A SessionState is always associated with an _open_ session (attached or
 * suspended) it is destroyed when the session is closed.
 *
 */
class SessionState
{
  public:
    typedef std::vector<AMQFrame> Replay;

    /**
     *Create a newly opened active session.
     *@param ackInterval send/solicit an ack whenever N unacked frames
     * have been received/sent.
     * 
     * N=0 disables voluntary send/solict ack.
     */
    SessionState(uint32_t ackInterval,
                 const framing::Uuid& id=framing::Uuid(true));

    /**
     * Create a non-resumable session. Does not store session frames,
     * never volunteers ack or solicit-ack.
     */
    SessionState(const framing::Uuid& id=framing::Uuid(true));

    const framing::Uuid& getId() const { return id; }
    
    /** Received incoming L3 frame.
     * @return SequenceNumber if an ack should be sent, empty otherwise.
     * SessionState assumes that acks are sent whenever it returns
     * a seq. number.
     */
    boost::optional<SequenceNumber> received(const AMQFrame&);

    /** Sent outgoing L3 frame.
     *@return true if solicit-ack should be sent. Note the SessionState
     *assumes that a solicit-ack is sent every time it returns true.
     */
    bool sent(const AMQFrame&);

    /** Received normal incoming ack. */
    void receivedAck(SequenceNumber);

    /** Frames to replay 
     *@pre getState()==ATTACHED
     */
    Replay replay();

    /** About to send an unscheduled ack, e.g. to respond to a solicit-ack.
     * 
     * Note: when received() returns a sequence number this function
     * should not be called. SessionState assumes that the ack is sent
     * every time received() returns a sequence number.
     */
    SequenceNumber sendingAck();

    SequenceNumber getLastSent() const { return lastSent; }
    SequenceNumber getLastReceived() const { return lastReceived; }

  private:
    typedef std::deque<AMQFrame> Unacked;

    bool sendingSolicit();

    framing::Uuid id;
    Unacked unackedOut;
    SequenceNumber lastReceived;
    SequenceNumber lastSent;
    uint32_t ackInterval;
    SequenceNumber sendAckAt;
    SequenceNumber solicitAckAt;
    bool ackSolicited;
    bool suspending;
    bool resumable;
};


}} // namespace qpid::common


#endif  /*!QPID_FRAMING_SESSIONSTATE_H*/
