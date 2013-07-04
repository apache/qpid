#ifndef QPID_HA_SETTINGS_H
#define QPID_HA_SETTINGS_H

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
#include "qpid/sys/Time.h"
#include "qpid/sys/IntegerTypes.h"
#include <string>

namespace qpid {
namespace ha {

/**
 * Configurable settings for HA.
 */
class Settings
{
  public:
    Settings() : cluster(false), queueReplication(false),
                 replicateDefault(NONE), backupTimeout(10*sys::TIME_SEC),
                 flowMessages(1000), flowBytes(0)
    {}

    bool cluster;               // True if we are a cluster member.
    bool queueReplication;      // True if enabled.
    std::string publicUrl;
    std::string brokerUrl;
    Enum<ReplicateLevel> replicateDefault;
    std::string username, password, mechanism;
    sys::Duration backupTimeout;

    uint32_t flowMessages, flowBytes;

    static const uint32_t NO_LIMIT=0xFFFFFFFF;
    static uint32_t flowValue(uint32_t n) { return n ? n : NO_LIMIT; }
    uint32_t getFlowMessages() const { return flowValue(flowMessages); }
    uint32_t getFlowBytes() const { return flowValue(flowBytes); }
};
}} // namespace qpid::ha

#endif  /*!QPID_HA_SETTINGS_H*/
