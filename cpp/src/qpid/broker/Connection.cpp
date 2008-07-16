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
#include "Connection.h"
#include "SessionState.h"
#include "Bridge.h"

#include "qpid/log/Statement.h"
#include "qpid/ptr_map.h"
#include "qpid/framing/AMQP_ClientProxy.h"
#include "qpid/agent/ManagementAgent.h"

#include <boost/bind.hpp>
#include <boost/ptr_container/ptr_vector.hpp>

#include <algorithm>
#include <iostream>
#include <assert.h>

using namespace boost;
using namespace qpid::sys;
using namespace qpid::framing;
using namespace qpid::sys;
using qpid::ptr_map_ptr;
using qpid::management::ManagementAgent;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;

namespace qpid {
namespace broker {

Connection::Connection(ConnectionOutputHandler* out_, Broker& broker_, const std::string& mgmtId_, bool isLink_) :
    ConnectionState(out_, broker_),
    adapter(*this, isLink_),
    isLink(isLink_),
    mgmtClosing(false),
    mgmtId(mgmtId_),
    mgmtObject(0),
    links(broker_.getLinks()),
    lastInHandler(*this),
    inChain(lastInHandler)
{
    Manageable* parent = broker.GetVhostObject();

    if (isLink)
        links.notifyConnection(mgmtId, this);

    if (parent != 0)
    {
        ManagementAgent* agent = ManagementAgent::Singleton::getInstance();

        if (agent != 0)
            mgmtObject = new management::Connection(agent, this, parent, mgmtId, !isLink);
        agent->addObject(mgmtObject);
    }
}

void Connection::requestIOProcessing(boost::function0<void> callback)
{
    ioCallback = callback;
    out->activateOutput();
}


Connection::~Connection()
{
    if (mgmtObject != 0)
        mgmtObject->resourceDestroy();
    if (isLink)
        links.notifyClosed(mgmtId);
}

void Connection::received(framing::AMQFrame& frame){ inChain->handle(frame); }
    
void Connection::receivedLast(framing::AMQFrame& frame){
    if (frame.getChannel() == 0 && frame.getMethod()) {
        adapter.handle(frame);
    } else {
        getChannel(frame.getChannel()).in(frame);
    }

    if (isLink)
        recordFromServer(frame);
    else
        recordFromClient(frame);
}

void Connection::recordFromServer(framing::AMQFrame& frame)
{
    if (mgmtObject != 0)
    {
        mgmtObject->inc_framesToClient();
        mgmtObject->inc_bytesToClient(frame.size());
    }
}

void Connection::recordFromClient(framing::AMQFrame& frame)
{
    if (mgmtObject != 0)
    {
        mgmtObject->inc_framesFromClient();
        mgmtObject->inc_bytesFromClient(frame.size());
    }
}

string Connection::getAuthMechanism()
{
    if (!isLink)
        return string("ANONYMOUS");

    return links.getAuthMechanism(mgmtId);
}

string Connection::getAuthCredentials()
{
    if (!isLink)
        return string();

    if (mgmtObject != 0)
    {
        if (links.getAuthMechanism(mgmtId) == "ANONYMOUS")
            mgmtObject->set_authIdentity("anonymous");
        else
            mgmtObject->set_authIdentity(links.getAuthIdentity(mgmtId));
    }

    return links.getAuthCredentials(mgmtId);
}

void Connection::notifyConnectionForced(const string& text)
{
    if (isLink)
        links.notifyConnectionForced(mgmtId, text);
}

void Connection::setUserId(const string& userId)
{
    ConnectionState::setUserId(userId);
    if (mgmtObject != 0)
        mgmtObject->set_authIdentity(userId);
}

void Connection::close(
    ReplyCode code, const string& text, ClassId classId, MethodId methodId)
{
    adapter.close(code, text, classId, methodId);
    channels.clear();
    getOutput().close();
}

void Connection::idleOut(){}

void Connection::idleIn(){}

void Connection::closed(){ // Physically closed, suspend open sessions.
    try {
        while (!channels.empty()) 
            ptr_map_ptr(channels.begin())->handleDetach();
        while (!exclusiveQueues.empty()) {
            Queue::shared_ptr q(exclusiveQueues.front());
            q->releaseExclusiveOwnership();
            if (q->canAutoDelete()) {
                Queue::tryAutoDelete(broker, q);
            }
            exclusiveQueues.erase(exclusiveQueues.begin());
        }
    } catch(std::exception& e) {
        QPID_LOG(error, " Unhandled exception while closing session: " <<
                 e.what());
        assert(0);
    }
}

bool Connection::doOutput()
{    
    try{
        if (ioCallback)
            ioCallback(); // Lend the IO thread for management processing
        ioCallback = 0;

        if (mgmtClosing)
            close(403, "Closed by Management Request", 0, 0);
        else
            //then do other output as needed:
            return outputTasks.doOutput();
    }catch(ConnectionException& e){
        close(e.code, e.getMessage(), 0, 0);
    }catch(std::exception& e){
        close(541/*internal error*/, e.what(), 0, 0);
    }
    return false;
}

void Connection::closeChannel(uint16_t id) {
    ChannelMap::iterator i = channels.find(id);
    if (i != channels.end()) channels.erase(i);
}

SessionHandler& Connection::getChannel(ChannelId id) {
    ChannelMap::iterator i=channels.find(id);
    if (i == channels.end()) {
        i = channels.insert(id, new SessionHandler(*this, id)).first;
    }
    return *ptr_map_ptr(i);
}

ManagementObject* Connection::GetManagementObject(void) const
{
    return (ManagementObject*) mgmtObject;
}

Manageable::status_t Connection::ManagementMethod(uint32_t methodId, Args&)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;

    QPID_LOG(debug, "Connection::ManagementMethod [id=" << methodId << "]");

    switch (methodId)
    {
    case management::Connection::METHOD_CLOSE :
        mgmtClosing = true;
        if (mgmtObject != 0) mgmtObject->set_closing(1);
        out->activateOutput();
        status = Manageable::STATUS_OK;
        break;
    }

    return status;
}

}}

