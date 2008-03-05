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
#include "qpid/framing/SequenceSet.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Time.h"
#include "qpid/management/Manageable.h"
#include "qpid/management/Session.h"
#include "SessionAdapter.h"
#include "DeliveryAdapter.h"
#include "MessageBuilder.h"
#include "SessionContext.h"
#include "SemanticState.h"
#include "IncomingExecutionContext.h"

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

class SessionHandler;
class SessionManager;
class Broker;
class ConnectionState;

/**
 * Broker-side session state includes sessions handler chains, which may
 * themselves have state. 
 */
class SessionState : public framing::SessionState,
    public SessionContext,
    public DeliveryAdapter,
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
    ConnectionState& getConnection();

    uint32_t getTimeout() const { return timeout; }
    void setTimeout(uint32_t t) { timeout = t; }

    Broker& getBroker() { return broker; }
    framing::ProtocolVersion getVersion() const { return version; }

    /** OutputControl **/
    void activateOutput();

    void handle(framing::AMQFrame& frame);

    void complete(const framing::SequenceSet& ranges);    
    void sendCompletion();

    //delivery adapter methods:
    DeliveryId deliver(QueuedMessage& msg, DeliveryToken::shared_ptr token);

    // Manageable entry points
    management::ManagementObject::shared_ptr GetManagementObject (void) const;
    management::Manageable::status_t
        ManagementMethod (uint32_t methodId, management::Args& args);

    // Normally SessionManager creates sessions.
    SessionState(SessionManager*,
                 SessionHandler* out,
                 uint32_t timeout,
                 uint32_t ackInterval);
    

    framing::SequenceSet completed;
    framing::SequenceSet knownCompleted;
    framing::SequenceNumber nextIn;
    framing::SequenceNumber nextOut;

  private:
    typedef boost::function<void(DeliveryId, DeliveryId)> RangedOperation;    

    SessionManager* factory;
    SessionHandler* handler;    
    framing::Uuid id;
    uint32_t timeout;
    sys::AbsTime expiry;        // Used by SessionManager.
    Broker& broker;
    framing::ProtocolVersion version;
    sys::Mutex lock;

    SemanticState semanticState;
    SessionAdapter adapter;
    MessageBuilder msgBuilder;

    RangedOperation ackOp;

    management::Session::shared_ptr mgmtObject;
    void handleCommand(framing::AMQMethodBody* method, framing::SequenceNumber& id);
    void handleContent(framing::AMQFrame& frame, framing::SequenceNumber& id);

    friend class SessionManager;
};


inline std::ostream& operator<<(std::ostream& out, const SessionState& session) {
    return out << session.getId();
}

}} // namespace qpid::broker



#endif  /*!QPID_BROKER_SESSION_H*/
