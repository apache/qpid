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

#include "qpid/linearstore/journal/Checksum.h"

namespace qpid {
namespace linearstore {
namespace journal {

Checksum::Checksum() : a(1UL), b(0UL), MOD_ADLER(65521UL) {}

Checksum::~Checksum() {}

void Checksum::addData(const unsigned char* data, const std::size_t len) {
    if (data) {
        for (uint32_t i = 0; i < len; i++) {
            a = (a + data[i]) % MOD_ADLER;
            b = (a + b) % MOD_ADLER;
        }
    }
}

uint32_t Checksum::getChecksum() {
    return (b << 16) | a;
}

}}}
