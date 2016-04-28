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
#include "ReceiverImpl.h"
#include "AddressResolution.h"
#include "MessageSource.h"
#include "SessionImpl.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Session.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/types/encodings.h"

namespace qpid {
namespace client {
namespace amqp0_10 {

using qpid::messaging::NoMessageAvailable;
using qpid::messaging::Receiver;
using qpid::messaging::Duration;

void ReceiverImpl::received()
{
    //TODO: should this be configurable
    sys::Mutex::ScopedLock l(lock);
    if (capacity && --window <= capacity/2) {
        session.sendCompletion();
        window = capacity;
    }
}

qpid::messaging::Message ReceiverImpl::get(qpid::messaging::Duration timeout)
{
    qpid::messaging::Message result;
    if (!get(result, timeout)) throw NoMessageAvailable();
    return result;
}

qpid::messaging::Message ReceiverImpl::fetch(qpid::messaging::Duration timeout)
{
    qpid::messaging::Message result;
    if (!fetch(result, timeout)) throw NoMessageAvailable();
    return result;
}

bool ReceiverImpl::get(qpid::messaging::Message& message, qpid::messaging::Duration timeout)
{
    Get f(*this, message, timeout);
    while (!parent->execute(f)) {}
    return f.result;
}

bool ReceiverImpl::fetch(qpid::messaging::Message& message, qpid::messaging::Duration timeout)
{
    Fetch f(*this, message, timeout);
    while (!parent->execute(f)) {}
    return f.result;
}

void ReceiverImpl::close()
{
    execute<Close>();
}

void ReceiverImpl::start()
{
    sys::Mutex::ScopedLock l(lock);
    if (state == STOPPED) {
        state = STARTED;
        startFlow(l);
        session.sendCompletion();
    }
}

void ReceiverImpl::stop()
{
    sys::Mutex::ScopedLock l(lock);
    state = STOPPED;
    session.messageStop(destination);
}

void ReceiverImpl::setCapacity(uint32_t c)
{
    execute1<SetCapacity>(c);
}

void ReceiverImpl::startFlow(const sys::Mutex::ScopedLock&)
{
    if (capacity > 0) {
        session.messageSetFlowMode(destination, FLOW_MODE_WINDOW);
        session.messageFlow(destination, CREDIT_UNIT_MESSAGE, capacity);
        session.messageFlow(destination, CREDIT_UNIT_BYTE, byteCredit);
        window = capacity;
    }
}

void ReceiverImpl::init(qpid::client::AsyncSession s, AddressResolution& resolver)
{
    sys::Mutex::ScopedLock l(lock);
    session = s;
    if (state == CANCELLED) return;
    if (state == UNRESOLVED) {
        source = resolver.resolveSource(session, address);
        assert(source.get());
        state = STARTED;
    }
    source->subscribe(session, destination);
    startFlow(l);
}

const std::string& ReceiverImpl::getName() const {
    return destination;
}

uint32_t ReceiverImpl::getCapacity()
{
    sys::Mutex::ScopedLock l(lock);
    return capacity;
}

uint32_t ReceiverImpl::getAvailable()
{
    return parent->getReceivable(destination);
}

uint32_t ReceiverImpl::getUnsettled()
{
    return parent->getUnsettledAcks(destination);
}

qpid::messaging::Address ReceiverImpl::getAddress() const
{
    return address;
}

ReceiverImpl::ReceiverImpl(SessionImpl& p, const std::string& name,
                           const qpid::messaging::Address& a, bool autoDecode_) :

    parent(&p), destination(name), address(a), byteCredit(0xFFFFFFFF), autoDecode(autoDecode_),
    state(UNRESOLVED), capacity(0), window(0) {}

namespace {
const std::string TEXT_PLAIN("text/plain");
}

bool ReceiverImpl::getImpl(qpid::messaging::Message& message, qpid::messaging::Duration timeout)
{
    {
        sys::Mutex::ScopedLock l(lock);
        if (state == CANCELLED) return false;
    }
    if (parent->get(*this, message, timeout)) {
        if (autoDecode) {
            if (message.getContentType() == qpid::amqp_0_10::MapCodec::contentType) {
                message.getContentObject() = qpid::types::Variant::Map();
                decode(message, message.getContentObject().asMap());
            } else if (message.getContentType() == qpid::amqp_0_10::ListCodec::contentType) {
                message.getContentObject() = qpid::types::Variant::List();
                decode(message, message.getContentObject().asList());
            } else if (!message.getContentBytes().empty()) {
                message.getContentObject() = message.getContentBytes();
                if (message.getContentType() == TEXT_PLAIN) {
                    message.getContentObject().setEncoding(qpid::types::encodings::UTF8);
                } else {
                    message.getContentObject().setEncoding(qpid::types::encodings::BINARY);
                }
            }
        }
        return true;
    } else {
        return false;
    }
}

bool ReceiverImpl::fetchImpl(qpid::messaging::Message& message, qpid::messaging::Duration timeout)
{
    {
        sys::Mutex::ScopedLock l(lock);
        if (state == CANCELLED) return false;

        if (capacity == 0 || state != STARTED) {
            session.messageSetFlowMode(destination, FLOW_MODE_CREDIT);
            session.messageFlow(destination, CREDIT_UNIT_MESSAGE, 1);
            session.messageFlow(destination, CREDIT_UNIT_BYTE, 0xFFFFFFFF);
        }
    }
    if (getImpl(message, timeout)) {
        return true;
    } else {
        qpid::client::Session s;
        {
            sys::Mutex::ScopedLock l(lock);
            if (state == CANCELLED) return false; // Might have been closed during get.
            s = sync(session);
        }
        s.messageFlush(destination);
        {
            sys::Mutex::ScopedLock l(lock);
            startFlow(l); //reallocate credit
            session.sendCompletion();//ensure previously received messages are signalled as completed
        }
        return getImpl(message, Duration::IMMEDIATE);
    }
}

void ReceiverImpl::closeImpl()
{
    sys::Mutex::ScopedLock l(lock);
    if (state != CANCELLED) {
        state = CANCELLED;
        sync(session).messageStop(destination);
        {
            sys::Mutex::ScopedUnlock l(lock);
            parent->releasePending(destination);
        }
        source->cancel(session, destination);
        {
            sys::Mutex::ScopedUnlock l(lock);
            parent->receiverCancelled(destination);
        }
    }
}

bool ReceiverImpl::isClosed() const {
    sys::Mutex::ScopedLock l(lock);
    return state == CANCELLED;
}

void ReceiverImpl::setCapacityImpl(uint32_t c)
{
    sys::Mutex::ScopedLock l(lock);
    if (c != capacity) {
        capacity = c;
        if (state == STARTED) {
            session.messageStop(destination);
            startFlow(l);
        }
    }
}

qpid::messaging::Session ReceiverImpl::getSession() const
{
    return qpid::messaging::Session(parent.get());
}

}}} // namespace qpid::client::amqp0_10
