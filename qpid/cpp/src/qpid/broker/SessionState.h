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
#include "qpid/framing/enum.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/SequenceSet.h"
#include "qpid/sys/Time.h"
#include "qpid/management/Manageable.h"
#include "qmf/org/apache/qpid/broker/Session.h"
#include "qpid/broker/SessionAdapter.h"
#include "qpid/broker/AsyncCompletion.h"
#include "qpid/broker/MessageBuilder.h"
#include "qpid/broker/SessionContext.h"
#include "qpid/broker/SemanticState.h"
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/sys/Monitor.h"

#include <boost/noncopyable.hpp>
#include <boost/scoped_ptr.hpp>
#include <boost/intrusive_ptr.hpp>

#include <queue>
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
class SessionHandler;
class SessionManager;

/**
 * Broker-side session state includes session's handler chains, which
 * may themselves have state.
 */
class SessionState : public qpid::SessionState,
                     public SessionContext,
                     public management::Manageable,
                     public framing::FrameHandler::InOutHandler
{
  public:
    SessionState(Broker&, SessionHandler&, const SessionId&,
                 const SessionState::Configuration&);
    ~SessionState();
    bool isAttached() const { return handler; }

    void detach();
    void attach(SessionHandler& handler);
    void disableOutput();

    SessionHandler* getHandler() { return handler; }

    /** @pre isAttached() */
    framing::AMQP_ClientProxy& getProxy();

    /** @pre isAttached() */
    uint16_t getChannel() const;

    /** @pre isAttached() */
    amqp_0_10::Connection& getConnection();
    bool isLocal(const OwnershipToken* t) const;

    Broker& getBroker();

    void setTimeout(uint32_t seconds);

    void senderCompleted(const framing::SequenceSet& ranges);

    void sendCompletion();

    DeliveryId deliver(const qpid::broker::amqp_0_10::MessageTransfer& message,
                       const std::string& destination, bool isRedelivered, uint64_t ttl,
                       qpid::framing::message::AcceptMode, qpid::framing::message::AcquireMode,
                       const qpid::types::Variant::Map& annotations, bool sync);

    // Manageable entry points
    management::ManagementObject::shared_ptr GetManagementObject(void) const;
    management::Manageable::status_t
    ManagementMethod (uint32_t methodId, management::Args& args, std::string&);

    void readyToSend();

    const SessionId& getSessionId() const { return getId(); }

    /**
     * Used by ExecutionHandler sync command processing.  Notifies
     * the SessionState of a received Execution.Sync command.
     * Return true if there are incomplete commands before the execution sync.
     */
    bool addPendingExecutionSync();

    /**
     * Mark commannd ID as an execution sync point, completions will be sent
     * when all commands up to that point are completed.
     */
    bool addPendingExecutionSync(SequenceNumber id);


    void setUnackedCount(uint64_t count) {
        if (mgmtObject)
            mgmtObject->set_unackedMessages(count);
    }

    // Used to delay creation of management object for sessions
    // belonging to inter-broker bridges
    void addManagementObject();

    // transaction-related methods just to update statistics
    void startTx();
    void commitTx();
    void rollbackTx();

    /** Send result and completion for a given command to the client. */
    void completeCommand(SequenceNumber id, bool requiresAccept, bool requiresSync,
                         const std::string& result);

    MessageBuilder& getMessageBuilder() { return msgBuilder; }

  private:
    void handleCommand(framing::AMQMethodBody* method);
    void handleContent(framing::AMQFrame& frame);

    void handleIn(framing::AMQFrame& frame);
    void handleOut(framing::AMQFrame& frame);

    // End of the input & output chains.
    void handleInLast(framing::AMQFrame& frame);
    void handleOutLast(framing::AMQFrame& frame);

    void sendAcceptAndCompletion();

    Broker& broker;
    SessionHandler* handler;
    sys::AbsTime expiry;        // Used by SessionManager.
    SemanticState semanticState;
    SessionAdapter adapter;
    MessageBuilder msgBuilder;
    qmf::org::apache::qpid::broker::Session::shared_ptr mgmtObject;
    qpid::framing::SequenceSet accepted;

    // sequence numbers for pending received Execution.Sync commands
    std::queue<SequenceNumber> pendingExecutionSyncs;

  public:

    /** Information about the currently executing command.
     * Can only be used in the IO thread during command execution.
     */
    class CurrentCommand {
      public:
        CurrentCommand(
            SequenceNumber id_=0, bool syncRequired_=false, bool completeSync_=true ) :
            id(id_), syncRequired(syncRequired_), completeSync(completeSync_)
        {}

        SequenceNumber getId() const { return id; }

        /**@return true if the sync flag was set for the command. */
        bool isSyncRequired() const { return syncRequired; }

        /**@return true if the command should be completed synchronously
         * in the handling thread.
         */
        bool isCompleteSync() const { return completeSync; }
        void setCompleteSync(bool b) { completeSync = b; }

      private:
        SequenceNumber id;   ///< Command identifier.
        bool syncRequired;   ///< True if sync flag set for the command.
        bool completeSync;   ///< Will be completed by handCommand.
    };

    CurrentCommand& getCurrentCommand() { return currentCommand; }

    /** This class provides a context for completing asynchronous commands in a thread
     * safe manner.  Asynchronous commands save their completion state in this class.
     * This class then schedules the completeCommands() method in the IO thread.
     * While running in the IO thread, completeCommands() may safely complete all
     * saved commands without the risk of colliding with other operations on this
     * SessionState.
     */
    class AsyncCommandCompleter : public RefCounted {
    private:
        SessionState *session;
        bool isAttached;
        qpid::sys::Mutex completerLock;

        struct CommandInfo {
            SequenceNumber cmd; // message.transfer command id
            bool requiresAccept;
            bool requiresSync;

            CommandInfo(
                SequenceNumber c, bool a, bool s)
                : cmd(c), requiresAccept(a), requiresSync(s) {}
        };

        std::vector<CommandInfo> completedCmds;
        // If an ingress message does not require a Sync, we need to
        // hold a reference to it in case an Execution.Sync command is received and we
        // have to manually flush the message.
        std::map<SequenceNumber, boost::intrusive_ptr<qpid::broker::amqp_0_10::MessageTransfer> > pendingMsgs;

        /** complete all pending commands, runs in IO thread */
        void completeCommands();

    public:
        AsyncCommandCompleter(SessionState *s) : session(s), isAttached(s->isAttached()) {};
        ~AsyncCommandCompleter() {};

        /** track a message pending ingress completion */
        void addPendingMessage(boost::intrusive_ptr<qpid::broker::amqp_0_10::MessageTransfer> m);
        void deletePendingMessage(SequenceNumber id);
        void flushPendingMessages();
        /** schedule the processing of command completion. */
        void scheduleCommandCompletion(SequenceNumber cmd,
                                       bool requiresAccept,
                                       bool requiresSync);
        void schedule(boost::function<void()>);
        void cancel();  // called by SessionState destructor.
        void attached();  // called by SessionState on attach()
        void detached();  // called by SessionState on detach()

        SessionState* getSession() const { return session; }
    };

    boost::intrusive_ptr<AsyncCommandCompleter> getAsyncCommandCompleter() {
        return asyncCommandCompleter;
    }

    /** Abstract class that represents a single asynchronous command that is
     * pending completion.
     */
    class AsyncCommandContext : public AsyncCompletion::Callback
    {
     public:
        AsyncCommandContext(SessionState& ss )
            : id(ss.getCurrentCommand().getId()),
              requiresSync(ss.getCurrentCommand().isSyncRequired()),
              completerContext(ss.getAsyncCommandCompleter())
        {}

        virtual ~AsyncCommandContext() {}

     protected:
        SequenceNumber id;
        bool requiresSync;
        boost::intrusive_ptr<AsyncCommandCompleter> completerContext;
    };


  private:
    boost::intrusive_ptr<AsyncCommandCompleter> asyncCommandCompleter;
    CurrentCommand currentCommand;

    /** incomplete Message.transfer commands - inbound to broker from client
     */
    class IncompleteIngressMsgXfer : public SessionState::AsyncCommandContext
    {
     public:
        IncompleteIngressMsgXfer( SessionState *ss,
                                  boost::intrusive_ptr<qpid::broker::amqp_0_10::MessageTransfer> m)
          : AsyncCommandContext(*ss),
            session(ss),
            msg(m),
            requiresAccept(m->requiresAccept()),
            requiresSync(m->getFrames().getMethod()->isSync()),
            pending(false)
        {
            assert(id == m->getCommandId());
        }

        virtual ~IncompleteIngressMsgXfer() {}

        virtual void completed(bool);
        virtual boost::intrusive_ptr<AsyncCompletion::Callback> clone();

     private:
        SessionState *session;  // only valid if sync flag in callback is true
        boost::intrusive_ptr<qpid::broker::amqp_0_10::MessageTransfer> msg;
        bool requiresAccept;
        bool requiresSync;
        bool pending;   // true if msg saved on pending list...
    };

  friend class SessionManager;
};


inline std::ostream& operator<<(std::ostream& out, const SessionState& session) {
    return out << session.getId();
}

}} // namespace qpid::broker



#endif  /*!QPID_BROKER_SESSION_H*/
