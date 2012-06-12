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
#include "HaBroker.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/broker/Connection.h"
#include <boost/function.hpp>
#include <sstream>

namespace qpid {
namespace ha {

ConnectionExcluder::ConnectionExcluder(HaBroker& hb, const types::Uuid& uuid)
    : haBroker(hb), logPrefix("HA: "), self(uuid) {}

namespace {
bool getBrokerInfo(broker::Connection& connection, BrokerInfo& info) {
    framing::FieldTable ft;
    if (connection.getClientProperties().getTable(ConnectionExcluder::BACKUP_TAG, ft)) {
        info = BrokerInfo(ft);
        return true;
    }
    return false;
}
}

void ConnectionExcluder::opened(broker::Connection& connection) {
    if (connection.isLink()) return; // Allow all outgoing links
    if (connection.getClientProperties().isSet(ADMIN_TAG)) {
        QPID_LOG(debug, logPrefix << "Allowing admin connection: "
                 << connection.getMgmtId());
        return;
    }
    BrokerStatus status = haBroker.getStatus();
    if (isBackup(status)) reject(connection);
    BrokerInfo info;            // Avoid self connections.
    if (getBrokerInfo(connection, info)) {
        if (info.getSystemId() == self) {
            QPID_LOG(debug, logPrefix << "Rejected self connection");
            reject(connection);
        }
        else {
            QPID_LOG(debug, logPrefix << "Allowed backup connection " << info);
            haBroker.getMembership().add(info);
            return;
        }
    }
    // else: Primary node accepts connections.
}

void ConnectionExcluder::reject(broker::Connection& connection) {
    throw Exception(
        QPID_MSG(logPrefix << "Rejected connection " << connection.getMgmtId()));
}

void ConnectionExcluder::closed(broker::Connection& connection) {
    BrokerInfo info;
    BrokerStatus status = haBroker.getStatus();
    if (isBackup(status)) return; // Don't mess with the map received from primary.
    if (getBrokerInfo(connection, info))
        haBroker.getMembership().remove(info.getSystemId());
}

const std::string ConnectionExcluder::ADMIN_TAG="qpid.ha-admin";
const std::string ConnectionExcluder::BACKUP_TAG="qpid.ha-backup";

}} // namespace qpid::ha
