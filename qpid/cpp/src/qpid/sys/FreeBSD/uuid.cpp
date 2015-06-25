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

#include "qpid/sys/uuid.h"

#include <string.h>
#include <uuid.h>

extern "C"
void uuid_generate (uint8_t out[qpid::sys::UuidSize])
{
    uuid_t uuid;
    uint32_t status;
    uuid_create(&uuid, &status);
    out[0] = (uuid.time_low & 0xff000000) >> 24;
    out[1] = (uuid.time_low & 0x00ff0000) >> 16;
    out[2] = (uuid.time_low & 0x0000ff00) >> 8;
    out[3] = (uuid.time_low & 0x000000ff);
    out[4] = (uuid.time_mid & 0xff00) >> 8;
    out[5] = (uuid.time_mid & 0x00ff);
    out[6] = (uuid.time_hi_and_version & 0xff00) >> 8;
    out[7] = (uuid.time_hi_and_version & 0x00ff);
    out[8] = uuid.clock_seq_hi_and_reserved;
    out[9] = uuid.clock_seq_low;
    ::memcpy(&out[10], &uuid.node, sizeof(uuid.node));
}
