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
#include "qpid/framing/ServerInvoker.h"
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
#include <boost/intrusive_ptr.hpp>

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

    // allows commands (dispatched via handleCommand()) to inform the session
    // that they may complete asynchronously.
    void registerAsyncCommand(boost::intrusive_ptr<SessionContext::AsyncCommandContext>&);
    void cancelAsyncCommand(boost::intrusive_ptr<SessionContext::AsyncCommandContext>&);
    
  private:
    void handleCommand(framing::AMQMethodBody* method, const framing::SequenceNumber& id);
    /** finish command processing started in handleCommand() */
    void completeCommand(const framing::SequenceNumber&,
                         const framing::Invoker::Result&, bool requiresAccept,
                         bool syncBitSet);
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

    // sequence numbers of received Execution.Sync commands that are pending completion.
    std::queue<SequenceNumber> pendingExecutionSyncs;

    // true if command completes during call to handleCommand()
    bool currentCommandComplete;
    bool syncCurrentCommand;
    bool acceptRequired;

 protected:
    /** This class provides a context for completing asynchronous commands in a thread
     * safe manner.  Asynchronous commands save their completion state in this class.
     * This class then schedules the processCompletedCommands() method in the IO thread.
     * While running in the IO thread, processCompletedCommands() may safely complete all
     * saved commands without the risk of colliding with other operations on this
     * SessionState.
     */
    class AsyncCommandManager : public SessionContext::AsyncCommandManager {
    private:
        SessionState *session;
        bool isAttached;
        qpid::sys::Mutex completerLock;

        /** all commands pending completion */
        std::map<SequenceNumber, boost::intrusive_ptr<AsyncCommandContext> > pendingCommands;

        // Store information about completed commands that are pending the
        // call to completeCommands()
        struct CommandInfo {
            SequenceNumber id;
            framing::Invoker::Result results;
            bool requiresAccept;    // only if cmd==Message.transfer
            bool syncBitSet;
        CommandInfo(SequenceNumber c, const framing::Invoker::Result& r, bool a, bool s)
        : id(c), results(r), requiresAccept(a), syncBitSet(s) {}
        };
        std::vector<CommandInfo> completedCommands;

        /** finish processing all completed commands, runs in IO thread */
        void processCompletedCommands();

        /** for scheduling a run of "processCompletedCommands()" on the IO thread */
        static void schedule(boost::intrusive_ptr<AsyncCommandManager>);


    public:
        AsyncCommandManager(SessionState *s) : session(s), isAttached(s->isAttached()) {};
        ~AsyncCommandManager() {};

        /** track a message pending ingress completion */
        //void addPendingMessage(boost::intrusive_ptr<Message> m);
        //void deletePendingMessage(SequenceNumber id);
        //void flushPendingMessages();
        /** schedule the processing of a completed ingress message.transfer command */
        //void scheduleMsgCompletion(SequenceNumber cmd,
        //                           bool requiresAccept,
        //                           bool requiresSync);
        void cancel();  // called by SessionState destructor.
        void attached();  // called by SessionState on attach()
        void detached();  // called by SessionState on detach()

        /** called by async command handlers */
        void addPendingCommand(boost::intrusive_ptr<AsyncCommandContext>&,
                               framing::SequenceNumber, bool, bool);
        void cancelPendingCommand(boost::intrusive_ptr<AsyncCommandContext>&);
        void flushPendingCommands();
        void completePendingCommand(boost::intrusive_ptr<AsyncCommandContext>&, const framing::Invoker::Result&);
    };
    boost::intrusive_ptr<AsyncCommandManager> asyncCommandManager;

 private:

    /** incomplete Message.transfer commands - inbound to broker from client
     */
    class IncompleteIngressMsgXfer : public AsyncCommandContext,
                                     public AsyncCompletion::Callback
    {
     public:
        IncompleteIngressMsgXfer( SessionState *ss,
                                  boost::intrusive_ptr<Message> m )
          : session(ss),
          msg(m),
          pending(false) {}
        virtual ~IncompleteIngressMsgXfer() {};

        // async completion calls
        virtual void completed(bool);
        virtual boost::intrusive_ptr<AsyncCompletion::Callback> clone();

        // async cmd calls
        virtual void flush();

     private:
        SessionState *session;  // only valid if sync flag in callback is true
        boost::intrusive_ptr<Message> msg;
        bool pending;   // true if msg saved on pending list...
    };

    friend class SessionManager;
};


inline std::ostream& operator<<(std::ostream& out, const SessionState& session) {
    return out << session.getId();
}

}} // namespace qpid::broker



#endif  /*!QPID_BROKER_SESSION_H*/
