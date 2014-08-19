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
#include "HaBroker.h"
#include "Membership.h"
#include "qpid/broker/Broker.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/types/Variant.h"
#include "qmf/org/apache/qpid/ha/EventMembersUpdate.h"
#include "qmf/org/apache/qpid/ha/HaBroker.h"
#include <boost/bind.hpp>
#include <iostream>
#include <iterator>

namespace qpid {
namespace ha {

namespace _qmf = ::qmf::org::apache::qpid::ha;

using sys::Mutex;
using types::Variant;

Membership::Membership(const BrokerInfo& info, HaBroker& b)
    : haBroker(b), self(info.getSystemId())
{
    brokers[self] = info;
    setPrefix();
    oldStatus = info.getStatus();
}

void Membership::setPrefix() {
    haBroker.logPrefix = Msg() << shortStr(brokers[self].getSystemId())
                               << "(" << printable(brokers[self].getStatus()) << ") ";
}
void Membership::clear() {
    Mutex::ScopedLock l(lock);
    BrokerInfo me = brokers[self];
    brokers.clear();
    brokers[self] = me;
}

void Membership::add(const BrokerInfo& b) {
    Mutex::ScopedLock l(lock);
    assert(b.getSystemId() != self);
    brokers[b.getSystemId()] = b;
    update(true, l);
}


void Membership::remove(const types::Uuid& id) {
    Mutex::ScopedLock l(lock);
    if (id == self) return;     // Never remove myself
    BrokerInfo::Map::iterator i = brokers.find(id);
    if (i != brokers.end()) {
        brokers.erase(i);
        update(true, l);
    }
}

bool Membership::contains(const types::Uuid& id) {
    Mutex::ScopedLock l(lock);
    return brokers.find(id) != brokers.end();
}

void Membership::assign(const types::Variant::List& list) {
    Mutex::ScopedLock l(lock);
    clear();
    for (types::Variant::List::const_iterator i = list.begin(); i != list.end(); ++i) {
        BrokerInfo b(i->asMap());
        brokers[b.getSystemId()] = b;
    }
    update(true, l);
}

types::Variant::List Membership::asList() const {
    Mutex::ScopedLock l(lock);
    return asList(l);
}

types::Variant::List Membership::asList(sys::Mutex::ScopedLock&) const {
    types::Variant::List list;
    for (BrokerInfo::Map::const_iterator i = brokers.begin(); i != brokers.end(); ++i)
        list.push_back(i->second.asMap());
    return list;
}

BrokerInfo::Set Membership::otherBackups() const {
    Mutex::ScopedLock l(lock);
    BrokerInfo::Set result;
    for (BrokerInfo::Map::const_iterator i = brokers.begin(); i != brokers.end(); ++i)
        if (i->second.getStatus() == READY && i->second.getSystemId() != self)
            result.insert(i->second);
    return result;
}

BrokerInfo::Set Membership::getBrokers() const {
    Mutex::ScopedLock l(lock);
    BrokerInfo::Set result;
    transform(brokers.begin(), brokers.end(), inserter(result, result.begin()),
              boost::bind(&BrokerInfo::Map::value_type::second, _1));
    return result;
}

bool Membership::get(const types::Uuid& id, BrokerInfo& result) const {
    Mutex::ScopedLock l(lock);
    BrokerInfo::Map::const_iterator i = brokers.find(id);
    if (i == brokers.end()) return false;
    result = i->second;
    return true;
}

namespace {
bool checkTransition(BrokerStatus from, BrokerStatus to) {
    // Legal state transitions. Initial state is JOINING, ACTIVE is terminal.
    static const BrokerStatus TRANSITIONS[][2] = {
        { STANDALONE, JOINING }, // Initialization of backup broker
        { JOINING, CATCHUP },    // Connected to primary
        { JOINING, RECOVERING }, // Chosen as initial primary.
        { CATCHUP, READY },      // Caught up all queues, ready to take over.
        { READY, RECOVERING },   // Chosen as new primary
        { READY, CATCHUP },      // Timed out failing over, demoted to catch-up.
        { RECOVERING, ACTIVE }   // All expected backups are ready
    };
    static const size_t N = sizeof(TRANSITIONS)/sizeof(TRANSITIONS[0]);
    for (size_t i = 0; i < N; ++i) {
        if (TRANSITIONS[i][0] == from && TRANSITIONS[i][1] == to)
            return true;
    }
    return false;
}
} // namespace

void Membership::update(bool log, Mutex::ScopedLock& l) {
    // Update managment and send update event.
    BrokerStatus newStatus = getStatus(l);
    Variant::List brokerList = asList(l);
    if (mgmtObject) {
        mgmtObject->set_status(printable(newStatus).str());
        mgmtObject->set_members(brokerList);
    }
    haBroker.getBroker().getManagementAgent()->raiseEvent(
        _qmf::EventMembersUpdate(brokerList));

    // Update link client properties
    framing::FieldTable linkProperties = haBroker.getBroker().getLinkClientProperties();
    if (isBackup(newStatus)) {
        // Set backup tag on outgoing link properties.
        linkProperties.setTable(
            ConnectionObserver::BACKUP_TAG, brokers[types::Uuid(self)].asFieldTable());
        haBroker.getBroker().setLinkClientProperties(linkProperties);
    } else {
        // Remove backup tag property from outgoing link properties.
        linkProperties.erase(ConnectionObserver::BACKUP_TAG);
        haBroker.getBroker().setLinkClientProperties(linkProperties);
    }

    // Check status transitions
    if (oldStatus != newStatus) {
        QPID_LOG(info, haBroker.logPrefix << "Status change: "
                 << printable(oldStatus) << " -> " << printable(newStatus));
        if (!checkTransition(oldStatus, newStatus)) {
            haBroker.shutdown(QPID_MSG("Illegal state transition: " << printable(oldStatus)
                                       << " -> " << printable(newStatus)));
        }
        oldStatus = newStatus;
        setPrefix();
        if (newStatus == READY) QPID_LOG(notice, haBroker.logPrefix << "Backup is ready");
    }
    if (log) QPID_LOG(info, haBroker.logPrefix << "Membership update: " <<  brokers);
}

void Membership::setMgmtObject(boost::shared_ptr<_qmf::HaBroker> mo) {
    Mutex::ScopedLock l(lock);
    mgmtObject = mo;
    update(false, l);
}


void Membership::setStatus(BrokerStatus newStatus) {
    Mutex::ScopedLock l(lock);
    brokers[self].setStatus(newStatus);
    update(false, l);
}

BrokerStatus Membership::getStatus() const  {
    Mutex::ScopedLock l(lock);
    return getStatus(l);
}

BrokerStatus Membership::getStatus(sys::Mutex::ScopedLock&) const  {
    BrokerInfo::Map::const_iterator i = brokers.find(self);
    assert(i != brokers.end());
    return i->second.getStatus();
}

BrokerInfo Membership::getSelf() const  {
    Mutex::ScopedLock l(lock);
    BrokerInfo::Map::const_iterator i = brokers.find(self);
    assert(i != brokers.end());
    return i->second;
}

void Membership::setSelfAddress(const Address& a) {
    Mutex::ScopedLock l(lock);
    brokers[self].setAddress(a);
    update(false, l);
}

}} // namespace qpid::ha
