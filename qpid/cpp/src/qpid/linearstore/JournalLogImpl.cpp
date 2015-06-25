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

#include "qpid/linearstore/JournalLogImpl.h"

#include "qpid/log/Statement.h"

namespace qpid {
namespace linearstore {

JournalLogImpl::JournalLogImpl(const qpid::linearstore::journal::JournalLog::log_level_t logLevelThreshold) : qpid::linearstore::journal::JournalLog(logLevelThreshold) {}
JournalLogImpl::~JournalLogImpl() {}

void
JournalLogImpl::log(const qpid::linearstore::journal::JournalLog::log_level_t level,
                    const std::string& log_stmt) const {
    switch (level) {
      case LOG_CRITICAL: QPID_LOG(critical, "Linear Store: " << log_stmt); break;
      case LOG_ERROR: QPID_LOG(error, "Linear Store: " << log_stmt); break;
      case LOG_WARN: QPID_LOG(warning, "Linear Store: " << log_stmt); break;
      case LOG_NOTICE: QPID_LOG(notice, "Linear Store: " << log_stmt); break;
      case LOG_INFO: QPID_LOG(info, "Linear Store: " << log_stmt); break;
      case LOG_DEBUG: QPID_LOG(debug, "Linear Store: " << log_stmt); break;
      default: QPID_LOG(trace, "Linear Store: " << log_stmt);
    }
}

void
JournalLogImpl::log(const qpid::linearstore::journal::JournalLog::log_level_t level,
                    const std::string& jid,
                    const std::string& log_stmt) const {
    switch (level) {
      case LOG_CRITICAL: QPID_LOG(critical, "Linear Store: Journal \"" << jid << "\": " << log_stmt); break;
      case LOG_ERROR: QPID_LOG(error, "Linear Store: Journal \"" << jid << "\": " << log_stmt); break;
      case LOG_WARN: QPID_LOG(warning, "Linear Store: Journal \"" << jid << "\": " << log_stmt); break;
      case LOG_NOTICE: QPID_LOG(notice, "Linear Store: Journal \"" << jid << "\": " << log_stmt); break;
      case LOG_INFO: QPID_LOG(info, "Linear Store: Journal \"" << jid << "\": " << log_stmt); break;
      case LOG_DEBUG: QPID_LOG(debug, "Linear Store: Journal \"" << jid << "\": " << log_stmt); break;
      default: QPID_LOG(trace, "Linear Store: Journal \"" << jid << "\": " << log_stmt);
    }
}

}} // namespace qpid::linearstore
