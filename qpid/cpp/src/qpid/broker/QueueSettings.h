#ifndef QPID_BROKER_QUEUESETTINGS_H
#define QPID_BROKER_QUEUESETTINGS_H

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
#include "qpid/broker/BrokerImportExport.h"
#include "qpid/broker/QueueDepth.h"
#include "qpid/sys/IntegerTypes.h"
#include "qpid/framing/FieldTable.h"
#include <string>
#include <map>

namespace qpid {
namespace types {
class Variant;
}
namespace broker {

/**
 * Defines the various queue configuration settings that can be specified
 */
struct QueueSettings
{
    QPID_BROKER_EXTERN QueueSettings(bool durable=false, bool autodelete=false);
    /**
     * The lifetime policy  dictates when an autodelete queue is
     * eligible for delete.
     */
    enum LifetimePolicy
    {
        DELETE_IF_UNUSED = 0,
        DELETE_IF_EMPTY,
        DELETE_IF_UNUSED_AND_EMPTY,
        DELETE_ON_CLOSE
    };

    bool durable;
    bool autodelete;
    LifetimePolicy lifetime;
    bool isTemporary;

    //basic queue types:
    std::string lvqKey;
    uint32_t priorities;
    uint32_t defaultFairshare;
    std::map<uint32_t,uint32_t> fairshare;

    //message groups:
    std::string groupKey;
    bool shareGroups;
    bool addTimestamp;//not actually used; always on at present?

    QueueDepth maxDepth;
    bool dropMessagesAtLimit;//aka ring queue policy
    bool selfDestructAtLimit;

    //PagedQueue:
    bool paging;
    uint maxPages;
    uint pageFactor;

    bool noLocal;
    bool isBrowseOnly;
    std::string traceId;
    std::string traceExcludes;
    uint64_t autoDeleteDelay;//queueTtl?

    //flow control:
    QueueDepth flowStop;
    QueueDepth flowResume;

    //threshold events:
    QueueDepth alertThreshold;
    QueueDepth alertThresholdDown;
    int64_t alertRepeatInterval;

    //file limits checked by Acl and shared with storeSettings
    uint64_t maxFileSize;
    uint64_t maxFileCount;

    std::string sequenceKey;
    // store bool to avoid testing string value
    bool sequencing;

    std::string filter;

    //yuck, yuck
    qpid::framing::FieldTable storeSettings;
    std::map<std::string, qpid::types::Variant> original;

    bool handle(const std::string& key, const qpid::types::Variant& value);
    void validate() const;
    QPID_BROKER_EXTERN void populate(const std::map<std::string, qpid::types::Variant>& inputs, std::map<std::string, qpid::types::Variant>& unused);
    QPID_BROKER_EXTERN void populate(const qpid::framing::FieldTable& inputs, qpid::framing::FieldTable& unused);
    QPID_BROKER_EXTERN std::map<std::string, qpid::types::Variant> asMap() const;
    std::string getLimitPolicy() const;

    struct Aliases : std::map<std::string, std::string>
    {
        Aliases();
    };
    static const Aliases aliases;
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_QUEUESETTINGS_H*/
