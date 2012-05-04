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

#include "Settings.h"
#include "qpid/Url.h"
#include "qpid/sys/Mutex.h"
#include "qmf/org/apache/qpid/ha/HaBroker.h"
#include "qpid/management/Manageable.h"
#include <memory>

namespace qpid {
namespace broker {
class Broker;
}
namespace ha {
class Backup;

/**
 * HA state and actions associated with a broker.
 *
 * THREAD SAFE: may be called in arbitrary broker IO or timer threads.
 */
class HaBroker : public management::Manageable
{
  public:
    HaBroker(broker::Broker&, const Settings&);
    ~HaBroker();

    // Implement Manageable.
    qpid::management::ManagementObject* GetManagementObject() const { return mgmtObject; }
    management::Manageable::status_t ManagementMethod (
        uint32_t methodId, management::Args& args, std::string& text);

    broker::Broker& getBroker() { return broker; }
    const Settings& getSettings() const { return settings; }

    // Log a critical error message and shut down the broker.
    void shutdown(const std::string& message);

  private:
    void setClientUrl(const Url&, const sys::Mutex::ScopedLock&);
    void setBrokerUrl(const Url&, const sys::Mutex::ScopedLock&);
    void setExpectedBackups(size_t, const sys::Mutex::ScopedLock&);
    void updateClientUrl(const sys::Mutex::ScopedLock&);
    bool isPrimary(const sys::Mutex::ScopedLock&) { return !backup.get(); }
    std::vector<Url> getKnownBrokers() const;

    broker::Broker& broker;
    const Settings settings;

    sys::Mutex lock;
    std::auto_ptr<Backup> backup;
    qmf::org::apache::qpid::ha::HaBroker* mgmtObject;
    Url clientUrl, brokerUrl;
    std::vector<Url> knownBrokers;
    size_t expectedBackups;
};
}} // namespace qpid::ha

#endif  /*!QPID_HA_BROKER_H*/
