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
#ifndef _ConnectionHandler_
#define _ConnectionHandler_

#include "ChainableFrameHandler.h"
#include "ConnectionSettings.h"
#include "StateManager.h"
#include "qpid/framing/AMQMethodBody.h"
#include "qpid/framing/AMQP_HighestVersion.h"
#include "qpid/framing/AMQP_ClientOperations.h"
#include "qpid/framing/AMQP_ServerProxy.h"
#include "qpid/framing/Array.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/InputHandler.h"

namespace qpid {
namespace client {

class ConnectionHandler : private StateManager,
                          public ConnectionSettings,
                          public ChainableFrameHandler,
                          public framing::InputHandler,
                          private framing::AMQP_ClientOperations::ConnectionHandler
{
    typedef framing::AMQP_ClientOperations::ConnectionHandler ConnectionOperations;
    enum STATES {NOT_STARTED, NEGOTIATING, OPENING, OPEN, CLOSING, CLOSED, FAILED};
    std::set<int> ESTABLISHED;

    class Adapter : public framing::FrameHandler
    {
        ConnectionHandler& handler;
    public:
        Adapter(ConnectionHandler& h) : handler(h) {}
        void handle(framing::AMQFrame& f) { handler.out(f); }
    }; 

    Adapter outHandler;
    framing::AMQP_ServerProxy::Connection proxy;
    uint16_t errorCode;
    std::string errorText;
    bool insist;
    framing::ProtocolVersion version;
    framing::Array capabilities;
    framing::FieldTable properties;

    void checkState(STATES s, const std::string& msg);

    //methods corresponding to connection controls:
    void start(const framing::FieldTable& serverProperties,
               const framing::Array& mechanisms,
               const framing::Array& locales);    
    void secure(const std::string& challenge);    
    void tune(uint16_t channelMax,
              uint16_t frameMax,
              uint16_t heartbeatMin,
              uint16_t heartbeatMax);    
    void openOk(const framing::Array& knownHosts);    
    void redirect(const std::string& host,
                  const framing::Array& knownHosts);    
    void close(uint16_t replyCode, const std::string& replyText);    
    void closeOk();

public:
    using InputHandler::handle;
    typedef boost::function<void()> CloseListener;    
    typedef boost::function<void(uint16_t, const std::string&)> ErrorListener;    

    ConnectionHandler(const ConnectionSettings&, framing::ProtocolVersion&);

    void received(framing::AMQFrame& f) { incoming(f); } 

    void incoming(framing::AMQFrame& frame);
    void outgoing(framing::AMQFrame& frame);

    void waitForOpen();
    void close();
    void fail(const std::string& message);

    // Note that open and closed aren't related by open = !closed
    bool isOpen() const;
    bool isClosed() const;

    CloseListener onClose;
    ErrorListener onError;
};

}}

#endif
