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

#include "SubscriptionImpl.h"
#include "SubscriptionManager.h"
#include "SubscriptionSettings.h"

namespace qpid {
namespace client {

using sys::Mutex;

SubscriptionImpl::SubscriptionImpl(SubscriptionManager& m, const std::string& q, const SubscriptionSettings& s, const std::string& n, MessageListener* l)
    : manager(m), name(n), queue(q), settings(s), listener(l)
{
    async(manager.getSession()).messageSubscribe( 
        arg::queue=queue,
        arg::destination=name,
        arg::acceptMode=settings.acceptMode,
        arg::acquireMode=settings.acquireMode);
    setFlowControl(settings.flowControl);
}

std::string SubscriptionImpl::getName() const { return name; }

std::string SubscriptionImpl::getQueue() const { return queue; }

const SubscriptionSettings& SubscriptionImpl::getSettings() const {
    Mutex::ScopedLock l(lock);
    return settings;
}

void SubscriptionImpl::setFlowControl(const FlowControl& f) {
    Mutex::ScopedLock l(lock);
    AsyncSession s=manager.getSession();
    if (&settings.flowControl != &f) settings.flowControl = f;
    s.messageSetFlowMode(name, f.window); 
    s.messageFlow(name, CREDIT_UNIT_MESSAGE, f.messages); 
    s.messageFlow(name, CREDIT_UNIT_BYTE, f.bytes);
    s.sync();
}

void SubscriptionImpl::setAutoAck(size_t n) {
    Mutex::ScopedLock l(lock);
    settings.autoAck = n;
}

SequenceSet SubscriptionImpl::getUnacquired() const { Mutex::ScopedLock l(lock); return unacquired; }
SequenceSet SubscriptionImpl::getUnaccepted() const { Mutex::ScopedLock l(lock); return unaccepted; }

void SubscriptionImpl::acquire(const SequenceSet& messageIds) {
    Mutex::ScopedLock l(lock);
    manager.getSession().messageAcquire(messageIds);
    unacquired.remove(messageIds);
    if (settings.acceptMode == ACCEPT_MODE_EXPLICIT)
        unaccepted.add(messageIds);
}

void SubscriptionImpl::accept(const SequenceSet& messageIds) {
    Mutex::ScopedLock l(lock);
    manager.getSession().messageAccept(messageIds);
    unaccepted.remove(messageIds);
}

Session SubscriptionImpl::getSession() const { return manager.getSession(); }

SubscriptionManager&SubscriptionImpl:: getSubscriptionManager() const { return manager; }

void SubscriptionImpl::cancel() { manager.cancel(name); }

void SubscriptionImpl::received(Message& m) {
    Mutex::ScopedLock l(lock);
    manager.getSession().markCompleted(m.getId(), false, false);        
    if (m.getMethod().getAcquireMode() == ACQUIRE_MODE_NOT_ACQUIRED) 
        unacquired.add(m.getId());
    else if (m.getMethod().getAcceptMode() == ACCEPT_MODE_EXPLICIT)
        unaccepted.add(m.getId());

    if (listener) {
        Mutex::ScopedUnlock u(lock);
        listener->received(m);
    }

    if (settings.autoAck) {
        if (unacquired.size() + unaccepted.size() >= settings.autoAck) {
            if (unacquired.size()) {
                async(manager.getSession()).messageAcquire(unacquired);
                unaccepted.add(unacquired);
                unaccepted.clear();
            }
            async(manager.getSession()).messageAccept(unaccepted);
            unaccepted.clear();
        }
    }
}

}} // namespace qpid::client

