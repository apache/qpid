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
#include "Relay.h"
#include "qpid/Exception.h"
#include "qpid/log/Statement.h"
#include <algorithm>
#include <string.h>
#include "config.h"

namespace qpid {
namespace broker {
namespace amqp {

Relay::Relay(size_t max_) : credit(0), max(max_), head(0), tail(0), isDetached(false), out(0), in(0) {}
void Relay::check()
{
    if (isDetached) throw qpid::Exception("other end of relay has been detached");
}
bool Relay::send(pn_link_t* link)
{
    BufferedTransfer* c(0);
    {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(lock);
        if (head < tail) {
            c = &buffer[head++];
        } else {
            return false;
        }
    }
    c->initOut(link);
    return true;
}

BufferedTransfer& Relay::push()
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(lock);
    buffer.push_back(BufferedTransfer());
    return buffer.back();
}

void Relay::received(pn_link_t* link, pn_delivery_t* delivery)
{
    BufferedTransfer& received = push();
    received.initIn(link, delivery);
    {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(lock);
        ++tail;
    }
    if (out) out->wakeup();
}
size_t Relay::size() const
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(lock);
    return buffer.size();
}
BufferedTransfer& Relay::front()
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(lock);
    return buffer.front();
}
void Relay::pop()
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(lock);
    buffer.pop_front();
    if (head) --head;
    if (tail) --tail;
}
void Relay::setCredit(int c)
{
    credit = c;
    if (in) in->wakeup();
}

int Relay::getCredit() const
{
    qpid::sys::ScopedLock<qpid::sys::Mutex> l(lock);
    return std::min(credit - size(), max);
}
void Relay::attached(Outgoing* o)
{
    out = o;
}
void Relay::attached(Incoming* i)
{
    in = i;
}
void Relay::detached(Outgoing*)
{
    out = 0;
    isDetached = true;
    QPID_LOG(info, "Outgoing link detached from relay [" << this << "]");
    if (in) in->wakeup();
}
void Relay::detached(Incoming*)
{
    in = 0;
    isDetached = true;
    QPID_LOG(info, "Incoming link detached from relay [" << this << "]");
    if (out) out->wakeup();
}

OutgoingFromRelay::OutgoingFromRelay(pn_link_t* l, Broker& broker, Session& parent, const std::string& source,
                                     const std::string& target, const std::string& name_, boost::shared_ptr<Relay> r)
    : Outgoing(broker, parent, source, target, name_), name(name_), link(l), relay(r) {}
/**
 * Allows the link to initiate any outgoing transfers
 */
bool OutgoingFromRelay::doWork()
{
    relay->check();
    relay->setCredit(pn_link_credit(link));
    bool worked = relay->send(link);
    pn_delivery_t *d = pn_link_current(link);
    if (d && pn_delivery_writable(d)) {
        handle(d);
        return true;
    }
    return worked;
}
/**
 * Called when a delivery is writable
 */
void OutgoingFromRelay::handle(pn_delivery_t* delivery)
{
    void* context = pn_delivery_get_context(delivery);
    BufferedTransfer* transfer = reinterpret_cast<BufferedTransfer*>(context);
    assert(transfer);
    if (pn_delivery_writable(delivery)) {
        if (transfer->write(link)) {
            outgoingMessageSent();
            QPID_LOG(debug, "Sent relayed message " << name << " [" << relay.get() << "]");
        } else {
            QPID_LOG(error, "Failed to send relayed message " << name << " [" << relay.get() << "]");
        }
    }
    if (pn_delivery_updated(delivery)) {
        uint64_t d = transfer->updated();
        switch (d) {
          case PN_ACCEPTED:
            outgoingMessageAccepted();
            break;
          case PN_REJECTED:
          case PN_RELEASED://TODO: not quite true...
          case PN_MODIFIED://TODO: not quite true...
            outgoingMessageRejected();
            break;
          default:
            QPID_LOG(warning, "Unhandled disposition: " << d);
        }
    }
}
/**
 * Signals that this link has been detached
 */
void OutgoingFromRelay::detached(bool /*closed*/)
{
    relay->detached(this);
}
void OutgoingFromRelay::init()
{
    relay->attached(this);
}
void OutgoingFromRelay::setSubjectFilter(const std::string&)
{
    //TODO
}
void OutgoingFromRelay::setSelectorFilter(const std::string&)
{
    //TODO
}

IncomingToRelay::IncomingToRelay(pn_link_t* link, Broker& broker, Session& parent, const std::string& source,
                                 const std::string& target, const std::string& name, boost::shared_ptr<Relay> r)
    : Incoming(link, broker, parent, source, target, name), relay(r)
{
    relay->attached(this);
}
bool IncomingToRelay::settle()
{
    bool result(false);
    while (relay->size() && relay->front().settle()) {
        result = true;
        relay->pop();
    }
    return result;
}
bool IncomingToRelay::doWork()
{
    relay->check();
    bool work(false);
    if (settle()) work = true;
    if (Incoming::doWork()) work = true;
    return work;
}
bool IncomingToRelay::haveWork()
{
    bool work(false);
    if (settle()) work = true;
    if (Incoming::haveWork()) work = true;
    return work;
}
void IncomingToRelay::readable(pn_delivery_t* delivery)
{
    relay->received(link, delivery);
    --window;
}

uint32_t IncomingToRelay::getCredit()
{
    return relay->getCredit();
}

void IncomingToRelay::detached(bool /*closed*/)
{
    relay->detached(this);
}

BufferedTransfer::BufferedTransfer() : disposition(0) {}
void BufferedTransfer::initIn(pn_link_t* link, pn_delivery_t* d)
{
    in.handle = d;
    //read in data
    data.resize(pn_delivery_pending(d));
    /*ssize_t read = */pn_link_recv(link, &data[0], data.size());
    pn_link_advance(link);

    //copy delivery tag
    pn_delivery_tag_t dt = pn_delivery_tag(d);
    tag.resize(dt.size);
#ifdef NO_PROTON_DELIVERY_TAG_T
    ::memmove(&tag[0], dt.start, dt.size);
#else
    ::memmove(&tag[0], dt.bytes, dt.size);
#endif

    //set context
    pn_delivery_set_context(d, this);

}

bool BufferedTransfer::settle()
{
    if (out.settled && !in.settled) {
        pn_delivery_update(in.handle, disposition);
        pn_delivery_settle(in.handle);
        in.settled = true;
    }
    return out.settled && in.settled;
}

void BufferedTransfer::initOut(pn_link_t* link)
{
    pn_delivery_tag_t dt;
#ifdef NO_PROTON_DELIVERY_TAG_T
    dt.start = &tag[0];
#else
    dt.bytes = &tag[0];
#endif
    dt.size = tag.size();
    out.handle = pn_delivery(link, dt);
    //set context
    pn_delivery_set_context(out.handle, this);
}

uint64_t BufferedTransfer::updated()
{
    disposition = pn_delivery_remote_state(out.handle);
    if (disposition) {
        pn_delivery_settle(out.handle);
        out.settled = true;
    }
    return disposition;
}

bool BufferedTransfer::write(pn_link_t* link)
{
    pn_link_send(link, &data[0], data.size());
    return pn_link_advance(link);
}
Delivery::Delivery() : settled(false), handle(0) {}
Delivery::Delivery(pn_delivery_t* d) : settled(false), handle(d) {}

}}} // namespace qpid::broker::amqp
