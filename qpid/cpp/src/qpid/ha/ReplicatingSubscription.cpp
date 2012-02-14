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
#include "Logging.h"
#include "qpid/broker/Queue.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace ha {

using namespace framing;
using namespace broker;
using namespace std;

const string ReplicatingSubscription::QPID_REPLICATING_SUBSCRIPTION("qpid.replicating-subscription");
const string ReplicatingSubscription::QPID_HIGH_SEQUENCE_NUMBER("qpid.high-sequence-number");
const string ReplicatingSubscription::QPID_LOW_SEQUENCE_NUMBER("qpid.low-sequence-number");

namespace {
const string DOLLAR("$");
const string INTERNAL("-internal");
} // namespace

class ReplicationStateInitialiser
{
  public:
    ReplicationStateInitialiser(
        qpid::framing::SequenceSet& r,
        const qpid::framing::SequenceNumber& s,
        const qpid::framing::SequenceNumber& e) : dequeues(r), start(s), end(e)
    {
        dequeues.add(start, end);
    }

    void operator()(const QueuedMessage& message) {
        if (message.position < start) {
            //replica does not have a message that should still be on the queue
            QPID_LOG(warning, "HA: Replica missing message " << QueuePos(message));
            // FIXME aconway 2011-12-09: we want the replica to dump
            // its messages and start from scratch in this case.
        } else if (message.position >= start && message.position <= end) {
            //i.e. message is within the intial range and has not been dequeued,
            //so remove it from the dequeues
            dequeues.remove(message.position);
        } //else message has not been seen by replica yet so can be ignored here
    }

  private:
    qpid::framing::SequenceSet& dequeues;
    const qpid::framing::SequenceNumber start;
    const qpid::framing::SequenceNumber end;
};

string mask(const string& in)
{
    return DOLLAR + in + INTERNAL;
}

boost::shared_ptr<broker::SemanticState::ConsumerImpl>
ReplicatingSubscription::Factory::create(
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
) {
    boost::shared_ptr<ReplicatingSubscription> rs;
    if (arguments.isSet(QPID_REPLICATING_SUBSCRIPTION)) {
        // FIXME aconway 2011-12-01: ignoring acquire param and setting acquire
        // false. Should this be done in the caller? Remove from ctor parameters.
        rs.reset(new ReplicatingSubscription(
                     parent, name, queue, ack, false, exclusive, tag,
                     resumeId, resumeTtl, arguments));
        // FIXME aconway 2011-12-08: need to removeObserver also.
        queue->addObserver(rs);
    }
    return rs;
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
    events(new Queue(mask(name))),
    consumer(new DelegatingConsumer(*this))
{
    // FIXME aconway 2011-12-09: Here we take advantage of existing
    // messages on the backup queue to reduce replication
    // effort. However if the backup queue is inconsistent with being
    // a backup of the primary queue, then we want to issue a warning
    // and tell the backup to dump its messages and start replicating
    // from scratch.
    QPID_LOG(debug, "HA: Replicating subscription " << name << " to " << queue->getName());
    if (arguments.isSet(QPID_HIGH_SEQUENCE_NUMBER)) {
        qpid::framing::SequenceNumber hwm = arguments.getAsInt(QPID_HIGH_SEQUENCE_NUMBER);
        qpid::framing::SequenceNumber lwm;
        if (arguments.isSet(QPID_LOW_SEQUENCE_NUMBER)) {
            lwm = arguments.getAsInt(QPID_LOW_SEQUENCE_NUMBER);
        } else {
            lwm = hwm;
        }
        qpid::framing::SequenceNumber oldest;
        if (queue->getOldest(oldest)) {
            if (oldest >= hwm) {
                dequeues.add(lwm, --oldest);
            } else if (oldest >= lwm) {
                ReplicationStateInitialiser initialiser(dequeues, lwm, hwm);
                queue->eachMessage(initialiser);
            } else { //i.e. older message on master than is reported to exist on replica
                // FIXME aconway 2011-12-09: dump and start from scratch?
                QPID_LOG(warning, "HA: Replica missing message on primary");
            }
        } else {
            //local queue (i.e. master) is empty
            dequeues.add(lwm, queue->getPosition());
            // FIXME aconway 2011-12-09: if hwm >
            // queue->getPosition(), dump and start from scratch?
        }
        QPID_LOG(debug, "HA: Initial set of dequeues for " << queue->getName() << ": "
                 << dequeues << " (lwm=" << lwm << ", hwm=" << hwm
                 << ", current=" << queue->getPosition() << ")");
        //set position of 'cursor'
        position = hwm;
    }
}

bool ReplicatingSubscription::deliver(QueuedMessage& m)
{
    return ConsumerImpl::deliver(m);
}

void ReplicatingSubscription::cancel()
{
    getQueue()->removeObserver(boost::dynamic_pointer_cast<QueueObserver>(shared_from_this()));
}

ReplicatingSubscription::~ReplicatingSubscription() {}

//called before we get notified of the message being available and
//under the message lock in the queue
void ReplicatingSubscription::enqueued(const QueuedMessage& m)
{
    QPID_LOG(trace, "HA: Enqueued message " << QueuePos(m) << " on " << getName());
    //delay completion
    m.payload->getIngressCompletion().startCompleter();
}

// Called with lock held.
void ReplicatingSubscription::generateDequeueEvent()
{
    QPID_LOG(trace, "HA: Sending dequeue event " << getQueue()->getName() << " " << dequeues << " on " << getName());
    string buf(dequeues.encodedSize(),'\0');
    framing::Buffer buffer(&buf[0], buf.size());
    dequeues.encode(buffer);
    dequeues.clear();
    buffer.reset();
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

    DeliveryProperties* props = event->getFrames().getHeaders()->get<DeliveryProperties>(true);
    props->setRoutingKey(QueueReplicator::DEQUEUE_EVENT_KEY);
    events->deliver(event);
}

// Called after the message has been removed from the deque and under
// the message lock in the queue.
void ReplicatingSubscription::dequeued(const QueuedMessage& m)
{
    {
        sys::Mutex::ScopedLock l(lock);
        dequeues.add(m.position);
        QPID_LOG(trace, "HA: Added " << QueuePos(m)
                 << " to dequeue event; subscription at " << position);
    }
    notify();                   // Ensure a call to doDispatch
    if (m.position > position) {
        m.payload->getIngressCompletion().finishCompleter();
        QPID_LOG(trace, "HA: Completed " << QueuePos(m) << " early, dequeued.");
    }
}

bool ReplicatingSubscription::doDispatch()
{
    {
        sys::Mutex::ScopedLock l(lock);
        if (!dequeues.empty()) {
            generateDequeueEvent();
        }
    }
    bool r1 = events->dispatch(consumer);
    bool r2 = ConsumerImpl::doDispatch();
    return r1 || r2;
}

ReplicatingSubscription::DelegatingConsumer::DelegatingConsumer(ReplicatingSubscription& c) : Consumer(c.getName(), true), delegate(c) {}
ReplicatingSubscription::DelegatingConsumer::~DelegatingConsumer() {}
bool ReplicatingSubscription::DelegatingConsumer::deliver(QueuedMessage& m)
{
    return delegate.deliver(m);
}
void ReplicatingSubscription::DelegatingConsumer::notify() { delegate.notify(); }
bool ReplicatingSubscription::DelegatingConsumer::filter(boost::intrusive_ptr<Message> msg) { return delegate.filter(msg); }
bool ReplicatingSubscription::DelegatingConsumer::accept(boost::intrusive_ptr<Message> msg) { return delegate.accept(msg); }
void ReplicatingSubscription::DelegatingConsumer::cancel() {}
OwnershipToken* ReplicatingSubscription::DelegatingConsumer::getSession() { return delegate.getSession(); }

}} // namespace qpid::broker
