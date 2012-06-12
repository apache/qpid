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
#include <boost/bind.hpp>
#include <iostream>
#include <iterator>

namespace qpid {
namespace ha {


void Membership::reset(const BrokerInfo& b) {
    sys::Mutex::ScopedLock l(lock);
    brokers.clear();
    brokers[b.getSystemId()] = b;
    update(l);
}

void Membership::add(const BrokerInfo& b) {
    sys::Mutex::ScopedLock l(lock);
    brokers[b.getSystemId()] = b;
    update(l);
}


void Membership::remove(const types::Uuid& id) {
    sys::Mutex::ScopedLock l(lock);
    BrokerInfo::Map::iterator i = brokers.find(id);
    if (i != brokers.end()) {
        brokers.erase(i);
        update(l);
    }
}

bool Membership::contains(const types::Uuid& id) {
    sys::Mutex::ScopedLock l(lock);
    return brokers.find(id) != brokers.end();
}

void Membership::assign(const types::Variant::List& list) {
    sys::Mutex::ScopedLock l(lock);
    brokers.clear();
    for (types::Variant::List::const_iterator i = list.begin(); i != list.end(); ++i) {
        BrokerInfo b(i->asMap());
        brokers[b.getSystemId()] = b;
    }
    update(l);
}

types::Variant::List Membership::asList() const {
    sys::Mutex::ScopedLock l(lock);
    return asList(l);
}

types::Variant::List Membership::asList(sys::Mutex::ScopedLock&) const {
    types::Variant::List list;
    for (BrokerInfo::Map::const_iterator i = brokers.begin(); i != brokers.end(); ++i)
        list.push_back(i->second.asMap());
    return list;
}

void Membership::update(sys::Mutex::ScopedLock& l) {
    if (updateCallback) {
        types::Variant::List list = asList(l);
        sys::Mutex::ScopedUnlock u(lock);
        updateCallback(list);
    }
    QPID_LOG(debug, " HA: Membership update: " << brokers);
}

BrokerInfo::Set Membership::otherBackups() const {
    sys::Mutex::ScopedLock l(lock);
    return otherBackups(l);
}

BrokerInfo::Set Membership::otherBackups(sys::Mutex::ScopedLock&) const {
    BrokerInfo::Set result;
    for (BrokerInfo::Map::const_iterator i = brokers.begin(); i != brokers.end(); ++i)
        if (isBackup(i->second.getStatus()) && i->second.getSystemId() != self)
            result.insert(i->second);
    return result;
}

bool Membership::get(const types::Uuid& id, BrokerInfo& result) {
    sys::Mutex::ScopedLock l(lock);
    BrokerInfo::Map::iterator i = brokers.find(id);
    if (i == brokers.end()) return false;
    result = i->second;
    return true;
}

}} // namespace qpid::ha
