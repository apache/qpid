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
#include "qpid/broker/SessionState.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/ConnectionState.h"
#include "qpid/broker/DeliveryRecord.h"
#include "qpid/broker/SessionManager.h"
#include "qpid/broker/SessionHandler.h"
#include "qpid/broker/RateFlowcontrol.h"
#include "qpid/sys/Timer.h"
#include "qpid/framing/AMQContentBody.h"
#include "qpid/framing/AMQHeaderBody.h"
#include "qpid/framing/AMQMethodBody.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/ServerInvoker.h"
#include "qpid/log/Statement.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/framing/AMQP_ClientProxy.h"

#include <boost/bind.hpp>
#include <boost/lexical_cast.hpp>

namespace qpid {
namespace broker {

using namespace framing;
using sys::Mutex;
using boost::intrusive_ptr;
using qpid::management::ManagementAgent;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;
using qpid::sys::AbsTime;
//using qpid::sys::Timer;
namespace _qmf = qmf::org::apache::qpid::broker;

SessionState::SessionState(
    Broker& b, SessionHandler& h, const SessionId& id,
    const SessionState::Configuration& config, bool delayManagement)
    : qpid::SessionState(id, config),
      broker(b), handler(&h),
      semanticState(*this, *this),
      adapter(semanticState),
      msgBuilder(&broker.getStore()),
      mgmtObject(0),
      rateFlowcontrol(0),
      scheduledCompleterContext(new ScheduledCompleterContext(this))
{
    uint32_t maxRate = broker.getOptions().maxSessionRate;
    if (maxRate) {
        if (handler->getConnection().getClientThrottling()) {
            rateFlowcontrol.reset(new RateFlowcontrol(maxRate));
        } else {
            QPID_LOG(warning, getId() << ": Unable to flow control client - client doesn't support");
        }
    }
    if (!delayManagement) addManagementObject();
    attach(h);
}

void SessionState::addManagementObject() {
    if (GetManagementObject()) return; // Already added.
    Manageable* parent = broker.GetVhostObject ();
    if (parent != 0) {
        ManagementAgent* agent = getBroker().getManagementAgent();
        if (agent != 0) {
            mgmtObject = new _qmf::Session
                (agent, this, parent, getId().getName());
            mgmtObject->set_attached (0);
            mgmtObject->set_detachedLifespan (0);
            mgmtObject->clr_expireTime();
            if (rateFlowcontrol)
                mgmtObject->set_maxClientRate(rateFlowcontrol->getRate());
            agent->addObject(mgmtObject);
        }
    }
}

SessionState::~SessionState() {
    semanticState.closed();
    if (mgmtObject != 0)
        mgmtObject->resourceDestroy ();

    if (flowControlTimer)
        flowControlTimer->cancel();

    // clean up any outstanding incomplete commands
    {
        qpid::sys::ScopedLock<Mutex> l(incompleteCmdsLock);
        std::map<SequenceNumber, boost::shared_ptr<IncompleteCommandContext> > copy(incompleteCmds);
        incompleteCmds.clear();
        while (!copy.empty()) {
            boost::shared_ptr<IncompleteCommandContext> ref(copy.begin()->second);
            copy.erase(copy.begin());
            {
                // note: need to drop lock, as callback may attempt to take it.
                qpid::sys::ScopedUnlock<Mutex> ul(incompleteCmdsLock);
                ref->cancel();
            }
        }
    }

    // At this point, we are guaranteed no further completion callbacks will be
    // made.  Cancel any outstanding scheduledCompleter calls...
    scheduledCompleterContext->cancel();
}

AMQP_ClientProxy& SessionState::getProxy() {
    assert(isAttached());
    return handler->getProxy();
}

uint16_t SessionState::getChannel() const {
    assert(isAttached());
    return handler->getChannel();
}

ConnectionState& SessionState::getConnection() {
    assert(isAttached());
    return handler->getConnection();
}

bool SessionState::isLocal(const ConnectionToken* t) const
{
    return isAttached() && &(handler->getConnection()) == t;
}

void SessionState::detach() {
    QPID_LOG(debug, getId() << ": detached on broker.");
    disableOutput();
    handler = 0;
    if (mgmtObject != 0)
        mgmtObject->set_attached  (0);
}

void SessionState::disableOutput()
{
    semanticState.detached(); //prevents further activateOutput calls until reattached
}

void SessionState::attach(SessionHandler& h) {
    QPID_LOG(debug, getId() << ": attached on broker.");
    handler = &h;
    if (mgmtObject != 0)
    {
        mgmtObject->set_attached (1);
        mgmtObject->set_connectionRef (h.getConnection().GetManagementObject()->getObjectId());
        mgmtObject->set_channelId (h.getChannel());
    }
}

void SessionState::abort() {
    if (isAttached())
        getConnection().outputTasks.abort();
}

void SessionState::activateOutput() {
    if (isAttached())
        getConnection().outputTasks.activateOutput();
}

void SessionState::giveReadCredit(int32_t credit) {
    if (isAttached())
        getConnection().outputTasks.giveReadCredit(credit);
}

ManagementObject* SessionState::GetManagementObject (void) const
{
    return (ManagementObject*) mgmtObject;
}

Manageable::status_t SessionState::ManagementMethod (uint32_t methodId,
                                                     Args&    /*args*/,
                                                     string&  /*text*/)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;

    switch (methodId)
    {
    case _qmf::Session::METHOD_DETACH :
        if (handler != 0) {
            handler->sendDetach();
        }
        status = Manageable::STATUS_OK;
        break;

    case _qmf::Session::METHOD_CLOSE :
        /*
          if (handler != 0)
          {
          handler->getConnection().closeChannel(handler->getChannel());
          }
          status = Manageable::STATUS_OK;
          break;
        */

    case _qmf::Session::METHOD_SOLICITACK :
    case _qmf::Session::METHOD_RESETLIFESPAN :
        status = Manageable::STATUS_NOT_IMPLEMENTED;
        break;
    }

    return status;
}

void SessionState::handleCommand(framing::AMQMethodBody* method, const SequenceNumber& id) {
    currentCommandComplete = true;      // assumed, can be overridden by invoker method (this sucks).
    Invoker::Result invocation = invoke(adapter, *method);
    if (currentCommandComplete) receiverCompleted(id);

    if (!invocation.wasHandled()) {
        throw NotImplementedException(QPID_MSG("Not implemented: " << *method));
    } else if (invocation.hasResult()) {
        getProxy().getExecution().result(id, invocation.getResult());
    }

    if (method->isSync() && currentCommandComplete) {
        sendAcceptAndCompletion();
    }
}

struct ScheduledCreditTask : public sys::TimerTask {
    sys::Timer& timer;
    SessionState& sessionState;
    ScheduledCreditTask(const qpid::sys::Duration& d, sys::Timer& t,
                        SessionState& s) :
        TimerTask(d,"ScheduledCredit"),
        timer(t),
        sessionState(s)
    {}

    void fire() {
        // This is the best we can currently do to avoid a destruction/fire race
        sessionState.getConnection().requestIOProcessing(boost::bind(&ScheduledCreditTask::sendCredit, this));
    }

    void sendCredit() {
        if ( !sessionState.processSendCredit(0) ) {
            QPID_LOG(warning, sessionState.getId() << ": Reschedule sending credit");
            setupNextFire();
            timer.add(this);
        }
    }
};

void SessionState::handleContent(AMQFrame& frame, const SequenceNumber& id)
{
    if (frame.getBof() && frame.getBos()) //start of frameset
        msgBuilder.start(id);
    intrusive_ptr<Message> msg(msgBuilder.getMessage());
    msgBuilder.handle(frame);
    if (frame.getEof() && frame.getEos()) {//end of frameset
        if (frame.getBof()) {
            //i.e this is a just a command frame, add a dummy header
            AMQFrame header((AMQHeaderBody()));
            header.setBof(false);
            header.setEof(false);
            msg->getFrames().append(header);
        }
        msg->setPublisher(&getConnection());

        boost::shared_ptr<AsyncCompletion> ac(boost::dynamic_pointer_cast<AsyncCompletion>(createIngressMsgXferContext(msg)));
        msg->setIngressCompletion( ac );
        ac->begin();
        semanticState.handle(msg);
        msgBuilder.end();
        ac->end();  // allows msg to complete xfer
    }

    // Handle producer session flow control
    if (rateFlowcontrol && frame.getBof() && frame.getBos()) {
        if ( !processSendCredit(1) ) {
            QPID_LOG(debug, getId() << ": Schedule sending credit");
            sys::Timer& timer = getBroker().getTimer();
            // Use heuristic for scheduled credit of time for 50 messages, but not longer than 500ms
            sys::Duration d = std::min(sys::TIME_SEC * 50 / rateFlowcontrol->getRate(), 500 * sys::TIME_MSEC);
            flowControlTimer = new ScheduledCreditTask(d, timer, *this);
            timer.add(flowControlTimer);
        }
    }
}

bool SessionState::processSendCredit(uint32_t msgs)
{
    qpid::sys::ScopedLock<Mutex> l(rateLock);
    // Check for violating flow control
    if ( msgs > 0 && rateFlowcontrol->flowStopped() ) {
        QPID_LOG(warning, getId() << ": producer throttling violation");
        // TODO: Probably do message.stop("") first time then disconnect
        // See comment on getClusterOrderProxy() in .h file
        getClusterOrderProxy().getMessage().stop("");
        return true;
    }
    AbsTime now = AbsTime::now();
    uint32_t sendCredit = rateFlowcontrol->receivedMessage(now, msgs);
    if (mgmtObject) mgmtObject->dec_clientCredit(msgs);
    if ( sendCredit>0 ) {
        QPID_LOG(debug, getId() << ": send producer credit " << sendCredit);
        getClusterOrderProxy().getMessage().flow("", 0, sendCredit);
        rateFlowcontrol->sentCredit(now, sendCredit);
        if (mgmtObject) mgmtObject->inc_clientCredit(sendCredit);
        return true;
    } else {
        return !rateFlowcontrol->flowStopped() ;
    }
}

void SessionState::sendAcceptAndCompletion()
{
    if (!accepted.empty()) {
        getProxy().getMessage().accept(accepted);
        accepted.clear();
    }
    sendCompletion();
}

/** Invoked when the given inbound message is finished being processed
 * by all interested parties (eg. it is done being enqueued to all queues,
 * its credit has been accounted for, etc).  At this point, msg is considered
 * by this receiver as 'completed' (as defined by AMQP 0_10)
 */
void SessionState::completeRcvMsg(SequenceNumber id,
                                  bool requiresAccept,
                                  bool requiresSync)
{
    bool callSendCompletion = false;
    receiverCompleted(id);
    if (requiresAccept)
        // will cause msg's seq to appear in the next message.accept we send.
        accepted.add(id);

    // Are there any outstanding Execution.Sync commands pending the
    // completion of this msg?  If so, complete them.
    while (!pendingExecutionSyncs.empty() &&
           receiverGetIncomplete().front() >= pendingExecutionSyncs.front()) {
        const SequenceNumber id = pendingExecutionSyncs.front();
        pendingExecutionSyncs.pop();
        QPID_LOG(debug, getId() << ": delayed execution.sync " << id << " is completed.");
        receiverCompleted(id);
        callSendCompletion = true;   // likely peer is pending for this completion.
    }

    // if the sender has requested immediate notification of the completion...
    if (requiresSync) {
        sendAcceptAndCompletion();
    } else if (callSendCompletion) {
        sendCompletion();
    }
}

void SessionState::handleIn(AMQFrame& frame) {
    SequenceNumber commandId = receiverGetCurrent();
    //TODO: make command handling more uniform, regardless of whether
    //commands carry content.
    AMQMethodBody* m = frame.getMethod();
    if (m == 0 || m->isContentBearing()) {
        handleContent(frame, commandId);
    } else if (frame.getBof() && frame.getEof()) {
        handleCommand(frame.getMethod(), commandId);
    } else {
        throw InternalErrorException("Cannot handle multi-frame command segments yet");
    }
}

void SessionState::handleOut(AMQFrame& frame) {
    assert(handler);
    handler->out(frame);
}

void SessionState::deliver(DeliveryRecord& msg, bool sync)
{
    uint32_t maxFrameSize = getConnection().getFrameMax();
    assert(senderGetCommandPoint().offset == 0);
    SequenceNumber commandId = senderGetCommandPoint().command;
    msg.deliver(getProxy().getHandler(), commandId, maxFrameSize);
    assert(senderGetCommandPoint() == SessionPoint(commandId+1, 0)); // Delivery has moved sendPoint.
    if (sync) {
        AMQP_ClientProxy::Execution& p(getProxy().getExecution());
        Proxy::ScopedSync s(p);
        p.sync();
    }
}

void SessionState::sendCompletion() {
    handler->sendCompletion();
}

void SessionState::senderCompleted(const SequenceSet& commands) {
    qpid::SessionState::senderCompleted(commands);
    semanticState.completed(commands);
}

void SessionState::readyToSend() {
    QPID_LOG(debug, getId() << ": ready to send, activating output.");
    assert(handler);
    semanticState.attached();
    if (rateFlowcontrol) {
        qpid::sys::ScopedLock<Mutex> l(rateLock);
        // Issue initial credit - use a heuristic here issue min of 300 messages or 1 secs worth
        uint32_t credit = std::min(rateFlowcontrol->getRate(), 300U);
        QPID_LOG(debug, getId() << ": Issuing producer message credit " << credit);
        // See comment on getClusterOrderProxy() in .h file
        getClusterOrderProxy().getMessage().setFlowMode("", 0);
        getClusterOrderProxy().getMessage().flow("", 0, credit);
        rateFlowcontrol->sentCredit(AbsTime::now(), credit);
        if (mgmtObject) mgmtObject->inc_clientCredit(credit);
    }
}

Broker& SessionState::getBroker() { return broker; }

// Session resume is not fully implemented so it is useless to set a
// non-0 timeout. Moreover it creates problems in a cluster because
// dead sessions are kept and interfere with failover.
void SessionState::setTimeout(uint32_t) { }

framing::AMQP_ClientProxy& SessionState::getClusterOrderProxy() {
    return handler->getClusterOrderProxy();
}


// Current received command is an execution.sync command.
// Complete this command only when all preceding commands have completed.
// (called via the invoker() in handleCommand() above)
void SessionState::addPendingExecutionSync()
{
    SequenceNumber syncCommandId = receiverGetCurrent();
    if (receiverGetIncomplete().front() < syncCommandId) {
        currentCommandComplete = false;
        pendingExecutionSyncs.push(syncCommandId);
        QPID_LOG(debug, getId() << ": delaying completion of execution.sync " << syncCommandId);
    }
}


/** factory for creating IncompleteIngressMsgXfer objects which
 * can be references from Messages as ingress AsyncCompletion objects.
 */
boost::shared_ptr<SessionState::IncompleteIngressMsgXfer>
SessionState::createIngressMsgXferContext(boost::intrusive_ptr<Message> msg)
{
    SequenceNumber id = msg->getCommandId();
    boost::shared_ptr<SessionState::IncompleteIngressMsgXfer> cmd(new SessionState::IncompleteIngressMsgXfer(this, id, msg));
    qpid::sys::ScopedLock<Mutex> l(incompleteCmdsLock);
    incompleteCmds[id] = cmd;
    return cmd;
}


/** Invoked by the asynchronous completer associated with
 * a received msg that is pending Completion.  May be invoked
 * by the SessionState directly (sync == true), or some external
 * entity (!sync).
 */
void SessionState::IncompleteIngressMsgXfer::completed(bool sync)
{
    if (!sync) {
        /** note well: this path may execute in any thread.  It is safe to access
         * the session, as the SessionState destructor will cancel all outstanding
         * callbacks before getting destroyed (so we'll never get here).
         */
        QPID_LOG(debug, ": async completion callback scheduled for msg seq=" << id);
        if (session->scheduledCompleterContext->scheduleCompletion(id))
            session->getConnection().requestIOProcessing(boost::bind(&scheduledCompleter,
                                                                     session->scheduledCompleterContext));
    } else {  // command is being completed in IO thread.
        // this path runs only on the IO thread.
        qpid::sys::ScopedLock<Mutex> l(session->incompleteCmdsLock);
        std::map<SequenceNumber, boost::shared_ptr<IncompleteCommandContext> >::iterator cmd;
        cmd = session->incompleteCmds.find(id);
        if (cmd != session->incompleteCmds.end()) {
            boost::shared_ptr<IncompleteCommandContext> tmp(cmd->second);
            session->incompleteCmds.erase(cmd);

            if (session->isAttached()) {
                QPID_LOG(debug, ": receive completed for msg seq=" << id);
                qpid::sys::ScopedUnlock<Mutex> ul(session->incompleteCmdsLock);
                session->completeRcvMsg(id, requiresAccept, requiresSync);
                return;
            }
        }
    }
}


/** Scheduled from incomplete command's completed callback, safely completes all
 * completed commands in the IO Thread.  Guaranteed not to be running at the same
 * time as the message receive code.
 */
void SessionState::scheduledCompleter(boost::shared_ptr<SessionState::ScheduledCompleterContext> ctxt)
{
    ctxt->completeCommands();
}


/** mark a command (sequence) as completed, return True if caller should
 * schedule a call to completeCommands()
 */
bool SessionState::ScheduledCompleterContext::scheduleCompletion(SequenceNumber cmd)
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(completedCmdsLock);

    completedCmds.push_back(cmd);
    return (completedCmds.size() == 1);
}


/** Cause the session to complete all completed commands */
void SessionState::ScheduledCompleterContext::completeCommands()
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(completedCmdsLock);

    // when session is destroyed, it clears the session pointer via cancel().
    if (!session) return;

    while (!completedCmds.empty()) {
        SequenceNumber id = completedCmds.front();
        completedCmds.pop_front();
        std::map<SequenceNumber, boost::shared_ptr<IncompleteCommandContext> >::iterator cmd;
        {
            qpid::sys::ScopedLock<qpid::sys::Mutex> l(session->incompleteCmdsLock);

            cmd = session->incompleteCmds.find(id);
            if (cmd !=session->incompleteCmds.end()) {
                boost::shared_ptr<IncompleteCommandContext> tmp(cmd->second);
                {
                    qpid::sys::ScopedUnlock<qpid::sys::Mutex> ul(session->incompleteCmdsLock);
                    tmp->do_completion();   // retakes incompleteCmdslock
                }
            }
        }
    }
}


/** cancel any pending calls to scheduleComplete */
void SessionState::ScheduledCompleterContext::cancel()
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(completedCmdsLock);
    session = 0;
}

}} // namespace qpid::broker
