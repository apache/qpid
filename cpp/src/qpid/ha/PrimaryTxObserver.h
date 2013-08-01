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

#include "qpid/broker/TransactionObserver.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/Uuid.h"
#include "qpid/sys/unordered_map.h"
#include <boost/functional/hash.hpp>
namespace qpid {

namespace broker {
class Broker;
}

namespace ha {
class HaBroker;

/**
 * Observe events in the lifecycle of a transaction.
 *
 * The observer is called by TxBuffer for each transactional event.
 * It puts the events on a special tx-queue.
 * A TxReplicator on the backup replicates the tx-queue and creates
 * a TxBuffer on the backup equivalent to the one on the primary.
 *
 * THREAD UNSAFE: called sequentially in the context of a transaction.
 */
class PrimaryTxObserver : public broker::TransactionObserver {
  public:
    PrimaryTxObserver(HaBroker&);

    void enqueue(const QueuePtr&, const broker::Message&);
    void dequeue(const QueuePtr& queue, QueuePosition, ReplicationId);
    bool prepare();
    void commit();
    void rollback();

  private:
    typedef qpid::sys::unordered_map<
      QueuePtr, ReplicationIdSet, boost::hash<QueuePtr> > QueueIdsMap;

    void deduplicate();

    std::string logPrefix;
    HaBroker& haBroker;
    broker::Broker& broker;
    framing::Uuid id;
    QueuePtr txQueue;
    QueueIdsMap enqueues;
};

}} // namespace qpid::ha

#endif  /*!QPID_HA_PRIMARYTXOBSERVER_H*/
