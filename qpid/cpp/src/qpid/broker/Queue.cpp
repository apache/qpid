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
#include "QueueEvents.h"
#include "Exchange.h"
#include "DeliverableMessage.h"
#include "MessageStore.h"
#include "NullMessageStore.h"
#include "QueueRegistry.h"

#include "qpid/StringUtils.h"
#include "qpid/log/Statement.h"
#include "qpid/management/ManagementBroker.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Time.h"
#include "qmf/org/apache/qpid/broker/ArgsQueuePurge.h"

#include <iostream>
#include <algorithm>
#include <functional>

#include <boost/bind.hpp>
#include <boost/intrusive_ptr.hpp>

using namespace qpid::broker;
using namespace qpid::sys;
using namespace qpid::framing;
using qpid::management::ManagementAgent;
using qpid::management::ManagementBroker;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;
using std::for_each;
using std::mem_fun;
namespace _qmf = qmf::org::apache::qpid::broker;


namespace 
{
const std::string qpidMaxSize("qpid.max_size");
const std::string qpidMaxCount("qpid.max_count");
const std::string qpidNoLocal("no-local");
const std::string qpidTraceIdentity("qpid.trace.id");
const std::string qpidTraceExclude("qpid.trace.exclude");
const std::string qpidLastValueQueue("qpid.last_value_queue");
const std::string qpidLastValueQueueNoBrowse("qpid.last_value_queue_no_browse");
const std::string qpidPersistLastNode("qpid.persist_last_node");
const std::string qpidVQMatchProperty("qpid.LVQ_key");
const std::string qpidQueueEventGeneration("qpid.queue_event_generation");

const int ENQUEUE_ONLY=1;
const int ENQUEUE_AND_DEQUEUE=2;
}

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
    lastValueQueue(false),
    lastValueQueueNoBrowse(false),
    persistLastNode(false),
    inLastNodeFailure(false),
    persistenceId(0),
    policyExceeded(false),
    mgmtObject(0),
    eventMode(0),
    eventMgr(0)
{
    if (parent != 0)
    {
        ManagementAgent* agent = ManagementAgent::Singleton::getInstance();

        if (agent != 0)
        {
            mgmtObject = new _qmf::Queue(agent, this, parent, _name, _store != 0, _autodelete, _owner != 0);

            // Add the object to the management agent only if this queue is not durable.
            // If it's durable, we will add it later when the queue is assigned a persistenceId.
            if (store == 0) {
                ManagementBroker* mb = dynamic_cast<ManagementBroker*>(agent);
                agent->addObject (mgmtObject, mb ? mb->allocateId(this) : 0);
            }
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
    QueueListeners::NotificationSet copy;
    {
        Mutex::ScopedLock locker(messageLock);
        listeners.populate(copy);
    }
    copy.notify();
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
            push(msg);
            msg->enqueueComplete();
        }else {
            push(msg);
        }
        mgntEnqStats(msg);
        QPID_LOG(debug, "Message " << msg << " enqueued on " << name << "[" << this << "]");
    }
}


void Queue::recover(boost::intrusive_ptr<Message>& msg){
    push(msg);
    msg->enqueueComplete(); // mark the message as enqueued
    mgntEnqStats(msg);

    if (store && !msg->isContentLoaded()) {
        //content has not been loaded, need to ensure that lazy loading mode is set:
        //TODO: find a nicer way to do this
        msg->releaseContent(store);
    }
}

void Queue::process(boost::intrusive_ptr<Message>& msg){
    push(msg);
    mgntEnqStats(msg);
    if (mgmtObject != 0){
        mgmtObject->inc_msgTxnEnqueues ();
        mgmtObject->inc_byteTxnEnqueues (msg->contentSize ());
    }
}

void Queue::requeue(const QueuedMessage& msg){
    if (policy.get() && !policy->isEnqueued(msg)) return;

    QueueListeners::NotificationSet copy;
    {    
        Mutex::ScopedLock locker(messageLock);
        msg.payload->enqueueComplete(); // mark the message as enqueued
        messages.push_front(msg);
        listeners.populate(copy);
    }
    copy.notify();
}

void Queue::clearLVQIndex(const QueuedMessage& msg){
    const framing::FieldTable* ft = msg.payload ? msg.payload->getApplicationHeaders() : 0;
    if (lastValueQueue && ft){
        string key = ft->getAsString(qpidVQMatchProperty);
        lvq.erase(key);
    }
}

bool Queue::acquireMessageAt(const SequenceNumber& position, QueuedMessage& message) 
{
    Mutex::ScopedLock locker(messageLock);
    QPID_LOG(debug, "Attempting to acquire message at " << position);
    for (Messages::iterator i = messages.begin(); i != messages.end(); i++) {
        if (i->position == position) {
            message = *i;
            if (lastValueQueue) {
                clearLVQIndex(*i);
            }
            messages.erase(i);
            QPID_LOG(debug, "Acquired message at " << i->position << " from " << name);
            return true;
        }
    }
    QPID_LOG(debug, "Could not acquire message at " << position << " from " << name << "; no message at that position");
    return false;
}

bool Queue::acquire(const QueuedMessage& msg) {
    Mutex::ScopedLock locker(messageLock);
    QPID_LOG(debug, "attempting to acquire " << msg.position);
    for (Messages::iterator i = messages.begin(); i != messages.end(); i++) {
        if ((i->position == msg.position && !lastValueQueue) // note that in some cases payload not be set
            || (lastValueQueue && (i->position == msg.position) && 
                msg.payload.get() == checkLvqReplace(*i).payload.get()) )  {

            clearLVQIndex(msg);
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

void Queue::notifyListener()
{
    QueueListeners::NotificationSet set;
    {
        Mutex::ScopedLock locker(messageLock);
        if (messages.size()) {
            listeners.populate(set);
        }
    }
    set.notify();
}

bool Queue::getNextMessage(QueuedMessage& m, Consumer::shared_ptr c)
{
    if (c->preAcquires()) {
        switch (consumeNextMessage(m, c)) {
          case CONSUMED:
            return true;
          case CANT_CONSUME:
            notifyListener();//let someone else try
          case NO_MESSAGES:
          default:
            return false;
        }        
    } else {
        return browseNextMessage(m, c);
    }
}

bool Queue::checkForMessages(Consumer::shared_ptr c)
{
    Mutex::ScopedLock locker(messageLock);
    if (messages.empty()) {
        //no message available, register consumer for notification
        //when this changes
        listeners.addListener(c);
        return false;
    } else {
        QueuedMessage msg = getFront();
        if (store && !msg.payload->isEnqueueComplete()) {
            //though a message is on the queue, it has not yet been
            //enqueued and so is not available for consumption yet,
            //register consumer for notification when this changes
            listeners.addListener(c);
            return false;            
        } else {
            //check that consumer has sufficient credit for the
            //message (if it does not, no need to register it for
            //notification as the consumer itself will handle the
            //credit allocation required to change this condition).
            return c->accept(msg.payload);
        }
    }
}

Queue::ConsumeCode Queue::consumeNextMessage(QueuedMessage& m, Consumer::shared_ptr c)
{
    while (true) {
        Mutex::ScopedLock locker(messageLock);
        if (messages.empty()) { 
            QPID_LOG(debug, "No messages to dispatch on queue '" << name << "'");
            listeners.addListener(c);
            return NO_MESSAGES;
        } else {
            QueuedMessage msg = getFront();
            if (msg.payload->hasExpired()) {
                QPID_LOG(debug, "Message expired from queue '" << name << "'");
                popAndDequeue();
                continue;
            }

            if (c->filter(msg.payload)) {
                if (c->accept(msg.payload)) {            
                    m = msg;
                    popMsg(msg);
                    return CONSUMED;
                } else {
                    //message(s) are available but consumer hasn't got enough credit
                    QPID_LOG(debug, "Consumer can't currently accept message from '" << name << "'");
                    return CANT_CONSUME;
                }
            } else {
                //consumer will never want this message
                QPID_LOG(debug, "Consumer doesn't want message from '" << name << "'");
                return CANT_CONSUME;
            } 
        }
    }
}


bool Queue::browseNextMessage(QueuedMessage& m, Consumer::shared_ptr c)
{
    QueuedMessage msg(this);
    while (seek(msg, c)) {
        if (c->filter(msg.payload) && !msg.payload->hasExpired()) {
            if (c->accept(msg.payload)) {
                //consumer wants the message
                c->position = msg.position;
                m = msg;
                if (!lastValueQueueNoBrowse) clearLVQIndex(msg);
                if (lastValueQueue) {
                    boost::intrusive_ptr<Message> replacement = msg.payload->getReplacementMessage(this);
                    if (replacement.get()) m.payload = replacement;
                }
                return true;
            } else {
                //browser hasn't got enough credit for the message
                QPID_LOG(debug, "Browser can't currently accept message from '" << name << "'");
                return false;
            }
        } else {
            //consumer will never want this message, continue seeking
            c->position = msg.position;
            QPID_LOG(debug, "Browser skipping message from '" << name << "'");
        }
    }
    return false;
}

void Queue::removeListener(Consumer::shared_ptr c)
{
    QueueListeners::NotificationSet set;
    {
        Mutex::ScopedLock locker(messageLock);
        listeners.removeListener(c);
        if (messages.size()) {
            listeners.populate(set);
        }
    }
    set.notify();
}

bool Queue::dispatch(Consumer::shared_ptr c)
{
    QueuedMessage msg(this);
    if (getNextMessage(msg, c)) {
        c->deliver(msg);
        return true;
    } else {
        return false;
    }
}

bool Queue::seek(QueuedMessage& msg, Consumer::shared_ptr c) {
    Mutex::ScopedLock locker(messageLock);
    if (!messages.empty() && messages.back().position > c->position) {
        if (c->position < getFront().position) {
            msg = getFront();
            return true;
        } else {        
            //TODO: can improve performance of this search, for now just searching linearly from end
            Messages::reverse_iterator pos;
            for (Messages::reverse_iterator i = messages.rbegin(); i != messages.rend() && i->position > c->position; i++) {
                pos = i;
            }
            msg = *pos;
            return true;
        }
    }
    listeners.addListener(c);
    return false;
}

namespace {
struct PositionEquals {
    SequenceNumber pos;
    PositionEquals(SequenceNumber p) : pos(p) {}
    bool operator()(const QueuedMessage& msg) const { return msg.position == pos; }
};
}// namespace

QueuedMessage Queue::find(SequenceNumber pos) const {
    Mutex::ScopedLock locker(messageLock);
    Messages::const_iterator i = std::find_if(messages.begin(), messages.end(), PositionEquals(pos));
    if (i != messages.end())
        return *i;
    return QueuedMessage();
}

void Queue::consume(Consumer::shared_ptr c, bool requestExclusive){
    Mutex::ScopedLock locker(consumerLock);
    if(exclusive) {
        throw ResourceLockedException(
            QPID_MSG("Queue " << getName() << " has an exclusive consumer. No more consumers allowed."));
    } else if(requestExclusive) {
        if(consumerCount) {
            throw ResourceLockedException(
                QPID_MSG("Queue " << getName() << " already has consumers. Exclusive access denied."));
        } else {
            exclusive = c->getSession();
        }
    }
    consumerCount++;
    if (mgmtObject != 0)
        mgmtObject->inc_consumerCount ();
}

void Queue::cancel(Consumer::shared_ptr c){
    removeListener(c);
    Mutex::ScopedLock locker(consumerLock);
    consumerCount--;
    if(exclusive) exclusive = 0;
    if (mgmtObject != 0)
        mgmtObject->dec_consumerCount ();
}

QueuedMessage Queue::get(){
    Mutex::ScopedLock locker(messageLock);
    QueuedMessage msg(this);

    if(!messages.empty()){
        msg = getFront();
        popMsg(msg);
    }
    return msg;
}

void Queue::purgeExpired()
{
    //As expired messages are discarded during dequeue also, only
    //bother explicitly expiring if the rate of dequeues since last
    //attempt is less than one per second.
    if (dequeueTracker.sampleRatePerSecond() < 1) {
        Messages expired;
        {
            Mutex::ScopedLock locker(messageLock);
            for (Messages::iterator i = messages.begin(); i != messages.end();) {
                if (lastValueQueue) checkLvqReplace(*i);
                if (i->payload->hasExpired()) {
                    expired.push_back(*i);
                    i = messages.erase(i);
                } else {
                    ++i;
                }
            }
        }
        for_each(expired.begin(), expired.end(), bind(&Queue::dequeue, this, (TransactionContext*) 0, _1));
    }
}

/**
 * purge - for purging all or some messages on a queue
 *         depending on the purge_request
 *
 * purge_request == 0 then purge all messages
 *               == N then purge N messages from queue
 * Sometimes purge_request == 1 to unblock the top of queue
 */
uint32_t Queue::purge(const uint32_t purge_request){
    Mutex::ScopedLock locker(messageLock);
    uint32_t purge_count = purge_request; // only comes into play if  >0 

    uint32_t count = 0;
    // Either purge them all or just the some (purge_count) while the queue isn't empty.
    while((!purge_request || purge_count--) && !messages.empty()) {
        popAndDequeue();
        count++;
    }
    return count;
}

uint32_t Queue::move(const Queue::shared_ptr destq, uint32_t qty) {
    Mutex::ScopedLock locker(messageLock);
    uint32_t move_count = qty; // only comes into play if  qty >0 
    uint32_t count = 0; // count how many were moved for returning

    while((!qty || move_count--) && !messages.empty()) {
        QueuedMessage qmsg = getFront();
        boost::intrusive_ptr<Message> msg = qmsg.payload;
        destq->deliver(msg); // deliver message to the destination queue
        popMsg(qmsg);
        dequeue(0, qmsg);
        count++;
    }
    return count;
}

void Queue::popMsg(QueuedMessage& qmsg)
{
    const framing::FieldTable* ft = qmsg.payload->getApplicationHeaders();
    if (lastValueQueue && ft){
        string key = ft->getAsString(qpidVQMatchProperty);
        lvq.erase(key);
    }
    messages.pop_front();
    ++dequeueTracker;
}

void Queue::push(boost::intrusive_ptr<Message>& msg){
    QueueListeners::NotificationSet copy;
    {
        Mutex::ScopedLock locker(messageLock);   
        QueuedMessage qm(this, msg, ++sequence);
        if (policy.get()) policy->tryEnqueue(qm);
         
        LVQ::iterator i;
        const framing::FieldTable* ft = msg->getApplicationHeaders();
        if (lastValueQueue && ft){
            string key = ft->getAsString(qpidVQMatchProperty);

            i = lvq.find(key);
            if (i == lvq.end()){
                messages.push_back(qm);
                listeners.populate(copy);
                lvq[key] = msg; 
            }else {
                boost::intrusive_ptr<Message> old = i->second->getReplacementMessage(this);
                if (!old) old = i->second;
                i->second->setReplacementMessage(msg,this);
                dequeued(QueuedMessage(qm.queue, old, qm.position));
            }		 
        }else {
            messages.push_back(qm);
            listeners.populate(copy);
        }
        if (eventMode && eventMgr) {
            eventMgr->enqueued(qm);
        }
    }
    copy.notify();
}

QueuedMessage Queue::getFront()
{
    QueuedMessage msg = messages.front();
    if (lastValueQueue) {
        boost::intrusive_ptr<Message> replacement = msg.payload->getReplacementMessage(this);
        if (replacement.get()) msg.payload = replacement;
    }
    return msg;
}

QueuedMessage& Queue::checkLvqReplace(QueuedMessage& msg)
{
    boost::intrusive_ptr<Message> replacement = msg.payload->getReplacementMessage(this);
    if (replacement.get()) {
        const framing::FieldTable* ft = replacement->getApplicationHeaders();
        if (ft) {
            string key = ft->getAsString(qpidVQMatchProperty);
            if (lvq.find(key) != lvq.end()){
                lvq[key] = replacement; 
            }        
        }
        msg.payload = replacement;
    }
    return msg;
}

/** function only provided for unit tests, or code not in critical message path */
uint32_t Queue::getMessageCount() const
{
    Mutex::ScopedLock locker(messageLock);
  
    uint32_t count = 0;
    for ( Messages::const_iterator i = messages.begin(); i != messages.end(); ++i ) {
        //NOTE: don't need to use checkLvqReplace() here as it
        //is only relevant for LVQ which does not support persistence
        //so the enqueueComplete check has no effect
        if ( i->payload->isEnqueueComplete() ) count ++;
    }
    
    return count;
}

uint32_t Queue::getConsumerCount() const
{
    Mutex::ScopedLock locker(consumerLock);
    return consumerCount;
}

bool Queue::canAutoDelete() const
{
    Mutex::ScopedLock locker(consumerLock);
    return autodelete && !consumerCount;
}

void Queue::clearLastNodeFailure()
{
    inLastNodeFailure = false;
}

void Queue::setLastNodeFailure()
{
    if (persistLastNode){
        Mutex::ScopedLock locker(messageLock);
    	for ( Messages::iterator i = messages.begin(); i != messages.end(); ++i ) {
            if (lastValueQueue) checkLvqReplace(*i);
            i->payload->forcePersistent();
            if (i->payload->getPersistenceId() == 0){
            	enqueue(0, i->payload);
            }
    	}
        inLastNodeFailure = true;
    }
}

// return true if store exists, 
bool Queue::enqueue(TransactionContext* ctxt, boost::intrusive_ptr<Message> msg)
{
    if (inLastNodeFailure && persistLastNode){
        msg->forcePersistent();
    }
	
    if (traceId.size()) {
        msg->addTraceId(traceId);
    }

    if (msg->isPersistent() && store && !lastValueQueue) {
        msg->enqueueAsync(shared_from_this(), store); //increment to async counter -- for message sent to more than one queue
        boost::intrusive_ptr<PersistableMessage> pmsg = boost::static_pointer_cast<PersistableMessage>(msg);
        store->enqueue(ctxt, pmsg, *this);
        return true;
    }
    return false;
}

// return true if store exists, 
bool Queue::dequeue(TransactionContext* ctxt, const QueuedMessage& msg)
{
    if (policy.get() && !policy->isEnqueued(msg)) return false;
    {
        Mutex::ScopedLock locker(messageLock);
        if (!ctxt) { 
            dequeued(msg);
        }
    }
    if (msg.payload->isPersistent() && store && !lastValueQueue) {
        msg.payload->dequeueAsync(shared_from_this(), store); //increment to async counter -- for message sent to more than one queue
        boost::intrusive_ptr<PersistableMessage> pmsg = boost::static_pointer_cast<PersistableMessage>(msg.payload);
        store->dequeue(ctxt, pmsg, *this);
        return true;
    }
    return false;
}

void Queue::dequeueCommitted(const QueuedMessage& msg)
{
    Mutex::ScopedLock locker(messageLock);
    dequeued(msg);    
    if (mgmtObject != 0) {
        mgmtObject->inc_msgTxnDequeues();
        mgmtObject->inc_byteTxnDequeues(msg.payload->contentSize());
    }
}

/**
 * Removes a message from the in-memory delivery queue as well
 * dequeing it from the logical (and persistent if applicable) queue
 */
void Queue::popAndDequeue()
{
    QueuedMessage msg = getFront();
    popMsg(msg);
    dequeue(0, msg);
}

/**
 * Updates policy and management when a message has been dequeued,
 * expects messageLock to be held
 */
void Queue::dequeued(const QueuedMessage& msg)
{
    if (policy.get()) policy->dequeued(msg);
    mgntDeqStats(msg.payload);
    if (eventMode == ENQUEUE_AND_DEQUEUE && eventMgr) {
        eventMgr->dequeued(msg);
    }
}


void Queue::create(const FieldTable& _settings)
{
    settings = _settings;
    if (store) {
        store->create(*this, _settings);
    }
    configure(_settings);
}

void Queue::configure(const FieldTable& _settings, bool recovering)
{
    setPolicy(QueuePolicy::createQueuePolicy(_settings));
    //set this regardless of owner to allow use of no-local with exclusive consumers also
    noLocal = _settings.get(qpidNoLocal);
    QPID_LOG(debug, "Configured queue with no-local=" << noLocal);

    lastValueQueue= _settings.get(qpidLastValueQueue);
    if (lastValueQueue) QPID_LOG(debug, "Configured queue as Last Value Queue");

    lastValueQueueNoBrowse = _settings.get(qpidLastValueQueueNoBrowse);
    if (lastValueQueueNoBrowse){
        QPID_LOG(debug, "Configured queue as Last Value Queue No Browse");
        lastValueQueue = lastValueQueueNoBrowse;
    }
    
    persistLastNode= _settings.get(qpidPersistLastNode);
    if (persistLastNode) QPID_LOG(debug, "Configured queue to Persist data if cluster fails to one node");

    traceId = _settings.getAsString(qpidTraceIdentity);
    std::string excludeList = _settings.getAsString(qpidTraceExclude);
    if (excludeList.size()) {
        split(traceExclude, excludeList, ", ");
    }
    QPID_LOG(debug, "Configured queue " << getName() << " with qpid.trace.id='" << traceId 
             << "' and qpid.trace.exclude='"<< excludeList << "' i.e. " << traceExclude.size() << " elements");

    eventMode = _settings.getAsInt(qpidQueueEventGeneration);

    if (mgmtObject != 0)
        mgmtObject->set_arguments (_settings);

    if ( isDurable() && ! getPersistenceId() && ! recovering )
      store->create(*this, _settings);
}

void Queue::destroy()
{
    if (alternateExchange.get()) {
        Mutex::ScopedLock locker(messageLock);
        while(!messages.empty()){
            DeliverableMessage msg(getFront().payload);
            alternateExchange->route(msg, msg.getMessage().getRoutingKey(),
                                     msg.getMessage().getApplicationHeaders());
            popAndDequeue();
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
        ManagementAgent* agent = ManagementAgent::Singleton::getInstance();
        agent->addObject (mgmtObject, 0x3000000000000000LL + _persistenceId);

        if (externalQueueStore) {
            ManagementObject* childObj = externalQueueStore->GetManagementObject();
            if (childObj != 0)
                childObj->setReference(mgmtObject->getObjectId());
        }
    }
    persistenceId = _persistenceId;
}

void Queue::encode(Buffer& buffer) const 
{
    buffer.putShortString(name);
    buffer.put(settings);
    if (policy.get()) { 
        buffer.put(*policy);
    }
}

uint32_t Queue::encodedSize() const
{
    return name.size() + 1/*short string size octet*/ + settings.encodedSize() 
        + (policy.get() ? (*policy).encodedSize() : 0);
}

Queue::shared_ptr Queue::decode ( QueueRegistry& queues, Buffer& buffer, bool recovering )
{
    string name;
    buffer.getShortString(name);
    std::pair<Queue::shared_ptr, bool> result = queues.declare(name, true);
    buffer.get(result.first->settings);
    result.first->configure(result.first->settings, recovering );
    if (result.first->policy.get() && buffer.available() >= result.first->policy->encodedSize()) {
        buffer.get ( *(result.first->policy) );
    }

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

bool Queue::releaseMessageContent(const QueuedMessage& m)
{
    if (store && !NullMessageStore::isNullStore(store)) {
        QPID_LOG(debug, "Message " << m.position << " on " << name << " released from memory");
        m.payload->releaseContent(store);
        return true;
    } else {
        QPID_LOG(warning, "Message " << m.position << " on " << name
                 << " cannot be released from memory as the queue is not durable");
        return false;
    }    
}

ManagementObject* Queue::GetManagementObject (void) const
{
    return (ManagementObject*) mgmtObject;
}

Manageable::status_t Queue::ManagementMethod (uint32_t methodId, Args& args, string&)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;

    QPID_LOG (debug, "Queue::ManagementMethod [id=" << methodId << "]");

    switch (methodId)
    {
      case _qmf::Queue::METHOD_PURGE :
        _qmf::ArgsQueuePurge& iargs = (_qmf::ArgsQueuePurge&) args;
        purge (iargs.i_request);
        status = Manageable::STATUS_OK;
        break;
    }

    return status;
}

void Queue::setPosition(SequenceNumber n) {
    Mutex::ScopedLock locker(messageLock);
    sequence = n;
}

int Queue::getEventMode() { return eventMode; }

void Queue::setQueueEventManager(QueueEvents& mgr)
{
    eventMgr = &mgr;
}
