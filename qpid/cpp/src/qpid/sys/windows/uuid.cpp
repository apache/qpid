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

#include "qpid/sys/windows/uuid.h"

#include <string.h>

namespace {
inline void iswap (char *p1, char *p2) {
    char t = *p1;
    *p1 = *p2;
    *p2 = t;
}

void toUuid (const UUID *guid, uuid_t uuid) {
    // copy then swap bytes
    memcpy ((char *) uuid, (char *) guid, qpid::sys::UuidSize);
    char *p = (char *) uuid;
    iswap (p, p+3);
    iswap (p+1, p+2);
    iswap (p+4, p+5);
    iswap (p+6, p+7);
}

void printHex (const unsigned char *bytes, char *buf, int n) {
    static char hexrep[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                          'a', 'b', 'c', 'd', 'e', 'f'};
    for (;n ; n--) {
        unsigned char b = *bytes++;
        *buf++ = hexrep[b >> 4];
        *buf++ = hexrep[b & 0xf];
    }
}
} // namespace


void uuid_generate (uuid_t out) {
    UUID guid;
    UuidCreate (&guid);
    // Version 4 GUID, convert to UUID
    toUuid (&guid, out);
}

int uuid_parse (const char *in, uuid_t uu) {
    UUID guid;
    if (UuidFromString ((unsigned char*)in, &guid) != RPC_S_OK)
        return -1;
    toUuid (&guid, uu);
    return 0;
}

void uuid_unparse (const uuid_t uu, char *out) {
    const uint8_t *in = uu;
    out[8] = out[13] = out[18] = out[23] = '-';
    printHex (in, out, 4);
    printHex (in+4, out+9, 2);
    printHex (in+6, out+14, 2);
    printHex (in+8, out+19, 2);
    printHex (in+10, out+24, 6);
    out[36] = '\0';
}
