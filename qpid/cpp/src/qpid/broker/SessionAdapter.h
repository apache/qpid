#ifndef QPID_BROKER_SESSIONADAPTER_H
#define QPID_BROKER_SESSIONADAPTER_H

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
#include "qpid/broker/Session.h"
#include "qpid/framing/amqp_types.h"

namespace qpid {
namespace broker {

class Connection;
class Session;

/**
 * A SessionAdapter is associated with each active channel. It
 * receives incoming frames, handles session commands and manages the
 * association between the channel and a session.
 *
 * SessionAdapters can be stored in a map by value.
 */
class SessionAdapter : public framing::FrameHandler
{
  public:
    SessionAdapter(Connection&, framing::ChannelId);
    ~SessionAdapter();

    /** Handle AMQP session methods, pass other frames to the session
     * if there is one. Frames channel must be == getChannel()
     */
    void handle(framing::AMQFrame&);

    /** Returns 0 if not attached to a session */
    Session* getSession() const { return session.get(); }

    framing::ChannelId getChannel() const { return channel; }
    Connection& getConnection() { return connection; }
    const Connection& getConnection() const { return connection; }
    
  private:
    Connection& connection;
    const framing::ChannelId channel;
    shared_ptr<Session> session;
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_SESSIONADAPTER_H*/
