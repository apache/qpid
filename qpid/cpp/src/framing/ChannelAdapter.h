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

#include "../shared_ptr.h"
#include "BodyHandler.h"
#include "Requester.h"
#include "Responder.h"
#include "Correlator.h"
#include "amqp_types.h"

namespace qpid {
namespace framing {

class MethodContext;

// FIXME aconway 2007-02-20: Rename as ChannelBase or just Channel.

/**
 * Base class for client and broker channels.
 *
 * - receives frame bodies from the network.
 * - Updates request/response data.
 * - Dispatches requests with a MethodContext for responses.
 *
 * send()
 * - Updates request/resposne ID data.
 * - Forwards frame to the peer.
 *
 * Thread safety: OBJECT UNSAFE. Instances must not be called
 * concurrently. AMQP defines channels to be serialized.
 */
class ChannelAdapter : public BodyHandler {
  public:
    /**
     *@param output Processed frames are forwarded to this handler.
     */
    ChannelAdapter(ChannelId id_=0, OutputHandler* out_=0,
                   ProtocolVersion ver=ProtocolVersion())
        : id(id_), out(out_), version(ver)  {}

    /** Initialize the channel adapter. */
    void init(ChannelId, OutputHandler&, ProtocolVersion);

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

    void handleMethod(shared_ptr<AMQMethodBody>);
    void handleRequest(shared_ptr<AMQRequestBody>);
    void handleResponse(shared_ptr<AMQResponseBody>);

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
    ChannelId id;
    OutputHandler* out;
    ProtocolVersion version;
    Requester requester;
    Responder responder;
    Correlator correlator;
};

}}


#endif
