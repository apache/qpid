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
#include "qpid/broker/QueueCursor.h"
#include "qpid/broker/QueueDepth.h"
#include "qpid/broker/QueueSettings.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/DeliverableMessage.h"
#include "qpid/broker/MessageStore.h"
#include "qpid/broker/MessageDeque.h"
#include "qpid/broker/MessageDistributor.h"
#include "qpid/broker/FifoDistributor.h"
#include "qpid/broker/NullMessageStore.h"
#include "qpid/broker/QueueRegistry.h"

//TODO: get rid of this
#include "qpid/broker/amqp_0_10/MessageTransfer.h"

#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/StringUtils.h"
#include "qpid/log/Statement.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Time.h"
#include "qpid/sys/Timer.h"
#include "qpid/types/Variant.h"
#include "qmf/org/apache/qpid/broker/ArgsQueuePurge.h"
#include "qmf/org/apache/qpid/broker/ArgsQueueReroute.h"
#include "qmf/org/apache/qpid/broker/EventQueueDelete.h"

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
using std::string;
using std::for_each;
using std::mem_fun;
namespace _qmf = qmf::org::apache::qpid::broker;


namespace
{

inline void mgntEnqStats(const Message& msg,
			 _qmf::Queue::shared_ptr mgmtObject,
			 _qmf::Broker::shared_ptr brokerMgmtObject)
{
    if (mgmtObject != 0) {
        _qmf::Queue::PerThreadStats *qStats = mgmtObject->getStatistics();
        _qmf::Broker::PerThreadStats *bStats = brokerMgmtObject->getStatistics();

        uint64_t contentSize = msg.getContentSize();
        qStats->msgTotalEnqueues +=1;
        bStats->msgTotalEnqueues += 1;
        qStats->byteTotalEnqueues += contentSize;
        bStats->byteTotalEnqueues += contentSize;
        if (msg.isPersistent ()) {
            qStats->msgPersistEnqueues += 1;
            bStats->msgPersistEnqueues += 1;
            qStats->bytePersistEnqueues += contentSize;
            bStats->bytePersistEnqueues += contentSize;
        }
        mgmtObject->statisticsUpdated();
        brokerMgmtObject->statisticsUpdated();
    }
}

inline void mgntDeqStats(const Message& msg,
			 _qmf::Queue::shared_ptr mgmtObject,
			 _qmf::Broker::shared_ptr brokerMgmtObject)
{
    if (mgmtObject != 0){
        _qmf::Queue::PerThreadStats *qStats = mgmtObject->getStatistics();
        _qmf::Broker::PerThreadStats *bStats = brokerMgmtObject->getStatistics();
        uint64_t contentSize = msg.getContentSize();

        qStats->msgTotalDequeues += 1;
        bStats->msgTotalDequeues += 1;
        qStats->byteTotalDequeues += contentSize;
        bStats->byteTotalDequeues += contentSize;
        if (msg.isPersistent ()){
            qStats->msgPersistDequeues += 1;
            bStats->msgPersistDequeues += 1;
            qStats->bytePersistDequeues += contentSize;
            bStats->bytePersistDequeues += contentSize;
        }
        mgmtObject->statisticsUpdated();
        brokerMgmtObject->statisticsUpdated();
    }
}

QueueSettings merge(const QueueSettings& inputs, const Broker::Options& globalOptions)
{
    QueueSettings settings(inputs);
    if (!settings.maxDepth.hasSize() && globalOptions.queueLimit) {
        settings.maxDepth.setSize(globalOptions.queueLimit);
    }
    return settings;
}

}

Queue::TxPublish::TxPublish(const Message& m, boost::shared_ptr<Queue> q) : message(m), queue(q), prepared(false) {}
bool Queue::TxPublish::prepare(TransactionContext* ctxt) throw()
{
    try {
        prepared = queue->enqueue(ctxt, message);
        return true;
    } catch (const std::exception& e) {
        QPID_LOG(error, "Failed to prepare: " << e.what());
        return false;
    }
}
void Queue::TxPublish::commit() throw()
{
    try {
        if (prepared) queue->process(message);
    } catch (const std::exception& e) {
        QPID_LOG(error, "Failed to commit: " << e.what());
    }
}
void Queue::TxPublish::rollback() throw()
{
    try {
        if (prepared) queue->enqueueAborted(message);
    } catch (const std::exception& e) {
        QPID_LOG(error, "Failed to rollback: " << e.what());
    }
}

Queue::Queue(const string& _name, const QueueSettings& _settings,
             MessageStore* const _store,
             Manageable* parent,
             Broker* b) :

    name(_name),
    store(_store),
    owner(0),
    consumerCount(0),
    browserCount(0),
    exclusive(0),
    persistLastNode(false),
    inLastNodeFailure(false),
    messages(new MessageDeque()),
    persistenceId(0),
    settings(b ? merge(_settings, b->getOptions()) : _settings),
    eventMode(0),
    broker(b),
    deleted(false),
    barrier(*this),
    allocator(new FifoDistributor( *messages ))
{
    if (settings.maxDepth.hasCount()) current.setCount(0);
    if (settings.maxDepth.hasSize()) current.setSize(0);
    if (settings.traceExcludes.size()) {
        split(traceExclude, settings.traceExcludes, ", ");
    }
    qpid::amqp_0_10::translate(settings.asMap(), encodableSettings);
    if (parent != 0 && broker != 0) {
        ManagementAgent* agent = broker->getManagementAgent();
        if (agent != 0) {
            mgmtObject = _qmf::Queue::shared_ptr(
                new _qmf::Queue(agent, this, parent, _name, _store != 0, settings.autodelete));
            mgmtObject->set_arguments(settings.asMap());
            agent->addObject(mgmtObject, 0, store != 0);
            brokerMgmtObject = boost::dynamic_pointer_cast<_qmf::Broker>(broker->GetManagementObject());
            if (brokerMgmtObject)
                brokerMgmtObject->inc_queueCount();
        }
    }

    if ( settings.isBrowseOnly ) {
        QPID_LOG ( info, "Queue " << name << " is browse-only." );
    }
}

Queue::~Queue()
{
}

bool isLocalTo(const OwnershipToken* token, const Message& msg)
{
    return token && token->isLocal(msg.getPublisher());
}

bool Queue::isLocal(const Message& msg)
{
    //message is considered local if it was published on the same
    //connection as that of the session which declared this queue
    //exclusive (owner) or which has an exclusive subscription
    //(exclusive)
    return settings.noLocal && (isLocalTo(owner, msg) || isLocalTo(exclusive, msg));
}

bool Queue::isExcluded(const Message& msg)
{
    return traceExclude.size() && msg.isExcluded(traceExclude);
}

void Queue::deliver(Message msg, TxBuffer* txn){
    //TODO: move some of this out of the queue and into the publishing
    //'link' for whatever protocol is used; that would let protocol
    //specific stuff be kept out the queue

    if (broker::amqp_0_10::MessageTransfer::isImmediateDeliveryRequired(msg) && getConsumerCount() == 0) {
        if (alternateExchange) {
            DeliverableMessage deliverable(msg, 0);
            alternateExchange->route(deliverable);
        }
    } else if (isLocal(msg)) {
        //drop message
        QPID_LOG(info, "Dropping 'local' message from " << getName());
    } else if (isExcluded(msg)) {
        //drop message
        QPID_LOG(info, "Dropping excluded message from " << getName());
    } else {
        if (txn) {
            TxOp::shared_ptr op(new TxPublish(msg, shared_from_this()));
            txn->enlist(op);
        } else {
            if (enqueue(0, msg)) {
                push(msg);
                QPID_LOG(debug, "Message " << msg << " enqueued on " << name);
            } else {
                QPID_LOG(debug, "Message " << msg << " dropped from " << name);
            }
        }
    }
}

void Queue::recoverPrepared(const Message& msg)
{
    Mutex::ScopedLock locker(messageLock);
    current += QueueDepth(1, msg.getContentSize());
}

void Queue::recover(Message& msg)
{
    recoverPrepared(msg);
    push(msg, true);
}

void Queue::process(Message& msg)
{
    push(msg);
    if (mgmtObject != 0){
        _qmf::Queue::PerThreadStats *qStats = mgmtObject->getStatistics();
        const uint64_t contentSize = msg.getContentSize();
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

void Queue::release(const QueueCursor& position, bool markRedelivered)
{
    QueueListeners::NotificationSet copy;
    {
        Mutex::ScopedLock locker(messageLock);
        if (!deleted) {
            Message* message = messages->release(position);
            if (message) {
                if (!markRedelivered) message->undeliver();
                listeners.populate(copy);
                observeRequeue(*message, locker);
                if (mgmtObject) {
                    mgmtObject->inc_releases();
                    if (brokerMgmtObject)
                        brokerMgmtObject->inc_releases();
                }
            }
        }
    }
    copy.notify();
}

bool Queue::dequeueMessageAt(const SequenceNumber& position)
{
    boost::intrusive_ptr<PersistableMessage> pmsg;
    {
        Mutex::ScopedLock locker(messageLock);
        QPID_LOG(debug, "Attempting to dequeue message at " << position);
        QueueCursor cursor;
        Message* msg = messages->find(position, &cursor);
        if (msg) {
            if (msg->isPersistent()) pmsg = msg->getPersistentContext();
            observeDequeue(*msg, locker);
            messages->deleted(cursor);
        } else {
            QPID_LOG(debug, "Could not dequeue message at " << position << "; no such message");
            return false;
        }
    }
    dequeueFromStore(pmsg);
    return true;
}

bool Queue::acquire(const QueueCursor& position, const std::string& consumer)
{
    Mutex::ScopedLock locker(messageLock);
    Message* msg;

    msg = messages->find(position);
    if (msg) {
        QPID_LOG(debug, consumer << " attempting to acquire message at " << msg->getSequence());
        if (!allocator->acquire(consumer, *msg)) {
            QPID_LOG(debug, "Not permitted to acquire msg at " << msg->getSequence() << " from '" << name);
            return false;
        } else {
            observeAcquire(*msg, locker);
            QPID_LOG(debug, "Acquired message at " << msg->getSequence() << " from " << name);
            return true;
        }
    } else {
        QPID_LOG(debug, "Failed to acquire message which no longer exists on " << name);
        return false;
    }
}

bool Queue::getNextMessage(Message& m, Consumer::shared_ptr& c)
{
    if (!checkNotDeleted(c)) return false;
    QueueListeners::NotificationSet set;
    while (true) {
        //TODO: reduce lock scope
        Mutex::ScopedLock locker(messageLock);
        QueueCursor cursor = c->getCursor(); // Save current position.
        Message* msg = messages->next(*c);   // Advances c.
        if (msg) {
            if (msg->hasExpired()) {
                QPID_LOG(debug, "Message expired from queue '" << name << "'");
                observeDequeue(*msg, locker);
                //ERROR: don't hold lock across call to store!!
                if (msg->isPersistent()) dequeueFromStore(msg->getPersistentContext());
                if (mgmtObject) {
                    mgmtObject->inc_discardsTtl();
                    if (brokerMgmtObject)
                        brokerMgmtObject->inc_discardsTtl();
                }
                messages->deleted(*c);
                continue;
            }

            if (c->filter(*msg)) {
                if (c->accept(*msg)) {
                    if (c->preAcquires()) {
                        QPID_LOG(debug, "Attempting to acquire message " << msg << " from '" << name << "' with state " << msg->getState());
                        if (allocator->acquire(c->getName(), *msg)) {
                            if (mgmtObject) {
                                mgmtObject->inc_acquires();
                                if (brokerMgmtObject)
                                    brokerMgmtObject->inc_acquires();
                            }
                            observeAcquire(*msg, locker);
                            msg->deliver();
                        } else {
                            QPID_LOG(debug, "Could not acquire message from '" << name << "'");
                            continue; //try another message
                        }
                    }
                    QPID_LOG(debug, "Message retrieved from '" << name << "'");
                    m = *msg;
                    return true;
                } else {
                    //message(s) are available but consumer hasn't got enough credit
                    QPID_LOG(debug, "Consumer can't currently accept message from '" << name << "'");
                    c->setCursor(cursor); // Restore cursor, will try again with credit
                    if (c->preAcquires()) {
                        //let someone else try
                        listeners.populate(set);
                    }
                    break;
                }
            } else {
                //consumer will never want this message, try another one
                QPID_LOG(debug, "Consumer doesn't want message from '" << name << "'");
                if (c->preAcquires()) {
                    //let someone else try to take this one
                    listeners.populate(set);
                }
            }
        } else {
            QPID_LOG(debug, "No messages to dispatch on queue '" << name << "'");
            listeners.addListener(c);
            return false;
        }
    }
    set.notify();
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
    Message msg;
    if (getNextMessage(msg, c)) {
        c->deliver(*c, msg);
        return true;
    } else {
        return false;
    }
}

bool Queue::find(SequenceNumber pos, Message& msg) const
{
    Mutex::ScopedLock locker(messageLock);
    Message* ptr = messages->find(pos, 0);
    if (ptr) {
        msg = *ptr;
        return true;
    }
    return false;
}

void Queue::consume(Consumer::shared_ptr c, bool requestExclusive)
{
    {
        Mutex::ScopedLock locker(messageLock);
        // NOTE: consumerCount is actually a count of all
        // subscriptions, both acquiring and non-acquiring (browsers).
        // Check for exclusivity of acquiring consumers.
        size_t acquiringConsumers = consumerCount - browserCount;
        if (c->preAcquires()) {
            if(settings.isBrowseOnly) {
                throw NotAllowedException(
                    QPID_MSG("Queue " << name << " is browse only.  Refusing acquiring consumer."));
            }

            if(exclusive) {
                throw ResourceLockedException(
                    QPID_MSG("Queue " << getName()
                             << " has an exclusive consumer. No more consumers allowed."));
            } else if(requestExclusive) {
                if(acquiringConsumers) {
                    throw ResourceLockedException(
                        QPID_MSG("Queue " << getName()
                                 << " already has consumers. Exclusive access denied."));
                } else {
                    exclusive = c->getSession();
                }
            }
        }
        else if(c->isCounted()) {
            browserCount++;
        }
        if(c->isCounted()) {
            consumerCount++;

            //reset auto deletion timer if necessary
            if (settings.autoDeleteDelay && autoDeleteTask) {
                autoDeleteTask->cancel();
            }

            observeConsumerAdd(*c, locker);
        }
    }
    if (mgmtObject != 0 && c->isCounted()) {
        mgmtObject->inc_consumerCount();
    }
}

void Queue::cancel(Consumer::shared_ptr c)
{
    removeListener(c);
    if(c->isCounted())
    {
        Mutex::ScopedLock locker(messageLock);
        consumerCount--;
        if (!c->preAcquires()) browserCount--;
        if(exclusive) exclusive = 0;
        observeConsumerRemove(*c, locker);
    }
    if (mgmtObject != 0 && c->isCounted()) {
        mgmtObject->dec_consumerCount();
    }
}

/**
 *@param lapse: time since the last purgeExpired
 */
void Queue::purgeExpired(sys::Duration lapse) {
    //As expired messages are discarded during dequeue also, only
    //bother explicitly expiring if the rate of dequeues since last
    //attempt is less than one per second.
    int count = dequeueSincePurge.get();
    dequeueSincePurge -= count;
    int seconds = int64_t(lapse)/qpid::sys::TIME_SEC;
    if (seconds == 0 || count / seconds < 1) {
        uint32_t count = remove(0, boost::bind(&Message::hasExpired, _1), 0, CONSUMER);
        QPID_LOG(debug, "Purged " << count << " expired messages from " << getName());
        //
        // Report the count of discarded-by-ttl messages
        //
        if (mgmtObject && count) {
            mgmtObject->inc_acquires(count);
            mgmtObject->inc_discardsTtl(count);
            if (brokerMgmtObject) {
                brokerMgmtObject->inc_acquires(count);
                brokerMgmtObject->inc_discardsTtl(count);
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
        virtual bool match( const Message& ) const { return true; }
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
        bool match( const Message& msg ) const
        {
            return msg.getPropertyAsString(header) == value;
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

    bool reroute(boost::shared_ptr<Exchange> e, const Message& m)
    {
        if (e) {
            DeliverableMessage d(m, 0);
            d.getMessage().clearTrace();
            e->routeWithAlternate(d);
            return true;
        } else {
            return false;
        }
    }
    void moveTo(boost::shared_ptr<Queue> q, Message& m)
    {
        if (q) {
            q->deliver(m);
        }
    }
} // end namespace

uint32_t Queue::remove(const uint32_t maxCount, MessagePredicate p, MessageFunctor f, SubscriptionType type)
{
    std::deque<Message> removed;
    {
        QueueCursor c(type);
        uint32_t count(0);
        Mutex::ScopedLock locker(messageLock);
        Message* m = messages->next(c);
        while (m){
            if (!p || p(*m)) {
                if (!maxCount || count++ < maxCount) {
                    if (m->getState() == AVAILABLE) {
                        //don't actually acquire, just act as if we did
                        observeAcquire(*m, locker);
                    }
                    observeDequeue(*m, locker);
                    removed.push_back(*m);//takes a copy of the message
                    if (!messages->deleted(c)) {
                        QPID_LOG(warning, "Failed to correctly remove message from " << name << "; state is not consistent!");
                        assert(false);
                    }
                } else {
                    break;
                }
            }
            m = messages->next(c);
        }
    }
    for (std::deque<Message>::iterator i = removed.begin(); i != removed.end(); ++i) {
        if (f) f(*i);//ERROR? need to clear old persistent context?
        if (i->isPersistent()) dequeueFromStore(i->getPersistentContext());//do this outside of lock and after any re-routing
    }
    return removed.size();
}


/**
 * purge - for purging all or some messages on a queue
 *         depending on the purge_request
 *
 * qty == 0 then purge all messages
 *     == N then purge N messages from queue
 * Sometimes qty == 1 to unblock the top of queue
 *
 * The dest exchange may be supplied to re-route messages through the exchange.
 * It is safe to re-route messages such that they arrive back on the same queue,
 * even if the queue is ordered by priority.
 *
 * An optional filter can be supplied that will be applied against each message.  The
 * message is purged only if the filter matches.  See MessageDistributor for more detail.
 */
uint32_t Queue::purge(const uint32_t qty, boost::shared_ptr<Exchange> dest,
                      const qpid::types::Variant::Map *filter)
{
    std::auto_ptr<MessageFilter> mf(MessageFilter::create(filter));
    uint32_t count = remove(qty, boost::bind(&MessageFilter::match, mf.get(), _1), boost::bind(&reroute, dest, _1), CONSUMER/*?*/);

    if (mgmtObject && count) {
        mgmtObject->inc_acquires(count);
        if (dest.get()) {
            mgmtObject->inc_reroutes(count);
            if (brokerMgmtObject) {
                brokerMgmtObject->inc_acquires(count);
                brokerMgmtObject->inc_reroutes(count);
            }
        } else {
            mgmtObject->inc_discardsPurge(count);
            if (brokerMgmtObject) {
                brokerMgmtObject->inc_acquires(count);
                brokerMgmtObject->inc_discardsPurge(count);
            }
        }
    }

    return count;
}

uint32_t Queue::move(const Queue::shared_ptr destq, uint32_t qty,
                     const qpid::types::Variant::Map *filter)
{
    std::auto_ptr<MessageFilter> mf(MessageFilter::create(filter));
    return remove(qty, boost::bind(&MessageFilter::match, mf.get(), _1), boost::bind(&moveTo, destq, _1), CONSUMER/*?*/);
}

void Queue::push(Message& message, bool /*isRecovery*/)
{
    QueueListeners::NotificationSet copy;
    {
        Mutex::ScopedLock locker(messageLock);
        message.setSequence(++sequence);
        messages->publish(message);
        listeners.populate(copy);
        observeEnqueue(message, locker);
    }
    copy.notify();
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
    return settings.autodelete && !consumerCount && !owner;
}

void Queue::clearLastNodeFailure()
{
    inLastNodeFailure = false;
}

void Queue::forcePersistent(const Message& /*message*/)
{
    //TODO
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


/*
 * return true if enqueue succeeded and message should be made
 * available; returning false will result in the message being dropped
 */
bool Queue::enqueue(TransactionContext* ctxt, Message& msg)
{
    ScopedUse u(barrier);
    if (!u.acquired) return false;

    {
        Mutex::ScopedLock locker(messageLock);
        if (!checkDepth(QueueDepth(1, msg.getContentSize()), msg)) {
            return false;
        }
    }

    if (inLastNodeFailure && persistLastNode){
        forcePersistent(msg);
    }

    if (settings.traceId.size()) {
        msg.addTraceId(settings.traceId);
    }

    if (msg.isPersistent() && store) {
        // mark the message as being enqueued - the store MUST CALL msg->enqueueComplete()
        // when it considers the message stored.
        boost::intrusive_ptr<PersistableMessage> pmsg = msg.getPersistentContext();
        assert(pmsg);
        pmsg->enqueueAsync(shared_from_this(), store);
        store->enqueue(ctxt, pmsg, *this);
    }
    return true;
}

void Queue::enqueueAborted(const Message& msg)
{
    //Called when any transactional enqueue is aborted (including but
    //not limited to a recovered dtx transaction)
    Mutex::ScopedLock locker(messageLock);
    current -= QueueDepth(1, msg.getContentSize());
}

void Queue::enqueueCommited(Message& msg)
{
    //called when a recovered dtx enqueue operation is committed; the
    //message is already on disk and space has been reserved in policy
    //but it should now be made available
    process(msg);
}
void Queue::dequeueAborted(Message& msg)
{
    //called when a recovered dtx dequeue operation is aborted; the
    //message should be added back to the queue
    push(msg);
}
void Queue::dequeueCommited(const Message& msg)
{
    //called when a recovered dtx dequeue operation is committed; the
    //message will at this point have already been removed from the
    //store and will not be available for delivery. The only action
    //required is to ensure the observers are notified and the
    //management stats are correctly decremented
    Mutex::ScopedLock locker(messageLock);
    observeDequeue(msg, locker);
    if (mgmtObject != 0) {
        mgmtObject->inc_msgTxnDequeues();
        mgmtObject->inc_byteTxnDequeues(msg.getContentSize());
    }
}


void Queue::dequeueFromStore(boost::intrusive_ptr<PersistableMessage> msg)
{
    ScopedUse u(barrier);
    if (u.acquired && msg && store) {
        store->dequeue(0, msg, *this);
    }
}

void Queue::dequeue(TransactionContext* ctxt, const QueueCursor& cursor)
{
    ScopedUse u(barrier);
    if (!u.acquired) return;
    boost::intrusive_ptr<PersistableMessage> pmsg;
    {
        Mutex::ScopedLock locker(messageLock);
        Message* msg = messages->find(cursor);
        if (msg) {
            if (msg->isPersistent()) pmsg = msg->getPersistentContext();
            if (!ctxt) {
                observeDequeue(*msg, locker);
                messages->deleted(cursor);//message pointer not valid after this
            }
        } else {
            return;
        }
    }
    if (store && pmsg) {
        store->dequeue(ctxt, pmsg, *this);
    }
}

void Queue::dequeueCommitted(const QueueCursor& cursor)
{
    Mutex::ScopedLock locker(messageLock);
    Message* msg = messages->find(cursor);
    if (msg) {
        const uint64_t contentSize = msg->getContentSize();
        observeDequeue(*msg, locker);
        if (mgmtObject != 0) {
            mgmtObject->inc_msgTxnDequeues();
            mgmtObject->inc_byteTxnDequeues(contentSize);
        }
        if (brokerMgmtObject) {
            _qmf::Broker::PerThreadStats *bStats = brokerMgmtObject->getStatistics();
            bStats->msgTxnDequeues += 1;
            bStats->byteTxnDequeues += contentSize;
            brokerMgmtObject->statisticsUpdated();
        }
        messages->deleted(cursor);
    } else {
        QPID_LOG(error, "Could not find dequeued message on commit");
    }
}

/**
 * Updates policy and management when a message has been dequeued,
 * Requires messageLock be held by caller.
 */
void Queue::observeDequeue(const Message& msg, const Mutex::ScopedLock&)
{
    current -= QueueDepth(1, msg.getContentSize());
    mgntDeqStats(msg, mgmtObject, brokerMgmtObject);
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
void Queue::observeAcquire(const Message& msg, const Mutex::ScopedLock&)
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
void Queue::observeRequeue(const Message& msg, const Mutex::ScopedLock&)
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


void Queue::create()
{
    if (store) {
        store->create(*this, settings.storeSettings);
    }
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

void Queue::abandoned(const Message& message)
{
    if (reroute(alternateExchange, message) && brokerMgmtObject)
        brokerMgmtObject->inc_abandonedViaAlt();
    else if (brokerMgmtObject)
        brokerMgmtObject->inc_abandoned();
}

void Queue::destroyed()
{
    unbind(broker->getExchanges());
    remove(0, 0, boost::bind(&Queue::abandoned, this, _1), REPLICATOR/*even acquired message are treated as abandoned*/);
    if (alternateExchange.get()) {
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
    {
        Mutex::ScopedLock lock(messageLock);
        for_each(observers.begin(), observers.end(),
                 boost::bind(&QueueObserver::destroy, _1));
        observers.clear();
    }

    if (mgmtObject != 0) {
        mgmtObject->resourceDestroy();
        if (brokerMgmtObject)
            brokerMgmtObject->dec_queueCount();
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

uint64_t Queue::getPersistenceId() const
{
    return persistenceId;
}

void Queue::setPersistenceId(uint64_t _persistenceId) const
{
    if (mgmtObject != 0 && persistenceId == 0 && externalQueueStore)
    {
        ManagementObject::shared_ptr childObj = externalQueueStore->GetManagementObject();
        if (childObj != 0)
            childObj->setReference(mgmtObject->getObjectId());
    }
    persistenceId = _persistenceId;
}

void Queue::encode(Buffer& buffer) const
{
    buffer.putShortString(name);
    buffer.put(encodableSettings);
    buffer.putShortString(alternateExchange.get() ? alternateExchange->getName() : std::string(""));
}

uint32_t Queue::encodedSize() const
{
    return name.size() + 1/*short string size octet*/
        + (alternateExchange.get() ? alternateExchange->getName().size() : 0) + 1 /* short string */
        + encodableSettings.encodedSize();
}

Queue::shared_ptr Queue::restore( QueueRegistry& queues, Buffer& buffer )
{
    string name;
    buffer.getShortString(name);
    FieldTable ft;
    buffer.get(ft);
    boost::shared_ptr<Exchange> alternate;
    QueueSettings settings(true, false);
    settings.populate(ft, settings.storeSettings);
    std::pair<Queue::shared_ptr, bool> result = queues.declare(name, settings, alternate, true);
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
    alternateExchange->incAlternateUsers();
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

void tryAutoDeleteImpl(Broker& broker, Queue::shared_ptr queue, const std::string& connectionId, const std::string& userId)
{
    if (broker.getQueues().destroyIf(queue->getName(),
                                     boost::bind(boost::mem_fn(&Queue::canAutoDelete), queue))) {
        QPID_LOG_CAT(debug, model, "Auto-delete queue: " << queue->getName()
            << " user:" << userId
            << " rhost:" << connectionId );
        queue->destroyed();
    }
}

struct AutoDeleteTask : qpid::sys::TimerTask
{
    Broker& broker;
    Queue::shared_ptr queue;
    std::string connectionId;
    std::string userId;

    AutoDeleteTask(Broker& b, Queue::shared_ptr q, const std::string& cId, const std::string& uId, AbsTime fireTime)
        : qpid::sys::TimerTask(fireTime, "DelayedAutoDeletion:"+q->getName()), broker(b), queue(q), connectionId(cId), userId(uId) {}

    void fire()
    {
        //need to detect case where queue was used after the task was
        //created, but then became unused again before the task fired;
        //in this case ignore this request as there will have already
        //been a later task added
        tryAutoDeleteImpl(broker, queue, connectionId, userId);
    }
};

void Queue::tryAutoDelete(Broker& broker, Queue::shared_ptr queue, const std::string& connectionId, const std::string& userId)
{
    if (queue->settings.autoDeleteDelay && queue->canAutoDelete()) {
        AbsTime time(now(), Duration(queue->settings.autoDeleteDelay * TIME_SEC));
        queue->autoDeleteTask = boost::intrusive_ptr<qpid::sys::TimerTask>(new AutoDeleteTask(broker, queue, connectionId, userId, time));
        broker.getTimer().add(queue->autoDeleteTask);
        QPID_LOG(debug, "Timed auto-delete for " << queue->getName() << " initiated");
    } else {
        tryAutoDeleteImpl(broker, queue, connectionId, userId);
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
    if (mgmtObject) {
        mgmtObject->set_exclusive(false);
    }
}

bool Queue::setExclusiveOwner(const OwnershipToken* const o)
{
    //reset auto deletion timer if necessary
    if (settings.autoDeleteDelay && autoDeleteTask) {
        autoDeleteTask->cancel();
    }
    Mutex::ScopedLock locker(ownershipLock);
    if (owner) {
        return false;
    } else {
        owner = o;
        if (mgmtObject) {
            mgmtObject->set_exclusive(true);
        }
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
        ManagementObject::shared_ptr childObj = inst->GetManagementObject();
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


ManagementObject::shared_ptr Queue::GetManagementObject(void) const
{
    return mgmtObject;
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
            if (rerouteArgs.i_useAltExchange) {
                if (!alternateExchange) {
                    status = Manageable::STATUS_PARAMETER_INVALID;
                    etext = "No alternate-exchange defined";
                    break;
                }
                dest = alternateExchange;
            } else {
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

namespace {
struct After {
    framing::SequenceNumber seq;
    After(framing::SequenceNumber s) : seq(s) {}
    bool operator()(const Message& m) { return m.getSequence() > seq; }
};
} // namespace


void Queue::setPosition(SequenceNumber n) {
    Mutex::ScopedLock locker(messageLock);
    if (n < sequence) {
        remove(0, After(n), MessagePredicate(), BROWSER);
    }
    sequence = n;
    QPID_LOG(debug, "Set position to " << sequence << " on " << getName());
}

SequenceNumber Queue::getPosition() {
    Mutex::ScopedLock locker(messageLock);
    return sequence;
}

void Queue::getRange(framing::SequenceNumber& front, framing::SequenceNumber& back,
                     SubscriptionType type)
{
    Mutex::ScopedLock locker(messageLock);
    QueueCursor cursor(type);
    back = sequence;
    Message* message = messages->next(cursor);
    front = message ? message->getSequence() : back+1;
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
    for (std::vector<Message>::iterator i = pendingDequeues.begin(); i != pendingDequeues.end(); ++i) {
        dequeueFromStore(i->getPersistentContext());
    }
    pendingDequeues.clear();
}

/** updates queue observers and state when a message has become available for transfer
 *  Requires messageLock be held by caller.
 */
void Queue::observeEnqueue(const Message& m, const Mutex::ScopedLock&)
{
    for (Observers::iterator i = observers.begin(); i != observers.end(); ++i) {
        try {
            (*i)->enqueued(m);
        } catch (const std::exception& e) {
            QPID_LOG(warning, "Exception on notification of enqueue for queue " << getName() << ": " << e.what());
        }
    }
    mgntEnqStats(m, mgmtObject, brokerMgmtObject);
}

bool Queue::checkNotDeleted(const Consumer::shared_ptr& c)
{
    if (deleted && !c->hideDeletedError())
        throw ResourceDeletedException(QPID_MSG("Queue " << getName() << " has been deleted."));
    return !deleted;
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

void Queue::reject(const QueueCursor& cursor)
{
    Exchange::shared_ptr alternate = getAlternateExchange();
    Message copy;
    boost::intrusive_ptr<PersistableMessage> pmsg;
    {
        Mutex::ScopedLock locker(messageLock);
        Message* message = messages->find(cursor);
        if (message) {
            if (alternate) copy = *message;
            if (message->isPersistent()) pmsg = message->getPersistentContext();
            countRejected();
            observeDequeue(*message, locker);
            messages->deleted(cursor);
        } else {
            return;
        }
    }
    if (alternate) {
        copy.resetDeliveryCount();
        DeliverableMessage delivery(copy, 0);
        alternate->routeWithAlternate(delivery);
        QPID_LOG(info, "Routed rejected message from " << getName() << " to "
                 << alternate->getName());
    } else {
        //just drop it
        QPID_LOG(info, "Dropping rejected message from " << getName());
    }
    dequeueFromStore(pmsg);
}

bool Queue::checkDepth(const QueueDepth& increment, const Message&)
{
    if (current && (settings.maxDepth - current < increment)) {
        if (mgmtObject) {
            mgmtObject->inc_discardsOverflow();
            if (brokerMgmtObject)
                brokerMgmtObject->inc_discardsOverflow();
        }
        throw ResourceLimitExceededException(QPID_MSG("Maximum depth exceeded on " << name << ": current=[" << current << "], max=[" << settings.maxDepth << "]"));
    } else {
        current += increment;
        return true;
    }
}

bool Queue::seek(QueueCursor& cursor, MessagePredicate predicate)
{
    Mutex::ScopedLock locker(messageLock);
    //hold lock across calls to predicate, or take copy of message?
    //currently hold lock, may want to revise depending on any new use
    //cases
    Message* message = messages->next(cursor);
    while (message && (predicate && !predicate(*message))) {
        message = messages->next(cursor);
    }
    return message != 0;
}

bool Queue::seek(QueueCursor& cursor, MessagePredicate predicate, qpid::framing::SequenceNumber start)
{
    Mutex::ScopedLock locker(messageLock);
    //hold lock across calls to predicate, or take copy of message?
    //currently hold lock, may want to revise depending on any new use
    //cases
    Message* message;
    message = messages->find(start, &cursor);
    if (message && (!predicate || predicate(*message))) return true;

    return seek(cursor, predicate);
}

bool Queue::seek(QueueCursor& cursor, qpid::framing::SequenceNumber start)
{
    Mutex::ScopedLock locker(messageLock);
    return messages->find(start, &cursor);
}

Queue::UsageBarrier::UsageBarrier(Queue& q) : parent(q), count(0) {}

bool Queue::UsageBarrier::acquire()
{
    Monitor::ScopedLock l(usageLock);
    if (parent.deleted) {
        return false;
    } else {
        ++count;
        return true;
    }
}

void Queue::UsageBarrier::release()
{
    Monitor::ScopedLock l(usageLock);
    if (--count == 0) usageLock.notifyAll();
}

void Queue::UsageBarrier::destroy()
{
    Monitor::ScopedLock l(usageLock);
    parent.deleted = true;
    while (count) usageLock.wait();
}

void Queue::addArgument(const string& key, const types::Variant& value) {
    settings.original[key] = value;
    qpid::amqp_0_10::translate(settings.asMap(), encodableSettings);
    boost::shared_ptr<qpid::framing::FieldValue> v;
    qpid::amqp_0_10::translate(value, v);
    settings.storeSettings.set(key, v);
    if (mgmtObject != 0) mgmtObject->set_arguments(settings.asMap());
}

}}

