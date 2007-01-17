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

// TODO aconway 2007-01-16: move to channel.
#include "Requester.h"
#include "Responder.h"

using namespace boost;
using namespace qpid::sys;
using namespace qpid::framing;
using namespace qpid::sys;

namespace qpid {
namespace broker {

Connection::Connection(SessionContext* context_, Broker& broker_) :
    adapter(*this),
    requester(broker.getRequester()),
    responder(broker.getResponder()),
    context(context_), 
    framemax(65536), 
    heartbeat(0),
    broker(broker_),
    settings(broker.getTimeout(), broker.getStagingThreshold())
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

void Connection::handleMethod(
    u_int16_t channel, qpid::framing::AMQBody::shared_ptr body)
{
    AMQMethodBody::shared_ptr method =
        shared_polymorphic_cast<AMQMethodBody, AMQBody>(body);
    try{
        method->invoke(adapter, channel);
    }catch(ChannelException& e){
        closeChannel(channel);
        client->getChannel().close(
            channel, e.code, e.toString(),
            method->amqpClassId(), method->amqpMethodId());
    }catch(ConnectionException& e){
        client->getConnection().close(
            0, e.code, e.toString(),
            method->amqpClassId(), method->amqpMethodId());
    }catch(std::exception& e){
        client->getConnection().close(
            0, 541/*internal error*/, e.what(),
            method->amqpClassId(), method->amqpMethodId());
    }
}

void Connection::received(qpid::framing::AMQFrame* frame){
    u_int16_t channel = frame->getChannel();
    AMQBody::shared_ptr body = frame->getBody();
    switch(body->type())
    {
      case REQUEST_BODY:
        responder.received(AMQRequestBody::getData(body));
        handleMethod(channel, body);
        break;
      case RESPONSE_BODY:
        // Must process responses before marking them received.
        handleMethod(channel, body);     
        requester.processed(AMQResponseBody::getData(body));
        break;
        // TODO aconway 2007-01-15: Leftover from 0-8 support, remove.
      case METHOD_BODY:    
        handleMethod(channel, body);
        break;
      case HEADER_BODY:
	handleHeader(
            channel, shared_polymorphic_cast<AMQHeaderBody>(body));
	break;

      case CONTENT_BODY:
	handleContent(
            channel, shared_polymorphic_cast<AMQContentBody>(body));
	break;

      case HEARTBEAT_BODY:
        assert(channel == 0);
	handleHeartbeat(
            shared_polymorphic_cast<AMQHeartbeatBody>(body));
	break;
    }
}

/**
 * An OutputHandler that does request/response procssing before
 * delgating to another OutputHandler.
 */
Connection::Sender::Sender(
    OutputHandler& oh, Requester& req, Responder& resp)
    : out(oh), requester(req), responder(resp)
{}

void Connection::Sender::send(AMQFrame* frame) {
    AMQBody::shared_ptr body =  frame->getBody();
    u_int16_t type = body->type();
    if (type == REQUEST_BODY)
        requester.sending(AMQRequestBody::getData(body));
    else if (type == RESPONSE_BODY)
        responder.sending(AMQResponseBody::getData(body));
    out.send(frame);
}

void Connection::initiated(qpid::framing::ProtocolInitiation* header) {
    if (client.get())
        // TODO aconway 2007-01-16: correct code.
        throw ConnectionException(0, "Connection initiated twice");

    client.reset(new qpid::framing::AMQP_ClientProxy(
                     context, header->getMajor(), header->getMinor()));
    FieldTable properties;
    string mechanisms("PLAIN");
    string locales("en_US");
    // TODO aconway 2007-01-16: Move to adapter.
    client->getConnection().start(
        0, header->getMajor(), header->getMinor(), properties,
        mechanisms, locales);
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

// TODO aconway 2007-01-16: colapse these. 
void Connection::handleHeader(u_int16_t channel, AMQHeaderBody::shared_ptr body){
    getChannel(channel).handleHeader(body);
}

void Connection::handleContent(u_int16_t channel, AMQContentBody::shared_ptr body){
    getChannel(channel).handleContent(body);
}

void Connection::handleHeartbeat(AMQHeartbeatBody::shared_ptr /*body*/){
    std::cout << "Connection::handleHeartbeat()" << std::endl;
}

void Connection::openChannel(u_int16_t channel) {
    if (channel == 0)
        throw ConnectionException(504, "Illegal channel 0");
    if (channels.find(channel) != channels.end())
        throw ConnectionException(504, "Channel already open: " + channel);
    channels.insert(
        channel,
        new Channel(
            client->getProtocolVersion(), context, channel, framemax,
            broker.getQueues().getStore(), settings.stagingThreshold));
}

void Connection::closeChannel(u_int16_t channel) {
    getChannel(channel).close(); // throws if channel does not exist.
    channels.erase(channels.find(channel));
}


Channel& Connection::getChannel(u_int16_t channel){
    ChannelMap::iterator i = channels.find(channel);
    if(i == channels.end())
        throw ConnectionException(504, "Unknown channel: " + channel);
    return *i;
}


}}

