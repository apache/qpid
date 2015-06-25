#ifndef QPID_HA_BROKERINFO_H
#define QPID_HA_BROKERINFO_H

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

#include "types.h"
#include "hash.h"
#include "qpid/Url.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/types/Uuid.h"
#include "qpid/types/Variant.h"
#include "qpid/sys/unordered_map.h"
#include <string>
#include <iosfwd>
#include <vector>

namespace qpid {
namespace ha {

/**
 * Information about a cluster broker, maintained by the cluster primary.
 */
class BrokerInfo
{
  public:
    typedef std::set<BrokerInfo> Set;
    typedef qpid::sys::unordered_map<types::Uuid, BrokerInfo, Hasher<types::Uuid> > Map;

    BrokerInfo();
    BrokerInfo(const types::Uuid& id, BrokerStatus, const Address& = Address());
    BrokerInfo(const framing::FieldTable& ft) { assign(ft); }
    BrokerInfo(const types::Variant::Map& m) { assign(m); }

    types::Uuid getSystemId() const { return systemId; }
    BrokerStatus getStatus() const { return status; }
    void setStatus(BrokerStatus s)  { status = s; }
    Address getAddress() const { return address; }
    void setAddress(const Address& a) { address = a; }

    framing::FieldTable asFieldTable() const;
    types::Variant::Map asMap() const;

    void assign(const framing::FieldTable&);
    void assign(const types::Variant::Map&);

    // So it can be put in a set.
    bool operator<(const BrokerInfo& x) const { return systemId < x.systemId; }

    bool operator==(const BrokerInfo& x) const
    { return address == x.address && systemId == x.systemId && status == x.status; }

    bool operator!=(const BrokerInfo& x) const { return !(*this == x); }

    // Print just the identifying information (shortId@address), not the status.
    std::ostream& printId(std::ostream& o) const;

  private:
    Address address;
    types::Uuid systemId;
    BrokerStatus status;
};

std::ostream& operator<<(std::ostream&, const BrokerInfo&);
std::ostream& operator<<(std::ostream&, const BrokerInfo::Set&);
std::ostream& operator<<(std::ostream&, const BrokerInfo::Map::value_type&);
std::ostream& operator<<(std::ostream&, const BrokerInfo::Map&);

}} // namespace qpid::ha

#endif  /*!QPID_HA_BROKERINFO_H*/
