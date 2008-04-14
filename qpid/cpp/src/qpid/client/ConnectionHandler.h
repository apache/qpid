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

#include "Connector.h"
#include "StateManager.h"
#include "ChainableFrameHandler.h"
#include "qpid/framing/InputHandler.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/AMQMethodBody.h"

namespace qpid {
namespace client {

struct ConnectionProperties
{
    std::string uid;
    std::string pwd;
    std::string vhost;
    framing::FieldTable properties;
    std::string mechanism;
    std::string locale;
    std::string capabilities;
    uint16_t heartbeat;
    uint16_t maxChannels;
    uint64_t maxFrameSize;
    bool insist;
    framing::ProtocolVersion version;
};

class ConnectionHandler : private StateManager, 
     public ConnectionProperties, 
     public ChainableFrameHandler, 
     public framing::InputHandler
{
    enum STATES {NOT_STARTED, NEGOTIATING, OPENING, OPEN, CLOSING, CLOSED, FAILED};
    std::set<int> ESTABLISHED;

    void handle(framing::AMQMethodBody* method);
    void send(const framing::AMQBody& body);
    void error(uint16_t code, const std::string& message, uint16_t classId = 0, uint16_t methodId = 0);
    void error(uint16_t code, const std::string& message, framing::AMQBody* body);

public:
    using InputHandler::handle;
    typedef boost::function<void()> CloseListener;    
    typedef boost::function<void(uint16_t, const std::string&)> ErrorListener;    

    ConnectionHandler();

    void received(framing::AMQFrame& f) { incoming(f); } 

    void incoming(framing::AMQFrame& frame);
    void outgoing(framing::AMQFrame& frame);

    void waitForOpen();
    void close();
    void fail(const std::string& message);

    CloseListener onClose;
    ErrorListener onError;
};

}}

#endif
