#ifndef QPID_HA_CONNECTIONEXCLUDER_H
#define QPID_HA_CONNECTIONEXCLUDER_H

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
#include "qpid/broker/Connection.h"
#include <boost/function.hpp>
#include <sstream>

namespace qpid {
namespace ha {

/**
 * Exclude normal connections to a backup broker.
 * Connections as ha-admin user are allowed.
 */
class ConnectionExcluder : public broker::ConnectionObserver
{
  public:
    typedef boost::function<bool()> PrimaryTest;

    ConnectionExcluder(string adminUser_, PrimaryTest isPrimary_)
        : adminUser(adminUser_), isPrimary(isPrimary_) {}

    void connect(broker::Connection& connection) {
        if (!isPrimary() && !connection.isLink()
            && !connection.isAuthenticatedUser(adminUser))
        {
            throw Exception(
                QPID_MSG(
                    "HA: Backup broker rejected connection "
                    << connection.getMgmtId() << " by user " << connection.getUserId()
                    << ". Only " << adminUser << " can connect to a backup."));
        }
        else {
            QPID_LOG(debug, "HA: Backup broker accepted connection"
                     << connection.getMgmtId() << " by user "
                     << connection.getUserId());
        }
    }

    void disconnect(broker::Connection&) {}

  private:
    string adminUser;
    PrimaryTest isPrimary;
};
}} // namespace qpid::ha

#endif  /*!QPID_HA_CONNECTIONEXCLUDER_H*/
