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
#include "qpid/framing/SessionState.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/OutputControl.h"
#include "qpid/sys/Time.h"
#include "qpid/management/Manageable.h"
#include "qpid/management/Session.h"

#include <boost/noncopyable.hpp>
#include <boost/scoped_ptr.hpp>

#include <set>
#include <vector>
#include <ostream>

namespace qpid {

namespace framing {
class AMQP_ClientProxy;
}

namespace broker {

class SemanticHandler;
class SessionHandler;
class SessionManager;
class Broker;
class Connection;

/**
 * Broker-side session state includes sessions handler chains, which may
 * themselves have state. 
 */
class SessionState : public framing::SessionState,
    public framing::FrameHandler::InOutHandler,
    public sys::OutputControl,
    public management::Manageable
{
  public:
    ~SessionState();
    bool isAttached() { return handler; }

    void detach();
    void attach(SessionHandler& handler);

    
    SessionHandler* getHandler();

    /** @pre isAttached() */
    framing::AMQP_ClientProxy& getProxy();
    
    /** @pre isAttached() */
    Connection& getConnection();

    uint32_t getTimeout() const { return timeout; }
    Broker& getBroker() { return broker; }
    framing::ProtocolVersion getVersion() const { return version; }

    /** OutputControl **/
    void activateOutput();

    // Manageable entry points
    management::ManagementObject::shared_ptr GetManagementObject (void) const;
    management::Manageable::status_t
        ManagementMethod (uint32_t methodId, management::Args& args);

  protected:
    void handleIn(framing::AMQFrame&);
    void handleOut(framing::AMQFrame&);
    
  private:
    // SessionManager creates sessions.
    SessionState(SessionManager&,
                 SessionHandler& out,
                 uint32_t timeout,
                 uint32_t ackInterval);
    
    SessionManager& factory;
    SessionHandler* handler;    
    framing::Uuid id;
    uint32_t timeout;
    sys::AbsTime expiry;        // Used by SessionManager.
    Broker& broker;
    framing::ProtocolVersion version;
    sys::Mutex lock;
    boost::scoped_ptr<SemanticHandler> semanticHandler;
    management::Session::shared_ptr mgmtObject;

  friend class SessionManager;
};


inline std::ostream& operator<<(std::ostream& out, const SessionState& session) {
    return out << session.getId();
}

}} // namespace qpid::broker



#endif  /*!QPID_BROKER_SESSION_H*/
