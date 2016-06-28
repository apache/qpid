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
#include "qpid/broker/Connection.h"
#include "qpid/broker/AclModule.h"
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
#include "qpid/broker/Selector.h"
#include "qpid/broker/TransactionObserver.h"
#include "qpid/broker/TxDequeue.h"

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
#include "qmf/org/apache/qpid/broker/EventSubscribe.h"
#include "qmf/org/apache/qpid/broker/EventUnsubscribe.h"

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
using qpid::management::getCurrentPublisher;
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

        uint64_t contentSize = msg.getMessageSize();
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
        uint64_t contentSize = msg.getMessageSize();

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

QueueSettings merge(const QueueSettings& inputs, const Broker& broker)
{
    QueueSettings settings(inputs);
    settings.maxDepth = QueueDepth();
    if (inputs.maxDepth.hasCount() && inputs.maxDepth.getCount()) {
        settings.maxDepth.setCount(inputs.maxDepth.getCount());
    }
    if (inputs.maxDepth.hasSize()) {
        if (inputs.maxDepth.getSize()) {
            settings.maxDepth.setSize(inputs.maxDepth.getSize());
        }
    } else if (broker.getQueueLimit()) {
        settings.maxDepth.setSize(broker.getQueueLimit());
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

void Queue::TxPublish::callObserver(
    const boost::shared_ptr<TransactionObserver>& observer)
{
    observer->enqueue(queue, message);
}

Queue::Queue(const string& _name, const QueueSettings& _settings,
             MessageStore* const _store,
             Manageable* parent,
             Broker* b) :

    name(_name),
    store(_store),
    owner(0),
    exclusive(0),
    messages(new MessageDeque()),
    persistenceId(0),
    settings(b ? merge(_settings, *b) : _settings),
    eventMode(0),
    observers(name, messageLock),
    broker(b),
    deleted(false),
    barrier(*this),
    allocator(new FifoDistributor( *messages )),
    redirectSource(false)
{
    current.setCount(0);//always track depth in messages
    if (settings.maxDepth.getSize()) current.setSize(0);//track depth in bytes only if policy requires it
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
    if (settings.filter.size()) {
        selector.reset(new Selector(settings.filter));
        QPID_LOG (info, "Queue " << name << " using filter: " << settings.filter);
    }
}

Queue::~Queue()
{
    if (mgmtObject != 0) {
        mgmtObject->debugStats("destroying");
        mgmtObject->resourceDestroy();
    }
}

bool Queue::isLocal(const Message& msg)
{
    //message is considered local if it was published on the same
    //connection as that of the session which declared this queue
    //exclusive (owner) or which has an exclusive subscription
    //(exclusive)
    return settings.noLocal && (msg.isLocalTo(owner) || msg.isLocalTo(exclusive));
}

bool Queue::isExcluded(const Message& msg)
{
    return traceExclude.size() && msg.isExcluded(traceExclude);
}

bool Queue::accept(const Message& msg)
{
    //TODO: move some of this out of the queue and into the publishing
    //'link' for whatever protocol is used; that would let protocol
    //specific stuff be kept out the queue
    if (broker::amqp_0_10::MessageTransfer::isImmediateDeliveryRequired(msg) && getConsumerCount() == 0) {
        if (alternateExchange) {
            DeliverableMessage deliverable(msg, 0);
            alternateExchange->route(deliverable);
        }
        return false;
    } else if (isLocal(msg)) {
        //drop message
        QPID_LOG(info, "Dropping 'local' message from " << getName());
        return false;
    } else if (isExcluded(msg)) {
        //drop message
        QPID_LOG(info, "Dropping excluded message from " << getName());
        return false;
    } else {
        messages->check(msg);
        if (selector) {
            return selector->filter(msg);
        } else {
            return true;
        }
    }
}

void Queue::deliver(Message msg, TxBuffer* txn)
{
    if (redirectPeer) {
        redirectPeer->deliverTo(msg, txn);
    } else {
        deliverTo(msg, txn);
    }
}

void Queue::deliverTo(Message msg, TxBuffer* txn)
{
    if (accept(msg)) {
        interceptors.record(msg);
        if (txn) {
            TxOp::shared_ptr op(new TxPublish(msg, shared_from_this()));
            txn->enlist(op);
            QPID_LOG(debug, "Message " << msg.getSequence() << " enqueue on " << name
                     << " enlisted in " << txn);
        } else {
            if (enqueue(0, msg)) {
                push(msg);
                QPID_LOG(debug, "Message " << msg.getSequence() << " enqueued on " << name);
            } else {
                QPID_LOG(debug, "Message " << msg.getSequence() << " dropped from " << name);
            }
        }
    }
}

void Queue::recoverPrepared(const Message& msg)
{
    Mutex::ScopedLock locker(messageLock);
    current += QueueDepth(1, msg.getMessageSize());
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
        const uint64_t contentSize = msg.getMessageSize();
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

void Queue::mergeMessageAnnotations(const QueueCursor& position,
                                    const qpid::types::Variant::Map& messageAnnotations)
{
  Mutex::ScopedLock locker(messageLock);
  Message *message = messages->find(position);
  if (!message) return;

  qpid::types::Variant::Map::const_iterator it;
  for (it = messageAnnotations.begin(); it != messageAnnotations.end(); ++it) {
    message->addAnnotation(it->first, it->second);
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
    ScopedAutoDelete autodelete(*this);
    boost::intrusive_ptr<PersistableMessage> pmsg;
    {
        Mutex::ScopedLock locker(messageLock);
        QPID_LOG(debug, "Attempting to dequeue message at " << position);
        QueueCursor cursor;
        Message* msg = messages->find(position, &cursor);
        if (msg) {
            if (msg->isPersistent()) pmsg = msg->getPersistentContext();
            observeDequeue(*msg, locker, settings.autodelete ? &autodelete : 0);
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
    ScopedAutoDelete autodelete(*this);
    bool messageFound(false);
    while (true) {
        //TODO: reduce lock scope
        Mutex::ScopedLock locker(messageLock);
        QueueCursor cursor = c->getCursor(); // Save current position.
        Message* msg = messages->next(*c);   // Advances c.
        if (msg) {
            if (isExpired(name, *msg,  sys::AbsTime::now())) {
                QPID_LOG(debug, "Message expired from queue '" << name << "'");
                observeDequeue(*msg, locker, settings.autodelete ? &autodelete : 0);
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
                        QPID_LOG(debug, "Attempting to acquire message " << msg->getSequence()
                                 << " from '" << name << "' with state " << msg->getState());
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
                    QPID_LOG(debug, "Message " << msg->getSequence() << " retrieved from '"
                             << name << "'");
                    m = *msg;
                    messageFound = true;
                    break;
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
            c->stopped();
            listeners.addListener(c);
            break;
        }

    }
    set.notify();
    return messageFound;
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

void Queue::markInUse(bool controlling)
{
    Mutex::ScopedLock locker(messageLock);
    if (controlling) users.addLifecycleController();
    else users.addOther();
}

void Queue::releaseFromUse(bool controlling, bool doDelete)
{
    bool trydelete;
    if (controlling) {
        Mutex::ScopedLock locker(messageLock);
        users.removeLifecycleController();
        trydelete = true;
    } else {
        Mutex::ScopedLock locker(messageLock);
        users.removeOther();
        trydelete = isUnused(locker);
    }
    if (trydelete && doDelete) scheduleAutoDelete();
}

void Queue::consume(Consumer::shared_ptr c, bool requestExclusive,
                    const framing::FieldTable& arguments,
                    const std::string& connectionId, const std::string& userId)
{
    boost::intrusive_ptr<qpid::sys::TimerTask> t;
    {
        Mutex::ScopedLock locker(messageLock);
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
                if(users.hasConsumers()) {
                    throw ResourceLockedException(
                        QPID_MSG("Queue " << getName()
                                 << " already has consumers. Exclusive access denied."));
                } else {
                    exclusive = c->getSession();
                }
            }
            users.addConsumer();
        } else if(c->isCounted()) {
            users.addBrowser();
        }
        if(c->isCounted()) {
            //reset auto deletion timer if necessary
            if (settings.autoDeleteDelay && autoDeleteTask) {
                t = autoDeleteTask;
            }

            observeConsumerAdd(*c, locker);
        }
    }
    if (t) t->cancel();
    if (mgmtObject != 0 && c->isCounted()) {
        mgmtObject->inc_consumerCount();
    }
    if (broker) {
        ManagementAgent* agent = broker->getManagementAgent();
        if (agent) {
            agent->raiseEvent(
                _qmf::EventSubscribe(connectionId, userId, name,
                                     c->getTag(), requestExclusive, ManagementAgent::toMap(arguments)));
        }
    }
}

void Queue::cancel(Consumer::shared_ptr c, const std::string& connectionId, const std::string& userId)
{
    removeListener(c);
    if(c->isCounted())

    {
        bool unused;
        {
            Mutex::ScopedLock locker(messageLock);
            if (c->preAcquires()) {
                users.removeConsumer();
                if (exclusive) exclusive = 0;
            } else {
                users.removeBrowser();
            }
            observeConsumerRemove(*c, locker);
            unused = !users.isUsed();
        }
        if (mgmtObject != 0) {
            mgmtObject->dec_consumerCount();
        }
        if (unused && settings.autodelete) scheduleAutoDelete();
    }
    if (broker) {
        ManagementAgent* agent = broker->getManagementAgent();
        if (agent) agent->raiseEvent(_qmf::EventUnsubscribe(connectionId, userId, c->getTag()));
    }
}

bool Queue::isExpired(const std::string& name, const Message& m, AbsTime now)
{
    if (m.getExpiration() < now) {
        QPID_LOG(debug, "Message expired from queue '" << name << "': " << m.printProperties());
        return true;
    } else {
        return false;
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
        sys::AbsTime time = sys::AbsTime::now();
        uint32_t count = remove(0, boost::bind(&isExpired, name, _1, time), 0, CONSUMER, settings.autodelete);
        QPID_LOG(debug, "Purged " << count << " expired messages from " << getName());
        //
        // Report the count of discarded-by-ttl messages
        //
        if (mgmtObject && count) {
            mgmtObject->inc_discardsTtl(count);
            if (brokerMgmtObject) {
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
            QPID_LOG(error, "Unrecognized message filter: '" << *filter << "'");
            throw qpid::Exception(QPID_MSG("Unrecognized message filter: '" << *filter << "'"));
        }
        return new MessageFilter();
    }

    void moveTo(boost::shared_ptr<Queue> q, Message& m)
    {
        if (q) {
            q->deliver(m);
        }
    }
} // end namespace

uint32_t Queue::remove(const uint32_t maxCount, MessagePredicate p, MessageFunctor f,
                       SubscriptionType type, bool triggerAutoDelete, uint32_t maxTests)
{
    ScopedAutoDelete autodelete(*this);
    std::deque<Message> removed;
    {
        QueueCursor c(type);
        uint32_t count(0), tests(0);
        Mutex::ScopedLock locker(messageLock);
        Message* m = messages->next(c);
        while (m){
            if (maxTests && tests++ >= maxTests) break;
            if (!p || p(*m)) {
                if (maxCount && count++ >= maxCount) break;
                if (m->getState() == AVAILABLE) {
                    //don't actually acquire, just act as if we did
                    observeAcquire(*m, locker);
                }
                observeDequeue(*m, locker, triggerAutoDelete ? &autodelete : 0);
                removed.push_back(*m);//takes a copy of the message
                if (!messages->deleted(c)) {
                    QPID_LOG(warning, "Failed to correctly remove message from " << name << "; state is not consistent!");
                    assert(false);
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
    uint32_t count = remove(qty, boost::bind(&MessageFilter::match, mf.get(), _1), boost::bind(&reroute, dest, _1), CONSUMER/*?*/, settings.autodelete);

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
    return remove(qty, boost::bind(&MessageFilter::match, mf.get(), _1), boost::bind(&moveTo, destq, _1), CONSUMER/*?*/, settings.autodelete);
}

void Queue::push(Message& message, bool /*isRecovery*/)
{
    QueueListeners::NotificationSet copy;
    {
        Mutex::ScopedLock locker(messageLock);
        message.setSequence(++sequence);
        if (settings.sequencing) message.addAnnotation(settings.sequenceKey, (uint32_t)sequence);
        interceptors.publish(message);
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
    return users.getSubscriberCount();
}

bool Queue::canAutoDelete() const
{
    Mutex::ScopedLock locker(messageLock);
    return !deleted && checkAutoDelete(locker);
}

bool Queue::checkAutoDelete(const Mutex::ScopedLock& lock) const
{
    if (settings.autodelete) {
        switch (settings.lifetime) {
          case QueueSettings::DELETE_IF_UNUSED:
            return isUnused(lock);
          case QueueSettings::DELETE_IF_EMPTY:
            return !users.isInUseByController() && isEmpty(lock);
          case QueueSettings::DELETE_IF_UNUSED_AND_EMPTY:
            return isUnused(lock) && isEmpty(lock);
          case QueueSettings::DELETE_ON_CLOSE:
            return !users.isInUseByController();
        }
    }
    return false;
}

bool Queue::isUnused(const Mutex::ScopedLock&) const
{
    return !owner && !users.isUsed();;
}

bool Queue::isEmpty(const Mutex::ScopedLock&) const
{
    return current.getCount() == 0;
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
        if (!checkDepth(QueueDepth(1, msg.getMessageSize()), msg)) {
            return false;
        }
    }

    if (settings.traceId.size()) {
        msg.addTraceId(settings.traceId);
    }

    if (msg.isPersistent() && store) {
        // mark the message as being enqueued - the store MUST CALL msg->enqueueComplete()
        // when it considers the message stored.
        boost::intrusive_ptr<PersistableMessage> pmsg = msg.getPersistentContext();
        assert(pmsg);
        pmsg->enqueueAsync(shared_from_this());
        try {
            store->enqueue(ctxt, pmsg, *this);
        } catch (...) {
            enqueueAborted(msg);
            throw;
        }
    }
    return true;
}

void Queue::enqueueAborted(const Message& msg)
{
    //Called when any transactional enqueue is aborted (including but
    //not limited to a recovered dtx transaction)
    Mutex::ScopedLock locker(messageLock);
    current -= QueueDepth(1, msg.getMessageSize());
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
    ScopedAutoDelete autodelete(*this);
    Mutex::ScopedLock locker(messageLock);
    observeDequeue(msg, locker, settings.autodelete ? &autodelete : 0);
    if (mgmtObject != 0) {
        mgmtObject->inc_msgTxnDequeues();
        mgmtObject->inc_byteTxnDequeues(msg.getMessageSize());
    }
}


void Queue::dequeueFromStore(boost::intrusive_ptr<PersistableMessage> msg)
{
    ScopedUse u(barrier);
    if (u.acquired && msg && store) {
        store->dequeue(0, msg, *this);
    }
}

void Queue::dequeue(const QueueCursor& cursor, TxBuffer* txn)
{
    if (txn) {
        TxOp::shared_ptr op;
        {
            Mutex::ScopedLock locker(messageLock);
            Message* msg = messages->find(cursor);
            if (msg) {
                op = TxOp::shared_ptr(new TxDequeue(cursor, shared_from_this(), msg->getSequence(), msg->getReplicationId()));
            }
        }
        if (op) txn->enlist(op);
    } else {
        dequeue(0, cursor);
    }
}


void Queue::dequeue(TransactionContext* ctxt, const QueueCursor& cursor)
{
    ScopedUse u(barrier);
    if (!u.acquired) return;
    ScopedAutoDelete autodelete(*this);
    boost::intrusive_ptr<PersistableMessage> pmsg;
    {
        Mutex::ScopedLock locker(messageLock);
        Message* msg = messages->find(cursor);
        if (msg) {
            if (msg->isPersistent()) pmsg = msg->getPersistentContext();
            if (!ctxt) {
                observeDequeue(*msg, locker, settings.autodelete ? &autodelete : 0);
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
    ScopedAutoDelete autodelete(*this);
    Mutex::ScopedLock locker(messageLock);
    Message* msg = messages->find(cursor);
    if (msg) {
        const uint64_t contentSize = msg->getMessageSize();
        observeDequeue(*msg, locker, settings.autodelete ? &autodelete : 0);
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
void Queue::observeDequeue(const Message& msg, const Mutex::ScopedLock& lock, ScopedAutoDelete* autodelete)
{
    current -= QueueDepth(1, msg.getMessageSize());
    mgntDeqStats(msg, mgmtObject, brokerMgmtObject);
    observers.dequeued(msg, lock);
    if (autodelete && isEmpty(lock)) autodelete->check(lock);
}

/** updates queue observers when a message has become unavailable for transfer.
 * Requires messageLock be held by caller.
 */
void Queue::observeAcquire(const Message& msg, const Mutex::ScopedLock& l)
{
    observers.acquired(msg, l);
}

/** updates queue observers when a message has become re-available for transfer
 *  Requires messageLock be held by caller.
 */
void Queue::observeRequeue(const Message& msg, const Mutex::ScopedLock& l)
{
    observers.requeued(msg, l);
}

/** updates queue observers when a new consumer has subscribed to this queue.
 */
void Queue::observeConsumerAdd( const Consumer& c, const qpid::sys::Mutex::ScopedLock& l)
{
    observers.consumerAdded(c, l);
}

/** updates queue observers when a consumer has unsubscribed from this queue.
 */
void Queue::observeConsumerRemove( const Consumer& c, const qpid::sys::Mutex::ScopedLock& l)
{
    observers.consumerRemoved(c, l);
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
    if (mgmtObject != 0)
        mgmtObject->debugStats("destroying");
    unbind(broker->getExchanges());
    remove(0, 0, boost::bind(&Queue::abandoned, this, _1), REPLICATOR/*even acquired message are treated as abandoned*/, false);
    if (alternateExchange.get()) {
        alternateExchange->decAlternateUsers();
        alternateExchange.reset();
    }

    if (store) {
        barrier.destroy();
        store->flush(*this);
        store->destroy(*this);
        store = 0;//ensure we make no more calls to the store for this queue
    }
    notifyDeleted();
    {
        Mutex::ScopedLock l(messageLock);
        if (autoDeleteTask) autoDeleteTask = boost::intrusive_ptr<TimerTask>();
        observers.destroy(l);
    }
    if (mgmtObject != 0) {
        mgmtObject->resourceDestroy();
        if (brokerMgmtObject)
            brokerMgmtObject->dec_queueCount();
        mgmtObject = _qmf::Queue::shared_ptr(); // dont print debugStats in Queue::~Queue
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
    buffer.putShortString(userId);
    buffer.putInt8(isAutoDelete());
}

uint32_t Queue::encodedSize() const
{
    return name.size() + 1/*short string size octet*/
        + (alternateExchange.get() ? alternateExchange->getName().size() : 0) + 1 /* short string */
        + userId.size() + 1 /* short string */
        + 1 /* autodelete flag */
        + encodableSettings.encodedSize();
}

void Queue::updateAclUserQueueCount()
{
  if (broker->getAcl())
    broker->getAcl()->approveCreateQueue(userId, name);
}

Queue::shared_ptr Queue::restore( QueueRegistry& queues, Buffer& buffer )
{
    string name;
    string _userId;
    FieldTable ft;
    boost::shared_ptr<Exchange> alternate;
    QueueSettings settings(true, false); // settings.autodelete might be overwritten
    string altExch;
    bool has_userId = false;
    bool has_altExch = false;

    buffer.getShortString(name);
    buffer.get(ft);
    settings.populate(ft, settings.storeSettings);
    //get alternate exchange
    if (buffer.available()) {
        buffer.getShortString(altExch);
        has_altExch = true;
    }
    //get userId of queue's creator; ACL counters for userId are done after ACL plugin is initialized
    if (buffer.available()) {
        buffer.getShortString(_userId);
        has_userId = true;
    }
    //get autodelete flag
    if (buffer.available()) {
        settings.autodelete = buffer.getInt8();
    }

    std::pair<Queue::shared_ptr, bool> result = queues.declare(name, settings, alternate, true);
    if (has_altExch)
        result.first->alternateExchangeName.assign(altExch);
    if (has_userId)
        result.first->setOwningUser(_userId);

    if (result.first->getSettings().autoDeleteDelay) {
        result.first->scheduleAutoDelete();
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

struct AutoDeleteTask : qpid::sys::TimerTask
{
    Queue::shared_ptr queue;
    long expectedVersion;

    AutoDeleteTask(Queue::shared_ptr q, AbsTime fireTime)
        : qpid::sys::TimerTask(fireTime, "DelayedAutoDeletion:"+q->getName()), queue(q), expectedVersion(q->version) {}

    void fire()
    {
        //need to detect case where queue was used after the task was
        //created, but then became unused again before the task fired;
        //in this case ignore this request as there will have already
        //been a later task added
        queue->tryAutoDelete(expectedVersion);
    }
};

void Queue::scheduleAutoDelete(bool immediate)
{
    if (canAutoDelete()) {
        if (!immediate && settings.autoDeleteDelay) {
            AbsTime time(now(), Duration(settings.autoDeleteDelay * TIME_SEC));
            autoDeleteTask = boost::intrusive_ptr<qpid::sys::TimerTask>(new AutoDeleteTask(shared_from_this(), time));
            broker->getTimer().add(autoDeleteTask);
            QPID_LOG(debug, "Timed auto-delete for " << getName() << " initiated");
        } else {
            tryAutoDelete(version);
        }
    }
}

void Queue::tryAutoDelete(long expectedVersion)
{
    bool proceed(false);
    {
        Mutex::ScopedLock locker(messageLock);
        if (!deleted && checkAutoDelete(locker)) {
            proceed = true;
        }
    }

    if (proceed) {
        if (broker->getQueues().destroyIfUntouched(name, expectedVersion)) {
            {
                Mutex::ScopedLock locker(messageLock);
                deleted = true;
            }
            if (broker->getAcl())
                broker->getAcl()->recordDestroyQueue(name);

            QPID_LOG_CAT(debug, model, "Auto-delete queue deleted: " << name << " (" << deleted << ")");
        } else {
            //queue was accessed since the delayed auto-delete was scheduled, so try again
            QPID_LOG_CAT(debug, model, "Auto-delete interrupted for queue: " << name);
            scheduleAutoDelete();
        }
    } else {
        QPID_LOG_CAT(debug, model, "Auto-delete queue could not be deleted: " << name);
    }
}

bool Queue::isExclusiveOwner(const OwnershipToken* const o) const
{
    Mutex::ScopedLock locker(messageLock);
    return o == owner;
}

void Queue::releaseExclusiveOwnership(bool immediateExpiry)
{
    bool unused;
    {
        Mutex::ScopedLock locker(messageLock);
        owner = 0;
        if (mgmtObject) {
            mgmtObject->set_exclusive(false);
        }
        unused = !users.isUsed();
    }
    if (unused && settings.autodelete) {
        scheduleAutoDelete(immediateExpiry);
    }
}

bool Queue::setExclusiveOwner(const OwnershipToken* const o)
{
    //reset auto deletion timer if necessary
    if (settings.autoDeleteDelay && autoDeleteTask) {
        autoDeleteTask->cancel();
    }
    Mutex::ScopedLock locker(messageLock);
    if (owner  || users.hasConsumers()) {
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
    Mutex::ScopedLock locker(messageLock);
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

ManagementObject::shared_ptr Queue::GetManagementObject(void) const
{
    return mgmtObject;
}

Manageable::status_t Queue::ManagementMethod (uint32_t methodId, Args& args, string& etext)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;
    AclModule* acl = broker->getAcl();
    std::string _userId = (getCurrentPublisher()?getCurrentPublisher()->getUserId():"");

    QPID_LOG (debug, "Queue::ManagementMethod [id=" << methodId << "]");

    switch (methodId) {
    case _qmf::Queue::METHOD_PURGE :
        {
            if ((acl)&&(!(acl->authorise(_userId, acl::ACT_PURGE, acl::OBJ_QUEUE, name, NULL)))) {
                throw framing::UnauthorizedAccessException(QPID_MSG("ACL denied purge request from " << _userId));
            }
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

            if (acl) {
                std::map<acl::Property, std::string> params;
                params.insert(make_pair(acl::PROP_EXCHANGENAME, dest->getName()));
                if (!acl->authorise(_userId, acl::ACT_REROUTE, acl::OBJ_QUEUE, name, &params)) {
                    throw framing::UnauthorizedAccessException(QPID_MSG("ACL denied reroute request from " << _userId));
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
        remove(0, After(n), MessagePredicate(), BROWSER, false);
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
void Queue::observeEnqueue(const Message& m, const Mutex::ScopedLock& l)
{
    observers.enqueued(m, l);
    mgntEnqStats(m, mgmtObject, brokerMgmtObject);
}

bool Queue::checkNotDeleted(const Consumer::shared_ptr& c)
{
    if (deleted && !c->hideDeletedError())
        throw ResourceDeletedException(QPID_MSG("Queue " << getName() << " has been deleted."));
    return !deleted;
}

bool Queue::isDeleted() const
{
    Mutex::ScopedLock lock(messageLock);
    return deleted;
}

void Queue::flush()
{
    ScopedUse u(barrier);
    if (u.acquired && store) store->flush(*this);
}


bool Queue::bind(boost::shared_ptr<Exchange> exchange, const std::string& key,
                 const qpid::framing::FieldTable& arguments)
{
    if (!isDeleted() && exchange->bind(shared_from_this(), key, &arguments)) {
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
    ScopedAutoDelete autodelete(*this);
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
            observeDequeue(*message, locker, settings.autodelete ? &autodelete : 0);
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
    if (settings.maxDepth && (settings.maxDepth - current < increment)) {
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


void Queue::setRedirectPeer ( Queue::shared_ptr peer, bool isSrc) {
    Mutex::ScopedLock locker(messageLock);
    redirectPeer = peer;
    redirectSource = isSrc;
}

void Queue::setMgmtRedirectState( std::string peer, bool enabled, bool isSrc ) {
    if (mgmtObject != 0) {
        mgmtObject->set_redirectPeer(enabled ? peer : "");
        mgmtObject->set_redirectSource(isSrc);
    }
}

void Queue::setOwningUser(std::string& _userId) {
    userId  = _userId;
    if (mgmtObject != 0)
       mgmtObject->set_creator(userId);
}

bool Queue::reroute(boost::shared_ptr<Exchange> e, const Message& m)
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

Queue::QueueUsers::QueueUsers() : consumers(0), browsers(0), others(0), controller(false) {}
void Queue::QueueUsers::addConsumer() { ++consumers; }
void Queue::QueueUsers::addBrowser() { ++browsers; }
void Queue::QueueUsers::addLifecycleController() { assert(!controller); controller = true; }
void Queue::QueueUsers::addOther(){ ++others; }
void Queue::QueueUsers::removeConsumer() { assert(consumers > 0); --consumers; }
void Queue::QueueUsers::removeBrowser() { assert(browsers > 0); --browsers; }
void Queue::QueueUsers::removeLifecycleController() { assert(controller); controller = false; }
void Queue::QueueUsers::removeOther() { assert(others > 0); --others; }
bool Queue::QueueUsers::isInUseByController() const { return controller; }
bool Queue::QueueUsers::isUsed() const { return controller || consumers || browsers || others; }
uint32_t Queue::QueueUsers::getSubscriberCount() const { return consumers + browsers; }
bool Queue::QueueUsers::hasConsumers() const { return consumers; }

Queue::ScopedAutoDelete::ScopedAutoDelete(Queue& q) : queue(q), eligible(false) {}
void Queue::ScopedAutoDelete::check(const sys::Mutex::ScopedLock& lock)
{
    eligible = queue.checkAutoDelete(lock);
}
Queue::ScopedAutoDelete::~ScopedAutoDelete()
{
    if (eligible)  queue.scheduleAutoDelete();
}

}}

