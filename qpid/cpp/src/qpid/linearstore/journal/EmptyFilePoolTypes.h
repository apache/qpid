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

#ifndef QPID_LINEARSTORE_JOURNAL_EMPTYFILEPOOLTYPES_H_
#define QPID_LINEARSTORE_JOURNAL_EMPTYFILEPOOLTYPES_H_

#include <iostream>
#include <sstream>
#include <stdint.h>

namespace qpid {
namespace linearstore {
namespace journal {

typedef uint64_t efpDataSize_kib_t;     ///< Size of data part of file (excluding file header) in kib
typedef uint64_t efpFileSize_kib_t;     ///< Size of file (header + data) in kib
typedef uint32_t efpDataSize_sblks_t;   ///< Size of data part of file (excluding file header) in sblks
typedef uint32_t efpFileSize_sblks_t;   ///< Size of file (header + data) in sblks
typedef uint32_t efpFileCount_t;        ///< Number of files in a partition or pool
typedef uint16_t efpPartitionNumber_t;  ///< Number assigned to a partition

typedef struct efpIdentity_t {
    efpPartitionNumber_t pn_;
    efpDataSize_kib_t ds_;
    efpIdentity_t() : pn_(0), ds_(0) {}
    efpIdentity_t(efpPartitionNumber_t pn, efpDataSize_kib_t ds) : pn_(pn), ds_(ds) {}
    efpIdentity_t(const efpIdentity_t& ei) : pn_(ei.pn_), ds_(ei.ds_) {}
    friend std::ostream& operator<<(std::ostream& os, const efpIdentity_t& id) {
        // This two-stage write allows this << operator to be used with std::setw() for formatted writes
        std::ostringstream oss;
        oss << id.pn_ << "," << id.ds_;
        os << oss.str();
        return os;
    }
} efpIdentity_t;

}}}

#endif /* QPID_LINEARSTORE_JOURNAL_EMPTYFILEPOOLTYPES_H_ */
