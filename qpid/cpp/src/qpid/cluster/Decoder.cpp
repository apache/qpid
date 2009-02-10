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

#include "Decoder.h"
#include "Event.h"
#include "qpid/framing/Buffer.h"
#include "qpid/ptr_map.h"

namespace qpid {
namespace cluster {

using namespace framing;

Decoder::Decoder(const Handler& h, ConnectionMap& cm) : handler(h), connections(cm) {}

void Decoder::decode(const EventHeader& eh, const void* data) {
    ConnectionId id = eh.getConnectionId();
    std::pair<Map::iterator, bool> ib = map.insert(id, new ConnectionDecoder(handler));
    ptr_map_ptr(ib.first)->decode(eh, data, connections);
}

void Decoder::erase(const ConnectionId& c) {
    Map::iterator i = map.find(c);
    if (i != map.end())     // FIXME aconway 2009-02-03: 
        map.erase(i);
}

}} // namespace qpid::cluster
