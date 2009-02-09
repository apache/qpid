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

#include "ExpiryPolicy.h"
#include "Multicaster.h"
#include "qpid/framing/ClusterMessageExpiredBody.h"
#include "qpid/sys/Time.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/Timer.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace cluster {

ExpiryPolicy::ExpiryPolicy(const boost::function<bool()> & f, Multicaster& m, const MemberId& id, broker::Timer& t)
    : expiredPolicy(new Expired), isLeader(f), mcast(m), memberId(id), timer(t) {}

namespace {
uint64_t clusterId(const broker::Message& m) {
    assert(m.getFrames().begin() != m.getFrames().end());
    return m.getFrames().begin()->getClusterId();
}

struct ExpiryTask : public broker::TimerTask {
    ExpiryTask(const boost::intrusive_ptr<ExpiryPolicy>& policy, uint64_t id, sys::AbsTime when)
        : TimerTask(when), expiryPolicy(policy), messageId(id) {}
    void fire() { expiryPolicy->sendExpire(messageId); }
    boost::intrusive_ptr<ExpiryPolicy> expiryPolicy;
    const uint64_t messageId;
};
}

void ExpiryPolicy::willExpire(broker::Message& m) {
    timer.add(new ExpiryTask(this, clusterId(m), m.getExpiration()));
}

bool ExpiryPolicy::hasExpired(broker::Message& m) {
    sys::Mutex::ScopedLock l(lock);
    IdSet::iterator i = expired.find(clusterId(m));
    if (i != expired.end()) {
        expired.erase(i);
        const_cast<broker::Message&>(m).setExpiryPolicy(expiredPolicy); // hasExpired() == true; 
        return true;
    }
    return false;
}

void ExpiryPolicy::sendExpire(uint64_t id) {
    sys::Mutex::ScopedLock l(lock);
    if (isLeader()) 
        mcast.mcastControl(framing::ClusterMessageExpiredBody(framing::ProtocolVersion(), id), memberId);
}

void ExpiryPolicy::deliverExpire(uint64_t id) {
    sys::Mutex::ScopedLock l(lock);
    expired.insert(id);
}

bool ExpiryPolicy::Expired::hasExpired(broker::Message&) { return true; }
void ExpiryPolicy::Expired::willExpire(broker::Message&) { }

}} // namespace qpid::cluster
