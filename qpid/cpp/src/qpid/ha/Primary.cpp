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
#include "IdSetter.h"
#include "ReplicatingSubscription.h"
#include "RemoteBackup.h"
#include "ConnectionObserver.h"
#include "QueueReplicator.h"
#include "qpid/assert.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/BrokerObserver.h"
#include "qpid/broker/Connection.h"
#include "qpid/broker/Queue.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/framing/Uuid.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Timer.h"
#include <boost/bind.hpp>

namespace qpid {
namespace ha {

using sys::Mutex;
using boost::shared_ptr;
using namespace std;
using namespace framing;

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

class PrimaryBrokerObserver : public broker::BrokerObserver
{
  public:
    PrimaryBrokerObserver(Primary& p) : primary(p) {}
    void queueCreate(const Primary::QueuePtr& q) { primary.queueCreate(q); }
    void queueDestroy(const Primary::QueuePtr& q) { primary.queueDestroy(q); }
    void exchangeCreate(const Primary::ExchangePtr& q) { primary.exchangeCreate(q); }
    void exchangeDestroy(const Primary::ExchangePtr& q) { primary.exchangeDestroy(q); }
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

Primary::Primary(HaBroker& hb, const BrokerInfo::Set& expect) :
    haBroker(hb), membership(hb.getMembership()),
    logPrefix("Primary: "), active(false),
    replicationTest(hb.getSettings().replicateDefault.get())
{
    hb.getMembership().setStatus(RECOVERING);
    broker::QueueRegistry& queues = hb.getBroker().getQueues();
    queues.eachQueue(boost::bind(&Primary::initializeQueue, this, _1));
    if (expect.empty()) {
        QPID_LOG(notice, logPrefix << "Promoted to primary. No expected backups.");
    }
    else {
        // NOTE: RemoteBackups must be created before we set the BrokerObserver
        // or ConnectionObserver so that there is no client activity while
        // the QueueGuards are created.
        QPID_LOG(notice, logPrefix << "Promoted to primary. Expected backups: " << expect);
        for (BrokerInfo::Set::const_iterator i = expect.begin(); i != expect.end(); ++i) {
            boost::shared_ptr<RemoteBackup> backup(new RemoteBackup(*i, 0));
            backups[i->getSystemId()] = backup;
            if (!backup->isReady()) expectedBackups.insert(backup);
            setCatchupQueues(backup, true); // Create guards
        }
        // Set timeout for expected brokers to connect and become ready.
        sys::AbsTime deadline(sys::now(), hb.getSettings().backupTimeout);
        timerTask = new ExpectedBackupTimerTask(*this, deadline);
        hb.getBroker().getTimer().add(timerTask);
    }
    brokerObserver.reset(new PrimaryBrokerObserver(*this));
    haBroker.getBroker().getBrokerObservers().add(brokerObserver);
    checkReady();               // Outside lock

    // Allow client connections
    connectionObserver.reset(new PrimaryConnectionObserver(*this));
    haBroker.getObserver()->setObserver(connectionObserver, logPrefix);
}

Primary::~Primary() {
    if (timerTask) timerTask->cancel();
    haBroker.getBroker().getBrokerObservers().remove(brokerObserver);
    haBroker.getObserver()->reset();
}

void Primary::initializeQueue(boost::shared_ptr<broker::Queue> q) {
    if (replicationTest.useLevel(*q) == ALL) {
        boost::shared_ptr<QueueReplicator> qr = haBroker.findQueueReplicator(q->getName());
        ReplicationId firstId = qr ? qr->getMaxId()+1 : ReplicationId(1);
        q->getMessageInterceptors().add(
            boost::shared_ptr<IdSetter>(new IdSetter(q->getName(), firstId)));
    }
}

void Primary::checkReady() {
    bool activate = false;
    {
        Mutex::ScopedLock l(lock);
        if (!active && expectedBackups.empty())
            activate = active = true;
    }
    if (activate) {
        QPID_LOG(notice, logPrefix << "Finished waiting for backups, primary is active.");
        membership.setStatus(ACTIVE); // Outside of lock.
    }
}

void Primary::checkReady(boost::shared_ptr<RemoteBackup> backup) {
    bool ready = false;
    {
        Mutex::ScopedLock l(lock);
        if (backup->reportReady()) {
            BrokerInfo info = backup->getBrokerInfo();
            info.setStatus(READY);
            membership.add(info);
            if (expectedBackups.erase(backup)) {
                QPID_LOG(info, logPrefix << "Expected backup is ready: " << info);
                ready = true;
            }
        else
            QPID_LOG(info, logPrefix << "New backup is ready: " << info);
        }
    }
    if (ready) checkReady(); // Outside lock
}

void Primary::timeoutExpectedBackups() {
    try {
        sys::Mutex::ScopedLock l(lock);
        if (active) return;         // Already activated
        // Remove records for any expectedBackups that are not yet connected
        // Allow backups that are connected to continue becoming ready.
        for (BackupSet::iterator i = expectedBackups.begin(); i != expectedBackups.end();)
        {
            // This loop erases elements of backups in backupDisconnect, so
            // save and increment the iterator.
            BackupSet::iterator j = i++;
            boost::shared_ptr<RemoteBackup> backup = *j;
            if (!backup->getConnection()) {
                BrokerInfo info = backup->getBrokerInfo();
                QPID_LOG(error, logPrefix << "Expected backup timed out: " << info);
                backupDisconnect(backup, l); // Calls erase(j)
                // Keep broker in membership but downgrade status to CATCHUP.
                // The broker will get this status change when it eventually connects.
                info.setStatus(CATCHUP);
                membership.add(info);
            }
        }
    }
    catch(const std::exception& e) {
        QPID_LOG(error, logPrefix << "Error timing out backups: " << e.what());
        // No-where for this exception to go.
    }
    checkReady();
}

void Primary::readyReplica(const ReplicatingSubscription& rs) {
    shared_ptr<RemoteBackup> backup;
    {
        sys::Mutex::ScopedLock l(lock);
        BackupMap::iterator i = backups.find(rs.getBrokerInfo().getSystemId());
        if (i != backups.end()) {
            backup = i->second;
            backup->ready(rs.getQueue());
        }
    }
    if (backup) checkReady(backup);
}

// NOTE: Called with queue registry lock held.
void Primary::queueCreate(const QueuePtr& q) {
    // Set replication argument.
    ReplicateLevel level = replicationTest.useLevel(*q);
    QPID_LOG(debug, logPrefix << "Created queue " << q->getName()
             << " replication: " << printable(level));
    q->addArgument(QPID_REPLICATE, printable(level).str());
    if (level) {
        initializeQueue(q);
        // Give each queue a unique id. Used by backups to avoid confusion of
        // same-named queues.
        q->addArgument(QPID_HA_UUID, types::Variant(Uuid(true)));
        {
            Mutex::ScopedLock l(lock);
            for (BackupMap::iterator i = backups.begin(); i != backups.end(); ++i)
                i->second->queueCreate(q);
        }
        checkReady();           // Outside lock
    }
}

// NOTE: Called with queue registry lock held.
void Primary::queueDestroy(const QueuePtr& q) {
    QPID_LOG(debug, logPrefix << "Destroyed queue " << q->getName());
    {
        Mutex::ScopedLock l(lock);
        for (BackupMap::iterator i = backups.begin(); i != backups.end(); ++i)
            i->second->queueDestroy(q);
    }
    checkReady();               // Outside lock
}

// NOTE: Called with exchange registry lock held.
void Primary::exchangeCreate(const ExchangePtr& ex) {
    ReplicateLevel level = replicationTest.useLevel(*ex);
    QPID_LOG(debug, logPrefix << "Created exchange " << ex->getName()
             << " replication: " << printable(level));
    FieldTable args = ex->getArgs();
    args.setString(QPID_REPLICATE, printable(level).str()); // Set replication arg.
    if (level) {
        // Give each exchange a unique id to avoid confusion of same-named exchanges.
        args.set(QPID_HA_UUID, FieldTable::ValuePtr(new UuidValue(&Uuid(true)[0])));
    }
    ex->setArgs(args);
}

// NOTE: Called with exchange registry lock held.
void Primary::exchangeDestroy(const ExchangePtr& ex) {
    QPID_LOG(debug, logPrefix << "Destroyed exchange " << ex->getName());
    // Do nothing
 }

// New backup connected
shared_ptr<RemoteBackup> Primary::backupConnect(
    const BrokerInfo& info, broker::Connection& connection, Mutex::ScopedLock&)
{
    shared_ptr<RemoteBackup> backup(new RemoteBackup(info, &connection));
    backups[info.getSystemId()] = backup;
    return backup;
}

// Remove a backup. Caller should not release the shared pointer returend till
// outside the lock.
void Primary::backupDisconnect(shared_ptr<RemoteBackup> backup, Mutex::ScopedLock&) {
    types::Uuid id = backup->getBrokerInfo().getSystemId();
    backup->cancel();
    expectedBackups.erase(backup);
    backups.erase(id);
}


void Primary::opened(broker::Connection& connection) {
    BrokerInfo info;
    shared_ptr<RemoteBackup> backup;
    if (ha::ConnectionObserver::getBrokerInfo(connection, info)) {
        Mutex::ScopedLock l(lock);
        BackupMap::iterator i = backups.find(info.getSystemId());
        if (i == backups.end()) {
            QPID_LOG(info, logPrefix << "New backup connection: " << info);
            backup = backupConnect(info, connection, l);
        }
        else if (i->second->getConnection()) {
            // The backup is failing over before we recieved the closed() call
            // for its previous connection. Remove the old entry and create a new one.
            QPID_LOG(error, logPrefix << "Known backup reconnect before disconnection: " << info);
            backupDisconnect(i->second, l);
            backup = backupConnect(info, connection, l);
        } else {
            QPID_LOG(info, logPrefix << "Known backup reconnection: " << info);
            i->second->setConnection(&connection);
        }
        if (info.getStatus() == JOINING) {
            info.setStatus(CATCHUP);
            membership.add(info);
        }
    }
    else
        QPID_LOG(debug, logPrefix << "Accepted client connection " << connection.getMgmtId());

    // Outside lock
    if (backup) {
        setCatchupQueues(backup, false);
        checkReady(backup);
    }
    checkReady();
}

void Primary::closed(broker::Connection& connection) {
    BrokerInfo info;
    shared_ptr<RemoteBackup> backup;
    if (ha::ConnectionObserver::getBrokerInfo(connection, info)) {
        Mutex::ScopedLock l(lock);
        BackupMap::iterator i = backups.find(info.getSystemId());
        // NOTE: It is possible for a backup connection to be rejected while we
        // are a backup, but closed() is called after we have become primary.
        // Checking  isConnected() lets us ignore such spurious closes.
        if (i == backups.end()) {
            QPID_LOG(info, logPrefix << "Disconnect from unknown backup " << info);
        }
        else if (i->second->getConnection() != &connection) {
            QPID_LOG(info, logPrefix << "Late disconnect from backup " << info);
        }
        else {
            QPID_LOG(info, logPrefix << "Disconnect from "
                     << (i->second->getConnection() ? "" : "disconnected ")
                     << "backup " << info);
            // Assign to shared_ptr so it will be deleted after we release the lock.
            backup = i->second;
            backupDisconnect(backup, l);
        }
    }
    checkReady();
}

boost::shared_ptr<QueueGuard> Primary::getGuard(const QueuePtr& q, const BrokerInfo& info)
{
    Mutex::ScopedLock l(lock);
    BackupMap::iterator i = backups.find(info.getSystemId());
    return i == backups.end() ? boost::shared_ptr<QueueGuard>() : i->second->guard(q);
}

Role* Primary::promote() {
    QPID_LOG(info, "Ignoring promotion, already primary: " << haBroker.getBrokerInfo());
    return 0;
}

void Primary::setCatchupQueues(const RemoteBackupPtr& backup, bool createGuards) {
    // Do queue iteration outside the lock to avoid deadlocks with QueueRegistry.
    haBroker.getBroker().getQueues().eachQueue(
        boost::bind(&RemoteBackup::catchupQueue, backup, _1, createGuards));
    backup->startCatchup();
}

}} // namespace qpid::ha
