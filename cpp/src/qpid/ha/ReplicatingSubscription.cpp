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

#include "QueueGuard.h"
#include "QueueRange.h"
#include "QueueReplicator.h"
#include "ReplicatingSubscription.h"
#include "Primary.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/SessionContext.h"
#include "qpid/broker/ConnectionState.h"
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/log/Statement.h"
#include "qpid/types/Uuid.h"
#include <sstream>

namespace qpid {
namespace ha {

using namespace framing;
using namespace broker;
using namespace std;
using sys::Mutex;

const string ReplicatingSubscription::QPID_REPLICATING_SUBSCRIPTION("qpid.ha-replicating-subscription");
const string ReplicatingSubscription::QPID_BACK("qpid.ha-back");
const string ReplicatingSubscription::QPID_FRONT("qpid.ha-front");
const string ReplicatingSubscription::QPID_BROKER_INFO("qpid.ha-broker-info");

namespace {
const string DOLLAR("$");
const string INTERNAL("-internal");
} // namespace

// Scan the queue for gaps and add them to the subscriptions dequed set.
class DequeueScanner
{
  public:
    DequeueScanner(
        ReplicatingSubscription& rs,
        SequenceNumber front_,
        SequenceNumber back_    // Inclusive
    ) : subscription(rs), front(front_), back(back_)
    {
        assert(front <= back);
        // INVARIANT deques have been added for positions <= at.
        at = front - 1;
    }

    void operator()(const Message& m) {
        if (m.getSequence() >= front && m.getSequence() <= back) {
            if (m.getSequence() > at+1) subscription.dequeued(at+1, m.getSequence()-1);
            at = m.getSequence();
        }
    }

    // Must call after scanning the queue.
    void finish() {
        if (at < back) subscription.dequeued(at+1, back);
    }

  private:
    ReplicatingSubscription& subscription;
    SequenceNumber front;
    SequenceNumber back;
    SequenceNumber at;
};

string mask(const string& in)
{
    return DOLLAR + in + INTERNAL;
}

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
    if (arguments.isSet(QPID_REPLICATING_SUBSCRIPTION)) {
        rs.reset(new ReplicatingSubscription(
                     parent, name, queue, ack, acquire, exclusive, tag,
                     resumeId, resumeTtl, arguments));
        rs->initialize();
    }
    return rs;
}

ReplicatingSubscription::ReplicatingSubscription(
    SemanticState* parent,
    const string& name,
    Queue::shared_ptr queue,
    bool ack,
    bool /*acquire*/,
    bool exclusive,
    const string& tag,
    const string& resumeId,
    uint64_t resumeTtl,
    const framing::FieldTable& arguments
) : ConsumerImpl(parent, name, queue, ack, REPLICATOR, exclusive, tag,
                 resumeId, resumeTtl, arguments),
    ready(false)
{
    try {
        FieldTable ft;
        if (!arguments.getTable(ReplicatingSubscription::QPID_BROKER_INFO, ft))
            throw Exception("Replicating subscription does not have broker info: " + tag);
        info.assign(ft);

        // Set a log prefix message that identifies the remote broker.
        ostringstream os;
        os << "Primary " << queue->getName() << "@" << info << ": ";
        logPrefix = os.str();

        // NOTE: Once the guard is attached we can have concurrent
        // calls to dequeued so we need to lock use of this->dequeues.
        //
        // However we must attach the guard _before_ we scan for
        // initial dequeues to be sure we don't miss any dequeues
        // between the scan and attaching the guard.
        if (Primary::get()) guard = Primary::get()->getGuard(queue, info);
        if (!guard) guard.reset(new QueueGuard(*queue, info));
        guard->attach(*this);

        QueueRange backup(arguments); // Remote backup range.
        QueueRange backupOriginal(backup);
        QueueRange primary(guard->getRange()); // Unguarded range when the guard was set.
        backupPosition = backup.back;

        // Sync backup and primary queues, don't send messages already on the backup

        if (backup.front > primary.front || // Missing messages at front
            backup.back < primary.front ||  // No overlap
            primary.empty() || backup.empty()) // Empty
        {
            // No useful overlap - erase backup and start from the beginning
            if (!backup.empty()) dequeued(backup.front, backup.back);
            position = primary.front-1;
        }
        else {  // backup and primary do overlap.
            // Remove messages from backup that are not in primary.
            if (primary.back < backup.back) {
                dequeued(primary.back+1, backup.back); // Trim excess messages at back
                backup.back = primary.back;
            }
            if (backup.front < primary.front) {
                dequeued(backup.front, primary.front-1); // Trim excess messages at front
                backup.front = primary.front;
            }
            DequeueScanner scan(*this, backup.front, backup.back);
            // FIXME aconway 2012-06-15: Optimize queue traversal, only in range.
            queue->eachMessage(boost::ref(scan)); // Remove missing messages in between.
            scan.finish();
            position = backup.back;
            //move cursor to position
            queue->seek(*this, position);
        }
        // NOTE: we are assuming that the messages that are on the backup are
        // consistent with those on the primary. If the backup is a replica
        // queue and hasn't been tampered with then that will be the case.

        QPID_LOG(debug, logPrefix << "Subscribed:"
                 << " backup:" << backupOriginal << " adjusted backup:" << backup
                 << " primary:" << primary
                 << " catch-up: " << position << "-" << primary.back
                 << "(" << primary.back-position << ")");

        // Check if we are ready yet.
        if (guard->subscriptionStart(position)) setReady();
    }
    catch (const std::exception& e) {
        QPID_LOG(error, logPrefix << "Creation error: " << e.what()
                 << ": arguments=" << getArguments());
        throw;
    }
}

ReplicatingSubscription::~ReplicatingSubscription() {}

// Called in subscription's connection thread when the subscription is created.
// Called separate from ctor because sending events requires
// shared_from_this
//
void ReplicatingSubscription::initialize() {
    try {
        Mutex::ScopedLock l(lock); // Note dequeued() can be called concurrently.

        // Send initial dequeues and position to the backup.
        // There must be a shared_ptr(this) when sending.
        sendDequeueEvent(l);
        sendPositionEvent(position, l);
        backupPosition = position;
    }
    catch (const std::exception& e) {
        QPID_LOG(error, logPrefix << "Initialization error: " << e.what()
                 << ": arguments=" << getArguments());
        throw;
    }
}

// Message is delivered in the subscription's connection thread.
bool ReplicatingSubscription::deliver(
    const qpid::broker::QueueCursor& c, const qpid::broker::Message& m)
{
    try {
        QPID_LOG(trace, logPrefix << "Replicating " << m.getSequence());
        {
            Mutex::ScopedLock l(lock);
            position = m.getSequence();

            // m.getSequence() is the position of the new message on local queue.
            // backupPosition is latest position on backup queue before enqueueing
            if (m.getSequence() <= backupPosition)
                throw Exception(
                    QPID_MSG(logPrefix << "Expected position >  " << backupPosition
                             << " but got " << m.getSequence()));
            if (m.getSequence() - backupPosition > 1) {
                // Position has advanced because of messages dequeued ahead of us.
                // Send the position before message was enqueued.
                sendPositionEvent(m.getSequence()-1, l);
            }
            // Backup will automatically advance by 1 on delivery of message.
            backupPosition = m.getSequence();
        }
        return ConsumerImpl::deliver(c, m);
    } catch (const std::exception& e) {
        QPID_LOG(critical, logPrefix << "Error replicating " << m.getSequence()
                 << ": " << e.what());
        throw;
    }
}

void ReplicatingSubscription::setReady() {
    {
        Mutex::ScopedLock l(lock);
        if (ready) return;
        ready = true;
    }
    // Notify Primary that a subscription is ready.
    QPID_LOG(debug, logPrefix << "Caught up");
    if (Primary::get()) Primary::get()->readyReplica(*this);
}

// Called in the subscription's connection thread.
void ReplicatingSubscription::cancel()
{
    QPID_LOG(debug, logPrefix << "Cancelled");
    guard->cancel();
    ConsumerImpl::cancel();
}

// Consumer override, called on primary in the backup's IO thread.
void ReplicatingSubscription::acknowledged(const broker::DeliveryRecord& r) {
    // Finish completion of message, it has been acknowledged by the backup.
    QPID_LOG(trace, logPrefix << "Acknowledged " << r.getMessageId());
    guard->complete(r.getMessageId());
    // If next message is protected by the guard then we are ready
    if (r.getMessageId() >= guard->getRange().back) setReady();
    ConsumerImpl::acknowledged(r);
}

// Called with lock held. Called in subscription's connection thread.
void ReplicatingSubscription::sendDequeueEvent(Mutex::ScopedLock&)
{
    if (dequeues.empty()) return;
    QPID_LOG(trace, logPrefix << "Sending dequeues " << dequeues);
    string buf(dequeues.encodedSize(),'\0');
    framing::Buffer buffer(&buf[0], buf.size());
    dequeues.encode(buffer);
    dequeues.clear();
    buffer.reset();
    {
        Mutex::ScopedUnlock u(lock);
        sendEvent(QueueReplicator::DEQUEUE_EVENT_KEY, buffer);
    }
}

// Called via QueueObserver::dequeued override on guard.
// Called after the message has been removed
// from the deque and under the messageLock in the queue. Called in
// arbitrary connection threads.
void ReplicatingSubscription::dequeued(const Message& m)
{
    QPID_LOG(trace, logPrefix << "Dequeued " << m.getSequence());
    {
        Mutex::ScopedLock l(lock);
        dequeues.add(m.getSequence());
    }
    notify();                   // Ensure a call to doDispatch
}

// Called during construction while scanning for initial dequeues.
void ReplicatingSubscription::dequeued(SequenceNumber first, SequenceNumber last) {
    QPID_LOG(trace, logPrefix << "Initial dequeue [" << first << ", " << last << "]");
    {
        Mutex::ScopedLock l(lock);
        dequeues.add(first,last);
    }
}

// Called with lock held. Called in subscription's connection thread.
void ReplicatingSubscription::sendPositionEvent(SequenceNumber pos, Mutex::ScopedLock&)
{
    if (pos == backupPosition) return; // No need to send.
    QPID_LOG(trace, logPrefix << "Sending position " << pos << ", was " << backupPosition);
    string buf(pos.encodedSize(),'\0');
    framing::Buffer buffer(&buf[0], buf.size());
    pos.encode(buffer);
    buffer.reset();
    {
        Mutex::ScopedUnlock u(lock);
        sendEvent(QueueReplicator::POSITION_EVENT_KEY, buffer);
    }
}

void ReplicatingSubscription::sendEvent(const std::string& key, framing::Buffer& buffer)
{
    //generate event message
    boost::intrusive_ptr<qpid::broker::amqp_0_10::MessageTransfer> event(new qpid::broker::amqp_0_10::MessageTransfer());
    AMQFrame method((MessageTransferBody(ProtocolVersion(), string(), 0, 0)));
    AMQFrame header((AMQHeaderBody()));
    AMQFrame content((AMQContentBody()));
    content.castBody<AMQContentBody>()->decode(buffer, buffer.getSize());
    header.setBof(false);
    header.setEof(false);
    header.setBos(true);
    header.setEos(true);
    content.setBof(false);
    content.setEof(true);
    content.setBos(true);
    content.setEos(true);
    event->getFrames().append(method);
    event->getFrames().append(header);
    event->getFrames().append(content);

    DeliveryProperties* props =
        event->getFrames().getHeaders()->get<DeliveryProperties>(true);
    props->setRoutingKey(key);
    // Send the event directly to the base consumer implementation.
    //dummy consumer prevents acknowledgements being handled, which is what we want for events
    ConsumerImpl::deliver(QueueCursor(), Message(event, 0), boost::shared_ptr<Consumer>());
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
        // FIXME aconway 2012-10-05: detect queue deletion, no warning.
        QPID_LOG(warning, logPrefix << " exception in dispatch: " << e.what());
        return false;
    }
}

}} // namespace qpid::ha
