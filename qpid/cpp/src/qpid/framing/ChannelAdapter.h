


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

#include "qpid/shared_ptr.h"
#include "BodyHandler.h"
#include "Requester.h"
#include "Responder.h"
#include "Correlator.h"
#include "amqp_types.h"
#include "FrameHandler.h"

namespace qpid {
namespace framing {

class MethodContext;

/**
 * Base class for client and broker channels.
 *
 * Provides in/out handler chains containing channel handlers.
 * Chains may be modified by ChannelUpdaters registered with the broker.
 * 
 * The handlers provided by the ChannelAdapter update request/response data.
 *
 * send() constructs a frame, updates request/resposne ID and forwards it
 * to the out() chain.
 *
 * Thread safety: OBJECT UNSAFE. Instances must not be called
 * concurrently. AMQP defines channels to be serialized.
 */
class ChannelAdapter : private BodyHandler {
  public:
    /**
     *@param output Processed frames are forwarded to this handler.
     */
    ChannelAdapter() : id(0) {}
    virtual ~ChannelAdapter() {}

    /** Initialize the channel adapter. */
    void init(ChannelId, OutputHandler&, ProtocolVersion);

    FrameHandler::Chains& getHandlers() { return handlers; }

    ChannelId getId() const { return id; }
    ProtocolVersion getVersion() const { return version; }

    /**
     * Send a frame.
     *@param body Body of the frame.
     *@param action optional action to execute when we receive a
     *response to this frame.  Ignored if body is not a Request.
     *@return If body is a request, the ID assigned else 0.
     */
    RequestId send(shared_ptr<AMQBody> body,
                   Correlator::Action action=Correlator::Action());

    // TODO aconway 2007-04-05:  remove and use make_shared_ptr at call sites.
    /**@deprecated Use make_shared_ptr with the other send() override */
    RequestId send(AMQBody* body) { return send(AMQBody::shared_ptr(body)); }

    virtual bool isOpen() const = 0;
    
  protected:
    void assertMethodOk(AMQMethodBody& method) const;
    void assertChannelOpen() const;
    void assertChannelNotOpen() const;

    virtual void handleMethodInContext(
        shared_ptr<AMQMethodBody> method,
        const MethodContext& context) = 0;

    RequestId getFirstAckRequest() { return requester.getFirstAckRequest(); }
    RequestId getLastAckRequest() { return requester.getLastAckRequest(); }
    RequestId getNextSendRequestId() { return requester.getNextId(); }

  private:
    class ChannelAdapterHandler;
  friend class ChannelAdapterHandler;
    
    void handleMethod(shared_ptr<AMQMethodBody>);
    void handleRequest(shared_ptr<AMQRequestBody>);
    void handleResponse(shared_ptr<AMQResponseBody>);

    ChannelId id;
    ProtocolVersion version;
    Requester requester;
    Responder responder;
    Correlator correlator;
    FrameHandler::Chains handlers;
};

}}


#endif
