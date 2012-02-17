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

#include "ConnectionExcluder.h"
#include "qpid/broker/Connection.h"
#include <boost/function.hpp>
#include <sstream>

namespace qpid {
namespace ha {

ConnectionExcluder::ConnectionExcluder(PrimaryTest isPrimary_) : isPrimary(isPrimary_) {}

void ConnectionExcluder::opened(broker::Connection& connection) {
    if (!isPrimary() && !connection.isLink()
        && !connection.getClientProperties().isSet(ADMIN_TAG))
        throw Exception(
            QPID_MSG("HA: Backup broker rejected connection " << connection.getMgmtId()));
    else
        QPID_LOG(debug, "HA: Backup broker accepted connection" << connection.getMgmtId());
}

const std::string ConnectionExcluder::ADMIN_TAG="qpid.ha-admin";

}} // namespace qpid::ha
