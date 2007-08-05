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
#include "ClientChannel.h"
#include "qpid/sys/Monitor.h"
#include "ClientMessage.h"
#include "qpid/QpidError.h"
#include "Connection.h"
#include "ConnectionHandler.h"
#include "FutureResponse.h"
#include "MessageListener.h"
#include <boost/format.hpp>
#include <boost/bind.hpp>

// FIXME aconway 2007-01-26: Evaluate all throws, ensure consistent
// handling of errors that should close the connection or the channel.
// Make sure the user thread receives a connection in each case.
//
using namespace std;
using namespace boost;
using namespace qpid::client;
using namespace qpid::framing;
using namespace qpid::sys;

namespace qpid{
namespace client{

const std::string empty;

}}

Channel::Channel(bool _transactional, u_int16_t _prefetch) :
    prefetch(_prefetch), transactional(_transactional), errorCode(200), errorText("Ok"), running(false)
{
}

Channel::~Channel(){}

void Channel::open(ConnectionImpl::shared_ptr c, SessionCore::shared_ptr s)
{
    if (isOpen())
        THROW_QPID_ERROR(INTERNAL_ERROR, "Attempt to re-open channel");

    connection = c;
    session = s;
}
    
bool Channel::isOpen() const { 
    Mutex::ScopedLock l(lock);
    return connection; 
}

void Channel::setQos() {
    sendSync(false, make_shared_ptr(new BasicQosBody(version, 0, getPrefetch(), false)));
    if(isTransactional()) {
        //I think this is wrong! should only send TxSelect once...
        sendSync(false, make_shared_ptr(new TxSelectBody(version)));
    }
}

void Channel::setPrefetch(uint16_t _prefetch){
    prefetch = _prefetch;
    setQos();
}

void Channel::declareExchange(Exchange& exchange, bool synch){
    string name = exchange.getName();
    string type = exchange.getType();
    FieldTable args;
    sendSync(synch, make_shared_ptr(new ExchangeDeclareBody(version, 0, name, type, empty, false, false, false, args)));
}

void Channel::deleteExchange(Exchange& exchange, bool synch){
    string name = exchange.getName();
    sendSync(synch, make_shared_ptr(new ExchangeDeleteBody(version, 0, name, false)));
}

void Channel::declareQueue(Queue& queue, bool synch){
    string name = queue.getName();
    FieldTable args;
    QueueDeclareOkBody::shared_ptr response =
        sendAndReceiveSync<QueueDeclareOkBody>(
            synch,
            make_shared_ptr(new QueueDeclareBody(
                version, 0, name, empty, false/*passive*/, queue.isDurable(),
                queue.isExclusive(), queue.isAutoDelete(), !synch, args)));
    if(synch) {
        if(queue.getName().length() == 0)
            queue.setName(response->getQueue());
    }
}

void Channel::deleteQueue(Queue& queue, bool ifunused, bool ifempty, bool synch){
    //ticket, queue, ifunused, ifempty, nowait
    string name = queue.getName();
    sendAndReceiveSync<QueueDeleteOkBody>(
        synch,
        make_shared_ptr(new QueueDeleteBody(version, 0, name, ifunused, ifempty, !synch)));
}

void Channel::bind(const Exchange& exchange, const Queue& queue, const std::string& key, const FieldTable& args, bool synch){
    string e = exchange.getName();
    string q = queue.getName();
    sendSync(synch, make_shared_ptr(new QueueBindBody(version, 0, q, e, key, args)));
}

void Channel::commit(){
    sendSync(false, make_shared_ptr(new TxCommitBody(version)));
}

void Channel::rollback(){
    sendSync(false, make_shared_ptr(new TxRollbackBody(version)));
}

void Channel::close()
{
    session->close();
    {
        Mutex::ScopedLock l(lock);
        if (connection);
        {
            connection->released(session);
            connection.reset();
        }
    }
    stop();
}

// Channel closed by peer.
void Channel::peerClose(uint16_t code, const std::string& message) {
    assert(isOpen());
    //record reason:
    errorCode = code;
    errorText = message;
    stop();
}

AMQMethodBody::shared_ptr Channel::sendAndReceive(AMQMethodBody::shared_ptr toSend, ClassId /*c*/, MethodId /*m*/)
{
    session->setSync(true);
    Response r = session->send(toSend, true);
    session->setSync(false);
    return r.getPtr();
}

void Channel::sendSync(bool sync, AMQMethodBody::shared_ptr command)
{
    if(sync) {
        session->setSync(true);
        session->send(command, false);
        session->setSync(false);
    } else {
        session->send(command);
    }
}

AMQMethodBody::shared_ptr Channel::sendAndReceiveSync(
    bool sync, AMQMethodBody::shared_ptr body, ClassId c, MethodId m)
{
    if(sync)
        return sendAndReceive(body, c, m);
    else {
        session->send(body);
        return AMQMethodBody::shared_ptr();
    }
}

void Channel::consume(
    Queue& queue, const std::string& tag, MessageListener* listener, 
    AckMode ackMode, bool noLocal, bool synch, const FieldTable* fields) {

    if (tag.empty()) {
        throw Exception("A tag must be specified for a consumer."); 
    }
    {
        Mutex::ScopedLock l(lock);
        ConsumerMap::iterator i = consumers.find(tag);
        if (i != consumers.end())
            throw Exception(boost::format("Consumer already exists with tag: '%1%'") % tag);
        Consumer& c = consumers[tag];
        c.listener = listener;
        c.ackMode = ackMode;
        c.lastDeliveryTag = 0;
    }
    sendAndReceiveSync<BasicConsumeOkBody>(
            synch,
            make_shared_ptr(new BasicConsumeBody(
                version, 0, queue.getName(), tag, noLocal,
                ackMode == NO_ACK, false, !synch,
                fields ? *fields : FieldTable())));
}
        
void Channel::cancel(const std::string& tag, bool synch) {
    Consumer c;
    {
        Mutex::ScopedLock l(lock);
        ConsumerMap::iterator i = consumers.find(tag);
        if (i == consumers.end())
            return;
        c = i->second;
        consumers.erase(i);
    }
    sendAndReceiveSync<BasicCancelOkBody>(
        synch, make_shared_ptr(new BasicCancelBody(version, tag, !synch)));
}

bool Channel::get(Message& msg, const Queue& queue, AckMode ackMode) {

    AMQMethodBody::shared_ptr request(new BasicGetBody(version, 0, queue.getName(), ackMode));
    Response response = session->send(request, true);
    if (response.isA<BasicGetEmptyBody>()) {
        return false;
    } else {
        ReceivedContent::shared_ptr content = gets.pop();
        content->populate(msg);
        return true;
    }
}

void Channel::publish(const Message& msg, const Exchange& exchange,
                      const std::string& routingKey, 
                      bool mandatory, bool immediate) {

    const string e = exchange.getName();
    string key = routingKey;

    session->send(make_shared_ptr(new BasicPublishBody(version, 0, e, key, mandatory, immediate)), msg, false);
}

void Channel::start(){
    running = true;
    dispatcher = Thread(*this);
}

void Channel::stop() {
    session->stop();
    gets.close();
    Mutex::ScopedLock l(stopLock);
    if(running) {
        dispatcher.join();
        running = false;
    }
}

void Channel::run() {
    try {
        while (true) {
            ReceivedContent::shared_ptr content = session->get();
            //need to dispatch this to the relevant listener:
            if (content->isA<BasicDeliverBody>()) {
                ConsumerMap::iterator i = consumers.find(content->as<BasicDeliverBody>()->getConsumerTag());
                if (i != consumers.end()) {
                    Message msg;
                    content->populate(msg);
                    i->second.listener->received(msg);
                } else {
                    QPID_LOG(warning, "Dropping message for unrecognised consumer: " << content->getMethod());                        
                }               
            } else if (content->isA<BasicGetOkBody>()) {
                gets.push(content);
            } else {
                QPID_LOG(warning, "Dropping unsupported message type: " << content->getMethod());                        
            }
        }
    } catch (const QueueClosed&) {}
}
