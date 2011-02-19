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
    SessionState(Broker&, SessionHandler&, const SessionId&,
                 const SessionState::Configuration&, bool delayManagement=false);
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

    // Used to delay creation of management object for sessions
    // belonging to inter-broker bridges
    void addManagementObject();

  private:
    void handleCommand(framing::AMQMethodBody* method, const framing::SequenceNumber& id);
    void handleContent(framing::AMQFrame& frame, const framing::SequenceNumber& id);

    // indicate that the given ingress msg has been completely received by the
    // broker, and the msg's message.transfer command can be considered completed.
    void completeRcvMsg(SequenceNumber id, bool requiresAccept, bool requiresSync);

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

    /** Abstract class that represents a command that is pending
     * completion.
     */
    class IncompleteCommandContext : public AsyncCompletion
    {
     public:
        IncompleteCommandContext( SessionState *ss, SequenceNumber _id )
          : id(_id), session(ss) {}
        virtual ~IncompleteCommandContext() {}

        /* allows manual invokation of completion, used by IO thread to
         * complete a command that was originally finished on a different
         * thread.
         */
        void do_completion() { completed(true); }

     protected:
        SequenceNumber id;
        SessionState    *session;
    };

    /** incomplete Message.transfer commands - inbound to broker from client
     */
    class IncompleteIngressMsgXfer : public SessionState::IncompleteCommandContext
    {
     public:
        IncompleteIngressMsgXfer( SessionState *ss,
                                  SequenceNumber _id,
                                  boost::intrusive_ptr<Message> msg )
          : IncompleteCommandContext(ss, _id),
          requiresAccept(msg->requiresAccept()),
          requiresSync(msg->getFrames().getMethod()->isSync()) {};
        virtual ~IncompleteIngressMsgXfer() {};

     protected:
        virtual void completed(bool);

     private:
        /** meta-info required to complete the message */
        bool requiresAccept;
        bool requiresSync;  // method's isSync() flag
    };
    /** creates a command context suitable for use as an AsyncCompletion in a message */
    boost::shared_ptr<SessionState::IncompleteIngressMsgXfer> createIngressMsgXferContext( boost::intrusive_ptr<Message> msg);

    /* A list of commands that are pending completion.  These commands are
     * awaiting some set of asynchronous operations to finish (eg: store,
     * flow-control, etc). before the command can be completed to the client
     */
    std::map<SequenceNumber, boost::shared_ptr<IncompleteCommandContext> > incompleteCmds;
    qpid::sys::Mutex incompleteCmdsLock;  // locks above container

    /** This context is shared between the SessionState and scheduledCompleter,
     * holds the sequence numbers of all commands that have completed asynchronously.
     */
    class ScheduledCompleterContext {
    private:
        std::list<SequenceNumber> completedCmds;
        // ordering: take this lock first, then incompleteCmdsLock
        qpid::sys::Mutex completedCmdsLock;
        SessionState *session;
    public:
        ScheduledCompleterContext(SessionState *s) : session(s) {};
        bool scheduleCompletion(SequenceNumber cmd);
        void completeCommands();
        void cancel();
    };
    boost::shared_ptr<ScheduledCompleterContext> scheduledCompleterContext;

    /** The following method runs the in IO thread and completes commands that
     * where finished asynchronously.
     */
    static void scheduledCompleter(boost::shared_ptr<ScheduledCompleterContext>);

    friend class SessionManager;
};


inline std::ostream& operator<<(std::ostream& out, const SessionState& session) {
    return out << session.getId();
}

}} // namespace qpid::broker



#endif  /*!QPID_BROKER_SESSION_H*/
