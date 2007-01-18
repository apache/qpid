#ifndef _BodyHandler_
#define _BodyHandler_

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

#include "Requester.h"
#include "Responder.h"

namespace qpid {
namespace framing {

class AMQRequestBody;
class AMQResponseBody;
class AMQMethodBody;
class AMQHeaderBody;
class AMQContentBody;
class AMQHeartbeatBody;

/**
 * Base class for client and broker channel handlers.
 * 
 * Handles request/response id management common to client and broker.
 * Derived classes provide remaining client/broker specific handling.
 */
class BodyHandler {
  public:
    virtual ~BodyHandler();
    
    void handleBody(boost::shared_ptr<AMQBody> body);

  protected:
    virtual void handleRequest(boost::shared_ptr<AMQRequestBody>);
    virtual void handleResponse(boost::shared_ptr<AMQResponseBody>);

    virtual void handleMethod(boost::shared_ptr<AMQMethodBody>) = 0;
    virtual void handleHeader(boost::shared_ptr<AMQHeaderBody>) = 0;
    virtual void handleContent(boost::shared_ptr<AMQContentBody>) = 0;
    virtual void handleHeartbeat(boost::shared_ptr<AMQHeartbeatBody>) = 0;

  protected:
    /** Throw protocol exception if this is not channel 0. */
    static void assertChannelZero(u_int16_t id);
    /** Throw protocol exception if this is channel 0. */
    static void assertChannelNonZero(u_int16_t id);

  private:
    Requester requester;
    Responder responder;
};

}}


#endif
