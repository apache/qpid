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

#include "QueueReplicator.h"
#include "ReplicatingSubscription.h"
#include "Logging.h"
#include "qpid/broker/Bridge.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Link.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/SessionHandler.h"
#include "qpid/framing/SequenceSet.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/log/Statement.h"
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

const std::string QueueReplicator::DEQUEUE_EVENT_KEY("qpid.dequeue-event");
const std::string QueueReplicator::POSITION_EVENT_KEY("qpid.position-event");

QueueReplicator::QueueReplicator(boost::shared_ptr<Queue> q, boost::shared_ptr<Link> l)
    : Exchange(QPID_REPLICATOR_+q->getName(), 0, 0), // FIXME aconway 2011-11-24: hidden from management?
      queue(q), link(l)
{
    QPID_LOG(debug, "HA: Replicating queue " << q->getName() << " " << q->getSettings());
    // Declare the replicator bridge.
    queue->getBroker()->getLinks().declare(
        link->getHost(), link->getPort(),
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
        boost::bind(&QueueReplicator::initializeBridge, this, _1, _2)
    );
}

QueueReplicator::~QueueReplicator() {}

// NB: This is called back ina broker connection thread when the
// bridge is created.
void QueueReplicator::initializeBridge(Bridge& bridge, SessionHandler& sessionHandler) {
    // No lock needed, no mutable member variables are used.
    framing::AMQP_ServerProxy peer(sessionHandler.out);
    const qmf::org::apache::qpid::broker::ArgsLinkBridge& args(bridge.getArgs());
    framing::FieldTable settings;

    // FIXME aconway 2011-12-09: Failover optimization removed.
    // There was code here to re-use messages already on the backup
    // during fail-over. This optimization was removed to simplify
    // the logic till we get the basic replication stable, it
    // can be re-introduced later. Last revision with the optimization:
    // r1213258 | QPID-3603: Fix QueueReplicator subscription parameters.

    // Clear out any old messages, reset the queue to start replicating fresh.
    queue->purge();
    queue->setPosition(0);

    settings.setInt(ReplicatingSubscription::QPID_REPLICATING_SUBSCRIPTION, 1);
    settings.setInt(QPID_SYNC_FREQUENCY, 1);
    peer.getMessage().subscribe(args.i_src, args.i_dest, 0/*accept-explicit*/, 1/*not-acquired*/, false, "", 0, settings);
    peer.getMessage().flow(getName(), 0, 0xFFFFFFFF);
    peer.getMessage().flow(getName(), 1, 0xFFFFFFFF);
    QPID_LOG(debug, "HA: Backup activated bridge from " << args.i_src << " to " << args.i_dest);
}

namespace {
template <class T> T decodeContent(Message& m) {
    std::string content;
    m.getFrames().getContent(content);
    Buffer buffer(const_cast<char*>(content.c_str()), content.size());
    T result;
    result.decode(buffer);
    return result;
}
}

void QueueReplicator::dequeue(SequenceNumber n,  const sys::Mutex::ScopedLock&) {
    // Thread safe: only calls thread safe Queue functions.
    if (queue->getPosition() >= n) { // Ignore dequeus we  haven't reached yet
        QueuedMessage message;
        if (queue->acquireMessageAt(n, message)) {
            queue->dequeue(0, message);
            QPID_LOG(trace, "HA: Backup dequeued: "<< QueuePos(message));
        }
    }
}

void QueueReplicator::route(Deliverable& msg, const std::string& key, const FieldTable* /*args*/)
{
    sys::Mutex::ScopedLock l(lock);
    if (key == DEQUEUE_EVENT_KEY) {
        SequenceSet dequeues = decodeContent<SequenceSet>(msg.getMessage());
        QPID_LOG(trace, "HA: Backup received dequeues: " << dequeues);
        //TODO: should be able to optimise the following
        for (SequenceSet::iterator i = dequeues.begin(); i != dequeues.end(); i++)
            dequeue(*i, l);
    } else if (key == POSITION_EVENT_KEY) {
        SequenceNumber position = decodeContent<SequenceNumber>(msg.getMessage());
        assert(queue->getPosition() <= position);
         //TODO aconway 2011-12-14: Optimize this?
        for (SequenceNumber i = queue->getPosition(); i < position; ++i)
            dequeue(i,l);
        queue->setPosition(position);
        QPID_LOG(trace, "HA: Backup advanced to: " << QueuePos(queue.get(), queue->getPosition()));
    } else {
        QPID_LOG(trace, "HA: Backup enqueued message: " << QueuePos(queue.get(), queue->getPosition()+1));
        msg.deliverTo(queue);
    }
}

bool QueueReplicator::bind(boost::shared_ptr<Queue>, const std::string&, const FieldTable*) { return false; }
bool QueueReplicator::unbind(boost::shared_ptr<Queue>, const std::string&, const FieldTable*) { return false; }
bool QueueReplicator::isBound(boost::shared_ptr<Queue>, const std::string* const, const FieldTable* const) { return false; }
std::string QueueReplicator::getType() const { return TYPE_NAME; }

}} // namespace qpid::broker
