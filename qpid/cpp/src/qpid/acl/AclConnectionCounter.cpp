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

#include "AclConnectionCounter.h"
#include "Acl.h"
#include "qpid/broker/Connection.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/SocketAddress.h"
#include <assert.h>
#include <sstream>

using namespace qpid::sys;

namespace qpid {
namespace acl {

//
// This module instantiates a broker::ConnectionObserver and limits client
// connections by counting connections per user name, per client IP address
// and per total connection count.
//


//
//
//
ConnectionCounter::ConnectionCounter(Acl& a, uint16_t nl, uint16_t hl, uint16_t tl) :
    acl(a), nameLimit(nl), hostLimit(hl), totalLimit(tl), totalCurrentConnections(0) {}

ConnectionCounter::~ConnectionCounter() {}


//
// limitApproveLH
//
// Connection creation approver. Return true only if user is under limit.
// Called with lock held.
//
bool ConnectionCounter::limitApproveLH(
    connectCountsMap_t& theMap,
    const std::string& theName,
    uint16_t theLimit,
    bool emitLog) {

    bool result(true);
    if (theLimit > 0) {
        uint16_t count;
        connectCountsMap_t::iterator eRef = theMap.find(theName);
        if (eRef != theMap.end()) {
            count = (uint16_t)(*eRef).second;
            result = count <= theLimit;
        } else {
            // Not found
            count = 0;
        }
        if (emitLog) {
            QPID_LOG(trace, "ACL ConnectionApprover IP=" << theName
                << " limit=" << theLimit
                << " curValue=" << count
                << " result=" << (result ? "allow" : "deny"));
        }
    }
    return result;
}


//
// countConnectionLH
//
// Increment the name's count in map and return an optional comparison
//  against a connection limit.
// Called with dataLock already taken.
//
bool ConnectionCounter::countConnectionLH(
    connectCountsMap_t& theMap,
    const std::string& theName,
    uint16_t theLimit,
    bool emitLog,
    bool enforceLimit) {

    bool result(true);
    uint16_t count(0);
    connectCountsMap_t::iterator eRef = theMap.find(theName);
    if (eRef != theMap.end()) {
        count = (uint16_t)(*eRef).second + 1;
        (*eRef).second = count;
    } else {
        theMap[theName] = count = 1;
    }
    if (enforceLimit) {
        result = count <= theLimit;
    }
    if (emitLog) {
        QPID_LOG(trace, "ACL ConnectionApprover user=" << theName
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
void ConnectionCounter::releaseLH(
    connectCountsMap_t& theMap, const std::string& theName) {

    connectCountsMap_t::iterator eRef = theMap.find(theName);
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
        // Connections denied by ACL never get users added
        //QPID_LOG(notice, "ACL ConnectionCounter Connection for '" << theName
        //    << "' not found in connection count pool");
    }
}


//
// connection - called during Connection's constructor
//
void ConnectionCounter::connection(broker::Connection& connection) {
    QPID_LOG(trace, "ACL ConnectionCounter new connection: " << connection.getMgmtId());

    const std::string& hostName(getClientHost(connection.getMgmtId()));

    Mutex::ScopedLock locker(dataLock);

    // Total connections goes up
    totalCurrentConnections += 1;

    // Record the fact that this connection exists
    connectProgressMap[connection.getMgmtId()] = C_CREATED;

    // Count the connection from this host.
    (void) countConnectionLH(connectByHostMap, hostName, hostLimit, false, false);
}


//
// closed - called during Connection's destructor
//
void ConnectionCounter::closed(broker::Connection& connection) {
    QPID_LOG(trace, "ACL ConnectionCounter closed: " << connection.getMgmtId()
        << ", userId:" << connection.getUserId());

    Mutex::ScopedLock locker(dataLock);

    connectCountsMap_t::iterator eRef = connectProgressMap.find(connection.getMgmtId());
    if (eRef != connectProgressMap.end()) {
        if ((*eRef).second == C_OPENED){
            // Normal case: connection was created and opened.
            // Decrement user in-use counts
            releaseLH(connectByNameMap,
                      connection.getUserId());
        } else {
            // Connection was created but not opened.
            // Don't decrement user count.
        }

        // Decrement host in-use count.
        releaseLH(connectByHostMap,
                  getClientHost(connection.getMgmtId()));

        // destroy connection progress indicator
        connectProgressMap.erase(eRef);

    } else {
        // connection not found in progress map
        QPID_LOG(notice, "ACL ConnectionCounter closed info for '" << connection.getMgmtId()
            << "' not found in connection state pool");
    }

    // total connections
    totalCurrentConnections -= 1;
}


//
// approveConnection
//  check total connections, connections from IP, connections by user and
//  disallow if over any limit
//
bool ConnectionCounter::approveConnection(
        const broker::Connection& connection,
        const std::string& userName,
        bool enforcingConnectionQuotas,
        uint16_t connectionUserQuota,
        boost::shared_ptr<AclData> localdata)
{
    const std::string& hostName(getClientHost(connection.getMgmtId()));

    Mutex::ScopedLock locker(dataLock);

    // Bump state from CREATED to OPENED
    (void) countConnectionLH(connectProgressMap, connection.getMgmtId(),
                             C_OPENED, false, false);

    // Run global black/white list check
    sys::SocketAddress sa(hostName, "");
    bool okByHostList(true);
    std::string hostLimitText;
    if (sa.isIp()) {
        AclResult result = localdata->isAllowedConnection(userName, hostName, hostLimitText);
        okByHostList = AclHelper::resultAllows(result);
        if (okByHostList) {
            QPID_LOG(trace, "ACL: ConnectionApprover host list " << hostLimitText);
        }
    }

    // Approve total connections
    bool okTotal  = true;
    if (totalLimit > 0) {
        okTotal = totalCurrentConnections <= totalLimit;
        QPID_LOG(trace, "ACL ConnectionApprover totalLimit=" << totalLimit
                 << " curValue=" << totalCurrentConnections
                 << " result=" << (okTotal ? "allow" : "deny"));
    }

    // Approve by IP host connections
    bool okByIP   = limitApproveLH(connectByHostMap, hostName, hostLimit, true);

    // Count and Approve the connection by the user
    bool okByUser = countConnectionLH(connectByNameMap, userName,
                                      connectionUserQuota, true,
                                      enforcingConnectionQuotas);

    // Emit separate log for each disapproval
    if (!okByHostList) {
        QPID_LOG(error, "ACL: ConnectionApprover host list " << hostLimitText
                 << " Connection refused.");
    }
    if (!okTotal) {
        QPID_LOG(error, "Client max total connection count limit of " << totalLimit
                 << " exceeded by '"
                 << connection.getMgmtId() << "', user: '"
                 << userName << "'. Connection refused");
    }
    if (!okByIP) {
        QPID_LOG(error, "Client max per-host connection count limit of "
                 << hostLimit << " exceeded by '"
                 << connection.getMgmtId() << "', user: '"
                 << userName << "'. Connection refused.");
    }
    if (!okByUser) {
        QPID_LOG(error, "Client max per-user connection count limit of "
                 << connectionUserQuota << " exceeded by '"
                 << connection.getMgmtId() << "', user: '"
                 << userName << "'. Connection refused.");
    }

    // Count/Event once for each disapproval
    bool result = okByHostList && okTotal && okByIP && okByUser;
    if (!result) {
        acl.reportConnectLimit(userName, hostName);
    }

    return result;
}

//
// getClientIp - given a connection's mgmtId return the client host part.
//
// TODO: Ideally this would be a method of the connection itself.
// TODO: Verify it works with rdma connection names.
//
std::string ConnectionCounter::getClientHost(const std::string mgmtId)
{
    size_t hyphen = mgmtId.find('-');
    if (std::string::npos != hyphen) {
        size_t colon = mgmtId.find_last_of(':');
        if (std::string::npos != colon) {
            // trailing colon found
            std::string tmp = mgmtId.substr(hyphen+1, colon - hyphen - 1);
            // undecorate ipv6
            if (tmp.length() >= 3 && tmp.find("[") == 0 && tmp.rfind("]") == tmp.length()-1)
                tmp = tmp.substr(1, tmp.length()-2);
            return tmp;

        } else {
            // colon not found - use everything after hyphen
            return mgmtId.substr(hyphen+1);
        }
    }

    // no hyphen found - use whole string
    return mgmtId;
}

}} // namespace qpid::ha
