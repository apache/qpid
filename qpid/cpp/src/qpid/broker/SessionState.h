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

#include "qpid/SessionState.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/SequenceSet.h"
#include "qpid/sys/Time.h"
#include "qpid/management/Manageable.h"
#include "qmf/org/apache/qpid/broker/Session.h"
#include "SessionAdapter.h"
#include "DeliveryAdapter.h"
#include "IncompleteMessageList.h"
#include "MessageBuilder.h"
#include "SessionContext.h"
#include "SemanticState.h"

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

class Broker;
class ConnectionState;
class Message;
class SessionHandler;
class SessionManager;

/**
 * Broker-side session state includes session's handler chains, which
 * may themselves have state.
 */
class SessionState : public qpid::SessionState, 
                     public SessionContext,
                     public DeliveryAdapter,
                     public management::Manageable,
                     public framing::FrameHandler::InOutHandler
{
  public:
    SessionState(Broker&, SessionHandler&, const SessionId&, const SessionState::Configuration&);
    ~SessionState();
    bool isAttached() const { return handler; }

    void detach();
    void attach(SessionHandler& handler);
    void disableOutput();

    /** @pre isAttached() */
    framing::AMQP_ClientProxy& getProxy();
    
    /** @pre isAttached() */
    ConnectionState& getConnection();
    bool isLocal(const ConnectionToken* t) const;

    Broker& getBroker();

    /** OutputControl **/
    void activateOutput();
    void giveReadCredit(int32_t);

    void senderCompleted(const framing::SequenceSet& ranges);
    
    void sendCompletion();

    //delivery adapter methods:
    void deliver(DeliveryRecord&);

    // Manageable entry points
    management::ManagementObject* GetManagementObject (void) const;
    management::Manageable::status_t
        ManagementMethod (uint32_t methodId, management::Args& args, std::string&);

    void readyToSend();

    // Used by cluster to create replica sessions.
    SemanticState& getSemanticState() { return semanticState; }
    boost::intrusive_ptr<Message> getMessageInProgress() { return msgBuilder.getMessage(); }

  private:

    void handleCommand(framing::AMQMethodBody* method, const framing::SequenceNumber& id);
    void handleContent(framing::AMQFrame& frame, const framing::SequenceNumber& id);
    void enqueued(boost::intrusive_ptr<Message> msg);

    void handleIn(framing::AMQFrame& frame);
    void handleOut(framing::AMQFrame& frame);

    // End of the input & output chains.
    void handleInLast(framing::AMQFrame& frame);
    void handleOutLast(framing::AMQFrame& frame);

    Broker& broker;
    SessionHandler* handler;    
    sys::AbsTime expiry;        // Used by SessionManager.
    SemanticState semanticState;
    SessionAdapter adapter;
    MessageBuilder msgBuilder;
    IncompleteMessageList incomplete;
    IncompleteMessageList::CompletionListener enqueuedOp;
    qmf::org::apache::qpid::broker::Session* mgmtObject;

  friend class SessionManager;
};


inline std::ostream& operator<<(std::ostream& out, const SessionState& session) {
    return out << session.getId();
}

}} // namespace qpid::broker



#endif  /*!QPID_BROKER_SESSION_H*/
