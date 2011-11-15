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
#include "Queue.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {

using namespace framing;

const std::string DOLLAR("$");
const std::string INTERNAL("_internall");

class ReplicationStateInitialiser
{
  public:
    ReplicationStateInitialiser(
        qpid::framing::SequenceSet& r,
        const qpid::framing::SequenceNumber& s,
        const qpid::framing::SequenceNumber& e) : results(r), start(s), end(e)
    {
        results.add(start, end);
    }

    void operator()(const QueuedMessage& message) {
        if (message.position < start) {
            //replica does not have a message that should still be on the queue
            QPID_LOG(warning, "Replica appears to be missing message at " << message.position);
        } else if (message.position >= start && message.position <= end) {
            //i.e. message is within the intial range and has not been dequeued, so remove it from the results
            results.remove(message.position);
        } //else message has not been seen by replica yet so can be ignored here
    }

  private:
    qpid::framing::SequenceSet& results;
    const qpid::framing::SequenceNumber start;
    const qpid::framing::SequenceNumber end;
};

std::string mask(const std::string& in)
{
    return DOLLAR + in + INTERNAL;
}

ReplicatingSubscription::ReplicatingSubscription(
    SemanticState* _parent,
    const std::string& _name,
    Queue::shared_ptr _queue,
    bool ack,
    bool _acquire,
    bool _exclusive,
    const std::string& _tag,
    const std::string& _resumeId,
    uint64_t _resumeTtl,
    const framing::FieldTable& _arguments
) : ConsumerImpl(_parent, _name, _queue, ack, _acquire, _exclusive, _tag, _resumeId, _resumeTtl, _arguments),
    events(new Queue(mask(_name))),
    consumer(new DelegatingConsumer(*this))
{

    if (_arguments.isSet("qpid.high_sequence_number")) {
        qpid::framing::SequenceNumber hwm = _arguments.getAsInt("qpid.high_sequence_number");
        qpid::framing::SequenceNumber lwm;
        if (_arguments.isSet("qpid.low_sequence_number")) {
            lwm = _arguments.getAsInt("qpid.low_sequence_number");
        } else {
            lwm = hwm;
        }
        qpid::framing::SequenceNumber oldest;
        if (_queue->getOldest(oldest)) {
            if (oldest >= hwm) {
                range.add(lwm, --oldest);
            } else if (oldest >= lwm) {
                ReplicationStateInitialiser initialiser(range, lwm, hwm);
                _queue->eachMessage(initialiser);
            } else { //i.e. have older message on master than is reported to exist on replica
                QPID_LOG(warning, "Replica appears to be missing message on master");
            }
        } else {
            //local queue (i.e. master) is empty
            range.add(lwm, _queue->getPosition());
        }
        QPID_LOG(debug, "Initial set of dequeues for " << _queue->getName() << " are " << range
                 << " (lwm=" << lwm << ", hwm=" << hwm << ", current=" << _queue->getPosition() << ")");
        //set position of 'cursor'
        position = hwm;
    }
}

bool ReplicatingSubscription::deliver(QueuedMessage& m)
{
    return ConsumerImpl::deliver(m);
}

void ReplicatingSubscription::init()
{
    getQueue()->addObserver(boost::dynamic_pointer_cast<QueueObserver>(shared_from_this()));
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
    QPID_LOG(debug, "Enqueued message at " << m.position);
    //delay completion
    m.payload->getIngressCompletion().startCompleter();
    QPID_LOG(debug, "Delayed " << m.payload.get());
}

void ReplicatingSubscription::generateDequeueEvent()
{
    std::string buf(range.encodedSize(),'\0');
    framing::Buffer buffer(&buf[0], buf.size());
    range.encode(buffer);
    range.clear();
    buffer.reset();

    //generate event message
    boost::intrusive_ptr<Message> event = new Message();
    AMQFrame method((MessageTransferBody(ProtocolVersion(), std::string(), 0, 0)));
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
    props->setRoutingKey("dequeue-event");

    events->deliver(event);
}

//called after the message has been removed from the deque and under
//the message lock in the queue
void ReplicatingSubscription::dequeued(const QueuedMessage& m)
{
    {
        sys::Mutex::ScopedLock l(lock);
        range.add(m.position);
        QPID_LOG(debug, "Updated dequeue event to include message at " << m.position << "; subscription is at " << position);
    }
    notify();
    if (m.position > position) {
        m.payload->getIngressCompletion().finishCompleter();
        QPID_LOG(debug, "Completed " << m.payload.get() << " early due to dequeue");
    }
}

bool ReplicatingSubscription::doDispatch()
{
    {
        sys::Mutex::ScopedLock l(lock);
        if (!range.empty()) {
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
OwnershipToken* ReplicatingSubscription::DelegatingConsumer::getSession() { return delegate.getSession(); }


}} // namespace qpid::broker
