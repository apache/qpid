#ifndef QPID_BROKER_BROKEROPTIONS_H
#define QPID_BROKER_BROKEROPTIONS_H

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

#include "qpid/Options.h"
#include "qpid/sys/Time.h"
#include "qpid/sys/IntegerTypes.h"

#include "qpid/broker/BrokerImportExport.h"

#include <string>
#include <vector>

namespace qpid {
namespace broker {

struct BrokerOptions : public qpid::Options
{
    static const std::string DEFAULT_DATA_DIR_LOCATION;
    static const std::string DEFAULT_DATA_DIR_NAME;
    static const std::string DEFAULT_PAGED_QUEUE_DIR;

    QPID_BROKER_EXTERN BrokerOptions(const std::string& name="Broker Options");

    bool noDataDir;
    std::string dataDir;
    std::string pagingDir;
    uint16_t port;
    std::vector<std::string> listenInterfaces;
    std::vector<std::string> listenDisabled;
    std::vector<std::string> protocols;
    int workerThreads;
    int connectionBacklog;
    bool enableMgmt;
    bool mgmtPublish;
    sys::Duration mgmtPubInterval;
    sys::Duration queueCleanInterval;
    bool auth;
    std::string realm;
    std::string saslServiceName;
    size_t replayFlushLimit;
    size_t replayHardLimit;
    uint queueLimit;
    bool tcpNoDelay;
    bool requireEncrypted;
    std::string knownHosts;
    std::string saslConfigPath;
    bool qmf2Support;
    bool qmf1Support;
    uint queueFlowStopRatio;    // producer flow control: on
    uint queueFlowResumeRatio;  // producer flow control: off
    uint16_t queueThresholdEventRatio;
    std::string defaultMsgGroup;
    bool timestampRcvMsgs;
    sys::Duration linkMaintenanceInterval;
    sys::Duration linkHeartbeatInterval;
    uint32_t dtxDefaultTimeout; // Default timeout of a DTX transaction
    uint32_t dtxMaxTimeout;     // Maximal timeout of a DTX transaction
    uint32_t maxNegotiateTime;  // Max time in ms for connection with no negotiation
    std::string fedTag;

private:
    std::string getHome();
};

}}

#endif // QPID_BROKER_BROKEROPTIONS_H
