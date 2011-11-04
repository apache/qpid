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
#include "PrettyId.h"
#include "qpid/broker/Queue.h"
#include "qpid/log/Statement.h"
#include <algorithm>

namespace qpid {
namespace cluster {

QueueReplica::QueueReplica(boost::shared_ptr<broker::Queue> q,
                           const MemberId& self_)
    : queue(q), self(self_), context(QueueContext::get(*q))
{}

struct PrintSubscribers {
    const QueueReplica::MemberQueue& mq;
    MemberId self;
    PrintSubscribers(const QueueReplica::MemberQueue& m, const MemberId& s) : mq(m), self(s) {}
};

std::ostream& operator<<(std::ostream& o, const PrintSubscribers& ps) {
    for (QueueReplica::MemberQueue::const_iterator i = ps.mq.begin();  i != ps.mq.end(); ++i)
        o << PrettyId(*i, ps.self) << " ";
    return o;
}

std::ostream& operator<<(std::ostream& o, QueueOwnership s) {
    static char* tags[] = { "unsubscribed", "subscribed", "sole_owner", "shared_owner" };
    return o << tags[s];
}

void QueueReplica::subscribe(const MemberId& member) {
    QueueOwnership before = getState();
    subscribers.push_back(member);
    update(before);
}

// FIXME aconway 2011-09-20: need to requeue.
void QueueReplica::unsubscribe(const MemberId& member) {
    QueueOwnership before = getState();
    MemberQueue::iterator i = std::remove(subscribers.begin(), subscribers.end(), member);
    if (i != subscribers.end()) subscribers.erase(i, subscribers.end());
    update(before);
}

void QueueReplica::resubscribe(const MemberId& member) {
    assert (member == subscribers.front());
    QueueOwnership before = getState();
    subscribers.pop_front();
    subscribers.push_back(member);
    update(before);
}

void QueueReplica::update(QueueOwnership before) {
    QueueOwnership after = getState();
    QPID_LOG(trace, "cluster: queue replica: " << queue->getName() << ": "
                 << before << "->" << after << " [" << PrintSubscribers(subscribers, self) << "]");
    context->replicaState(before, after);
}

QueueOwnership QueueReplica::getState() const {
    if (isOwner())
        return (subscribers.size() > 1) ? SHARED_OWNER : SOLE_OWNER;
    else
        return (isSubscriber(self)) ? SUBSCRIBED : UNSUBSCRIBED;
}

bool QueueReplica::isOwner() const {
    return !subscribers.empty() && subscribers.front() == self;
}

bool QueueReplica::isSubscriber(const MemberId& member) const {
    return std::find(subscribers.begin(), subscribers.end(), member) != subscribers.end();
}

}} // namespace qpid::cluster::exp
