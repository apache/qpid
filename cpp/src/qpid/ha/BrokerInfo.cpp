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
#include "qpid/Exception.h"
#include "qpid/log/Statement.h"
#include <iostream>


namespace qpid {
namespace ha {

namespace {
std::string SYSTEM_ID="system-id";
std::string HOST_NAME="host-name";
std::string PORT="port";
std::string STATUS="status";
}

using framing::Uuid;
using framing::FieldTable;

FieldTable BrokerInfo::asFieldTable() const {
    FieldTable ft;
    ft.setString(SYSTEM_ID, systemId.str());
    ft.setString(HOST_NAME, hostName);
    ft.setInt(PORT, port);
    ft.setInt(STATUS, status);
    return ft;
}

void BrokerInfo::assign(const FieldTable& ft) {
    systemId = Uuid(ft.getAsString(SYSTEM_ID));
    hostName = ft.getAsString(HOST_NAME);
    port = ft.getAsInt(PORT);
    status = BrokerStatus(ft.getAsInt(STATUS));
}

std::ostream& operator<<(std::ostream& o, const BrokerInfo& b) {
    return o << b.getHostName() << ":" << b.getPort() << "(" << b.getSystemId()
             << "," << printable(b.getStatus()) << ")";
}

}}
