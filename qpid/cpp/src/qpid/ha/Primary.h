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
#include "BrokerInfo.h"
#include "qpid/sys/Mutex.h"
#include <boost/shared_ptr.hpp>
#include <boost/intrusive_ptr.hpp>
#include <map>
#include <string>

namespace qpid {

namespace broker {
class Queue;
class Connection;
class ConnectionObserver;
class ConfigurationObserver;
}

namespace sys {
class TimerTask;
}

namespace ha {
class HaBroker;
class ReplicatingSubscription;
class RemoteBackup;
class QueueGuard;

/**
 * State associated with a primary broker:
 * - sets queue guards and tracks readiness of initial backups till active.
 * - sets queue guards on new queues for each backup.
 *
 * THREAD SAFE: called concurrently in arbitrary connection threads.
 */
class Primary
{
  public:
    typedef boost::shared_ptr<broker::Queue> QueuePtr;
    typedef boost::shared_ptr<broker::Exchange> ExchangePtr;

    static Primary* get() { return instance; }

    Primary(HaBroker& hb, const BrokerInfo::Set& expectedBackups);
    ~Primary();

    void readyReplica(const ReplicatingSubscription&);
    void removeReplica(const std::string& q);

    // Called via ConfigurationObserver
    void queueCreate(const QueuePtr&);
    void queueDestroy(const QueuePtr&);
    void exchangeCreate(const ExchangePtr&);
    void exchangeDestroy(const ExchangePtr&);

    // Called via ConnectionObserver
    void opened(broker::Connection& connection);
    void closed(broker::Connection& connection);

    boost::shared_ptr<QueueGuard> getGuard(const QueuePtr& q, const BrokerInfo&);

    // Called in timer thread when the deadline for expected backups expires.
    void timeoutExpectedBackups();

  private:
    typedef std::map<types::Uuid, boost::shared_ptr<RemoteBackup> > BackupMap;
    typedef std::set<boost::shared_ptr<RemoteBackup> > BackupSet;

    void checkReady(sys::Mutex::ScopedLock&);
    void checkReady(BackupMap::iterator, sys::Mutex::ScopedLock&);

    sys::Mutex lock;
    HaBroker& haBroker;
    std::string logPrefix;
    bool active;
    /**
     * Set of expected backups that must be ready before we declare ourselves
     * active. These are backups that were known before the primary crashed. As
     * new primary we expect them to re-connect.
     */
    BackupSet expectedBackups;
    /**
     * Map of all the expected backups plus all connected backups.
     */
    BackupMap backups;
    boost::shared_ptr<broker::ConnectionObserver> connectionObserver;
    boost::shared_ptr<broker::ConfigurationObserver> configurationObserver;
    boost::intrusive_ptr<sys::TimerTask> timerTask;

    static Primary* instance;
};
}} // namespace qpid::ha

#endif  /*!QPID_HA_PRIMARY_H*/
