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


#include "TxReplicator.h"
#include "Role.h"
#include "Backup.h"
#include "BrokerReplicator.h"
#include "Event.h"
#include "HaBroker.h"
#include "ReplicatingSubscription.h"
#include "types.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Link.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/SessionHandler.h"
#include "qpid/broker/TxBuffer.h"
#include "qpid/broker/TxAccept.h"
#include "qpid/broker/amqp_0_10/Connection.h"
#include "qpid/broker/DeliverableMessage.h"
#include "qpid/framing/BufferTypes.h"
#include "qpid/log/Statement.h"
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/framing/MessageTransferBody.h"
#include <boost/shared_ptr.hpp>
#include <boost/bind.hpp>
#include <sstream>

namespace qpid {
namespace ha {

using namespace std;
using namespace qpid::broker;
using namespace qpid::framing;
using qpid::broker::amqp_0_10::MessageTransfer;
using qpid::types::Uuid;

namespace {
const string PREFIX(TRANSACTION_REPLICATOR_PREFIX);
} // namespace

bool TxReplicator::isTxQueue(const string& q) {
    return startsWith(q, PREFIX);
}

Uuid TxReplicator::getTxId(const string& q) {
    if (TxReplicator::isTxQueue(q)) {
        std::istringstream is(q);
        is.seekg(PREFIX.size());
        Uuid id;
        is >> id;
        if (!is.fail()) return id;
    }
    throw Exception(QPID_MSG("Invalid tx queue: " << q));
}

string TxReplicator::getType() const { return ReplicatingSubscription::QPID_TX_REPLICATOR; }

boost::shared_ptr<TxReplicator> TxReplicator::create(
    HaBroker& hb,
    const boost::shared_ptr<broker::Queue>& txQueue,
    const boost::shared_ptr<broker::Link>& link)
{
    boost::shared_ptr<TxReplicator> tr(new TxReplicator(hb, txQueue, link));
    tr->initialize();
    return tr;
}

TxReplicator::TxReplicator(
    HaBroker& hb,
    const boost::shared_ptr<broker::Queue>& txQueue,
    const boost::shared_ptr<broker::Link>& link) :
    QueueReplicator(hb, txQueue, link),
    logPrefix(hb.logPrefix),
    store(hb.getBroker().hasStore() ? &hb.getBroker().getStore() : 0),
    channel(link->nextChannel()),
    empty(true), ended(false),
    dequeueState(hb.getBroker().getQueues())
{
    logPrefix = "Backup of TX "+shortStr(getTxId(txQueue->getName()))+": ";
    QPID_LOG(debug, logPrefix << "Started");
    if (!store) throw Exception(QPID_MSG(logPrefix << "No message store loaded."));

    // Dispatch transaction events.
    dispatch[TxEnqueueEvent::KEY] =
        boost::bind(&TxReplicator::enqueue, this, _1, _2);
    dispatch[TxDequeueEvent::KEY] =
        boost::bind(&TxReplicator::dequeue, this, _1, _2);
    dispatch[TxPrepareEvent::KEY] =
        boost::bind(&TxReplicator::prepare, this, _1, _2);
    dispatch[TxCommitEvent::KEY] =
        boost::bind(&TxReplicator::commit, this, _1, _2);
    dispatch[TxRollbackEvent::KEY] =
        boost::bind(&TxReplicator::rollback, this, _1, _2);
    dispatch[TxBackupsEvent::KEY] =
        boost::bind(&TxReplicator::backups, this, _1, _2);
}

TxReplicator::~TxReplicator() {
    link->returnChannel(channel);
}

// Send a message to the primary tx.
void TxReplicator::sendMessage(const broker::Message& msg, sys::Mutex::ScopedLock&) {
    assert(sessionHandler);
    const MessageTransfer& transfer(MessageTransfer::get(msg));
    for (FrameSet::const_iterator i = transfer.getFrames().begin();
         i != transfer.getFrames().end();
         ++i)
    {
        sessionHandler->out.handle(const_cast<AMQFrame&>(*i));
    }
}

void TxReplicator::deliver(const broker::Message& m_) {
    boost::intrusive_ptr<broker::TxBuffer> txbuf;
    broker::Message m(m_);
    {
        sys::Mutex::ScopedLock l(lock);
        if (!txBuffer) return;
        txbuf = txBuffer;
        m.setReplicationId(enq.id); // Use enqueued replicated id.
    }
    // Deliver message to the target queue, not the tx-queue.
    boost::shared_ptr<broker::Queue> queue = haBroker.getBroker().getQueues().get(enq.queue);
    QPID_LOG(trace, logPrefix << "Deliver " << logMessageId(*queue, m.getReplicationId()));
    DeliverableMessage dm(m, txbuf.get());
    dm.deliverTo(queue);
}

void TxReplicator::enqueue(const string& data, sys::Mutex::ScopedLock&) {
    sys::Mutex::ScopedLock l(lock);
    if (!txBuffer) return;
    TxEnqueueEvent e;
    decodeStr(data, e);
    QPID_LOG(trace, logPrefix << "Enqueue: " << e);
    enq = e;
    empty = false;
}

void TxReplicator::dequeue(const string& data, sys::Mutex::ScopedLock&) {
    sys::Mutex::ScopedLock l(lock);
    if (!txBuffer) return;
    TxDequeueEvent e;
    decodeStr(data, e);
    QPID_LOG(trace, logPrefix << "Dequeue: " << e);
    // NOTE: Backup does not see transactional dequeues until the transaction is
    // prepared, then they are all receieved before the prepare event.
    // We collect the events here so we can do a single scan of the queue in prepare.
    dequeueState.add(e);
    empty = false;
}

void TxReplicator::DequeueState::add(const TxDequeueEvent& event) {
    events[event.queue] += event.id;
}

// Use this function as a seek() predicate to find the dequeued messages.
bool TxReplicator::DequeueState::addRecord(
    const broker::Message& m, const boost::shared_ptr<Queue>& queue,
    const ReplicationIdSet& rids)
{
    if (rids.contains(m.getReplicationId())) {
        DeliveryRecord dr(cursor, m.getSequence(), m.getReplicationId(), queue,
                          string() /*tag*/,
                          boost::shared_ptr<Consumer>(),
                          true /*acquired*/,
                          false /*accepted*/,
                          false /*credit.isWindowMode()*/,
                          0 /*credit*/);
        // Generate record ids, unique within this transaction.
        dr.setId(nextId++);
        records.push_back(dr);
        recordIds += dr.getId();
    }
    return false;
}

void TxReplicator::DequeueState::addRecords(const EventMap::value_type& entry) {
    // Process all the dequeues for a single queue, in one pass of seek()
    boost::shared_ptr<broker::Queue> q = queues.get(entry.first);
    q->seek(cursor, boost::bind(&TxReplicator::DequeueState::addRecord,
                                this, _1, q, entry.second));
}

boost::shared_ptr<TxAccept> TxReplicator::DequeueState::makeAccept() {
    for_each(events.begin(), events.end(),
             boost::bind(&TxReplicator::DequeueState::addRecords, this, _1));
    return boost::shared_ptr<TxAccept>(
        new TxAccept(boost::cref(recordIds), boost::ref(records)));
}

void TxReplicator::prepare(const string&, sys::Mutex::ScopedLock& l) {
    if (!txBuffer) return;
    txBuffer->enlist(dequeueState.makeAccept());
    context = store->begin();
    if (txBuffer->prepare(context.get())) {
        QPID_LOG(debug, logPrefix << "Local prepare OK");
        sendMessage(TxPrepareOkEvent(haBroker.getSystemId()).message(queue->getName()), l);
    } else {
        QPID_LOG(error, logPrefix << "Local prepare failed");
        sendMessage(TxPrepareFailEvent(haBroker.getSystemId()).message(queue->getName()), l);
    }
}

void TxReplicator::commit(const string&, sys::Mutex::ScopedLock& l) {
    if (!txBuffer) return;
    QPID_LOG(debug, logPrefix << "Commit");
    if (context.get()) store->commit(*context);
    txBuffer->commit();
    end(l);
}

void TxReplicator::rollback(const string&, sys::Mutex::ScopedLock& l) {
    if (!txBuffer) return;
    // Don't bleat about rolling back empty transactions, this happens all the time
    // when a session closes and rolls back its outstanding transaction.
    if (!empty) QPID_LOG(debug, logPrefix << "Rollback");
    if (context.get()) store->abort(*context);
    txBuffer->rollback();
    end(l);
}

void TxReplicator::backups(const string& data, sys::Mutex::ScopedLock& l) {
    TxBackupsEvent e;
    decodeStr(data, e);
    if (!e.backups.count(haBroker.getMembership().getSelf().getSystemId())) {
        QPID_LOG(info, logPrefix << "Not participating");
        end(l);
    } else {
        QPID_LOG(debug, logPrefix << "Backups: " << e.backups);
        txBuffer = new broker::TxBuffer;
    }
}

void TxReplicator::end(sys::Mutex::ScopedLock&) {
    ended = true;
    txBuffer = 0;
    // QueueReplicator::destroy cancels subscription to the primary tx-queue
    // which allows the primary to clean up resources.
    sys::Mutex::ScopedUnlock u(lock);
    QueueReplicator::destroy();
}

// Called when the tx queue is deleted.
void TxReplicator::destroy(sys::Mutex::ScopedLock& l) {
    if (!ended) {
        if (!empty) QPID_LOG(error, logPrefix << "Destroyed prematurely, rollback");
        rollback(string(), l);
    }
    QueueReplicator::destroy(l);
}

}} // namespace qpid::ha
