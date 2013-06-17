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

#include "makeMessage.h"
#include "HaBroker.h"
#include "QueueReplicator.h"
#include "QueueSnapshots.h"
#include "ReplicatingSubscription.h"
#include "Settings.h"
#include "qpid/broker/Bridge.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Link.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueObserver.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/SessionHandler.h"
#include "qpid/broker/SessionHandler.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/log/Statement.h"
#include "qpid/Msg.h"
#include "qpid/assert.h"
#include <boost/shared_ptr.hpp>

namespace {
const std::string QPID_REPLICATOR_("qpid.replicator-");
const std::string TYPE_NAME("qpid.queue-replicator");
const std::string QPID_HA("qpid.ha-");
}

namespace qpid {
namespace ha {
using namespace broker;
using namespace framing;
using namespace std;
using sys::Mutex;

const std::string QueueReplicator::DEQUEUE_EVENT_KEY(QPID_HA+"dequeue");
const std::string QueueReplicator::ID_EVENT_KEY(QPID_HA+"id");
const std::string QueueReplicator::QPID_SYNC_FREQUENCY("qpid.sync_frequency");

std::string QueueReplicator::replicatorName(const std::string& queueName) {
    return QPID_REPLICATOR_ + queueName;
}

bool QueueReplicator::isReplicatorName(const std::string& name) {
    return name.compare(0, QPID_REPLICATOR_.size(), QPID_REPLICATOR_) == 0;
}

bool QueueReplicator::isEventKey(const std::string key) {
    const std::string& prefix = QPID_HA;
    bool ret = key.size() > prefix.size() && key.compare(0, prefix.size(), prefix) == 0;
    return ret;
}

class QueueReplicator::ErrorListener : public SessionHandler::ErrorListener {
  public:
    ErrorListener(const std::string& prefix) : logPrefix(prefix) {}
    void connectionException(framing::connection::CloseCode, const std::string& msg) {
        QPID_LOG(error, logPrefix << "Connection error: " << msg);
    }
    void channelException(framing::session::DetachCode, const std::string& msg) {
        QPID_LOG(error, logPrefix << "Channel error: " << msg);
    }
    void executionException(framing::execution::ErrorCode, const std::string& msg) {
        QPID_LOG(error, logPrefix << "Execution error: " << msg);
    }
    void detach() {
        QPID_LOG(debug, logPrefix << "Session detached");
    }
  private:
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

QueueReplicator::QueueReplicator(HaBroker& hb,
                                 boost::shared_ptr<Queue> q,
                                 boost::shared_ptr<Link> l)
    : Exchange(replicatorName(q->getName()), 0, q->getBroker()),
      haBroker(hb),
      logPrefix("Backup of "+q->getName()+": "),
      queue(q), link(l), brokerInfo(hb.getBrokerInfo()), subscribed(false),
      settings(hb.getSettings()), destroyed(false),
      nextId(0), maxId(0)
{
    QPID_LOG(debug, logPrefix << "Created");
    args.setString(QPID_REPLICATE, printable(NONE).str());
    Uuid uuid(true);
    bridgeName = replicatorName(q->getName()) + std::string(".") + uuid.str();
    framing::FieldTable args = getArgs();
    args.setString(QPID_REPLICATE, printable(NONE).str());
    setArgs(args);
}

// This must be called immediately after the constructor.
// It has to be separate so we can call shared_from_this().
void QueueReplicator::activate() {
    Mutex::ScopedLock l(lock);
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
        boost::shared_ptr<ErrorListener>(new ErrorListener(logPrefix)));

    // Enable callback to destroy()
    queue->addObserver(
        boost::shared_ptr<QueueObserver>(new QueueObserver(shared_from_this())));
}

QueueReplicator::~QueueReplicator() {}

// Called from Queue::destroyed()
void QueueReplicator::destroy() {
    boost::shared_ptr<Bridge> bridge2; // To call outside of lock
    {
        Mutex::ScopedLock l(lock);
        if (destroyed) return;
        destroyed = true;
        QPID_LOG(debug, logPrefix << "Destroyed");
        // Need to drop shared pointers to avoid pointer cycles keeping this in memory.
        queue.reset();
        link.reset();
        bridge.reset();
        getBroker()->getExchanges().destroy(getName());
        bridge2 = bridge;
    }
    if (bridge2) bridge2->close(); // Outside of lock, avoid deadlock.
}

// Called in a broker connection thread when the bridge is created.
// Note: called with the Link lock held.
void QueueReplicator::initializeBridge(Bridge& bridge, SessionHandler& sessionHandler) {
    Mutex::ScopedLock l(lock);
    if (destroyed) return;         // Already destroyed
    AMQP_ServerProxy peer(sessionHandler.out);
    const qmf::org::apache::qpid::broker::ArgsLinkBridge& args(bridge.getArgs());
    FieldTable arguments;
    arguments.setInt(ReplicatingSubscription::QPID_REPLICATING_SUBSCRIPTION, 1);
    arguments.setInt(QPID_SYNC_FREQUENCY, 1); // TODO aconway 2012-05-22: optimize?
    arguments.setTable(ReplicatingSubscription::QPID_BROKER_INFO, brokerInfo.asFieldTable());
    arguments.setString(ReplicatingSubscription::QPID_ID_SET,
                        encodeStr(haBroker.getQueueSnapshots()->get(queue)->snapshot()));
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
    QPID_LOG(info, logPrefix << "Connected to " << primary << "(" << bridgeName << ")");
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

void QueueReplicator::dequeue(const ReplicationIdSet& dequeues, Mutex::ScopedLock&) {
    QPID_LOG(trace, logPrefix << "Dequeue " << dequeues);
    //TODO: should be able to optimise the following
    for (ReplicationIdSet::iterator i = dequeues.begin(); i != dequeues.end(); ++i) {
        PositionMap::iterator j = positions.find(*i);
        if (j != positions.end()) queue->dequeueMessageAt(j->second);
    }
}

// Called in connection thread of the queues bridge to primary.
void QueueReplicator::route(Deliverable& msg)
{
    try {
        Mutex::ScopedLock l(lock);
        if (destroyed) return;
        const std::string& key = msg.getMessage().getRoutingKey();
        if (!isEventKey(key)) { // Replicated message
            ReplicationId id = nextId++;
            maxId = std::max(maxId, id);
            msg.getMessage().setReplicationId(id);
            msg.deliverTo(queue);
            QueuePosition position = queue->getPosition();
            positions[id] = position;
            QPID_LOG(trace, logPrefix << "Enqueued " << LogMessageId(*queue,position,id));
        }
        else if (key == DEQUEUE_EVENT_KEY) {
            dequeue(decodeContent<ReplicationIdSet>(msg.getMessage()), l);
        }
        else if (key == ID_EVENT_KEY) {
            nextId = decodeContent<ReplicationId>(msg.getMessage());
        }
        // Ignore unknown event keys, may be introduced in later versions.
    }
    catch (const std::exception& e) {
        haBroker.shutdown(QPID_MSG(logPrefix << "Replication failed: " << e.what()));
    }
}

ReplicationId QueueReplicator::getMaxId() {
    Mutex::ScopedLock l(lock);
    return maxId;
}

// Unused Exchange methods.
bool QueueReplicator::bind(boost::shared_ptr<Queue>, const std::string&, const FieldTable*) { return false; }
bool QueueReplicator::unbind(boost::shared_ptr<Queue>, const std::string&, const FieldTable*) { return false; }
bool QueueReplicator::isBound(boost::shared_ptr<Queue>, const std::string* const, const FieldTable* const) { return false; }
std::string QueueReplicator::getType() const { return TYPE_NAME; }

}} // namespace qpid::broker
