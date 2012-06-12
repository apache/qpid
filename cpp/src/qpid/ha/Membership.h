#ifndef QPID_HA_MEMBERSHIP_H
#define QPID_HA_MEMBERSHIP_H

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

#include "BrokerInfo.h"
#include "types.h"
#include "qpid/framing/Uuid.h"
#include "qpid/log/Statement.h"
#include "qpid/types/Variant.h"
#include <boost/function.hpp>
#include <set>
#include <vector>
#include <iosfwd>
namespace qpid {
namespace ha {

/**
 * Keep track of the brokers in the membership.
 * THREAD SAFE: updated in arbitrary connection threads.
 */
class Membership
{
  public:
    typedef boost::function<void (const types::Variant::List&) > UpdateCallback;
    Membership(const types::Uuid& self_, UpdateCallback updateFn)
        : self(self_), updateCallback(updateFn) {}

    void reset(const BrokerInfo& b); ///< Reset to contain just one member.
    void add(const BrokerInfo& b);
    void remove(const types::Uuid& id);
    bool contains(const types::Uuid& id);
    /** Return IDs of all backups other than self */
    BrokerInfo::Set otherBackups() const;

    void assign(const types::Variant::List&);
    types::Variant::List asList() const;

    bool get(const types::Uuid& id, BrokerInfo& result);

  private:
    BrokerInfo::Set otherBackups(sys::Mutex::ScopedLock&) const;
    types::Variant::List asList(sys::Mutex::ScopedLock&) const;
    void update(sys::Mutex::ScopedLock&);

    mutable sys::Mutex lock;
    types::Uuid self;
    BrokerInfo::Map brokers;
    UpdateCallback updateCallback;
};

}} // namespace qpid::ha

#endif  /*!QPID_HA_MEMBERSHIP_H*/
