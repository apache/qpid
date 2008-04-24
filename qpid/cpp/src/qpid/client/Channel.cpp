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
#include <sstream>
#include "Channel.h"
#include "qpid/sys/Monitor.h"
#include "AckPolicy.h"
#include "Message.h"
#include "Connection.h"
#include "Demux.h"
#include "MessageListener.h"
#include "MessageQueue.h"
#include <boost/format.hpp>
#include <boost/bind.hpp>
#include "qpid/framing/all_method_bodies.h"
#include "qpid/framing/reply_exceptions.h"

using namespace std;
using namespace boost;
using namespace qpid::framing;
using namespace qpid::sys;

namespace qpid{
namespace client{
using namespace arg;

const std::string empty;

class ScopedSync
{
    Session& session;
    const bool change; 
    const bool value;
  public:
    ScopedSync(Session& s, bool desired = true) : session(s), change(s.isSynchronous() != desired), value(desired)
    { 
        if (change) session.setSynchronous(value); 
    }
    ~ScopedSync() 
    { 
        if (change) session.setSynchronous(!value); 
    }
};

Channel::Channel(bool _transactional, u_int16_t _prefetch) :
    prefetch(_prefetch), transactional(_transactional), running(false), 
    uniqueId(true)/*could eventually be the session id*/, nameCounter(0), active(false)
{
}

Channel::~Channel()
{
    join();
}

void Channel::open(const Session& s)
{
    Mutex::ScopedLock l(stopLock);
    if (isOpen())
        throw SessionBusyException();
    active = true;
    session = s;
    if(isTransactional()) {
        session.txSelect();
    }
}
    
bool Channel::isOpen() const { 
    Mutex::ScopedLock l(stopLock);
    return active; 
}

void Channel::setPrefetch(uint32_t _prefetch){
    prefetch = _prefetch;
}

void Channel::declareExchange(Exchange& _exchange, bool synch){
    ScopedSync s(session, synch);
    session.exchangeDeclare(exchange=_exchange.getName(), type=_exchange.getType());
}

void Channel::deleteExchange(Exchange& _exchange, bool synch){
    ScopedSync s(session, synch);
    session.exchangeDelete(exchange=_exchange.getName(), ifUnused=false);
}

void Channel::declareQueue(Queue& _queue, bool synch){
    if (_queue.getName().empty()) {
        stringstream uniqueName;
        uniqueName << uniqueId << "-queue-" << ++nameCounter;
        _queue.setName(uniqueName.str());
    }

    ScopedSync s(session, synch);
    session.queueDeclare(queue=_queue.getName(), passive=false/*passive*/, durable=_queue.isDurable(),
                              exclusive=_queue.isExclusive(), autoDelete=_queue.isAutoDelete());
    
}

void Channel::deleteQueue(Queue& _queue, bool ifunused, bool ifempty, bool synch){
    ScopedSync s(session, synch);
    session.queueDelete(queue=_queue.getName(), ifUnused=ifunused, ifEmpty=ifempty);
}

void Channel::bind(const Exchange& exchange, const Queue& queue, const std::string& key, const FieldTable& args, bool synch){
    string e = exchange.getName();
    string q = queue.getName();
    ScopedSync s(session, synch);
    session.exchangeBind(q, e, key, args);
}

void Channel::commit(){
    session.txCommit();
}

void Channel::rollback(){
    session.txRollback();
}

void Channel::consume(
    Queue& _queue, const std::string& tag, MessageListener* listener, 
    AckMode ackMode, bool noLocal, bool synch, FieldTable* fields) {

    if (tag.empty()) {
        throw Exception("A tag must be specified for a consumer."); 
    }
    {
        Mutex::ScopedLock l(lock);
        ConsumerMap::iterator i = consumers.find(tag);
        if (i != consumers.end())
            throw PreconditionFailedException(QPID_MSG("Consumer already exists with tag " << tag ));
        Consumer& c = consumers[tag];
        c.listener = listener;
        c.ackMode = ackMode;
        c.count = 0;
    }
    uint8_t confirmMode = ackMode == NO_ACK ? 1 : 0;
    ScopedSync s(session, synch);
    FieldTable ft;
    FieldTable* ftptr = fields ? fields : &ft;
    if (noLocal) {
        ftptr->setString("qpid.no-local","yes");
    }
    session.messageSubscribe(_queue.getName(), tag, 
                             confirmMode, 0/*pre-acquire*/, 
                             false, "", 0, *ftptr);
    if (!prefetch) {
        session.messageSetFlowMode(tag, 0/*credit based*/);
    }

    //allocate some credit:
    session.messageFlow(tag, 1/*BYTES*/, 0xFFFFFFFF);
    session.messageFlow(tag, 0/*MESSAGES*/, prefetch ? prefetch : 0xFFFFFFFF);
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
    ScopedSync s(session, synch);
    session.messageCancel(tag);
}

bool Channel::get(Message& msg, const Queue& _queue, AckMode ackMode) {
    string tag = "get-handler";
    ScopedDivert handler(tag, session.getExecution().getDemux());
    Demux::QueuePtr incoming = handler.getQueue();

    session.messageSubscribe(destination=tag, queue=_queue.getName(), acceptMode=(ackMode == NO_ACK ? 1 : 0));
    session.messageFlow(tag, 1/*BYTES*/, 0xFFFFFFFF);
    session.messageFlow(tag, 0/*MESSAGES*/, 1);
    {
        ScopedSync s(session);
        session.messageFlush(tag);
    }
    session.messageCancel(tag);

    FrameSet::shared_ptr p;
    if (incoming->tryPop(p)) {
        msg.populate(*p);
        if (ackMode == AUTO_ACK) {
            AckPolicy acker;
            acker.ack(msg, session);
        } else {
            session.markCompleted(msg.getId(), false, false);
        }
        return true;
    }
    else
        return false;
}

void Channel::publish(Message& msg, const Exchange& exchange,
                      const std::string& routingKey, 
                      bool mandatory, bool /*?TODO-restore immediate?*/) {

    msg.getDeliveryProperties().setRoutingKey(routingKey);
    msg.getDeliveryProperties().setDiscardUnroutable(!mandatory);
    session.messageTransfer(destination=exchange.getName(), content=msg);
}

void Channel::close()
{
    session.close();
    {
        Mutex::ScopedLock l(stopLock);
        active = false;
    }
    stop();
}

void Channel::start(){
    running = true;
    dispatcher = Thread(*this);
}

void Channel::stop() {
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

void Channel::dispatch(FrameSet& content, const std::string& destination)
{
    ConsumerMap::iterator i = consumers.find(destination);
    if (i != consumers.end()) {
        Message msg;
        msg.populate(content);
        MessageListener* listener = i->second.listener;
        listener->received(msg);
        if (isOpen() && i->second.ackMode != CLIENT_ACK) {
            bool send = i->second.ackMode == AUTO_ACK
                || (prefetch &&  ++(i->second.count) > (prefetch / 2));
            if (send) i->second.count = 0;
            session.markCompleted(content.getId(), true, send);
        }
    } else {
        QPID_LOG(warning, "Dropping message for unrecognised consumer: " << destination);                        
    }
}

void Channel::run() {
    try {
        while (true) {
            FrameSet::shared_ptr content = session.get();
            //need to dispatch this to the relevant listener:
            if (content->isA<MessageTransferBody>()) {
                dispatch(*content, content->as<MessageTransferBody>()->getDestination());
            } else {
                QPID_LOG(warning, "Dropping unsupported message type: " << content->getMethod());                        
            }
        }
    } catch (const ClosedException&) {}
}

}}

