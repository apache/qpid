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
#include "qpid/broker/amqp_0_10/Connection.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/SessionHandlerObserver.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/log/Statement.h"
#include "qpid/types/Uuid.h"
#include "qpid/types/Variant.h"
#include "qpid/sys/Timer.h"
#include <boost/bind.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/intrusive_ptr.hpp>

namespace qpid {
namespace ha {

using sys::Mutex;
using boost::shared_ptr;
using boost::intrusive_ptr;
using namespace std;
using namespace framing;

namespace {

const std::string CLIENT_PROCESS_NAME("qpid.client_process");
const std::string CLIENT_PID("qpid.client_pid");
const std::string CLIENT_PPID("qpid.client_ppid");

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

class PrimaryErrorListener : public broker::SessionHandler::ErrorListener {
  public:
    PrimaryErrorListener(const LogPrefix& lp) : logPrefix(lp) {}

    void connectionException(framing::connection::CloseCode code, const std::string& msg) {
        QPID_LOG(debug, logPrefix << framing::createConnectionException(code, msg).what());
    }
    void channelException(framing::session::DetachCode code, const std::string& msg) {
        QPID_LOG(debug, logPrefix << framing::createChannelException(code, msg).what());
    }
    void executionException(framing::execution::ErrorCode code, const std::string& msg) {
        QPID_LOG(debug, logPrefix << framing::createSessionException(code, msg).what());
    }
    void incomingExecutionException(framing::execution::ErrorCode code, const std::string& msg) {
        QPID_LOG(debug, logPrefix << "Incoming " << framing::createSessionException(code, msg).what());
    }
    void detach() {}

  private:
    const LogPrefix& logPrefix;
};

class PrimarySessionHandlerObserver : public broker::SessionHandlerObserver {
  public:
    PrimarySessionHandlerObserver(const LogPrefix& logPrefix)
        : errorListener(new PrimaryErrorListener(logPrefix)) {}
    void newSessionHandler(broker::SessionHandler& sh) {
        BrokerInfo info;
        // Suppress error logging for backup connections
        // TODO aconway 2014-01-31: Be more selective, suppress only expected errors?
        if (ha::ConnectionObserver::getBrokerInfo(sh.getConnection(), info)) {
            sh.setErrorListener(errorListener);
        }
    }
  private:
    boost::shared_ptr<PrimaryErrorListener> errorListener;
};


} // namespace

Primary::Primary(HaBroker& hb, const BrokerInfo::Set& expect) :
    haBroker(hb), membership(hb.getMembership()),
    logPrefix(hb.logPrefix), active(false),
    replicationTest(hb.getSettings().replicateDefault.get()),
    sessionHandlerObserver(new PrimarySessionHandlerObserver(logPrefix)),
    queueLimits(logPrefix, hb.getBroker().getQueues(), replicationTest)
{
    // Note that at this point, we are still rejecting client connections.
    // So we are safe from client interference while we set up the primary.

    hb.getMembership().setStatus(RECOVERING);
    QPID_LOG(notice, logPrefix << "Promoted to primary");

    // Process all QueueReplicators, handles auto-delete queues.
    QueueReplicator::Vector qrs;
    QueueReplicator::copy(hb.getBroker().getExchanges(), qrs);
    std::for_each(qrs.begin(), qrs.end(), boost::bind(&QueueReplicator::promoted, _1));

    if (!expect.empty()) {
        // NOTE: RemoteBackups must be created before we set the BrokerObserver
        // or ConnectionObserver so that there is no client activity while
        // the QueueGuards are created.
        QPID_LOG(notice, logPrefix << "Recovering backups: " << expect);
        for (BrokerInfo::Set::const_iterator i = expect.begin(); i != expect.end(); ++i) {
            boost::shared_ptr<RemoteBackup> backup(new RemoteBackup(*i, 0, haBroker.logPrefix));
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
    haBroker.getBroker().getSessionHandlerObservers().add(sessionHandlerObserver);

    checkReady();               // Outside lock

    // Allow client connections
    connectionObserver.reset(new PrimaryConnectionObserver(*this));
    haBroker.getObserver()->setObserver(connectionObserver);
}

Primary::~Primary() {
    if (timerTask) timerTask->cancel();
    haBroker.getBroker().getBrokerObservers().remove(brokerObserver);
    haBroker.getBroker().getSessionHandlerObservers().remove(sessionHandlerObserver);
    haBroker.getObserver()->reset();
}

void Primary::checkReady() {
    bool activate = false;
    {
        Mutex::ScopedLock l(lock);
        if (!active && expectedBackups.empty())
            activate = active = true;
    }
    if (activate) {
        membership.setStatus(ACTIVE); // Outside of lock.
        QPID_LOG(notice, logPrefix << "All backups recovered.");
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
                QPID_LOG(info, logPrefix << "Recovering backup is ready: " << info);
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
                QPID_LOG(error, logPrefix << "Recovering backup timed out: " << info);
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
    q->addArgument(QPID_REPLICATE, printable(level).str());
    if (level) {
        QPID_LOG(debug, logPrefix << "Created queue " << q->getName()
                 << " replication: " << printable(level));
        // Give each queue a unique id. Used by backups to avoid confusion of
        // same-named queues.
        q->addArgument(QPID_HA_UUID, types::Variant(Uuid(true)));
        {
            Mutex::ScopedLock l(lock);
            queueLimits.addQueue(q); // Throws if limit exceeded
            for (BackupMap::iterator i = backups.begin(); i != backups.end(); ++i)
                i->second->queueCreate(q);
        }
        checkReady();           // Outside lock
    }
}

// NOTE: Called with queue registry lock held.
void Primary::queueDestroy(const QueuePtr& q) {
    if (replicationTest.useLevel(*q)) {
        QPID_LOG(debug, logPrefix << "Destroyed queue " << q->getName());
        {
            Mutex::ScopedLock l(lock);
            queueLimits.removeQueue(q);
            for (BackupMap::iterator i = backups.begin(); i != backups.end(); ++i)
                i->second->queueDestroy(q);
        }
        checkReady();               // Outside lock
    }
}

// NOTE: Called with exchange registry lock held.
void Primary::exchangeCreate(const ExchangePtr& ex) {
    ReplicateLevel level = replicationTest.useLevel(*ex);
    FieldTable args = ex->getArgs();
    args.setString(QPID_REPLICATE, printable(level).str()); // Set replication arg.
    if (level) {
        QPID_LOG(debug, logPrefix << "Created exchange " << ex->getName()
                 << " replication: " << printable(level));
         // Give each exchange a unique id to avoid confusion of same-named exchanges.
        args.set(QPID_HA_UUID, FieldTable::ValuePtr(new UuidValue(Uuid(true).data())));
    }
    ex->setArgs(args);
}

// NOTE: Called with exchange registry lock held.
void Primary::exchangeDestroy(const ExchangePtr& ex) {
    if (replicationTest.useLevel(*ex)) {
        QPID_LOG(debug, logPrefix << "Destroyed exchange " << ex->getName());
        // Do nothing
    }
 }

// New backup connected
shared_ptr<RemoteBackup> Primary::backupConnect(
    const BrokerInfo& info, broker::Connection& connection, Mutex::ScopedLock&)
{
    shared_ptr<RemoteBackup> backup(new RemoteBackup(info, &connection, haBroker.logPrefix));
    queueLimits.addBackup(backup);
    backups[info.getSystemId()] = backup;
    return backup;
}

// Remove a backup. Caller should not release the shared pointer returend till
// outside the lock.
void Primary::backupDisconnect(shared_ptr<RemoteBackup> backup, Mutex::ScopedLock&) {
    queueLimits.addBackup(backup);
    types::Uuid id = backup->getBrokerInfo().getSystemId();
    backup->cancel();
    expectedBackups.erase(backup);
    backups.erase(id);
    membership.remove(id);
}


void Primary::opened(broker::Connection& connection) {
    BrokerInfo info;
    shared_ptr<RemoteBackup> backup;
    if (ha::ConnectionObserver::getBrokerInfo(connection, info)) {
        Mutex::ScopedLock l(lock);
        BackupMap::iterator i = backups.find(info.getSystemId());
        if (info.getStatus() == JOINING) {
            info.setStatus(CATCHUP);
            membership.add(info);
        }
        if (i == backups.end()) {
            if (info.getStatus() == JOINING) {
                info.setStatus(CATCHUP);
                membership.add(info);
            }
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
            backup = i->second;
        }
    }
    else {
        const types::Variant::Map& properties = connection.getClientProperties();
        std::ostringstream pinfo;
        types::Variant::Map::const_iterator i = properties.find(CLIENT_PROCESS_NAME);
        // FIXME aconway 2014-08-13: Conditional on logging.
        if (i != properties.end()) {
            pinfo << "  " << i->second;
            i = properties.find(CLIENT_PID);
            if (i != properties.end())
                pinfo << "(" << i->second << ")";
        }
        QPID_LOG(info, logPrefix << "Accepted client connection " << connection.getMgmtId() << pinfo.str());
    }

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
    QPID_LOG(info, logPrefix << "Ignoring promotion, already primary");
    return 0;
}

void Primary::setCatchupQueues(const RemoteBackupPtr& backup, bool createGuards) {
    // Do queue iteration outside the lock to avoid deadlocks with QueueRegistry.
    haBroker.getBroker().getQueues().eachQueue(
        boost::bind(&RemoteBackup::catchupQueue, backup, _1, createGuards));
    backup->startCatchup();
}

}} // namespace qpid::ha
