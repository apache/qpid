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

namespace qpid {
namespace cluster {
using framing::Buffer;

const size_t Event::OVERHEAD = 1 /*type*/ + 8 /*64-bit pointr*/;

Event::Event(EventType t, const ConnectionId c, const size_t s)
    : type(t), connection(c), size(s), data(RefCountedBuffer::create(s)) {}

Event::Event(const MemberId& m, const char* d, size_t s)
    : connection(m, 0), size(s-OVERHEAD), data(RefCountedBuffer::create(size))
{
    memcpy(data->get(), d, s);
}
    
void Event::mcast(const Cpg::Name& name, Cpg& cpg) {
    char header[OVERHEAD];
    Buffer b;
    b.putOctet(type);
    b.putLongLong(reinterpret_cast<uint64_t>(connection.getConnectionPtr()));
    iovec iov[] = { { header, b.getPosition() }, { data.get(), size } };
    cpg.mcast(name, iov, sizeof(iov)/sizeof(*iov));
}



}} // namespace qpid::cluster
