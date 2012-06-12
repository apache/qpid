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
#include "Backup.h"
#include "ConnectionExcluder.h"
#include "HaBroker.h"
#include "Primary.h"
#include "ReplicatingSubscription.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Queue.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/log/Statement.h"
#include <boost/bind.hpp>

namespace qpid {
namespace ha {

Primary* Primary::instance = 0;

Primary::Primary(HaBroker& hb, const IdSet& backups) :
    haBroker(hb), logPrefix("HA primary: "),
    unready(0), activated(false),
    queues(hb.getBroker(), hb.getReplicationTest(), backups)
{
    assert(instance == 0);
    instance = this;            // Let queue replicators find us.
    if (backups.empty()) {
        QPID_LOG(debug, logPrefix << "Not waiting for backups");
        activated = true;
    }
    else {
        QPID_LOG(debug, logPrefix << "Waiting for backups: " << backups);
    }
}

void Primary::readyReplica(const ReplicatingSubscription& rs) {
    sys::Mutex::ScopedLock l(lock);
    if (queues.ready(rs.getQueue(), rs.getBrokerInfo().getSystemId()) && !activated) {
        activated = true;
        haBroker.activate();
        QPID_LOG(notice, logPrefix << "Activated, all initial queues are safe.");
    }
}

}} // namespace qpid::ha
