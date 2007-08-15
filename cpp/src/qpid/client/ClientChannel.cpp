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

class ScopedSync
{
    Session& session;
public:
    ScopedSync(Session& s, bool enabled = true) : session(s) { session.setSynchronous(enabled); }
    ~ScopedSync() { session.setSynchronous(false); }
};

}}

Channel::Channel(bool _transactional, u_int16_t _prefetch) :
    prefetch(_prefetch), transactional(_transactional), running(false)
{
}

Channel::~Channel()
{
    join();
}

void Channel::open(ConnectionImpl::shared_ptr c, SessionCore::shared_ptr s)
{
    if (isOpen())
        THROW_QPID_ERROR(INTERNAL_ERROR, "Attempt to re-open channel");

    connection = c;
    sessionCore = s;
    session = auto_ptr<Session>(new Session(c, s));
}
    
bool Channel::isOpen() const { 
    Mutex::ScopedLock l(lock);
    return connection; 
}

void Channel::setQos() {
    session->basicQos(0, getPrefetch(), false);
    if(isTransactional()) {
        //I think this is wrong! should only send TxSelect once...
        session->txSelect();
    }
}

void Channel::setPrefetch(uint16_t _prefetch){
    prefetch = _prefetch;
    setQos();
}

void Channel::declareExchange(Exchange& exchange, bool synch){
    FieldTable args;
    ScopedSync s(*session, synch);
    session->exchangeDeclare(0, exchange.getName(), exchange.getType(), empty, false, false, false, args);
}

void Channel::deleteExchange(Exchange& exchange, bool synch){
    ScopedSync s(*session, synch);
    session->exchangeDelete(0, exchange.getName(), false);
}

void Channel::declareQueue(Queue& queue, bool synch){
    FieldTable args;
    ScopedSync s(*session, synch);
    Response r = session->queueDeclare(0, queue.getName(), empty, false/*passive*/, queue.isDurable(),
                                       queue.isExclusive(), queue.isAutoDelete(), !synch, args);
    
    if(synch) {
        if(queue.getName().length() == 0)
            queue.setName(r.as<QueueDeclareOkBody>().getQueue());
    }
}

void Channel::deleteQueue(Queue& queue, bool ifunused, bool ifempty, bool synch){
    ScopedSync s(*session, synch);
    session->queueDelete(0, queue.getName(), ifunused, ifempty, !synch);
}

void Channel::bind(const Exchange& exchange, const Queue& queue, const std::string& key, const FieldTable& args, bool synch){
    string e = exchange.getName();
    string q = queue.getName();
    ScopedSync s(*session, synch);
    session->queueBind(0, q, e, key, args);
}

void Channel::commit(){
    session->txCommit();
}

void Channel::rollback(){
    session->txRollback();
}

void Channel::close()
{
    session->close();
    {
        Mutex::ScopedLock l(lock);
        if (connection);
        {
            session.reset();
            sessionCore.reset();
            connection.reset();
        }
    }
    stop();
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
    ScopedSync s(*session, synch);
    session->basicConsume(0, queue.getName(), tag, noLocal,
                          ackMode == NO_ACK, false, !synch,
                          fields ? *fields : FieldTable());
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
    ScopedSync s(*session, synch);
    session->basicCancel(tag, !synch);
}

bool Channel::get(Message& msg, const Queue& queue, AckMode ackMode) {
    Response response = session->basicGet(0, queue.getName(), ackMode == NO_ACK);
    sessionCore->flush();//TODO: need to expose the ability to request completion info through session
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

    session->basicPublish(0, e, key, mandatory, immediate, msg);
}

void Channel::start(){
    running = true;
    dispatcher = Thread(*this);
}

void Channel::stop() {
    //session->stop();
    gets.close();
    join();
}

void Channel::join() {
    Mutex::ScopedLock l(stopLock);
    if(running && dispatcher.id()) {
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
