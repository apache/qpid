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
#ifndef _SemanticHandler_
#define _SemanticHandler_

#include <memory>
#include "BrokerAdapter.h"
#include "DeliveryAdapter.h"
#include "MessageBuilder.h"
#include "IncomingExecutionContext.h"
#include "HandlerImpl.h"

#include "qpid/framing/amqp_types.h"
#include "qpid/framing/AMQP_ServerOperations.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/SequenceNumber.h"

#include <boost/function.hpp>

namespace qpid {

namespace framing {
class AMQMethodBody;
class AMQHeaderBody;
class AMQContentBody;
class AMQHeaderBody;
}

namespace broker {

class SessionState;

class SemanticHandler : public DeliveryAdapter,
                        public framing::FrameHandler,
                        public framing::AMQP_ServerOperations::ExecutionHandler
    
{
    typedef boost::function<void(DeliveryId, DeliveryId)> RangedOperation;    

    SemanticState state;
    SessionState& session;
    // TODO aconway 2007-09-20: Why are these on the handler rather than the
    // state?
    IncomingExecutionContext incoming;
    framing::Window outgoing;
    MessageBuilder msgBuilder;
    RangedOperation ackOp;

    enum TrackId {EXECUTION_CONTROL_TRACK, MODEL_COMMAND_TRACK, MODEL_CONTENT_TRACK};
    TrackId getTrack(const framing::AMQFrame& frame);

    void handleL3(framing::AMQMethodBody* method);
    void handleCommand(framing::AMQMethodBody* method);
    void handleContent(framing::AMQFrame& frame);

    void sendCompletion();

    //delivery adapter methods:
    DeliveryId deliver(QueuedMessage& msg, DeliveryToken::shared_ptr token);

    framing::AMQP_ClientProxy& getProxy() { return session.getProxy(); }
    //Connection& getConnection() { return session.getConnection(); }
    Broker& getBroker() { return session.getBroker(); }

public:
    SemanticHandler(SessionState& session);

    //frame handler:
    void handle(framing::AMQFrame& frame);

    //execution class method handlers:
    void complete(uint32_t cumulativeExecutionMark, const framing::SequenceNumberSet& range);    
    void flush();
    void noop();
    void result(uint32_t command, const std::string& data);
    void sync();


    SemanticState& getSemanticState() { return state; }
};

}}

#endif
