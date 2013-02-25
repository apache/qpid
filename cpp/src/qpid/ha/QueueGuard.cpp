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
#include "qpid/broker/QueueObserver.h"
#include "qpid/log/Statement.h"
#include <boost/bind.hpp>
#include <sstream>

namespace qpid {
namespace ha {

using namespace broker;
using sys::Mutex;
using framing::SequenceNumber;
using framing::SequenceSet;

class QueueGuard::QueueObserver : public broker::QueueObserver
{
  public:
    QueueObserver(QueueGuard& g) : guard(g) {}
    void enqueued(const broker::Message& m) { guard.enqueued(m); }
    void dequeued(const broker::Message& m) { guard.dequeued(m); }
    void acquired(const broker::Message&) {}
    void requeued(const broker::Message&) {}
  private:
    QueueGuard& guard;
};



QueueGuard::QueueGuard(broker::Queue& q, const BrokerInfo& info)
    : cancelled(false), queue(q), subscription(0)
{
    std::ostringstream os;
    os << "Primary guard " << queue.getName() << "@" << info << ": ";
    logPrefix = os.str();
    observer.reset(new QueueObserver(*this));
    queue.addObserver(observer);
    // Set range after addObserver so we know that range.back+1 is a guarded position.
    range = QueueRange(q);
}

QueueGuard::~QueueGuard() { cancel(); }

// NOTE: Called with message lock held.
void QueueGuard::enqueued(const Message& m) {
    // Delay completion
    QPID_LOG(trace, logPrefix << "Delayed completion of " << m.getSequence());
    Mutex::ScopedLock l(lock);
    if (cancelled) return;  // Don't record enqueues after we are cancelled.
    assert(delayed.find(m.getSequence()) == delayed.end());
    delayed[m.getSequence()] = m.getIngressCompletion();
    m.getIngressCompletion()->startCompleter();
}

// NOTE: Called with message lock held.
void QueueGuard::dequeued(const Message& m) {
    QPID_LOG(trace, logPrefix << "Dequeued " << m);
    Mutex::ScopedLock l(lock);
    if (subscription) subscription->dequeued(m);
    complete(m.getSequence(), l);
}

void QueueGuard::cancel() {
    queue.removeObserver(observer);
    Mutex::ScopedLock l(lock);
    if (cancelled) return;
    cancelled = true;
    for (Delayed::iterator i = delayed.begin(); i != delayed.end();) {
        complete(i, l);
        delayed.erase(i++);
    }
}

void QueueGuard::attach(ReplicatingSubscription& rs) {
    Mutex::ScopedLock l(lock);
    subscription = &rs;
}

bool QueueGuard::subscriptionStart(SequenceNumber position) {
    // Complete any messages before or at the ReplicatingSubscription start position.
    // Those messages are already on the backup.
    Mutex::ScopedLock l(lock);
    Delayed::iterator i = delayed.begin();
    while(i != delayed.end() && i->first <= position) {
        complete(i, l);
        delayed.erase(i++);
    }
    return position >= range.back;
}

void QueueGuard::complete(SequenceNumber sequence) {
    Mutex::ScopedLock l(lock);
    complete(sequence, l);
}

void QueueGuard::complete(SequenceNumber sequence, Mutex::ScopedLock& l) {
    // The same message can be completed twice, by
    // ReplicatingSubscription::acknowledged and dequeued. Remove it
    // from the map so we only call finishCompleter() once
    Delayed::iterator i = delayed.find(sequence);
    if (i != delayed.end()) {
        complete(i, l);
        delayed.erase(i);
    }
}

void QueueGuard::complete(Delayed::iterator i, Mutex::ScopedLock&) {
    QPID_LOG(trace, logPrefix << "Completed " << i->first);
    i->second->finishCompleter();
}

}} // namespaces qpid::ha
