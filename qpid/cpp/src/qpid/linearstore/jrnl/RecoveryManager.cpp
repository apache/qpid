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

#include "qpid/linearstore/jrnl/RecoveryManager.h"

#include <iomanip>
#include "qpid/linearstore/jrnl/jcfg.h"
#include <sstream>

namespace qpid {
namespace qls_jrnl
{

RecoveryManager::RecoveryManager() : _journalFileList(),
                                     _fileNumberNameMap(),
                                     _enqueueCountList(),
                                     _journalEmptyFlag(false),
                                     _firstRecordOffset(0),
                                     _endOffset(0),
                                     _highestRecordId(0ULL),
                                     _lastFileFullFlag(false),
                                     _currentRid(0ULL),
                                     _currentFileNumber(0ULL),
                                     _currentFileName(),
                                     _fileSize(0),
                                     _recordStart(0),
                                     _inFileStream(),
                                     _readComplete(false)
{}

RecoveryManager::~RecoveryManager() {}

std::string
RecoveryManager::toString(const std::string& jid,
                          bool compact) {
    std::ostringstream oss;
    if (compact) {
        oss << "Recovery journal analysis (jid=\"" << jid << "\"):";
        oss << " jfl=[";
        for (std::map<uint64_t, std::string>::const_iterator i=_fileNumberNameMap.begin(); i!=_fileNumberNameMap.end(); ++i) {
            if (i!=_fileNumberNameMap.begin()) oss << " ";
            oss << i->first << ":" << i->second.substr(i->second.rfind('/')+1);
        }
        oss << "] ecl=[ ";
        for (std::vector<uint32_t>::const_iterator j = _enqueueCountList.begin(); j!=_enqueueCountList.end(); ++j) {
            if (j != _enqueueCountList.begin()) oss << " ";
            oss << *j;
        }
        oss << " ] empty=" << (_journalEmptyFlag ? "T" : "F");
        oss << " fro=0x" << std::hex << _firstRecordOffset << std::dec << " (" << (_firstRecordOffset/JRNL_DBLK_SIZE_BYTES) << " dblks)";
        oss << " eo=0x" << std::hex << _endOffset << std::dec << " ("  << (_endOffset/JRNL_DBLK_SIZE_BYTES) << " dblks)";
        oss << " hrid=0x" << std::hex << _highestRecordId << std::dec;
        oss << " lffull=" << (_lastFileFullFlag ? "T" : "F");
    } else {
        oss << "Recovery journal analysis (jid=\"" << jid << "\"):" << std::endl;
        oss << "  Number of journal files = " << _fileNumberNameMap.size() << std::endl;
        oss << "  Journal File List:" << std::endl;
        for (std::map<uint64_t, std::string>::const_iterator i=_fileNumberNameMap.begin(); i!=_fileNumberNameMap.end(); ++i) {
            oss << "    " << i->first << ": " << i->second.substr(i->second.rfind('/')+1) << std::endl;
        }
        oss << "  Enqueue Counts: [ " << std::endl;
        for (std::vector<uint32_t>::const_iterator j = _enqueueCountList.begin(); j!=_enqueueCountList.end(); ++j) {
            if (j != _enqueueCountList.begin()) oss << ", ";
            oss << *j;
        }
        oss << " ]" << std::endl;
        for (unsigned i=0; i<_enqueueCountList.size(); i++)
           oss << "    File " << std::setw(2) << i << ": " << _enqueueCountList[i] << std::endl;
        oss << "  Journal empty (_jempty) = " << (_journalEmptyFlag ? "TRUE" : "FALSE") << std::endl;
        oss << "  First record offset in first fid (_fro) = 0x" << std::hex << _firstRecordOffset <<
                std::dec << " (" << (_firstRecordOffset/JRNL_DBLK_SIZE_BYTES) << " dblks)" << std::endl;
        oss << "  End offset (_eo) = 0x" << std::hex << _endOffset << std::dec << " ("  <<
                (_endOffset/JRNL_DBLK_SIZE_BYTES) << " dblks)" << std::endl;
        oss << "  Highest rid (_h_rid) = 0x" << std::hex << _highestRecordId << std::dec << std::endl;
        oss << "  Last file full (_lffull) = " << (_lastFileFullFlag ? "TRUE" : "FALSE") << std::endl;
        oss << "  Enqueued records (txn & non-txn):" << std::endl;
    }
    return oss.str();
}

}} // namespace qpid::qls_jrnl
