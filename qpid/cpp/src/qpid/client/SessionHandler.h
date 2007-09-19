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
#ifndef _SessionHandler_
#define _SessionHandler_

#include "StateManager.h"
#include "ChainableFrameHandler.h"
#include "qpid/framing/amqp_framing.h"

namespace qpid {
namespace client {

class SessionHandler : private StateManager, public ChainableFrameHandler
{
    enum STATES {OPENING, OPEN, CLOSING, CLOSED, CLOSED_BY_PEER};
    framing::ProtocolVersion version;
    uint16_t id;
    
    uint16_t code;
    std::string text;

    void handleMethod(framing::AMQMethodBody* method);
    void closed(uint16_t code, const std::string& msg);

public:
    typedef boost::function<void(uint16_t, const std::string&)> CloseListener;    

    SessionHandler();

    void incoming(framing::AMQFrame& frame);
    void outgoing(framing::AMQFrame& frame);

    void open(uint16_t id);
    void close();
    
    CloseListener onClose;
};

}}

#endif
