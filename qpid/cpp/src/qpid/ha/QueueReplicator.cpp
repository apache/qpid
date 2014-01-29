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
#include "qpid/framing/FieldTable.h"
#include "qpid/log/Statement.h"
#include "qpid/Msg.h"
#include "qpid/assert.h"
#include <boost/shared_ptr.hpp>
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

class QueueReplicator::ErrorListener : public SessionHandler::ErrorListener {
  public:
    ErrorListener(const boost::shared_ptr<QueueReplicator>& qr)
        : queueReplicator(qr), logPrefix(qr->logPrefix) {}

    void connectionException(framing::connection::CloseCode, const std::string&) {}
    void channelException(framing::session::DetachCode, const std::string&) {}
    void executionException(framing::execution::ErrorCode, const std::string&) {}

    void incomingExecutionException(ErrorCode e, const std::string& msg) {
        queueReplicator->incomingExecutionException(e, msg);
    }
    void detach() {
        QPID_LOG(debug, logPrefix << "Session detached");
    }
  private:
    boost::shared_ptr<QueueReplicator> queueReplicator;
    std::string logPrefix;
};

class QueueReplicator::QueueObserver : public broker::QueueObserver {
  public:
    QueueObserver(boost::shared_ptr<QueueReplicator> qr) : queueReplicator(qr) {}
    void enqueued(const Message&) {}
    void dequeued(const Message&) {}
    void acquired(const Message&) {}
    void requeued(const Message&) {}
    void consumerAdded( const Consumer& ) {}
    void consumerRemoved( const Consumer& ) {}
    // Queue observer is destroyed when the queue is.
    void destroy() { queueReplicator->destroy(); }
  private:
    boost::shared_ptr<QueueReplicator> queueReplicator;
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
      logPrefix("Backup of "+q->getName()+": "),
      subscribed(false),
      settings(hb.getSettings()),
      nextId(0), maxId(0)
{
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
    if (q->isAutoDelete()) q->markInUse();

    dispatch[DequeueEvent::KEY] =
        boost::bind(&QueueReplicator::dequeueEvent, this, _1, _2);
    dispatch[IdEvent::KEY] =
        boost::bind(&QueueReplicator::idEvent, this, _1, _2);
}

QueueReplicator::~QueueReplicator() {}

void QueueReplicator::initialize() {
    Mutex::ScopedLock l(lock);
    QPID_LOG(debug, logPrefix << "Created");
    if (!queue) return;         // Already destroyed

    // Enable callback to route()
    if (!getBroker()->getExchanges().registerExchange(shared_from_this()))
        throw Exception(QPID_MSG("Duplicate queue replicator " << getName()));

    // Enable callback to initializeBridge
    std::pair<Bridge::shared_ptr, bool> result =
    queue->getBroker()->getLinks().declare(
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
    );
    bridge = result.first;
    bridge->setErrorListener(
        boost::shared_ptr<ErrorListener>(new ErrorListener(shared_from_this())));

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
    boost::shared_ptr<Bridge> bridge2; // To call outside of lock
    {
        Mutex::ScopedLock l(lock);
        if (!queue) return;     // Already destroyed
        QPID_LOG(debug, logPrefix << "Destroyed");
        bridge2 = bridge;       // call close outside the lock.
        // Need to drop shared pointers to avoid pointer cycles keeping this in memory.
        queue.reset();
        bridge.reset();
        getBroker()->getExchanges().destroy(getName());
    }
    if (bridge2) bridge2->close(); // Outside of lock, avoid deadlock.
}

// Called in a broker connection thread when the bridge is created.
// Note: called with the Link lock held.
void QueueReplicator::initializeBridge(Bridge& bridge, SessionHandler& sessionHandler_) {
    Mutex::ScopedLock l(lock);
    if (!queue) return;         // Already destroyed
    sessionHandler = &sessionHandler_;
    AMQP_ServerProxy peer(sessionHandler->out);
    const qmf::org::apache::qpid::broker::ArgsLinkBridge& args(bridge.getArgs());
    FieldTable arguments;
    arguments.setString(ReplicatingSubscription::QPID_REPLICATING_SUBSCRIPTION, getType());
    arguments.setInt(QPID_SYNC_FREQUENCY, 1); // TODO aconway 2012-05-22: optimize?
    arguments.setTable(ReplicatingSubscription::QPID_BROKER_INFO, brokerInfo.asFieldTable());
    boost::shared_ptr<QueueSnapshot> qs = queue->getObservers().findType<QueueSnapshot>();
    if (qs) arguments.setString(ReplicatingSubscription::QPID_ID_SET, encodeStr(qs->getSnapshot()));

    try {
        peer.getMessage().subscribe(
            args.i_src, args.i_dest, 0/*accept-explicit*/, 1/*not-acquired*/,
            false/*exclusive*/, "", 0, arguments);
        peer.getMessage().setFlowMode(getName(), 1); // Window
        peer.getMessage().flow(getName(), 0, settings.getFlowMessages());
        peer.getMessage().flow(getName(), 1, settings.getFlowBytes());
    }
    catch(const exception& e) {
        QPID_LOG(error, QPID_MSG(logPrefix + "Cannot connect to primary: " << e.what()));
        throw;
    }
    qpid::Address primary;
    link->getRemoteAddress(primary);
    QPID_LOG(debug, logPrefix << "Connected to " << primary << "(" << bridgeName << ")");
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
        PositionMap::iterator j = positions.find(*i);
        if (j != positions.end()) queue->dequeueMessageAt(j->second);
    }
}

// Called in connection thread of the queues bridge to primary.

void QueueReplicator::route(Deliverable& deliverable)
{
    try {
        Mutex::ScopedLock l(lock);
        if (!queue) return;     // Already destroyed
        broker::Message& message(deliverable.getMessage());
        string key(message.getRoutingKey());
        if (!isEventKey(message.getRoutingKey())) {
            ReplicationId id = nextId++;
            maxId = std::max(maxId, id);
            message.setReplicationId(id);
            deliver(message);
            QueuePosition position = queue->getPosition();
            positions[id] = position;
            QPID_LOG(trace, logPrefix << "Enqueued " << LogMessageId(*queue,position,id));
        }
        else {
            DispatchMap::iterator i = dispatch.find(key);
            if (i == dispatch.end()) {
                QPID_LOG(info, logPrefix << "Ignoring unknown event: " << key);
            }
            else {
                (i->second)(message.getContent(), l);
            }
        }
    }
    catch (const std::exception& e) {
        haBroker.shutdown(QPID_MSG(logPrefix << "Replication failed: " << e.what()));
    }
}

void QueueReplicator::deliver(const broker::Message& m) {
    queue->deliver(m);
}

void QueueReplicator::idEvent(const string& data, Mutex::ScopedLock&) {
    nextId = decodeStr<IdEvent>(data).id;
}

void QueueReplicator::incomingExecutionException(ErrorCode e, const std::string& msg) {
    if (e == ERROR_CODE_NOT_FOUND || e == ERROR_CODE_RESOURCE_DELETED) {
        // If the queue is destroyed at the same time we are subscribing, we may
        // get a not-found or resource-deleted exception before the
        // BrokerReplicator gets the queue-delete event. Shut down the bridge by
        // calling destroy(), we can let the BrokerReplicator delete the queue
        // when the queue-delete arrives.
        QPID_LOG(debug, logPrefix << "Deleted on primary: " << msg);
        destroy();
    }
    else
        QPID_LOG(error, logPrefix << "Incoming execution exception: " << msg);
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
        queue->getMessageInterceptors().add(
            boost::shared_ptr<IdSetter>(new IdSetter(maxId+1)));
        // Process auto-deletes
        if (queue->isAutoDelete() && subscribed) {
            // Make a temporary shared_ptr to prevent premature deletion of queue.
            // Otherwise scheduleAutoDelete can call this->destroy, which resets this->queue
            // which could delete the queue while it's still running it's destroyed logic.
            boost::shared_ptr<Queue> q(queue);
            q->releaseFromUse();
            q->scheduleAutoDelete();
        }
    }
}

}} // namespace qpid::broker
