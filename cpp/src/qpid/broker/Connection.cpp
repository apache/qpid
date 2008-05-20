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
#include "qpid/management/ManagementAgent.h"

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

class Connection::MgmtClient : public Connection::MgmtWrapper
{
    management::Client::shared_ptr mgmtClient;

public:
    MgmtClient(Connection* conn, Manageable* parent, ManagementAgent::shared_ptr agent,
               const std::string& mgmtId, bool incoming);
    ~MgmtClient();
    void received(framing::AMQFrame& frame);
    management::ManagementObject::shared_ptr getManagementObject() const;
    void closing();
};

Connection::Connection(ConnectionOutputHandler* out_, Broker& broker_, const std::string& mgmtId_, bool isLink) :
    ConnectionState(out_, broker_),
    adapter(*this, isLink),
    mgmtClosing(false),
    mgmtId(mgmtId_)
{
    initMgmt();
}

void Connection::initMgmt(bool asLink)
{
    Manageable* parent = broker.GetVhostObject ();

    if (parent != 0)
    {
        ManagementAgent::shared_ptr agent = ManagementAgent::getAgent ();

        if (agent.get () != 0)
        {
            if (asLink) {
                mgmtWrapper = std::auto_ptr<MgmtWrapper>(new MgmtClient(this, parent, agent, mgmtId, false));
            } else {
                mgmtWrapper = std::auto_ptr<MgmtWrapper>(new MgmtClient(this, parent, agent, mgmtId, true));
            }
        }
    }
}

void Connection::requestIOProcessing (boost::function0<void> callback)
{
    ioCallback = callback;
    out->activateOutput();
}


Connection::~Connection () {}

void Connection::received(framing::AMQFrame& frame){
    if (mgmtClosing)
        close (403, "Closed by Management Request", 0, 0);

    if (frame.getChannel() == 0 && frame.getMethod()) {
        adapter.handle(frame);
    } else {
        getChannel(frame.getChannel()).in(frame);
    }
    
    if (mgmtWrapper.get()) mgmtWrapper->received(frame);
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
        if (mgmtClosing) close (403, "Closed by Management Request", 0, 0);

        //then do other output as needed:
        return outputTasks.doOutput();
    }catch(ConnectionException& e){
        close(e.code, e.what(), 0, 0);
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

ManagementObject::shared_ptr Connection::GetManagementObject (void) const
{
    return mgmtWrapper.get() ? mgmtWrapper->getManagementObject() : ManagementObject::shared_ptr();
}

Manageable::status_t Connection::ManagementMethod (uint32_t methodId, Args&)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;

    QPID_LOG (debug, "Connection::ManagementMethod [id=" << methodId << "]");

    switch (methodId)
    {
    case management::Client::METHOD_CLOSE :
        mgmtClosing = true;
        if (mgmtWrapper.get()) mgmtWrapper->closing();
        out->activateOutput();
        status = Manageable::STATUS_OK;
        break;
    }

    return status;
}

Connection::MgmtClient::MgmtClient(Connection* conn, Manageable* parent,
                                   ManagementAgent::shared_ptr agent,
                                   const std::string& mgmtId, bool incoming)
{
    mgmtClient = management::Client::shared_ptr
        (new management::Client (conn, parent, mgmtId, incoming));
    agent->addObject (mgmtClient);
}

Connection::MgmtClient::~MgmtClient()
{
    if (mgmtClient.get () != 0)
        mgmtClient->resourceDestroy ();
}

void Connection::MgmtClient::received(framing::AMQFrame& frame)
{
    if (mgmtClient.get () != 0)
    {
        mgmtClient->inc_framesFromClient ();
        mgmtClient->inc_bytesFromClient (frame.size ());
    }
}

management::ManagementObject::shared_ptr Connection::MgmtClient::getManagementObject() const
{
    return dynamic_pointer_cast<ManagementObject>(mgmtClient);
}

void Connection::MgmtClient::closing()
{
    if (mgmtClient) mgmtClient->set_closing (1);
}

}}

