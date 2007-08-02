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
#include "qpid/framing/SequenceNumber.h"
#include "BlockingQueue.h"
#include "ChainableFrameHandler.h"
#include "CompletionTracker.h"
#include "Correlator.h"
#include "ReceivedContent.h"

namespace qpid {
namespace client {

class ExecutionHandler : 
    private framing::AMQP_ServerOperations::ExecutionHandler,
    public ChainableFrameHandler
{
    framing::Window incoming;
    framing::Window outgoing;
    ReceivedContent::shared_ptr arriving;
    Correlator correlation;
    CompletionTracker completion;
    framing::ProtocolVersion version;

    void complete(uint32_t mark, framing::SequenceNumberSet range);    
    void flush();

public:
    BlockingQueue<ReceivedContent::shared_ptr> received; 

    ExecutionHandler();

    void handle(framing::AMQFrame& frame);
    void send(framing::AMQBody::shared_ptr command, 
              CompletionTracker::Listener f = CompletionTracker::Listener(), 
              Correlator::Listener g = Correlator::Listener());
    void sendContent(framing::AMQBody::shared_ptr command, 
                     const framing::BasicHeaderProperties& headers, const std::string& data, 
                     uint64_t frameSize,
                     CompletionTracker::Listener f = CompletionTracker::Listener(), 
                     Correlator::Listener g = Correlator::Listener());

    void sendContent(framing::AMQBody::shared_ptr content);
};

}}

#endif
