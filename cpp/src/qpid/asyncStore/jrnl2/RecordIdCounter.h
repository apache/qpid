/*
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
 */

/**
 * \file RecordIdCounter.h
 */

#ifndef qpid_asyncStore_jrnl2_RecordCounter_h_
#define qpid_asyncStore_jrnl2_RecordCounter_h_

#include "AtomicCounter.h"

#include <stdint.h> // uint64_t

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

typedef uint64_t recordId_t;                            ///< Integral type used to represent the Record Id (RID).
typedef AtomicCounter<recordId_t> RecordIdCounter_t;    ///< Counter to increment record ids

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_RecordCounter_h_
