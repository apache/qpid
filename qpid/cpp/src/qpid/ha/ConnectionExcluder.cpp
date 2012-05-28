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

#include "ConnectionExcluder.h"
#include "BrokerInfo.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/broker/Connection.h"
#include <boost/function.hpp>
#include <sstream>

namespace qpid {
namespace ha {

ConnectionExcluder::ConnectionExcluder(const LogPrefix& lp, const framing::Uuid& uuid)
    : logPrefix(lp), backupAllowed(false), self(uuid) {}

void ConnectionExcluder::opened(broker::Connection& connection) {
    if (connection.isLink()) return; // Allow all outgoing links
    if (connection.getClientProperties().isSet(ADMIN_TAG)) {
        QPID_LOG(debug, logPrefix << "Allowing admin connection: "
                 << connection.getMgmtId());
        return;
    }
    framing::FieldTable ft;
    if (connection.getClientProperties().getTable(BACKUP_TAG, ft)) {
        BrokerInfo info(ft);
        if (info.getSystemId() == self) {
            QPID_LOG(debug, logPrefix << "Self connection rejected");
        }
        else {
            QPID_LOG(debug, logPrefix << "Backup connection " << info <<
                     (backupAllowed ? " allowed" : " rejected"));
            if (backupAllowed) return;
        }
    }
    // Abort the connection.
    throw Exception(
        QPID_MSG(logPrefix << "Rejected connection " << connection.getMgmtId()));
}

const std::string ConnectionExcluder::ADMIN_TAG="qpid.ha-admin";
const std::string ConnectionExcluder::BACKUP_TAG="qpid.ha-backup";

}} // namespace qpid::ha
