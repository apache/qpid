#ifndef QPID_HA_PRIMARY_H
#define QPID_HA_PRIMARY_H

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
#include "hash.h"
#include "BrokerInfo.h"
#include "LogPrefix.h"
#include "PrimaryQueueLimits.h"
#include "ReplicationTest.h"
#include "Role.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/unordered_map.h"
#include <boost/shared_ptr.hpp>
#include <boost/weak_ptr.hpp>
#include <boost/intrusive_ptr.hpp>
#include <string>

namespace qpid {

namespace broker {
class Queue;
class Connection;
class ConnectionObserver;
class BrokerObserver;
class SessionHandlerObserver;
class TxBuffer;
class DtxBuffer;
}

namespace sys {
class TimerTask;
}

namespace ha {
class HaBroker;
class ReplicatingSubscription;
class RemoteBackup;
class QueueGuard;
class Membership;
class PrimaryTxObserver;

/**
 * State associated with a primary broker:
 * - sets queue guards and tracks readiness of initial backups till active.
 * - sets queue guards on new queues for each backup.
 *
 * THREAD SAFE: called concurrently in arbitrary connection threads.
 *
 * Locking rules: BrokerObserver create/destroy functions are called with
 * the QueueRegistry lock held. Functions holding Primary::lock *must not*
 * directly or indirectly call on the queue registry.
 */
class Primary : public Role
{
  public:
    typedef boost::shared_ptr<broker::Queue> QueuePtr;
    typedef boost::shared_ptr<broker::Exchange> ExchangePtr;
    typedef boost::shared_ptr<RemoteBackup> RemoteBackupPtr;

    Primary(HaBroker& hb, const BrokerInfo::Set& expectedBackups);
    ~Primary();

    // Role implementation
    Role* promote();
    void setBrokerUrl(const Url&) {}

    void readyReplica(const ReplicatingSubscription&);
    void addReplica(ReplicatingSubscription&);
    void removeReplica(const ReplicatingSubscription&);

    /** Skip replication of ids to queue on backup. */
    void skipEnqueues(const types::Uuid& backup,
                      const boost::shared_ptr<broker::Queue>& queue,
                      const ReplicationIdSet& ids);

    /** Skip replication of dequeue of ids to queue on backup. */
    void skipDequeues(const types::Uuid& backup,
                      const boost::shared_ptr<broker::Queue>& queue,
                      const ReplicationIdSet& ids);

    // Called via BrokerObserver
    void queueCreate(const QueuePtr&);
    void queueDestroy(const QueuePtr&);
    void exchangeCreate(const ExchangePtr&);
    void exchangeDestroy(const ExchangePtr&);
    void startTx(const boost::intrusive_ptr<broker::TxBuffer>&);
    void startDtx(const boost::intrusive_ptr<broker::DtxBuffer>&);

    // Called via ConnectionObserver
    void opened(broker::Connection& connection);
    void closed(broker::Connection& connection);

    boost::shared_ptr<QueueGuard> getGuard(const QueuePtr& q, const BrokerInfo&);

    // Called in timer thread when the deadline for expected backups expires.
    void timeoutExpectedBackups();

  private:
    typedef sys::unordered_map<
      types::Uuid, RemoteBackupPtr, Hasher<types::Uuid> > BackupMap;

    typedef std::set<RemoteBackupPtr > BackupSet;

    typedef std::pair<types::Uuid, boost::shared_ptr<broker::Queue> > UuidQueue;
    typedef sys::unordered_map<UuidQueue, ReplicatingSubscription*,
                               Hasher<UuidQueue> > ReplicaMap;

    // Map of PrimaryTxObservers by tx-queue name
    typedef sys::unordered_map<std::string, boost::weak_ptr<PrimaryTxObserver> > TxMap;

    RemoteBackupPtr backupConnect(const BrokerInfo&, broker::Connection&, sys::Mutex::ScopedLock&);
    void backupDisconnect(RemoteBackupPtr, sys::Mutex::ScopedLock&);

    void checkReady();
    void checkReady(RemoteBackupPtr);
    void setCatchupQueues(const RemoteBackupPtr&, bool createGuards);
    void deduplicate();
    boost::shared_ptr<PrimaryTxObserver> makeTxObserver(
        const boost::intrusive_ptr<broker::TxBuffer>&);

    mutable sys::Mutex lock;
    HaBroker& haBroker;
    Membership& membership;
    const LogPrefix& logPrefix;
    bool active;
    ReplicationTest replicationTest;

    /**
     * Set of expected backups that must be ready before we declare ourselves
     * active. These are backups that were known and ready before the primary
     * crashed. As new primary we expect them to re-connect.
     */
    BackupSet expectedBackups;
    /**
     * Map of all the expected backups plus all connected backups.
     */
    BackupMap backups;
    boost::shared_ptr<broker::ConnectionObserver> connectionObserver;
    boost::shared_ptr<broker::BrokerObserver> brokerObserver;
    boost::shared_ptr<broker::SessionHandlerObserver> sessionHandlerObserver;
    boost::intrusive_ptr<sys::TimerTask> timerTask;
    ReplicaMap replicas;
    TxMap txMap;
    PrimaryQueueLimits queueLimits;
};
}} // namespace qpid::ha

#endif  /*!QPID_HA_PRIMARY_H*/
