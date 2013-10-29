#ifndef QPID_HA_PRIMARYTXOBSERVER_H
#define QPID_HA_PRIMARYTXOBSERVER_H

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

#include "types.h"
#include "ReplicationTest.h"
#include "qpid/broker/SessionState.h"
#include "qpid/broker/TransactionObserver.h"
#include "qpid/log/Statement.h"
#include "qpid/types/Uuid.h"
#include "qpid/sys/unordered_map.h"
#include "qpid/sys/Monitor.h"
#include <boost/enable_shared_from_this.hpp>
#include <boost/intrusive_ptr.hpp>

namespace qpid {

namespace broker {
class Broker;
class Message;
class Consumer;
class AsyncCompletion;
}

namespace ha {
class HaBroker;
class ReplicatingSubscription;
class Primary;

/**
 * Observe events in the lifecycle of a transaction.
 *
 * The observer is called by TxBuffer for each transactional event.
 * It puts the events on a special tx-queue.
 * A TxReplicator on the backup replicates the tx-queue and creates
 * a TxBuffer on the backup equivalent to the one on the primary.
 *
 * Also observes the tx-queue for prepare-complete messages and
 * subscription cancellations.
 *
 * THREAD SAFE: called in user connection thread for TX events,
 * and in backup connection threads for prepare-completed events
 * and unsubscriptions.
 */
class PrimaryTxObserver : public broker::TransactionObserver,
                          public boost::enable_shared_from_this<PrimaryTxObserver>
{
  public:
    PrimaryTxObserver(Primary&, HaBroker&, const boost::intrusive_ptr<broker::TxBuffer>&);
    ~PrimaryTxObserver();

    /** Call immediately after constructor, uses shared_from_this. */
    void initialize();

    void enqueue(const QueuePtr&, const broker::Message&);
    void dequeue(const QueuePtr& queue, QueuePosition, ReplicationId);
    bool prepare();
    void commit();
    void rollback();

    types::Uuid getId() const { return id; }
    QueuePtr getTxQueue() const { return txQueue; }
    std::string getExchangeName() const { return exchangeName; }

    // Notify that a backup subscription has been cancelled.
    void cancel(const ReplicatingSubscription&);

  private:
    class Exchange;
    typedef qpid::sys::unordered_map<
      QueuePtr, ReplicationIdSet, Hasher<QueuePtr> > QueueIdsMap;

    void membership(const BrokerInfo::Map&);
    void end(sys::Mutex::ScopedLock&);
    void txPrepareOkEvent(const std::string& data);
    void txPrepareFailEvent(const std::string& data);


    sys::Monitor lock;
    std::string logPrefix;
    Primary& primary;
    HaBroker& haBroker;
    broker::Broker& broker;
    ReplicationTest replicationTest;
    // NOTE: There is an intrusive_ptr cycle between PrimaryTxObserver
    // and TxBuffer. The cycle is broken in PrimaryTxObserver::end()
    boost::intrusive_ptr<broker::TxBuffer> txBuffer;

    types::Uuid id;
    std::string exchangeName;
    QueuePtr txQueue;
    QueueIdsMap enqueues;
    bool complete;
    UuidSet members;            // All members of transaction.
    UuidSet unprepared;         // Members that have not yet responded to prepare.
    UuidSet unfinished;         // Members that have not yet disconnected.
};

}} // namespace qpid::ha

#endif  /*!QPID_HA_PRIMARYTXOBSERVER_H*/
