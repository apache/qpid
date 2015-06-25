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

#ifndef QPID_LINEARSTORE_JOURNAL_ENUMS_H
#define QPID_LINEARSTORE_JOURNAL_ENUMS_H

namespace qpid {
namespace linearstore {
namespace journal {

// TODO: Change this to flags, as multiple of these conditions may exist simultaneously
/**
* \brief Enumeration of possible return states from journal read and write operations.
*/
enum _iores
{
    RHM_IORES_SUCCESS = 0,  ///< Success: IO operation completed noramlly.
    RHM_IORES_PAGE_AIOWAIT, ///< IO operation suspended - next page is waiting for AIO.
    RHM_IORES_FILE_AIOWAIT, ///< IO operation suspended - next file is waiting for AIO.
    RHM_IORES_EMPTY,        ///< During read operations, nothing further is available to read.
    RHM_IORES_TXPENDING     ///< Operation blocked by pending transaction.
};
typedef _iores iores;

static inline const char* iores_str(iores res)
{
    switch (res)
    {
        case RHM_IORES_SUCCESS: return "RHM_IORES_SUCCESS";
        case RHM_IORES_PAGE_AIOWAIT: return "RHM_IORES_PAGE_AIOWAIT";
        case RHM_IORES_FILE_AIOWAIT: return "RHM_IORES_FILE_AIOWAIT";
        case RHM_IORES_EMPTY: return "RHM_IORES_EMPTY";
        case RHM_IORES_TXPENDING: return "RHM_IORES_TXPENDING";
    }
    return "<iores unknown>";
}

}}}

#endif // ifndef QPID_LINEARSTORE_JOURNAL_ENUMS_H
