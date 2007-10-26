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
#include "qpid/framing/AMQP_ServerOperations.h"
#include "qpid/framing/AMQP_ClientProxy.h"
#include "qpid/framing/amqp_types.h"
#include "qpid/framing/ChannelHandler.h"

#include <boost/noncopyable.hpp>

namespace qpid {
namespace broker {

class Connection;
class SessionState;

/**
 * A SessionHandler is associated with each active channel. It
 * receives incoming frames, handles session commands and manages the
 * association between the channel and a session.
 */
class SessionHandler : public framing::FrameHandler::InOutHandler,
                       public framing::AMQP_ServerOperations::SessionHandler,
                       private boost::noncopyable
{
  public:
    SessionHandler(Connection&, framing::ChannelId);
    ~SessionHandler();

    /** Returns 0 if not attached to a session */
    SessionState* getSession() { return session.get(); }
    const SessionState* getSession() const { return session.get(); }

    framing::ChannelId getChannel() const { return channel.get(); }
    
    Connection& getConnection() { return connection; }
    const Connection& getConnection() const { return connection; }

    framing::AMQP_ClientProxy& getProxy() { return proxy; }
    const framing::AMQP_ClientProxy& getProxy() const { return proxy; }

    // Called by closing connection.
    void localSuspend();
    
  protected:
    void handleIn(framing::AMQFrame&);
    void handleOut(framing::AMQFrame&);
    
  private:
    /// Session methods
    void open(uint32_t detachedLifetime);
    void flow(bool active);
    void flowOk(bool active);
    void close();
    void closed(uint16_t replyCode, const std::string& replyText);
    void resume(const framing::Uuid& sessionId);
    void suspend();
    void ack(uint32_t cumulativeSeenMark,
             const framing::SequenceNumberSet& seenFrameSet);
    void highWaterMark(uint32_t lastSentMark);
    void solicitAck();


    void assertAttached(const char* method) const;
    void assertActive(const char* method) const;
    void assertClosed(const char* method) const;

    Connection& connection;
    framing::ChannelHandler channel;
    framing::AMQP_ClientProxy proxy;
    framing::AMQP_ClientProxy::Session peerSession;
    bool ignoring;
    std::auto_ptr<SessionState> session;
};

}} // namespace qpid::broker



#endif  /*!QPID_BROKER_SESSIONHANDLER_H*/
