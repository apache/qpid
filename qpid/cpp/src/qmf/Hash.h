#ifndef QMF_HASH_H
#define QMF_HASH_H
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

#include "qpid/sys/IntegerTypes.h"
#include "qpid/types/Uuid.h"
#include <string>

namespace qmf {
    class Hash {
    public:
        Hash();
        qpid::types::Uuid asUuid() const { return qpid::types::Uuid((const unsigned char*) data); }
        void update(const char* s, uint32_t len);
        void update(uint8_t v) { update((char*) &v, sizeof(v)); }
        void update(uint32_t v) { update((char*) &v, sizeof(v)); }
        void update(const std::string& v) { update(const_cast<char*>(v.c_str()), v.size()); }
        void update(bool v) { update(uint8_t(v ? 1 : 0)); }
        
    private:
        uint64_t data[2];
    };
}

#endif
