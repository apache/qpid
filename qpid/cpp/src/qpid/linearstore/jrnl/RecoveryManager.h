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

#ifndef QPID_LINEARSTORE_RECOVERYSTATE_H_
#define QPID_LINEARSTORE_RECOVERYSTATE_H_

#include <fstream>
#include <map>
#include <stdint.h>
#include <vector>

namespace qpid {
namespace qls_jrnl {

class RecoveryManager
{
private:
    // Initial journal analysis data
    std::vector<std::string> _journalFileList;          ///< Journal file list
    std::map<uint64_t, std::string> _fileNumberNameMap; ///< File number - name map
    std::vector<uint32_t> _enqueueCountList;            ///< Number enqueued records found for each file
    bool _journalEmptyFlag;                             ///< Journal data files empty
    std::streamoff _firstRecordOffset;                  ///< First record offset in ffid
    std::streamoff _endOffset;                          ///< End offset (first byte past last record)
    uint64_t _highestRecordId;                          ///< Highest rid found
    bool _lastFileFullFlag;                             ///< Last file is full

    // State for recovery of individual enqueued records
    uint64_t _currentRid;
    uint64_t _currentFileNumber;
    std::string _currentFileName;
    std::streamoff _fileSize;
    std::streamoff _recordStart;
    std::ifstream _inFileStream;
    bool _readComplete;

public:
    RecoveryManager();
    virtual ~RecoveryManager();

    std::string toString(const std::string& jid,
                         bool compact = true);
};

}} // namespace qpid::qls_jrnl

#endif // QPID_LINEARSTORE_RECOVERYSTATE_H_
