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
#include "SemanticHandler.h"

#include "qpid/log/Statement.h"
#include "qpid/ptr_map.h"
#include "qpid/framing/AMQP_ClientProxy.h"
#include "qpid/management/ManagementAgent.h"

#include <boost/bind.hpp>

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

Connection::Connection(ConnectionOutputHandler* out_, Broker& broker_, const std::string& mgmtId) :
    broker(broker_),
    outputTasks(*out_),
    out(out_),
    framemax(65535), 
    heartbeat(0),
    client(0),
    stagingThreshold(broker.getStagingThreshold()),
    adapter(*this),
    mgmtClosing(0)
{
    Manageable* parent = broker.GetVhostObject ();

    if (parent != 0)
    {
        ManagementAgent::shared_ptr agent = ManagementAgent::getAgent ();

        if (agent.get () != 0)
        {
            mgmtObject = management::Client::shared_ptr
                (new management::Client (this, parent, mgmtId));
            agent->addObject (mgmtObject);
        }
    }
}

Connection::~Connection ()
{
    if (mgmtObject.get () != 0)
        mgmtObject->resourceDestroy ();
}

void Connection::received(framing::AMQFrame& frame){
    if (mgmtClosing)
        close (403, "Closed by Management Request", 0, 0);

    if (frame.getChannel() == 0) {
        adapter.handle(frame);
    } else {
        getChannel(frame.getChannel()).in(frame);
    }

    if (mgmtObject.get () != 0)
    {
        mgmtObject->inc_framesFromClient ();
        mgmtObject->inc_bytesFromClient (frame.size ());
    }
}

void Connection::close(
    ReplyCode code, const string& text, ClassId classId, MethodId methodId)
{
    adapter.close(code, text, classId, methodId);
    channels.clear();
    getOutput().close();
}

void Connection::initiated(const framing::ProtocolInitiation& header) {
    version = ProtocolVersion(header.getMajor(), header.getMinor());
    adapter.init(header);
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
    return dynamic_pointer_cast<ManagementObject> (mgmtObject);
}

Manageable::status_t Connection::ManagementMethod (uint32_t methodId,
                                                   Args&    /*args*/)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;

    QPID_LOG (debug, "Connection::ManagementMethod [id=" << methodId << "]");

    switch (methodId)
    {
    case management::Client::METHOD_CLOSE :
        mgmtClosing = 1;
        mgmtObject->set_closing (1);
        status = Manageable::STATUS_OK;
        break;
    }

    return status;
}

void Connection::setUserId(const string& uid)
{
    userId = uid;
    QPID_LOG (debug, "UserId is " << userId);
}

const string& Connection::getUserId() const
{
    return userId;
}

}}

