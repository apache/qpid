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
#include "qpid/broker/Queue.h"
#include "qpid/broker/SessionContext.h"
#include "qpid/broker/ConnectionState.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/log/Statement.h"
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
    stringstream ss;
    string url = parent->getSession().getConnection().getUrl();
    string qname = getQueue()->getName();
    ss << "HA: Primary queue " << qname << ", backup " <<  url << ": ";
    logPrefix = ss.str();
    
    // FIXME aconway 2011-12-09: Failover optimization removed.
    // There was code here to re-use messages already on the backup
    // during fail-over. This optimization was removed to simplify
    // the logic till we get the basic replication stable, it
    // can be re-introduced later. Last revision with the optimization:
    // r1213258 | QPID-3603: Fix QueueReplicator subscription parameters.

    QPID_LOG(debug, logPrefix << "Created subscription " << name);

    // FIXME aconway 2011-12-15: ConsumerImpl::position is left at 0
    // so we will start consuming from the lowest numbered message.
    // This is incorrect if the sequence number wraps around, but
    // this is what all consumers currently do.
}

// Message is delivered in the subscription's connection thread.
bool ReplicatingSubscription::deliver(QueuedMessage& m) {
    // Add position events for the subscribed queue, not for the internal event queue.
    if (m.queue && m.queue == getQueue().get()) {
        sys::Mutex::ScopedLock l(lock);
        assert(position == m.position);
        // m.position is the position of the newly enqueued m on the local queue.
        // backupPosition is latest position on the backup queue (before enqueueing m.)
        assert(m.position > backupPosition);
        if (m.position - backupPosition > 1) {
            // Position has advanced because of messages dequeued ahead of us.
            SequenceNumber send(m.position);
            --send;   // Send the position before m was enqueued.
            sendPositionEvent(send, l);
            QPID_LOG(trace, logPrefix << "Sending position " << send
                     << ", was " << backupPosition);
        }
        backupPosition = m.position;
        QPID_LOG(trace, logPrefix << "Replicating message " << m.position);
    }
    return ConsumerImpl::deliver(m);
}

ReplicatingSubscription::~ReplicatingSubscription() {}

// Called in the subscription's connection thread.
void ReplicatingSubscription::cancel()
{
    QPID_LOG(debug, logPrefix <<"Cancelled");
    getQueue()->removeObserver(boost::dynamic_pointer_cast<QueueObserver>(shared_from_this()));
}

// Called before we get notified of the message being available and
// under the message lock in the queue. Called in arbitrary connection thread.
void ReplicatingSubscription::enqueued(const QueuedMessage& m)
{
    //delay completion
    m.payload->getIngressCompletion().startCompleter();
}

// Called with lock held. Called in subscription's connection thread.
void ReplicatingSubscription::sendDequeueEvent(const sys::Mutex::ScopedLock& l)
{
    QPID_LOG(trace, logPrefix << "Sending dequeues " << dequeues);
    string buf(dequeues.encodedSize(),'\0');
    framing::Buffer buffer(&buf[0], buf.size());
    dequeues.encode(buffer);
    dequeues.clear();
    buffer.reset();
    sendEvent(QueueReplicator::DEQUEUE_EVENT_KEY, buffer, l);
}

// Called with lock held. Called in subscription's connection thread.
void ReplicatingSubscription::sendPositionEvent(
    SequenceNumber position, const sys::Mutex::ScopedLock&l )
{
    string buf(backupPosition.encodedSize(),'\0');
    framing::Buffer buffer(&buf[0], buf.size());
    position.encode(buffer);
    buffer.reset();
    sendEvent(QueueReplicator::POSITION_EVENT_KEY, buffer, l);
}

void ReplicatingSubscription::sendEvent(const std::string& key, framing::Buffer& buffer,
                                        const sys::Mutex::ScopedLock&)
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

    DeliveryProperties* props = event->getFrames().getHeaders()->get<DeliveryProperties>(true);
    props->setRoutingKey(key);
    // Send the event using the events queue. Consumer is a
    // DelegatingConsumer that delegates to *this for everything but
    // has an independnet position. We put an event on events and
    // dispatch it through ourselves to send it in line with the
    // normal browsing messages.
    events->deliver(event);
    events->dispatch(consumer);
}

// Called after the message has been removed from the deque and under
// the messageLock in the queue. Called in arbitrary connection threads.
void ReplicatingSubscription::dequeued(const QueuedMessage& m)
{
    QPID_LOG(trace, logPrefix << "Dequeued message " << m.position);
    {
        sys::Mutex::ScopedLock l(lock);
        dequeues.add(m.position);
        // If we have not yet sent this message to the backup, then
        // complete it now as it will never be accepted.

        // FIXME aconway 2012-01-05: suspect use of position in
        // foreign connection thread.  Race with deliver() which is
        // not under the message lock?
        if (m.position > position) {
            m.payload->getIngressCompletion().finishCompleter();
            QPID_LOG(trace, logPrefix << "Completed message " << m.position << " early");
        }
    }
    notify();                   // Ensure a call to doDispatch
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

ReplicatingSubscription::DelegatingConsumer::DelegatingConsumer(ReplicatingSubscription& c) : Consumer(c.getName(), true), delegate(c) {}
ReplicatingSubscription::DelegatingConsumer::~DelegatingConsumer() {}
bool ReplicatingSubscription::DelegatingConsumer::deliver(QueuedMessage& m) { return delegate.deliver(m); }
void ReplicatingSubscription::DelegatingConsumer::notify() { delegate.notify(); }
bool ReplicatingSubscription::DelegatingConsumer::filter(boost::intrusive_ptr<Message> msg) { return delegate.filter(msg); }
bool ReplicatingSubscription::DelegatingConsumer::accept(boost::intrusive_ptr<Message> msg) { return delegate.accept(msg); }
OwnershipToken* ReplicatingSubscription::DelegatingConsumer::getSession() { return delegate.getSession(); }

}} // namespace qpid::ha
