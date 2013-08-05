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

namespace qpid {
namespace framing {
class FieldTable;
}
namespace ha {

using namespace std;
using namespace qpid::broker;
using namespace qpid::framing;

// Exchange to receive prepare OK events.
class PrimaryTxObserver::Exchange : public broker::Exchange {
  public:
    Exchange(const boost::shared_ptr<PrimaryTxObserver>& tx_) :
        broker::Exchange(TRANSACTION_REPLICATOR_PREFIX+tx_->getId().str()),
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

PrimaryTxObserver::PrimaryTxObserver(HaBroker& hb) :
    haBroker(hb), broker(hb.getBroker()), id(true), failed(false)
{
    logPrefix = "Primary transaction "+shortStr(id)+": ";

    // The brokers known at this point are the ones that will be included
    // in the transaction. Brokers that join later are not included
    // Latecomers that have replicated the transaction will be rolled back
    // when the tx-queue is deleted.
    //
    BrokerInfo::Set infoSet(haBroker.getMembership().otherBackups());
    std::transform(infoSet.begin(), infoSet.end(), inserter(backups, backups.begin()),
              boost::bind(&BrokerInfo::getSystemId, _1));
    QPID_LOG(debug, logPrefix << "Started on " << backups);

    pair<QueuePtr, bool> result =
        broker.getQueues().declare(
            TRANSACTION_REPLICATOR_PREFIX+id.str(),
            QueueSettings(/*durable*/false, /*autodelete*/true));
    assert(result.second);
    txQueue = result.first;
}

void PrimaryTxObserver::initialize() {
    broker.getExchanges().registerExchange(
        boost::shared_ptr<Exchange>(new Exchange(shared_from_this())));
}

void PrimaryTxObserver::enqueue(const QueuePtr& q, const broker::Message& m)
{
    sys::Mutex::ScopedLock l(lock);
    QPID_LOG(trace, logPrefix << "Enqueue: " << LogMessageId(*q, m));
    enqueues[q] += m.getReplicationId();
    txQueue->deliver(TxEnqueueEvent(q->getName(), m.getReplicationId()).message());
    txQueue->deliver(m);
}

void PrimaryTxObserver::dequeue(
    const QueuePtr& q, QueuePosition pos, ReplicationId id)
{
    sys::Mutex::ScopedLock l(lock);
    QPID_LOG(trace, logPrefix << "Dequeue: " << LogMessageId(*q, pos, id));
    txQueue->deliver(TxDequeueEvent(q->getName(), id).message());
}

void PrimaryTxObserver::deduplicate(sys::Mutex::ScopedLock&) {
    boost::shared_ptr<Primary> primary(boost::dynamic_pointer_cast<Primary>(haBroker.getRole()));
    assert(primary);
    // Tell replicating subscriptions to skip IDs in the transaction.
    for (UuidSet::iterator b = backups.begin(); b != backups.end(); ++b)
        for (QueueIdsMap::iterator q = enqueues.begin(); q != enqueues.end(); ++q)
            primary->skip(*b, q->first, q->second);
}

bool PrimaryTxObserver::prepare() {
    sys::Mutex::ScopedLock l(lock);
    // FIXME aconway 2013-07-23: WRONG blocking. Need async completion.
    QPID_LOG(debug, logPrefix << "Prepare");
    deduplicate(l);
    txQueue->deliver(TxPrepareEvent().message());
    while (!isPrepared(l)) lock.wait();
    return !failed;
}

void PrimaryTxObserver::commit() {
    sys::Mutex::ScopedLock l(lock);
    QPID_LOG(debug, logPrefix << "Commit");
    txQueue->deliver(TxCommitEvent().message());
}

void PrimaryTxObserver::rollback() {
    sys::Mutex::ScopedLock l(lock);
    QPID_LOG(debug, logPrefix << "Rollback");
    txQueue->deliver(TxRollbackEvent().message());
}

void PrimaryTxObserver::txPrepareOkEvent(const string& data) {
    sys::Mutex::ScopedLock l(lock);
    types::Uuid backup = decodeStr<TxPrepareOkEvent>(data).broker;
    QPID_LOG(debug, logPrefix << "Backup prepared ok: " << backup);
    prepared.insert(backup);
    lock.notify();
}

void PrimaryTxObserver::txPrepareFailEvent(const string& data) {
    sys::Mutex::ScopedLock l(lock);
    types::Uuid backup = decodeStr<TxPrepareFailEvent>(data).broker;
    QPID_LOG(error, logPrefix << "Backup prepare failed: " << backup);
    prepared.insert(backup);
    failed = true;
    lock.notify();
}

bool PrimaryTxObserver::isPrepared(sys::Mutex::ScopedLock&) {
    return (prepared == backups || failed);
}

}} // namespace qpid::ha
