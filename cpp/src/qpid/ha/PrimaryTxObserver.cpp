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
#include "qpid/framing/reply_exceptions.h"
#include <boost/lexical_cast.hpp>
#include <algorithm>

namespace qpid {
namespace framing {
class FieldTable;
}
namespace ha {

using namespace std;
using namespace sys;
using namespace broker;
using namespace framing;
using types::Uuid;

// Exchange to receive prepare OK events.
class PrimaryTxObserver::Exchange : public broker::Exchange {
  public:
    Exchange(const boost::shared_ptr<PrimaryTxObserver>& tx_) :
        broker::Exchange(tx_->getExchangeName()),
        tx(tx_)
    {
        args.setString(QPID_REPLICATE, printable(NONE).str()); // Set replication arg.
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
    bool hasBindings() { return false; }
    string getType() const { return TYPE_NAME; }

  private:
    static const string TYPE_NAME;
    typedef boost::function<void(const std::string&)> DispatchFn;
    typedef unordered_map<std::string, DispatchFn> DispatchMap;

    DispatchMap dispatch;
    boost::shared_ptr<PrimaryTxObserver> tx;
};

const string PrimaryTxObserver::Exchange::TYPE_NAME(string(QPID_HA_PREFIX)+"primary-tx-observer");

boost::shared_ptr<PrimaryTxObserver> PrimaryTxObserver::create(
    Primary& p, HaBroker& hb, const boost::intrusive_ptr<broker::TxBuffer>& tx) {
    boost::shared_ptr<PrimaryTxObserver> pto(new PrimaryTxObserver(p, hb, tx));
    pto->initialize();
    return pto;
}


PrimaryTxObserver::PrimaryTxObserver(
    Primary& p, HaBroker& hb, const boost::intrusive_ptr<broker::TxBuffer>& tx
) :
    state(SENDING),
    primary(p), haBroker(hb), broker(hb.getBroker()),
    replicationTest(hb.getSettings().replicateDefault.get()),
    txBuffer(tx),
    id(true),
    exchangeName(TRANSACTION_REPLICATOR_PREFIX+id.str()),
    empty(true)
{
    logPrefix = "Primary transaction "+shortStr(id)+": ";

    // The brokers known at this point are the ones that will be included
    // in the transaction. Brokers that join later are not included.
    //
    BrokerInfo::Set backups_(haBroker.getMembership().otherBackups());
    std::transform(backups_.begin(), backups_.end(), inserter(backups, backups.begin()),
		   boost::bind(&BrokerInfo::getSystemId, _1));

    // Delay completion of TX untill all backups have responded to prepare.
    incomplete = backups;
    for (size_t i = 0; i < incomplete.size(); ++i)
        txBuffer->startCompleter();

    QPID_LOG(debug, logPrefix << "Started TX " << id);
    QPID_LOG(debug, logPrefix << "Backups: " << backups);
}

void PrimaryTxObserver::initialize() {
    boost::shared_ptr<Exchange> ex(new Exchange(shared_from_this()));
    broker.getExchanges().registerExchange(ex);
    pair<QueuePtr, bool> result =
        broker.createQueue(
            exchangeName,
            QueueSettings(/*durable*/false, /*autodelete*/true),
            0,            // no owner regardless of exclusivity on primary
            string(),     // No alternate exchange
            haBroker.getUserId(),
            string());          // Remote host.
    if (!result.second)
        throw InvalidArgumentException(
            QPID_MSG(logPrefix << "TX replication queue already exists."));
    txQueue = result.first;
    txQueue->markInUse(); // Prevent auto-delete till we are done.
    txQueue->deliver(TxBackupsEvent(backups).message());

}


PrimaryTxObserver::~PrimaryTxObserver() {
    QPID_LOG(debug, logPrefix << "Ended");
}

void PrimaryTxObserver::checkState(State expect, const std::string& msg) {
    if (state != expect)
        throw IllegalStateException(QPID_MSG(logPrefix << "Illegal state: " << msg));
}

void PrimaryTxObserver::enqueue(const QueuePtr& q, const broker::Message& m)
{
    Mutex::ScopedLock l(lock);
    if (replicationTest.useLevel(*q) == ALL) { // Ignore unreplicated queues.
        QPID_LOG(trace, logPrefix << "Enqueue: " << logMessageId(*q, m.getReplicationId()));
        checkState(SENDING, "Too late for enqueue");
        empty = false;
        enqueues[q] += m.getReplicationId();
        txQueue->deliver(TxEnqueueEvent(q->getName(), m.getReplicationId()).message());
        txQueue->deliver(m);
    }
}

void PrimaryTxObserver::dequeue(
    const QueuePtr& q, QueuePosition pos, ReplicationId id)
{
    Mutex::ScopedLock l(lock);
    checkState(SENDING, "Too late for dequeue");
    if (replicationTest.useLevel(*q) == ALL) { // Ignore unreplicated queues.
        QPID_LOG(trace, logPrefix << "Dequeue: " << logMessageId(*q, pos, id));
        empty = false;
        dequeues[q] += id;
        txQueue->deliver(TxDequeueEvent(q->getName(), id).message());
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

    void skipEnqueues(Primary& p) const { p.skipEnqueues(backup, queue, ids); }
    void skipDequeues(Primary& p) const { p.skipDequeues(backup, queue, ids); }
};
} // namespace

void PrimaryTxObserver::skip(Mutex::ScopedLock&) {
    // Tell replicating subscriptions to skip IDs in the transaction.
    vector<Skip> skipEnq, skipDeq;
    for (UuidSet::iterator b = backups.begin(); b != backups.end(); ++b) {
        for (QueueIdsMap::iterator q = enqueues.begin(); q != enqueues.end(); ++q)
            skipEnq.push_back(Skip(*b, q->first, q->second));
        for (QueueIdsMap::iterator q = dequeues.begin(); q != dequeues.end(); ++q)
            skipDeq.push_back(Skip(*b, q->first, q->second));
    }
    Mutex::ScopedUnlock u(lock); // Outside lock
    for_each(skipEnq.begin(), skipEnq.end(), boost::bind(&Skip::skipEnqueues, _1, boost::ref(primary)));
    for_each(skipDeq.begin(), skipDeq.end(), boost::bind(&Skip::skipDequeues, _1, boost::ref(primary)));
}

bool PrimaryTxObserver::prepare() {
    QPID_LOG(debug, logPrefix << "Prepare " << backups);
    Mutex::ScopedLock l(lock);
    checkState(SENDING, "Too late for prepare");
    state = PREPARING;
    txQueue->deliver(TxPrepareEvent().message());
    return true;
}

void PrimaryTxObserver::commit() {
    QPID_LOG(debug, logPrefix << "Commit");
    Mutex::ScopedLock l(lock);
    checkState(PREPARING, "Cannot commit, not preparing");
    if (incomplete.size() == 0) {
        skip(l); // Tell local replicating subscriptions to skip tx enqueue/dequeue.
        txQueue->deliver(TxCommitEvent().message());
        end(l);
    } else {
        txQueue->deliver(TxRollbackEvent().message());
        end(l);
        throw PreconditionFailedException(
            QPID_MSG(logPrefix << "Cannot commit, " << incomplete.size()
                     << " incomplete backups"));
    }
}

void PrimaryTxObserver::rollback() {
    Mutex::ScopedLock l(lock);
    // Don't bleat about rolling back empty transactions, this happens all the time
    // when a session closes and rolls back its outstanding transaction.
    if (!empty) QPID_LOG(debug, logPrefix << "Rollback");
    if (state != ENDED) {
        txQueue->deliver(TxRollbackEvent().message());
        end(l);
    }
}

void PrimaryTxObserver::end(Mutex::ScopedLock&) {
    if (state == ENDED) return;
    state = ENDED;
    // If there are no outstanding completions, break pointer cycle here.
    // Otherwise break it in cancel() when the remaining completions are done.
    if (incomplete.empty()) txBuffer = 0;
    txQueue->releaseFromUse();  // txQueue will auto-delete
    txQueue->scheduleAutoDelete();
    txQueue.reset();
    try {
        broker.getExchanges().destroy(getExchangeName());
    } catch (const std::exception& e) {
        QPID_LOG(error, logPrefix << "Deleting transaction exchange: "  << e.what());
    }
}

bool PrimaryTxObserver::completed(const Uuid& id, Mutex::ScopedLock&) {
    if (incomplete.erase(id)) {
        txBuffer->finishCompleter();
        return true;
    }
    return false;
}

bool PrimaryTxObserver::error(const Uuid& id, const char* msg, Mutex::ScopedLock& l)
{
    if (incomplete.find(id) != incomplete.end()) {
        // Note: setError before completed since completed may trigger completion.
        txBuffer->setError(QPID_MSG(logPrefix << msg << id));
        completed(id, l);
        return true;
    }
    return false;
}

void PrimaryTxObserver::txPrepareOkEvent(const string& data) {
    Mutex::ScopedLock l(lock);
    types::Uuid backup = decodeStr<TxPrepareOkEvent>(data).broker;
    if (completed(backup, l)) {
        QPID_LOG(debug, logPrefix << "Backup prepared ok: " << backup);
    } else {
        QPID_LOG(error, logPrefix << "Unexpected prepare-ok response from " << backup);
    }
}

void PrimaryTxObserver::txPrepareFailEvent(const string& data) {
    Mutex::ScopedLock l(lock);
    types::Uuid backup = decodeStr<TxPrepareFailEvent>(data).broker;
    if (error(backup, "Prepare failed on backup: ", l)) {
        QPID_LOG(error, logPrefix << "Prepare failed on backup " << backup);
    } else {
        QPID_LOG(error, logPrefix << "Unexpected prepare-fail response from " << backup);
    }
}

void PrimaryTxObserver::cancel(const ReplicatingSubscription& rs) {
    Mutex::ScopedLock l(lock);
    types::Uuid backup = rs.getBrokerInfo().getSystemId();
    // Normally the backup should be completed before it is cancelled.
    if (completed(backup, l)) error(backup, "Unexpected disconnect:", l);
    // Break the pointer cycle if backups have completed and we are done with txBuffer.
    if (state == ENDED && incomplete.empty()) txBuffer = 0;
}

}} // namespace qpid::ha
