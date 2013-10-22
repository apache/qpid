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

#ifndef QPID_QLS_JRNL_EMPTYFILEPOOLTYPES_H_
#define QPID_QLS_JRNL_EMPTYFILEPOOLTYPES_H_

#include <stdint.h>
#include <utility> // std::pair

namespace qpid {
namespace qls_jrnl {

    typedef uint64_t efpDataSize_kib_t;     ///< Size of data part of file (excluding file header) in kib
    typedef uint64_t efpFileSize_kib_t;     ///< Size of file (header + data) in kib
    typedef uint32_t efpDataSize_sblks_t;   ///< Size of data part of file (excluding file header) in sblks
    typedef uint32_t efpFileSize_sblks_t;   ///< Size of file (header + data) in sblks
    typedef uint32_t efpFileCount_t;        ///< Number of files in a partition or pool
    typedef uint16_t efpPartitionNumber_t;  ///< Number assigned to a partition
    typedef std::pair<efpPartitionNumber_t, efpDataSize_kib_t> efpIdentity_t; ///< Unique identity of a pool, consisting of the partition number and data size

}} // namespace qpid::qls_jrnl

#endif /* QPID_QLS_JRNL_EMPTYFILEPOOLTYPES_H_ */
