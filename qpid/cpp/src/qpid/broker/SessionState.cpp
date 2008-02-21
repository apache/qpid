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
#include "SessionManager.h"
#include "SessionContext.h"
#include "ConnectionState.h"
#include "Broker.h"
#include "SemanticHandler.h"
#include "qpid/framing/reply_exceptions.h"

namespace qpid {
namespace broker {

using namespace framing;
using sys::Mutex;
using qpid::management::ManagementAgent;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;

SessionState::SessionState(
    SessionManager* f, SessionContext* h, uint32_t timeout_, uint32_t ack) 
    : framing::SessionState(ack, timeout_ > 0),
      factory(f), handler(h), id(true), timeout(timeout_),
      broker(h->getConnection().broker),
      version(h->getConnection().getVersion()),
      semanticHandler(new SemanticHandler(*this))
{
    in.next = semanticHandler.get();
    out.next = &handler->out;

    getConnection().outputTasks.addOutputTask(&semanticHandler->getSemanticState());

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

SessionContext* SessionState::getHandler() {
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
    getConnection().outputTasks.removeOutputTask(&semanticHandler->getSemanticState());
    Mutex::ScopedLock l(lock);
    handler = 0; out.next = 0; 
    if (mgmtObject.get() != 0)
    {
        mgmtObject->set_attached  (0);
    }
}

void SessionState::attach(SessionContext& h) {
    {
        Mutex::ScopedLock l(lock);
        handler = &h;
        out.next = &handler->out;
        if (mgmtObject.get() != 0)
        {
            mgmtObject->set_attached (1);
            mgmtObject->set_clientRef (h.getConnection().GetManagementObject()->getObjectId());
            mgmtObject->set_channelId (h.getChannel());
        }
    }
    h.getConnection().outputTasks.addOutputTask(&semanticHandler->getSemanticState());
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


}} // namespace qpid::broker
