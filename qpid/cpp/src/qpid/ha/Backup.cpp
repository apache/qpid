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
#include "Backup.h"
#include "Settings.h"
#include "qpid/Url.h"
#include "qpid/broker/Broker.h"

namespace qpid {
namespace ha {

Backup::Backup(broker::Broker& b, const Settings& s) : broker(b), settings(s) {
    // Create a link to replicate wiring
    if (s.brokerUrl != "dummy") {
        Url url(s.brokerUrl);
        QPID_LOG(info, "HA backup broker connecting to: " << url);

        std::string protocol = url[0].protocol.empty() ? "tcp" : url[0].protocol;
        broker.getLinks().declare( // Declare the link
            url[0].host, url[0].port, protocol,
            false,              // durable
            s.mechanism, s.username, s.password);

        broker.getLinks().declare( // Declare the bridge
            url[0].host, url[0].port,
            false,              // durable
            "qpid.node-cloner", // src
            "qpid.node-cloner", // dest
            "x",                // key
            false,              // isQueue
            false,              // isLocal
            "",                 // id/tag
            "",                 // excludes
            false,              // dynamic
            0);                 // sync?
    }
    // FIXME aconway 2011-11-17: need to enhance the link code to
    // handle discovery of the primary broker and fail-over correctly.
}

}} // namespace qpid::ha
