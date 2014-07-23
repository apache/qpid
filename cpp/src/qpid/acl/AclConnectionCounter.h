#ifndef QPID_ACL_CONNECTIONCOUNTER_H
#define QPID_ACL_CONNECTIONCOUNTER_H

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

#include "qpid/broker/ConnectionObserver.h"
#include "qpid/sys/Mutex.h"
#include "qpid/acl/AclData.h"

#include <map>

namespace qpid {

namespace broker {
class Connection;
}

namespace acl {
class Acl;

 /**
 * Terminate client connections when a user tries to create 'too many'.
 * Terminate hostIp connections when an IP host tries to create 'too many'.
 */
class ConnectionCounter : public broker::ConnectionObserver
{
private:
    typedef std::map<std::string, uint32_t> connectCountsMap_t;
    enum CONNECTION_PROGRESS { C_CREATED=1, C_OPENED=2 };

    Acl&             acl;
    uint16_t         nameLimit;
    uint16_t         hostLimit;
    uint16_t         totalLimit;
    uint16_t         totalCurrentConnections;
    qpid::sys::Mutex dataLock;

    /** Records per-connection state */
    connectCountsMap_t connectProgressMap;

    /** Records per-username counts */
    connectCountsMap_t connectByNameMap;

    /** Records per-host counts */
    connectCountsMap_t connectByHostMap;

    /** Given a connection's management ID, return the client host name */
    std::string getClientHost(const std::string mgmtId);

    /** Return approval for proposed connection */
    bool limitApproveLH(connectCountsMap_t& theMap,
                        const std::string& theName,
                        uint16_t theLimit,
                        bool emitLog);

    /** Record a connection.
     * @return indication if user/host is over its limit */
    bool countConnectionLH(connectCountsMap_t& theMap,
                           const std::string& theName,
                           uint16_t theLimit,
                           bool emitLog,
                           bool enforceLimit);

    /** Release a connection */
    void releaseLH(connectCountsMap_t& theMap,
                   const std::string& theName);

public:
    ConnectionCounter(Acl& acl, uint16_t nl, uint16_t hl, uint16_t tl);
    ~ConnectionCounter();

    // ConnectionObserver interface
    void connection(broker::Connection& connection);
    void     closed(broker::Connection& connection);

    // Connection counting
    bool approveConnection(const broker::Connection& conn,
                           const std::string& userName,
                           bool enforcingConnectionQuotas,
                           uint16_t connectionLimit,
                           boost::shared_ptr<AclData> localdata
                          );
};

}} // namespace qpid::ha

#endif  /*!QPID_ACL_CONNECTIONCOUNTER_H*/
