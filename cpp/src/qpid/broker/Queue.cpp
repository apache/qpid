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
#include "qpid/framing/reply_exceptions.h"
#include "Broker.h"
#include "Queue.h"
#include "Exchange.h"
#include "DeliverableMessage.h"
#include "MessageStore.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Time.h"
#include <iostream>
#include <boost/bind.hpp>
#include "QueueRegistry.h"
#include <algorithm>
#include <functional>

using namespace qpid::broker;
using namespace qpid::sys;
using namespace qpid::framing;
using qpid::management::ManagementAgent;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;
using std::for_each;
using std::mem_fun;

Queue::Queue(const string& _name, bool _autodelete, 
             MessageStore* const _store,
             const ConnectionToken* const _owner,
             Manageable* parent) :

    name(_name), 
    autodelete(_autodelete),
    store(_store),
    owner(_owner), 
    consumerCount(0),
    exclusive(false),
    persistenceId(0)
{
    if (parent != 0)
    {
        ManagementAgent::shared_ptr agent = ManagementAgent::getAgent ();

        if (agent.get () != 0)
        {
            mgmtObject = management::Queue::shared_ptr
                (new management::Queue (this, parent, _name, _store != 0, _autodelete, 0));
            agent->addObject (mgmtObject);
        }
    }
}

Queue::~Queue()
{
    if (mgmtObject.get () != 0)
        mgmtObject->resourceDestroy ();
}

void Queue::notifyDurableIOComplete()
{
    Mutex::ScopedLock locker(messageLock);
    notify();
}


void Queue::deliver(intrusive_ptr<Message>& msg){
    if (msg->isImmediate() && getConsumerCount() == 0) {
        if (alternateExchange) {
            DeliverableMessage deliverable(msg);
            alternateExchange->route(deliverable, msg->getRoutingKey(), msg->getApplicationHeaders());
        }
    } else {


        // if no store then mark as enqueued
        if (!enqueue(0, msg)){
            push(msg);
            msg->enqueueComplete();
            if (mgmtObject.get() != 0) {
                Mutex::ScopedLock alock(mgmtObject->accessorLock);
                mgmtObject->inc_msgTotalEnqueues ();
                mgmtObject->inc_byteTotalEnqueues (msg->contentSize ());
                mgmtObject->inc_msgDepth ();
                mgmtObject->inc_byteDepth (msg->contentSize ());
            }
        }else {
            if (mgmtObject.get() != 0) {
                Mutex::ScopedLock alock(mgmtObject->accessorLock);
                mgmtObject->inc_msgTotalEnqueues ();
                mgmtObject->inc_byteTotalEnqueues (msg->contentSize ());
                mgmtObject->inc_msgDepth ();
                mgmtObject->inc_byteDepth (msg->contentSize ());
                mgmtObject->inc_msgPersistEnqueues ();
                mgmtObject->inc_bytePersistEnqueues (msg->contentSize ());
            }
            push(msg);
        }
        QPID_LOG(debug, "Message " << msg << " enqueued on " << name << "[" << this << "]");
    }
}


void Queue::recover(intrusive_ptr<Message>& msg){
    push(msg);
    msg->enqueueComplete(); // mark the message as enqueued
    if (mgmtObject.get() != 0) {
        Mutex::ScopedLock alock(mgmtObject->accessorLock);
        mgmtObject->inc_msgTotalEnqueues ();
        mgmtObject->inc_byteTotalEnqueues (msg->contentSize ());
        mgmtObject->inc_msgPersistEnqueues ();
        mgmtObject->inc_bytePersistEnqueues (msg->contentSize ());
        mgmtObject->inc_msgDepth ();
        mgmtObject->inc_byteDepth (msg->contentSize ());
    }

    if (store && !msg->isContentLoaded()) {
        //content has not been loaded, need to ensure that lazy loading mode is set:
        //TODO: find a nicer way to do this
        msg->releaseContent(store);
    }
}

void Queue::process(intrusive_ptr<Message>& msg){
    push(msg);
    if (mgmtObject.get() != 0) {
        Mutex::ScopedLock alock(mgmtObject->accessorLock);
        mgmtObject->inc_msgTotalEnqueues ();
        mgmtObject->inc_byteTotalEnqueues (msg->contentSize ());
        mgmtObject->inc_msgTxnEnqueues ();
        mgmtObject->inc_byteTxnEnqueues (msg->contentSize ());
        mgmtObject->inc_msgDepth ();
        mgmtObject->inc_byteDepth (msg->contentSize ());
        if (msg->isPersistent ()) {
            mgmtObject->inc_msgPersistEnqueues ();
            mgmtObject->inc_bytePersistEnqueues (msg->contentSize ());
        }
    }
}

void Queue::requeue(const QueuedMessage& msg){
    Mutex::ScopedLock locker(messageLock);
    msg.payload->enqueueComplete(); // mark the message as enqueued
    messages.push_front(msg);
    notify();
}

bool Queue::acquire(const QueuedMessage& msg) {
    Mutex::ScopedLock locker(messageLock);
    for (Messages::iterator i = messages.begin(); i != messages.end(); i++) {
        if (i->position == msg.position) {
            messages.erase(i);
            return true;
        }
    }
    return false;
}

/**
 * Return true if the message can be excluded. This is currently the
 * case if the queue is exclusive and has an exclusive consumer that
 * doesn't want the message or has a single consumer that doesn't want
 * the message (covers the JMS topic case).
 */
bool Queue::canExcludeUnwanted()
{
    Mutex::ScopedLock locker(consumerLock);
    return hasExclusiveOwner() && (exclusive || consumerCount == 1);
}


bool Queue::getNextMessage(QueuedMessage& m, Consumer& c)
{
    if (c.preAcquires()) {
        return consumeNextMessage(m, c);
    } else {
        return browseNextMessage(m, c);
    }
}

bool Queue::consumeNextMessage(QueuedMessage& m, Consumer& c)
{
    while (true) {
        Mutex::ScopedLock locker(messageLock);
        if (messages.empty()) { 
            QPID_LOG(debug, "No messages to dispatch on queue '" << name << "'");
            addListener(c);
            return false;
        } else {
            QueuedMessage msg = messages.front();
            if (!msg.payload->isEnqueueComplete()) { 
                QPID_LOG(debug, "Messages not ready to dispatch on queue '" << name << "'");
                addListener(c);
                return false;
            }
            
            if (c.filter(msg.payload)) {
                if (c.accept(msg.payload)) {            
                    m = msg;
                    pop();
                    return true;
                } else {
                    //message(s) are available but consumer hasn't got enough credit
                    QPID_LOG(debug, "Consumer can't currently accept message from '" << name << "'");
                    return false;
                }
            } else {
                //consumer will never want this message
                if (canExcludeUnwanted()) {
                    //hack for no-local on JMS topics; get rid of this message
                    QPID_LOG(debug, "Excluding message from '" << name << "'");
                    pop();
                } else {
                    //leave it for another consumer
                    QPID_LOG(debug, "Consumer doesn't want message from '" << name << "'");
                    return false;
                }
            } 
        }
    }
}


bool Queue::browseNextMessage(QueuedMessage& m, Consumer& c)
{
    QueuedMessage msg(this);
    while (seek(msg, c)) {
        if (c.filter(msg.payload)) {
            if (c.accept(msg.payload)) {
                //consumer wants the message
                c.position = msg.position;
                m = msg;
                return true;
            } else {
                //consumer hasn't got enough credit for the message
                QPID_LOG(debug, "Consumer can't currently accept message from '" << name << "'");
                return false;
            }
        } else {
            //consumer will never want this message, continue seeking
            c.position = msg.position;
            QPID_LOG(debug, "Browser skipping message from '" << name << "'");
        }
    }
    return false;
}

void Queue::notify()
{
    //notify listeners that there may be messages to process
    for_each(listeners.begin(), listeners.end(), mem_fun(&Consumer::notify));
    listeners.clear();
}

void Queue::removeListener(Consumer& c)
{
    Mutex::ScopedLock locker(messageLock);
    listeners.erase(&c);
}

void Queue::addListener(Consumer& c)
{
    listeners.insert(&c);
}

bool Queue::dispatch(Consumer& c)
{
    QueuedMessage msg(this);
    if (getNextMessage(msg, c)) {
        c.deliver(msg);
        return true;
    } else {
        return false;
    }
}

bool Queue::seek(QueuedMessage& msg, Consumer& c) {
    Mutex::ScopedLock locker(messageLock);
    if (!messages.empty() && messages.back().position > c.position) {
        if (c.position < messages.front().position) {
            msg = messages.front();
            return true;
        } else {        
            uint index = (c.position - messages.front().position) + 1;
            if (index < messages.size()) {
                msg = messages[index];
                return true;
            } 
        }
    }
    addListener(c);
    return false;
}

void Queue::consume(Consumer&, bool requestExclusive){
    Mutex::ScopedLock locker(consumerLock);
    if(exclusive) {
        throw AccessRefusedException(
            QPID_MSG("Queue " << getName() << " has an exclusive consumer. No more consumers allowed."));
    } else if(requestExclusive) {
        if(consumerCount) {
            throw AccessRefusedException(
                QPID_MSG("Queue " << getName() << " already has consumers. Exclusive access denied."));
        } else {
            exclusive = true;
        }
    }
    consumerCount++;

    if (mgmtObject.get() != 0){
        Mutex::ScopedLock alock(mgmtObject->accessorLock);
        mgmtObject->inc_consumers ();
    }
}

void Queue::cancel(Consumer& c){
    removeListener(c);
    Mutex::ScopedLock locker(consumerLock);
    consumerCount--;
    if(exclusive) exclusive = false;
    if (mgmtObject.get() != 0){
        Mutex::ScopedLock alock(mgmtObject->accessorLock);
        mgmtObject->dec_consumers ();
    }
}

QueuedMessage Queue::dequeue(){
    Mutex::ScopedLock locker(messageLock);
    QueuedMessage msg(this);

    if(!messages.empty()){
        msg = messages.front();
        pop();
    }
    return msg;
}

uint32_t Queue::purge(){
    Mutex::ScopedLock locker(messageLock);
    int count = messages.size();
    while(!messages.empty()) pop();
    return count;
}

/**
 * Assumes messageLock is held
 */
void Queue::pop(){
    QueuedMessage& msg = messages.front();

    if (policy.get()) policy->dequeued(msg.payload->contentSize());
    if (mgmtObject.get() != 0){
        Mutex::ScopedLock alock(mgmtObject->accessorLock);
        mgmtObject->inc_msgTotalDequeues  ();
        mgmtObject->inc_byteTotalDequeues (msg.payload->contentSize());
        mgmtObject->dec_msgDepth ();
        mgmtObject->dec_byteDepth (msg.payload->contentSize());
        if (msg.payload->isPersistent ()){
            mgmtObject->inc_msgPersistDequeues ();
            mgmtObject->inc_bytePersistDequeues (msg.payload->contentSize());
        }
    }
    messages.pop_front();
}

void Queue::push(intrusive_ptr<Message>& msg){
    Mutex::ScopedLock locker(messageLock);   
    messages.push_back(QueuedMessage(this, msg, ++sequence));
    if (policy.get()) {
        policy->enqueued(msg->contentSize());
        if (policy->limitExceeded()) {
            if (store) {
                QPID_LOG(debug, "Message " << msg << " on " << name << " released from memory");
                msg->releaseContent(store);
            } else {
                QPID_LOG(warning, "Message " << msg << " on " << name
                         << " exceeds the policy for the queue but can't be released from memory as the queue is not durable");
            }
        }
    }
    notify();
}

/** function only provided for unit tests, or code not in critical message path */
uint32_t Queue::getMessageCount() const{
    Mutex::ScopedLock locker(messageLock);
  
    uint32_t count =0;
    for ( Messages::const_iterator i = messages.begin(); i != messages.end(); ++i ) {
        if ( i->payload->isEnqueueComplete() ) count ++;
    }
    
    return count;
}

uint32_t Queue::getConsumerCount() const{
    Mutex::ScopedLock locker(consumerLock);
    return consumerCount;
}

bool Queue::canAutoDelete() const{
    Mutex::ScopedLock locker(consumerLock);
    return autodelete && !consumerCount;
}

// return true if store exists, 
bool Queue::enqueue(TransactionContext* ctxt, intrusive_ptr<Message> msg)
{
    if (msg->isPersistent() && store) {
        msg->enqueueAsync(this, store); //increment to async counter -- for message sent to more than one queue
        intrusive_ptr<PersistableMessage> pmsg = static_pointer_cast<PersistableMessage>(msg);
        store->enqueue(ctxt, pmsg, *this);
        return true;
    }
    //msg->enqueueAsync();   // increments intrusive ptr cnt
    return false;
}

// return true if store exists, 
bool Queue::dequeue(TransactionContext* ctxt, intrusive_ptr<Message> msg)
{
    if (msg->isPersistent() && store) {
        msg->dequeueAsync(this, store); //increment to async counter -- for message sent to more than one queue
        intrusive_ptr<PersistableMessage> pmsg = static_pointer_cast<PersistableMessage>(msg);
        store->dequeue(ctxt, pmsg, *this);
        return true;
    }
    //msg->dequeueAsync();   // decrements intrusive ptr cnt
    return false;
}


namespace 
{
    const std::string qpidMaxSize("qpid.max_size");
    const std::string qpidMaxCount("qpid.max_count");
}

void Queue::create(const FieldTable& _settings)
{
    settings = _settings;
    //TODO: hold onto settings and persist them as part of encode
    //      in fact settings should be passed in on construction
    if (store) {
        store->create(*this);
    }
    configure(_settings);
}

void Queue::configure(const FieldTable& _settings)
{
    std::auto_ptr<QueuePolicy> _policy(new QueuePolicy(_settings));
    if (_policy->getMaxCount() || _policy->getMaxSize()) 
        setPolicy(_policy);
}

void Queue::destroy()
{
    if (alternateExchange.get()) {
        Mutex::ScopedLock locker(messageLock);
        while(!messages.empty()){
            DeliverableMessage msg(messages.front().payload);
            alternateExchange->route(msg, msg.getMessage().getRoutingKey(),
                                     msg.getMessage().getApplicationHeaders());
            pop();
        }
        alternateExchange->decAlternateUsers();
    }

    if (store) {
        store->destroy(*this);
    }
}

void Queue::bound(const string& exchange, const string& key,
                  const FieldTable& args)
{
    bindings.add(exchange, key, args);
}

void Queue::unbind(ExchangeRegistry& exchanges, Queue::shared_ptr shared_ref)
{
    bindings.unbind(exchanges, shared_ref);
}

void Queue::setPolicy(std::auto_ptr<QueuePolicy> _policy)
{
    policy = _policy;
}

const QueuePolicy* const Queue::getPolicy()
{
    return policy.get();
}

uint64_t Queue::getPersistenceId() const 
{ 
    return persistenceId; 
}

void Queue::setPersistenceId(uint64_t _persistenceId) const
{ 
    persistenceId = _persistenceId; 
}

void Queue::encode(framing::Buffer& buffer) const 
{
    buffer.putShortString(name);
    buffer.put(settings);
}

uint32_t Queue::encodedSize() const
{
    return name.size() + 1/*short string size octet*/ + settings.size();
}

Queue::shared_ptr Queue::decode(QueueRegistry& queues, framing::Buffer& buffer)
{
    string name;
    buffer.getShortString(name);
    std::pair<Queue::shared_ptr, bool> result = queues.declare(name, true);
    buffer.get(result.first->settings);
    result.first->configure(result.first->settings);
    return result.first;
}


void Queue::setAlternateExchange(boost::shared_ptr<Exchange> exchange)
{
    alternateExchange = exchange;
}

boost::shared_ptr<Exchange> Queue::getAlternateExchange()
{
    return alternateExchange;
}

void Queue::tryAutoDelete(Broker& broker, Queue::shared_ptr queue)
{
    if (broker.getQueues().destroyIf(queue->getName(), 
                                     boost::bind(boost::mem_fn(&Queue::canAutoDelete), queue))) {
        queue->unbind(broker.getExchanges(), queue);
        queue->destroy();
    }

}

bool Queue::isExclusiveOwner(const ConnectionToken* const o) const 
{ 
    Mutex::ScopedLock locker(ownershipLock);
    return o == owner; 
}

void Queue::releaseExclusiveOwnership() 
{ 
    Mutex::ScopedLock locker(ownershipLock);
    owner = 0; 
}

bool Queue::setExclusiveOwner(const ConnectionToken* const o) 
{ 
    Mutex::ScopedLock locker(ownershipLock);
    if (owner) {
        return false;
    } else {
        owner = o; 
        return true;
    }
}

bool Queue::hasExclusiveOwner() const 
{ 
    Mutex::ScopedLock locker(ownershipLock);
    return owner != 0; 
}

bool Queue::hasExclusiveConsumer() const 
{ 
    return exclusive; 
}

ManagementObject::shared_ptr Queue::GetManagementObject (void) const
{
    return dynamic_pointer_cast<ManagementObject> (mgmtObject);
}

Manageable::status_t Queue::ManagementMethod (uint32_t methodId,
                                              Args&    /*args*/)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;

    QPID_LOG (debug, "Queue::ManagementMethod [id=" << methodId << "]");

    switch (methodId)
    {
    case management::Queue::METHOD_PURGE :
        purge ();
        status = Manageable::STATUS_OK;
        break;

    case management::Queue::METHOD_INCREASEJOURNALSIZE :
        status = Manageable::STATUS_NOT_IMPLEMENTED;
        break;
    }

    return status;
}
