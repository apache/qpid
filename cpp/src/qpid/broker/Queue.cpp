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

#include "Broker.h"
#include "Queue.h"
#include "Exchange.h"
#include "DeliverableMessage.h"
#include "MessageStore.h"
#include "QueueRegistry.h"

#include "qpid/StringUtils.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Time.h"

#include <iostream>
#include <algorithm>
#include <functional>

#include <boost/bind.hpp>
#include <boost/intrusive_ptr.hpp>

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
             const OwnershipToken* const _owner,
             Manageable* parent) :

    name(_name), 
    autodelete(_autodelete),
    store(_store),
    owner(_owner), 
    consumerCount(0),
    exclusive(0),
    noLocal(false),
    persistenceId(0),
    policyExceeded(false),
    mgmtObject(0)
{
    if (parent != 0)
    {
        ManagementAgent* agent = ManagementAgent::getAgent ();

        if (agent != 0)
        {
            mgmtObject = new management::Queue (agent, this, parent, _name, _store != 0, _autodelete, _owner != 0);

            // Add the object to the management agent only if this queue is not durable.
            // If it's durable, we will add it later when the queue is assigned a persistenceId.
            if (store == 0)
                agent->addObject (mgmtObject);
        }
    }
}

Queue::~Queue()
{
    if (mgmtObject != 0)
        mgmtObject->resourceDestroy ();
}

void Queue::notifyDurableIOComplete()
{
    Mutex::ScopedLock locker(messageLock);
    notify();
}

bool isLocalTo(const OwnershipToken* token, boost::intrusive_ptr<Message>& msg)
{
    return token && token->isLocal(msg->getPublisher());
}

bool Queue::isLocal(boost::intrusive_ptr<Message>& msg)
{
    //message is considered local if it was published on the same
    //connection as that of the session which declared this queue
    //exclusive (owner) or which has an exclusive subscription
    //(exclusive)
    return noLocal && (isLocalTo(owner, msg) || isLocalTo(exclusive, msg));
}

bool Queue::isExcluded(boost::intrusive_ptr<Message>& msg)
{
    return traceExclude.size() && msg->isExcluded(traceExclude);
}

void Queue::deliver(boost::intrusive_ptr<Message>& msg){

    if (msg->isImmediate() && getConsumerCount() == 0) {
        if (alternateExchange) {
            DeliverableMessage deliverable(msg);
            alternateExchange->route(deliverable, msg->getRoutingKey(), msg->getApplicationHeaders());
        }
    } else if (isLocal(msg)) {
        //drop message
        QPID_LOG(info, "Dropping 'local' message from " << getName());
    } else if (isExcluded(msg)) {
        //drop message
        QPID_LOG(info, "Dropping excluded message from " << getName());
    } else {
        // if no store then mark as enqueued
        if (!enqueue(0, msg)){
            if (mgmtObject != 0) {
                mgmtObject->inc_msgTotalEnqueues ();
                mgmtObject->inc_byteTotalEnqueues (msg->contentSize ());
            }
            push(msg);
            msg->enqueueComplete();
        }else {
            if (mgmtObject != 0) {
                mgmtObject->inc_msgTotalEnqueues ();
                mgmtObject->inc_byteTotalEnqueues (msg->contentSize ());
                mgmtObject->inc_msgPersistEnqueues ();
                mgmtObject->inc_bytePersistEnqueues (msg->contentSize ());
            }
            push(msg);
        }
        QPID_LOG(debug, "Message " << msg << " enqueued on " << name << "[" << this << "]");
    }
}


void Queue::recover(boost::intrusive_ptr<Message>& msg){
    push(msg);
    msg->enqueueComplete(); // mark the message as enqueued
    if (mgmtObject != 0) {
        mgmtObject->inc_msgTotalEnqueues ();
        mgmtObject->inc_byteTotalEnqueues (msg->contentSize ());
        mgmtObject->inc_msgPersistEnqueues ();
        mgmtObject->inc_bytePersistEnqueues (msg->contentSize ());
    }

    if (store && !msg->isContentLoaded()) {
        //content has not been loaded, need to ensure that lazy loading mode is set:
        //TODO: find a nicer way to do this
        msg->releaseContent(store);
    }
}

void Queue::process(boost::intrusive_ptr<Message>& msg){
    push(msg);
    if (mgmtObject != 0) {
        mgmtObject->inc_msgTotalEnqueues ();
        mgmtObject->inc_byteTotalEnqueues (msg->contentSize ());
        mgmtObject->inc_msgTxnEnqueues ();
        mgmtObject->inc_byteTxnEnqueues (msg->contentSize ());
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
    QPID_LOG(debug, "attempting to acquire " << msg.position);
    for (Messages::iterator i = messages.begin(); i != messages.end(); i++) {
        if (i->position == msg.position) {
            messages.erase(i);
            QPID_LOG(debug, "Match found, acquire succeeded: " << i->position << " == " << msg.position);
            return true;
        } else {
            QPID_LOG(debug, "No match: " << i->position << " != " << msg.position);
        }
    }
    QPID_LOG(debug, "Acquire failed for " << msg.position);
    return false;
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
            if (store && !msg.payload->isEnqueueComplete()) { 
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
                QPID_LOG(debug, "Consumer doesn't want message from '" << name << "'");
                return false;
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
                //browser hasn't got enough credit for the message
                QPID_LOG(debug, "Browser can't currently accept message from '" << name << "'");
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

/**
 * notify listeners that there may be messages to process
 */
void Queue::notify()
{
    if (listeners.empty()) return;

    Listeners copy(listeners);
    listeners.clear();

    sys::ScopedLock<Guard> g(notifierLock);//prevent consumers being deleted while held in copy
    {
        Mutex::ScopedUnlock u(messageLock);
        for_each(copy.begin(), copy.end(), mem_fun(&Consumer::notify));
    }
}

void Queue::removeListener(Consumer& c)
{
    Mutex::ScopedLock locker(messageLock);
    notifierLock.wait(messageLock);//wait until no notifies are in progress 
    Listeners::iterator i = find(listeners.begin(), listeners.end(), &c);
    if (i != listeners.end()) listeners.erase(i);
}

void Queue::addListener(Consumer& c)
{
    Listeners::iterator i = find(listeners.begin(), listeners.end(), &c);
    if (i == listeners.end()) listeners.push_back(&c);
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
            //TODO: can improve performance of this search, for now just searching linearly from end
            Messages::reverse_iterator pos;
            for (Messages::reverse_iterator i = messages.rbegin(); i != messages.rend() && i->position > c.position; i++) {
                pos = i;
            }
            msg = *pos;
            return true;
        }
    }
    addListener(c);
    return false;
}

void Queue::consume(Consumer& c, bool requestExclusive){
    Mutex::ScopedLock locker(consumerLock);
    if(exclusive) {
        throw ResourceLockedException(
            QPID_MSG("Queue " << getName() << " has an exclusive consumer. No more consumers allowed."));
    } else if(requestExclusive) {
        if(consumerCount) {
            throw ResourceLockedException(
                QPID_MSG("Queue " << getName() << " already has consumers. Exclusive access denied."));
        } else {
            exclusive = c.getSession();
        }
    }
    consumerCount++;

    if (mgmtObject != 0)
        mgmtObject->inc_consumerCount ();
}

void Queue::cancel(Consumer& c){
    removeListener(c);
    Mutex::ScopedLock locker(consumerLock);
    consumerCount--;
    if(exclusive) exclusive = 0;
    if (mgmtObject != 0)
        mgmtObject->dec_consumerCount ();
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
    while(!messages.empty()) {
        QueuedMessage& msg = messages.front();
        if (store && msg.payload->isPersistent()) {
            boost::intrusive_ptr<PersistableMessage> pmsg =
                boost::static_pointer_cast<PersistableMessage>(msg.payload);
            store->dequeue(0, pmsg, *this);
        }
        pop();
    }
    return count;
}

/**
 * Assumes messageLock is held
 */
void Queue::pop(){
    QueuedMessage& msg = messages.front();

    if (policy.get()) policy->dequeued(msg.payload->contentSize());
    if (mgmtObject != 0){
        mgmtObject->inc_msgTotalDequeues  ();
        mgmtObject->inc_byteTotalDequeues (msg.payload->contentSize());
        if (msg.payload->isPersistent ()){
            mgmtObject->inc_msgPersistDequeues ();
            mgmtObject->inc_bytePersistDequeues (msg.payload->contentSize());
        }
    }
    messages.pop_front();
}

void Queue::push(boost::intrusive_ptr<Message>& msg){
    Mutex::ScopedLock locker(messageLock);   
    messages.push_back(QueuedMessage(this, msg, ++sequence));
    if (policy.get()) {
        policy->enqueued(msg->contentSize());
        if (policy->limitExceeded()) {
            if (!policyExceeded) {
                policyExceeded = true;
                QPID_LOG(info, "Queue size exceeded policy for " << name);
            }
            if (store) {
                QPID_LOG(debug, "Message " << msg << " on " << name << " released from memory");
                msg->releaseContent(store);
            } else {
                QPID_LOG(error, "Message " << msg << " on " << name
                         << " exceeds the policy for the queue but can't be released from memory as the queue is not durable");
                throw ResourceLimitExceededException(QPID_MSG("Policy exceeded for " << name));
            }
        } else {
            if (policyExceeded) {
                policyExceeded = false;
                QPID_LOG(info, "Queue size within policy for " << name);
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
bool Queue::enqueue(TransactionContext* ctxt, boost::intrusive_ptr<Message> msg)
{
    if (traceId.size()) {
        msg->addTraceId(traceId);
    }

    if (msg->isPersistent() && store) {
        msg->enqueueAsync(shared_from_this(), store); //increment to async counter -- for message sent to more than one queue
        boost::intrusive_ptr<PersistableMessage> pmsg = boost::static_pointer_cast<PersistableMessage>(msg);
        store->enqueue(ctxt, pmsg, *this);
        return true;
    }
    //msg->enqueueAsync();   // increments intrusive ptr cnt
    return false;
}

// return true if store exists, 
bool Queue::dequeue(TransactionContext* ctxt, boost::intrusive_ptr<Message> msg)
{
    if (msg->isPersistent() && store) {
        msg->dequeueAsync(shared_from_this(), store); //increment to async counter -- for message sent to more than one queue
        boost::intrusive_ptr<PersistableMessage> pmsg = boost::static_pointer_cast<PersistableMessage>(msg);
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
    const std::string qpidNoLocal("no-local");
    const std::string qpidTraceIdentity("qpid.trace.id");
    const std::string qpidTraceExclude("qpid.trace.exclude");
}

void Queue::create(const FieldTable& _settings)
{
    settings = _settings;
    if (store) {
        store->create(*this, _settings);
    }
    configure(_settings);
}

void Queue::configure(const FieldTable& _settings)
{
    std::auto_ptr<QueuePolicy> _policy(new QueuePolicy(_settings));
    if (_policy->getMaxCount() || _policy->getMaxSize()) {
        setPolicy(_policy);
    }
    //set this regardless of owner to allow use of no-local with exclusive consumers also
    noLocal = _settings.get(qpidNoLocal);
    QPID_LOG(debug, "Configured queue with no-local=" << noLocal);

    traceId = _settings.getString(qpidTraceIdentity);
    std::string excludeList = _settings.getString(qpidTraceExclude);
    if (excludeList.size()) {
        split(traceExclude, excludeList, ", ");
    }
    QPID_LOG(debug, "Configured queue " << getName() << " with qpid.trace.id='" << traceId 
             << "' and qpid.trace.exclude='"<< excludeList << "' i.e. " << traceExclude.size() << " elements");

    if (mgmtObject != 0)
        mgmtObject->set_arguments (_settings);
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
        store->flush(*this);
        store->destroy(*this);
        store = 0;//ensure we make no more calls to the store for this queue
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

const QueuePolicy* Queue::getPolicy()
{
    return policy.get();
}

uint64_t Queue::getPersistenceId() const 
{ 
    return persistenceId; 
}

void Queue::setPersistenceId(uint64_t _persistenceId) const
{
    if (mgmtObject != 0 && persistenceId == 0)
    {
        ManagementAgent* agent = ManagementAgent::getAgent ();
        agent->addObject (mgmtObject, _persistenceId, 3);

        if (externalQueueStore) {
            ManagementObject* childObj = externalQueueStore->GetManagementObject();
            if (childObj != 0)
                childObj->setReference(mgmtObject->getObjectId());
        }
    }
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

bool Queue::isExclusiveOwner(const OwnershipToken* const o) const 
{ 
    Mutex::ScopedLock locker(ownershipLock);
    return o == owner; 
}

void Queue::releaseExclusiveOwnership() 
{ 
    Mutex::ScopedLock locker(ownershipLock);
    owner = 0; 
}

bool Queue::setExclusiveOwner(const OwnershipToken* const o) 
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

void Queue::setExternalQueueStore(ExternalQueueStore* inst) {
    if (externalQueueStore!=inst && externalQueueStore) 
        delete externalQueueStore; 
    externalQueueStore = inst;

    if (inst) {
        ManagementObject* childObj = inst->GetManagementObject();
        if (childObj != 0 && mgmtObject != 0)
            childObj->setReference(mgmtObject->getObjectId());
    }
}

/*
 * Use of Guard requires an external lock to be held before calling
 * any of its methods
 */
Queue::Guard::Guard() : count(0) {}

void Queue::Guard::lock()
{
    count++;
}

void Queue::Guard::unlock()
{
    if (--count == 0) condition.notifyAll();
}

void Queue::Guard::wait(sys::Mutex& m)
{
    while (count) condition.wait(m);
}

ManagementObject* Queue::GetManagementObject (void) const
{
    return (ManagementObject*) mgmtObject;
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
    }

    return status;
}
