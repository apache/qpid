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
#include "SessionState.h"
#include "Broker.h"
#include "ConnectionState.h"
#include "MessageDelivery.h"
#include "SessionManager.h"
#include "SessionHandler.h"
#include "qpid/framing/AMQContentBody.h"
#include "qpid/framing/AMQHeaderBody.h"
#include "qpid/framing/AMQMethodBody.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/ServerInvoker.h"
#include "qpid/log/Statement.h"

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

SessionState::SessionState(
    Broker& b, SessionHandler& h, const SessionId& id, const SessionState::Configuration& config) 
    : qpid::SessionState(id, config),
      broker(b), handler(&h),
      ignoring(false),
      semanticState(*this, *this),
      adapter(semanticState),
      msgBuilder(&broker.getStore(), broker.getStagingThreshold()),
      enqueuedOp(boost::bind(&SessionState::enqueued, this, _1)),
      inLastHandler(*this),
      outLastHandler(*this),
      inChain(inLastHandler),
      outChain(outLastHandler)
{
    Manageable* parent = broker.GetVhostObject ();
    if (parent != 0) {
        ManagementAgent::shared_ptr agent = ManagementAgent::getAgent ();
        if (agent.get () != 0) {
            mgmtObject = management::Session::shared_ptr
                (new management::Session (this, parent, getId().getName()));
            mgmtObject->set_attached (0);
            mgmtObject->set_detachedLifespan (0);
            agent->addObject (mgmtObject);
        }
    }
    attach(h);
}

SessionState::~SessionState() {
    // Remove ID from active session list.
    // FIXME aconway 2008-05-12: Need to distinguish outgoing sessions established by bridge,
    // they don't belong in the manager. For now rely on uniqueness of UUIDs.
    // 
    broker.getSessionManager().forget(getId());
    if (mgmtObject.get () != 0)
        mgmtObject->resourceDestroy ();
}

AMQP_ClientProxy& SessionState::getProxy() {
    assert(isAttached());
    return handler->getProxy();
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
    // activateOutput can be called in a different thread, lock to protect attached status
    Mutex::ScopedLock l(lock);
    QPID_LOG(debug, getId() << ": detached on broker.");
    getConnection().outputTasks.removeOutputTask(&semanticState);
    handler = 0;
    if (mgmtObject.get() != 0)
        mgmtObject->set_attached  (0);
}

void SessionState::attach(SessionHandler& h) {
    // activateOutput can be called in a different thread, lock to protect attached status
    Mutex::ScopedLock l(lock);
    QPID_LOG(debug, getId() << ": attached on broker.");
    handler = &h;
    if (mgmtObject.get() != 0)
    {
        mgmtObject->set_attached (1);
        mgmtObject->set_connectionRef (h.getConnection().GetManagementObject()->getObjectId());
        mgmtObject->set_channelId (h.getChannel());
    }
}

void SessionState::activateOutput() {
    // activateOutput can be called in a different thread, lock to protect attached status
    Mutex::ScopedLock l(lock);
    if (isAttached()) 
        getConnection().outputTasks.activateOutput();
    // FIXME aconway 2008-05-22: should we hold the lock over activateOutput??
}

ManagementObject::shared_ptr SessionState::GetManagementObject (void) const
{
    return dynamic_pointer_cast<ManagementObject> (mgmtObject);
}

Manageable::status_t SessionState::ManagementMethod (uint32_t methodId,
                                                     Args&    /*args*/)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;

    switch (methodId)
    {
      case management::Session::METHOD_DETACH :
        if (handler != 0)
        {
            handler->sendDetach();
        }
        status = Manageable::STATUS_OK;
        break;

      case management::Session::METHOD_CLOSE :
        /*
          if (handler != 0)
          {
          handler->getConnection().closeChannel(handler->getChannel());
          }
          status = Manageable::STATUS_OK;
          break;
        */

      case management::Session::METHOD_SOLICITACK :
      case management::Session::METHOD_RESETLIFESPAN :
        status = Manageable::STATUS_NOT_IMPLEMENTED;
        break;
    }

    return status;
}

void SessionState::handleCommand(framing::AMQMethodBody* method, const SequenceNumber& id) {
    Invoker::Result invocation = invoke(adapter, *method);
    receiverCompleted(id);                                    
    if (!invocation.wasHandled()) {
        throw NotImplementedException(QPID_MSG("Not implemented: " << *method));
    } else if (invocation.hasResult()) {
        getProxy().getExecution().result(id, invocation.getResult());
    }
    if (method->isSync()) { 
        incomplete.process(enqueuedOp, true);
        sendCompletion(); 
    }
}

void SessionState::handleContent(AMQFrame& frame, const SequenceNumber& id)
{
    if (frame.getBof() && frame.getBos()) //start of frameset
        msgBuilder.start(id);
    intrusive_ptr<Message> msg(msgBuilder.getMessage());
    msgBuilder.handle(frame);
    if (frame.getEof() && frame.getEos()) {//end of frameset
        if (frame.getBof()) {
            //i.e this is a just a command frame, add a dummy header
            AMQFrame header;
            header.setBody(AMQHeaderBody());
            header.setBof(false);
            header.setEof(false);
            msg->getFrames().append(header);                        
        }
        msg->setPublisher(&getConnection());
        semanticState.handle(msg);        
        msgBuilder.end();

        if (msg->isEnqueueComplete()) {
            enqueued(msg);
        } else {
            incomplete.add(msg);
        }

        //hold up execution until async enqueue is complete        
        if (msg->getFrames().getMethod()->isSync()) { 
            incomplete.process(enqueuedOp, true);
            sendCompletion(); 
        } else {
            incomplete.process(enqueuedOp, false);
        }
    }
}

void SessionState::enqueued(boost::intrusive_ptr<Message> msg)
{
    receiverCompleted(msg->getCommandId());
    if (msg->requiresAccept())         
        getProxy().getMessage().accept(SequenceSet(msg->getCommandId()));        
}

void SessionState::handleIn(AMQFrame& f) { inChain.handle(f); }
void SessionState::handleOut(AMQFrame& f) { outChain.handle(f); }

void SessionState::handleInLast(AMQFrame& frame) {
    SequenceNumber commandId = receiverGetCurrent();
    try {
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
    } catch(const SessionException& e) {
        //TODO: better implementation of new exception handling mechanism

        //0-10 final changes the types of exceptions, 'model layer'
        //exceptions will all be session exceptions regardless of
        //current channel/connection classification

        AMQMethodBody* m = frame.getMethod();
        if (m) {
            getProxy().getExecution().exception(e.code, commandId, m->amqpClassId(), m->amqpMethodId(), 0, e.what(), FieldTable());
        } else {
            getProxy().getExecution().exception(e.code, commandId, 0, 0, 0, e.what(), FieldTable());
        }
        ignoring = true;
        handler->sendDetach();
    }
}

void SessionState::handleOutLast(AMQFrame& frame) {
    assert(handler);
    handler->out(frame);
}

DeliveryId SessionState::deliver(QueuedMessage& msg, DeliveryToken::shared_ptr token)
{
    uint32_t maxFrameSize = getConnection().getFrameMax();
    assert(senderGetCommandPoint().offset == 0);
    SequenceNumber commandId = senderGetCommandPoint().command;
    MessageDelivery::deliver(msg, getProxy().getHandler(), commandId, token, maxFrameSize);
    assert(senderGetCommandPoint() == SessionPoint(commandId+1, 0)); // Delivery has moved sendPoint.
    return commandId;
}

void SessionState::sendCompletion() { handler->sendCompletion(); }

void SessionState::senderCompleted(const SequenceSet& commands) {
    qpid::SessionState::senderCompleted(commands);
    for (SequenceSet::RangeIterator i = commands.rangesBegin(); i != commands.rangesEnd(); i++)
        semanticState.completed(i->first(), i->last());
}

void SessionState::readyToSend() {
    QPID_LOG(debug, getId() << ": ready to send, activating output.");
    assert(handler);
    sys::AggregateOutput& tasks = handler->getConnection().outputTasks;
    tasks.addOutputTask(&semanticState);
    tasks.activateOutput();
}

Broker& SessionState::getBroker() { return broker; }

framing::FrameHandler::Chain& SessionState::getInChain() { return inChain; }

framing::FrameHandler::Chain& SessionState::getOutChain() { return outChain; }

}} // namespace qpid::broker
