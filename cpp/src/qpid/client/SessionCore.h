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

#include <boost/shared_ptr.hpp>
#include "qpid/framing/AMQMethodBody.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/FrameSet.h"
#include "qpid/framing/MethodContent.h"
#include "ChannelHandler.h"
#include "ExecutionHandler.h"
#include "FutureFactory.h"
#include "Response.h"

namespace qpid {
namespace client {

class SessionCore : public framing::FrameHandler
{
    ExecutionHandler l3;
    ChannelHandler l2;
    FutureFactory futures;
    const uint16_t id;
    bool sync;
    
public:    
    typedef boost::shared_ptr<SessionCore> shared_ptr;

    SessionCore(uint16_t id, boost::shared_ptr<framing::FrameHandler> out, uint64_t maxFrameSize);
    Response send(const framing::AMQMethodBody& method, bool expectResponse = false);
    Response send(const framing::AMQMethodBody& method, const framing::MethodContent& content, bool expectResponse = false);
    framing::FrameSet::shared_ptr get();
    uint16_t getId() const { return id; } 
    void setSync(bool);
    bool isSync();
    void flush();
    void open();
    void close();
    void stop();
    void closed(uint16_t code, const std::string& text);
    
    //for incoming frames:
    void handle(framing::AMQFrame& frame);    
};

}
}


#endif
