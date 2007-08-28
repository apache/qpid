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
#include "qpid/framing/AMQP_ServerOperations.h"
#include "qpid/framing/FrameSet.h"
#include "qpid/framing/SequenceNumber.h"
#include "BlockingQueue.h"
#include "ChainableFrameHandler.h"
#include "CompletionTracker.h"
#include "Correlator.h"

namespace qpid {
namespace client {

class ExecutionHandler : 
    private framing::AMQP_ServerOperations::ExecutionHandler,
    public ChainableFrameHandler
{
    framing::Window incoming;
    framing::Window outgoing;
    framing::FrameSet::shared_ptr arriving;
    Correlator correlation;
    CompletionTracker completion;
    framing::ProtocolVersion version;
    uint64_t maxFrameSize;

    void complete(uint32_t mark, const framing::SequenceNumberSet& range);    
    void flush();
    void noop();
    void result(uint32_t command, const std::string& data);
    void sync();

public:
    BlockingQueue<framing::FrameSet::shared_ptr> received; 

    ExecutionHandler(uint64_t maxFrameSize = 65536);

    void setMaxFrameSize(uint64_t size) { maxFrameSize = size; }

    void handle(framing::AMQFrame& frame);
    void send(const framing::AMQBody& command, 
              CompletionTracker::Listener f = CompletionTracker::Listener(), 
              Correlator::Listener g = Correlator::Listener());
    void sendContent(const framing::AMQBody& command, 
                     const framing::BasicHeaderProperties& headers, const std::string& data, 
                     CompletionTracker::Listener f = CompletionTracker::Listener(), 
                     Correlator::Listener g = Correlator::Listener());
    void sendFlush();
};

}}

#endif
