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

#include "qpid/Url.h"
#include "qmf/org/apache/qpid/ha/HaBroker.h"
#include "qmf/org/apache/qpid/ha/ArgsHaBrokerSetStatus.h"
#include "qpid/management/Manageable.h"

namespace qpid {
namespace broker {
class Broker;
}
namespace ha {
class Settings;
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

  private:
    broker::Broker& broker;
    Url clientUrl, brokerUrl;
    std::auto_ptr<Backup> backup;
    qmf::org::apache::qpid::ha::HaBroker* mgmtObject;
};
}} // namespace qpid::ha

#endif  /*!QPID_HA_BROKER_H*/
