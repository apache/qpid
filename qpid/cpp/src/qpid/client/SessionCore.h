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

#ifndef _SessionCore_
#define _SessionCore_

#include <boost/function.hpp>
#include <boost/shared_ptr.hpp>
#include "qpid/framing/AMQMethodBody.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/FrameSet.h"
#include "qpid/framing/MethodContent.h"
#include "qpid/framing/Uuid.h"
#include "SessionHandler.h"
#include "ExecutionHandler.h"

namespace qpid {
namespace client {

class Future;
class ConnectionImpl;

/**
 * Session implementation, sets up handler chains.
 * Attaches to a SessionHandler when active, detaches
 * when closed.
 */
class SessionCore : public framing::FrameHandler::InOutHandler
{
    struct Reason
    {
        uint16_t code;
        std::string text;
    };

    shared_ptr<ConnectionImpl> connection;
    uint16_t channel;
    SessionHandler l2;
    ExecutionHandler l3;
    framing::Uuid uuid;
    bool sync;
    Reason reason;

  protected:
    void handleIn(framing::AMQFrame& frame);
    void handleOut(framing::AMQFrame& frame);

  public:
    SessionCore(shared_ptr<ConnectionImpl>, uint16_t channel, uint64_t maxFrameSize);
    ~SessionCore();

    framing::FrameSet::shared_ptr get();

    framing::Uuid getId() const { return uuid; } 
    void setId(const framing::Uuid& id)  { uuid= id; }
        
    uint16_t getChannel() const { assert(channel); return channel; }
    void setChannel(uint16_t ch) { assert(ch); channel=ch; }

    void open(uint32_t detachedLifetime);

    /** Closed by client code */
    void close();

    /** Closed by peer */
    void closed(uint16_t code, const std::string& text);

    void resume(shared_ptr<ConnectionImpl>);
    void suspend();

    void setSync(bool);
    bool isSync();
    ExecutionHandler& getExecution();
    void checkClosed() const;

    Future send(const framing::AMQBody& command);
    Future send(const framing::AMQBody& command, const framing::MethodContent& content);
};

}
}


#endif
