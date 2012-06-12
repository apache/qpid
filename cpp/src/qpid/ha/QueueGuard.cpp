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
#include "QueueGuard.h"
#include "ReplicatingSubscription.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueuedMessage.h"
#include "qpid/log/Statement.h"
#include <sstream>

namespace qpid {
namespace ha {

using namespace broker;
using sys::Mutex;

QueueGuard::QueueGuard(broker::Queue& q, const BrokerInfo& info)
  : queue(q), subscription(0)
{
    // Set a log prefix message that identifies the remote broker.
    std::ostringstream os;
    os << "HA subscription " << queue.getName() << "@" << info.getLogId() << ": ";
    logPrefix = os.str();
}

void QueueGuard::initialize() {
    Mutex::ScopedLock l(lock);
    queue.addObserver(shared_from_this());
}

void QueueGuard::enqueued(const QueuedMessage& qm) {
    // Delay completion
    QPID_LOG(trace, logPrefix << "Delaying completion of " << qm);
    qm.payload->getIngressCompletion().startCompleter();
    {
        sys::Mutex::ScopedLock l(lock);
        assert(!delayed.contains(qm.position));
        delayed += qm.position;
    }
}

// FIXME aconway 2012-06-05: ERROR, must call on ReplicatingSubscription

void QueueGuard::dequeued(const QueuedMessage& qm) {
    QPID_LOG(trace, logPrefix << "Dequeued " << qm);
    ReplicatingSubscription* rs = 0;
    {
        sys::Mutex::ScopedLock l(lock);
        rs = subscription;
    }
    if (rs) rs->dequeued(qm);
}

void QueueGuard::cancel() {
    queue.removeObserver(shared_from_this());
    queue.eachMessage(boost::bind(&QueueGuard::complete, this, _1));
}

void QueueGuard::attach(ReplicatingSubscription& rs) {
    sys::Mutex::ScopedLock l(lock);
    subscription = &rs;
}

void QueueGuard::complete(const QueuedMessage& qm, sys::Mutex::ScopedLock&) {
    QPID_LOG(trace, logPrefix << "Completed " << qm);
    // The same message can be completed twice, by acknowledged and
    // dequeued, remove it from the set so we only call
    // finishCompleter() once
    if (delayed.contains(qm.position)) {
        qm.payload->getIngressCompletion().finishCompleter();
        delayed -= qm.position;
    }
}

void QueueGuard::complete(const QueuedMessage& qm) {
    Mutex::ScopedLock l(lock);
    complete(qm, l);
}

// FIXME aconway 2012-06-04: TODO support for timeout.

}} // namespaces qpid::ha
