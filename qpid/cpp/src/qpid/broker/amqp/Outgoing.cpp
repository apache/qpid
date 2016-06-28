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
#include "qpid/broker/amqp/DataReader.h"
#include "qpid/broker/amqp/Outgoing.h"
#include "qpid/broker/amqp/Exception.h"
#include "qpid/broker/amqp/Header.h"
#include "qpid/broker/amqp/Session.h"
#include "qpid/broker/amqp/Translation.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/Selector.h"
#include "qpid/broker/TopicKeyNode.h"
#include "qpid/sys/OutputControl.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/amqp/Descriptor.h"
#include "qpid/amqp/MessageEncoder.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"
#include "config.h"

namespace qpid {
namespace broker {
namespace amqp {

Outgoing::Outgoing(Broker& broker, Session& parent, const std::string& source, const std::string& target, const std::string& name)
    : ManagedOutgoingLink(broker, parent, source, target, name), session(parent) {}

void Outgoing::wakeup()
{
    session.wakeup();
}

namespace {
bool requested_reliable(pn_link_t* link)
{
    return pn_link_remote_snd_settle_mode(link) == PN_SND_UNSETTLED;
}
bool requested_unreliable(pn_link_t* link)
{
    return pn_link_remote_snd_settle_mode(link) == PN_SND_SETTLED;
}
}

OutgoingFromQueue::OutgoingFromQueue(Broker& broker, const std::string& source, const std::string& target, boost::shared_ptr<Queue> q, pn_link_t* l, Session& session,
                                     qpid::sys::OutputControl& o, SubscriptionType type, bool e, bool p)
    : Outgoing(broker, session, source, target, pn_link_name(l)),
      Consumer(pn_link_name(l), type, target),
      exclusive(e),
      isControllingUser(p),
      queue(q), deliveries(5000), link(l), out(o),
      current(0),
      buffer(1024)/*used only for header at present*/,
      //for exclusive queues, assume unreliable unless reliable is explicitly requested; otherwise assume reliable unless unreliable requested
      unreliable(exclusive ? !requested_reliable(link) : requested_unreliable(link)),
      cancelled(false),
      trackingUndeliverableMessages(false)
{
    for (size_t i = 0 ; i < deliveries.capacity(); ++i) {
        deliveries[i].init(i);
    }
    if (isControllingUser) queue->markInUse(true);
}

void OutgoingFromQueue::init()
{
    queue->consume(shared_from_this(), exclusive);//may throw exception
}

bool OutgoingFromQueue::doWork()
{
    QPID_LOG(trace, "Dispatching to " << getName() << ": " << pn_link_credit(link));
    if (canDeliver()) {
        try{
            if (queue->dispatch(shared_from_this())) {
                return true;
            } else {
                pn_link_drained(link);
                QPID_LOG(trace, "No message available on " << queue->getName());
            }
        } catch (const qpid::framing::ResourceDeletedException& e) {
            throw Exception(qpid::amqp::error_conditions::RESOURCE_DELETED, e.what());
        }
    } else {
        QPID_LOG(trace, "Can't deliver to " << getName() << " from " << queue->getName() << ": " << pn_link_credit(link));
    }
    return false;
}

void OutgoingFromQueue::write(const char* data, size_t size)
{
    pn_link_send(link, data, size);
}

void OutgoingFromQueue::mergeMessageAnnotationsIfRequired(const Record &r)
{
    pn_data_t *remoteAnnotationsRaw =
      pn_disposition_annotations(pn_delivery_remote(r.delivery));
    if (remoteAnnotationsRaw == 0) {
      return;
    }

    qpid::types::Variant::Map remoteMessageAnnotations;
    DataReader::read(remoteAnnotationsRaw, remoteMessageAnnotations);
    queue->mergeMessageAnnotations(r.cursor, remoteMessageAnnotations);
}

void OutgoingFromQueue::handle(pn_delivery_t* delivery)
{
    size_t i = Record::getIndex(pn_delivery_tag(delivery));
    Record& r = deliveries[i];
    if (r.delivery && pn_delivery_updated(delivery)) {
        assert(r.delivery == delivery);
        r.disposition = pn_delivery_remote_state(delivery);

        std::pair<TxBuffer*,uint64_t> txn = session.getTransactionalState(delivery);
        if (txn.first) {
            r.disposition = txn.second;
        }

        if (!r.disposition && pn_delivery_settled(delivery)) {
            //if peer has settled without setting state, assume accepted
            r.disposition = PN_ACCEPTED;
        }
        if (r.disposition) {
            switch (r.disposition) {
              case PN_ACCEPTED:
                if (preAcquires()) queue->dequeue(r.cursor, txn.first);
                outgoingMessageAccepted();
                break;
              case PN_REJECTED:
                if (preAcquires()) queue->reject(r.cursor);
                outgoingMessageRejected();
                break;
              case PN_RELEASED:
                if (preAcquires()) queue->release(r.cursor, false);//for PN_RELEASED, delivery count should not be incremented
                outgoingMessageRejected();//TODO: not quite true...
                break;
              case PN_MODIFIED:
                if (preAcquires()) {
                    mergeMessageAnnotationsIfRequired(r);
                    if (pn_disposition_is_undeliverable(pn_delivery_remote(delivery))) {
                        if (!trackingUndeliverableMessages) {
                            // observe queue for changes to track undeliverable messages
                            queue->getObservers().add(
                              boost::dynamic_pointer_cast<OutgoingFromQueue>(shared_from_this()));
                            trackingUndeliverableMessages = true;
                        }

                        undeliverableMessages.add(r.msg.getSequence());
                    }

                    queue->release(r.cursor, pn_disposition_is_failed(pn_delivery_remote(delivery)));
                }
                outgoingMessageRejected();//TODO: not quite true...
                break;
              default:
                QPID_LOG(warning, "Unhandled disposition: " << r.disposition);
            }
            //TODO: only settle once any dequeue on store has completed
            pn_delivery_settle(delivery);
            r.reset();
        }
    }
}

bool OutgoingFromQueue::canDeliver()
{
    return deliveries[current].delivery == 0 && pn_link_credit(link);
}

void OutgoingFromQueue::detached(bool closed)
{
    QPID_LOG(debug, "Detaching outgoing link " << getName() << " from " << queue->getName());

    if (trackingUndeliverableMessages) {
      // stop observation of the queue
      queue->getObservers().remove(
        boost::dynamic_pointer_cast<OutgoingFromQueue>(shared_from_this()));
    }

    queue->cancel(shared_from_this());
    //TODO: release in a clearer order?
    for (size_t i = 0 ; i < deliveries.capacity(); ++i) {
        if (deliveries[i].msg) queue->release(deliveries[i].cursor, true);
    }
    if (exclusive) {
        queue->releaseExclusiveOwnership(closed);
    } else if (isControllingUser) {
        queue->releaseFromUse(true);
    }
    cancelled = true;
}

OutgoingFromQueue::~OutgoingFromQueue()
{
    if (!cancelled && isControllingUser) queue->releaseFromUse(true);
}

//Consumer interface:
bool OutgoingFromQueue::deliver(const QueueCursor& cursor, const qpid::broker::Message& msg)
{
    Record& r = deliveries[current++];
    if (current >= deliveries.capacity()) current = 0;
    r.cursor = cursor;
    r.msg = msg;
    r.delivery = pn_delivery(link, r.tag);
    //write header
    qpid::amqp::MessageEncoder encoder(&buffer[0], buffer.size());
    encoder.writeHeader(Header(r.msg));
    write(&buffer[0], encoder.getPosition());
    Translation t(r.msg);
    t.write(*this);
    if (pn_link_advance(link)) {
        if (unreliable) pn_delivery_settle(r.delivery);
        outgoingMessageSent();
        QPID_LOG(debug, "Sent message " << r.msg.getSequence() << " from " << queue->getName() << ", index=" << r.index);
    } else {
        QPID_LOG(error, "Failed to send message " << r.msg.getSequence() << " from " << queue->getName() << ", index=" << r.index);
    }
    if (unreliable) {
        if (preAcquires()) queue->dequeue(0, r.cursor);
        r.reset();
    }
    QPID_LOG(debug, "Requested delivery of " << r.msg.getSequence() << " from " << queue->getName() << ", index=" << r.index);
    return true;
}

void OutgoingFromQueue::notify()
{
    QPID_LOG(trace, "Notification received for " << queue->getName());
    out.activateOutput();
}

bool OutgoingFromQueue::accept(const qpid::broker::Message&)
{
    return true;
}

void OutgoingFromQueue::setSubjectFilter(const std::string& f)
{
    subjectFilter = f;
}

void OutgoingFromQueue::setSelectorFilter(const std::string& f)
{
    selector.reset(new Selector(f));
}

namespace {

bool match(TokenIterator& filter, TokenIterator& target)
{
    bool wild = false;
    while (!filter.finished())
    {
        if (filter.match1('*')) {
            if (target.finished()) return false;
            //else move to next word in filter target
            filter.next();
            target.next();
        } else if (filter.match1('#')) {
            // i.e. filter word is '#' which can match a variable number of words in the target
            filter.next();
            if (filter.finished()) return true;
            else if (target.finished()) return false;
            wild = true;
        } else {
            //filter word needs to match target exactly
            if (target.finished()) return false;
            std::string word;
            target.pop(word);
            if (filter.match(word)) {
                wild = false;
                filter.next();
            } else if (!wild) {
                return false;
            }
        }
    }
    return target.finished();
}
bool match(const std::string& filter, const std::string& target)
{
    TokenIterator lhs(filter);
    TokenIterator rhs(target);
    return match(lhs, rhs);
}
}

bool OutgoingFromQueue::filter(const qpid::broker::Message& m)
{
    if (undeliverableMessages.contains(m.getSequence())) return false;
    return (subjectFilter.empty() || subjectFilter == m.getRoutingKey() || match(subjectFilter, m.getRoutingKey()))
           && (!selector || selector->filter(m));
}

void OutgoingFromQueue::cancel() {}

void OutgoingFromQueue::acknowledged(const qpid::broker::DeliveryRecord&) {}

qpid::broker::OwnershipToken* OutgoingFromQueue::getSession()
{
    return 0;
}

OutgoingFromQueue::Record::Record() : delivery(0), disposition(0), index(0)
{
#ifdef NO_PROTON_DELIVERY_TAG_T
    tag.start = tagData;
#else
    tag.bytes = tagData;
#endif
    tag.size = TAG_WIDTH;
}
void OutgoingFromQueue::Record::init(size_t i)
{
    index = i;
    qpid::framing::Buffer buffer(tagData, tag.size);
    assert(index <= std::numeric_limits<uint32_t>::max());
    buffer.putLong(index);
}
void OutgoingFromQueue::Record::reset()
{
    cursor = QueueCursor();
    msg = qpid::broker::Message();
    delivery = 0;
    disposition = 0;
}

size_t OutgoingFromQueue::Record::getIndex(pn_delivery_tag_t t)
{
    assert(t.size == TAG_WIDTH);
#ifdef NO_PROTON_DELIVERY_TAG_T
    qpid::framing::Buffer buffer(const_cast<char*>(t.start)/*won't ever be written to*/, t.size);
#else
    qpid::framing::Buffer buffer(const_cast<char*>(t.bytes)/*won't ever be written to*/, t.size);
#endif
    return (size_t) buffer.getLong();
}

boost::shared_ptr<Queue> OutgoingFromQueue::getExclusiveSubscriptionQueue(Outgoing* o)
{
    OutgoingFromQueue* s = dynamic_cast<OutgoingFromQueue*>(o);
    if (s && s->exclusive) return s->queue;
    else return boost::shared_ptr<Queue>();
}

void OutgoingFromQueue::dequeued(const qpid::broker::Message &m)
{
    if (undeliverableMessages.contains(m.getSequence())) {
        undeliverableMessages.remove(m.getSequence());
    }
}

}}} // namespace qpid::broker::amqp
