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
#include "qpid/broker/Broker.h"
#include "qpid/broker/Queue.h"
#include "qpid/framing/FieldTable.h"
#include <boost/bind.hpp>

namespace qpid {
namespace ha {

Primary* Primary::instance = 0;

Primary::Primary(HaBroker& b) :
    haBroker(b), logPrefix(b),
    expected(b.getSettings().expectedBackups),
    unready(0), activated(false)
{
    instance = this;            // Let queue replicators find us.
    if (expected == 0)          // No need for ready check
        activate(*(sys::Mutex::ScopedLock*)0); // fake lock, ok in ctor.
    else {
        // Set up the primary-ready check: ready when all queues have
        // expected number of backups. Note clients are excluded at this point
        // so dont't have to worry about changes to the set of queues.
        HaBroker::QueueNames names = haBroker.getActiveBackups();
        for (HaBroker::QueueNames::const_iterator i = names.begin(); i != names.end(); ++i)
        {
            queues[*i] = 0;
            ++unready;
            QPID_LOG(debug, logPrefix << "Need backup of " << *i
                     << ", " << unready << " unready queues");
        }
        if (queues.empty())
            activate(*(sys::Mutex::ScopedLock*)0); // fake lock, ok in ctor.
        else {
            QPID_LOG(debug, logPrefix << "Waiting for  " << expected
                     << " backups on " << queues.size() << " queues");
            // Allow backups to connect.
            haBroker.getExcluder()->setBackupAllowed(true);
        }
    }
}

void Primary::readyReplica(const std::string& q) {
    sys::Mutex::ScopedLock l(lock);
    if (!activated) {
        QueueCounts::iterator i = queues.find(q);
        if (i != queues.end()) {
            ++i->second;
            if (i->second == expected) --unready;
            QPID_LOG(debug, logPrefix << i->second << " backups caught up on " << q
                     << ", " << unready << " unready queues");
            if (unready == 0) activate(l);
        }
    }
}

void Primary::activate(sys::Mutex::ScopedLock&) {
    activated = true;
    haBroker.activate();
}

}} // namespace qpid::ha
