#ifndef _OutputHandler_
#define _OutputHandler_

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
#include <boost/noncopyable.hpp>
#include "FrameHandler.h"

namespace qpid {
namespace framing {

class OutputHandler : private boost::noncopyable {
  public:
    virtual ~OutputHandler() {}
    virtual void send(AMQFrame&) = 0;
};

/** OutputHandler that delegates to a FrameHandler */
struct FrameHandlerOutputHandler : public OutputHandler {
    FrameHandlerOutputHandler(shared_ptr<FrameHandler> h) : handler(h) {}
    void received(AMQFrame& frame) { handler->handle(frame); }
    FrameHandler::Chain handler;
};

/** FrameHandler that delegates to an OutputHandler */
struct OutputHandlerFrameHandler : public FrameHandler {
    OutputHandlerFrameHandler(OutputHandler& out_) : out(out_) {}
    void handle(ParamType frame) { out.send(frame); }
    OutputHandler& out;
};


}}


#endif
