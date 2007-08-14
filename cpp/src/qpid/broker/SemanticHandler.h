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
#include "BrokerChannel.h"
#include "Connection.h"
#include "DeliveryAdapter.h"
#include "qpid/framing/amqp_types.h"
#include "qpid/framing/AMQP_ServerOperations.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/SequenceNumber.h"

namespace qpid {
namespace broker {

class BrokerAdapter;
class framing::ChannelAdapter;

class SemanticHandler : private framing::ChannelAdapter, 
    private DeliveryAdapter,
    public framing::FrameHandler, 
    public framing::AMQP_ServerOperations::ExecutionHandler
{
    Connection& connection;
    Channel channel;
    std::auto_ptr<BrokerAdapter> adapter;
    framing::Window incoming;
    framing::Window outgoing;
    sys::Mutex outLock;

    void handleL4(boost::shared_ptr<qpid::framing::AMQMethodBody> method, 
                               const qpid::framing::MethodContext& context);

    //ChannelAdapter virtual methods:
    void handleMethodInContext(boost::shared_ptr<qpid::framing::AMQMethodBody> method, 
                               const qpid::framing::MethodContext& context);
    bool isOpen() const;
    void handleHeader(boost::shared_ptr<qpid::framing::AMQHeaderBody>);
    void handleContent(boost::shared_ptr<qpid::framing::AMQContentBody>);
    void handleHeartbeat(boost::shared_ptr<qpid::framing::AMQHeartbeatBody>);

    framing::RequestId send(shared_ptr<framing::AMQBody> body);


    //delivery adapter methods:
    DeliveryId deliver(Message::shared_ptr& msg, DeliveryToken::shared_ptr token);
    void redeliver(Message::shared_ptr& msg, DeliveryToken::shared_ptr token, DeliveryId tag);

public:
    SemanticHandler(framing::ChannelId id, Connection& c);

    //frame handler:
    void handle(framing::AMQFrame& frame);

    //execution class method handlers:
    void complete(uint32_t cumulativeExecutionMark, framing::SequenceNumberSet range);    
    void flush();
};

}}

#endif
