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
#include "qpid/broker/amqp/Outgoing.h"
#include "qpid/broker/amqp/Header.h"
#include "qpid/broker/amqp/Translation.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/Selector.h"
#include "qpid/broker/TopicKeyNode.h"
#include "qpid/sys/OutputControl.h"
#include "qpid/amqp/MessageEncoder.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {
namespace amqp {

Outgoing::Outgoing(Broker& broker, boost::shared_ptr<Queue> q, pn_link_t* l, ManagedSession& session, qpid::sys::OutputControl& o, bool topic)
    : Consumer(pn_link_name(l), /*FIXME*/CONSUMER),
      ManagedOutgoingLink(broker, *q, session, pn_link_name(l), topic),
      exclusive(topic),
      queue(q), deliveries(5000), link(l), out(o),
      current(0), outstanding(0),
      buffer(1024)/*used only for header at present*/
{
    for (size_t i = 0 ; i < deliveries.capacity(); ++i) {
        deliveries[i].init(i);
    }
}

void Outgoing::init()
{
    queue->consume(shared_from_this(), exclusive);//may throw exception
}

bool Outgoing::dispatch()
{
    QPID_LOG(trace, "Dispatching to " << getName() << ": " << pn_link_credit(link));
    if (canDeliver()) {
        if (queue->dispatch(shared_from_this())) {
            return true;
        } else {
            pn_link_drained(link);
            QPID_LOG(debug, "No message available on " << queue->getName());
        }
    } else {
        QPID_LOG(debug, "Can't deliver to " << getName() << " from " << queue->getName() << ": " << pn_link_credit(link));
    }
    return false;
}

void Outgoing::write(const char* data, size_t size)
{
    pn_link_send(link, data, size);
}

void Outgoing::handle(pn_delivery_t* delivery)
{
    pn_delivery_tag_t tag = pn_delivery_tag(delivery);
    size_t i = *reinterpret_cast<const size_t*>(tag.bytes);
    Record& r = deliveries[i];
    if (pn_delivery_writable(delivery)) {
        assert(r.msg);
        assert(!r.delivery);
        r.delivery = delivery;
        //write header
        qpid::amqp::MessageEncoder encoder(&buffer[0], buffer.size());
        encoder.writeHeader(Header(r.msg));
        write(&buffer[0], encoder.getPosition());
        Translation t(r.msg);
        t.write(*this);
        if (pn_link_advance(link)) {
            --outstanding;
            outgoingMessageSent();
            QPID_LOG(debug, "Sent message " << r.msg.getSequence() << " from " << queue->getName() << ", index=" << r.index);
        } else {
            QPID_LOG(error, "Failed to send message " << r.msg.getSequence() << " from " << queue->getName() << ", index=" << r.index);
        }
    }
    if (pn_delivery_updated(delivery)) {
        assert(r.delivery == delivery);
        r.disposition = pn_delivery_remote_state(delivery);
        if (r.disposition) {
            switch (r.disposition) {
              case PN_ACCEPTED:
                //TODO: only if consuming
                queue->dequeue(0, r.cursor);
                outgoingMessageAccepted();
                break;
              case PN_REJECTED:
                queue->reject(r.cursor);
                outgoingMessageRejected();
                break;
              case PN_RELEASED:
                queue->release(r.cursor, false);//TODO: for PN_RELEASED, delivery count should not be incremented
                outgoingMessageRejected();//TODO: not quite true...
                break;
              case PN_MODIFIED:
                queue->release(r.cursor, true);//TODO: proper handling of modified
                outgoingMessageRejected();//TODO: not quite true...
                break;
              default:
                QPID_LOG(warning, "Unhandled disposition: " << r.disposition);
            }
            //TODO: ony settle once any dequeue on store has completed
            pn_delivery_settle(delivery);
            r.reset();
        }
    }
}

bool Outgoing::canDeliver()
{
    return deliveries[current].delivery == 0 && pn_link_credit(link) > outstanding;
}

void Outgoing::detached()
{
    QPID_LOG(debug, "Detaching outgoing link from " << queue->getName());
    queue->cancel(shared_from_this());
    //TODO: release in a clearer order?
    for (size_t i = 0 ; i < deliveries.capacity(); ++i) {
        if (deliveries[i].msg) queue->release(deliveries[i].cursor, true);
    }
    Queue::tryAutoDelete(*queue->getBroker(), queue, "", "");
}

//Consumer interface:
bool Outgoing::deliver(const QueueCursor& cursor, const qpid::broker::Message& msg)
{
    Record& r = deliveries[current++];
    if (current >= deliveries.capacity()) current = 0;
    r.cursor = cursor;
    r.msg = msg;
    pn_delivery(link, r.tag);
    QPID_LOG(debug, "Requested delivery of " << r.msg.getSequence() << " from " << queue->getName() << ", index=" << r.index);
    ++outstanding;
    return true;
}

void Outgoing::notify()
{
    QPID_LOG(trace, "Notification received for " << queue->getName());
    out.activateOutput();
}

bool Outgoing::accept(const qpid::broker::Message&)
{
    return true;
}

void Outgoing::setSubjectFilter(const std::string& f)
{
    subjectFilter = f;
}

void Outgoing::setSelectorFilter(const std::string& f)
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

bool Outgoing::filter(const qpid::broker::Message& m)
{
    return (subjectFilter.empty() || subjectFilter == m.getRoutingKey() || match(subjectFilter, m.getRoutingKey()))
           && (!selector || selector->filter(m));
}

void Outgoing::cancel() {}

void Outgoing::acknowledged(const qpid::broker::DeliveryRecord&) {}

qpid::broker::OwnershipToken* Outgoing::getSession()
{
    return 0;
}

Outgoing::Record::Record() : delivery(0), disposition(0), index(0) {}
void Outgoing::Record::init(size_t i)
{
    index = i;
    tag.bytes = reinterpret_cast<const char*>(&index);
    tag.size = sizeof(index);
}
void Outgoing::Record::reset()
{
    cursor = QueueCursor();
    msg = qpid::broker::Message();
    delivery = 0;
    disposition = 0;
}


}}} // namespace qpid::broker::amqp
