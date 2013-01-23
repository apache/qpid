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
#include "Membership.h"
#include "HaBroker.h"
#include "qpid/broker/Broker.h"
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
}

void Membership::clear() {
    Mutex::ScopedLock l(lock);
    BrokerInfo me = brokers[self];
    brokers.clear();
    brokers[self] = me;
}

void Membership::add(const BrokerInfo& b) {
    Mutex::ScopedLock l(lock);
    brokers[b.getSystemId()] = b;
    update(l);
}


void Membership::remove(const types::Uuid& id) {
    Mutex::ScopedLock l(lock);
    BrokerInfo::Map::iterator i = brokers.find(id);
    if (i != brokers.end()) {
        brokers.erase(i);
        update(l);
    }
}

bool Membership::contains(const types::Uuid& id) {
    Mutex::ScopedLock l(lock);
    return brokers.find(id) != brokers.end();
}

void Membership::assign(const types::Variant::List& list) {
    Mutex::ScopedLock l(lock);
    brokers.clear();
    for (types::Variant::List::const_iterator i = list.begin(); i != list.end(); ++i) {
        BrokerInfo b(i->asMap());
        brokers[b.getSystemId()] = b;
    }
    update(l);
}

types::Variant::List Membership::asList() const {
    Mutex::ScopedLock l(lock);
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

bool Membership::get(const types::Uuid& id, BrokerInfo& result) const {
    Mutex::ScopedLock l(lock);
    BrokerInfo::Map::const_iterator i = brokers.find(id);
    if (i == brokers.end()) return false;
    result = i->second;
    return true;
}

void Membership::update(Mutex::ScopedLock& l) {
    QPID_LOG(info, "Membership: " <<  brokers);
    Variant::List brokers = asList();
    if (mgmtObject) mgmtObject->set_status(printable(getStatus(l)).str());
    if (mgmtObject) mgmtObject->set_members(brokers);
    haBroker.getBroker().getManagementAgent()->raiseEvent(
        _qmf::EventMembersUpdate(brokers));
}

void Membership::setMgmtObject(boost::shared_ptr<_qmf::HaBroker> mo) {
    Mutex::ScopedLock l(lock);
    mgmtObject = mo;
    update(l);
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

void Membership::setStatus(BrokerStatus newStatus) {
    BrokerStatus status = getStatus();
    QPID_LOG(info, "Status change: "
             << printable(status) << " -> " << printable(newStatus));
    bool legal = checkTransition(status, newStatus);
    if (!legal) {
        haBroker.shutdown(QPID_MSG("Illegal state transition: " << printable(status)
                                 << " -> " << printable(newStatus)));
    }

    Mutex::ScopedLock l(lock);
    brokers[self].setStatus(newStatus);
    if (mgmtObject) mgmtObject->set_status(printable(newStatus).str());
    update(l);
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

BrokerInfo Membership::getInfo() const  {
    Mutex::ScopedLock l(lock);
    BrokerInfo::Map::const_iterator i = brokers.find(self);
    assert(i != brokers.end());
    return i->second;
}

// FIXME aconway 2013-01-23: move to .h?
}} // namespace qpid::ha
