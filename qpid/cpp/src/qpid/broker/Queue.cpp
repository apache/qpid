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

#include "qpid/broker/Broker.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueEvents.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/Fairshare.h"
#include "qpid/broker/DeliverableMessage.h"
#include "qpid/broker/LegacyLVQ.h"
#include "qpid/broker/MessageDeque.h"
#include "qpid/broker/MessageMap.h"
#include "qpid/broker/MessageStore.h"
#include "qpid/broker/NullMessageStore.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/QueueFlowLimit.h"
#include "qpid/broker/ThresholdAlerts.h"

#include "qpid/StringUtils.h"
#include "qpid/log/Statement.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/sys/ClusterSafe.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Time.h"
#include "qpid/types/Variant.h"
#include "qmf/org/apache/qpid/broker/ArgsQueuePurge.h"
#include "qmf/org/apache/qpid/broker/ArgsQueueReroute.h"

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
namespace _qmf = qmf::org::apache::qpid::broker;


namespace
{
const std::string qpidMaxSize("qpid.max_size");
const std::string qpidMaxCount("qpid.max_count");
const std::string qpidNoLocal("no-local");
const std::string qpidTraceIdentity("qpid.trace.id");
const std::string qpidTraceExclude("qpid.trace.exclude");
const std::string qpidLastValueQueueKey("qpid.last_value_queue_key");
const std::string qpidLastValueQueue("qpid.last_value_queue");
const std::string qpidLastValueQueueNoBrowse("qpid.last_value_queue_no_browse");
const std::string qpidPersistLastNode("qpid.persist_last_node");
const std::string qpidVQMatchProperty("qpid.LVQ_key");
const std::string qpidQueueEventGeneration("qpid.queue_event_generation");
const std::string qpidAutoDeleteTimeout("qpid.auto_delete_timeout");
//following feature is not ready for general use as it doesn't handle
//the case where a message is enqueued on more than one queue well enough:
const std::string qpidInsertSequenceNumbers("qpid.insert_sequence_numbers");

const int ENQUEUE_ONLY=1;
const int ENQUEUE_AND_DEQUEUE=2;
}


// KAG TBD: find me a home....
namespace qpid {
namespace broker {

class MessageAllocator
{
 protected:
    Queue *queue;
 public:
    MessageAllocator( Queue *q ) : queue(q) {}
    virtual ~MessageAllocator() {};

    // assumes caller holds messageLock
    virtual bool nextMessage( Consumer::shared_ptr c, QueuedMessage& next,
                              const Mutex::ScopedLock&);
    /** acquire a message previously browsed via nextMessage().  assume messageLock held
     * @param consumer name of consumer that is attempting to acquire the message
     * @param qm the message to be acquired
     * @param messageLock - ensures caller is holding it!
     * @returns true if acquire is successful, false if acquire failed.
     */
    virtual bool canAcquire( const std::string& consumer, const QueuedMessage& qm,
                             const Mutex::ScopedLock&);

    /** hook to add any interesting management state to the status map (lock held) */
    virtual void query(qpid::types::Variant::Map&, const Mutex::ScopedLock&) const {};

    /** for move, purge, reroute - check if message matches against a filter,
     * return true if message matches.
     */
     virtual bool match(const qpid::types::Variant::Map* filter,
                        const QueuedMessage& message) const;
};



class MessageGroupManager : public QueueObserver, public MessageAllocator
{
    const std::string groupIdHeader;    // msg header holding group identifier
    const unsigned int timestamp;       // mark messages with timestamp if set

    struct GroupState {
        //const std::string group;          // group identifier
        //Consumer::shared_ptr owner; // consumer with outstanding acquired messages
        std::string owner; // consumer with outstanding acquired messages
        uint32_t acquired;  // count of outstanding acquired messages
        uint32_t total; // count of enqueued messages in this group
        GroupState() : acquired(0), total(0) {}
    };
    typedef std::map<std::string, struct GroupState> GroupMap;
    typedef std::set<std::string> Consumers;

    GroupMap messageGroups;
    Consumers consumers;

    static const std::string qpidMessageGroupKey;
    static const std::string qpidMessageGroupTimestamp;
    static const std::string qpidMessageGroupDefault;
    static const std::string qpidMessageGroupFilter;    // key for move/purge filter map

    const std::string getGroupId( const QueuedMessage& qm ) const;

 public:

    static boost::shared_ptr<MessageGroupManager> create( Queue *q, const qpid::framing::FieldTable& settings );

    MessageGroupManager(const std::string& header, Queue *q, unsigned int _timestamp=0 )
        : QueueObserver(), MessageAllocator(q), groupIdHeader( header ), timestamp(_timestamp) {}
    void enqueued( const QueuedMessage& qm );
    void acquired( const QueuedMessage& qm );
    void requeued( const QueuedMessage& qm );
    void dequeued( const QueuedMessage& qm );
    void consumerAdded( const Consumer& );
    void consumerRemoved( const Consumer& );
    bool nextMessage( Consumer::shared_ptr c, QueuedMessage& next,
                      const Mutex::ScopedLock&);
    bool canAcquire(const std::string& consumer, const QueuedMessage& msg,
                    const Mutex::ScopedLock&);
    void query(qpid::types::Variant::Map&, const Mutex::ScopedLock&) const;
    bool match(const qpid::types::Variant::Map*, const QueuedMessage&) const;
};

const std::string MessageGroupManager::qpidMessageGroupKey("qpid.group_header_key");
const std::string MessageGroupManager::qpidMessageGroupTimestamp("qpid.group_timestamp");
const std::string MessageGroupManager::qpidMessageGroupDefault("qpid.no_group");     /** @todo KAG: make configurable in Broker options */
const std::string MessageGroupManager::qpidMessageGroupFilter("qpid.group_id");

}}

Queue::Queue(const string& _name, bool _autodelete,
             MessageStore* const _store,
             const OwnershipToken* const _owner,
             Manageable* parent,
             Broker* b) :

    name(_name),
    autodelete(_autodelete),
    store(_store),
    owner(_owner),
    consumerCount(0),
    exclusive(0),
    noLocal(false),
    persistLastNode(false),
    inLastNodeFailure(false),
    messages(new MessageDeque()),
    persistenceId(0),
    policyExceeded(false),
    mgmtObject(0),
    eventMode(0),
    insertSeqNo(0),
    broker(b),
    deleted(false),
    barrier(*this),
    autoDeleteTimeout(0),
    allocator(new MessageAllocator( this )),
    type(FIFO)
{
    if (parent != 0 && broker != 0) {
        ManagementAgent* agent = broker->getManagementAgent();

        if (agent != 0) {
            mgmtObject = new _qmf::Queue(agent, this, parent, _name, _store != 0, _autodelete, _owner != 0);
            agent->addObject(mgmtObject, 0, store != 0);
        }
    }
}

Queue::~Queue()
{
    if (mgmtObject != 0)
        mgmtObject->resourceDestroy();
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

void Queue::deliver(boost::intrusive_ptr<Message> msg){
    // Check for deferred delivery in a cluster.
    if (broker && broker->deferDelivery(name, msg))
        return;
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
        enqueue(0, msg);
        push(msg);
        QPID_LOG(debug, "Message " << msg << " enqueued on " << name);
    }
}

void Queue::recoverPrepared(boost::intrusive_ptr<Message>& msg)
{
    if (policy.get()) policy->recoverEnqueued(msg);
}

void Queue::recover(boost::intrusive_ptr<Message>& msg){
    if (policy.get()) policy->recoverEnqueued(msg);

    push(msg, true);
    if (store){
        // setup synclist for recovered messages, so they don't get re-stored on lastNodeFailure
        msg->addToSyncList(shared_from_this(), store);
    }

    if (store && (!msg->isContentLoaded() || msg->checkContentReleasable())) {
        //content has not been loaded, need to ensure that lazy loading mode is set:
        //TODO: find a nicer way to do this
        msg->releaseContent(store);
        // NOTE: The log message in this section are used for flow-to-disk testing (which checks the log for the
        // presence of this message). Do not change this without also checking these tests.
        QPID_LOG(debug, "Message id=\"" << msg->getProperties<MessageProperties>()->getMessageId() << "\"; pid=0x" <<
                        std::hex << msg->getPersistenceId() << std::dec << ": Content released after recovery");
    }
}

void Queue::process(boost::intrusive_ptr<Message>& msg){
    push(msg);
    if (mgmtObject != 0){
        mgmtObject->inc_msgTxnEnqueues ();
        mgmtObject->inc_byteTxnEnqueues (msg->contentSize ());
    }
}

void Queue::requeue(const QueuedMessage& msg){
    assertClusterSafe();
    QueueListeners::NotificationSet copy;
    {
        Mutex::ScopedLock locker(messageLock);
        if (!isEnqueued(msg)) return;
        messages->reinsert(msg);
        listeners.populate(copy);

        // for persistLastNode - don't force a message twice to disk, but force it if no force before
        if(inLastNodeFailure && persistLastNode && !msg.payload->isStoredOnQueue(shared_from_this())) {
            msg.payload->forcePersistent();
            if (msg.payload->isForcedPersistent() ){
                boost::intrusive_ptr<Message> payload = msg.payload;
            	enqueue(0, payload);
            }
        }

        for (Observers::const_iterator i = observers.begin(); i != observers.end(); ++i) {
            try{
                (*i)->requeued(msg);
            } catch (const std::exception& e) {
                QPID_LOG(warning, "Exception on notification of message requeue for queue " << getName() << ": " << e.what());
            }
        }
    }
    copy.notify();
}

bool Queue::acquireMessageAt(const SequenceNumber& position, QueuedMessage& message)
{
    Mutex::ScopedLock locker(messageLock);
    assertClusterSafe();
    QPID_LOG(debug, "Attempting to acquire message at " << position);
    if (acquire(position, message )) {
        QPID_LOG(debug, "Acquired message at " << position << " from " << name);
        return true;
    } else {
        QPID_LOG(debug, "Could not acquire message at " << position << " from " << name << "; no message at that position");
        return false;
    }
}

bool Queue::acquire(const QueuedMessage& msg, const std::string& consumer)
{
    Mutex::ScopedLock locker(messageLock);
    assertClusterSafe();
    QPID_LOG(debug, consumer << " attempting to acquire message at " << msg.position);

    if (!allocator->canAcquire( consumer, msg, locker )) {
        QPID_LOG(debug, "Not permitted to acquire msg at " << msg.position << " from '" << name);
        return false;
    }

    QueuedMessage copy(msg);
    if (acquire( msg.position, copy )) {
        QPID_LOG(debug, "Acquired message at " << msg.position << " from " << name);
        return true;
    }
    QPID_LOG(debug, "Could not acquire message at " << msg.position << " from " << name << "; no message at that position");
    return false;
}

void Queue::notifyListener()
{
    assertClusterSafe();
    QueueListeners::NotificationSet set;
    {
        Mutex::ScopedLock locker(messageLock);
        if (messages->size()) {
            listeners.populate(set);
        }
    }
    set.notify();
}

bool Queue::getNextMessage(QueuedMessage& m, Consumer::shared_ptr c)
{
    checkNotDeleted();
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

Queue::ConsumeCode Queue::consumeNextMessage(QueuedMessage& m, Consumer::shared_ptr c)
{

    while (true) {
        Mutex::ScopedLock locker(messageLock);
        QueuedMessage msg;

        if (!allocator->nextMessage(c, msg, locker)) { // no next available
            QPID_LOG(debug, "No messages available to dispatch to consumer " <<
                     c->getName() << " on queue '" << name << "'");
            listeners.addListener(c);
            return NO_MESSAGES;
        }

        if (msg.payload->hasExpired()) {
            QPID_LOG(debug, "Message expired from queue '" << name << "'");
            c->position = msg.position;
            acquire( msg.position, msg );
            dequeue( 0, msg );
            continue;
        }

        // a message is available for this consumer - can the consumer use it?

        if (c->filter(msg.payload)) {
            if (c->accept(msg.payload)) {
                bool ok = acquire( msg.position, msg );
                (void) ok; assert(ok);
                m = msg;
                c->position = m.position;
                return CONSUMED;
            } else {
                //message(s) are available but consumer hasn't got enough credit
                QPID_LOG(debug, "Consumer can't currently accept message from '" << name << "'");
                return CANT_CONSUME;
            }
        } else {
            //consumer will never want this message
            QPID_LOG(debug, "Consumer doesn't want message from '" << name << "'");
            c->position = msg.position;
            return CANT_CONSUME;
        }
    }
}

bool Queue::browseNextMessage(QueuedMessage& m, Consumer::shared_ptr c)
{
    while (true) {
        Mutex::ScopedLock locker(messageLock);
        QueuedMessage msg;

        if (!allocator->nextMessage(c, msg, locker)) { // no next available
            QPID_LOG(debug, "No browsable messages available for consumer " <<
                     c->getName() << " on queue '" << name << "'");
            listeners.addListener(c);
            return false;
        }

        if (c->filter(msg.payload) && !msg.payload->hasExpired()) {
            if (c->accept(msg.payload)) {
                //consumer wants the message
                c->position = msg.position;
                m = msg;
                return true;
            } else {
                //browser hasn't got enough credit for the message
                QPID_LOG(debug, "Browser can't currently accept message from '" << name << "'");
                return false;
            }
        } else {
            //consumer will never want this message, continue seeking
            QPID_LOG(debug, "Browser skipping message from '" << name << "'");
            c->position = msg.position;
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
        if (messages->size()) {
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

bool Queue::find(SequenceNumber pos, QueuedMessage& msg) const {

    Mutex::ScopedLock locker(messageLock);
    if (messages->find(pos, msg))
        return true;
    return false;
}

void Queue::consume(Consumer::shared_ptr c, bool requestExclusive){
    assertClusterSafe();
    {
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
        //reset auto deletion timer if necessary
        if (autoDeleteTimeout && autoDeleteTask) {
            autoDeleteTask->cancel();
        }
    }
    for (Observers::const_iterator i = observers.begin(); i != observers.end(); ++i) {
        try{
            Mutex::ScopedLock locker(messageLock);
            (*i)->consumerAdded(*c);
        } catch (const std::exception& e) {
            QPID_LOG(warning, "Exception on notification of new consumer for queue " << getName() << ": " << e.what());
        }
    }
}

void Queue::cancel(Consumer::shared_ptr c){
    removeListener(c);
    {
        Mutex::ScopedLock locker(consumerLock);
        consumerCount--;
        if(exclusive) exclusive = 0;
        if (mgmtObject != 0)
            mgmtObject->dec_consumerCount ();
    }
    for (Observers::const_iterator i = observers.begin(); i != observers.end(); ++i) {
        try{
            Mutex::ScopedLock locker(messageLock);
            (*i)->consumerRemoved(*c);
        } catch (const std::exception& e) {
            QPID_LOG(warning, "Exception on notification of removed consumer for queue " << getName() << ": " << e.what());
        }
    }
}

QueuedMessage Queue::get(){
    Mutex::ScopedLock locker(messageLock);
    QueuedMessage msg(this);
    if (messages->pop(msg))
        acquired( msg );
    return msg;
}

bool collect_if_expired(std::deque<QueuedMessage>& expired, QueuedMessage& message)
{
    if (message.payload->hasExpired()) {
        expired.push_back(message);
        return true;
    } else {
        return false;
    }
}

/**
 *@param lapse: time since the last purgeExpired
 */
void Queue::purgeExpired(qpid::sys::Duration lapse)
{
    //As expired messages are discarded during dequeue also, only
    //bother explicitly expiring if the rate of dequeues since last
    //attempt is less than one per second.
    int count = dequeueSincePurge.get();
    dequeueSincePurge -= count;
    int seconds = int64_t(lapse)/qpid::sys::TIME_SEC;
    if (seconds == 0 || count / seconds < 1) {
        std::deque<QueuedMessage> expired;
        {
            Mutex::ScopedLock locker(messageLock);
            messages->removeIf(boost::bind(&collect_if_expired, boost::ref(expired), _1));
        }

        for (std::deque<QueuedMessage>::const_iterator i = expired.begin();
             i != expired.end(); ++i) {
            {
                Mutex::ScopedLock locker(messageLock);
                acquired( *i );   // expects messageLock held
            }
            dequeue( 0, *i );
        }
    }
}


namespace {
    // for use with purge/move below - collect messages that match a given filter
    struct Collector {
        const uint32_t maxMatches;
        const qpid::types::Variant::Map *filter;
        std::deque<QueuedMessage> matches;
        boost::shared_ptr<MessageAllocator> allocator;
        Collector(boost::shared_ptr<MessageAllocator> a, uint32_t m,
                  const qpid::types::Variant::Map *f)
            : maxMatches(m), filter(f), allocator(a) {}
        void operator() (QueuedMessage& qm)
        {
            if (maxMatches == 0 || matches.size() < maxMatches) {
                if (allocator->match( filter, qm )) {
                    matches.push_back(qm);
                }
            }
        }
    };
}


/**
 * purge - for purging all or some messages on a queue
 *         depending on the purge_request
 *
 * purge_request == 0 then purge all messages
 *               == N then purge N messages from queue
 * Sometimes purge_request == 1 to unblock the top of queue
 *
 * The dest exchange may be supplied to re-route messages through the exchange.
 * It is safe to re-route messages such that they arrive back on the same queue,
 * even if the queue is ordered by priority.
 *
 * An optional filter can be supplied that will be applied against each message.  The
 * message is purged only if the filter matches.  See MessageAllocator for more detail.
 */
uint32_t Queue::purge(const uint32_t purge_request, boost::shared_ptr<Exchange> dest,
                      const qpid::types::Variant::Map *filter)
{
    Collector c(allocator, purge_request, filter);

    Mutex::ScopedLock locker(messageLock);
    messages->foreach( boost::bind<void>(boost::ref(c), _1) );

    uint32_t count = c.matches.size();

    // first remove all matches
    for (std::deque<QueuedMessage>::iterator qmsg = c.matches.begin();
         qmsg != c.matches.end(); qmsg++) {
        /** @todo KAG: need a direct remove method here */
        bool ok = acquire(qmsg->position, *qmsg);
        (void) ok; assert(ok);
        dequeue(0, *qmsg);
    }

    // now reroute if necessary
    if (dest.get()) {
        while (!c.matches.empty()) {
            QueuedMessage msg = c.matches.front();
            c.matches.pop_front();
            assert(msg.payload);
            DeliverableMessage dmsg(msg.payload);
            dest->routeWithAlternate(dmsg);
        }
    }
    return count;
}

uint32_t Queue::move(const Queue::shared_ptr destq, uint32_t qty,
                     const qpid::types::Variant::Map *filter)
{
    Collector c(allocator, qty, filter);

    Mutex::ScopedLock locker(messageLock);
    messages->foreach( boost::bind<void>(boost::ref(c), _1) );

    uint32_t count = c.matches.size();

    while (!c.matches.empty()) {
        QueuedMessage qmsg = c.matches.front();
        c.matches.pop_front();
        /** @todo KAG: need a direct remove method here */
        bool ok = acquire(qmsg.position, qmsg);
        (void) ok; assert(ok);
        dequeue(0, qmsg);
        assert(qmsg.payload);
        destq->deliver(qmsg.payload);
    }
    return count;
}

/** Acquire the front (oldest) message from the in-memory queue.
 * assumes messageLock held by caller
 */
void Queue::pop()
{
    assertClusterSafe();
    QueuedMessage msg;
    if (messages->pop(msg)) {
        acquired( msg ); // mark it removed
        ++dequeueSincePurge;
    }
}

/** Acquire the message at the given position, return true and msg if acquire succeeds */
bool Queue::acquire(const qpid::framing::SequenceNumber& position, QueuedMessage& msg )
{
    if (messages->remove(position, msg)) {
        acquired( msg );
        ++dequeueSincePurge;
        return true;
    }
    return false;
}

void Queue::push(boost::intrusive_ptr<Message>& msg, bool isRecovery){
    assertClusterSafe();
    QueueListeners::NotificationSet copy;
    QueuedMessage removed;
    bool dequeueRequired = false;
    {
        Mutex::ScopedLock locker(messageLock);
        QueuedMessage qm(this, msg, ++sequence);
        if (insertSeqNo) msg->getOrInsertHeaders().setInt64(seqNoKey, sequence);

        dequeueRequired = messages->push(qm, removed);
        listeners.populate(copy);
        enqueued(qm);
    }
    copy.notify();
    if (dequeueRequired) {
        acquired( removed );  // tell observers
        if (isRecovery) {
            //can't issue new requests for the store until
            //recovery is complete
            pendingDequeues.push_back(removed);
        } else {
            dequeue(0, removed);
        }
    }
}

void isEnqueueComplete(uint32_t* result, const QueuedMessage& message)
{
    if (message.payload->isIngressComplete()) (*result)++;
}

/** function only provided for unit tests, or code not in critical message path */
uint32_t Queue::getEnqueueCompleteMessageCount() const
{
    Mutex::ScopedLock locker(messageLock);
    uint32_t count = 0;
    messages->foreach(boost::bind(&isEnqueueComplete, &count, _1));
    return count;
}

uint32_t Queue::getMessageCount() const
{
    Mutex::ScopedLock locker(messageLock);
    return messages->size();
}

uint32_t Queue::getConsumerCount() const
{
    Mutex::ScopedLock locker(consumerLock);
    return consumerCount;
}

bool Queue::canAutoDelete() const
{
    Mutex::ScopedLock locker(consumerLock);
    return autodelete && !consumerCount && !owner;
}

void Queue::clearLastNodeFailure()
{
    inLastNodeFailure = false;
}

void Queue::forcePersistent(QueuedMessage& message)
{
    if(!message.payload->isStoredOnQueue(shared_from_this())) {
        message.payload->forcePersistent();
        if (message.payload->isForcedPersistent() ){
            enqueue(0, message.payload);
        }
    }
}

void Queue::setLastNodeFailure()
{
    if (persistLastNode){
        Mutex::ScopedLock locker(messageLock);
        try {
            messages->foreach(boost::bind(&Queue::forcePersistent, this, _1));
        } catch (const std::exception& e) {
            // Could not go into last node standing (for example journal not large enough)
            QPID_LOG(error, "Unable to fail to last node standing for queue: " << name << " : " << e.what());
        }
        inLastNodeFailure = true;
    }
}


// return true if store exists,
bool Queue::enqueue(TransactionContext* ctxt, boost::intrusive_ptr<Message>& msg, bool suppressPolicyCheck)
{
    ScopedUse u(barrier);
    if (!u.acquired) return false;

    if (policy.get() && !suppressPolicyCheck) {
        std::deque<QueuedMessage> dequeues;
        {
            Mutex::ScopedLock locker(messageLock);
            policy->tryEnqueue(msg);
            policy->getPendingDequeues(dequeues);
        }
        //depending on policy, may have some dequeues that need to performed without holding the lock
        for_each(dequeues.begin(), dequeues.end(), boost::bind(&Queue::dequeue, this, (TransactionContext*) 0, _1));
    }

    if (inLastNodeFailure && persistLastNode){
        msg->forcePersistent();
    }

    if (traceId.size()) {
        //copy on write: take deep copy of message before modifying it
        //as the frames may already be available for delivery on other
        //threads
        boost::intrusive_ptr<Message> copy(new Message(*msg));
        msg = copy;
        msg->addTraceId(traceId);
    }

    if ((msg->isPersistent() || msg->checkContentReleasable()) && store) {
        // mark the message as being enqueued - the store MUST CALL msg->enqueueComplete()
        // when it considers the message stored.
        msg->enqueueAsync(shared_from_this(), store);
        boost::intrusive_ptr<PersistableMessage> pmsg = boost::static_pointer_cast<PersistableMessage>(msg);
        store->enqueue(ctxt, pmsg, *this);
        return true;
    }
    if (!store) {
        //Messages enqueued on a transient queue should be prevented
        //from having their content released as it may not be
        //recoverable by these queue for delivery
        msg->blockContentRelease();
    }
    return false;
}

void Queue::enqueueAborted(boost::intrusive_ptr<Message> msg)
{
    Mutex::ScopedLock locker(messageLock);
    if (policy.get()) policy->enqueueAborted(msg);
}

// return true if store exists,
bool Queue::dequeue(TransactionContext* ctxt, const QueuedMessage& msg)
{
    ScopedUse u(barrier);
    if (!u.acquired) return false;

    {
        Mutex::ScopedLock locker(messageLock);
        if (!isEnqueued(msg)) return false;
        if (!ctxt) {
            dequeued(msg);
        }
    }
    // This check prevents messages which have been forced persistent on one queue from dequeuing
    // from another on which no forcing has taken place and thus causing a store error.
    bool fp = msg.payload->isForcedPersistent();
    if (!fp || (fp && msg.payload->isStoredOnQueue(shared_from_this()))) {
        if ((msg.payload->isPersistent() || msg.payload->checkContentReleasable()) && store) {
            msg.payload->dequeueAsync(shared_from_this(), store); //increment to async counter -- for message sent to more than one queue
            boost::intrusive_ptr<PersistableMessage> pmsg = boost::static_pointer_cast<PersistableMessage>(msg.payload);
            store->dequeue(ctxt, pmsg, *this);
            return true;
        }
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
 * Removes the first (oldest) message from the in-memory delivery queue as well dequeing
 * it from the logical (and persistent if applicable) queue
 */
void Queue::popAndDequeue()
{
    if (!messages->empty()) {
        QueuedMessage msg = messages->front();
        pop();
        dequeue(0, msg);
    }
}

/**
 * Updates policy and management when a message has been dequeued,
 * expects messageLock to be held
 */
void Queue::dequeued(const QueuedMessage& msg)
{
    if (policy.get()) policy->dequeued(msg);
    mgntDeqStats(msg.payload);
    for (Observers::const_iterator i = observers.begin(); i != observers.end(); ++i) {
        try{
            (*i)->dequeued(msg);
        } catch (const std::exception& e) {
            QPID_LOG(warning, "Exception on notification of dequeue for queue " << getName() << ": " << e.what());
        }
    }
}

/** updates queue observers when a message has become unavailable for transfer,
 * expects messageLock to be held
 */
void Queue::acquired(const QueuedMessage& msg)
{
    for (Observers::const_iterator i = observers.begin(); i != observers.end(); ++i) {
        try{
            (*i)->acquired(msg);
        } catch (const std::exception& e) {
            QPID_LOG(warning, "Exception on notification of message removal for queue " << getName() << ": " << e.what());
        }
    }
}


void Queue::create(const FieldTable& _settings)
{
    settings = _settings;
    if (store) {
        store->create(*this, _settings);
    }
    configureImpl(_settings);
}


int getIntegerSetting(const qpid::framing::FieldTable& settings, const std::string& key)
{
    qpid::framing::FieldTable::ValuePtr v = settings.get(key);
    if (!v) {
        return 0;
    } else if (v->convertsTo<int>()) {
        return v->get<int>();
    } else if (v->convertsTo<std::string>()){
        std::string s = v->get<std::string>();
        try {
            return boost::lexical_cast<int>(s);
        } catch(const boost::bad_lexical_cast&) {
            QPID_LOG(warning, "Ignoring invalid integer value for " << key << ": " << s);
            return 0;
        }
    } else {
        QPID_LOG(warning, "Ignoring invalid integer value for " << key << ": " << *v);
        return 0;
    }
}

void Queue::configure(const FieldTable& _settings)
{
    settings = _settings;
    configureImpl(settings);
}

void Queue::configureImpl(const FieldTable& _settings)
{
    eventMode = _settings.getAsInt(qpidQueueEventGeneration);
    if (eventMode && broker) {
        broker->getQueueEvents().observe(*this, eventMode == ENQUEUE_ONLY);
    }

    if (QueuePolicy::getType(_settings) == QueuePolicy::FLOW_TO_DISK &&
        (!store || NullMessageStore::isNullStore(store) || (broker && !(broker->getQueueEvents().isSync())) )) {
        if ( NullMessageStore::isNullStore(store)) {
            QPID_LOG(warning, "Flow to disk not valid for non-persisted queue:" << getName());
        } else if (broker && !(broker->getQueueEvents().isSync()) ) {
            QPID_LOG(warning, "Flow to disk not valid with async Queue Events:" << getName());
        }
        FieldTable copy(_settings);
        copy.erase(QueuePolicy::typeKey);
        setPolicy(QueuePolicy::createQueuePolicy(getName(), copy));
    } else {
        setPolicy(QueuePolicy::createQueuePolicy(getName(), _settings));
    }
    if (broker && broker->getManagementAgent()) {
        ThresholdAlerts::observe(*this, *(broker->getManagementAgent()), _settings, broker->getOptions().queueThresholdEventRatio);
    }

    //set this regardless of owner to allow use of no-local with exclusive consumers also
    noLocal = _settings.get(qpidNoLocal);
    QPID_LOG(debug, "Configured queue " << getName() << " with no-local=" << noLocal);

    std::string lvqKey = _settings.getAsString(qpidLastValueQueueKey);
    if (lvqKey.size()) {
        QPID_LOG(debug, "Configured queue " <<  getName() << " as Last Value Queue with key " << lvqKey);
        messages = std::auto_ptr<Messages>(new MessageMap(lvqKey));
        type = LVQ;
    } else if (_settings.get(qpidLastValueQueueNoBrowse)) {
        QPID_LOG(debug, "Configured queue " <<  getName() << " as Legacy Last Value Queue with 'no-browse' on");
        messages = LegacyLVQ::updateOrReplace(messages, qpidVQMatchProperty, true, broker);
        type = LVQ;
    } else if (_settings.get(qpidLastValueQueue)) {
        QPID_LOG(debug, "Configured queue " <<  getName() << " as Legacy Last Value Queue");
        messages = LegacyLVQ::updateOrReplace(messages, qpidVQMatchProperty, false, broker);
        type = LVQ;
    } else {
        std::auto_ptr<Messages> m = Fairshare::create(_settings);
        if (m.get()) {
            messages = m;
            QPID_LOG(debug, "Configured queue " <<  getName() << " as priority queue.");
            type = PRIORITY;
        }
    }

    {   // override default message allocator if message groups configured.
        boost::shared_ptr<MessageAllocator> ma = boost::static_pointer_cast<MessageAllocator>(MessageGroupManager::create( this, _settings ));
        if (ma) {
            allocator = ma;
            type = GROUP;
        }
    }

    persistLastNode= _settings.get(qpidPersistLastNode);
    if (persistLastNode) QPID_LOG(debug, "Configured queue to Persist data if cluster fails to one node for: " << getName());

    traceId = _settings.getAsString(qpidTraceIdentity);
    std::string excludeList = _settings.getAsString(qpidTraceExclude);
    if (excludeList.size()) {
        split(traceExclude, excludeList, ", ");
    }
    QPID_LOG(debug, "Configured queue " << getName() << " with qpid.trace.id='" << traceId
             << "' and qpid.trace.exclude='"<< excludeList << "' i.e. " << traceExclude.size() << " elements");

    FieldTable::ValuePtr p =_settings.get(qpidInsertSequenceNumbers);
    if (p && p->convertsTo<std::string>()) insertSequenceNumbers(p->get<std::string>());

    autoDeleteTimeout = getIntegerSetting(_settings, qpidAutoDeleteTimeout);
    if (autoDeleteTimeout)
        QPID_LOG(debug, "Configured queue " << getName() << " with qpid.auto_delete_timeout=" << autoDeleteTimeout);

    if (mgmtObject != 0) {
        mgmtObject->set_arguments(ManagementAgent::toMap(_settings));
    }

    QueueFlowLimit::observe(*this, _settings);
}

void Queue::destroyed()
{
    unbind(broker->getExchanges());
    if (alternateExchange.get()) {
        Mutex::ScopedLock locker(messageLock);
        while(!messages->empty()){
            DeliverableMessage msg(messages->front().payload);
            alternateExchange->routeWithAlternate(msg);
            popAndDequeue();
        }
        alternateExchange->decAlternateUsers();
    }

    if (store) {
        barrier.destroy();
        store->flush(*this);
        store->destroy(*this);
        store = 0;//ensure we make no more calls to the store for this queue
    }
    if (autoDeleteTask) autoDeleteTask = boost::intrusive_ptr<TimerTask>();
    notifyDeleted();
}

void Queue::notifyDeleted()
{
    QueueListeners::ListenerSet set;
    {
        Mutex::ScopedLock locker(messageLock);
        listeners.snapshot(set);
        deleted = true;
    }
    set.notifyAll();
}

void Queue::bound(const string& exchange, const string& key,
                  const FieldTable& args)
{
    bindings.add(exchange, key, args);
}

void Queue::unbind(ExchangeRegistry& exchanges)
{
    bindings.unbind(exchanges, shared_from_this());
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
    if (mgmtObject != 0 && persistenceId == 0 && externalQueueStore)
    {
        ManagementObject* childObj = externalQueueStore->GetManagementObject();
        if (childObj != 0)
            childObj->setReference(mgmtObject->getObjectId());
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
    buffer.putShortString(alternateExchange.get() ? alternateExchange->getName() : std::string(""));
}

uint32_t Queue::encodedSize() const
{
    return name.size() + 1/*short string size octet*/
        + (alternateExchange.get() ? alternateExchange->getName().size() : 0) + 1 /* short string */
        + settings.encodedSize()
        + (policy.get() ? (*policy).encodedSize() : 0);
}

Queue::shared_ptr Queue::restore( QueueRegistry& queues, Buffer& buffer )
{
    string name;
    buffer.getShortString(name);
    FieldTable settings;
    buffer.get(settings);
    boost::shared_ptr<Exchange> alternate;
    std::pair<Queue::shared_ptr, bool> result = queues.declare(name, true, false, 0, alternate, settings, true);
    if (result.first->policy.get() && buffer.available() >= result.first->policy->encodedSize()) {
        buffer.get ( *(result.first->policy) );
    }
    if (buffer.available()) {
        string altExch;
        buffer.getShortString(altExch);
        result.first->alternateExchangeName.assign(altExch);
    }

    return result.first;
}


void Queue::setAlternateExchange(boost::shared_ptr<Exchange> exchange)
{
    alternateExchange = exchange;
    if (mgmtObject) {
        if (exchange.get() != 0)
            mgmtObject->set_altExchange(exchange->GetManagementObject()->getObjectId());
        else
            mgmtObject->clr_altExchange();
    }
}

boost::shared_ptr<Exchange> Queue::getAlternateExchange()
{
    return alternateExchange;
}

void tryAutoDeleteImpl(Broker& broker, Queue::shared_ptr queue)
{
    if (broker.getQueues().destroyIf(queue->getName(),
                                     boost::bind(boost::mem_fn(&Queue::canAutoDelete), queue))) {
        QPID_LOG(debug, "Auto-deleting " << queue->getName());
        queue->destroyed();
    }
}

struct AutoDeleteTask : qpid::sys::TimerTask
{
    Broker& broker;
    Queue::shared_ptr queue;

    AutoDeleteTask(Broker& b, Queue::shared_ptr q, AbsTime fireTime)
        : qpid::sys::TimerTask(fireTime, "DelayedAutoDeletion"), broker(b), queue(q) {}

    void fire()
    {
        //need to detect case where queue was used after the task was
        //created, but then became unused again before the task fired;
        //in this case ignore this request as there will have already
        //been a later task added
        tryAutoDeleteImpl(broker, queue);
    }
};

void Queue::tryAutoDelete(Broker& broker, Queue::shared_ptr queue)
{
    if (queue->autoDeleteTimeout && queue->canAutoDelete()) {
        AbsTime time(now(), Duration(queue->autoDeleteTimeout * TIME_SEC));
        queue->autoDeleteTask = boost::intrusive_ptr<qpid::sys::TimerTask>(new AutoDeleteTask(broker, queue, time));
        broker.getClusterTimer().add(queue->autoDeleteTask);
        QPID_LOG(debug, "Timed auto-delete for " << queue->getName() << " initiated");
    } else {
        tryAutoDeleteImpl(broker, queue);
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
    //reset auto deletion timer if necessary
    if (autoDeleteTimeout && autoDeleteTask) {
        autoDeleteTask->cancel();
    }
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

ManagementObject* Queue::GetManagementObject (void) const
{
    return (ManagementObject*) mgmtObject;
}

Manageable::status_t Queue::ManagementMethod (uint32_t methodId, Args& args, string& etext)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;

    QPID_LOG (debug, "Queue::ManagementMethod [id=" << methodId << "]");

    switch (methodId) {
    case _qmf::Queue::METHOD_PURGE :
        {
            _qmf::ArgsQueuePurge& purgeArgs = (_qmf::ArgsQueuePurge&) args;
            purge(purgeArgs.i_request, boost::shared_ptr<Exchange>(), &purgeArgs.i_filter);
            status = Manageable::STATUS_OK;
        }
        break;

    case _qmf::Queue::METHOD_REROUTE :
        {
            _qmf::ArgsQueueReroute& rerouteArgs = (_qmf::ArgsQueueReroute&) args;
            boost::shared_ptr<Exchange> dest;
            if (rerouteArgs.i_useAltExchange)
                dest = alternateExchange;
            else {
                try {
                    dest = broker->getExchanges().get(rerouteArgs.i_exchange);
                } catch(const std::exception&) {
                    status = Manageable::STATUS_PARAMETER_INVALID;
                    etext = "Exchange not found";
                    break;
                }
            }

            purge(rerouteArgs.i_request, dest, &rerouteArgs.i_filter);
            status = Manageable::STATUS_OK;
        }
        break;
    }

    return status;
}


void Queue::query(qpid::types::Variant::Map& results) const
{
    Mutex::ScopedLock locker(messageLock);
    /** @todo add any interesting queue state into results */
    if (allocator) allocator->query( results, messageLock );
}

void Queue::setPosition(SequenceNumber n) {
    Mutex::ScopedLock locker(messageLock);
    sequence = n;
}

SequenceNumber Queue::getPosition() {
    return sequence;
}

int Queue::getEventMode() { return eventMode; }

void Queue::recoveryComplete(ExchangeRegistry& exchanges)
{
    // set the alternate exchange
    if (!alternateExchangeName.empty()) {
        try {
            Exchange::shared_ptr ae = exchanges.get(alternateExchangeName);
            setAlternateExchange(ae);
        } catch (const NotFoundException&) {
            QPID_LOG(warning, "Could not set alternate exchange \"" << alternateExchangeName << "\" on queue \"" << name << "\": exchange does not exist.");
        }
    }
    //process any pending dequeues
    for_each(pendingDequeues.begin(), pendingDequeues.end(), boost::bind(&Queue::dequeue, this, (TransactionContext*) 0, _1));
    pendingDequeues.clear();
}

void Queue::insertSequenceNumbers(const std::string& key)
{
    seqNoKey = key;
    insertSeqNo = !seqNoKey.empty();
    QPID_LOG(debug, "Inserting sequence numbers as " << key);
}

void Queue::enqueued(const QueuedMessage& m)
{
    for (Observers::iterator i = observers.begin(); i != observers.end(); ++i) {
        try {
            (*i)->enqueued(m);
        } catch (const std::exception& e) {
            QPID_LOG(warning, "Exception on notification of enqueue for queue " << getName() << ": " << e.what());
        }
    }
    if (policy.get()) {
        policy->enqueued(m);
    }
    mgntEnqStats(m.payload);
}

void Queue::updateEnqueued(const QueuedMessage& m)
{
    if (m.payload) {
        boost::intrusive_ptr<Message> payload = m.payload;
        enqueue ( 0, payload, true );
        if (policy.get()) {
            policy->recoverEnqueued(payload);
        }
        enqueued(m);
    } else {
        QPID_LOG(warning, "Queue informed of enqueued message that has no payload");
    }
}

bool Queue::isEnqueued(const QueuedMessage& msg)
{
    return !policy.get() || policy->isEnqueued(msg);
}

QueueListeners& Queue::getListeners() { return listeners; }
Messages& Queue::getMessages() { return *messages; }
const Messages& Queue::getMessages() const { return *messages; }

void Queue::checkNotDeleted()
{
    if (deleted) {
        throw ResourceDeletedException(QPID_MSG("Queue " << getName() << " has been deleted."));
    }
}

void Queue::addObserver(boost::shared_ptr<QueueObserver> observer)
{
    observers.insert(observer);
}

void Queue::flush()
{
    ScopedUse u(barrier);
    if (u.acquired && store) store->flush(*this);
}


bool Queue::bind(boost::shared_ptr<Exchange> exchange, const std::string& key,
                 const qpid::framing::FieldTable& arguments)
{
    if (exchange->bind(shared_from_this(), key, &arguments)) {
        bound(exchange->getName(), key, arguments);
        if (exchange->isDurable() && isDurable()) {
            store->bind(*exchange, *this, key, arguments);
        }
        return true;
    } else {
        return false;
    }
}


const Broker* Queue::getBroker()
{
    return broker;
}

void Queue::setDequeueSincePurge(uint32_t value) {
    dequeueSincePurge = value;
}


Queue::UsageBarrier::UsageBarrier(Queue& q) : parent(q), count(0) {}

bool Queue::UsageBarrier::acquire()
{
    Monitor::ScopedLock l(parent.messageLock);
    if (parent.deleted) {
        return false;
    } else {
        ++count;
        return true;
    }
}

void Queue::UsageBarrier::release()
{
    Monitor::ScopedLock l(parent.messageLock);
    if (--count == 0) parent.messageLock.notifyAll();
}

void Queue::UsageBarrier::destroy()
{
    Monitor::ScopedLock l(parent.messageLock);
    parent.deleted = true;
    while (count) parent.messageLock.wait();
}


// KAG TBD: flesh out...




const std::string MessageGroupManager::getGroupId( const QueuedMessage& qm ) const
{
    const qpid::framing::FieldTable* headers = qm.payload->getApplicationHeaders();
    if (!headers) return qpidMessageGroupDefault;
    FieldTable::ValuePtr id = headers->get( groupIdHeader );
    if (!id || !id->convertsTo<std::string>()) return qpidMessageGroupDefault;
    return id->get<std::string>();
}


void MessageGroupManager::enqueued( const QueuedMessage& qm )
{
    std::string group( getGroupId(qm) );
    uint32_t total = ++messageGroups[group].total;
    QPID_LOG( trace, "group queue " << queue->getName() <<
              ": added message to group id=" << group << " total=" << total );
}


void MessageGroupManager::acquired( const QueuedMessage& qm )
{
    std::string group( getGroupId(qm) );
    GroupMap::iterator gs = messageGroups.find( group );
    assert( gs != messageGroups.end() );
    GroupState& state( gs->second );
    state.acquired += 1;
    QPID_LOG( trace, "group queue " << queue->getName() <<
              ": acquired message in group id=" << group << " acquired=" << state.acquired );
}


void MessageGroupManager::requeued( const QueuedMessage& qm )
{
    std::string group( getGroupId(qm) );
    GroupMap::iterator gs = messageGroups.find( group );
    assert( gs != messageGroups.end() );
    GroupState& state( gs->second );
    assert( state.acquired != 0 );
    state.acquired -= 1;
    if (state.acquired == 0 && !state.owner.empty()) {
        QPID_LOG( trace, "group queue " << queue->getName() <<
                  ": consumer name=" << state.owner << " released group id=" << gs->first);
        state.owner.clear();   // KAG TODO: need to invalidate consumer's positions?
    }
    QPID_LOG( trace, "group queue " << queue->getName() <<
              ": requeued message to group id=" << group << " acquired=" << state.acquired );
}


void MessageGroupManager::dequeued( const QueuedMessage& qm )
{
    std::string group( getGroupId(qm) );
    GroupMap::iterator gs = messageGroups.find( group );
    assert( gs != messageGroups.end() );
    GroupState& state( gs->second );
    assert( state.total != 0 );
    uint32_t total = state.total -= 1;
    assert( state.acquired != 0 );
    state.acquired -= 1;
    if (state.total == 0) {
        QPID_LOG( trace, "group queue " << queue->getName() << ": deleting group id=" << gs->first);
        messageGroups.erase( gs );
    } else {
        if (state.acquired == 0 && !state.owner.empty()) {
            QPID_LOG( trace, "group queue " << queue->getName() <<
                      ": consumer name=" << state.owner << " released group id=" << gs->first);
            state.owner.clear();   // KAG TODO: need to invalidate consumer's positions?
        }
    }
    QPID_LOG( trace, "group queue " << queue->getName() <<
              ": dequeued message from group id=" << group << " total=" << total );
}

void MessageGroupManager::consumerAdded( const Consumer& c )
{
    const std::string& name(c.getName());
    bool unique = consumers.insert( name ).second;
    (void) unique; assert( unique );
    QPID_LOG( trace, "group queue " << queue->getName() << ": added consumer name=" << name );
}

void MessageGroupManager::consumerRemoved( const Consumer& c )
{
    const std::string& name(c.getName());
    size_t count = consumers.erase( name );
    (void) count; assert( count == 1 );

    bool needReset = false;
    for (GroupMap::iterator gs = messageGroups.begin();
         gs != messageGroups.end(); ++gs) {

        GroupState& state( gs->second );
        if (state.owner == name) {
            state.owner.clear();
            needReset = true;
            QPID_LOG( trace, "group queue " << queue->getName() <<
                      ": consumer name=" << name << " released group id=" << gs->first);
        }
    }

    if (needReset) {
        // KAG TODO: How do I invalidate all consumers that need invalidating????
    }
    QPID_LOG( trace, "group queue " << queue->getName() << ": removed consumer name=" << name );
}


bool MessageGroupManager::nextMessage( Consumer::shared_ptr c, QueuedMessage& next,
                                       const Mutex::ScopedLock& )
{
    Messages& messages(queue->getMessages());

    if (messages.empty())
        return false;

    if (c->preAcquires()) {     // not browsing
        next = messages.front();
        QueuedMessage current;
        do {
            current = next;
            /** @todo KAG: horrifingly suboptimal  - optimize */
            std::string group( getGroupId( current ) );
            GroupMap::iterator gs = messageGroups.find( group );    /** @todo need to cache this somehow */
            assert( gs != messageGroups.end() );
            GroupState& state( gs->second );
            if (state.owner.empty()) {
                state.owner = c->getName();
                QPID_LOG( trace, "group queue " << queue->getName() <<
                          ": consumer name=" << c->getName() << " has acquired group id=" << group);
                return true;
            }
            if (state.owner == c->getName()) {
                return true;
            }
        } while (messages.next( current, next ));     /** @todo: .next() is a linear search from front - optimize */
        return false;
    } else if (messages.next(c->position, next))
        return true;
    return false;
}


bool MessageGroupManager::canAcquire(const std::string& consumer, const QueuedMessage& qm,
                                     const Mutex::ScopedLock&)
{
    std::string group( getGroupId(qm) );
    GroupMap::iterator gs = messageGroups.find( group );
    assert( gs != messageGroups.end() );
    GroupState& state( gs->second );

    if (state.owner.empty()) {
        state.owner = consumer;
        QPID_LOG( trace, "group queue " << queue->getName() <<
                  ": consumer name=" << consumer << " has acquired group id=" << gs->first);
        return true;
    }
    return state.owner == consumer;
}

namespace {
    const std::string GroupQueryKey("qpid.message_group_queue");
    const std::string GroupHeaderKey("group_header_key");
    const std::string GroupStateKey("group_state");
    const std::string GroupIdKey("group_id");
    const std::string GroupMsgCount("msg_count");
    const std::string GroupTimestamp("timestamp");
    const std::string GroupConsumer("consumer");
}

void MessageGroupManager::query(qpid::types::Variant::Map& status,
                                const Mutex::ScopedLock&) const
{
    /** Add a description of the current state of the message groups for this queue.
        FORMAT:
        { "qpid.message_group_queue":
            { "group_header_key" : "<KEY>",
              "group_state" :
                   [ { "group_id"  : "<name>",
                       "msg_count" : <int>,
                       "timestamp" : <absTime>,
                       "consumer"  : <consumer name> },
                     {...} // one for each known group
                   ]
            }
        }
    **/

    assert(status.find(GroupQueryKey) == status.end());
    qpid::types::Variant::Map state;
    qpid::types::Variant::List groups;

    state[GroupHeaderKey] = groupIdHeader;
    for (GroupMap::const_iterator g = messageGroups.begin();
         g != messageGroups.end(); ++g) {
        qpid::types::Variant::Map info;
        info[GroupIdKey] = g->first;
        info[GroupMsgCount] = g->second.total;
        info[GroupTimestamp] = 0;   /** @todo KAG - NEED HEAD MSG TIMESTAMP */
        info[GroupConsumer] = g->second.owner;
        groups.push_back(info);
    }
    state[GroupStateKey] = groups;
    status[GroupQueryKey] = state;
}

bool MessageGroupManager::match(const qpid::types::Variant::Map* filter,
                                const QueuedMessage& message) const
{
    if (!filter) return true;
    qpid::types::Variant::Map::const_iterator i = filter->find( qpidMessageGroupFilter );
    if (i == filter->end()) return true;
    if (i->second.asString() == getGroupId(message)) return true;
    return false;
}

boost::shared_ptr<MessageGroupManager> MessageGroupManager::create( Queue *q,
                                                                    const qpid::framing::FieldTable& settings )
{
    boost::shared_ptr<MessageGroupManager> empty;

    if (settings.isSet(qpidMessageGroupKey)) {

        Queue::Disposition qt = q->getDisposition();

        if (qt == Queue::LVQ) {
            QPID_LOG( error, "Message Groups cannot be enabled on LVQ Queues, queue=" << q->getName());
            return empty;
        }
        if (qt == Queue::PRIORITY) {
            QPID_LOG( error, "Message Groups cannot be enabled for Priority Queues, queue=" << q->getName());
            return empty;
        }
        std::string headerKey = settings.getAsString(qpidMessageGroupKey);
        if (headerKey.empty()) {
            QPID_LOG( error, "A Message Group header key must be configured, queue=" << q->getName());
            return empty;
        }
        unsigned int timestamp = settings.getAsInt(qpidMessageGroupTimestamp);

        boost::shared_ptr<MessageGroupManager> manager( new MessageGroupManager( headerKey, q, timestamp ) );

        q->addObserver( boost::static_pointer_cast<QueueObserver>(manager) );

        QPID_LOG( debug, "Configured Queue '" << q->getName() <<
                  "' for message grouping using header key '" << headerKey << "'" <<
                  " (timestamp=" << timestamp << ")");
        return manager;
    }
    return empty;
}




// default allocator - requires messageLock to be held by caller!
bool MessageAllocator::nextMessage( Consumer::shared_ptr c, QueuedMessage& next,
                                   const Mutex::ScopedLock& /*just to enforce locking*/)
{
    Messages& messages(queue->getMessages());

    if (messages.empty())
        return false;

    if (c->preAcquires()) {     // not browsing
        next = messages.front();
        return true;
    } else if (messages.next(c->position, next))
        return true;
    return false;
}


// default allocator - requires messageLock to be held by caller!
bool MessageAllocator::canAcquire(const std::string&, const QueuedMessage&,
                                  const Mutex::ScopedLock& /*just to enforce locking*/)
{
    return true;    // always give permission to acquire
}


// default match - ignore filter and always match.
bool MessageAllocator::match(const qpid::types::Variant::Map*,
                             const QueuedMessage&) const
{
    return true;
}




