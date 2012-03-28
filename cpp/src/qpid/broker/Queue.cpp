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

#include "qpid/broker/Queue.h"

#include "qpid/broker/Broker.h"
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
#include "qpid/broker/FifoDistributor.h"
#include "qpid/broker/MessageGroupManager.h"

#include "qpid/StringUtils.h"
#include "qpid/log/Statement.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/FieldValue.h"
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


namespace qpid {
namespace broker {

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

inline void mgntEnqStats(const boost::intrusive_ptr<Message>& msg,
			 _qmf::Queue* mgmtObject,
			 _qmf::Broker* brokerMgmtObject)
{
    if (mgmtObject != 0) {
        _qmf::Queue::PerThreadStats *qStats = mgmtObject->getStatistics();
        _qmf::Broker::PerThreadStats *bStats = brokerMgmtObject->getStatistics();

        uint64_t contentSize = msg->contentSize();
        qStats->msgTotalEnqueues +=1;
        bStats->msgTotalEnqueues += 1;
        qStats->byteTotalEnqueues += contentSize;
        bStats->byteTotalEnqueues += contentSize;
        if (msg->isPersistent ()) {
            qStats->msgPersistEnqueues += 1;
            bStats->msgPersistEnqueues += 1;
            qStats->bytePersistEnqueues += contentSize;
            bStats->bytePersistEnqueues += contentSize;
        }
        mgmtObject->statisticsUpdated();
        brokerMgmtObject->statisticsUpdated();
    }
}

inline void mgntDeqStats(const boost::intrusive_ptr<Message>& msg,
			 _qmf::Queue* mgmtObject,
			 _qmf::Broker* brokerMgmtObject)
{
    if (mgmtObject != 0){
        _qmf::Queue::PerThreadStats *qStats = mgmtObject->getStatistics();
        _qmf::Broker::PerThreadStats *bStats = brokerMgmtObject->getStatistics();
        uint64_t contentSize = msg->contentSize();

        qStats->msgTotalDequeues += 1;
        bStats->msgTotalDequeues += 1;
        qStats->byteTotalDequeues += contentSize;
        bStats->byteTotalDequeues += contentSize;
        if (msg->isPersistent ()){
            qStats->msgPersistDequeues += 1;
            bStats->msgPersistDequeues += 1;
            qStats->bytePersistDequeues += contentSize;
            bStats->bytePersistDequeues += contentSize;
        }
        mgmtObject->statisticsUpdated();
        brokerMgmtObject->statisticsUpdated();
    }
}

} // namespace

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
    brokerMgmtObject(0),
    eventMode(0),
    insertSeqNo(0),
    broker(b),
    deleted(false),
    barrier(*this),
    autoDeleteTimeout(0),
    allocator(new FifoDistributor( *messages ))
{
    if (parent != 0 && broker != 0) {
        ManagementAgent* agent = broker->getManagementAgent();

        if (agent != 0) {
            mgmtObject = new _qmf::Queue(agent, this, parent, _name, _store != 0, _autodelete, _owner != 0);
            agent->addObject(mgmtObject, 0, store != 0);
            brokerMgmtObject = (qmf::org::apache::qpid::broker::Broker*) broker->GetManagementObject();
            if (brokerMgmtObject)
                brokerMgmtObject->inc_queueCount();
        }
    }
}

Queue::~Queue()
{
    if (mgmtObject != 0) {
        mgmtObject->resourceDestroy();
        if (brokerMgmtObject)
            brokerMgmtObject->dec_queueCount();
    }
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
            alternateExchange->route(deliverable);
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
    Mutex::ScopedLock locker(messageLock);
    if (policy.get()) policy->recoverEnqueued(msg);
}

void Queue::recover(boost::intrusive_ptr<Message>& msg)
{
    {
        Mutex::ScopedLock locker(messageLock);
        if (policy.get()) policy->recoverEnqueued(msg);
    }

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
        _qmf::Queue::PerThreadStats *qStats = mgmtObject->getStatistics();
        const uint64_t contentSize = msg->contentSize();
        qStats->msgTxnEnqueues  += 1;
        qStats->byteTxnEnqueues += contentSize;
        mgmtObject->statisticsUpdated();
        if (brokerMgmtObject) {
            _qmf::Broker::PerThreadStats *bStats = brokerMgmtObject->getStatistics();
            bStats->msgTxnEnqueues += 1;
            bStats->byteTxnEnqueues += contentSize;
            brokerMgmtObject->statisticsUpdated();
        }
    }
}

void Queue::requeue(const QueuedMessage& msg){
    assertClusterSafe();
    QueueListeners::NotificationSet copy;
    {
        if (!isEnqueued(msg)) return;
        if (deleted) {
            //
            // If the queue has been deleted, requeued messages must be sent to the alternate exchange
            // if one is configured.
            //
            if (alternateExchange.get()) {
                DeliverableMessage dmsg(msg.payload);
                alternateExchange->routeWithAlternate(dmsg);
                if (brokerMgmtObject)
                    brokerMgmtObject->inc_abandonedViaAlt();
            } else {
                if (brokerMgmtObject)
                    brokerMgmtObject->inc_abandoned();
            }
            mgntDeqStats(msg.payload, mgmtObject, brokerMgmtObject);
        } else {
            {
                Mutex::ScopedLock locker(messageLock);
                messages->release(msg);
                observeRequeue(msg, locker);
                listeners.populate(copy);
            }

            if (mgmtObject) {
                mgmtObject->inc_releases();
                if (brokerMgmtObject)
                    brokerMgmtObject->inc_releases();
            }

            // for persistLastNode - don't force a message twice to disk, but force it if no force before
            if(inLastNodeFailure && persistLastNode && !msg.payload->isStoredOnQueue(shared_from_this())) {
                msg.payload->forcePersistent();
                if (msg.payload->isForcedPersistent() ){
                    boost::intrusive_ptr<Message> payload = msg.payload;
                    enqueue(0, payload);
                }
            }
        }
    }
    copy.notify();
}

bool Queue::acquireMessageAt(const SequenceNumber& position, QueuedMessage& message)
{
    assertClusterSafe();
    QPID_LOG(debug, "Attempting to acquire message at " << position);
    if (acquire(position, message)) {
        QPID_LOG(debug, "Acquired message at " << position << " from " << name);
        return true;
    } else {
        QPID_LOG(debug, "Could not acquire message at " << position << " from " << name << "; no message at that position");
        return false;
    }
}

bool Queue::acquire(const QueuedMessage& msg, const std::string& consumer)
{
    assertClusterSafe();
    QPID_LOG(debug, consumer << " attempting to acquire message at " << msg.position);
    bool ok;
    {
        Mutex::ScopedLock locker(messageLock);
        ok = allocator->allocate( consumer, msg );
    }
    if (!ok) {
        QPID_LOG(debug, "Not permitted to acquire msg at " << msg.position << " from '" << name);
        return false;
    }

    QueuedMessage copy(msg);
    if (acquire( msg.position, copy)) {
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

bool Queue::getNextMessage(QueuedMessage& m, Consumer::shared_ptr& c)
{
    checkNotDeleted(c);
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

Queue::ConsumeCode Queue::consumeNextMessage(QueuedMessage& m, Consumer::shared_ptr& c)
{
    while (true) {
        QueuedMessage msg;
        bool found;
        {
            Mutex::ScopedLock locker(messageLock);
            found = allocator->nextConsumableMessage(c, msg);
            if (!found) listeners.addListener(c);
        }
        if (!found) {
            QPID_LOG(debug, "No messages to dispatch on queue '" << name << "'");
            return NO_MESSAGES;
        }

        if (msg.payload->hasExpired()) {
            QPID_LOG(debug, "Message expired from queue '" << name << "'");
            c->setPosition(msg.position);
            dequeue(0, msg);
            if (mgmtObject) {
                mgmtObject->inc_discardsTtl();
                if (brokerMgmtObject)
                    brokerMgmtObject->inc_discardsTtl();
            }
            continue;
        }

        if (c->filter(msg.payload)) {
            if (c->accept(msg.payload)) {
                {
                    Mutex::ScopedLock locker(messageLock);
                    bool ok = allocator->allocate( c->getName(), msg );  // inform allocator
                    (void) ok; assert(ok);
                    observeAcquire(msg, locker);
                }
                if (mgmtObject) {
                    mgmtObject->inc_acquires();
                    if (brokerMgmtObject)
                        brokerMgmtObject->inc_acquires();
                }
                m = msg;
                return CONSUMED;
            } else {
                //message(s) are available but consumer hasn't got enough credit
                QPID_LOG(debug, "Consumer can't currently accept message from '" << name << "'");
            }
        } else {
            //consumer will never want this message
            QPID_LOG(debug, "Consumer doesn't want message from '" << name << "'");
        }

        Mutex::ScopedLock locker(messageLock);
        messages->release(msg);
        return CANT_CONSUME;
    }
}

bool Queue::browseNextMessage(QueuedMessage& m, Consumer::shared_ptr& c)
{
    while (true) {
        QueuedMessage msg;
        bool found;
        {
            Mutex::ScopedLock locker(messageLock);
            found = allocator->nextBrowsableMessage(c, msg);
            if (!found) listeners.addListener(c);
        }
        if (!found) { // no next available
            QPID_LOG(debug, "No browsable messages available for consumer " <<
                     c->getName() << " on queue '" << name << "'");
            return false;
        }

        if (c->filter(msg.payload) && !msg.payload->hasExpired()) {
            if (c->accept(msg.payload)) {
                //consumer wants the message
                c->setPosition(msg.position);
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
            c->setPosition(msg.position);
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
        Mutex::ScopedLock locker(messageLock);
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
        //reset auto deletion timer if necessary
        if (autoDeleteTimeout && autoDeleteTask) {
            autoDeleteTask->cancel();
        }
        observeConsumerAdd(*c, locker);
    }
    if (mgmtObject != 0)
        mgmtObject->inc_consumerCount ();
}

void Queue::cancel(Consumer::shared_ptr c){
    removeListener(c);
    {
        Mutex::ScopedLock locker(messageLock);
        consumerCount--;
        if(exclusive) exclusive = 0;
        observeConsumerRemove(*c, locker);
    }
    if (mgmtObject != 0)
        mgmtObject->dec_consumerCount ();
}

QueuedMessage Queue::get(){
    QueuedMessage msg(this);
    bool ok;
    {
        Mutex::ScopedLock locker(messageLock);
        ok = messages->consume(msg);
        if (ok) observeAcquire(msg, locker);
    }

    if (ok && mgmtObject) {
        mgmtObject->inc_acquires();
        if (brokerMgmtObject)
            brokerMgmtObject->inc_acquires();
    }

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

        if (!expired.empty()) {
            if (mgmtObject) {
                mgmtObject->inc_acquires(expired.size());
                mgmtObject->inc_discardsTtl(expired.size());
                if (brokerMgmtObject) {
                    brokerMgmtObject->inc_acquires(expired.size());
                    brokerMgmtObject->inc_discardsTtl(expired.size());
                }
            }

            for (std::deque<QueuedMessage>::const_iterator i = expired.begin();
                 i != expired.end(); ++i) {
                {
                    // KAG: should be safe to retake lock after the removeIf, since
                    // no other thread can touch these messages after the removeIf() call
                    Mutex::ScopedLock locker(messageLock);
                    observeAcquire(*i, locker);
                }
                dequeue( 0, *i );
            }
        }
    }
}


namespace {
    // for use with purge/move below - collect messages that match a given filter
    //
    class MessageFilter
    {
    public:
        static const std::string typeKey;
        static const std::string paramsKey;
        static MessageFilter *create( const ::qpid::types::Variant::Map *filter );
        virtual bool match( const QueuedMessage& ) const { return true; }
        virtual ~MessageFilter() {}
    protected:
        MessageFilter() {};
    };
    const std::string MessageFilter::typeKey("filter_type");
    const std::string MessageFilter::paramsKey("filter_params");

    // filter by message header string value exact match
    class HeaderMatchFilter : public MessageFilter
    {
    public:
        /* Config:
           { 'filter_type' : 'header_match_str',
             'filter_params' : { 'header_key' : "<header name>",
                                 'header_value' : "<value to match>"
                               }
           }
        */
        static const std::string typeKey;
        static const std::string headerKey;
        static const std::string valueKey;
        HeaderMatchFilter( const std::string& _header, const std::string& _value )
            : MessageFilter (), header(_header), value(_value) {}
        bool match( const QueuedMessage& msg ) const
        {
            const qpid::framing::FieldTable* headers = msg.payload->getApplicationHeaders();
            if (!headers) return false;
            FieldTable::ValuePtr h = headers->get(header);
            if (!h || !h->convertsTo<std::string>()) return false;
            return h->get<std::string>() == value;
        }
    private:
        const std::string header;
        const std::string value;
    };
    const std::string HeaderMatchFilter::typeKey("header_match_str");
    const std::string HeaderMatchFilter::headerKey("header_key");
    const std::string HeaderMatchFilter::valueKey("header_value");

    // factory to create correct filter based on map
    MessageFilter* MessageFilter::create( const ::qpid::types::Variant::Map *filter )
    {
        using namespace qpid::types;
        if (filter && !filter->empty()) {
            Variant::Map::const_iterator i = filter->find(MessageFilter::typeKey);
            if (i != filter->end()) {

                if (i->second.asString() == HeaderMatchFilter::typeKey) {
                    Variant::Map::const_iterator p = filter->find(MessageFilter::paramsKey);
                    if (p != filter->end() && p->second.getType() == VAR_MAP) {
                        Variant::Map::const_iterator k = p->second.asMap().find(HeaderMatchFilter::headerKey);
                        Variant::Map::const_iterator v = p->second.asMap().find(HeaderMatchFilter::valueKey);
                        if (k != p->second.asMap().end() && v != p->second.asMap().end()) {
                            std::string headerKey(k->second.asString());
                            std::string value(v->second.asString());
                            QPID_LOG(debug, "Message filtering by header value configured.  key: " << headerKey << " value: " << value );
                            return new HeaderMatchFilter( headerKey, value );
                        }
                    }
                }
            }
            QPID_LOG(error, "Ignoring unrecognized message filter: '" << *filter << "'");
        }
        return new MessageFilter();
    }

    // used by removeIf() to collect all messages matching a filter, maximum match count is
    // optional.
    struct Collector {
        const uint32_t maxMatches;
        MessageFilter& filter;
        std::deque<QueuedMessage> matches;
        Collector(MessageFilter& filter, uint32_t max)
            : maxMatches(max), filter(filter) {}
        bool operator() (QueuedMessage& qm)
        {
            if (maxMatches == 0 || matches.size() < maxMatches) {
                if (filter.match( qm )) {
                    matches.push_back(qm);
                    return true;
                }
            }
            return false;
        }
    };

} // end namespace


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
 * message is purged only if the filter matches.  See MessageDistributor for more detail.
 */
uint32_t Queue::purge(const uint32_t purge_request, boost::shared_ptr<Exchange> dest,
                      const qpid::types::Variant::Map *filter)
{
    std::auto_ptr<MessageFilter> mf(MessageFilter::create(filter));
    Collector c(*mf.get(), purge_request);

    {
        Mutex::ScopedLock locker(messageLock);
        messages->removeIf( boost::bind<bool>(boost::ref(c), _1) );
    }

    if (!c.matches.empty()) {
        if (mgmtObject) {
            mgmtObject->inc_acquires(c.matches.size());
            if (dest.get()) {
                mgmtObject->inc_reroutes(c.matches.size());
                if (brokerMgmtObject) {
                    brokerMgmtObject->inc_acquires(c.matches.size());
                    brokerMgmtObject->inc_reroutes(c.matches.size());
                }
            } else {
                mgmtObject->inc_discardsPurge(c.matches.size());
                if (brokerMgmtObject) {
                    brokerMgmtObject->inc_acquires(c.matches.size());
                    brokerMgmtObject->inc_discardsPurge(c.matches.size());
                }
            }
        }

        for (std::deque<QueuedMessage>::iterator qmsg = c.matches.begin();
             qmsg != c.matches.end(); ++qmsg) {

            {
                // KAG: should be safe to retake lock after the removeIf, since
                // no other thread can touch these messages after the removeIf call
                Mutex::ScopedLock locker(messageLock);
                observeAcquire(*qmsg, locker);
            }
            dequeue(0, *qmsg);
            QPID_LOG(debug, "Purged message at " << qmsg->position << " from " << getName());
            // now reroute if necessary
            if (dest.get()) {
                assert(qmsg->payload);
                DeliverableMessage dmsg(qmsg->payload);
                dest->routeWithAlternate(dmsg);
            }
        }
    }
    return c.matches.size();
}

uint32_t Queue::move(const Queue::shared_ptr destq, uint32_t qty,
                     const qpid::types::Variant::Map *filter)
{
    std::auto_ptr<MessageFilter> mf(MessageFilter::create(filter));
    Collector c(*mf.get(), qty);

    {
        Mutex::ScopedLock locker(messageLock);
        messages->removeIf( boost::bind<bool>(boost::ref(c), _1) );
    }


    if (!c.matches.empty()) {
        // Update observers and message state:

        if (mgmtObject) {
            mgmtObject->inc_acquires(c.matches.size());
            if (brokerMgmtObject)
                brokerMgmtObject->inc_acquires(c.matches.size());
        }

        for (std::deque<QueuedMessage>::iterator qmsg = c.matches.begin();
             qmsg != c.matches.end(); ++qmsg) {
            {
                Mutex::ScopedLock locker(messageLock);
                observeAcquire(*qmsg, locker);
            }
            dequeue(0, *qmsg);
            // and move to destination Queue.
            assert(qmsg->payload);
            destq->deliver(qmsg->payload);
        }
    }
    return c.matches.size();
}

/** Acquire the message at the given position, return true and msg if acquire succeeds */
bool Queue::acquire(const qpid::framing::SequenceNumber& position, QueuedMessage& msg)
{
    bool ok;
    {
        Mutex::ScopedLock locker(messageLock);
        ok = messages->acquire(position, msg);
        if (ok) observeAcquire(msg, locker);
    }
    if (ok) {
        if (mgmtObject) {
            mgmtObject->inc_acquires();
            if (brokerMgmtObject)
                brokerMgmtObject->inc_acquires();
        }
        ++dequeueSincePurge;
        return true;
    }
    return false;
}

void Queue::push(boost::intrusive_ptr<Message>& msg, bool isRecovery){
    assertClusterSafe();
    QueueListeners::NotificationSet copy;
    QueuedMessage removed, qm(this, msg);
    bool dequeueRequired = false;
    {
        Mutex::ScopedLock locker(messageLock);
        qm.position = ++sequence;
        if (messages->push(qm, removed)) {
            dequeueRequired = true;
            observeAcquire(removed, locker);
        }
        observeEnqueue(qm, locker);
        if (policy.get()) {
            policy->enqueued(qm);
        }
        listeners.populate(copy);
    }
    if (insertSeqNo) msg->insertCustomProperty(seqNoKey, qm.position);

    mgntEnqStats(msg, mgmtObject, brokerMgmtObject);

    if (dequeueRequired) {
        if (mgmtObject) {
            mgmtObject->inc_acquires();
            mgmtObject->inc_discardsLvq();
            if (brokerMgmtObject)
                brokerMgmtObject->inc_acquires();
                brokerMgmtObject->inc_discardsLvq();
        }
        if (isRecovery) {
            //can't issue new requests for the store until
            //recovery is complete
            Mutex::ScopedLock locker(messageLock);
            pendingDequeues.push_back(removed);
        } else {
            dequeue(0, removed);
        }
    }
    copy.notify();
}

void isEnqueueComplete(uint32_t* result, const QueuedMessage& message)
{
    if (message.payload->isIngressComplete()) (*result)++;
}

/** function only provided for unit tests, or code not in critical message path */
uint32_t Queue::getEnqueueCompleteMessageCount() const
{
    uint32_t count = 0;
    Mutex::ScopedLock locker(messageLock);
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
    Mutex::ScopedLock locker(messageLock);
    return consumerCount;
}

bool Queue::canAutoDelete() const
{
    Mutex::ScopedLock locker(messageLock);
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
            try {
                policy->tryEnqueue(msg);
            } catch(ResourceLimitExceededException&) {
                if (mgmtObject) {
                    mgmtObject->inc_discardsOverflow();
                    if (brokerMgmtObject)
                        brokerMgmtObject->inc_discardsOverflow();
                }
                throw;
            }
            policy->getPendingDequeues(dequeues);
        }
        //depending on policy, may have some dequeues that need to performed without holding the lock

        //
        // Count the dequeues as ring-discards.  We know that these aren't rejects because
        // policy->tryEnqueue would have thrown an exception.
        //
        if (mgmtObject && !dequeues.empty()) {
            mgmtObject->inc_discardsRing(dequeues.size());
            if (brokerMgmtObject)
                brokerMgmtObject->inc_discardsRing(dequeues.size());
        }

        for_each(dequeues.begin(), dequeues.end(), boost::bind(&Queue::dequeue, this, (TransactionContext*) 0, _1));
    }

    if (inLastNodeFailure && persistLastNode){
        msg->forcePersistent();
    }

    if (traceId.size()) {
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
            if (policy.get()) policy->dequeued(msg);
            messages->deleted(msg);
            observeDequeue(msg, locker);
        }
    }

    if (!ctxt) {
        mgntDeqStats(msg.payload, mgmtObject, brokerMgmtObject);
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
    {
        Mutex::ScopedLock locker(messageLock);
        if (policy.get()) policy->dequeued(msg);
        messages->deleted(msg);
        observeDequeue(msg, locker);
    }
    mgntDeqStats(msg.payload, mgmtObject, brokerMgmtObject);
    if (mgmtObject != 0) {
        _qmf::Queue::PerThreadStats *qStats = mgmtObject->getStatistics();
        const uint64_t contentSize = msg.payload->contentSize();
        qStats->msgTxnDequeues  += 1;
        qStats->byteTxnDequeues += contentSize;
        mgmtObject->statisticsUpdated();
        if (brokerMgmtObject) {
            _qmf::Broker::PerThreadStats *bStats = brokerMgmtObject->getStatistics();
            bStats->msgTxnDequeues += 1;
            bStats->byteTxnDequeues += contentSize;
            brokerMgmtObject->statisticsUpdated();
        }
    }
}

/**
 * Removes the first (oldest) message from the in-memory delivery queue as well dequeing
 * it from the logical (and persistent if applicable) queue
 */
bool Queue::popAndDequeue(QueuedMessage& msg)
{
    bool popped;
    {
        Mutex::ScopedLock locker(messageLock);
        popped = messages->consume(msg);
        if (popped) observeAcquire(msg, locker);
    }
    if (popped) {
        if (mgmtObject) {
            mgmtObject->inc_acquires();
            if (brokerMgmtObject)
                brokerMgmtObject->inc_acquires();
        }
        dequeue(0, msg);
        return true;
    } else {
        return false;
    }
}

/**
 * Updates policy and management when a message has been dequeued,
 * Requires messageLock be held by caller.
 */
void Queue::observeDequeue(const QueuedMessage& msg, const qpid::sys::Mutex::ScopedLock&)
{
    for (Observers::const_iterator i = observers.begin(); i != observers.end(); ++i) {
        try{
            (*i)->dequeued(msg);
        } catch (const std::exception& e) {
            QPID_LOG(warning, "Exception on notification of dequeue for queue " << getName() << ": " << e.what());
        }
    }
}

/** updates queue observers when a message has become unavailable for transfer.
 * Requires messageLock be held by caller.
 */
void Queue::observeAcquire(const QueuedMessage& msg, const qpid::sys::Mutex::ScopedLock&)
{
    for (Observers::const_iterator i = observers.begin(); i != observers.end(); ++i) {
        try{
            (*i)->acquired(msg);
        } catch (const std::exception& e) {
            QPID_LOG(warning, "Exception on notification of message removal for queue " << getName() << ": " << e.what());
        }
    }
}

/** updates queue observers when a message has become re-available for transfer
 *  Requires messageLock be held by caller.
 */
void Queue::observeRequeue(const QueuedMessage& msg, const qpid::sys::Mutex::ScopedLock&)
{
    for (Observers::const_iterator i = observers.begin(); i != observers.end(); ++i) {
        try{
            (*i)->requeued(msg);
        } catch (const std::exception& e) {
            QPID_LOG(warning, "Exception on notification of message requeue for queue " << getName() << ": " << e.what());
        }
    }
}

/** updates queue observers when a new consumer has subscribed to this queue.
 */
void Queue::observeConsumerAdd( const Consumer& c, const qpid::sys::Mutex::ScopedLock&)
{
    for (Observers::const_iterator i = observers.begin(); i != observers.end(); ++i) {
        try{
            (*i)->consumerAdded(c);
        } catch (const std::exception& e) {
            QPID_LOG(warning, "Exception on notification of new consumer for queue " << getName() << ": " << e.what());
        }
    }
}

/** updates queue observers when a consumer has unsubscribed from this queue.
 */
void Queue::observeConsumerRemove( const Consumer& c, const qpid::sys::Mutex::ScopedLock&)
{
    for (Observers::const_iterator i = observers.begin(); i != observers.end(); ++i) {
        try{
            (*i)->consumerRemoved(c);
        } catch (const std::exception& e) {
            QPID_LOG(warning, "Exception on notification of removed consumer for queue " << getName() << ": " << e.what());
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

bool getBoolSetting(const qpid::framing::FieldTable& settings, const std::string& key)
{
    qpid::framing::FieldTable::ValuePtr v = settings.get(key);
    if (!v) {
        return false;
    } else if (v->convertsTo<int>()) {
        return v->get<int>() != 0;
    } else if (v->convertsTo<std::string>()){
        std::string s = v->get<std::string>();
        if (s == "True")  return true;
        if (s == "true")  return true;
        if (s == "False") return false;
        if (s == "false") return false;
        try {
            return boost::lexical_cast<bool>(s);
        } catch(const boost::bad_lexical_cast&) {
            QPID_LOG(warning, "Ignoring invalid boolean value for " << key << ": " << s);
            return false;
        }
    } else {
        QPID_LOG(warning, "Ignoring invalid boolean value for " << key << ": " << *v);
        return false;
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
    noLocal = getBoolSetting(_settings, qpidNoLocal);
    QPID_LOG(debug, "Configured queue " << getName() << " with no-local=" << noLocal);

    std::string lvqKey = _settings.getAsString(qpidLastValueQueueKey);
    if (lvqKey.size()) {
        QPID_LOG(debug, "Configured queue " <<  getName() << " as Last Value Queue with key " << lvqKey);
        messages = std::auto_ptr<Messages>(new MessageMap(lvqKey));
        allocator = boost::shared_ptr<MessageDistributor>(new FifoDistributor( *messages ));
    } else if (getBoolSetting(_settings, qpidLastValueQueueNoBrowse)) {
        QPID_LOG(debug, "Configured queue " <<  getName() << " as Legacy Last Value Queue with 'no-browse' on");
        messages = LegacyLVQ::updateOrReplace(messages, qpidVQMatchProperty, true, broker);
        allocator = boost::shared_ptr<MessageDistributor>(new FifoDistributor( *messages ));
    } else if (getBoolSetting(_settings, qpidLastValueQueue)) {
        QPID_LOG(debug, "Configured queue " <<  getName() << " as Legacy Last Value Queue");
        messages = LegacyLVQ::updateOrReplace(messages, qpidVQMatchProperty, false, broker);
        allocator = boost::shared_ptr<MessageDistributor>(new FifoDistributor( *messages ));
    } else {
        std::auto_ptr<Messages> m = Fairshare::create(_settings);
        if (m.get()) {
            messages = m;
            allocator = boost::shared_ptr<MessageDistributor>(new FifoDistributor( *messages ));
            QPID_LOG(debug, "Configured queue " <<  getName() << " as priority queue.");
        } else { // default (FIFO) queue type
            // override default message allocator if message groups configured.
            boost::shared_ptr<MessageGroupManager> mgm(MessageGroupManager::create( getName(), *messages, _settings));
            if (mgm) {
                allocator = mgm;
                addObserver(mgm);
            }
        }
    }

    persistLastNode = getBoolSetting(_settings, qpidPersistLastNode);
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

    QueuedMessage m;
    while(popAndDequeue(m)) {
        DeliverableMessage msg(m.payload);
        if (alternateExchange.get()) {
            if (brokerMgmtObject)
                brokerMgmtObject->inc_abandonedViaAlt();
            alternateExchange->routeWithAlternate(msg);
        } else {
            if (brokerMgmtObject)
                brokerMgmtObject->inc_abandoned();
        }
    }
    if (alternateExchange.get())
        alternateExchange->decAlternateUsers();

    if (store) {
        barrier.destroy();
        store->flush(*this);
        store->destroy(*this);
        store = 0;//ensure we make no more calls to the store for this queue
    }
    if (autoDeleteTask) autoDeleteTask = boost::intrusive_ptr<TimerTask>();
    notifyDeleted();
    {
        Mutex::ScopedLock lock(messageLock);
        observers.clear();
    }
}

void Queue::notifyDeleted()
{
    QueueListeners::ListenerSet set;
    {
        Mutex::ScopedLock locker(messageLock);
        deleted = true;
        listeners.snapshot(set);
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
    Mutex::ScopedLock locker(messageLock);
    policy = _policy;
    if (policy.get())
        policy->setQueue(this);
}

const QueuePolicy* Queue::getPolicy()
{
    Mutex::ScopedLock locker(messageLock);
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
        : qpid::sys::TimerTask(fireTime, "DelayedAutoDeletion:"+q->getName()), broker(b), queue(q) {}

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

void Queue::countRejected() const
{
    if (mgmtObject) {
        mgmtObject->inc_discardsSubscriber();
        if (brokerMgmtObject)
            brokerMgmtObject->inc_discardsSubscriber();
    }
}

void Queue::countFlowedToDisk(uint64_t size) const
{
    if (mgmtObject) {
        _qmf::Queue::PerThreadStats *qStats = mgmtObject->getStatistics();
        qStats->msgFtdEnqueues += 1;
        qStats->byteFtdEnqueues += size;
        mgmtObject->statisticsUpdated();
        if (brokerMgmtObject) {
            _qmf::Broker::PerThreadStats *bStats = brokerMgmtObject->getStatistics();
            bStats->msgFtdEnqueues += 1;
            bStats->byteFtdEnqueues += size;
            brokerMgmtObject->statisticsUpdated();
        }
    }
}

void Queue::countLoadedFromDisk(uint64_t size) const
{
    if (mgmtObject) {
        _qmf::Queue::PerThreadStats *qStats = mgmtObject->getStatistics();
        qStats->msgFtdDequeues += 1;
        qStats->byteFtdDequeues += size;
        mgmtObject->statisticsUpdated();
        if (brokerMgmtObject) {
            _qmf::Broker::PerThreadStats *bStats = brokerMgmtObject->getStatistics();
            bStats->msgFtdDequeues += 1;
            bStats->byteFtdDequeues += size;
            brokerMgmtObject->statisticsUpdated();
        }
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
    if (allocator) allocator->query(results);
}

void Queue::setPosition(SequenceNumber n) {
    Mutex::ScopedLock locker(messageLock);
    sequence = n;
    QPID_LOG(trace, "Set position to " << sequence << " on " << getName());
}

SequenceNumber Queue::getPosition() {
    return sequence;
}

int Queue::getEventMode() { return eventMode; }

void Queue::recoveryComplete(ExchangeRegistry& exchanges)
{
    // set the alternate exchange
    if (!alternateExchangeName.empty()) {
        Exchange::shared_ptr ae = exchanges.find(alternateExchangeName);
        if (ae) setAlternateExchange(ae);
        else QPID_LOG(warning, "Could not set alternate exchange \""
                      << alternateExchangeName << "\" on queue \"" << name
                      << "\": exchange does not exist.");
    }
    //process any pending dequeues
    std::deque<QueuedMessage> pd;
    {
        Mutex::ScopedLock locker(messageLock);
        pendingDequeues.swap(pd);
    }
    for_each(pd.begin(), pd.end(), boost::bind(&Queue::dequeue, this, (TransactionContext*) 0, _1));
}

void Queue::insertSequenceNumbers(const std::string& key)
{
    seqNoKey = key;
    insertSeqNo = !seqNoKey.empty();
    QPID_LOG(debug, "Inserting sequence numbers as " << key);
}

/** updates queue observers and state when a message has become available for transfer
 *  Requires messageLock be held by caller.
 */
void Queue::observeEnqueue(const QueuedMessage& m, const qpid::sys::Mutex::ScopedLock&)
{
    for (Observers::iterator i = observers.begin(); i != observers.end(); ++i) {
        try {
            (*i)->enqueued(m);
        } catch (const std::exception& e) {
            QPID_LOG(warning, "Exception on notification of enqueue for queue " << getName() << ": " << e.what());
        }
    }
}

void Queue::updateEnqueued(const QueuedMessage& m)
{
    if (m.payload) {
        boost::intrusive_ptr<Message> payload = m.payload;
        enqueue(0, payload, true);
        {
            Mutex::ScopedLock locker(messageLock);
            messages->updateAcquired(m);
            observeEnqueue(m, locker);
            if (policy.get()) {
                policy->recoverEnqueued(payload);
                policy->enqueued(m);
            }
        }
        mgntEnqStats(m.payload, mgmtObject, brokerMgmtObject);
    } else {
        QPID_LOG(warning, "Queue informed of enqueued message that has no payload");
    }
}

bool Queue::isEnqueued(const QueuedMessage& msg)
{
    Mutex::ScopedLock locker(messageLock);
    return !policy.get() || policy->isEnqueued(msg);
}

// Note: accessing listeners outside of lock is dangerous.  Caller must ensure the queue's
// state is not changed while listeners is referenced.
QueueListeners& Queue::getListeners() { return listeners; }

// Note: accessing messages outside of lock is dangerous.  Caller must ensure the queue's
// state is not changed while messages is referenced.
Messages& Queue::getMessages() { return *messages; }
const Messages& Queue::getMessages() const { return *messages; }

void Queue::checkNotDeleted(const Consumer::shared_ptr& c)
{
    if (deleted && !c->hideDeletedError()) {
        throw ResourceDeletedException(QPID_MSG("Queue " << getName() << " has been deleted."));
    }
}

void Queue::addObserver(boost::shared_ptr<QueueObserver> observer)
{
    Mutex::ScopedLock lock(messageLock);
    observers.insert(observer);
}

void Queue::removeObserver(boost::shared_ptr<QueueObserver> observer)
{
    Mutex::ScopedLock lock(messageLock);
    observers.erase(observer);
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


Broker* Queue::getBroker()
{
    return broker;
}

void Queue::setDequeueSincePurge(uint32_t value) {
    dequeueSincePurge = value;
}

namespace{
class FindLowest
{
  public:
    FindLowest() : init(false) {}
    void process(const QueuedMessage& message) {
        QPID_LOG(debug, "FindLowest processing: " << message.position);
        if (!init || message.position < lowest) lowest = message.position;
        init = true;
    }
    bool getLowest(qpid::framing::SequenceNumber& result) {
        if (init) {
            result = lowest;
            return true;
        } else {
            return false;
        }
    }
  private:
    bool init;
    qpid::framing::SequenceNumber lowest;
};
}

Queue::UsageBarrier::UsageBarrier(Queue& q) : parent(q), count(0) {}

bool Queue::UsageBarrier::acquire()
{
    Monitor::ScopedLock l(parent.messageLock);  /** @todo: use a dedicated lock instead of messageLock */
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

}}

