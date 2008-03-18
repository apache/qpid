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
#include "BrokerAdapter.h"
#include "Bridge.h"
#include "SemanticHandler.h"

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
using namespace qpid::ptr_map;
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
    MgmtClient(Connection* conn, Manageable* parent, ManagementAgent::shared_ptr agent, const std::string& mgmtId);
    ~MgmtClient();
    void received(framing::AMQFrame& frame);
    management::ManagementObject::shared_ptr getManagementObject() const;
    void closing();
};

class Connection::MgmtLink : public Connection::MgmtWrapper
{
    typedef boost::ptr_vector<Bridge> Bridges;

    management::Link::shared_ptr mgmtLink;
    Bridges created;//holds list of bridges pending creation
    Bridges cancelled;//holds list of bridges pending cancellation
    Bridges active;//holds active bridges
    uint channelCounter;
    sys::Mutex lock;

    void cancel(Bridge*);

public:
    MgmtLink(Connection* conn, Manageable* parent, ManagementAgent::shared_ptr agent, const std::string& mgmtId);
    ~MgmtLink();
    void received(framing::AMQFrame& frame);
    management::ManagementObject::shared_ptr getManagementObject() const;
    void closing();
    void processPending();
    void process(Connection& connection, const management::Args& args);
};


Connection::Connection(ConnectionOutputHandler* out_, Broker& broker_, const std::string& mgmtId_) :
    ConnectionState(out_, broker_),
    adapter(*this),
    mgmtClosing(0),
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
                mgmtWrapper = std::auto_ptr<MgmtWrapper>(new MgmtLink(this, parent, agent, mgmtId));
            } else {
                mgmtWrapper = std::auto_ptr<MgmtWrapper>(new MgmtClient(this, parent, agent, mgmtId));
            }
        }
    }
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
	for (ChannelMap::iterator i = channels.begin(); i != channels.end(); ++i)
	    get_pointer(i)->localSuspend();
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
        //process any pending mgmt commands:
        if (mgmtWrapper.get()) mgmtWrapper->processPending();

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
    return *get_pointer(i);
}

ManagementObject::shared_ptr Connection::GetManagementObject (void) const
{
    return mgmtWrapper.get() ? mgmtWrapper->getManagementObject() : ManagementObject::shared_ptr();
}

Manageable::status_t Connection::ManagementMethod (uint32_t methodId,
                                                   Args&    args)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;

    QPID_LOG (debug, "Connection::ManagementMethod [id=" << methodId << "]");

    switch (methodId)
    {
    case management::Client::METHOD_CLOSE :
        mgmtClosing = 1;
        if (mgmtWrapper.get()) mgmtWrapper->closing();
        status = Manageable::STATUS_OK;
        break;
    case management::Link::METHOD_BRIDGE :
        //queue this up and request chance to do output (i.e. get connections thread of control):
        mgmtWrapper->process(*this, args);
        out->activateOutput();
        status = Manageable::STATUS_OK;
        break;
    }

    return status;
}

Connection::MgmtLink::MgmtLink(Connection* conn, Manageable* parent, ManagementAgent::shared_ptr agent, const std::string& mgmtId) 
    : channelCounter(1)
{
    mgmtLink = management::Link::shared_ptr
        (new management::Link(conn, parent, mgmtId));
    agent->addObject (mgmtLink);
}

Connection::MgmtLink::~MgmtLink()
{
    if (mgmtLink.get () != 0)
        mgmtLink->resourceDestroy ();
}

void Connection::MgmtLink::received(framing::AMQFrame& frame)
{
    if (mgmtLink.get () != 0)
    {
        mgmtLink->inc_framesFromPeer ();
        mgmtLink->inc_bytesFromPeer (frame.size ());
    }
}

management::ManagementObject::shared_ptr Connection::MgmtLink::getManagementObject() const
{
    return dynamic_pointer_cast<ManagementObject>(mgmtLink);
}

void Connection::MgmtLink::closing()
{
    if (mgmtLink) mgmtLink->set_closing (1);
}

void Connection::MgmtLink::processPending()
{
    //process any pending creates
    if (!created.empty()) {
        for (Bridges::iterator i = created.begin(); i != created.end(); ++i) {
            i->create();
        }
        active.transfer(active.end(), created.begin(), created.end(), created);
    }
    if (!cancelled.empty()) {
        //process any pending cancellations
        for (Bridges::iterator i = cancelled.begin(); i != cancelled.end(); ++i) {
            i->cancel();
        }
        cancelled.clear();
    }
}

void Connection::MgmtLink::process(Connection& connection, const management::Args& args)
{   
    created.push_back(new Bridge(channelCounter++, connection, 
                                 boost::bind(&MgmtLink::cancel, this, _1),
                                 dynamic_cast<const management::ArgsLinkBridge&>(args)));
}

void Connection::MgmtLink::cancel(Bridge* b)
{   
    //need to take this out the active map and add it to the cancelled map
    for (Bridges::iterator i = active.begin(); i != active.end(); i++) {
        if (&(*i) == b) {
            cancelled.transfer(cancelled.end(), i, active);
            break;
        }
    }
}

Connection::MgmtClient::MgmtClient(Connection* conn, Manageable* parent, ManagementAgent::shared_ptr agent, const std::string& mgmtId)
{
    mgmtClient = management::Client::shared_ptr
        (new management::Client (conn, parent, mgmtId));
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

