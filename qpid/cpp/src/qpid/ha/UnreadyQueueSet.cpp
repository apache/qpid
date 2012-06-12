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
#include "UnreadyQueueSet.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueRegistry.h"
#include <boost/bind.hpp>
#include <iostream>
#include <algorithm>

namespace qpid {
namespace ha {

using sys::Mutex;

UnreadyQueueSet::UnreadyQueueSet(broker::Broker& broker, ReplicationTest rt, const IdSet& ids) :
    logPrefix("HA unsafe queues: "), replicationTest(rt), expected(ids),
    initializing(true), initialQueues(0)
{
    if (!expected.empty()) {
        QPID_LOG(debug, logPrefix << "Recovering, waiting for backups: " << expected);
        broker.getQueues().eachQueue(boost::bind(&UnreadyQueueSet::queueCreate, this, _1));
        initialQueues = queues.size();
    }
    initializing = false;
}

void UnreadyQueueSet::setExpectedBackups(const IdSet& ids) {
    Mutex::ScopedLock l(lock);
    expected = ids;
}

bool UnreadyQueueSet::ready(const boost::shared_ptr<broker::Queue>& q, const types::Uuid& id) {
    Mutex::ScopedLock l(lock);
    QPID_LOG(debug, logPrefix << "Replicating subscription ready: " << id << " on "
             << q->getName());
    QueueMap::iterator i = queues.find(q);
    if (i != queues.end()) {
        // if (i->second.guard->ready(id)) {
        //     QPID_LOG(debug, logPrefix << "Releasing guard on " << q->getName());
        //     remove(i, l);
            if (i->second.initial) --initialQueues;
            //     }
    }
    return initialQueues == 0;
}

void UnreadyQueueSet::queueCreate(const boost::shared_ptr<broker::Queue>& q) {
    Mutex::ScopedLock l(lock);
    if (replicationTest.isReplicated(*q) && !expected.empty()) {
        QPID_LOG(debug, logPrefix << "Guarding " << q->getName() << " for " << expected);
        // GuardPtr guard(new QueueGuard(*q, expected));
        // FIXME aconway 2012-06-05: q->addObserver(guard);
        queues[q] = Entry(initializing);//, guard);
    }
}

void UnreadyQueueSet::queueDestroy(const boost::shared_ptr<broker::Queue>& q) {
    Mutex::ScopedLock l(lock);
    remove(queues.find(q), l);
}

void UnreadyQueueSet::remove(QueueMap::iterator i, sys::Mutex::ScopedLock&) {
    if (i != queues.end()) {
        QPID_LOG(debug, logPrefix << "Queue is safe: " << i->first->getName());
        // FIXME aconway 2012-06-05: i->first->removeObserver(i->second.guard);
        //i->second.guard->release();
        queues.erase(i);
    }
}

}} // namespace qpid::ha
