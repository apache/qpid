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
#include <boost/iterator/iterator_concepts.hpp>

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
    uint32_t         nameLimit;
    uint32_t         hostLimit;
    qpid::sys::Mutex dataLock;

    connectCountsMap_t connectProgressMap;
    connectCountsMap_t connectByNameMap;
    connectCountsMap_t connectByHostMap;

    std::string getClientHost(const std::string mgmtId);

    bool limitCheckLH(connectCountsMap_t& theMap,
                      const std::string& theName,
                      uint32_t theLimit);

    void releaseLH(connectCountsMap_t& theMap,
                   const std::string& theName,
                   uint32_t theLimit);

public:
    ConnectionCounter(Acl& acl, uint32_t nl, uint32_t hl);
    ~ConnectionCounter();

    void connection(broker::Connection& connection);
    void     opened(broker::Connection& connection);
    void     closed(broker::Connection& connection);

};

}} // namespace qpid::ha

#endif  /*!QPID_ACL_CONNECTIONCOUNTER_H*/
