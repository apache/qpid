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

#include "ReplicatingSubscription.h"
#include "Primary.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/SessionContext.h"
#include "qpid/broker/ConnectionState.h"
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

const string ReplicatingSubscription::QPID_REPLICATING_SUBSCRIPTION("qpid.replicating-subscription");

namespace {
const string DOLLAR("$");
const string INTERNAL("-internal");
} // namespace

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
                     LogPrefix(haBroker),
                     parent, name, queue, ack, acquire, exclusive, tag,
                     resumeId, resumeTtl, arguments));
        queue->addObserver(rs);
        // NOTE: readyPosition must be set _after_ addObserver, so
        // messages can't be enqueued after setting readyPosition
        // but before registering the observer.
        rs->setReadyPosition();
    }
    return rs;
}

ReplicatingSubscription::ReplicatingSubscription(
    LogPrefix lp,
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
) : ConsumerImpl(parent, name, queue, ack, acquire, exclusive, tag,
                 resumeId, resumeTtl, arguments),
    logPrefix(lp, queue->getName()),
    dummy(new Queue(mask(name))),
    ready(false)
{
    // Separate the remote part from a "local-remote" address for logging.
    string address = parent->getSession().getConnection().getUrl();
    size_t i = address.find('-');
    if (i != string::npos) address = address.substr(i+1);
    logSuffix = " (" + address + ")";

    // FIXME aconway 2011-12-09: Failover optimization removed.
    // There was code here to re-use messages already on the backup
    // during fail-over. This optimization was removed to simplify
    // the logic till we get the basic replication stable, it
    // can be re-introduced later. Last revision with the optimization:
    // r1213258 | QPID-3603: Fix QueueReplicator subscription parameters.

    // FIXME aconway 2011-12-15: ConsumerImpl::position is left at 0
    // so we will start consuming from the lowest numbered message.
    // This is incorrect if the sequence number wraps around, but
    // this is what all consumers currently do.

    QPID_LOG(debug, logPrefix << "Created replicating subscription" << logSuffix);
}

ReplicatingSubscription::~ReplicatingSubscription() {
    QPID_LOG(debug, logPrefix << "Detroyed replicating subscription" << logSuffix);
}

// Called in subscription's connection thread when the subscription is created.
void ReplicatingSubscription::setReadyPosition() {
    // Don't need to lock, this is called only on creation.

    // All messages after this position have been seen by us as QueueObserver.
    readyPosition = getQueue()->getPosition();
    // Create a separate subscription to browse the front message on
    // the queue so that we can test for queue empty.
    boost::shared_ptr<Consumer> c(new GetPositionConsumer);
    bool found = getQueue()->dispatch(c);
    SequenceNumber front = c->getPosition();
    if (!found || front >= readyPosition) {
        // The queue is empty, or has already advanced past the ready position.
        QPID_LOG(debug, logPrefix << "backup subscribed, no catch up, at "
                 << readyPosition << logSuffix);
        // Fake lock, only called during creation:
        setReady(*(sys::Mutex::ScopedLock*)0);
    }
    else {
        QPID_LOG(debug, logPrefix << "backup subscribed, catching up "
                 << front << "-" << readyPosition << logSuffix);
    }
}

// Message is delivered in the subscription's connection thread.
bool ReplicatingSubscription::deliver(QueuedMessage& qm) {
    try {
        // Add position events for the subscribed queue, not for the internal event queue.
        if (qm.queue == getQueue().get()) {
            QPID_LOG(trace, logPrefix << "replicating " << qm << logSuffix);
            {
                sys::Mutex::ScopedLock l(lock);
                assert(position == qm.position);
                // qm.position is the position of the newly enqueued qm on the local queue.
                // backupPosition is latest position on backup queue before enqueueing
                if (qm.position <= backupPosition)
                    throw Exception(
                        QPID_MSG("Expected position >  " << backupPosition
                                 << " but got " << qm.position));
                if (qm.position - backupPosition > 1) {
                    // Position has advanced because of messages dequeued ahead of us.
                    SequenceNumber send(qm.position);
                    --send;   // Send the position before qm was enqueued.
                    sendPositionEvent(send);
                }
                backupPosition = qm.position;
            }
            // Deliver the message
            bool delivered = ConsumerImpl::deliver(qm);
            {
                sys::Mutex::ScopedLock l(lock);
                // If we have advanced to the initial position, the backup is ready.
                if (qm.position >= readyPosition) setReady(l);
            }
            return delivered;
        }
        else
            return ConsumerImpl::deliver(qm); // Message is for internal event queue.
    } catch (const std::exception& e) {
        QPID_LOG(critical, logPrefix << "error replicating " << qm
                 << logSuffix << ": " << e.what());
        throw;
    }
}

// Send a ready event to the backup.
void ReplicatingSubscription::setReady(const sys::Mutex::ScopedLock&) {
    if (ready) return;
    ready = true;
    QPID_LOG(info, logPrefix << "Caught up at " << getPosition() << logSuffix);
    // Notify Primary that a subscription is ready.
    if (Primary::get()) Primary::get()->readyReplica(getQueue()->getName());
}

// INVARIANT: delayed contains msg <=> we have outstanding startCompletion on msg

// Mark a message completed. May be called by acknowledge or dequeued,
// in arbitrary connection threads.
void ReplicatingSubscription::complete(
    const QueuedMessage& qm, const sys::Mutex::ScopedLock&)
{
    // Handle completions for the subscribed queue, not the internal event queue.
    if (qm.queue == getQueue().get()) {
        QPID_LOG(trace, logPrefix << "completed " << qm << logSuffix);
        Delayed::iterator i= delayed.find(qm.position);
        // The same message can be completed twice, by acknowledged and
        // dequeued, remove it from the set so it only gets completed
        // once.
        if (i != delayed.end()) {
            assert(i->second.payload == qm.payload);
            qm.payload->getIngressCompletion().finishCompleter();
            delayed.erase(i);
        }
    }
}

// Called before we get notified of the message being available and
// under the message lock in the queue.
// Called in arbitrary connection thread *with the queue lock held*
void ReplicatingSubscription::enqueued(const QueuedMessage& qm) {
    // Delay completion
    QPID_LOG(trace, logPrefix << "delaying completion of " << qm << logSuffix);
    qm.payload->getIngressCompletion().startCompleter();
    {
        sys::Mutex::ScopedLock l(lock);
        assert(delayed.find(qm.position) == delayed.end());
        delayed[qm.position] = qm;
    }
}

// Function to complete a delayed message, called by cancel()
void ReplicatingSubscription::cancelComplete(
    const Delayed::value_type& v, const sys::Mutex::ScopedLock&)
{
    QPID_LOG(trace, logPrefix << "cancel completed " << v.second << logSuffix);
    v.second.payload->getIngressCompletion().finishCompleter();
}

// Called in the subscription's connection thread.
void ReplicatingSubscription::cancel()
{
    getQueue()->removeObserver(
        boost::dynamic_pointer_cast<QueueObserver>(shared_from_this()));
    {
        sys::Mutex::ScopedLock l(lock);
        QPID_LOG(debug, logPrefix << "cancel backup subscription to "
                 << getQueue()->getName() << logSuffix);
        for_each(delayed.begin(), delayed.end(),
                 boost::bind(&ReplicatingSubscription::cancelComplete, this, _1, boost::ref(l)));
        delayed.clear();
    }
    ConsumerImpl::cancel();
}

// Consumer override, called on primary in the backup's IO thread.
void ReplicatingSubscription::acknowledged(const QueuedMessage& msg) {
    sys::Mutex::ScopedLock l(lock);
    // Finish completion of message, it has been acknowledged by the backup.
    complete(msg, l);
}

// Hide the "queue deleted" error for a ReplicatingSubscription when a
// queue is deleted, this is normal and not an error.
bool ReplicatingSubscription::hideDeletedError() { return true; }

// Called with lock held. Called in subscription's connection thread.
void ReplicatingSubscription::sendDequeueEvent(const sys::Mutex::ScopedLock&)
{
    QPID_LOG(trace, logPrefix << "sending dequeues " << dequeues
             << " from " << getQueue()->getName() << logSuffix);
    string buf(dequeues.encodedSize(),'\0');
    framing::Buffer buffer(&buf[0], buf.size());
    dequeues.encode(buffer);
    buffer.reset();
    sendEvent(QueueReplicator::DEQUEUE_EVENT_KEY, buffer);
}

// QueueObserver override. Called after the message has been removed
// from the deque and under the messageLock in the queue. Called in
// arbitrary connection threads.
void ReplicatingSubscription::dequeued(const QueuedMessage& qm)
{
    {
        QPID_LOG(trace, logPrefix << "dequeued " << qm << logSuffix);
        sys::Mutex::ScopedLock l(lock);
        dequeues.add(qm.position);
        // If we have not yet sent this message to the backup, then
        // complete it now as it will never be accepted.
        if (qm.position > position) complete(qm, l);
    }
    notify();                   // Ensure a call to doDispatch
}

// Called with lock held. Called in subscription's connection thread.
void ReplicatingSubscription::sendPositionEvent(SequenceNumber position)
{
    QPID_LOG(trace, logPrefix << "sending position " << position
             << ", was " << backupPosition << logSuffix);
    string buf(backupPosition.encodedSize(),'\0');
    framing::Buffer buffer(&buf[0], buf.size());
    position.encode(buffer);
    buffer.reset();
    sendEvent(QueueReplicator::POSITION_EVENT_KEY, buffer);
}

void ReplicatingSubscription::sendEvent(const std::string& key, framing::Buffer& buffer)
{
    //generate event message
    boost::intrusive_ptr<Message> event = new Message();
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
    // We don't really need a queue here but we pass a dummy queue
    // to conform to the consumer API.
    QueuedMessage qm(dummy.get(), event);
    ConsumerImpl::deliver(qm);
}


// Called in subscription's connection thread.
bool ReplicatingSubscription::doDispatch()
{
    {
        sys::Mutex::ScopedLock l(lock);
        if (!dequeues.empty()) {
            sendDequeueEvent(l);
            dequeues.clear();
        }
    }
    return ConsumerImpl::doDispatch();
}

}} // namespace qpid::ha
