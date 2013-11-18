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

#include "qpid/linearstore/journal/JournalLog.h"

#include <iostream>

namespace qpid {
namespace linearstore {
namespace journal {

JournalLog::JournalLog(log_level_t logLevelThreshold) : logLevelThreshold_(logLevelThreshold) {}

JournalLog::~JournalLog() {}

void JournalLog::log(const log_level_t logLevel,
                     const std::string& logStatement) const {
    if (logLevel >= logLevelThreshold_) {
        std::cerr << log_level_str(logLevel) << ": " << logStatement << std::endl;
    }
}

void JournalLog::log(log_level_t logLevel,
                     const std::string& journalId,
                     const std::string& logStatement) const {
    if (logLevel >= logLevelThreshold_) {
        std::cerr << log_level_str(logLevel) << ": Journal \"" << journalId << "\": " << logStatement << std::endl;
    }
}

const char* JournalLog::log_level_str(log_level_t logLevel) {
    switch (logLevel)
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

}}}
