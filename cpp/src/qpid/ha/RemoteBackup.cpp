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
#include "RemoteBackup.h"
#include "QueueGuard.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueRegistry.h"
#include <boost/bind.hpp>

namespace qpid {
namespace ha {

using sys::Mutex;

RemoteBackup::RemoteBackup(
    const BrokerInfo& info, broker::Broker& broker, ReplicationTest rt) :
    logPrefix("HA primary, backup to "+info.getLogId()+": "), brokerInfo(info), replicationTest(rt)
{
    QPID_LOG(debug, logPrefix << "Guarding queues for backup broker. ");
    broker.getQueues().eachQueue(boost::bind(&RemoteBackup::initialQueue, this, _1));
}

bool RemoteBackup::isReady() {
    return initialQueues.empty();
}

void RemoteBackup::initialQueue(const QueuePtr& q) {
    initialQueues.insert(q);
    queueCreate(q);
}

RemoteBackup::GuardPtr RemoteBackup::guard(const QueuePtr& q) {
    GuardMap::iterator i = guards.find(q);
    if (i == guards.end()) {
        assert(0);
        throw Exception(logPrefix+": Guard cannot find queue guard: "+q->getName());
    }
    GuardPtr guard = i->second;
    guards.erase(i);
    return guard;
}

void RemoteBackup::ready(const QueuePtr& q) {
    initialQueues.erase(q);
}

void RemoteBackup::queueCreate(const QueuePtr& q) {
    if (replicationTest.isReplicated(ALL, *q)) {
        QPID_LOG(debug, logPrefix << "Setting guard on " << q->getName());
        guards[q].reset(new QueueGuard(*q, brokerInfo));
    }
}

void RemoteBackup::queueDestroy(const QueuePtr& q) {
    initialQueues.erase(q);
    GuardMap::iterator i = guards.find(q);
    if (i != guards.end()) {
        i->second->cancel();
        guards.erase(i);
    }
}

}} // namespace qpid::ha
