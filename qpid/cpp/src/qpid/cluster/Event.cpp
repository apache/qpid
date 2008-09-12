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
#include "Event.h"
#include "Cpg.h"
#include "qpid/framing/Buffer.h"
#include <ostream>
#include <iterator>
#include <algorithm>

namespace qpid {
namespace cluster {

using framing::Buffer;

const size_t Event::OVERHEAD = sizeof(uint8_t) + sizeof(uint64_t);

Event::Event(EventType t, const ConnectionId c, const size_t s)
    : type(t), connectionId(c), size(s), data(RefCountedBuffer::create(s)) {}

Event Event::delivered(const MemberId& m, void* d, size_t s) {
    Buffer buf(static_cast<char*>(d), s);
    EventType type((EventType)buf.getOctet()); 
    ConnectionId connection(m, reinterpret_cast<Connection*>(buf.getLongLong()));
    assert(buf.getPosition() == OVERHEAD);
    Event e(type, connection, s-OVERHEAD);
    memcpy(e.getData(), static_cast<char*>(d)+OVERHEAD, s-OVERHEAD);
    return e;
}
    
void Event::mcast (const Cpg::Name& name, Cpg& cpg) const {
    char header[OVERHEAD];
    Buffer b(header, OVERHEAD);
    b.putOctet(type);
    b.putLongLong(reinterpret_cast<uint64_t>(connectionId.getConnectionPtr()));
    iovec iov[] = { { header, OVERHEAD }, { const_cast<char*>(getData()), getSize() } };
    cpg.mcast(name, iov, sizeof(iov)/sizeof(*iov));
}

Event::operator Buffer() const  {
    return Buffer(const_cast<char*>(getData()), getSize());
}

static const char* EVENT_TYPE_NAMES[] = { "data", "control" };
std::ostream& operator << (std::ostream& o, const Event& e) {
    o << "[event: " << e.getConnectionId()
      << " " << EVENT_TYPE_NAMES[e.getType()]
      << " " << e.getSize() << " bytes: ";
    std::ostream_iterator<char> oi(o,"");
    std::copy(e.getData(), e.getData()+std::min(e.getSize(), size_t(16)), oi);
    return o << "...]";
}

}} // namespace qpid::cluster
