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
#include "ReplicatingSubscription.h"
#include "RemoteBackup.h"
#include "ConnectionObserver.h"
#include "qpid/assert.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/ConfigurationObserver.h"
#include "qpid/broker/Queue.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/log/Statement.h"
#include <boost/bind.hpp>

namespace qpid {
namespace ha {

using sys::Mutex;

namespace {
// No-op connection observer, allows all connections.
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

} // namespace

Primary* Primary::instance = 0;

Primary::Primary(HaBroker& hb, const BrokerInfo::Set& expect) :
    haBroker(hb), logPrefix("Primary: "), active(false)
{
    assert(instance == 0);
    instance = this;            // Let queue replicators find us.
    if (expect.empty()) {
        QPID_LOG(debug, logPrefix << "Expected backups: none");
    }
    else {
        QPID_LOG(debug, logPrefix << "Expected backups: " << expect);
        for (BrokerInfo::Set::const_iterator i = expect.begin(); i != expect.end(); ++i) {
            bool guard = true;  // Create queue guards immediately for expected backups.
            boost::shared_ptr<RemoteBackup> backup(
                new RemoteBackup(*i, haBroker.getBroker(), haBroker.getReplicationTest(), guard));
            backups[i->getSystemId()] = backup;
            if (!backup->isReady()) initialBackups.insert(backup);
        }
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
    haBroker.getObserver()->setObserver(boost::shared_ptr<broker::ConnectionObserver>());
    haBroker.getBroker().getConfigurationObservers().remove(configurationObserver);
}

void Primary::checkReady(Mutex::ScopedLock&) {
    if (!active && initialBackups.empty()) {
        active = true;
        QPID_LOG(notice, logPrefix << "All initial backups are ready.");
        Mutex::ScopedUnlock u(lock); // Don't hold lock across callback
        haBroker.activate();
    }
}

void Primary::checkReady(BackupMap::iterator i, Mutex::ScopedLock& l)  {
    if (i != backups.end() && i->second->isReady()) {
        BrokerInfo info = i->second->getBrokerInfo();
        info.setStatus(READY);
        haBroker.getMembership().add(info);
        initialBackups.erase(i->second);
        checkReady(l);
    }
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
    Mutex::ScopedLock l(lock);
    BrokerInfo info;
    if (ha::ConnectionObserver::getBrokerInfo(connection, info)) {
        BackupMap::iterator i = backups.find(info.getSystemId());
        if (i == backups.end()) {
            QPID_LOG(debug, logPrefix << "New backup connected: " << info);
            bool guard = false; // Lazy-create queue guards, pre-creating them here could cause deadlock.
            backups[info.getSystemId()].reset(
                new RemoteBackup(info, haBroker.getBroker(), haBroker.getReplicationTest(), guard));
        }
        else {
            QPID_LOG(debug, logPrefix << "Known backup connected: " << info);
        }
        haBroker.getMembership().add(info);
    }
}

void Primary::closed(broker::Connection& connection) {
    Mutex::ScopedLock l(lock);
    BrokerInfo info;
    if (ha::ConnectionObserver::getBrokerInfo(connection, info)) {
        haBroker.getMembership().remove(info.getSystemId());
        QPID_LOG(debug, logPrefix << "Backup disconnected: " << info);
    }
    // NOTE: we do not modify backups here, we only add to the known backups set
    // we never remove from it.

    // It is possible for a backup connection to be rejected while we are a backup,
    // but the closed is seen when we have become primary. Removing the entry
    // from backups in this case would be incorrect.
}


boost::shared_ptr<QueueGuard> Primary::getGuard(const QueuePtr& q, const BrokerInfo& info)
{
    BackupMap::iterator i = backups.find(info.getSystemId());
    return i == backups.end() ? boost::shared_ptr<QueueGuard>() : i->second->guard(q);
}

}} // namespace qpid::ha
