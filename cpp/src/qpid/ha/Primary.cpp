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
#include "HaBroker.h"
#include "Primary.h"
#include "ReplicationTest.h"
#include "ReplicatingSubscription.h"
#include "RemoteBackup.h"
#include "ConnectionObserver.h"
#include "qpid/assert.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/ConfigurationObserver.h"
#include "qpid/broker/Connection.h"
#include "qpid/broker/Queue.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Timer.h"
#include <boost/bind.hpp>

namespace qpid {
namespace ha {

using sys::Mutex;

namespace {

class PrimaryConnectionObserver : public broker::ConnectionObserver
{
  public:
    PrimaryConnectionObserver(Primary& p) : primary(p) {}
    void opened(broker::Connection& c) { primary.opened(c); }
    void closed(broker::Connection& c) { primary.closed(c); }
  private:
    Primary& primary;
};

class PrimaryConfigurationObserver : public broker::ConfigurationObserver
{
  public:
    PrimaryConfigurationObserver(Primary& p) : primary(p) {}
    void queueCreate(const Primary::QueuePtr& q) { primary.queueCreate(q); }
    void queueDestroy(const Primary::QueuePtr& q) { primary.queueDestroy(q); }
  private:
    Primary& primary;
};

class ExpectedBackupTimerTask : public sys::TimerTask {
  public:
    ExpectedBackupTimerTask(Primary& p, sys::AbsTime deadline)
        : TimerTask(deadline, "ExpectedBackupTimerTask"), primary(p) {}
    void fire() { primary.timeoutExpectedBackups(); }
  private:
    Primary& primary;
};

} // namespace

Primary* Primary::instance = 0;

Primary::Primary(HaBroker& hb, const BrokerInfo::Set& expect) :
    haBroker(hb), logPrefix("Primary: "), active(false)
{
    assert(instance == 0);
    instance = this;            // Let queue replicators find us.
    if (expect.empty()) {
        QPID_LOG(debug, logPrefix << "Promoted, no expected backups");
    }
    else {
        // NOTE: RemoteBackups must be created before we set the ConfigurationObserver
        // or ConnectionObserver so that there is no client activity while
        // the QueueGuards are created.
        QPID_LOG(debug, logPrefix << "Promoted, expected backups: " << expect);
        for (BrokerInfo::Set::const_iterator i = expect.begin(); i != expect.end(); ++i) {
            boost::shared_ptr<RemoteBackup> backup(
                new RemoteBackup(*i, haBroker.getReplicationTest(), false));
            backups[i->getSystemId()] = backup;
            if (!backup->isReady()) expectedBackups.insert(backup);
            backup->setInitialQueues(hb.getBroker().getQueues(), true); // Create guards
        }
        // Set timeout for expected brokers to connect and become ready.
        sys::Duration timeout(int64_t(hb.getSettings().backupTimeout*sys::TIME_SEC));
        sys::AbsTime deadline(sys::now(), timeout);
        timerTask = new ExpectedBackupTimerTask(*this, deadline);
        hb.getBroker().getTimer().add(timerTask);
    }

    configurationObserver.reset(new PrimaryConfigurationObserver(*this));
    haBroker.getBroker().getConfigurationObservers().add(configurationObserver);

    Mutex::ScopedLock l(lock);  // We are now active as a configurationObserver
    checkReady(l);
    // Allow client connections
    connectionObserver.reset(new PrimaryConnectionObserver(*this));
    haBroker.getObserver()->setObserver(connectionObserver);
}

Primary::~Primary() {
    if (timerTask) timerTask->cancel();
    haBroker.getBroker().getConfigurationObservers().remove(configurationObserver);
}

void Primary::checkReady(Mutex::ScopedLock&) {
    if (!active && expectedBackups.empty()) {
        active = true;
        Mutex::ScopedUnlock u(lock); // Don't hold lock across callback
        QPID_LOG(notice, logPrefix << "Finished waiting for backups, primary is active.");
        haBroker.activate();
    }
}

void Primary::checkReady(BackupMap::iterator i, Mutex::ScopedLock& l)  {
    if (i != backups.end() && i->second->isReady()) {
        BrokerInfo info = i->second->getBrokerInfo();
        QPID_LOG(info, "Expected backup is ready: " << info);
        info.setStatus(READY);
        haBroker.addBroker(info);
        expectedBackups.erase(i->second);
        checkReady(l);
    }
}

void Primary::timeoutExpectedBackups() {
    sys::Mutex::ScopedLock l(lock);
    if (active) return;         // Already activated
    // Remove records for any expectedBackups that are not yet connected
    // Allow backups that are connected to continue becoming ready.
    for (BackupSet::iterator i = expectedBackups.begin(); i != expectedBackups.end();)
    {
        boost::shared_ptr<RemoteBackup> rb = *i;
        if (!rb->isConnected()) {
            BrokerInfo info = rb->getBrokerInfo();
            QPID_LOG(error, "Expected backup timed out: " << info);
            expectedBackups.erase(i++);
            backups.erase(info.getSystemId());
            rb->cancel();
            // Downgrade the broker to CATCHUP
            info.setStatus(CATCHUP);
            haBroker.addBroker(info);
        }
        else ++i;
    }
    checkReady(l);
}

void Primary::readyReplica(const ReplicatingSubscription& rs) {
    sys::Mutex::ScopedLock l(lock);
    BackupMap::iterator i = backups.find(rs.getBrokerInfo().getSystemId());
    if (i != backups.end()) {
        i->second->ready(rs.getQueue());
        checkReady(i, l);
    }
}

void Primary::queueCreate(const QueuePtr& q) {
    // Throw if there is an invalid replication level in the queue settings.
    haBroker.getReplicationTest().replicateLevel(q->getSettings());
    Mutex::ScopedLock l(lock);
    for (BackupMap::iterator i = backups.begin(); i != backups.end(); ++i) {
        i->second->queueCreate(q);
        checkReady(i, l);
    }
}

void Primary::queueDestroy(const QueuePtr& q) {
    Mutex::ScopedLock l(lock);
    for (BackupMap::iterator i = backups.begin(); i != backups.end(); ++i)
        i->second->queueDestroy(q);
    checkReady(l);
}

void Primary::opened(broker::Connection& connection) {
    BrokerInfo info;
    if (ha::ConnectionObserver::getBrokerInfo(connection, info)) {
        Mutex::ScopedLock l(lock);
        BackupMap::iterator i = backups.find(info.getSystemId());
        if (i == backups.end()) {
            boost::shared_ptr<RemoteBackup> backup(
                new RemoteBackup(info, haBroker.getReplicationTest(), true));
            {
                // Avoid deadlock with queue registry lock.
                Mutex::ScopedUnlock u(lock);
                backup->setInitialQueues(haBroker.getBroker().getQueues(), false);
            }
            backups[info.getSystemId()] = backup;
            QPID_LOG(debug, logPrefix << "New backup connected: " << info);
        }
        else {
            QPID_LOG(debug, logPrefix << "Known backup connected: " << info);
            i->second->setConnected(true);
            checkReady(i, l);
        }
        if (info.getStatus() == JOINING) info.setStatus(CATCHUP);
        haBroker.addBroker(info);
    }
    else
        QPID_LOG(debug, logPrefix << "Accepted client connection "
                 << connection.getMgmtId());
}

void Primary::closed(broker::Connection& connection) {
    Mutex::ScopedLock l(lock);
    BrokerInfo info;
    if (ha::ConnectionObserver::getBrokerInfo(connection, info)) {
        QPID_LOG(debug, logPrefix << "Backup disconnected: " << info);
        haBroker.removeBroker(info.getSystemId());
        BackupMap::iterator i = backups.find(info.getSystemId());
        if (i != backups.end()) i->second->setConnected(false);
    }
    // NOTE: we do not remove from the backups map here, the backups map holds
    // all the backups we know about whether connected or not.
    //
    // It is possible for a backup connection to be rejected while we are a backup,
    // but the closed is seen after we have become primary. Removing the entry
    // from backups in this case would be incorrect.
}


boost::shared_ptr<QueueGuard> Primary::getGuard(const QueuePtr& q, const BrokerInfo& info)
{
    Mutex::ScopedLock l(lock);
    BackupMap::iterator i = backups.find(info.getSystemId());
    return i == backups.end() ? boost::shared_ptr<QueueGuard>() : i->second->guard(q);
}

}} // namespace qpid::ha
