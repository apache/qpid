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
#ifndef sys_MemStat
#define sys_MemStat

#include "qpid/CommonImportExport.h"
#include "qmf/org/apache/qpid/broker/Memory.h"

namespace qpid {
namespace sys {

    class QPID_COMMON_CLASS_EXTERN MemStat {
    public:
        QPID_COMMON_EXTERN static void loadMemInfo(qmf::org::apache::qpid::broker::Memory* object);
    };

}}

#endif

