#ifndef QPID_AMQP_0_10_SESSIONHANDLER_H
#define QPID_AMQP_0_10_SESSIONHANDLER_H

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

#include "qpid/framing/ChannelHandler.h"
#include "qpid/framing/AMQP_AllProxy.h"
#include "qpid/framing/AMQP_AllOperations.h"
#include "qpid/SessionState.h"

namespace qpid {


namespace amqp_0_10 {

/**
 * Base SessionHandler with logic common to both client and broker.
 * 
 * A SessionHandler is associated with a channel and can be attached
 * to a session state.
 */

class SessionHandler : public framing::AMQP_AllOperations::SessionHandler,
                       public framing::FrameHandler::InOutHandler
{
  public:
    SessionHandler(framing::FrameHandler* out=0, uint16_t channel=0);
    ~SessionHandler();

    void setChannel(uint16_t ch) { channel = ch; }
    uint16_t getChannel() const { return channel.get(); }

    void setOutHandler(framing::FrameHandler& h) { channel.next = &h; }

    virtual SessionState* getState() = 0;
    virtual framing::FrameHandler* getInHandler() = 0;

    // Non-protocol methods, called locally to initiate some action.
    void sendDetach();
    void sendCompletion();
    void sendAttach(bool force);
    void sendTimeout(uint32_t t);
    void sendFlush();
    void markReadyToSend();//TODO: only needed for inter-broker bridge; cleanup

    /** True if the handler is ready to send and receive */
    bool ready() const;

    // Protocol methods
    void attach(const std::string& name, bool force);
    void attached(const std::string& name);
    void detach(const std::string& name);
    void detached(const std::string& name, uint8_t code);

    void requestTimeout(uint32_t t);
    void timeout(uint32_t t);

    void commandPoint(const framing::SequenceNumber& id, uint64_t offset);
    void expected(const framing::SequenceSet& commands, const framing::Array& fragments);
    void confirmed(const framing::SequenceSet& commands,const framing::Array& fragments);
    void completed(const framing::SequenceSet& commands, bool timelyReply);
    void knownCompleted(const framing::SequenceSet& commands);
    void flush(bool expected, bool confirmed, bool completed);
    void gap(const framing::SequenceSet& commands);    

  protected:
    virtual void invoke(const framing::AMQMethodBody& m);

    virtual void setState(const std::string& sessionName, bool force) = 0;
    virtual void channelException(framing::session::DetachCode code, const std::string& msg) = 0;
    virtual void connectionException(framing::connection::CloseCode code, const std::string& msg) = 0;
    virtual void detaching() = 0;

    // Notification of events
    virtual void readyToSend() {}
    virtual void readyToReceive() {}
    
    virtual void handleDetach();
    virtual void handleIn(framing::AMQFrame&);
    virtual void handleOut(framing::AMQFrame&);

    framing::ChannelHandler channel;
    framing::AMQP_AllProxy::Session  peer;
    bool ignoring;
    bool sendReady, receiveReady;
    std::string name;

  private:
    void sendCommandPoint(const SessionPoint&);
};
}} // namespace qpid::amqp_0_10

#endif  /*!QPID_AMQP_0_10_SESSIONHANDLER_H*/
