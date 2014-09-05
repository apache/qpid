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
#include "SenderImpl.h"
#include "MessageSink.h"
#include "SessionImpl.h"
#include "AddressResolution.h"
#include "OutgoingMessage.h"
#include "qpid/messaging/Session.h"

namespace qpid {
namespace client {
namespace amqp0_10 {

SenderImpl::SenderImpl(SessionImpl& _parent, const std::string& _name, 
                       const qpid::messaging::Address& _address, bool _autoReconnect) : 
    parent(&_parent), autoReconnect(_autoReconnect), name(_name), address(_address), state(UNRESOLVED),
    capacity(50), window(0), flushed(false), unreliable(AddressResolution::is_unreliable(address)) {}

qpid::messaging::Address SenderImpl::getAddress() const
{
    return address;
}

void SenderImpl::send(const qpid::messaging::Message& message, bool sync) 
{
    if (unreliable) {           // immutable, don't need lock
        UnreliableSend f(*this, message);
        parent->execute(f);
    } else {
        Send f(*this, message);
        while (f.repeat) parent->execute(f);
    }
    if (sync) parent->sync(true);
}

void SenderImpl::close()
{
    execute<Close>();
}

void SenderImpl::setCapacity(uint32_t c)
{
    bool flush;
    {
        sys::Mutex::ScopedLock l(lock);
        flush = c < capacity;
        capacity = c;
    }
    execute1<CheckPendingSends>(flush);
}

uint32_t SenderImpl::getCapacity() {
    sys::Mutex::ScopedLock l(lock);
    return capacity;
}

uint32_t SenderImpl::getUnsettled()
{
    CheckPendingSends f(*this, false);
    parent->execute(f);
    return f.pending;
} 

void SenderImpl::init(qpid::client::AsyncSession s, AddressResolution& resolver)
{
    sys::Mutex::ScopedLock l(lock);
    session = s;
    if (state == UNRESOLVED) {
        sink = resolver.resolveSink(session, address);
        state = ACTIVE;
    }
    if (state == CANCELLED) {
        sink->cancel(session, name);
        sys::Mutex::ScopedUnlock u(lock);
        parent->senderCancelled(name);
    } else {
        sink->declare(session, name);
        replay(l);
    }
}

void SenderImpl::waitForCapacity() 
{
    sys::Mutex::ScopedLock l(lock);
    try {
        //TODO: add option to throw exception rather than blocking?
        if (!unreliable && capacity <=
            (flushed ? checkPendingSends(false, l) : outgoing.size()))
        {
            //Initial implementation is very basic. As outgoing is
            //currently only reduced on receiving completions and we are
            //blocking anyway we may as well sync(). If successful that
            //should clear all outstanding sends.
            session.sync();
            checkPendingSends(false, l);
        }
        //flush periodically and check for conmpleted sends
        if (++window > (capacity / 4)) {//TODO: make this configurable?
            checkPendingSends(true, l);
            window = 0;
        }
    } catch (const qpid::TransportFailure&) {
        //Disconnection prevents flushing or syncing. If we have any
        //capacity we will return anyway (the subsequent attempt to
        //send will fail, but message will be on replay buffer).
        if (capacity > outgoing.size()) return;
        //If we are out of capacity, but autoreconnect is on, then
        //rethrow the transport failure to trigger reconnect which
        //will have the effect of blocking until connected and
        //capacity is freed up
        if (autoReconnect) throw;
        //Otherwise, in order to clearly signal to the application
        //that the message was not pushed to replay buffer, throw an
        //out of capacity error
        throw qpid::messaging::OutOfCapacity(name);
    }
}

void SenderImpl::sendImpl(const qpid::messaging::Message& m)
{
    sys::Mutex::ScopedLock l(lock);
    std::auto_ptr<OutgoingMessage> msg(new OutgoingMessage());
    msg->setSubject(m.getSubject().empty() ? address.getSubject() : m.getSubject());
    msg->convert(m);
    outgoing.push_back(msg.release());
    sink->send(session, name, outgoing.back());
}

void SenderImpl::sendUnreliable(const qpid::messaging::Message& m)
{
    sys::Mutex::ScopedLock l(lock);
    OutgoingMessage msg;
    msg.setSubject(m.getSubject().empty() ? address.getSubject() : m.getSubject());
    msg.convert(m);
    sink->send(session, name, msg);
}

void SenderImpl::replay(const sys::Mutex::ScopedLock& l)
{
    checkPendingSends(false, l);
    for (OutgoingMessages::iterator i = outgoing.begin(); i != outgoing.end(); ++i) {
        i->markRedelivered();
        sink->send(session, name, *i);
    }
}

uint32_t SenderImpl::checkPendingSends(bool flush) {
    sys::Mutex::ScopedLock l(lock);
    return checkPendingSends(flush, l);
}

uint32_t SenderImpl::checkPendingSends(bool flush, const sys::Mutex::ScopedLock&)
{
    if (flush) {
        session.flush();
        flushed = true;
    } else {
        flushed = false;
    }
    while (!outgoing.empty() && outgoing.front().isComplete()) {
        outgoing.pop_front();
    }
    return outgoing.size();
}

void SenderImpl::closeImpl()
{
    {
        sys::Mutex::ScopedLock l(lock);
        state = CANCELLED;
        sink->cancel(session, name);
    }
    parent->senderCancelled(name);
}

const std::string& SenderImpl::getName() const
{
    sys::Mutex::ScopedLock l(lock);
    return name;
}

qpid::messaging::Session SenderImpl::getSession() const
{
    sys::Mutex::ScopedLock l(lock);
    return qpid::messaging::Session(parent.get());
}

}}} // namespace qpid::client::amqp0_10
