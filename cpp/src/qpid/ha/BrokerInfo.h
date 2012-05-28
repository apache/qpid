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

#include "Enum.h"
#include "qpid/Url.h"
#include "qpid/framing/Uuid.h"
#include "qpid/framing/FieldTable.h"
#include <string>
#include <iosfwd>

namespace qpid {
namespace ha {

/**
 * Information about a cluster broker, maintained by the cluster primary.
 */
class BrokerInfo
{
  public:
    BrokerInfo(const std::string& host, uint16_t port_, const framing::Uuid& id) :
        hostName(host), port(port_), systemId(id) {}

    BrokerInfo(const framing::FieldTable& ft) { assign(ft); }
    framing::FieldTable asFieldTable() const;
    void assign(const framing::FieldTable&);

    framing::Uuid getSystemId() const { return systemId; }
    std::string getHostName() const { return hostName; }
    BrokerStatus getStatus() const { return status; }
     uint16_t getPort() const { return port; }

    void setStatus(BrokerStatus s)  { status = s; }

  private:
    std::string hostName;
    uint16_t port;
    framing::Uuid systemId;
    BrokerStatus status;
};

std::ostream& operator<<(std::ostream&, const BrokerInfo&);

}} // namespace qpid::ha

#endif  /*!QPID_HA_BROKERINFO_H*/
