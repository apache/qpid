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
#include "qpid/broker/SessionAdapter.h"
#include "qpid/broker/DeliveryAdapter.h"
#include "qpid/broker/AsyncCompletion.h"
#include "qpid/broker/MessageBuilder.h"
#include "qpid/broker/SessionContext.h"
#include "qpid/broker/SemanticState.h"
#include "qpid/sys/Monitor.h"

#include <boost/noncopyable.hpp>
#include <boost/scoped_ptr.hpp>

#include <set>
#include <vector>
#include <ostream>

namespace qpid {

namespace framing {
class AMQP_ClientProxy;
}

namespace sys {
class TimerTask;
}

namespace broker {

class Broker;
class ConnectionState;
class Message;
class SessionHandler;
class SessionManager;
class RateFlowcontrol;

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
    uint16_t getChannel() const;

    /** @pre isAttached() */
    ConnectionState& getConnection();
    bool isLocal(const ConnectionToken* t) const;

    Broker& getBroker();

    void setTimeout(uint32_t seconds);

    /** OutputControl **/
    void abort();
    void activateOutput();
    void giveReadCredit(int32_t);

    void senderCompleted(const framing::SequenceSet& ranges);

    void sendCompletion();

    //delivery adapter methods:
    void deliver(DeliveryRecord&, bool sync);

    // Manageable entry points
    management::ManagementObject* GetManagementObject (void) const;
    management::Manageable::status_t
    ManagementMethod (uint32_t methodId, management::Args& args, std::string&);

    void readyToSend();

    // Used by cluster to create replica sessions.
    SemanticState& getSemanticState() { return semanticState; }
    boost::intrusive_ptr<Message> getMessageInProgress() { return msgBuilder.getMessage(); }
    SessionAdapter& getSessionAdapter() { return adapter; }

    bool processSendCredit(uint32_t msgs);

    const SessionId& getSessionId() const { return getId(); }

    // Used by ExecutionHandler sync command processing.  Notifies
    // the SessionState of a received Execution.Sync command.
    void addPendingExecutionSync();

  private:

    void handleCommand(framing::AMQMethodBody* method, const framing::SequenceNumber& id);
    void handleContent(framing::AMQFrame& frame, const framing::SequenceNumber& id);

    // indicate that the given ingress msg has been completely received by the
    // broker, and the msg's message.transfer command can be considered completed.
    void completeRcvMsg(boost::intrusive_ptr<qpid::broker::Message> msg);

    void handleIn(framing::AMQFrame& frame);
    void handleOut(framing::AMQFrame& frame);

    // End of the input & output chains.
    void handleInLast(framing::AMQFrame& frame);
    void handleOutLast(framing::AMQFrame& frame);

    void sendAcceptAndCompletion();

    /**
     * If commands are sent based on the local time (e.g. in timers), they don't have
     * a well-defined ordering across cluster nodes.
     * This proxy is for sending such commands. In a clustered broker it will take steps
     * to synchronize command order across the cluster. In a stand-alone broker
     * it is just a synonym for getProxy()
     */
    framing::AMQP_ClientProxy& getClusterOrderProxy();

    Broker& broker;
    SessionHandler* handler;
    sys::AbsTime expiry;        // Used by SessionManager.
    SemanticState semanticState;
    SessionAdapter adapter;
    MessageBuilder msgBuilder;
    qmf::org::apache::qpid::broker::Session* mgmtObject;
    qpid::framing::SequenceSet accepted;

    // State used for producer flow control (rate limited)
    qpid::sys::Mutex rateLock;
    boost::scoped_ptr<RateFlowcontrol> rateFlowcontrol;
    boost::intrusive_ptr<sys::TimerTask> flowControlTimer;

    // sequence numbers for pending received Execution.Sync commands
    std::queue<SequenceNumber> pendingExecutionSyncs;
    bool currentCommandComplete;

    // A list of ingress messages whose message.transfer command is pending
    // completion.  These messages are awaiting some set of asynchronous
    // operations to complete (eg: store, flow-control, etc). before
    // the message.transfer can be completed.
    class IncompleteRcvMsg : public AsyncCompletion::CompletionHandler
    {
  public:
        IncompleteRcvMsg(SessionState& _session, boost::intrusive_ptr<Message> _msg)
          : session(&_session), msg(_msg) {}
        virtual void operator() (bool sync);    // invoked when msg is completed.
        void cancel();   // cancel pending incomplete callback [operator() above].

        typedef boost::shared_ptr<IncompleteRcvMsg>  shared_ptr;
        typedef std::deque<shared_ptr> deque;

  private:
        SessionState *session;
        boost::intrusive_ptr<Message> msg;

        static void scheduledCompleter(boost::shared_ptr<deque>);
    };
    std::map<const IncompleteRcvMsg *, IncompleteRcvMsg::shared_ptr> incompleteRcvMsgs;  // msgs pending completion
    qpid::sys::Mutex incompleteRcvMsgsLock;
    boost::shared_ptr<IncompleteRcvMsg> createPendingMsg(boost::intrusive_ptr<Message>& msg) {
        boost::shared_ptr<IncompleteRcvMsg> pending(new IncompleteRcvMsg(*this, msg));
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(incompleteRcvMsgsLock);
        incompleteRcvMsgs[pending.get()] = pending;
        return pending;
    }

    // holds msgs waiting for IO thread to run scheduledCompleter()
    boost::shared_ptr<IncompleteRcvMsg::deque> scheduledRcvMsgs;

    friend class SessionManager;
    friend class IncompleteRcvMsg;
};


inline std::ostream& operator<<(std::ostream& out, const SessionState& session) {
    return out << session.getId();
}

}} // namespace qpid::broker



#endif  /*!QPID_BROKER_SESSION_H*/
