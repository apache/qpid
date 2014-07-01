#ifndef QPID_ACL_RESOURCECOUNTER_H
#define QPID_ACL_RESOURCECOUNTER_H

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

#include "qpid/sys/Mutex.h"

#include <map>

namespace qpid {

namespace acl {
class Acl;

 /**
 * Approve or disapprove resource creation requests
 */
class ResourceCounter
{
private:
    typedef std::map<std::string, uint32_t> countsMap_t;
    typedef std::map<std::string, std::string> queueOwnerMap_t;

    Acl&             acl;
    uint16_t         queueLimit;
    qpid::sys::Mutex dataLock;

    /** Records queueName-queueUserId */
    queueOwnerMap_t queueOwnerMap;

    /** Records queue-by-owner counts */
    countsMap_t queuePerUserMap;

    /** Return approval for proposed resource creation */
    bool limitApproveLH(countsMap_t& theMap,
                        const std::string& theName,
                        uint16_t theLimit,
                        bool emitLog,
                        bool enforceLimit);

    /** Release a connection */
    void releaseLH(countsMap_t& theMap,
                   const std::string& theName);

public:
    ResourceCounter(Acl& acl, uint16_t ql);
    ~ResourceCounter();

    // Queue counting
    bool approveCreateQueue(const std::string& userId,
    		const std::string& queueName,
            bool enforcingQueueQuotas,
            uint16_t queueUserQuota );
    void recordDestroyQueue(const std::string& queueName);
};

}} // namespace qpid::acl

#endif  /*!QPID_ACL_RESOURCECOUNTER_H*/
