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

#include "AclResourceCounter.h"
#include "Acl.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Mutex.h"
#include <assert.h>
#include <sstream>

using namespace qpid::sys;

namespace qpid {
namespace acl {

//
// This module approves various resource creation requests:
//   Queues
//


//
//
//
ResourceCounter::ResourceCounter(Acl& a, uint16_t ql) :
    acl(a), queueLimit(ql) {}

ResourceCounter::~ResourceCounter() {}


//
// limitApproveLH
//
// Resource creation approver.
// If user is under limit increment count and return true.
// Called with lock held.
//
bool ResourceCounter::limitApproveLH(
    countsMap_t& theMap,
    const std::string& theName,
    uint16_t theLimit,
    bool emitLog,
    bool enforceLimit) {

    bool result(true);
    uint16_t count;
    countsMap_t::iterator eRef = theMap.find(theName);
    if (eRef != theMap.end()) {
        count = (uint16_t)(*eRef).second;
        result = (enforceLimit ? count < theLimit : true);
        if (result) {
            count += 1;
            (*eRef).second = count;
        }
    } else {
        // user not found in map
        if (enforceLimit) {
            if (theLimit > 0) {
                theMap[theName] = count = 1;
            } else {
                count = 0;
                result = false;
            }
        }
        else {
            // not enforcing the limit
            theMap[theName] = count = 1;
        }
    }
    if (emitLog) {
        QPID_LOG(trace, "ACL QueueApprover user=" << theName
            << " limit=" << theLimit
            << " curValue=" << count
            << " result=" << (result ? "allow" : "deny"));
    }
    return result;
}


//
// releaseLH
//
// Decrement the name's count in map.
// called with dataLock already taken
//
void ResourceCounter::releaseLH(countsMap_t& theMap, const std::string& theName) {

    countsMap_t::iterator eRef = theMap.find(theName);
    if (eRef != theMap.end()) {
        uint16_t count = (uint16_t) (*eRef).second;
        assert (count > 0);
        if (1 == count) {
            theMap.erase (eRef);
        } else {
            (*eRef).second = count - 1;
        }
    } else {
        // User had no connections.
        QPID_LOG(notice, "ACL resource counter: Queue owner for queue '" << theName
            << "' not found in resource count pool");
    }
}


//
// approveCreateQueue
//  Count an attempted queue creation by this user.
//  Disapprove if over limit.
//
bool ResourceCounter::approveCreateQueue(const std::string& userId,
        const std::string& queueName,
        bool enforcingQueueQuotas,
        uint16_t queueUserQuota )
{
    Mutex::ScopedLock locker(dataLock);

    bool okByQ = limitApproveLH(queuePerUserMap, userId, queueUserQuota, true, enforcingQueueQuotas);

    if (okByQ) {
        // Queue is owned by this userId
        queueOwnerMap[queueName] = userId;

        QPID_LOG(trace, "ACL create queue approved for user '" << userId
            << "' queue '" << queueName << "'");
    } else {

        QPID_LOG(error, "Client max queue count limit of " << queueUserQuota
            << " exceeded by '" << userId << "' creating queue '"
            << queueName << "'. Queue creation denied.");

        acl.reportQueueLimit(userId, queueName);
    }
    return okByQ;
}


//
// recordDestroyQueue
//  Return a destroyed queue to a user's quota
//
void ResourceCounter::recordDestroyQueue(const std::string& queueName)
{
    Mutex::ScopedLock locker(dataLock);

    queueOwnerMap_t::iterator eRef = queueOwnerMap.find(queueName);
    if (eRef != queueOwnerMap.end()) {
        releaseLH(queuePerUserMap, (*eRef).second);

        queueOwnerMap.erase(eRef);
    } else {
        QPID_LOG(notice, "ACL resource counter: Queue '" << queueName
            << "' not found in queue owner map");
    }
}

}} // namespace qpid::acl
