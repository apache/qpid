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
#include "qpid/log/Statement.h"
#include <iostream>
#include <assert.h>

#include "Connection.h"
#include "SessionState.h"
#include "qpid/framing/AMQP_ClientProxy.h"
#include "BrokerAdapter.h"
#include "SemanticHandler.h"

#include <boost/utility/in_place_factory.hpp>

using namespace boost;
using namespace qpid::sys;
using namespace qpid::framing;
using namespace qpid::sys;

namespace qpid {
namespace broker {

Connection::Connection(ConnectionOutputHandler* out_, Broker& broker_) :
    broker(broker_),
    out(out_),
    framemax(65536), 
    heartbeat(0),
    client(0),
    stagingThreshold(broker.getStagingThreshold()),
    adapter(*this)
{}

void Connection::received(framing::AMQFrame& frame){
    if (frame.getChannel() == 0) {
        adapter.handle(frame);
    } else {
        getChannel(frame.getChannel()).in(frame);
    }
}

void Connection::close(
    ReplyCode code, const string& text, ClassId classId, MethodId methodId)
{
    adapter.close(code, text, classId, methodId);
    getOutput().close();
}

void Connection::initiated(const framing::ProtocolInitiation& header) {
    version = ProtocolVersion(header.getMajor(), header.getMinor());
    adapter.init(header);
}

void Connection::idleOut(){}

void Connection::idleIn(){}

void Connection::closed(){
    try {
        while (!exclusiveQueues.empty()) {
            Queue::shared_ptr q(exclusiveQueues.front());
            broker.getQueues().destroy(q->getName());
            exclusiveQueues.erase(exclusiveQueues.begin());
            q->unbind(broker.getExchanges(), q);
            q->destroy();
        }
    } catch(std::exception& e) {
        QPID_LOG(error, " Unhandled exception while closing session: " <<
                 e.what());
        assert(0);
    }
}

void Connection::closeChannel(uint16_t id) {
    ChannelMap::iterator i = channels.find(id);
    if (i != channels.end()) channels.erase(i);
}

SessionHandler& Connection::getChannel(ChannelId id) {
    boost::optional<SessionHandler>& ch = channels[id];
    if (!ch) {
        ch = boost::in_place(boost::ref(*this), id); 
    }
    return *ch;
}


}}

