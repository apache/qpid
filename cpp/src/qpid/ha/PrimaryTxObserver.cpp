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
#include "QueueReplicator.h"

#include "qpid/broker/Broker.h"
#include "qpid/broker/Queue.h"
#include <boost/lexical_cast.hpp>
#include <algorithm>

namespace qpid {
namespace framing {
class FieldTable;
}
namespace ha {

using namespace std;
using namespace qpid::broker;
using namespace qpid::framing;
using types::Uuid;

// Exchange to receive prepare OK events.
class PrimaryTxObserver::Exchange : public broker::Exchange {
  public:
    Exchange(const boost::shared_ptr<PrimaryTxObserver>& tx_) :
        broker::Exchange(tx_->getExchangeName()),
        tx(tx_)
    {
        dispatch[TxPrepareOkEvent::KEY] =
            boost::bind(&PrimaryTxObserver::txPrepareOkEvent, tx, _1);
        dispatch[TxPrepareFailEvent::KEY] =
            boost::bind(&PrimaryTxObserver::txPrepareFailEvent, tx, _1);
    }

    void route(Deliverable& deliverable) {
        const broker::Message& message(deliverable.getMessage());
        DispatchMap::iterator i = dispatch.find(message.getRoutingKey());
        if (i != dispatch.end()) i->second(message.getContent());
    }

    bool bind(boost::shared_ptr<Queue>, const string&, const FieldTable*) { return false; }
    bool unbind(boost::shared_ptr<Queue>, const string&, const FieldTable*) { return false; }
    bool isBound(boost::shared_ptr<Queue>, const string* const, const FieldTable* const) { return false; }
    string getType() const { return TYPE_NAME; }

  private:
    static const string TYPE_NAME;
    typedef boost::function<void(const std::string&)> DispatchFn;
    typedef qpid::sys::unordered_map<std::string, DispatchFn> DispatchMap;

    DispatchMap dispatch;
    boost::shared_ptr<PrimaryTxObserver> tx;
};

const string PrimaryTxObserver::Exchange::TYPE_NAME(string(QPID_HA_PREFIX)+"primary-tx-observer");

PrimaryTxObserver::PrimaryTxObserver(
    Primary& p, HaBroker& hb, const boost::intrusive_ptr<broker::TxBuffer>& tx
) :
    primary(p), haBroker(hb), broker(hb.getBroker()),
    replicationTest(hb.getSettings().replicateDefault.get()),
    txBuffer(tx),
    id(true),
    exchangeName(TRANSACTION_REPLICATOR_PREFIX+id.str()),
    complete(false)
{
    logPrefix = "Primary transaction "+shortStr(id)+": ";

    // The brokers known at this point are the ones that will be included
    // in the transaction. Brokers that join later are not included.
    //
    BrokerInfo::Set backups(haBroker.getMembership().otherBackups());
    std::transform(backups.begin(), backups.end(), inserter(members, members.begin()),
		   boost::bind(&BrokerInfo::getSystemId, _1));

    QPID_LOG(debug, logPrefix << "Started TX " << id);
    QPID_LOG(debug, logPrefix << "Members: " << members);
    unprepared = unfinished = members;

    pair<QueuePtr, bool> result =
        broker.getQueues().declare(
            exchangeName, QueueSettings(/*durable*/false, /*autodelete*/true));
    assert(result.second);
    txQueue = result.first;
    txQueue->deliver(TxMembersEvent(members).message());
}

PrimaryTxObserver::~PrimaryTxObserver() {
    QPID_LOG(debug, logPrefix << "Ended");
}

void PrimaryTxObserver::initialize() {
    boost::shared_ptr<Exchange> ex(new Exchange(shared_from_this()));
    FieldTable args = ex->getArgs();
    args.setString(QPID_REPLICATE, printable(NONE).str()); // Set replication arg.
    broker.getExchanges().registerExchange(ex);
}

void PrimaryTxObserver::enqueue(const QueuePtr& q, const broker::Message& m)
{
    sys::Mutex::ScopedLock l(lock);
    if (replicationTest.useLevel(*q) == ALL) { // Ignore unreplicated queues.
        QPID_LOG(trace, logPrefix << "Enqueue: " << LogMessageId(*q, m));
        enqueues[q] += m.getReplicationId();
        txQueue->deliver(TxEnqueueEvent(q->getName(), m.getReplicationId()).message());
        txQueue->deliver(m);
    }
}

void PrimaryTxObserver::dequeue(
    const QueuePtr& q, QueuePosition pos, ReplicationId id)
{
    sys::Mutex::ScopedLock l(lock);
    if (replicationTest.useLevel(*q) == ALL) { // Ignore unreplicated queues.
        QPID_LOG(trace, logPrefix << "Dequeue: " << LogMessageId(*q, pos, id));
        txQueue->deliver(TxDequeueEvent(q->getName(), id).message());
    }
    else {
        QPID_LOG(warning, logPrefix << "Dequeue skipped, queue not replicated: "
                 << LogMessageId(*q, pos, id));
    }
}

namespace {
struct Skip {
    Uuid backup;
    boost::shared_ptr<broker::Queue> queue;
    ReplicationIdSet ids;

    Skip(const Uuid& backup_,
         const boost::shared_ptr<broker::Queue>& queue_,
         const ReplicationIdSet& ids_) :
        backup(backup_), queue(queue_), ids(ids_) {}

    void skip(Primary& p) const { p.skip(backup, queue, ids); }
};
} // namespace

bool PrimaryTxObserver::prepare() {
    QPID_LOG(debug, logPrefix << "Prepare " << members);
    vector<Skip> skips;
    {
        sys::Mutex::ScopedLock l(lock);
        for (size_t i = 0; i < members.size(); ++i) txBuffer->startCompleter();

        // Tell replicating subscriptions to skip IDs in the transaction.
        for (UuidSet::iterator b = members.begin(); b != members.end(); ++b)
            for (QueueIdsMap::iterator q = enqueues.begin(); q != enqueues.end(); ++q)
                skips.push_back(Skip(*b, q->first, q->second));
    }
    // Outside lock
    for_each(skips.begin(), skips.end(),
             boost::bind(&Skip::skip, _1, boost::ref(primary)));
    txQueue->deliver(TxPrepareEvent().message());
    return true;
}

void PrimaryTxObserver::commit() {
    QPID_LOG(debug, logPrefix << "Commit");
    sys::Mutex::ScopedLock l(lock);
    txQueue->deliver(TxCommitEvent().message());
    complete = true;
    end(l);
}

void PrimaryTxObserver::rollback() {
    QPID_LOG(debug, logPrefix << "Rollback");
    sys::Mutex::ScopedLock l(lock);
    txQueue->deliver(TxRollbackEvent().message());
    complete = true;
    end(l);
}

void PrimaryTxObserver::end(sys::Mutex::ScopedLock&) {
    // Don't destroy the tx-queue until the transaction is complete and there
    // are no connected subscriptions.
    if (txBuffer && complete && unfinished.empty()) {
        txBuffer = 0;       // Break pointer cycle.
        try {
            haBroker.getBroker().deleteQueue(txQueue->getName(), haBroker.getUserId(), string());
        } catch (const std::exception& e) {
            QPID_LOG(error, logPrefix << "Deleting transaction queue: "  << e.what());
        }
        try {
            broker.getExchanges().destroy(getExchangeName());
        } catch (const std::exception& e) {
            QPID_LOG(error, logPrefix << "Deleting transaction exchange: "  << e.what());
        }
    }
}

void PrimaryTxObserver::txPrepareOkEvent(const string& data) {
    sys::Mutex::ScopedLock l(lock);
    types::Uuid backup = decodeStr<TxPrepareOkEvent>(data).broker;
    if (unprepared.erase(backup)) {
        QPID_LOG(debug, logPrefix << "Backup prepared ok: " << backup);
        txBuffer->finishCompleter();
    }
}

void PrimaryTxObserver::txPrepareFailEvent(const string& data) {
    sys::Mutex::ScopedLock l(lock);
    types::Uuid backup = decodeStr<TxPrepareFailEvent>(data).broker;
    if (unprepared.erase(backup)) {
        QPID_LOG(error, logPrefix << "Prepare failed on backup: " << backup);
        txBuffer->setError(
            QPID_MSG(logPrefix << "Prepare failed on backup: " << backup));
        txBuffer->finishCompleter();
    }
}

void PrimaryTxObserver::cancel(const ReplicatingSubscription& rs) {
    sys::Mutex::ScopedLock l(lock);
    types::Uuid backup = rs.getBrokerInfo().getSystemId();
    if (unprepared.erase(backup) ){
        complete = true;          // Cancelled before prepared.
        txBuffer->setError(
            QPID_MSG(logPrefix << "Backup disconnected: " << rs.getBrokerInfo()));
        txBuffer->finishCompleter();
    }
    unfinished.erase(backup);
    end(l);
}

}} // namespace qpid::ha
