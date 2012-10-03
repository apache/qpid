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

#include "HaBroker.h"
#include "QueueReplicator.h"
#include "ReplicatingSubscription.h"
#include "qpid/broker/Bridge.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Link.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/SessionHandler.h"
#include "qpid/broker/SessionHandler.h"
#include "qpid/framing/SequenceSet.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/log/Statement.h"
#include "qpid/Msg.h"
#include <boost/shared_ptr.hpp>

namespace {
const std::string QPID_REPLICATOR_("qpid.replicator-");
const std::string TYPE_NAME("qpid.queue-replicator");
const std::string QPID_SYNC_FREQUENCY("qpid.sync_frequency");
}

namespace qpid {
namespace ha {
using namespace broker;
using namespace framing;
using namespace std;

const std::string QPID_HA_EVENT_PREFIX("qpid.ha-");
const std::string QueueReplicator::DEQUEUE_EVENT_KEY(QPID_HA_EVENT_PREFIX+"dequeue");
const std::string QueueReplicator::POSITION_EVENT_KEY(QPID_HA_EVENT_PREFIX+"position");

std::string QueueReplicator::replicatorName(const std::string& queueName) {
    return QPID_REPLICATOR_ + queueName;
}

bool QueueReplicator::isEventKey(const std::string key) {
    const std::string& prefix = QPID_HA_EVENT_PREFIX;
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
        QPID_LOG(error, logPrefix << "Unexpectedly detached.");
    }
  private:
    std::string logPrefix;
};

QueueReplicator::QueueReplicator(HaBroker& hb,
                                 boost::shared_ptr<Queue> q,
                                 boost::shared_ptr<Link> l)
    : Exchange(replicatorName(q->getName()), 0, q->getBroker()),
      haBroker(hb),
      logPrefix("Backup queue "+q->getName()+": "),
      queue(q), link(l), brokerInfo(hb.getBrokerInfo())
{
    args.setString(QPID_REPLICATE, printable(NONE).str());
    Uuid uuid(true);
    bridgeName = replicatorName(q->getName()) + std::string(".") + uuid.str();
    getArgs().setString(QPID_REPLICATE, printable(NONE).str());
}

// This must be separate from the constructor so we can call shared_from_this.
void QueueReplicator::activate() {
    sys::Mutex::ScopedLock l(lock);
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
        // Include shared_ptr to self to ensure we are not deleted
        // before initializeBridge is called.
        boost::bind(&QueueReplicator::initializeBridge, shared_from_this(), _1, _2)
    );
    bridge = result.first;
    bridge->setErrorListener(
        boost::shared_ptr<ErrorListener>(new ErrorListener(logPrefix)));
}

QueueReplicator::~QueueReplicator() { deactivate(); }

void QueueReplicator::deactivate() {
    // destroy the route
    sys::Mutex::ScopedLock l(lock);
    if (bridge) {
        bridge->close();
        bridge.reset();
        QPID_LOG(debug, logPrefix << "Deactivated bridge " << bridgeName);
    }
}

// Called in a broker connection thread when the bridge is created.
void QueueReplicator::initializeBridge(Bridge& bridge, SessionHandler& sessionHandler) {
    sys::Mutex::ScopedLock l(lock);
    AMQP_ServerProxy peer(sessionHandler.out);
    const qmf::org::apache::qpid::broker::ArgsLinkBridge& args(bridge.getArgs());
    FieldTable settings;
    settings.setInt(ReplicatingSubscription::QPID_REPLICATING_SUBSCRIPTION, 1);
    settings.setInt(QPID_SYNC_FREQUENCY, 1); // FIXME aconway 2012-05-22: optimize?
    settings.setInt(ReplicatingSubscription::QPID_BACK,
                    queue->getPosition());
    settings.setTable(ReplicatingSubscription::QPID_BROKER_INFO,
                      brokerInfo.asFieldTable());
    SequenceNumber front, back;
    queue->getRange(front, back, broker::REPLICATOR);
    if (front <= back) settings.setInt(ReplicatingSubscription::QPID_FRONT, front);
    try {
        peer.getMessage().subscribe(
            args.i_src, args.i_dest, 0/*accept-explicit*/, 1/*not-acquired*/,
            false/*exclusive*/, "", 0, settings);
        // FIXME aconway 2012-05-22: use a finite credit window?
        peer.getMessage().flow(getName(), 0, 0xFFFFFFFF);
        peer.getMessage().flow(getName(), 1, 0xFFFFFFFF);
    }
    catch(const exception& e) {
        QPID_LOG(error, QPID_MSG(logPrefix + "Cannot connect to primary: " << e.what()));
        throw;
    }
    qpid::Address primary;
    link->getRemoteAddress(primary);
    QPID_LOG(info, logPrefix << "Connected to " << primary << "(" << bridgeName << ")");
    QPID_LOG(trace, logPrefix << "Subscription settings: " << settings);
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

void QueueReplicator::dequeue(SequenceNumber n, sys::Mutex::ScopedLock&) {
    // Thread safe: only calls thread safe Queue functions.
    queue->dequeueMessageAt(n);
}

namespace {
bool getSequence(const Message& message, SequenceNumber& result) {
    result = message.getSequence();
    return true;
}
bool getNext(broker::Queue& q, SequenceNumber position, SequenceNumber& result) {
    QueueCursor cursor(REPLICATOR);
    return q.seek(cursor, boost::bind(&getSequence, _1, boost::ref(result)), position+1);
}
} // namespace

// Called in connection thread of the queues bridge to primary.
void QueueReplicator::route(Deliverable& msg)
{
    try {
        const std::string& key = msg.getMessage().getRoutingKey();
        sys::Mutex::ScopedLock l(lock);
        if (!isEventKey(key)) {
            msg.deliverTo(queue);
            // We are on a backup so the queue is not modified except via this.
            QPID_LOG(trace, logPrefix << "Enqueued message " << queue->getPosition());
        }
        else if (key == DEQUEUE_EVENT_KEY) {
            SequenceSet dequeues = decodeContent<SequenceSet>(msg.getMessage());
            QPID_LOG(trace, logPrefix << "Dequeue: " << dequeues);
            //TODO: should be able to optimise the following
            for (SequenceSet::iterator i = dequeues.begin(); i != dequeues.end(); i++)
                dequeue(*i, l);
        }
        else if (key == POSITION_EVENT_KEY) {
            SequenceNumber position = decodeContent<SequenceNumber>(msg.getMessage());
            QPID_LOG(trace, logPrefix << "Position moved from " << queue->getPosition()
                     << " to " << position);
            // Verify that there are no messages after the new position in the queue.
            SequenceNumber next;
            if (getNext(*queue, position, next))
                throw Exception(QPID_MSG(logPrefix << "Invalid position " << position
                                         << " preceeds message at " << next));
            queue->setPosition(position);
        }
        // Ignore unknown event keys, may be introduced in later versions.
    }
    catch (const std::exception& e) {
        QPID_LOG(critical, logPrefix << "Replication failed: " << e.what());
        haBroker.shutdown();
        throw;
    }
}

// Unused Exchange methods.
bool QueueReplicator::bind(boost::shared_ptr<Queue>, const std::string&, const FieldTable*) { return false; }
bool QueueReplicator::unbind(boost::shared_ptr<Queue>, const std::string&, const FieldTable*) { return false; }
bool QueueReplicator::isBound(boost::shared_ptr<Queue>, const std::string* const, const FieldTable* const) { return false; }
std::string QueueReplicator::getType() const { return TYPE_NAME; }

}} // namespace qpid::broker
