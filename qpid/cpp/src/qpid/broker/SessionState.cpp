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
#include "SemanticHandler.h"
#include "SessionManager.h"
#include "SessionHandler.h"
#include "qpid/framing/AMQContentBody.h"
#include "qpid/framing/AMQHeaderBody.h"
#include "qpid/framing/AMQMethodBody.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/ServerInvoker.h"

#include <boost/bind.hpp>

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
    SessionManager* f, SessionHandler* h, uint32_t timeout_, uint32_t ack) 
    : framing::SessionState(ack, timeout_ > 0), nextOut(0),
      factory(f), handler(h), id(true), timeout(timeout_),
      broker(h->getConnection().broker),
      version(h->getConnection().getVersion()),
      semanticState(*this, *this),
      adapter(semanticState),
      msgBuilder(&broker.getStore(), broker.getStagingThreshold()),
      ackOp(boost::bind(&SemanticState::completed, &semanticState, _1, _2)),
      enqueuedOp(boost::bind(&SessionState::enqueued, this, _1))
{
    getConnection().outputTasks.addOutputTask(&semanticState);

    Manageable* parent = broker.GetVhostObject ();

    if (parent != 0)
    {
        ManagementAgent::shared_ptr agent = ManagementAgent::getAgent ();

        if (agent.get () != 0)
        {
            mgmtObject = management::Session::shared_ptr
                (new management::Session (this, parent, id.str ()));
            mgmtObject->set_attached (1);
            mgmtObject->set_clientRef (h->getConnection().GetManagementObject()->getObjectId());
            mgmtObject->set_channelId (h->getChannel());
            mgmtObject->set_detachedLifespan (getTimeout());
            agent->addObject (mgmtObject);
        }
    }
}

SessionState::~SessionState() {
    // Remove ID from active session list.
    if (factory)
        factory->erase(getId());
    if (mgmtObject.get () != 0)
        mgmtObject->resourceDestroy ();
}

SessionHandler* SessionState::getHandler() {
    return handler;
}

AMQP_ClientProxy& SessionState::getProxy() {
    assert(isAttached());
    return getHandler()->getProxy();
}

ConnectionState& SessionState::getConnection() {
    assert(isAttached());
    return getHandler()->getConnection();
}

bool SessionState::isLocal(const ConnectionToken* t) const
{
    return isAttached() && &(handler->getConnection()) == t;
}

void SessionState::detach() {
    getConnection().outputTasks.removeOutputTask(&semanticState);
    Mutex::ScopedLock l(lock);
    handler = 0;
    if (mgmtObject.get() != 0)
    {
        mgmtObject->set_attached  (0);
    }
}

void SessionState::attach(SessionHandler& h) {
    {
        Mutex::ScopedLock l(lock);
        handler = &h;
        if (mgmtObject.get() != 0)
        {
            mgmtObject->set_attached (1);
            mgmtObject->set_clientRef (h.getConnection().GetManagementObject()->getObjectId());
            mgmtObject->set_channelId (h.getChannel());
        }
    }
    h.getConnection().outputTasks.addOutputTask(&semanticState);
}

void SessionState::activateOutput()
{
    Mutex::ScopedLock l(lock);
    if (isAttached()) {
        getConnection().outputTasks.activateOutput();
    }
}
    //This class could be used as the callback for queue notifications
    //if not attached, it can simply ignore the callback, else pass it
    //on to the connection

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
            handler->detach();
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

void SessionState::handleCommand(framing::AMQMethodBody* method, SequenceNumber& id)
{
    id = nextIn++;
    Invoker::Result invocation = invoke(adapter, *method);
    completed.add(id);                                    
    
    if (!invocation.wasHandled()) {
        throw NotImplementedException(QPID_MSG("Not implemented: " << *method));
    } else if (invocation.hasResult()) {
        nextOut++;//execution result is now a command, so the counter must be incremented
        getProxy().getExecution010().result(id, invocation.getResult());
    }
    if (method->isSync()) { 
        incomplete.process(enqueuedOp, true);
        sendCompletion(); 
    }
    //TODO: if window gets too large send unsolicited completion
}

void SessionState::handleContent(AMQFrame& frame, SequenceNumber& id)
{
    intrusive_ptr<Message> msg(msgBuilder.getMessage());
    if (frame.getBof() && frame.getBos()) {//start of frameset
        id = nextIn++;
        msgBuilder.start(id);
        msg = msgBuilder.getMessage();
    } else {        
        id = msg->getCommandId();
    }
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
    completed.add(msg->getCommandId());
    if (msg->requiresAccept()) {        
        nextOut++;//accept is a command, so the counter must be incremented
        getProxy().getMessage010().accept(SequenceSet(msg->getCommandId()));        
    }
}

void SessionState::handle(AMQFrame& frame)
{
    received(frame);

    SequenceNumber commandId;
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
            getProxy().getExecution010().exception(e.code, commandId, m->amqpClassId(), m->amqpMethodId(), 0, e.what(), FieldTable());
        } else {
            getProxy().getExecution010().exception(e.code, commandId, 0, 0, 0, e.what(), FieldTable());
        }
        throw e;
    }
}

DeliveryId SessionState::deliver(QueuedMessage& msg, DeliveryToken::shared_ptr token)
{
    uint32_t maxFrameSize = getConnection().getFrameMax();
    MessageDelivery::deliver(msg, getProxy().getHandler(), nextOut, token, maxFrameSize);
    return nextOut++;
}

void SessionState::sendCompletion()
{
    handler->sendCompletion();
}

void SessionState::complete(const SequenceSet& commands) 
{
    knownCompleted.add(commands);
    commands.for_each(ackOp);
}


}} // namespace qpid::broker
