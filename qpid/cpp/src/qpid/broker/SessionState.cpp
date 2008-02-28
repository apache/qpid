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
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/ServerInvoker.h"

#include <boost/bind.hpp>

namespace qpid {
namespace broker {

using namespace framing;
using sys::Mutex;
using qpid::management::ManagementAgent;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;

SessionState::SessionState(
    SessionManager* f, SessionHandler* h, uint32_t timeout_, uint32_t ack) 
    : framing::SessionState(ack, timeout_ > 0),
      factory(f), handler(h), id(true), timeout(timeout_),
      broker(h->getConnection().broker),
      version(h->getConnection().getVersion()),
      semanticState(*this, *this),
      adapter(semanticState),
      msgBuilder(&broker.getStore(), broker.getStagingThreshold()),
      ackOp(boost::bind(&SemanticState::ackRange, &semanticState, _1, _2))
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

void SessionState::handleCommand(framing::AMQMethodBody* method)
{
    SequenceNumber id = incoming.next();
    Invoker::Result invocation = invoke(adapter, *method);
    incoming.complete(id);                                    
    
    if (!invocation.wasHandled()) {
        throw NotImplementedException("Not implemented");
    } else if (invocation.hasResult()) {
        getProxy().getExecution().result(id.getValue(), invocation.getResult());
    }
    if (method->isSync()) { 
        incoming.sync(id); 
        sendCompletion(); 
    }
    //TODO: if window gets too large send unsolicited completion
}

void SessionState::handleContent(AMQFrame& frame)
{
    intrusive_ptr<Message> msg(msgBuilder.getMessage());
    if (!msg) {//start of frameset will be indicated by frame flags
        msgBuilder.start(incoming.next());
        msg = msgBuilder.getMessage();
    }
    msgBuilder.handle(frame);
    if (frame.getEof() && frame.getEos()) {//end of frameset will be indicated by frame flags
        msg->setPublisher(&getConnection());
        semanticState.handle(msg);        
        msgBuilder.end();
        incoming.track(msg);
        if (msg->getFrames().getMethod()->isSync()) { 
            incoming.sync(msg->getCommandId()); 
            sendCompletion(); 
        }
    }
}

void SessionState::handle(AMQFrame& frame)
{
    //TODO: make command handling more uniform, regardless of whether
    //commands carry content. (For now, assume all single frame
    //assmblies are non-content bearing and all content-bearing
    //assmeblies will have more than one frame):
    if (frame.getBof() && frame.getEof()) {
        handleCommand(frame.getMethod());
    } else {
        handleContent(frame);
    }

}

DeliveryId SessionState::deliver(QueuedMessage& msg, DeliveryToken::shared_ptr token)
{
    uint32_t maxFrameSize = getConnection().getFrameMax();
    MessageDelivery::deliver(msg, getProxy().getHandler(), ++outgoing.hwm, token, maxFrameSize);
    return outgoing.hwm;
}

void SessionState::sendCompletion()
{
    SequenceNumber mark = incoming.getMark();
    SequenceNumberSet range = incoming.getRange();
    getProxy().getExecution().complete(mark.getValue(), range);
}

void SessionState::complete(uint32_t cumulative, const SequenceNumberSet& range) 
{
    //record: 
    SequenceNumber mark(cumulative);
    if (outgoing.lwm < mark) {
        outgoing.lwm = mark;
        //ack messages:
        semanticState.ackCumulative(mark.getValue());
    }
    range.processRanges(ackOp);
}

void SessionState::flush()
{
    incoming.flush();
    sendCompletion();
}

void SessionState::sync()
{
    incoming.sync();
    sendCompletion();
}

void SessionState::noop()
{
    incoming.noop();
}


}} // namespace qpid::broker
