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

#include "Event.h"
#include "HaBroker.h"
#include "IdSetter.h"
#include "LogPrefix.h"
#include "QueueReplicator.h"
#include "QueueSnapshot.h"
#include "ReplicatingSubscription.h"
#include "Settings.h"
#include "types.h"
#include "qpid/broker/Bridge.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Link.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueObserver.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/SessionHandler.h"
#include "qpid/broker/SessionState.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/log/Statement.h"
#include "qpid/Msg.h"
#include "qpid/assert.h"
#include <boost/shared_ptr.hpp>
#include <boost/weak_ptr.hpp>
#include <boost/bind.hpp>


namespace qpid {
namespace ha {
using namespace broker;
using namespace framing;
using namespace framing::execution;
using namespace std;
using std::exception;
using sys::Mutex;
using boost::shared_ptr;

const std::string QueueReplicator::QPID_SYNC_FREQUENCY("qpid.sync_frequency");

std::string QueueReplicator::replicatorName(const std::string& queueName) {
    return QUEUE_REPLICATOR_PREFIX + queueName;
}

bool QueueReplicator::isReplicatorName(const std::string& name) {
    return startsWith(name, QUEUE_REPLICATOR_PREFIX);
}

namespace {
void pushIfQr(QueueReplicator::Vector& v, const shared_ptr<Exchange>& ex) {
    shared_ptr<QueueReplicator> qr = boost::dynamic_pointer_cast<QueueReplicator>(ex);
    if (qr) v.push_back(qr);
}
}

void QueueReplicator::copy(ExchangeRegistry& registry, Vector& result) {
    registry.eachExchange(boost::bind(&pushIfQr, boost::ref(result), _1));
}

// Debug log expected exceptions on queue replicator, check incoming execution
// exceptions for "deleted on primary" conditions.
class QueueReplicator::ErrorListener : public SessionHandler::ErrorListener {
  public:
    ErrorListener(const boost::shared_ptr<QueueReplicator>& qr)
        : queueReplicator(qr), logPrefix(qr->logPrefix.prePrefix, qr->logPrefix.get()) {}

    void connectionException(framing::connection::CloseCode code, const std::string& msg) {
        QPID_LOG(error, logPrefix << "Outgoing " << framing::createConnectionException(code, msg).what());
    }
    void channelException(framing::session::DetachCode code, const std::string& msg) {
        QPID_LOG(error, logPrefix << "Outgoing " << framing::createChannelException(code, msg).what());
    }
    void executionException(framing::execution::ErrorCode code, const std::string& msg) {
        QPID_LOG(error, logPrefix  << "Outgoing " << framing::createSessionException(code, msg).what());
    }
    void incomingExecutionException(ErrorCode code, const std::string& msg) {
        boost::shared_ptr<QueueReplicator> qr = queueReplicator.lock();
        if (!(qr && qr->deletedOnPrimary(code, msg)))
            QPID_LOG(error, logPrefix << "Incoming " << framing::createSessionException(code, msg).what());
    }
    void detach() {}

  private:
    boost::weak_ptr<QueueReplicator> queueReplicator;
    LogPrefix2 logPrefix;
};

class QueueReplicator::QueueObserver : public broker::QueueObserver {
  public:
    typedef boost::shared_ptr<QueueReplicator> Ptr;
    QueueObserver(Ptr qr) : queueReplicator(qr) {}

    void enqueued(const Message& m) {
        Ptr qr = queueReplicator.lock();
        if (qr) qr->enqueued(m);
    }

    void dequeued(const Message& m) {
        Ptr qr = queueReplicator.lock();
        if (qr) qr->dequeued(m);
    }

    void acquired(const Message&) {}
    void requeued(const Message&) {}
    void consumerAdded( const Consumer& ) {}
    void consumerRemoved( const Consumer& ) {}
    // Queue observer is destroyed when the queue is.
    void destroy() {
        Ptr qr = queueReplicator.lock();
        if (qr) qr->destroy();
    }

  private:
    boost::weak_ptr<QueueReplicator> queueReplicator;
};


boost::shared_ptr<QueueReplicator> QueueReplicator::create(
    HaBroker& hb, boost::shared_ptr<broker::Queue> q, boost::shared_ptr<broker::Link> l)
{
    boost::shared_ptr<QueueReplicator> qr(new QueueReplicator(hb, q, l));
    qr->initialize();
    return qr;
}

QueueReplicator::QueueReplicator(HaBroker& hb,
                                 boost::shared_ptr<Queue> q,
                                 boost::shared_ptr<Link> l)
    : Exchange(replicatorName(q->getName()), 0, q->getBroker()),
      haBroker(hb),
      brokerInfo(hb.getBrokerInfo()),
      link(l),
      queue(q),
      sessionHandler(0),
      logPrefix(hb.logPrefix, "Backup of "+q->getName()+": "),
      subscribed(false),
      settings(hb.getSettings()),
      nextId(0), maxId(0)
{
    QPID_LOG(debug, logPrefix << "Created");
    // The QueueReplicator will take over setting replication IDs.
    boost::shared_ptr<IdSetter> setter =
        q->getMessageInterceptors().findType<IdSetter>();
    if (setter) q->getMessageInterceptors().remove(setter);

    args.setString(QPID_REPLICATE, printable(NONE).str());
    Uuid uuid(true);
    bridgeName = replicatorName(q->getName()) + std::string(".") + uuid.str();
    framing::FieldTable args = getArgs();
    args.setString(QPID_REPLICATE, printable(NONE).str());
    setArgs(args);
    // Don't allow backup queues to auto-delete, primary decides when to delete.
    if (q->isAutoDelete()) q->markInUse(false);

    dispatch[DequeueEvent::KEY] =
        boost::bind(&QueueReplicator::dequeueEvent, this, _1, _2);
    dispatch[IdEvent::KEY] =
        boost::bind(&QueueReplicator::idEvent, this, _1, _2);
}

QueueReplicator::~QueueReplicator() {}

void QueueReplicator::initialize() {
    Mutex::ScopedLock l(lock);
    if (!queue) return;         // Already destroyed

    // Enable callback to route()
    if (!getBroker()->getExchanges().registerExchange(shared_from_this()))
        throw Exception(QPID_MSG("Duplicate queue replicator " << getName()));

    // Enable callback to initializeBridge
    boost::shared_ptr<Bridge> b = queue->getBroker()->getLinks().declare(
        bridgeName,
        *link,
        false,              // durable
        queue->getName(),   // src
        getName(),          // dest
        "",                 // key
        false,              // isQueue
        false,              // isLocal
        "",                 // id/tag
        "",                 // excludes
        false,              // dynamic
        0,                  // sync?
        LinkRegistry::INFINITE_CREDIT,
        // Include shared_ptr to self to ensure we are not deleted
        // before initializeBridge is called.
        boost::bind(&QueueReplicator::initializeBridge, shared_from_this(), _1, _2)
    ).first;
    b->setErrorListener(
        boost::shared_ptr<ErrorListener>(new ErrorListener(shared_from_this())));
    bridge = b;                 // bridge is a weak_ptr to avoid a cycle.

    // Enable callback to destroy()
    queue->getObservers().add(
        boost::shared_ptr<QueueObserver>(new QueueObserver(shared_from_this())));
}

void QueueReplicator::disconnect() {
    Mutex::ScopedLock l(lock);
    sessionHandler = 0;
}

// Called from Queue::destroyed()
void QueueReplicator::destroy() {
    QPID_LOG(debug, logPrefix << "Destroyed");
    boost::shared_ptr<Bridge> bridge2; // To call outside of lock
    {
        Mutex::ScopedLock l(lock);
        if (!queue) return;     // Already destroyed
        bridge2 = bridge.lock(); // !call close outside the lock.
        destroy(l);
    }
    if (bridge2) bridge2->close(); // Outside of lock, avoid deadlock.
}

void QueueReplicator::destroy(Mutex::ScopedLock&) {
    // Need to drop shared pointers to avoid pointer cycles keeping this in memory.
    queue.reset();
    bridge.reset();
    getBroker()->getExchanges().destroy(getName());
}


// Called in a broker connection thread when the bridge is created.
// Note: called with the Link lock held.
void QueueReplicator::initializeBridge(Bridge& bridge, SessionHandler& sessionHandler_) {
    Mutex::ScopedLock l(lock);
    if (!queue) return;         // Already destroyed
    sessionHandler = &sessionHandler_;
    if (sessionHandler->getSession()) {
        // Don't overwrite the exchange property set on the primary.
        sessionHandler->getSession()->getMessageBuilder().setCopyExchange(false);
    }
    AMQP_ServerProxy peer(sessionHandler->out);
    const qmf::org::apache::qpid::broker::ArgsLinkBridge& args(bridge.getArgs());
    FieldTable arguments;
    arguments.setString(ReplicatingSubscription::QPID_REPLICATING_SUBSCRIPTION, getType());
    arguments.setInt(QPID_SYNC_FREQUENCY, 1); // TODO aconway 2012-05-22: optimize?
    arguments.setTable(ReplicatingSubscription::QPID_BROKER_INFO, brokerInfo.asFieldTable());
    boost::shared_ptr<QueueSnapshot> qs = queue->getObservers().findType<QueueSnapshot>();
    ReplicationIdSet snapshot;
    if (qs) {
        snapshot = qs->getSnapshot();
        arguments.set(
            ReplicatingSubscription::QPID_ID_SET,
            FieldTable::ValuePtr(new Var32Value(encodeStr(snapshot), TYPE_CODE_VBIN32)));
    }
    try {
        peer.getMessage().subscribe(
            args.i_src, args.i_dest, 0/*accept-explicit*/, 1/*not-acquired*/,
            false/*exclusive*/, "", 0, arguments);
        peer.getMessage().setFlowMode(getName(), 1); // Window
        peer.getMessage().flow(getName(), 0, settings.getFlowMessages());
        peer.getMessage().flow(getName(), 1, settings.getFlowBytes());
    }
    catch(const exception& e) {
        QPID_LOG(error, logPrefix << "Cannot connect to primary: " << e.what());
        throw;
    }
    qpid::Address primary;
    link->getRemoteAddress(primary);
    QPID_LOG(debug, logPrefix << "Connected to " << primary << " snapshot=" << snapshot << " bridge=" << bridgeName);
    QPID_LOG(trace, logPrefix << "Subscription arguments: " << arguments);
}

namespace {
template <class T> T decodeContent(Message& m) {
    std::string content = m.getContent();
    Buffer buffer(const_cast<char*>(content.c_str()), content.size());
    T result;
    result.decode(buffer);
    return result;
}
}

void QueueReplicator::dequeueEvent(const string& data, Mutex::ScopedLock&) {
    DequeueEvent e;
    decodeStr(data, e);
    QPID_LOG(trace, logPrefix << "Dequeue " << e.ids);
    //TODO: should be able to optimise the following
    for (ReplicationIdSet::iterator i = e.ids.begin(); i != e.ids.end(); ++i) {
        QueuePosition position;
        {
            Mutex::ScopedLock l(lock);
            PositionMap::iterator j = positions.find(*i);
            if (j == positions.end()) continue;
            position = j->second;
        }
        queue->dequeueMessageAt(position); // Outside lock, will call dequeued().
        // positions will be cleaned up in dequeued()
    }
}

// Called in connection thread of the queues bridge to primary.
void QueueReplicator::route(Deliverable& deliverable)
{
    try {
        broker::Message& message(deliverable.getMessage());
        {
            Mutex::ScopedLock l(lock);
            if (!queue) return;     // Already destroyed
            string key(message.getRoutingKey());
            if (isEventKey(key)) {
                DispatchMap::iterator i = dispatch.find(key);
                if (i == dispatch.end()) {
                    QPID_LOG(info, logPrefix << "Ignoring unknown event: " << key);
                } else {
                    (i->second)(message.getContent(), l);
                }
                return;
            }
            ReplicationId id = nextId++;
            message.setReplicationId(id);
            PositionMap::iterator i = positions.find(id);
            if (i != positions.end()) {
                QPID_LOG(trace, logPrefix << "Already on queue: " << logMessageId(*queue, message));
                return;
            }
            QPID_LOG(trace, logPrefix << "Received: " << logMessageId(*queue, message));
        }
        deliver(message);       // Outside lock, will call enqueued()
    }
    catch (const std::exception& e) {
        haBroker.shutdown(QPID_MSG(logPrefix << "Replication failed: " << e.what()));
    }

}

void QueueReplicator::deliver(const broker::Message& m) {
    queue->deliver(m);
}

// Called via QueueObserver when message is enqueued. Could be as part of deliver()
// or in a different thread if a message is enqueued via a transaction.
//
void QueueReplicator::enqueued(const broker::Message& m) {
    Mutex::ScopedLock l(lock);
    maxId = std::max(maxId, ReplicationId(m.getReplicationId()));
    positions[m.getReplicationId()] = m.getSequence();
    QPID_LOG(trace, logPrefix << "Enqueued " << logMessageId(*queue, m));
}

// Called via QueueObserver
void QueueReplicator::dequeued(const broker::Message& m) {
    Mutex::ScopedLock l(lock);
    positions.erase(m.getReplicationId());
}

void QueueReplicator::idEvent(const string& data, Mutex::ScopedLock&) {
    nextId = decodeStr<IdEvent>(data).id;
}

bool QueueReplicator::deletedOnPrimary(ErrorCode e, const std::string& msg) {
    if (e == ERROR_CODE_NOT_FOUND || e == ERROR_CODE_RESOURCE_DELETED) {
        // If the queue is destroyed at the same time we are subscribing, we may
        // get a not-found or resource-deleted exception before the
        // BrokerReplicator gets the queue-delete event. Shut down the bridge by
        // calling destroy(), we can let the BrokerReplicator delete the queue
        // when the queue-delete arrives.
        QPID_LOG(debug, logPrefix << "Deleted on primary: "
                 << framing::createSessionException(e, msg).what());
        destroy();
        return true;
    }
    return false;
}

// Unused Exchange methods.
bool QueueReplicator::bind(boost::shared_ptr<Queue>, const std::string&, const FieldTable*) { return false; }
bool QueueReplicator::unbind(boost::shared_ptr<Queue>, const std::string&, const FieldTable*) { return false; }
bool QueueReplicator::isBound(boost::shared_ptr<Queue>, const std::string* const, const FieldTable* const) { return false; }
bool QueueReplicator::hasBindings() { return false; }
std::string QueueReplicator::getType() const { return ReplicatingSubscription::QPID_QUEUE_REPLICATOR; }

void QueueReplicator::promoted() {
    if (queue) {
        // On primary QueueReplicator no longer sets IDs, start an IdSetter.
        QPID_LOG(debug, logPrefix << "Promoted, first replication-id " << maxId+1)
        queue->getMessageInterceptors().add(
            boost::shared_ptr<IdSetter>(new IdSetter(logPrefix, queue->getName(), maxId+1)));
        // Process auto-deletes
        if (queue->isAutoDelete()) {
            // Make a temporary shared_ptr to prevent premature deletion of queue.
            // Otherwise scheduleAutoDelete can call this->destroy, which resets this->queue
            // which could delete the queue while it's still running it's destroyed logic.
            boost::shared_ptr<Queue> q(queue);
            // See markInUse in ctor: release but don't delete if never used.
            q->releaseFromUse(false/*controller*/, subscribed/*doDelete*/);
        }
    }
}

}} // namespace qpid::broker
