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

#include "JournalLog.h"
#include <iostream>

namespace qpid {
namespace qls_jrnl {

JournalLog::JournalLog() {}

JournalLog::~JournalLog() {}

void
JournalLog::log(log_level_t ll, const std::string& jid, const std::string& log_stmt) const {
    log(ll, jid.c_str(), log_stmt.c_str());
}

void
JournalLog::log(log_level_t ll, const char* jid, const char* const log_stmt) const {
    if (ll > LOG_ERROR) {
        std::cerr << log_level_str(ll) << ": Journal \"" << jid << "\": " << log_stmt << std::endl;
    } else if (ll >= LOG_INFO) {
        std::cout << log_level_str(ll) << ": Journal \"" << jid << "\": " << log_stmt << std::endl;
    }

}

const char*
JournalLog::log_level_str(log_level_t ll) {
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

}} // namespace qpid::qls_jrnl
