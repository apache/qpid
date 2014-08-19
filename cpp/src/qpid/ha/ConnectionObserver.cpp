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
#include "qpid/Url.h"
#include "qpid/types/Variant.h"
#include "qpid/broker/Connection.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace ha {

ConnectionObserver::ConnectionObserver(HaBroker& hb, const types::Uuid& uuid)
    : haBroker(hb), logPrefix(hb.logPrefix), self(uuid) {}

bool ConnectionObserver::getBrokerInfo(const broker::Connection& connection, BrokerInfo& info) {
    qpid::types::Variant::Map::const_iterator i = connection.getClientProperties().find(ConnectionObserver::BACKUP_TAG);
    if (i != connection.getClientProperties().end() && i->second.getType() == qpid::types::VAR_MAP) {
        info = BrokerInfo(i->second.asMap());
        return true;
    }
    return false;
}

bool ConnectionObserver::getAddress(const broker::Connection& connection, Address& addr) {
    qpid::types::Variant::Map::const_iterator i = connection.getClientProperties().find(ConnectionObserver::ADDRESS_TAG);
    if (i != connection.getClientProperties().end()) {
        Url url;
        url.parseNoThrow(i->second.asString().c_str());
        if (!url.empty()) {
            addr = url[0];
            return true;
        }
    }
    return false;
}

void ConnectionObserver::setObserver(const ObserverPtr& o)
{
    sys::Mutex::ScopedLock l(lock);
    observer = o;
}

ConnectionObserver::ObserverPtr ConnectionObserver::getObserver() {
    sys::Mutex::ScopedLock l(lock);
    return observer;
}

void ConnectionObserver::reset() {
    sys::Mutex::ScopedLock l(lock);
    observer.reset();
}

bool ConnectionObserver::isSelf(const broker::Connection& connection) {
    BrokerInfo info;
    return getBrokerInfo(connection, info) && info.getSystemId() == self;
}

void ConnectionObserver::opened(broker::Connection& connection) {
    try {
        if (isSelf(connection)) { // Reject self connections
            // Set my own address if there is an address header.
            Address addr;
            if (getAddress(connection, addr)) haBroker.setAddress(addr);
            QPID_LOG(trace, logPrefix << "Rejected self connection "+connection.getMgmtId());
            connection.abort();
            return;
        }
        if (connection.isLink()) return; // Allow outgoing links.
        if (connection.getClientProperties().find(ADMIN_TAG) != connection.getClientProperties().end()) {
            QPID_LOG(trace, logPrefix << "Accepted admin connection: " << connection.getMgmtId());
            return;                 // No need to call observer, always allow admins.
        }
        ObserverPtr o(getObserver());
        if (o) o->opened(connection);
    }
    catch (const std::exception& e) {
        QPID_LOG(error, logPrefix << "Error on incoming connection " << connection.getMgmtId()
                 << ": " << e.what());
        throw;
    }
}

void ConnectionObserver::closed(broker::Connection& connection) {
    if (isSelf(connection)) return; // Ignore closing of self connections.
    try {
        ObserverPtr o(getObserver());
        if (o) o->closed(connection);
    }
    catch (const std::exception& e) {
        QPID_LOG(error, logPrefix << "Error closing incoming connection " << connection.getMgmtId()
                 << ": " << e.what());
        throw;
    }
}

const std::string ConnectionObserver::ADMIN_TAG="qpid.ha-admin";
const std::string ConnectionObserver::BACKUP_TAG="qpid.ha-backup";
const std::string ConnectionObserver::ADDRESS_TAG="qpid.ha-address";

}} // namespace qpid::ha
