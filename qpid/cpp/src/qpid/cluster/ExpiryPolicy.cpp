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

#include "qpid/broker/Message.h"
#include "qpid/cluster/ExpiryPolicy.h"
#include "qpid/cluster/Multicaster.h"
#include "qpid/framing/ClusterMessageExpiredBody.h"
#include "qpid/sys/Time.h"
#include "qpid/sys/Timer.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace cluster {

ExpiryPolicy::ExpiryPolicy(Multicaster& m, const MemberId& id, sys::Timer& t)
    : expiryId(1), expiredPolicy(new Expired), mcast(m), memberId(id), timer(t) {}

struct ExpiryTask : public sys::TimerTask {
    ExpiryTask(const boost::intrusive_ptr<ExpiryPolicy>& policy, uint64_t id, sys::AbsTime when)
        : TimerTask(when,"ExpiryPolicy"), expiryPolicy(policy), expiryId(id) {}
    void fire() { expiryPolicy->sendExpire(expiryId); }
    boost::intrusive_ptr<ExpiryPolicy> expiryPolicy;
    const uint64_t expiryId;
};

// Called while receiving an update
void ExpiryPolicy::setId(uint64_t id) {
    sys::Mutex::ScopedLock l(lock);
    expiryId = id;
}

// Called while giving an update
uint64_t ExpiryPolicy::getId() const {
    sys::Mutex::ScopedLock l(lock);
    return expiryId;
}

// Called in enqueuing connection thread
void ExpiryPolicy::willExpire(broker::Message& m) {
    uint64_t id;
    {
        // When messages are fanned out to multiple queues, update sends
        // them as independenty messages so we can have multiple messages
        // with the same expiry ID.
        //
        sys::Mutex::ScopedLock l(lock);
        id = expiryId++;
        if (!id) {              // This is an update of an already-expired message.
            m.setExpiryPolicy(expiredPolicy);
        }
        else {
            assert(unexpiredByMessage.find(&m) == unexpiredByMessage.end());
            // If this is an update, the id may already exist
            unexpiredById.insert(IdMessageMap::value_type(id, &m));
            unexpiredByMessage[&m] = id;
        }
    }
    timer.add(new ExpiryTask(this, id, m.getExpiration()));
}

// Called in dequeueing connection thread
void ExpiryPolicy::forget(broker::Message& m) {
    sys::Mutex::ScopedLock l(lock);
    MessageIdMap::iterator i = unexpiredByMessage.find(&m);
    assert(i != unexpiredByMessage.end());
    unexpiredById.erase(i->second);
    unexpiredByMessage.erase(i);
}

// Called in dequeueing connection or cleanup thread.
bool ExpiryPolicy::hasExpired(broker::Message& m) {
    sys::Mutex::ScopedLock l(lock);
    return unexpiredByMessage.find(&m) == unexpiredByMessage.end();
}

// Called in timer thread
void ExpiryPolicy::sendExpire(uint64_t id) {
    {
        sys::Mutex::ScopedLock l(lock);
        // Don't multicast an expiry notice if message is already forgotten.
        if (unexpiredById.find(id) == unexpiredById.end()) return;
    }
    mcast.mcastControl(framing::ClusterMessageExpiredBody(framing::ProtocolVersion(), id), memberId);
}

// Called in CPG deliver thread.
void ExpiryPolicy::deliverExpire(uint64_t id) {
    sys::Mutex::ScopedLock l(lock);
    std::pair<IdMessageMap::iterator, IdMessageMap::iterator> expired = unexpiredById.equal_range(id);
    IdMessageMap::iterator i = expired.first;
    while (i != expired.second) {
        i->second->setExpiryPolicy(expiredPolicy); // hasExpired() == true; 
        unexpiredByMessage.erase(i->second);
        unexpiredById.erase(i++);
    }
}

// Called in update thread on the updater.
boost::optional<uint64_t> ExpiryPolicy::getId(broker::Message& m) {
    sys::Mutex::ScopedLock l(lock);
    MessageIdMap::iterator i = unexpiredByMessage.find(&m);
    return i == unexpiredByMessage.end() ? boost::optional<uint64_t>() : i->second;
}

bool ExpiryPolicy::Expired::hasExpired(broker::Message&) { return true; }
void ExpiryPolicy::Expired::willExpire(broker::Message&) { }

}} // namespace qpid::cluster
