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
#ifndef _ExecutionHandler_
#define _ExecutionHandler_

#include <queue>
#include "qpid/framing/AccumulatedAck.h"
#include "qpid/framing/AMQP_ServerOperations.h"
#include "qpid/framing/FrameSet.h"
#include "qpid/framing/MethodContent.h"
#include "qpid/framing/SequenceNumber.h"
#include "qpid/sys/Mutex.h"
#include "ChainableFrameHandler.h"
#include "CompletionTracker.h"
#include "Correlator.h"
#include "Demux.h"
#include "Execution.h"

namespace qpid {
namespace client {

class ExecutionHandler : 
        public framing::AMQP_ServerOperations::ExecutionHandler,
        public framing::FrameHandler,
        public Execution
{
    framing::SequenceNumber incomingCounter;
    framing::AccumulatedAck incomingCompletionStatus;
    framing::SequenceNumber outgoingCounter;
    framing::AccumulatedAck outgoingCompletionStatus;
    framing::FrameSet::shared_ptr arriving;
    Correlator correlation;
    CompletionTracker completion;
    Demux demux;
    sys::Mutex lock;
    framing::ProtocolVersion version;
    uint64_t maxFrameSize;
    boost::function<void()> completionListener;

    void complete(uint32_t mark, const framing::SequenceNumberSet& range);    
    void flush();
    void noop();
    void result(uint32_t command, const std::string& data);
    void sync();

    void sendCompletion();

    framing::SequenceNumber send(const framing::AMQBody&, CompletionTracker::ResultListener, bool hasContent);
    void sendContent(const framing::MethodContent&);

public:
    typedef CompletionTracker::ResultListener ResultListener;

    // Allow other classes to set the out handler.
    framing::FrameHandler::Chain out;

    ExecutionHandler(uint64_t maxFrameSize = 65536);

    // Incoming handler. 
    void handle(framing::AMQFrame& frame);
    
    framing::SequenceNumber send(const framing::AMQBody& command, ResultListener=ResultListener());
    framing::SequenceNumber send(const framing::AMQBody& command, const framing::MethodContent& content, 
                                 ResultListener=ResultListener());
    framing::SequenceNumber lastSent() const;
    void sendSyncRequest();
    void sendFlushRequest();
    void completed(const framing::SequenceNumber& id, bool cumulative, bool send);
    void syncTo(const framing::SequenceNumber& point);
    void flushTo(const framing::SequenceNumber& point);

    bool isComplete(const framing::SequenceNumber& id);
    bool isCompleteUpTo(const framing::SequenceNumber& id);

    void setMaxFrameSize(uint64_t size) { maxFrameSize = size; }
    Correlator& getCorrelator() { return correlation; }
    CompletionTracker& getCompletionTracker() { return completion; }
    Demux& getDemux() { return demux; }

    void setCompletionListener(boost::function<void()>);
};

}}

#endif
