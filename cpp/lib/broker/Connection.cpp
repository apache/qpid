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
#include <iostream>
#include <assert.h>

#include "Connection.h"
#include "BrokerChannel.h"
#include "AMQP_ClientProxy.h"
#include "BrokerAdapter.h"

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
    timeout(broker.getTimeout()),
    stagingThreshold(broker.getStagingThreshold())
{}


Queue::shared_ptr Connection::getQueue(const string& name, uint16_t channel){
    Queue::shared_ptr queue;
    if (name.empty()) {
        queue = getChannel(channel).getDefaultQueue();
        if (!queue) throw ConnectionException( 530, "Queue must be specified or previously declared" );
    } else {
        queue = broker.getQueues().find(name);
        if (queue == 0) {
            throw ChannelException( 404, "Queue not found: " + name);
        }
    }
    return queue;
}


Exchange::shared_ptr Connection::findExchange(const string& name){
    return broker.getExchanges().get(name);
}


void Connection::received(framing::AMQFrame* frame){
    getChannel(frame->getChannel()).handleBody(frame->getBody());
}

void Connection::close(
    ReplyCode code, const string& text, ClassId classId, MethodId methodId)
{
    client->close(code, text, classId, methodId);
    getOutput().close();
}

void Connection::initiated(const framing::ProtocolInitiation& header) {
    version = ProtocolVersion(header.getMajor(), header.getMinor());
    FieldTable properties;
    string mechanisms("PLAIN");
    string locales("en_US");
    getChannel(0).init(0, *out, getVersion());
    client = &getChannel(0).getAdatper().getProxy().getConnection();
    client->start(
        header.getMajor(), header.getMinor(),
        properties, mechanisms, locales);
}

void Connection::idleOut(){}

void Connection::idleIn(){}

void Connection::closed(){
    try {
        while (!exclusiveQueues.empty()) {
            broker.getQueues().destroy(exclusiveQueues.front()->getName());
            exclusiveQueues.erase(exclusiveQueues.begin());
        }
    } catch(std::exception& e) {
        std::cout << "Caught unhandled exception while closing session: " <<
            e.what() << std::endl;
        assert(0);
    }
}

void Connection::closeChannel(uint16_t id) {
    ChannelMap::iterator i = channels.find(id);
    if (i != channels.end())
        i->close();
}


Channel& Connection::getChannel(ChannelId id) {
    ChannelMap::iterator i = channels.find(id);
    if (i == channels.end()) {
        i = channels.insert(
            id, new Channel(
                *this, id, framemax, broker.getQueues().getStore(),
                broker.getStagingThreshold())).first;
    }        
    return *i;
}


}}

