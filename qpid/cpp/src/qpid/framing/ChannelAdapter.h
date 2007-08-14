


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
#include "ProtocolVersion.h"
#include "amqp_types.h"
#include "FrameHandler.h"

namespace qpid {
namespace framing {

class OutputHandler;

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
class ChannelAdapter : protected BodyHandler {
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

    virtual void send(shared_ptr<AMQBody> body);

    virtual bool isOpen() const = 0;
    
  protected:
    void assertMethodOk(AMQMethodBody& method) const;
    void assertChannelOpen() const;
    void assertChannelNotOpen() const;

    virtual void handleMethod(shared_ptr<AMQMethodBody>) = 0;

  private:
    class ChannelAdapterHandler;
    friend class ChannelAdapterHandler;
    
    ChannelId id;
    ProtocolVersion version;
    FrameHandler::Chains handlers;
};

}}


#endif
