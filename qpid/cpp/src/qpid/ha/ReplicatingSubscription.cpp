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
#include "IdSetter.h"
#include "QueueGuard.h"
#include "QueueSnapshot.h"
#include "ReplicatingSubscription.h"
#include "Primary.h"
#include "HaBroker.h"
#include "qpid/assert.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/SessionContext.h"
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"
#include "qpid/types/Uuid.h"
#include <sstream>


namespace qpid {
namespace ha {

using namespace framing;
using namespace broker;
using namespace std;
using sys::Mutex;
using broker::amqp_0_10::MessageTransfer;
namespace { const string QPID_HA(QPID_HA_PREFIX); }
const string ReplicatingSubscription::QPID_REPLICATING_SUBSCRIPTION(QPID_HA+"repsub");
const string ReplicatingSubscription::QPID_BROKER_INFO(QPID_HA+"info");
const string ReplicatingSubscription::QPID_ID_SET(QPID_HA+"ids");
const string ReplicatingSubscription::QPID_QUEUE_REPLICATOR(QPID_HA+"qrep");

/* Called by SemanticState::consume to create a consumer */
boost::shared_ptr<broker::SemanticState::ConsumerImpl>
ReplicatingSubscription::Factory::create(
    SemanticState* parent,
    const string& name,
    Queue::shared_ptr queue,
    bool ack,
    bool acquire,
    bool exclusive,
    const string& tag,
    const string& resumeId,
    uint64_t resumeTtl,
    const framing::FieldTable& arguments
) {
    boost::shared_ptr<ReplicatingSubscription> rs;
    std::string type = arguments.getAsString(QPID_REPLICATING_SUBSCRIPTION);
    if (type == QPID_QUEUE_REPLICATOR) {
        rs.reset(new ReplicatingSubscription(
                     haBroker,
                     parent, name, queue, ack, acquire, exclusive, tag,
                     resumeId, resumeTtl, arguments));
    }
    if (rs) rs->initialize();
    return rs;
}

ReplicatingSubscription::ReplicatingSubscription(
    HaBroker& hb,
    SemanticState* parent,
    const string& name,
    Queue::shared_ptr queue_,
    bool ack,
    bool /*acquire*/,
    bool exclusive,
    const string& tag,
    const string& resumeId,
    uint64_t resumeTtl,
    const framing::FieldTable& arguments
) : ConsumerImpl(parent, name, queue_, ack, REPLICATOR, exclusive, tag,
                 resumeId, resumeTtl, arguments),
    logPrefix(hb.logPrefix),
    position(0), wasStopped(false), ready(false), cancelled(false),
    haBroker(hb),
    primary(boost::dynamic_pointer_cast<Primary>(haBroker.getRole()))
{}

// Called in subscription's connection thread when the subscription is created.
// Separate from ctor because we need to use shared_from_this
//
void ReplicatingSubscription::initialize() {
    try {
        FieldTable ft;
        if (!getArguments().getTable(ReplicatingSubscription::QPID_BROKER_INFO, ft))
            throw InvalidArgumentException(
                logPrefix.get()+"Can't subscribe, no broker info: "+getTag());
        info.assign(ft);

        // Set a log prefix message that identifies the remote broker.
        ostringstream os;
        os << "Subscription to " << queue->getName() << " at ";
        info.printId(os) << ": ";
        logPrefix = os.str();

        // If there's already a guard (we are in failover) use it, else create one.
        if (primary) guard = primary->getGuard(queue, info);
        if (!guard) guard.reset(new QueueGuard(*queue, info, logPrefix.prePrefix));

        // NOTE: Once the observer is attached we can have concurrent
        // calls to dequeued so we need to lock use of this->dequeues.
        //
        // However we must attach the observer _before_ we snapshot for
        // initial dequeues to be sure we don't miss any dequeues
        // between the snapshot and attaching the observer.
        queue->getObservers().add(
            boost::dynamic_pointer_cast<ReplicatingSubscription>(shared_from_this()));
        boost::shared_ptr<QueueSnapshot> snapshot = queue->getObservers().findType<QueueSnapshot>();
        // There may be no snapshot if the queue is being deleted concurrently.
        if (!snapshot) {
            queue->getObservers().remove(
                boost::dynamic_pointer_cast<ReplicatingSubscription>(shared_from_this()));
            throw ResourceDeletedException(logPrefix.get()+"Can't subscribe, queue deleted");
        }
        ReplicationIdSet primaryIds = snapshot->getSnapshot();
        std::string backupStr = getArguments().getAsString(ReplicatingSubscription::QPID_ID_SET);
        ReplicationIdSet backupIds;
        if (!backupStr.empty()) backupIds = decodeStr<ReplicationIdSet>(backupStr);

        // Initial dequeues are messages on backup but not on primary.
        ReplicationIdSet initDequeues = backupIds - primaryIds;
        QueuePosition front,back;
        queue->getRange(front, back, broker::REPLICATOR); // Outside lock, getRange locks queue
        {
            sys::Mutex::ScopedLock l(lock); // Concurrent calls to dequeued()
            dequeues += initDequeues;       // Messages on backup that are not on primary.
            skipEnqueue = backupIds - initDequeues; // Messages already on the backup.
            // Queue front is moving but we know this subscriptions will start at a
            // position >= front so if front is safe then position must be.
            position = front;

            QPID_LOG(debug, logPrefix << "Subscribed: primary ["
                     << front << "," << back << "]=" << primaryIds
                     << ", guarded " << guard->getFirst()
                     << ", backup (keep " << skipEnqueue << ", drop " << initDequeues << ")");
            checkReady(l);
        }
        Mutex::ScopedLock l(lock); // Note dequeued() can be called concurrently.
        // Send initial dequeues to the backup.
        // There must be a shared_ptr(this) when sending.
        sendDequeueEvent(l);
    }
    catch (const std::exception& e) {
        QPID_LOG(error, logPrefix << "Subscribe failed: " << e.what());
        throw;
    }
}

ReplicatingSubscription::~ReplicatingSubscription() {}

void ReplicatingSubscription::stopped() {
    Mutex::ScopedLock l(lock);
    // We have reached the last available message on the queue.
    //
    // Note that if messages have been removed out-of-order this may not be the
    // head of the queue. We may not even have reached the guard
    // position. However there are no more messages to protect and we will not
    // be advanced any further, so we should consider ourselves guarded for
    // purposes of readiness.
    wasStopped = true;
    checkReady(l);
}

// True if the next position for the ReplicatingSubscription is a guarded position.
bool ReplicatingSubscription::isGuarded(sys::Mutex::ScopedLock&) {
    // See comment in stopped()
    return wasStopped || (position+1 >= guard->getFirst());
}

// Message is delivered in the subscription's connection thread.
bool ReplicatingSubscription::deliver(
    const qpid::broker::QueueCursor& c, const qpid::broker::Message& m)
{
    Mutex::ScopedLock l(lock);
    ReplicationId id = m.getReplicationId();
    position = m.getSequence();
    try {
        bool result = false;
        if (skipEnqueue.contains(id)) {
            QPID_LOG(trace, logPrefix << "Skip " << logMessageId(*getQueue(), m));
            skipEnqueue -= id;
            guard->complete(id); // This will never be acknowledged.
            notify();
            result = true;
        }
        else {
            QPID_LOG(trace, logPrefix << "Replicated " << logMessageId(*getQueue(), m));
            if (!ready && !isGuarded(l)) unready += id;
            sendIdEvent(id, l);
            result = ConsumerImpl::deliver(c, m);
        }
        checkReady(l);
        return result;
    } catch (const std::exception& e) {
        QPID_LOG(critical, logPrefix << "Error replicating " << logMessageId(*getQueue(), m)
                 << ": " << e.what());
        throw;
    }
}

void ReplicatingSubscription::checkReady(sys::Mutex::ScopedLock& l) {
    if (!ready && isGuarded(l) && unready.empty()) {
        ready = true;
        sys::Mutex::ScopedUnlock u(lock);
        // Notify Primary that a subscription is ready.
        if (position+1 >= guard->getFirst()) {
            QPID_LOG(debug, logPrefix << "Caught up at " << position);
        } else {
            QPID_LOG(debug, logPrefix << "Caught up at " << position << "short of guard at " << guard->getFirst());
        }

        if (primary) primary->readyReplica(*this);
    }
}

// Called in the subscription's connection thread.
void ReplicatingSubscription::cancel()
{
    {
        Mutex::ScopedLock l(lock);
        if (cancelled) return;
        cancelled = true;
    }
    QPID_LOG(debug, logPrefix << "Cancelled");
    getQueue()->getObservers().remove(
        boost::dynamic_pointer_cast<ReplicatingSubscription>(shared_from_this()));
    guard->cancel();
    ConsumerImpl::cancel();
}

// Consumer override, called on primary in the backup's IO thread.
void ReplicatingSubscription::acknowledged(const broker::DeliveryRecord& r) {
    // Finish completion of message, it has been acknowledged by the backup.
    ReplicationId id = r.getReplicationId();
    QPID_LOG(trace, logPrefix << "Acknowledged " <<
             logMessageId(*getQueue(), r.getMessageId(), id));
    guard->complete(id);
    {
        Mutex::ScopedLock l(lock);
        unready -= id;
        checkReady(l);
    }
    ConsumerImpl::acknowledged(r);
}

// Called with lock held. Called in subscription's connection thread.
void ReplicatingSubscription::sendDequeueEvent(Mutex::ScopedLock& l)
{
    if (dequeues.empty()) return;
    QPID_LOG(trace, logPrefix << "Sending dequeues " << dequeues);
    DequeueEvent d(dequeues);
    dequeues.clear();
    sendEvent(d, l);
}

// Called after the message has been removed
// from the deque and under the messageLock in the queue. Called in
// arbitrary connection threads.
void ReplicatingSubscription::dequeued(const broker::Message& m)
{
    ReplicationId id = m.getReplicationId();
    QPID_LOG(trace, logPrefix << "Dequeued ID " << id);
    {
        Mutex::ScopedLock l(lock);
        dequeues.add(id);
    }
    notify();                   // Ensure a call to doDispatch
}


// Called with lock held. Called in subscription's connection thread.
void ReplicatingSubscription::sendIdEvent(ReplicationId pos, Mutex::ScopedLock& l)
{
    sendEvent(IdEvent(pos), l);
}

void ReplicatingSubscription::sendEvent(const Event& event, Mutex::ScopedLock&)
{
    Mutex::ScopedUnlock u(lock);
    // Send the event directly to the base consumer implementation.  The dummy
    // consumer prevents acknowledgements being handled, which is what we want
    // for events
    ConsumerImpl::deliver(QueueCursor(), event.message(), boost::shared_ptr<Consumer>());
}

// Called in subscription's connection thread.
bool ReplicatingSubscription::doDispatch()
{
    {
        Mutex::ScopedLock l(lock);
        if (!dequeues.empty()) sendDequeueEvent(l);
    }
    try {
        return ConsumerImpl::doDispatch();
    }
    catch (const std::exception& e) {
        QPID_LOG(warning, logPrefix << " exception in dispatch: " << e.what());
        return false;
    }
}

}} // namespace qpid::ha
