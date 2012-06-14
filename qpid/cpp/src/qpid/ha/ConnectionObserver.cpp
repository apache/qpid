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

#include "ConnectionObserver.h"
#include "BrokerInfo.h"
#include "HaBroker.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/broker/Connection.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace ha {

ConnectionObserver::ConnectionObserver(HaBroker& hb, const types::Uuid& uuid)
    : haBroker(hb), logPrefix("Connections: "), self(uuid) {}

// FIXME aconway 2012-06-06: move to BrokerInfo
bool ConnectionObserver::getBrokerInfo(broker::Connection& connection, BrokerInfo& info) {
    framing::FieldTable ft;
    if (connection.getClientProperties().getTable(ConnectionObserver::BACKUP_TAG, ft)) {
        info = BrokerInfo(ft);
        return true;
    }
    return false;
}

void ConnectionObserver::setObserver(const ObserverPtr& o){
    sys::Mutex::ScopedLock l(lock);
    observer = o;
}

ConnectionObserver::ObserverPtr ConnectionObserver::getObserver() {
    sys::Mutex::ScopedLock l(lock);
    return observer;
}

void ConnectionObserver::opened(broker::Connection& connection) {
    if (connection.isLink()) return; // Allow outgoing links.
    if (connection.getClientProperties().isSet(ADMIN_TAG)) {
        QPID_LOG(debug, logPrefix << "Allowing admin connection: "
                 << connection.getMgmtId());
        return;                 // No need to call observer, always allow admins.
    }
    BrokerInfo info;            // Avoid self connections.
    if (getBrokerInfo(connection, info)) {
        if (info.getSystemId() == self) {
            // FIXME aconway 2012-06-13: suppress caught error message, make this an info message.
            QPID_LOG(error, "HA broker rejected self connection "+connection.getMgmtId());
            throw Exception("HA broker rejected self connection "+connection.getMgmtId());
        }

    }
    ObserverPtr o(getObserver());
    if (o) o->opened(connection);
}

void ConnectionObserver::closed(broker::Connection& connection) {
    BrokerInfo info;
    ObserverPtr o(getObserver());
    if (o) o->closed(connection);
}

const std::string ConnectionObserver::ADMIN_TAG="qpid.ha-admin";
const std::string ConnectionObserver::BACKUP_TAG="qpid.ha-backup";

}} // namespace qpid::ha
