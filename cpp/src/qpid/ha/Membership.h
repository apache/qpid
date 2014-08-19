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
#include "qpid/log/Statement.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Time.h"
#include "qpid/types/Variant.h"
#include <boost/function.hpp>
#include <set>
#include <vector>
#include <iosfwd>

namespace qmf { namespace org { namespace apache { namespace qpid { namespace ha {
class HaBroker;
}}}}}

namespace qpid {

namespace broker {
class Broker;
}

namespace types {
class Uuid;
}

namespace ha {
class HaBroker;

/**
 * Keep track of the brokers in the membership.
 * Send management when events on membership changes.
 * THREAD SAFE
 */
class Membership
{
  public:
    Membership(const BrokerInfo& info, HaBroker&);

    void setMgmtObject(boost::shared_ptr<qmf::org::apache::qpid::ha::HaBroker>);

    void clear();               ///< Clear all but self.
    void add(const BrokerInfo& b);
    void remove(const types::Uuid& id);
    bool contains(const types::Uuid& id);

    /** Return IDs of all READY backups other than self */
    BrokerInfo::Set otherBackups() const;

    /** Return IDs of all brokers */
    BrokerInfo::Set getBrokers() const;

    void assign(const types::Variant::List&);
    types::Variant::List asList() const;

    bool get(const types::Uuid& id, BrokerInfo& result) const;

    BrokerInfo getSelf() const;
    BrokerStatus getStatus() const;
    void setStatus(BrokerStatus s);

    void setSelfAddress(const Address&);

  private:
    void setPrefix();
    void update(bool log, sys::Mutex::ScopedLock&);
    BrokerStatus getStatus(sys::Mutex::ScopedLock&) const;
    types::Variant::List asList(sys::Mutex::ScopedLock&) const;

    mutable sys::Mutex lock;
    HaBroker& haBroker;
    boost::shared_ptr<qmf::org::apache::qpid::ha::HaBroker> mgmtObject;
    const types::Uuid self;
    BrokerInfo::Map brokers;
    BrokerStatus oldStatus;
};

}} // namespace qpid::ha

#endif  /*!QPID_HA_MEMBERSHIP_H*/
