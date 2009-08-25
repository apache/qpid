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
#include "MessageSource.h"
#include "SessionImpl.h"
#include "qpid/messaging/MessageListener.h"
#include "qpid/messaging/Receiver.h"

namespace qpid {
namespace client {
namespace amqp0_10 {

using qpid::messaging::Receiver;

void ReceiverImpl::received(qpid::messaging::Message&)
{
    //TODO: should this be configurable
    if (capacity && --window <= capacity/2) {
        session.sendCompletion();
        window = capacity;
    }
}

bool ReceiverImpl::get(qpid::messaging::Message& message, qpid::sys::Duration timeout)
{
    return parent.get(*this, message, timeout);
}
    
qpid::messaging::Message ReceiverImpl::get(qpid::sys::Duration timeout) 
{
    qpid::messaging::Message result;
    if (!get(result, timeout)) throw Receiver::NoMessageAvailable();
    return result;
}

bool ReceiverImpl::fetch(qpid::messaging::Message& message, qpid::sys::Duration timeout)
{
    if (capacity == 0 && !cancelled) {
        session.messageFlow(destination, CREDIT_UNIT_MESSAGE, 1);
        if (!started) session.messageFlow(destination, CREDIT_UNIT_BYTE, byteCredit);
    }
    
    if (get(message, timeout)) {
        return true;
    } else {
        if (!cancelled) {
            sync(session).messageFlush(destination);
            start();//reallocate credit
        }
        return get(message, 0);
    }
}
    
qpid::messaging::Message ReceiverImpl::fetch(qpid::sys::Duration timeout) 
{
    qpid::messaging::Message result;
    if (!fetch(result, timeout)) throw Receiver::NoMessageAvailable();
    return result;
}

void ReceiverImpl::cancel() 
{ 
    if (!cancelled) {
        //TODO: should syncronicity be an optional argument to this call?
        source->cancel(session, destination);
        //need to be sure cancel is complete and all incoming
        //framesets are processed before removing the receiver
        parent.receiverCancelled(destination);
        cancelled = true;
    }
}

void ReceiverImpl::start()
{
    if (!cancelled) {
        started = true;
        session.messageSetFlowMode(destination, capacity > 0);
        session.messageFlow(destination, CREDIT_UNIT_MESSAGE, capacity);
        session.messageFlow(destination, CREDIT_UNIT_BYTE, byteCredit);
        window = capacity;
    }
}

void ReceiverImpl::stop()
{
    session.messageStop(destination);
    started = false;
}

void ReceiverImpl::subscribe()
{
    source->subscribe(session, destination);
}

void ReceiverImpl::setSession(qpid::client::AsyncSession s) 
{ 
    session = s;
    if (!cancelled) {
        subscribe();
        //if we were in started state before the session was changed,
        //start again on this new session
        //TODO: locking if receiver is to be threadsafe...
        if (started) start();
    }
}

void ReceiverImpl::setCapacity(uint32_t c)
{
    if (c != capacity) {
        capacity = c;
        if (!cancelled && started) {
            stop();
            start();
        }
    }
}

void ReceiverImpl::setListener(qpid::messaging::MessageListener* l) { listener = l; }
qpid::messaging::MessageListener* ReceiverImpl::getListener() { return listener; }

const std::string& ReceiverImpl::getName() const { return destination; }

ReceiverImpl::ReceiverImpl(SessionImpl& p, const std::string& name, std::auto_ptr<MessageSource> s) : 
    parent(p), source(s), destination(name), byteCredit(0xFFFFFFFF), 
    capacity(0), started(false), cancelled(false), listener(0), window(0) {}


}}} // namespace qpid::client::amqp0_10
