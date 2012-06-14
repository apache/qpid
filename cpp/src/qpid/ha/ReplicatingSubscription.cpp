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
        const SequenceNumber& first_,
        const SequenceNumber& last_ // Inclusive
    ) : subscription(rs), first(first_), last(last_)
    {
        assert(first <= last);
        // INVARIANT no deques are needed for positions <= at.
        at = first - 1;
    }

    void operator()(const QueuedMessage& qm) {
        if (qm.position >= first && qm.position <= last) {
            if (qm.position > at+1)
                subscription.dequeued(at+1, qm.position-1);
            at = qm.position;
        }
    }
    // Must call after scanning the queue.
    void finish() {
        if (at < last) subscription.dequeued(at+1, last);
    }

  private:
    ReplicatingSubscription& subscription;
    SequenceNumber first;
    SequenceNumber last;
    SequenceNumber at;
};

string mask(const string& in)
{
    return DOLLAR + in + INTERNAL;
}


/** Dummy consumer used to get the front position on the queue */
class GetPositionConsumer : public Consumer
{
  public:
    GetPositionConsumer() :
        Consumer("ha.GetPositionConsumer."+types::Uuid(true).str(), false) {}
    bool deliver(broker::QueuedMessage& ) { return true; }
    void notify() {}
    bool filter(boost::intrusive_ptr<broker::Message>) { return true; }
    bool accept(boost::intrusive_ptr<broker::Message>) { return true; }
    void cancel() {}
    void acknowledged(const broker::QueuedMessage&) {}
    bool browseAcquired() const { return true; }
    broker::OwnershipToken* getSession() { return 0; }
};


bool ReplicatingSubscription::getNext(
    broker::Queue& q, SequenceNumber from, SequenceNumber& result)
{
    boost::shared_ptr<Consumer> c(new GetPositionConsumer);
    c->setPosition(from);
    if (!q.dispatch(c)) return false;
    result = c->getPosition();
    return true;
}

bool ReplicatingSubscription::getFront(broker::Queue& q, SequenceNumber& front) {
    // FIXME aconway 2012-05-23: won't wrap, assumes 0 is < all messages in queue.
    return getNext(q, 0, front);
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

struct QueueRange {
    bool empty;
    SequenceNumber front;
    SequenceNumber back;

    QueueRange() { }

    QueueRange(broker::Queue& q) {
        back = q.getPosition();
        front = back+1;         // Assume empty
        empty = !ReplicatingSubscription::getFront(q, front);
        assert(empty || front <= back);
    }

    QueueRange(const framing::FieldTable args) {
        back = args.getAsInt(ReplicatingSubscription::QPID_BACK);
        front = back+1;
        empty = !args.isSet(ReplicatingSubscription::QPID_FRONT);
        if (!empty) {
            front = args.getAsInt(ReplicatingSubscription::QPID_FRONT);
            if (back < front)
                throw InvalidArgumentException(
                    QPID_MSG("Invalid range [" << front << "," << back <<"]"));
        }
    }
};

ostream& operator<<(ostream& o, const QueueRange& qr) {
    if (qr.front > qr.back) return o << "[-" << qr.back << "]";
    else return o << "[" << qr.front << "," << qr.back << "]";
}

ReplicatingSubscription::ReplicatingSubscription(
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
    dummy(new Queue(mask(name))),
    ready(false)
{
    try {
        FieldTable ft;
        if (!arguments.getTable(ReplicatingSubscription::QPID_BROKER_INFO, ft))
            throw Exception("Replicating subscription does not have broker info: " + tag);
        info.assign(ft);

        // Set a log prefix message that identifies the remote broker.
        ostringstream os;
        os << "Primary " << queue->getName() << "@" << info.getLogId() << ": ";
        logPrefix = os.str();

        // FIXME aconway 2012-06-10: unsafe to rely in queue front or position they are changing?

        QueueRange primary(*queue); // The local primary queue.
        QueueRange backup(arguments); // The remote backup state.
        backupPosition = backup.back;

        // NOTE: Once the guard is attached we can have concurrent
        // calles to dequeued so we need to lock use of this->deques.
        //

        // However we must attach the guard _before_ we scan for
        // initial dequeues to be sure we don't miss any dequeues
        // between the scan and attaching the guard.
        if (Primary::get()) guard = Primary::get()->getGuard(queue, info);
        if (!guard) guard.reset(new QueueGuard(*queue, getBrokerInfo()));
        guard->attach(*this);

        // We can re-use some backup messages if backup and primary queues
        // overlap and the backup is not missing messages at the front of the queue.
        // FIXME aconway 2012-06-10: disable re-use of backup queue till stall problem is solved.
        /*        if (!primary.empty &&   // Primary not empty
            !backup.empty &&    // Backup not empty
            primary.front >= backup.front && // Not missing messages at the front
            primary.front <= backup.back     // Overlap
        )
        {
            // Scan primary queue for gaps that should be dequeued on the backup.
            // NOTE: this runs concurrently with the guard calling dequeued()
            // FIXME aconway 2012-05-22: optimize queue iteration
            DequeueScanner scan(*this, backup.front, backup.back);
            queue->eachMessage(scan);
            scan.finish();
            // If the backup was ahead it has been pruned back to the primary.
            position = std::min(guard->getFirstSafe(), backup.back);
        }
        else */ {
            // Clear the backup queue and reset to start browsing at the
            // front of the primary queue.
            if (!backup.empty) dequeued(backup.front, backup.back);
            position = primary.front - 1; // Start consuming from front.
        }
        QPID_LOG(debug, logPrefix << "Subscribed: "
                 << " backup:" << backup
                 << " backup position:" << backupPosition
                 << " primary:" << primary
                 << " position:" << position
        );

        // Are we ready yet?
        if (position+1 >= guard->getFirstSafe()) // Next message will be safe.
            setReady();
        else
            QPID_LOG(debug, logPrefix << "Catching up from "
                     << position << " to " << guard->getFirstSafe());
    }
    catch (const std::exception& e) {
        throw InvalidArgumentException(QPID_MSG(logPrefix << e.what()
                                                << ": arguments=" << arguments));
    }
}

ReplicatingSubscription::~ReplicatingSubscription() {
    QPID_LOG(debug, logPrefix << "Detroyed replicating subscription");
}

// Called in subscription's connection thread when the subscription is created.
// Called separate from ctor because sending events requires
// shared_from_this
//
void ReplicatingSubscription::initialize() {
    Mutex::ScopedLock l(lock); // Note dequeued() can be called concurrently.

    // Send initial dequeues and position to the backup.
    // There must be a shared_ptr(this) when sending.
    sendDequeueEvent(l);
    sendPositionEvent(position, l);
    backupPosition = position;
}

// Message is delivered in the subscription's connection thread.
bool ReplicatingSubscription::deliver(QueuedMessage& qm) {
    try {
        // Add position events for the subscribed queue, not the internal event queue.
        if (qm.queue == getQueue().get()) {
            QPID_LOG(trace, logPrefix << "Replicating " << qm);
            {
                Mutex::ScopedLock l(lock);
                assert(position == qm.position);
                // qm.position is the position of the newly enqueued qm on local queue.
                // backupPosition is latest position on backup queue before enqueueing
                if (qm.position <= backupPosition)
                    throw Exception(
                        QPID_MSG("Expected position >  " << backupPosition
                                 << " but got " << qm.position));
                if (qm.position - backupPosition > 1) {
                    // Position has advanced because of messages dequeued ahead of us.
                    // Send the position before qm was enqueued.
                    sendPositionEvent(qm.position-1, l);
                }
                // Backup will automatically advance by 1 on delivery of message.
                backupPosition = qm.position;
            }
            // Deliver the message
            bool delivered = ConsumerImpl::deliver(qm);
            // If we have advanced past the initial position, the backup is ready.
            if (qm.position >= guard->getFirstSafe()) setReady();
            return delivered;
        }
        else
            return ConsumerImpl::deliver(qm); // Message is for internal event queue.
    } catch (const std::exception& e) {
        QPID_LOG(critical, logPrefix << "Error replicating " << qm
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
    QPID_LOG(info, logPrefix << "Caught up at " << getPosition());
    if (Primary::get()) Primary::get()->readyReplica(*this);
}

// Called in the subscription's connection thread.
void ReplicatingSubscription::cancel()
{
    guard->cancel();
    ConsumerImpl::cancel();
}

// Consumer override, called on primary in the backup's IO thread.
void ReplicatingSubscription::acknowledged(const QueuedMessage& qm) {
    if (qm.queue == getQueue().get()) { // Don't complete messages on the internal queue
        // Finish completion of message, it has been acknowledged by the backup.
        QPID_LOG(trace, logPrefix << "Acknowledged " << qm);
        guard->complete(qm);
    }
    ConsumerImpl::acknowledged(qm);
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
void ReplicatingSubscription::dequeued(const QueuedMessage& qm)
{
    assert (qm.queue == getQueue().get());
     QPID_LOG(trace, logPrefix << "Dequeued " << qm);
    {
        Mutex::ScopedLock l(lock);
        dequeues.add(qm.position);
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
        Mutex::ScopedLock l(lock);
        if (!dequeues.empty()) sendDequeueEvent(l);
    }
    return ConsumerImpl::doDispatch();
}

}} // namespace qpid::ha
