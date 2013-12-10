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

/**
 * \file enums.h
 *
 * Qpid asynchronous store plugin library
 *
 * File containing definitions for namespace mrg::journal enums.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_ENUMS_H
#define QPID_LEGACYSTORE_JRNL_ENUMS_H

namespace mrg
{
namespace journal
{

    // TODO: Change this to flags, as multiple of these conditions may exist simultaneously
    /**
    * \brief Enumeration of possilbe return states from journal read and write operations.
    */
    enum _iores
    {
        RHM_IORES_SUCCESS = 0,  ///< Success: IO operation completed noramlly.
        RHM_IORES_PAGE_AIOWAIT, ///< IO operation suspended - next page is waiting for AIO.
        RHM_IORES_FILE_AIOWAIT, ///< IO operation suspended - next file is waiting for AIO.
        RHM_IORES_EMPTY,        ///< During read operations, nothing further is available to read.
        RHM_IORES_RCINVALID,    ///< Read page cache is invalid (ie obsolete or uninitialized)
        RHM_IORES_ENQCAPTHRESH, ///< Enqueue capacity threshold (limit) reached.
        RHM_IORES_FULL,         ///< During write operations, the journal files are full.
        RHM_IORES_BUSY,         ///< Another blocking operation is in progress.
        RHM_IORES_TXPENDING,    ///< Operation blocked by pending transaction.
        RHM_IORES_NOTIMPL       ///< Function is not yet implemented.
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
            case RHM_IORES_RCINVALID: return "RHM_IORES_RCINVALID";
            case RHM_IORES_ENQCAPTHRESH: return "RHM_IORES_ENQCAPTHRESH";
            case RHM_IORES_FULL: return "RHM_IORES_FULL";
            case RHM_IORES_BUSY: return "RHM_IORES_BUSY";
            case RHM_IORES_TXPENDING: return "RHM_IORES_TXPENDING";
            case RHM_IORES_NOTIMPL: return "RHM_IORES_NOTIMPL";
        }
        return "<iores unknown>";
    }

    enum _log_level
    {
        LOG_TRACE = 0,
        LOG_DEBUG,
        LOG_INFO,
        LOG_NOTICE,
        LOG_WARN,
        LOG_ERROR,
        LOG_CRITICAL
    };
    typedef _log_level log_level;

    static inline const char* log_level_str(log_level ll)
    {
        switch (ll)
        {
            case LOG_TRACE: return "TRACE";
            case LOG_DEBUG: return "DEBUG";
            case LOG_INFO: return "INFO";
            case LOG_NOTICE: return "NOTICE";
            case LOG_WARN: return "WARN";
            case LOG_ERROR: return "ERROR";
            case LOG_CRITICAL: return "CRITICAL";
        }
        return "<log level unknown>";
    }


} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_ENUMS_H
