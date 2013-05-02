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


void uuid_clear (uuid_t uu) {
    // all zeros, no change between GUID and UUID
    UuidCreateNil (reinterpret_cast<UUID*>(uu));
}

void uuid_copy (uuid_t dst, const uuid_t src) {
    memcpy (dst, src, qpid::sys::UuidSize);
}

void uuid_generate (uuid_t out) {
    UUID guid;
    UuidCreate (&guid);
    // Version 4 GUID, convert to UUID
    toUuid (&guid, out);
}

int uuid_is_null (const uuid_t uu) {
    RPC_STATUS unused;
    return UuidIsNil ((UUID*)uu, &unused);
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

namespace {

typedef struct {
    uint32_t time_low;
    uint16_t time_mid;
    uint16_t time_hi_and_version;
    uint8_t  clock_seq_hi_and_reserved;
    uint8_t  clock_seq_low;
    uint8_t  node[6];
} rfc_uuid_t;

#undef RFC_CMP
#define RFC_CMP(a, b) if (a != b) return (a < b) ? -1 : 1

}

int uuid_compare (const uuid_t a, const uuid_t b) {
    // Could convert each to a GUID and then use UuidEqual(),
    // but RFC test is straight forward
    rfc_uuid_t* u1 = (rfc_uuid_t *) a;
    rfc_uuid_t* u2 = (rfc_uuid_t *) b;
    RFC_CMP (u1->time_low, u2->time_low);
    RFC_CMP (u1->time_mid, u2->time_mid);
    RFC_CMP (u1->time_hi_and_version, u2->time_hi_and_version);
    RFC_CMP (u1->clock_seq_hi_and_reserved, u2->clock_seq_hi_and_reserved);
    RFC_CMP (u1->clock_seq_low, u2->clock_seq_low);
    for (int i = 0; i < 6; i++)
        RFC_CMP (u1->node[i], u2->node[i]);
    return 0;
}
