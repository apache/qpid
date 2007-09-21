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
#ifndef _ConnectionAdapter_
#define _ConnectionAdapter_

#include <memory>
#include "qpid/framing/amqp_types.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/AMQP_ServerOperations.h"
#include "qpid/framing/AMQP_ClientProxy.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/Exception.h"

namespace qpid {
namespace broker {

class Connection;

// TODO aconway 2007-09-18: Rename to ConnectionHandler
class ConnectionHandler : public framing::FrameHandler
{
    struct Handler : public framing::AMQP_ServerOperations::ConnectionHandler
    {
        framing::AMQP_ClientProxy::Connection client;
        Connection& connection;
    
        Handler(Connection& connection);
        void startOk(const qpid::framing::FieldTable& clientProperties,
                     const std::string& mechanism, const std::string& response,
                     const std::string& locale); 
        void secureOk(const std::string& response); 
        void tuneOk(uint16_t channelMax, uint32_t frameMax, uint16_t heartbeat); 
        void open(const std::string& virtualHost,
                  const std::string& capabilities, bool insist); 
        void close(uint16_t replyCode, const std::string& replyText,
                   uint16_t classId, uint16_t methodId); 
        void closeOk(); 
    };
    std::auto_ptr<Handler> handler;
  public:
    ConnectionHandler(Connection& connection);
    void init(const framing::ProtocolInitiation& header);
    void close(framing::ReplyCode code, const std::string& text, framing::ClassId classId, framing::MethodId methodId);
    void handle(framing::AMQFrame& frame);
};


}}

#endif
