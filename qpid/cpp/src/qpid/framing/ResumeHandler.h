#ifndef QPID_FRAMING_RESUMEHANDLER_H
#define QPID_FRAMING_RESUMEHANDLER_H

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

#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/SequenceNumber.h"

#include <deque>

namespace qpid {
namespace framing {

/**
 * In/out handler pair for managing exactly-once session delivery.
 * The same handler is used by client and broker.
 * This handler only deals with TCP style SequenceNumber acks,
 * not with fragmented SequenceNumberSet.
 *
 * THREAD UNSAFE. Expected to be used in a serialized context.
 */
class ResumeHandler : public FrameHandler::InOutHandler
{
  public:
    /** Received acknowledgement for sent frames up to and including sentOk */
    void ackReceived(SequenceNumber sentOk);

    /** What was the last sequence number we received. */
    SequenceNumber getLastReceived() { return lastReceived; }

    /** Resend the unacked frames to the output handler */
    void resend();

  protected:
    void handleIn(AMQFrame&);
    void handleOut(AMQFrame&);
    
  private:
    typedef std::deque<AMQFrame> Frames;
    Frames unacked;
    SequenceNumber lastReceived;
    SequenceNumber lastSent;
};


}} // namespace qpid::common


#endif  /*!QPID_FRAMING_RESUMEHANDLER_H*/
