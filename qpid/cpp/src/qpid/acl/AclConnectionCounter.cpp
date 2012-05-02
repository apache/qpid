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
#include "qpid/broker/Connection.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Mutex.h"
#include <assert.h>
#include <sstream>

using namespace qpid::sys;

namespace qpid {
namespace acl {

//
// This module instantiates a broker::ConnectionObserver and limits client
// connections by counting connections per user name and per client IP address.
//


//
//
//
ConnectionCounter::ConnectionCounter(uint32_t nl, uint32_t hl) :
    nameLimit(nl), hostLimit(hl) {}

ConnectionCounter::~ConnectionCounter() {}


//
// limitCheckLH
//
// Increment the name's count in map and return a comparison against the limit.
// called with dataLock already taken
//
bool ConnectionCounter::limitCheckLH(
    connectCountsMap_t& theMap, const std::string& theName, uint32_t theLimit) {

    bool result(true);
    if (theLimit > 0) {
        connectCountsMap_t::iterator eRef = theMap.find(theName);
        if (eRef != theMap.end()) {
            uint32_t count = (uint32_t)(*eRef).second + 1;
            (*eRef).second = count;
            result = count <= theLimit;
        } else {
            theMap[theName] = 1;
        }
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
    connectCountsMap_t& theMap, const std::string& theName, uint32_t theLimit) {

    if (theLimit > 0) {
        connectCountsMap_t::iterator eRef = theMap.find(theName);
        if (eRef != theMap.end()) {
            uint32_t count = (uint32_t) (*eRef).second;
            assert (count > 0);
            if (1 == count) {
                theMap.erase (eRef);
            } else {
                (*eRef).second = count - 1;
            }
        } else {
            // User had no connections.
            QPID_LOG(notice, "ACL ConnectionCounter Connection for '" << theName
                << "' not found in connection count pool");
        }
    }
}


//
// connection - called during Connection's constructor
//
void ConnectionCounter::connection(broker::Connection& connection) {
    QPID_LOG(trace, "ACL ConnectionCounter connection IP:" << connection.getMgmtId()
        << ", user:" << connection.getUsername());

    Mutex::ScopedLock locker(dataLock);

    connectProgressMap[connection.getMgmtId()] = C_CREATED;
}


//
// opened - called when first AMQP frame is received over Connection
//
void ConnectionCounter::opened(broker::Connection& connection) {
    QPID_LOG(trace, "ACL ConnectionCounter Opened IP:" << connection.getMgmtId()
        << ", user:" << connection.getUsername());

    Mutex::ScopedLock locker(dataLock);

    const std::string& userName(              connection.getUsername());
    const std::string& hostName(getClientHost(connection.getMgmtId()));

    // Bump state from CREATED to OPENED
    (void) limitCheckLH(connectProgressMap, connection.getMgmtId(), C_OPENED);

    bool nameOk = limitCheckLH(connectByNameMap, userName, nameLimit);
    bool hostOk = limitCheckLH(connectByHostMap, hostName, hostLimit);

    if (!nameOk) {
        // User has too many
        QPID_LOG(info, "ACL ConnectionCounter User '" << userName
            << "' exceeded maximum allowed connections");
        throw Exception(
            QPID_MSG("User '" << userName
                << "' exceeded maximum allowed connections"));
    }

    if (!hostOk) {
        // Host has too many
        QPID_LOG(info, "ACL ConnectionCounter Client host '" << hostName
            << "' exceeded maximum allowed connections");
        throw Exception(
            QPID_MSG("Client host '" << hostName
                << "' exceeded maximum allowed connections"));
    }
}


//
// closed - called during Connection's destructor
//
void ConnectionCounter::closed(broker::Connection& connection) {
    QPID_LOG(trace, "ACL ConnectionCounter Closed IP:" << connection.getMgmtId()
        << ", user:" << connection.getUsername());

    Mutex::ScopedLock locker(dataLock);

    connectCountsMap_t::iterator eRef = connectProgressMap.find(connection.getMgmtId());
    if (eRef != connectProgressMap.end()) {
        if ((*eRef).second == C_OPENED){
            // Normal case: connection was created and opened.
            // Decrement in-use counts
            releaseLH(connectByNameMap,
                      connection.getUsername(),
                      nameLimit);

            releaseLH(connectByHostMap,
                      getClientHost(connection.getMgmtId()),
                      hostLimit);
        } else {
            // Connection was created but not opened.
            // Don't decrement any connection counts.
        }
        connectProgressMap.erase(eRef);

    } else {
        // connection not found in progress map
        QPID_LOG(notice, "ACL ConnectionCounter info for '" << connection.getMgmtId()
            << "' not found in connection state pool");
    }
}


//
// getClientIp - given a connection's mgmtId return the client host part.
//
// TODO: Ideally this would be a method of the connection itself.
//
std::string ConnectionCounter::getClientHost(const std::string mgmtId)
{
    size_t hyphen = mgmtId.find('-');
    if (std::string::npos != hyphen) {
        size_t colon = mgmtId.find_last_of(':');
        if (std::string::npos != colon) {
            // trailing colon found
            return mgmtId.substr(hyphen+1, colon - hyphen - 1);
        } else {
            // colon not found - use everything after hyphen
            return mgmtId.substr(hyphen+1);
        }
    }

    // no hyphen found - use whole string
    assert(false);
    return mgmtId;
}

}} // namespace qpid::ha
