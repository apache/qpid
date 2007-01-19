#ifndef _ChannelAdapter_
#define _ChannelAdapter_

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

#include <boost/shared_ptr.hpp>

#include "BodyHandler.h"
#include "Requester.h"
#include "Responder.h"
#include "OutputHandler.h"

namespace qpid {
namespace framing {

class MethodContext;

/**
 * Base class for client and broker channel adapters.
 *
 * As BodyHandler:
 * - Creates MethodContext and dispatches methods+context to derived class.
 * - Updates request/response ID data.
 *
 * As OutputHandler:
 * - Updates request/resposne ID data.
 * 
 */
class ChannelAdapter : public BodyHandler, public OutputHandler {
  public:
    /**
     *@param output Processed frames are forwarded to this handler.
     */
    ChannelAdapter(OutputHandler& output, ChannelId channelId) 
        : id(channelId), out(output) {}

    ChannelId getId() { return id; }
    
    /**
     * Do request/response-id processing and then forward to
     * handler provided to constructor. Response frames should
     * have their request-id set before calling send.
     */
    void send(AMQFrame* frame);

    void handleMethod(boost::shared_ptr<qpid::framing::AMQMethodBody>);
    void handleRequest(boost::shared_ptr<qpid::framing::AMQRequestBody>);
    void handleResponse(boost::shared_ptr<qpid::framing::AMQResponseBody>);

  protected:
    /** Throw protocol exception if this is not channel 0. */
    static void assertChannelZero(u_int16_t id);
    /** Throw protocol exception if this is channel 0. */
    static void assertChannelNonZero(u_int16_t id);

    virtual void handleMethodInContext(
        boost::shared_ptr<qpid::framing::AMQMethodBody> method,
        const MethodContext& context) = 0;

    ChannelId id;

  private:
    Requester requester;
    Responder responder;
    OutputHandler& out;
};

}}


#endif
