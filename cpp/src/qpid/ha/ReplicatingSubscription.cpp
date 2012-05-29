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
#include "HaBroker.h"
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
const string ReplicatingSubscription::QPID_HIGH_SEQUENCE_NUMBER("qpid.high-sequence-number");
const string ReplicatingSubscription::QPID_LOW_SEQUENCE_NUMBER("qpid.low-sequence-number");
const string ReplicatingSubscription::QPID_BROKER_INFO("qpid.broker-info");

namespace {
const string DOLLAR("$");
const string INTERNAL("-internal");
} // namespace

class DequeueRemover
{
  public:
    DequeueRemover(
        SequenceSet& r,
        const SequenceNumber& s,
        const SequenceNumber& e
    ) : dequeues(r), start(s), end(e)
    {
        dequeues.add(start, end);
    }

    void operator()(const QueuedMessage& message) {
 if (message.position >= start && message.position <= end) {
            //i.e. message is within the intial range and has not been dequeued,
            //so remove it from the dequeues
            dequeues.remove(message.position);
        }
    }

  private:
    SequenceSet& dequeues;
    const SequenceNumber start;
    const SequenceNumber end;
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
                     haBroker,
                     parent, name, queue, ack, acquire, exclusive, tag,
                     resumeId, resumeTtl, arguments));
        queue->addObserver(rs);
        // NOTE: initialize must be called _after_ addObserver, so
        // messages can't be enqueued after setting readyPosition
        // but before registering the observer.
        rs->initialize();
    }
    return rs;
}

struct QueueRange {
    bool empty;
    SequenceNumber front;
    SequenceNumber back;

    QueueRange() { }

    // FIXME aconway 2012-05-26: fix front calculation
    QueueRange(broker::Queue& q) {
        back = q.getPosition();
        front = back+1;
        empty = !ReplicatingSubscription::getFront(q, front);
    }

    QueueRange(const framing::FieldTable args) {
        back = args.getAsInt(ReplicatingSubscription::QPID_HIGH_SEQUENCE_NUMBER);
        front = back+1;
        empty = !args.isSet(ReplicatingSubscription::QPID_LOW_SEQUENCE_NUMBER);
        if (!empty) {
            front = args.getAsInt(ReplicatingSubscription::QPID_LOW_SEQUENCE_NUMBER);
            if (back < front)
                throw InvalidArgumentException("Invalid bounds for backup queue");
        }
    }

    /** Consumer position to start consuming from the front */
    SequenceNumber browserStart() { return front-1; }
};

ostream& operator<<(ostream& o, const QueueRange& qr) {

    if (qr.front > qr.back) return o << "empty(" << qr.back << ")";
    else return o << "[" << qr.front << "," << qr.back << "]";
}

ReplicatingSubscription::ReplicatingSubscription(
    HaBroker& hb,
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
    haBroker(hb),
    logPrefix(hb),
    dummy(new Queue(mask(name))),
    ready(false)
{
    try {
        // Set a log prefix message that identifies the remote broker.
        // FIXME aconway 2012-05-24: use URL instead of host:port, include transport?
        ostringstream os;
        os << queue->getName() << "@";
        FieldTable ft;
        if (arguments.getTable(ReplicatingSubscription::QPID_BROKER_INFO, ft)) {
            BrokerInfo info(ft);
            os << info.getHostName() << ":" << info.getPort();
        }
        else
            os << parent->getSession().getConnection().getUrl();
        logPrefix.setMessage(os.str());

        QueueRange primary(*queue);
        QueueRange backup(arguments);
        backupPosition = backup.back;
        // We can re-use some backup messages if backup and primary queues
        // overlap and the backup is not missing messages at the front of the queue.
        if (!primary.empty &&   // Primary not empty
            !backup.empty &&    // Backup not empty
            primary.front >= backup.front && // Not missing messages at the front
            primary.front <= backup.back     // Overlap
        )
        {
            // Remove messages that are still on the primary queue from dequeues
            // FIXME aconway 2012-05-22: optimize to iterate only the relevant
            // section of the queue
            DequeueRemover remover(dequeues, backup.front, backup.back);
            queue->eachMessage(remover);
            position = std::min(primary.back, backup.back);
        }
        else {
            // Clear the backup queue and reset to start browsing at the
            // front of the primary queue.
            if (!backup.empty) dequeues.add(backup.front, backup.back);
            position = primary.browserStart();

        }
        QPID_LOG(debug, logPrefix << "New backup subscription " << getName()
                 << " backup range " << backup
                 << " primary range " << primary
                 << " position " << position
                 << " dequeues " << dequeues);
    }
    catch (const std::exception& e) {
        throw Exception(QPID_MSG(logPrefix << "Error setting up replication: "
                                 << e.what()));
    }
}

ReplicatingSubscription::~ReplicatingSubscription() {
    QPID_LOG(debug, logPrefix << "Detroyed replicating subscription");
}

// Called in subscription's connection thread when the subscription is created.
void ReplicatingSubscription::initialize() {
    sys::Mutex::ScopedLock l(lock); // QueueObserver methods can be called concurrently

    // Send initial dequeues and position to the backup.
    // There most be a shared_ptr(this) when sending.
    sendDequeueEvent(l);
    sendPositionEvent(position, l);
    backupPosition = position;

    // Set the ready position.  All messages after this position have
    // been seen by us as QueueObserver.
    QueueRange range;
    {
        // Drop the lock, QueueRange will lock the queues message lock
        // which is also locked around calls to enqueued() and dequeued()
        sys::Mutex::ScopedUnlock u(lock);
        range = QueueRange(*getQueue());
    }
    readyPosition = range.back;
    if (range.empty || position >= readyPosition) {
        setReady(l);
    }
    else {
        QPID_LOG(debug, logPrefix << "Backup subscription catching up from "
                 << position << " to " << readyPosition);
    }
}

// Message is delivered in the subscription's connection thread.
bool ReplicatingSubscription::deliver(QueuedMessage& qm) {
    try {
        // Add position events for the subscribed queue, not the internal event queue.
        if (qm.queue == getQueue().get()) {
            QPID_LOG(trace, logPrefix << "Replicating " << qm);
            {
                sys::Mutex::ScopedLock l(lock);
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
                // Backup will automaticall advance by 1 on delivery of message.
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
        QPID_LOG(critical, logPrefix << "Error replicating " << qm
                 << ": " << e.what());
        throw;
    }
}

void ReplicatingSubscription::setReady(const sys::Mutex::ScopedLock&) {
    if (ready) return;
    ready = true;
    // Notify Primary that a subscription is ready.
    {
        sys::Mutex::ScopedUnlock u(lock);
        QPID_LOG(info, logPrefix << "Caught up at " << getPosition());
        if (Primary::get()) Primary::get()->readyReplica(getQueue()->getName());
    }
}

// INVARIANT: delayed contains msg <=> we have outstanding startCompletion on msg

// Mark a message completed. May be called by acknowledge or dequeued,
// in arbitrary connection threads.
void ReplicatingSubscription::complete(
    const QueuedMessage& qm, const sys::Mutex::ScopedLock&)
{
    // Handle completions for the subscribed queue, not the internal event queue.
    if (qm.queue == getQueue().get()) {
        QPID_LOG(trace, logPrefix << "Completed " << qm);
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
    QPID_LOG(trace, logPrefix << "Delaying completion of " << qm);
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
    QPID_LOG(trace, logPrefix << "Cancel completed " << v.second);
    v.second.payload->getIngressCompletion().finishCompleter();
}

// Called in the subscription's connection thread.
void ReplicatingSubscription::cancel()
{
    getQueue()->removeObserver(
        boost::dynamic_pointer_cast<QueueObserver>(shared_from_this()));
    {
        sys::Mutex::ScopedLock l(lock);
        QPID_LOG(debug, logPrefix << "Cancel backup subscription to "
                 << getQueue()->getName());
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
    if (dequeues.empty()) return;
    QPID_LOG(trace, logPrefix << "Sending dequeues " << dequeues);
    string buf(dequeues.encodedSize(),'\0');
    framing::Buffer buffer(&buf[0], buf.size());
    dequeues.encode(buffer);
    dequeues.clear();
    buffer.reset();
    {
        sys::Mutex::ScopedUnlock u(lock);
        sendEvent(QueueReplicator::DEQUEUE_EVENT_KEY, buffer);
    }
}

// QueueObserver override. Called after the message has been removed
// from the deque and under the messageLock in the queue. Called in
// arbitrary connection threads.
void ReplicatingSubscription::dequeued(const QueuedMessage& qm)
{
    QPID_LOG(trace, logPrefix << "Dequeued " << qm);
    {
        sys::Mutex::ScopedLock l(lock);
        dequeues.add(qm.position);
        // If we have not yet sent this message to the backup, then
        // complete it now as it will never be accepted.
        if (qm.position > position) complete(qm, l);
    }
    notify();                   // Ensure a call to doDispatch
}

// Called with lock held. Called in subscription's connection thread.
void ReplicatingSubscription::sendPositionEvent(SequenceNumber pos, const sys::Mutex::ScopedLock&)
{
    if (pos == backupPosition) return; // No need to send.
    QPID_LOG(trace, logPrefix << "Sending position " << pos
             << ", was " << backupPosition);
    string buf(pos.encodedSize(),'\0');
    framing::Buffer buffer(&buf[0], buf.size());
    pos.encode(buffer);
    buffer.reset();
    {
        sys::Mutex::ScopedUnlock u(lock);
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
        sys::Mutex::ScopedLock l(lock);
        if (!dequeues.empty()) sendDequeueEvent(l);
    }
    return ConsumerImpl::doDispatch();
}

}} // namespace qpid::ha
