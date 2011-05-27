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

#include "qmf/Hash.h"

using namespace qmf;

Hash::Hash()
{
    data[0] = 0x5A5A5A5A5A5A5A5ALL;
    data[1] = 0x5A5A5A5A5A5A5A5ALL;
}

void Hash::update(const char* s, uint32_t len)
{
    uint64_t* first  = &data[0];
    uint64_t* second = &data[1];

    for (uint32_t idx = 0; idx < len; idx++) {
        uint64_t recycle = ((*second & 0xff00000000000000LL) >> 56);
        *second = *second << 8;
        *second |= ((*first & 0xFF00000000000000LL) >> 56);
        *first = *first << 8;
        *first = *first + (uint64_t) s[idx] + recycle;
    }
}

