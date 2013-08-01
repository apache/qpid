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

#include "Event.h"
#include "HaBroker.h"
#include "Primary.h"
#include "PrimaryTxObserver.h"
#include "QueueGuard.h"
#include "RemoteBackup.h"
#include "ReplicatingSubscription.h"

#include "qpid/broker/Broker.h"
#include "qpid/broker/Queue.h"
#include <boost/lexical_cast.hpp>

namespace qpid {
namespace ha {

using namespace std;
using namespace boost;
using namespace broker;

PrimaryTxObserver::PrimaryTxObserver(HaBroker& hb) :
    haBroker(hb), broker(hb.getBroker()),
    id(true)          // FIXME aconway 2013-07-11: is UUID an appropriate TX ID?
{
    logPrefix = "Primary transaction "+id.str().substr(0,8)+": ";
    QPID_LOG(trace, logPrefix << "started");
    pair<shared_ptr<Queue>, bool> result =
        broker.getQueues().declare(
            TRANSACTION_REPLICATOR_PREFIX+id.str(),
            QueueSettings(/*durable*/false, /*autodelete*/true));
    assert(result.second);
    txQueue = result.first;
}

void PrimaryTxObserver::enqueue(const QueuePtr& q, const broker::Message& m)
{
    QPID_LOG(trace, logPrefix << "enqueue: " << LogMessageId(*q, m));
    enqueues[q] += m.getReplicationId();
    txQueue->deliver(TxEnqueueEvent(q->getName(), m.getReplicationId()).message());
    txQueue->deliver(m);
}

void PrimaryTxObserver::dequeue(
    const QueuePtr& q, QueuePosition pos, ReplicationId id)
{
    QPID_LOG(trace, logPrefix << "dequeue: " << LogMessageId(*q, pos, id));
    txQueue->deliver(TxDequeueEvent(q->getName(), id).message());
}

void PrimaryTxObserver::deduplicate() {
    shared_ptr<Primary> primary(boost::dynamic_pointer_cast<Primary>(haBroker.getRole()));
    assert(primary);
    // FIXME aconway 2013-07-29: need to verify which backups are *in* the transaction.
    // Use cluster membership for now
    BrokerInfo::Set brokers = haBroker.getMembership().getBrokers();
    types::Uuid selfId = haBroker.getMembership().getSelf();
    // Tell replicating subscriptions to skip IDs in the transaction.
    for (BrokerInfo::Set::iterator b = brokers.begin(); b != brokers.end(); ++b) {
        if (b->getSystemId() == selfId) continue;
        for (QueueIdsMap::iterator q = enqueues.begin(); q != enqueues.end(); ++q)
            primary->skip(b->getSystemId(), q->first, q->second);
    }
}

bool PrimaryTxObserver::prepare() {
    // FIXME aconway 2013-07-23: need to delay completion of prepare till all
    // backups have prepared.
    QPID_LOG(trace, logPrefix << "prepare");
    deduplicate();
    txQueue->deliver(TxPrepareEvent().message());
    return true;
}

void PrimaryTxObserver::commit() {
    QPID_LOG(trace, logPrefix << "commit");
    txQueue->deliver(TxCommitEvent().message());
}

void PrimaryTxObserver::rollback() {
    QPID_LOG(trace, logPrefix << "rollback");
    txQueue->deliver(TxRollbackEvent().message());
}

}} // namespace qpid::ha
