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

using namespace boost;
using namespace qpid::sys;
using namespace qpid::framing;
using namespace qpid::sys;

namespace qpid {
namespace broker {

Connection::Connection(ConnectionOutputHandler* out_, Broker& broker_) :
    framemax(65536), 
    heartbeat(0),
    broker(broker_),
    settings(broker.getTimeout(), broker.getStagingThreshold()),
    out(out_)
{}

Queue::shared_ptr Connection::getQueue(const string& name, u_int16_t channel){
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


void Connection::received(qpid::framing::AMQFrame* frame){
    getAdapter(frame->getChannel()).handleBody(frame->getBody());
}

void Connection::initiated(qpid::framing::ProtocolInitiation* header) {
    if (client.get())
        // TODO aconway 2007-01-16: correct error code.
        throw ConnectionException(0, "Connection initiated twice");
    client.reset(new qpid::framing::AMQP_ClientProxy(
                     out, header->getMajor(), header->getMinor()));
    FieldTable properties;
    string mechanisms("PLAIN");
    string locales("en_US");
    // TODO aconway 2007-01-16: Client call, move to adapter.
    client->getConnection().start(
        MethodContext(0, &getAdapter(0)),
        header->getMajor(), header->getMinor(),
        properties, mechanisms, locales);
    getAdapter(0).init(0, *out, client->getProtocolVersion());
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

void Connection::closeChannel(u_int16_t channel) {
    getChannel(channel).close(); 
    adapters.erase(adapters.find(channel));
}


BrokerAdapter& Connection::getAdapter(u_int16_t id) { 
    AdapterMap::iterator i = adapters.find(id);
    if (i == adapters.end()) {
        Channel* ch=new Channel(
            client->getProtocolVersion(), out, id,
            framemax, broker.getQueues().getStore(),
            settings.stagingThreshold);
        BrokerAdapter* adapter =  new BrokerAdapter(ch, *this, broker);
        adapters.insert(id, adapter);
        return *adapter;
    }
    else
        return *i;
}

Channel& Connection::getChannel(u_int16_t id) {
    return getAdapter(id).getChannel();
}


}}

