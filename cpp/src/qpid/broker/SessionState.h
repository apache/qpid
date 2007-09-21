#ifndef QPID_BROKER_SESSION_H
#define QPID_BROKER_SESSION_H

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

#include "qpid/framing/Uuid.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/ProtocolVersion.h"

#include <boost/ptr_container/ptr_vector.hpp>
#include <boost/noncopyable.hpp>


namespace qpid {

namespace framing {
class AMQP_ClientProxy;
}

namespace broker {

class SessionHandler;
class Broker;
class Connection;

/**
 * State of a session.
 *
 * An attached session has a SessionHandler which is attached to a
 * connection. A suspended session has no handler.
 *
 * A SessionState is always associated with an open session (attached or
 * suspended) it is destroyed when the session is closed.
 *
 * The SessionState includes the sessions handler chains, which may
 * themselves have state. The handlers will be preserved as long as
 * the session is alive.
 */
class SessionState : public framing::FrameHandler::Chains,
                     private boost::noncopyable
{
  public:
    /** SessionState for a newly opened connection. */
    SessionState(SessionHandler& h, uint32_t timeout_);

    bool isAttached() { return handler; }

    /** @pre isAttached() */
    SessionHandler& getHandler();

    /** @pre isAttached() */
    framing::AMQP_ClientProxy& getProxy();
    
    /** @pre isAttached() */
    Connection& getConnection();

    const framing::Uuid& getId() const { return id; }
    uint32_t getTimeout() const { return timeout; }
    Broker& getBroker() { return broker; }
    framing::ProtocolVersion getVersion() const { return version; }
    

  private:
  friend class SessionHandler;  // Only SessionHandler can attach/detach
    void detach() { handler=0; }
    void attach(SessionHandler& h) { handler = &h; }

    SessionHandler* handler;    
    framing::Uuid id;
    uint32_t timeout;
    Broker& broker;
    boost::ptr_vector<framing::FrameHandler> chain;
    framing::ProtocolVersion version;
};

}} // namespace qpid::broker



#endif  /*!QPID_BROKER_SESSION_H*/
