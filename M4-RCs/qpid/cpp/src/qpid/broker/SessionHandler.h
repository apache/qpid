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

#include "qpid/amqp_0_10/SessionHandler.h"
#include "qpid/framing/AMQP_ClientProxy.h"

namespace qpid {
class SessionState;

namespace broker {

class Connection;
class ConnectionState;
class SessionState;

/**
 * A SessionHandler is associated with each active channel. It
 * receives incoming frames, handles session controls and manages the
 * association between the channel and a session.
 */
class SessionHandler : public amqp_0_10::SessionHandler {
  public:
    SessionHandler(Connection&, framing::ChannelId);
    ~SessionHandler();

    /** Get broker::SessionState */
    SessionState* getSession() { return session.get(); }
    const SessionState* getSession() const { return session.get(); }

    ConnectionState& getConnection();
    const ConnectionState& getConnection() const;

    framing::AMQP_ClientProxy& getProxy() { return proxy; }
    const framing::AMQP_ClientProxy& getProxy() const { return proxy; }

    virtual void handleDetach();
    
    // Overrides
    void attached(const std::string& name);

  protected:
    virtual void setState(const std::string& sessionName, bool force);
    virtual qpid::SessionState* getState();
    virtual framing::FrameHandler* getInHandler();
    virtual void channelException(framing::session::DetachCode code, const std::string& msg);
    virtual void connectionException(framing::connection::CloseCode code, const std::string& msg);
    virtual void detaching();
    virtual void readyToSend();

  private:
    Connection& connection;
    framing::AMQP_ClientProxy proxy;
    std::auto_ptr<SessionState> session;
};

}} // namespace qpid::broker



#endif  /*!QPID_BROKER_SESSIONHANDLER_H*/
