#ifndef QPID_HA_BROKER_H
#define QPID_HA_BROKER_H

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

#include "BrokerInfo.h"
#include "Membership.h"
#include "types.h"
#include "ReplicationTest.h"
#include "Settings.h"
#include "qpid/Url.h"
#include "qpid/sys/Mutex.h"
#include "qmf/org/apache/qpid/ha/HaBroker.h"
#include "qpid/management/Manageable.h"
#include "qpid/types/Variant.h"
#include <set>
#include <boost/shared_ptr.hpp>

namespace qpid {

namespace types {
class Variant;
}

namespace broker {
class Broker;
class Queue;
}
namespace framing {
class FieldTable;
}

namespace ha {
class Backup;
class ConnectionObserver;
class Primary;
class StatusCheck;

/**
 * HA state and actions associated with a HA broker. Holds all the management info.
 *
 * THREAD SAFE: may be called in arbitrary broker IO or timer threads.
 */
class HaBroker : public management::Manageable
{
  public:
    /** HaBroker is constructed during earlyInitialize */
    HaBroker(broker::Broker&, const Settings&);
    ~HaBroker();

    /** Called during plugin initialization */
    void initialize();

    // Implement Manageable.
    qpid::management::ManagementObject::shared_ptr GetManagementObject() const { return mgmtObject; }
    management::Manageable::status_t ManagementMethod (
        uint32_t methodId, management::Args& args, std::string& text);

    broker::Broker& getBroker() { return broker; }
    const Settings& getSettings() const { return settings; }

    /** Shut down the broker. Caller should log a critical error message. */
    void shutdown();

    BrokerStatus getStatus() const;
    void setStatus(BrokerStatus);
    void activate();

    Backup* getBackup() { return backup.get(); }
    ReplicationTest getReplicationTest() const { return replicationTest; }

    boost::shared_ptr<ConnectionObserver> getObserver() { return observer; }

    const BrokerInfo& getBrokerInfo() const { return brokerInfo; }

    void setMembership(const types::Variant::List&); // Set membership from list.
    void addBroker(const BrokerInfo& b);       // Add a broker to the membership.
    void removeBroker(const types::Uuid& id);  // Remove a broker from membership.

    types::Uuid getSystemId() const { return systemId; }

  private:
    void setPublicUrl(const Url&);
    void setBrokerUrl(const Url&);
    void updateClientUrl(sys::Mutex::ScopedLock&);

    void setStatus(BrokerStatus, sys::Mutex::ScopedLock&);
    void recover();
    void statusChanged(sys::Mutex::ScopedLock&);
    void setLinkProperties(sys::Mutex::ScopedLock&);

    std::vector<Url> getKnownBrokers() const;

    void membershipUpdated(sys::Mutex::ScopedLock&);

    broker::Broker& broker;
    types::Uuid systemId;
    const Settings settings;

    mutable sys::Mutex lock;
    boost::shared_ptr<ConnectionObserver> observer; // Used by Backup and Primary
    boost::shared_ptr<Backup> backup;
    boost::shared_ptr<Primary> primary;
    qmf::org::apache::qpid::ha::HaBroker::shared_ptr mgmtObject;
    Url publicUrl, brokerUrl;
    std::vector<Url> knownBrokers;
    BrokerStatus status;
    std::string logPrefix;
    BrokerInfo brokerInfo;
    Membership membership;
    ReplicationTest replicationTest;
    std::auto_ptr<StatusCheck> statusCheck;
};
}} // namespace qpid::ha

#endif  /*!QPID_HA_BROKER_H*/
