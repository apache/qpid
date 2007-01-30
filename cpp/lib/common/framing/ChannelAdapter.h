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
 * - receives frame bodies from the network.
 * - Updates request/response data.
 * - Dispatches requests with a MethodContext for responses.
 *
 * As OutputHandler:
 * - Updates request/resposne ID data.
 * - Forwards frame to the peer.
 *
 * Thread safety: OBJECT UNSAFE. Instances must not be called
 * concurrently. AMQP defines channels to be serialized.
 */
class ChannelAdapter : public BodyHandler, public OutputHandler {
  public:
    /**
     *@param output Processed frames are forwarded to this handler.
     */
    ChannelAdapter() : context(0), id(0), out(0) {}

    /** Initialize the channel adapter. */
    void init(ChannelId, OutputHandler&, const ProtocolVersion&);

    ChannelId getId() const { return id; }
    const ProtocolVersion& getVersion() const { return version; }
    
    /**
     * Do request/response-id processing and then forward to
     * handler provided to constructor. Response frames should
     * have their request-id set before calling send.
     */
    void send(AMQFrame* frame);
    /**
     * Wrap body in a frame and send the frame.
     * Takes ownership of body.
     */
    void send(AMQBody::shared_ptr body);
    void send(AMQBody* body) { send(AMQBody::shared_ptr(body)); }

    void handleMethod(boost::shared_ptr<qpid::framing::AMQMethodBody>);
    void handleRequest(boost::shared_ptr<qpid::framing::AMQRequestBody>);
    void handleResponse(boost::shared_ptr<qpid::framing::AMQResponseBody>);

    virtual bool isOpen() const = 0;
    
  protected:
    void assertMethodOk(AMQMethodBody& method) const;
    void assertChannelOpen() const;
    void assertChannelNotOpen() const;

    virtual void handleMethodInContext(
        boost::shared_ptr<qpid::framing::AMQMethodBody> method,
        const MethodContext& context) = 0;

    RequestId getRequestInProgress() { return requestInProgress; }

  protected:
    MethodContext context;
    
  private:
    ChannelId id;
    OutputHandler* out;
    ProtocolVersion version;
    Requester requester;
    Responder responder;
    RequestId requestInProgress; // TODO aconway 2007-01-24: use it.
};

}}


#endif
