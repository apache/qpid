#ifndef QPID_BROKER_SESSIONHANDLER_H
#define QPID_BROKER_SESSIONHANDLER_H

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

#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/AMQP_ClientOperations.h"
#include "qpid/framing/AMQP_ServerOperations.h"
#include "qpid/framing/AMQP_ClientProxy.h"
#include "qpid/framing/amqp_types.h"
#include "qpid/framing/Array.h"
#include "qpid/framing/ChannelHandler.h"
#include "qpid/framing/SequenceNumber.h"
#include "qpid/framing/SequenceSet.h"

#include <boost/noncopyable.hpp>

namespace qpid {
namespace broker {

class Connection;
class ConnectionState;
class SessionState;

/**
 * A SessionHandler is associated with each active channel. It
 * receives incoming frames, handles session controls and manages the
 * association between the channel and a session.
 */
class SessionHandler : public framing::AMQP_ServerOperations::Session010Handler,
                       public framing::FrameHandler::InOutHandler,
                       private boost::noncopyable
{
  public:
    SessionHandler(Connection&, framing::ChannelId);
    ~SessionHandler();

    /** Returns 0 if not attached to a session */
    SessionState* getSession() { return session.get(); }
    const SessionState* getSession() const { return session.get(); }

    framing::ChannelId getChannel() const { return channel.get(); }
    
    ConnectionState& getConnection();
    const ConnectionState& getConnection() const;

    framing::AMQP_ClientProxy& getProxy() { return proxy; }
    const framing::AMQP_ClientProxy& getProxy() const { return proxy; }

    // Called by closing connection.
    void localSuspend();
    void detach() { localSuspend(); }
    void sendCompletion();
    
  protected:
    void handleIn(framing::AMQFrame&);
    void handleOut(framing::AMQFrame&);
    
  private:
    //new methods:
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

    //hacks for old generator:
    void commandPoint(uint32_t id, uint64_t offset) { commandPoint(framing::SequenceNumber(id), offset); }

    void assertAttached(const char* method) const;
    void assertActive(const char* method) const;
    void assertClosed(const char* method) const;

    Connection& connection;
    framing::ChannelHandler channel;
    framing::AMQP_ClientProxy proxy;
    framing::AMQP_ClientProxy::Session010 peerSession;
    bool ignoring;
    std::auto_ptr<SessionState> session;
};

}} // namespace qpid::broker



#endif  /*!QPID_BROKER_SESSIONHANDLER_H*/
