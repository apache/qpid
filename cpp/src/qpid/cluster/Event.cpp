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
#include "qpid/framing/AMQFrame.h"
#include <ostream>
#include <iterator>
#include <algorithm>

namespace qpid {
namespace cluster {

using framing::Buffer;

const size_t Event::HEADER_SIZE =
    sizeof(uint8_t) +  // type
    sizeof(uint64_t) + // connection pointer only, CPG provides member ID.
    sizeof(uint32_t);  // payload size

Event::Event(EventType t, const ConnectionId& c,  size_t s)
    : type(t), connectionId(c), size(s), store(RefCountedBuffer::create(s+HEADER_SIZE)) {
    encodeHeader();
}

Event Event::decode(const MemberId& m, framing::Buffer& buf) {
    if (buf.available() <= HEADER_SIZE)
        throw ClusterLeaveException("Not enough for multicast header");
    EventType type((EventType)buf.getOctet());
    if(type != DATA && type != CONTROL)
        throw ClusterLeaveException("Invalid multicast event type");
    ConnectionId connection(m, reinterpret_cast<Connection*>(buf.getLongLong()));
    uint32_t size = buf.getLong();
    Event e(type, connection, size);
    if (buf.available() < size)
        throw ClusterLeaveException("Not enough data for multicast event");
    memcpy(e.getData(), buf.getPointer() + buf.getPosition(), size);
    return e;
}

Event Event::control(const framing::AMQBody& body, const ConnectionId& cid) {
    framing::AMQFrame f(body);
    Event e(CONTROL, cid, f.encodedSize());
    Buffer buf(e);
    f.encode(buf);
    return e;
}
    
void Event::encodeHeader () {
    Buffer b(getStore(), HEADER_SIZE);
    b.putOctet(type);
    b.putLongLong(reinterpret_cast<uint64_t>(connectionId.getPointer()));
    b.putLong(size);
    assert(b.getPosition() == HEADER_SIZE);
}

Event::operator Buffer() const  {
    return Buffer(const_cast<char*>(getData()), getSize());
}

static const char* EVENT_TYPE_NAMES[] = { "data", "control" };

std::ostream& operator << (std::ostream& o, const Event& e) {
    o << "[event " << e.getConnectionId() 
      << " " << EVENT_TYPE_NAMES[e.getType()]
      << " " << e.getSize() << " bytes]";
    return o;
}

}} // namespace qpid::cluster
