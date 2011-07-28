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
#include "QueueReplica.h"
#include "QueueContext.h"
#include "qpid/broker/Queue.h"
#include "qpid/log/Statement.h"
#include <algorithm>

namespace qpid {
namespace cluster {

QueueReplica::QueueReplica(boost::shared_ptr<broker::Queue> q,
                           const MemberId& self_)
    : queue(q), self(self_), context(QueueContext::get(*q))
{
    // q is initially stopped.
}

struct PrintSubscribers {
    const QueueReplica::MemberQueue& mq;
    PrintSubscribers(const QueueReplica::MemberQueue& m) : mq(m) {}
};

std::ostream& operator<<(std::ostream& o, const PrintSubscribers& ps) {
    copy(ps.mq.begin(), ps.mq.end(), std::ostream_iterator<MemberId>(o, " "));
    return o;
}

std::ostream& operator<<(std::ostream& o, QueueOwnership s) {
    static char* tags[] = { "UNSUBSCRIBED", "SUBSCRIBED", "SOLE_OWNER", "SHARED_OWNER" };
    return o << tags[s];
}

std::ostream& operator<<(std::ostream& o, const QueueReplica& qr) {
    o << qr.queue->getName() << "(" << qr.getState() << "): "
      <<  PrintSubscribers(qr.subscribers);
    return o;
}

// FIXME aconway 2011-05-17: error handling for asserts.

void QueueReplica::subscribe(const MemberId& member) {
    QueueOwnership before = getState();
    subscribers.push_back(member);
    update(before);
}

void QueueReplica::unsubscribe(const MemberId& member) {
    QueueOwnership before = getState();
    MemberQueue::iterator i = std::remove(subscribers.begin(), subscribers.end(), member);
    if (i != subscribers.end()) {
        subscribers.erase(i, subscribers.end());
        update(before);
    }
}

void QueueReplica::resubscribe(const MemberId& member) {
    assert (member == subscribers.front()); // FIXME aconway 2011-06-27: error handling
    QueueOwnership before = getState();
    subscribers.pop_front();
    subscribers.push_back(member);
    update(before);
}

void QueueReplica::update(QueueOwnership before) {
    QPID_LOG(trace, "QueueReplica " << *this << " (was " << before << ")");
    QueueOwnership after = getState();
    if (before == after) return;
    context->replicaState(after);
}

QueueOwnership QueueReplica::getState() const {
    if (isOwner())
        return (subscribers.size() > 1) ? SHARED_OWNER : SOLE_OWNER;
    return (isSubscriber(self)) ? SUBSCRIBED : UNSUBSCRIBED;
}

bool QueueReplica::isOwner() const {
    return !subscribers.empty() && subscribers.front() == self;
}

bool QueueReplica::isSubscriber(const MemberId& member) const {
    // FIXME aconway 2011-06-27: linear search here, is it a performance issue?
    return std::find(subscribers.begin(), subscribers.end(), member) != subscribers.end();
}

}} // namespace qpid::cluster::exp
