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

#include "QueueContext.h"
#include "Multicaster.h"
#include "qpid/framing/ClusterQueueResubscribeBody.h"
#include "qpid/framing/ClusterQueueUnsubscribeBody.h"
#include "qpid/broker/Queue.h"
#include "qpid/log/Statement.h"


namespace qpid {
namespace cluster {

QueueContext::QueueContext(broker::Queue& q, Multicaster& m)
    : owner(NOT_OWNER), count(0), queue(q), mcast(m)
{
    QPID_LOG(debug, "Assign cluster context to queue " << q.getName());
    q.stop();                   // Initially stopped. Must all before setClusterContext
    q.setClusterContext(boost::intrusive_ptr<QueueContext>(this));

}

// Called by QueueReplica in deliver thread.
void QueueContext::sharedOwner(size_t limit) {
    QPID_LOG(critical, "FIXME QueueContext::sharedOwner " << queue.getName() << queue.getClusterContext().get());
    sys::Mutex::ScopedLock l(lock);
    count = limit;
    if (owner == NOT_OWNER) queue.start(); // FIXME aconway 2011-06-09: ok inside mutex?
    owner = SHARED_OWNER;
}

// Called by QueueReplica in deliver thread.
void QueueContext::soleOwner() {
    QPID_LOG(critical, "FIXME QueueContext::soleOwner " << queue.getName() << queue.getClusterContext().get());
    sys::Mutex::ScopedLock l(lock);
    count = 0;
    if (owner == NOT_OWNER) queue.start(); // FIXME aconway 2011-06-09: ok inside mutex?
    owner = SOLE_OWNER;
}

// Called by BrokerContext in connection thread(s) on acquiring a message
void QueueContext::acquire() {
    bool stop = false;
    {
        sys::Mutex::ScopedLock l(lock);
        assert(owner != NOT_OWNER); // Can't acquire on a queue we don't own.
        QPID_LOG(critical, "FIXME QueueContext::acquire " << queue.getName()
                 << " owner="  << owner << " count=" << count);
        if (owner == SHARED_OWNER) {
            // Note count could be 0 if there are concurrent calls to acquire.
            if (count && --count == 0) {
                stop = true;
            }
        }
    }
    // FIXME aconway 2011-06-28: could have multiple stop() threads...
    if (stop) queue.stop();
}

// Callback set up by queue.stop()
void QueueContext::stopped() {
    sys::Mutex::ScopedLock l(lock);
    if (owner == NOT_OWNER) {
        mcast.mcast(framing::ClusterQueueUnsubscribeBody(
                        framing::ProtocolVersion(), queue.getName()));
    } else {
        owner = NOT_OWNER;
        mcast.mcast(framing::ClusterQueueResubscribeBody(
                        framing::ProtocolVersion(), queue.getName()));
    }
}

void QueueContext::unsubscribed() {
    QPID_LOG(critical, "FIXME QueueContext unsubscribed, stopping " << queue.getName());
    queue.stop();
    sys::Mutex::ScopedLock l(lock);
    owner = NOT_OWNER;
}

boost::intrusive_ptr<QueueContext> QueueContext::get(broker::Queue& q) {
    return boost::intrusive_ptr<QueueContext>(
        static_cast<QueueContext*>(q.getClusterContext().get()));
}


}} // namespace qpid::cluster
