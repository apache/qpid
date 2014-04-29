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

/*
 * UUIDs and GUIDs (both RFC 4122) differ on byte positions of the
 * internal representation.  This matters when encoding to the wire
 * and adhering to versioning info.  Microsoft APIs used here operate
 * on GUIDs even if the name implies UUIDs. AMQP expects the UUID 128
 * bit format which is used here unless otherwise noted.
 */

#include <rpc.h>
#ifdef uuid_t   /*  Done in rpcdce.h */
#  undef uuid_t
#endif

#include "qpid/sys/uuid.h"

#include <string.h>

namespace {
inline void iswap (char *p1, char *p2) {
    char t = *p1;
    *p1 = *p2;
    *p2 = t;
}

void toUuid (const UUID *guid, uint8_t uuid[qpid::sys::UuidSize]) {
    // copy then swap bytes
    memcpy ((char *) uuid, (char *) guid, qpid::sys::UuidSize);
    char *p = (char *) uuid;
    iswap (p, p+3);
    iswap (p+1, p+2);
    iswap (p+4, p+5);
    iswap (p+6, p+7);
}

} // namespace

namespace qpid {
namespace sys {

void uuid_generate (uint8_t out[qpid::sys::UuidSize]) {
    UUID guid;
    UuidCreate (&guid);
    // Version 4 GUID, convert to UUID
    toUuid (&guid, out);
}

}}
