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

namespace qpid {
namespace ha {


void Membership::reset(const BrokerInfo& b) {
    {
        sys::Mutex::ScopedLock l(lock);
        brokers.clear();
        brokers[b.getSystemId()] = b;
    }
    update();
}

void Membership::add(const BrokerInfo& b) {
    {
        sys::Mutex::ScopedLock l(lock);
        brokers[b.getSystemId()] = b;
    }
    update();
}


void Membership::remove(const types::Uuid& id) {
    {
        sys::Mutex::ScopedLock l(lock);
        BrokerMap::iterator i = brokers.find(id);
        if (i != brokers.end())
            brokers.erase(i);
    }
    update();
}

bool Membership::contains(const types::Uuid& id) {
    sys::Mutex::ScopedLock l(lock);
    return brokers.find(id) != brokers.end();
}

void Membership::assign(const types::Variant::List& list) {
    {
        sys::Mutex::ScopedLock l(lock);
        brokers.clear();
        for (types::Variant::List::const_iterator i = list.begin(); i != list.end(); ++i) {
            BrokerInfo b(i->asMap());
            brokers[b.getSystemId()] = b;
        }
    }
    update();
}

types::Variant::List Membership::asList() const {
    sys::Mutex::ScopedLock l(lock);
    types::Variant::List list;
    for (BrokerMap::const_iterator i = brokers.begin(); i != brokers.end(); ++i)
        list.push_back(i->second.asMap());
    return list;
}

void Membership::update() {
    if (updateCallback) {
        types::Variant::List list;
        for (BrokerMap::const_iterator i = brokers.begin(); i != brokers.end(); ++i)
            list.push_back(i->second.asMap());
        updateCallback(list);
    }
}

}} // namespace qpid::ha
